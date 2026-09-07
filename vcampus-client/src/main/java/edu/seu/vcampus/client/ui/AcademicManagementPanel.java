package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.AcademicClientService;
import edu.seu.vcampus.client.service.StudentCsvParser;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.CatalogCourseDto;
import edu.seu.vcampus.common.dto.CatalogQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.MajorDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.StudentImportFailure;
import edu.seu.vcampus.common.dto.StudentImportResponse;
import edu.seu.vcampus.common.dto.StudentProfileUpdateRequest;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Academic records plus the read-only SEU major and curriculum catalog. */
public final class AcademicManagementPanel extends JPanel {
    private enum EntityType {
        STUDENT("学生"), TEACHER("教师"), DEPARTMENT("院系"), SCHOOL_CLASS("班级"),
        MAJOR("专业目录"), CATALOG_COURSE("培养方案课程");

        private final String label;

        EntityType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final AcademicClientService service;
    private final SubSystemRole effectiveRole;
    private final JComboBox<EntityType> entityType = SeuFields.combo(EntityType.values());
    private final JTextField keyword = SeuFields.text(12);
    private final JComboBox<FilterChoice> departmentFilter = SeuFields.combo(new FilterChoice[]{
            FilterChoice.all("全部学院")});
    private final JComboBox<FilterChoice> classFilter = SeuFields.combo(new FilterChoice[]{
            FilterChoice.all("全部班级")});
    private final JTextField majorId = SeuFields.text(8);
    private final JButton searchButton = SeuButtons.primary("查询");
    private final JButton addButton = SeuButtons.secondary("新增");
    private final JButton editButton = SeuButtons.secondary("编辑");
    private final JButton deleteButton = SeuButtons.danger("删除");
    private final JButton importButton = SeuButtons.secondary("批量导入 CSV");
    private final JButton profileButton = SeuButtons.secondary("编辑个人资料");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final DefaultTableModel tableModel = SeuTables.readOnlyModel(new String[0]);
    private final JTable table = SeuTables.create(tableModel);
    private List<?> rows = new ArrayList<Object>();
    private List<DepartmentDto> departments = new ArrayList<DepartmentDto>();
    private List<SchoolClassDto> classes = new ArrayList<SchoolClassDto>();
    private boolean updatingFilters;

    public AcademicManagementPanel(AcademicClientService service, SubSystemRole effectiveRole) {
        super(new BorderLayout(0, SeuTheme.SPACE_MD));
        this.service = service;
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        this.effectiveRole = effectiveRole;
        setBackground(SeuTheme.PAGE_BG);
        setBorder(SeuTheme.pageBorder());
        buildUi();
        bindActions();
        loadReferenceData();
    }

    private void buildUi() {
        updateKeywordHint();
        SeuFields.setPlaceholder(majorId, "如：080901");

        JPanel filterRow = SeuPanels.toolbar();
        filterRow.add(SeuLabels.field("数据类型"));
        filterRow.add(entityType);
        filterRow.add(SeuLabels.field("关键字"));
        filterRow.add(keyword);
        filterRow.add(SeuLabels.field("学院"));
        filterRow.add(departmentFilter);
        filterRow.add(SeuLabels.field("班级"));
        filterRow.add(classFilter);
        filterRow.add(SeuLabels.field("专业代码"));
        filterRow.add(majorId);
        filterRow.add(searchButton);

        JPanel actionRow = SeuPanels.toolbar();
        actionRow.add(addButton);
        actionRow.add(editButton);
        actionRow.add(deleteButton);
        actionRow.add(importButton);
        actionRow.add(profileButton);

        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading("学籍管理 · 学籍与培养方案（"
                + effectiveRole.getDisplayName() + "）", statusLabel), BorderLayout.NORTH);
        north.add(effectiveRole == SubSystemRole.ADMIN || effectiveRole == SubSystemRole.STUDENT
                ? SeuPanels.stack(filterRow, actionRow) : filterRow, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel card = SeuPanels.card();
        card.add(SeuTables.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        boolean administrator = effectiveRole == SubSystemRole.ADMIN;
        addButton.setVisible(administrator);
        editButton.setVisible(administrator);
        deleteButton.setVisible(administrator);
        importButton.setVisible(administrator);
        profileButton.setVisible(effectiveRole == SubSystemRole.STUDENT);
        if (effectiveRole == SubSystemRole.STUDENT) {
            entityType.removeItem(EntityType.TEACHER);
            entityType.removeItem(EntityType.DEPARTMENT);
            entityType.removeItem(EntityType.SCHOOL_CLASS);
            entityType.setSelectedItem(EntityType.STUDENT);
        }
        updateActionAvailability(false);
    }

    private void bindActions() {
        searchButton.addActionListener(event -> refreshRows());
        keyword.addActionListener(event -> refreshRows());
        entityType.addActionListener(event -> {
            updateKeywordHint();
            updateActionAvailability(false);
            refreshRows();
        });
        departmentFilter.addActionListener(event -> {
            if (updatingFilters) {
                return;
            }
            refreshClassFilter();
            refreshRows();
        });
        classFilter.addActionListener(event -> {
            if (!updatingFilters) {
                refreshRows();
            }
        });
        addButton.addActionListener(event -> editRecord(null));
        editButton.addActionListener(event -> editSelected());
        deleteButton.addActionListener(event -> deleteSelected());
        importButton.addActionListener(event -> importStudents());
        profileButton.addActionListener(event -> editOwnProfile());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && effectiveRole == SubSystemRole.ADMIN
                        && !isCatalogType()) {
                    editSelected();
                }
            }
        });
    }

    private AcademicQueryRequest currentQuery() {
        return new AcademicQueryRequest(keyword.getText(), selectedId(departmentFilter),
                selectedId(classFilter), false);
    }

    private CatalogQueryRequest currentCatalogQuery() {
        return new CatalogQueryRequest(keyword.getText(), selectedId(departmentFilter),
                majorId.getText(), false);
    }

    private void loadReferenceData() {
        setBusy(true, "正在加载学院和班级……");
        new SwingWorker<List<?>[], Void>() {
            @Override
            protected List<?>[] doInBackground() throws Exception {
                return new List<?>[]{
                        service.queryDepartments(true),
                        service.queryClasses(new AcademicQueryRequest(null, null, null, false))
                };
            }

            @Override
            protected void done() {
                try {
                    List<?>[] result = get();
                    departments = castDepartments(result[0]);
                    classes = castClasses(result[1]);
                    refreshDepartmentFilter();
                    refreshRows();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("加载学院和班级被中断");
                    setBusy(false, "加载失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "加载失败");
                } catch (IOException e) {
                    showError(e.getMessage());
                    setBusy(false, "加载失败");
                }
            }
        }.execute();
    }

    private List<DepartmentDto> castDepartments(List<?> source) throws IOException {
        List<DepartmentDto> result = new ArrayList<DepartmentDto>();
        for (Object item : source) {
            if (!(item instanceof DepartmentDto)) {
                throw new IOException("学院数据格式不正确");
            }
            result.add((DepartmentDto) item);
        }
        return result;
    }

    private List<SchoolClassDto> castClasses(List<?> source) throws IOException {
        List<SchoolClassDto> result = new ArrayList<SchoolClassDto>();
        for (Object item : source) {
            if (!(item instanceof SchoolClassDto)) {
                throw new IOException("班级数据格式不正确");
            }
            result.add((SchoolClassDto) item);
        }
        return result;
    }

    private void refreshDepartmentFilter() {
        String previous = selectedId(departmentFilter);
        updatingFilters = true;
        departmentFilter.removeAllItems();
        departmentFilter.addItem(FilterChoice.all("全部学院"));
        for (DepartmentDto department : departments) {
            departmentFilter.addItem(new FilterChoice(department.getDepartmentId(),
                    department.getDepartmentName() + "（" + department.getDepartmentId() + "）"));
        }
        select(departmentFilter, previous);
        updatingFilters = false;
        refreshClassFilter();
    }

    private void refreshClassFilter() {
        String previous = selectedId(classFilter);
        String departmentId = selectedId(departmentFilter);
        updatingFilters = true;
        classFilter.removeAllItems();
        classFilter.addItem(FilterChoice.all("全部班级"));
        for (SchoolClassDto schoolClass : classes) {
            if (departmentId.isEmpty() || departmentId.equals(schoolClass.getDepartmentId())) {
                classFilter.addItem(new FilterChoice(schoolClass.getClassId(),
                        schoolClass.getClassName() + "（" + schoolClass.getClassId() + "）"));
            }
        }
        select(classFilter, previous);
        updatingFilters = false;
    }

    private void updateKeywordHint() {
        EntityType selected = (EntityType) entityType.getSelectedItem();
        String hint;
        if (selected == EntityType.STUDENT) {
            hint = "学号 / 姓名";
        } else if (selected == EntityType.TEACHER) {
            hint = "工号 / 姓名";
        } else if (selected == EntityType.SCHOOL_CLASS) {
            hint = "班级编号 / 名称";
        } else if (selected == EntityType.DEPARTMENT) {
            hint = "学院编号 / 名称";
        } else if (selected == EntityType.MAJOR) {
            hint = "专业代码 / 名称";
        } else {
            hint = "课程代码 / 名称";
        }
        SeuFields.setPlaceholder(keyword, hint);
        keyword.setToolTipText("关键字仅模糊匹配当前数据类型的编号和名称");
    }

    private void refreshRows() {
        setBusy(true, "正在加载……");
        final EntityType requestedType = (EntityType) entityType.getSelectedItem();
        final AcademicQueryRequest requestedQuery = currentQuery();
        final CatalogQueryRequest requestedCatalogQuery = currentCatalogQuery();
        new SwingWorker<List<?>, Void>() {
            @Override
            protected List<?> doInBackground() throws Exception {
                switch (requestedType) {
                    case STUDENT:
                        return service.queryStudents(requestedQuery);
                    case TEACHER:
                        return service.queryTeachers(requestedQuery);
                    case DEPARTMENT:
                        return service.queryDepartments(requestedQuery);
                    case SCHOOL_CLASS:
                        return service.queryClasses(requestedQuery);
                    case MAJOR:
                        return service.queryMajors(requestedCatalogQuery);
                    case CATALOG_COURSE:
                        return service.queryCatalogCourses(requestedCatalogQuery);
                    default:
                        throw new IllegalStateException("Unknown academic entity");
                }
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    renderRows(requestedType);
                    statusLabel.setText("共 " + rows.size() + " 条记录");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("加载被中断");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                } finally {
                    setBusy(false, statusLabel.getText());
                }
            }
        }.execute();
    }

    private void renderRows(EntityType type) {
        tableModel.setRowCount(0);
        tableModel.setColumnIdentifiers(columns(type));
        for (Object row : rows) {
            tableModel.addRow(rowValues(type, row));
        }
    }

    private String[] columns(EntityType type) {
        switch (type) {
            case STUDENT:
                return new String[]{"学号", "账号", "姓名", "性别", "出生日期", "院系",
                        "班级", "入学年", "状态", "电话", "邮箱"};
            case TEACHER:
                return new String[]{"工号", "账号", "姓名", "院系", "职称", "电话", "邮箱", "在岗"};
            case DEPARTMENT:
                return new String[]{"院系编号", "院系名称", "简介", "启用"};
            case SCHOOL_CLASS:
                return new String[]{"班级编号", "班级名称", "学院", "年级", "辅导员",
                        "容量", "启用"};
            case MAJOR:
                return new String[]{"专业代码", "专业名称", "院系", "学位", "学制",
                        "来源年份", "启用", "官方来源"};
            case CATALOG_COURSE:
                return new String[]{"课程代码", "课程名称", "院系", "学分", "理论学时",
                        "实践学时", "课程类别", "建议年级", "建议学期", "必修",
                        "来源年份", "官方来源"};
            default:
                return new String[0];
        }
    }

    private Object[] rowValues(EntityType type, Object row) {
        switch (type) {
            case STUDENT:
                StudentDto student = (StudentDto) row;
                return new Object[]{student.getStudentId(), student.getUserId(),
                        student.getFullName(), student.getGender(), student.getBirthDate(),
                        student.getDepartmentId(), student.getClassId(),
                        student.getEnrollmentYear(), student.getStatus(),
                        student.getPhone(), student.getEmail()};
            case TEACHER:
                TeacherDto teacher = (TeacherDto) row;
                return new Object[]{teacher.getTeacherId(), teacher.getUserId(),
                        teacher.getFullName(), teacher.getDepartmentId(), teacher.getTitle(),
                        teacher.getPhone(), teacher.getEmail(), teacher.isActive()};
            case DEPARTMENT:
                DepartmentDto department = (DepartmentDto) row;
                return new Object[]{department.getDepartmentId(), department.getDepartmentName(),
                        department.getDescription(), department.isActive()};
            case SCHOOL_CLASS:
                SchoolClassDto schoolClass = (SchoolClassDto) row;
                return new Object[]{schoolClass.getClassId(), schoolClass.getClassName(),
                        schoolClass.getDepartmentId(), schoolClass.getGradeYear(),
                        schoolClass.getCounselor(), schoolClass.getCapacity(),
                        schoolClass.isActive()};
            case MAJOR:
                MajorDto major = (MajorDto) row;
                return new Object[]{major.getMajorId(), major.getMajorName(),
                        major.getDepartmentId(), major.getDegreeType(),
                        major.getDurationYears() + " 年", major.getSourceYear(),
                        major.isActive(), major.getSourceUrl()};
            case CATALOG_COURSE:
                CatalogCourseDto course = (CatalogCourseDto) row;
                return new Object[]{course.getCourseId(), course.getCourseName(),
                        course.getDepartmentId(), course.getCredits(),
                        course.getLectureHours(), course.getPracticeHours(),
                        course.getCourseType(), course.getRecommendedYear(),
                        course.getRecommendedSemester(), course.isRequired(),
                        course.getSourceYear(), course.getSourceUrl()};
            default:
                return new Object[0];
        }
    }

    private Object selectedRecord() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            SeuMessages.info(this, "请先选择一条记录");
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
    }

    private void editRecord(Object existing) {
        if (isCatalogType()) {
            return;
        }
        try {
            final Object edited = showEditor(existing);
            if (edited == null) {
                return;
            }
            runMutation("正在保存……", new IoAction() {
                @Override
                public void run() throws IOException {
                    save(edited);
                }
            });
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void editSelected() {
        Object selected = selectedRecord();
        if (selected != null) {
            editRecord(selected);
        }
    }

    private Object showEditor(Object existing) {
        switch ((EntityType) entityType.getSelectedItem()) {
            case STUDENT:
                return AcademicEditors.editStudent(
                        this, (StudentDto) existing, departments, classes);
            case TEACHER:
                return AcademicEditors.editTeacher(this, (TeacherDto) existing, departments);
            case DEPARTMENT:
                return AcademicEditors.editDepartment(this, (DepartmentDto) existing);
            case SCHOOL_CLASS:
                return AcademicEditors.editClass(this, (SchoolClassDto) existing, departments);
            default:
                return null;
        }
    }

    private void save(Object value) throws IOException {
        if (value instanceof StudentDto) {
            service.saveStudent((StudentDto) value);
        } else if (value instanceof TeacherDto) {
            service.saveTeacher((TeacherDto) value);
        } else if (value instanceof DepartmentDto) {
            service.saveDepartment((DepartmentDto) value);
        } else if (value instanceof SchoolClassDto) {
            service.saveClass((SchoolClassDto) value);
        }
    }

    private void editOwnProfile() {
        if (rows.isEmpty() || !(rows.get(0) instanceof StudentDto)) {
            SeuMessages.info(this, "当前账号没有可编辑的学籍记录");
            return;
        }
        final StudentProfileUpdateRequest profile = AcademicEditors.editOwnProfile(
                this, (StudentDto) rows.get(0));
        if (profile == null) {
            return;
        }
        runMutation("正在保存个人资料……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.updateOwnProfile(profile);
            }
        });
    }

    private void importStudents() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择学生学籍 CSV 文件");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final List<StudentDto> students;
        try {
            students = StudentCsvParser.parse(readUtf8(chooser.getSelectedFile()));
        } catch (IOException e) {
            showError("读取文件失败：" + e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        if (!SeuMessages.confirm(this, "已读取 " + students.size()
                + " 条学籍。班级为空的记录将自动分班，是否开始导入？")) {
            return;
        }
        setBusy(true, "正在批量导入……");
        new SwingWorker<StudentImportResponse, Void>() {
            @Override
            protected StudentImportResponse doInBackground() throws Exception {
                return service.importStudents(students);
            }

            @Override
            protected void done() {
                try {
                    presentImportResult(get());
                    loadReferenceData();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("批量导入被中断");
                    setBusy(false, "导入失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "导入失败");
                }
            }
        }.execute();
    }

    private void presentImportResult(StudentImportResponse response) {
        StringBuilder message = new StringBuilder("成功导入 ")
                .append(response.getImported()).append(" 条学籍");
        if (!response.getFailures().isEmpty()) {
            message.append("，失败 ").append(response.getFailures().size()).append(" 条：");
            int shown = 0;
            for (StudentImportFailure failure : response.getFailures()) {
                if (shown++ >= 20) {
                    message.append("\n其余失败记录请修正后重新导入。");
                    break;
                }
                message.append("\n数据第 ").append(failure.getRow()).append(" 行 ")
                        .append(failure.getStudentId() == null ? "" : failure.getStudentId())
                        .append("：").append(failure.getReason());
            }
        }
        SeuMessages.info(this, "批量导入结果", message.toString());
    }

    private String readUtf8(File file) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    private void deleteSelected() {
        if (isCatalogType()) {
            return;
        }
        final Object selected = selectedRecord();
        if (selected == null || !SeuMessages.confirm(this, "确定删除所选记录吗？")) {
            return;
        }
        runMutation("正在删除……", new IoAction() {
            @Override
            public void run() throws IOException {
                if (selected instanceof StudentDto) {
                    service.deleteStudent(((StudentDto) selected).getStudentId());
                } else if (selected instanceof TeacherDto) {
                    service.deleteTeacher(((TeacherDto) selected).getTeacherId());
                } else if (selected instanceof DepartmentDto) {
                    service.deleteDepartment(((DepartmentDto) selected).getDepartmentId());
                } else if (selected instanceof SchoolClassDto) {
                    service.deleteClass(((SchoolClassDto) selected).getClassId());
                }
            }
        });
    }

    private void runMutation(String status, final IoAction action) {
        setBusy(true, status);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadReferenceData();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("操作被中断");
                    setBusy(false, "操作失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "操作失败");
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        searchButton.setEnabled(!busy);
        updateActionAvailability(busy);
        entityType.setEnabled(!busy);
        keyword.setEnabled(!busy);
        departmentFilter.setEnabled(!busy);
        classFilter.setEnabled(!busy);
        majorId.setEnabled(!busy);
    }

    private void updateActionAvailability(boolean busy) {
        boolean mutable = effectiveRole == SubSystemRole.ADMIN && !isCatalogType();
        addButton.setEnabled(!busy && mutable);
        editButton.setEnabled(!busy && mutable);
        deleteButton.setEnabled(!busy && mutable);
        importButton.setEnabled(!busy && effectiveRole == SubSystemRole.ADMIN);
        profileButton.setEnabled(!busy && effectiveRole == SubSystemRole.STUDENT
                && entityType.getSelectedItem() == EntityType.STUDENT && !rows.isEmpty());
    }

    private boolean isCatalogType() {
        EntityType selected = (EntityType) entityType.getSelectedItem();
        return selected == EntityType.MAJOR || selected == EntityType.CATALOG_COURSE;
    }

    private static String selectedId(JComboBox<FilterChoice> combo) {
        FilterChoice selected = (FilterChoice) combo.getSelectedItem();
        return selected == null ? "" : selected.id;
    }

    private static void select(JComboBox<FilterChoice> combo, String id) {
        String wanted = id == null ? "" : id;
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (wanted.equals(combo.getItemAt(index).id)) {
                combo.setSelectedIndex(index);
                return;
            }
        }
        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    private String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? "操作失败" : cause.getMessage();
    }

    private void showError(String message) {
        SeuMessages.error(this, message);
    }

    private interface IoAction {
        void run() throws IOException;
    }

    private static final class FilterChoice {
        private final String id;
        private final String label;

        private FilterChoice(String id, String label) {
            this.id = id;
            this.label = label;
        }

        private static FilterChoice all(String label) {
            return new FilterChoice("", label);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
