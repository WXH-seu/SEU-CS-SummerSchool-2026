package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.AcademicClientService;
import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Search and maintenance page for students, teachers, classes and departments. */
public final class AcademicManagementPanel extends JPanel {
    private enum EntityType {
        STUDENT("学生"), TEACHER("教师"), DEPARTMENT("院系"), SCHOOL_CLASS("班级");

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
    private final JComboBox<EntityType> entityType = new JComboBox<EntityType>(EntityType.values());
    private final JTextField keyword = new JTextField(10);
    private final JTextField departmentId = new JTextField(7);
    private final JTextField classId = new JTextField(8);
    private final JButton searchButton = new JButton("查询");
    private final JButton addButton = new JButton("新增");
    private final JButton editButton = new JButton("编辑");
    private final JButton deleteButton = new JButton("删除");
    private final JLabel statusLabel = new JLabel("准备就绪");
    private final DefaultTableModel tableModel = new DefaultTableModel() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private List<?> rows = new ArrayList<Object>();

    public AcademicManagementPanel(AcademicClientService service, SubSystemRole effectiveRole) {
        super(new BorderLayout(0, 12));
        this.service = service;
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        this.effectiveRole = effectiveRole;
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        buildUi();
        bindActions();
        refreshRows();
    }

    private void buildUi() {
        JPanel heading = new JPanel(new BorderLayout());
        JLabel title = new JLabel("学籍管理");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        heading.add(title, BorderLayout.WEST);
        heading.add(statusLabel, BorderLayout.EAST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(new JLabel("数据类型"));
        filters.add(entityType);
        filters.add(new JLabel("关键字"));
        filters.add(keyword);
        filters.add(new JLabel("院系"));
        filters.add(departmentId);
        filters.add(new JLabel("班级"));
        filters.add(classId);
        filters.add(searchButton);
        filters.add(addButton);
        filters.add(editButton);
        filters.add(deleteButton);

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.add(heading, BorderLayout.NORTH);
        north.add(filters, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        boolean administrator = effectiveRole == SubSystemRole.ADMIN;
        addButton.setVisible(administrator);
        editButton.setVisible(administrator);
        deleteButton.setVisible(administrator);
        if (effectiveRole == SubSystemRole.STUDENT) {
            entityType.setSelectedItem(EntityType.STUDENT);
            entityType.setEnabled(false);
        }
    }

    private void bindActions() {
        searchButton.addActionListener(event -> refreshRows());
        keyword.addActionListener(event -> refreshRows());
        entityType.addActionListener(event -> refreshRows());
        addButton.addActionListener(event -> editRecord(null));
        editButton.addActionListener(event -> editSelected());
        deleteButton.addActionListener(event -> deleteSelected());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && effectiveRole == SubSystemRole.ADMIN) {
                    editSelected();
                }
            }
        });
    }

    private AcademicQueryRequest currentQuery() {
        return new AcademicQueryRequest(keyword.getText(), departmentId.getText(),
                classId.getText(), false);
    }

    private void refreshRows() {
        setBusy(true, "正在加载……");
        final EntityType requestedType = (EntityType) entityType.getSelectedItem();
        final AcademicQueryRequest requestedQuery = currentQuery();
        new SwingWorker<List<?>, Void>() {
            @Override
            protected List<?> doInBackground() throws Exception {
                switch (requestedType) {
                    case STUDENT:
                        return service.queryStudents(requestedQuery);
                    case TEACHER:
                        return service.queryTeachers(requestedQuery);
                    case DEPARTMENT:
                        return service.queryDepartments(false);
                    case SCHOOL_CLASS:
                        return service.queryClasses(requestedQuery);
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
                return new String[]{"班级编号", "班级名称", "院系", "年级", "辅导员", "启用"};
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
                        schoolClass.getCounselor(), schoolClass.isActive()};
            default:
                return new Object[0];
        }
    }

    private Object selectedRecord() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一条记录", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
    }

    private void editRecord(Object existing) {
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
                return AcademicEditors.editStudent(this, (StudentDto) existing);
            case TEACHER:
                return AcademicEditors.editTeacher(this, (TeacherDto) existing);
            case DEPARTMENT:
                return AcademicEditors.editDepartment(this, (DepartmentDto) existing);
            case SCHOOL_CLASS:
                return AcademicEditors.editClass(this, (SchoolClassDto) existing);
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

    private void deleteSelected() {
        final Object selected = selectedRecord();
        if (selected == null || JOptionPane.showConfirmDialog(this,
                "确定删除所选记录吗？", "确认删除", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) {
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
                    refreshRows();
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
        addButton.setEnabled(!busy);
        editButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy);
        entityType.setEnabled(!busy && effectiveRole != SubSystemRole.STUDENT);
    }

    private String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? "操作失败" : cause.getMessage();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }

    private interface IoAction {
        void run() throws IOException;
    }
}
