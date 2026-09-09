package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.AcademicClientService;
import edu.seu.vcampus.client.service.CourseClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SectionAudienceDto;
import edu.seu.vcampus.common.dto.SectionRosterEntry;
import edu.seu.vcampus.common.dto.SectionScheduleDto;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** 开课目录：学生选课、教师名单、管理员维护。 */
final class CourseCatalogPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String[] NATURE_ITEMS =
            {"", "必修", "限选", "任选", "通选"};
    private static final DateTimeFormatter WINDOW_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter WINDOW_SHORT =
            DateTimeFormatter.ofPattern("M/d HH:mm");

    private final CourseClientService service;
    private final AcademicClientService academicService;
    private final SubSystemRole effectiveRole;
    private final Runnable onSelected;
    private final boolean student;

    private final JTextField keyword = SeuFields.text(14);
    private final JComboBox<String> natureFilter = SeuFields.combo(NATURE_ITEMS);
    private final JCheckBox includeInactive = new JCheckBox("含停用");
    private final JButton searchButton = SeuButtons.primary("查询");
    private final JButton selectButton = SeuButtons.accent("选课");
    private final JButton rosterButton = SeuButtons.secondary("选课名单");
    private final JButton addButton = SeuButtons.secondary("新增");
    private final JButton editButton = SeuButtons.secondary("编辑");
    private final JButton deleteButton = SeuButtons.danger("删除");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final DefaultTableModel tableModel = SeuTables.readOnlyModel(new String[0]);
    private final JTable table = SeuTables.create(tableModel);
    private List<CourseDto> rows = new ArrayList<CourseDto>();

    CourseCatalogPanel(CourseClientService service, AcademicClientService academicService,
                       SubSystemRole effectiveRole, Runnable onSelected) {
        super(new BorderLayout(0, SeuTheme.SPACE_MD));
        this.service = service;
        this.academicService = academicService;
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        this.effectiveRole = effectiveRole;
        this.student = effectiveRole == SubSystemRole.STUDENT;
        this.onSelected = onSelected;
        setBackground(SeuTheme.PAGE_BG);
        setBorder(SeuTheme.pageBorder());
        buildUi();
        bindActions();
        refresh();
    }

    private void buildUi() {
        SeuFields.setPlaceholder(keyword, "课程编号 / 名称 / 教师");
        includeInactive.setFont(SeuTheme.bodyFont());
        includeInactive.setForeground(SeuTheme.TEXT);
        includeInactive.setOpaque(false);

        JPanel filters = SeuPanels.toolbar();
        filters.add(SeuLabels.field("关键字"));
        filters.add(keyword);
        filters.add(SeuLabels.field("性质"));
        filters.add(natureFilter);
        filters.add(includeInactive);
        filters.add(searchButton);
        filters.add(selectButton);
        filters.add(rosterButton);
        filters.add(addButton);
        filters.add(editButton);
        filters.add(deleteButton);

        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading("选课 · 开课目录（"
                + effectiveRole.getDisplayName() + "）", statusLabel), BorderLayout.NORTH);
        north.add(filters, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel card = SeuPanels.card();
        card.add(SeuTables.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        boolean administrator = effectiveRole == SubSystemRole.ADMIN;
        selectButton.setVisible(student);
        rosterButton.setVisible(!student);
        includeInactive.setVisible(!student);
        addButton.setVisible(administrator);
        editButton.setVisible(administrator);
        deleteButton.setVisible(administrator);
    }

    private void bindActions() {
        searchButton.addActionListener(event -> refresh());
        keyword.addActionListener(event -> refresh());
        natureFilter.addActionListener(event -> refresh());
        includeInactive.addActionListener(event -> refresh());
        selectButton.addActionListener(event -> selectSelected());
        rosterButton.addActionListener(event -> showRoster());
        addButton.addActionListener(event -> editSection(null));
        editButton.addActionListener(event -> editSelected());
        deleteButton.addActionListener(event -> deleteSelected());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    showDetails(selectedRow());
                }
            }
        });
        installTooltips(table);
    }

    void refresh() {
        setBusy(true, "正在加载……");
        final CourseQueryRequest query = new CourseQueryRequest(
                keyword.getText(), null, null, null,
                student || !includeInactive.isSelected(),
                emptyToNull((String) natureFilter.getSelectedItem()));
        new SwingWorker<List<CourseDto>, Void>() {
            @Override
            protected List<CourseDto> doInBackground() throws Exception {
                return service.queryCourses(query);
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    renderRows();
                    statusLabel.setText("共 " + rows.size() + " 个教学班");
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

    private void renderRows() {
        String[] columns = student ? studentColumns() : staffColumns();
        int[] widths = student ? studentWidths() : staffWidths();
        tableModel.setColumnIdentifiers(columns);
        tableModel.setRowCount(0);
        for (int i = 0; i < columns.length && i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        for (CourseDto course : rows) {
            String capacity = course.getCapacity() + "/" + course.getEnrolledCount();
            if (student) {
                tableModel.addRow(new Object[]{
                        course.getCourseId(), course.getCourseName(),
                        course.getCourseNature(), course.getTeacherName(),
                        course.getCredit(), quotaOf(course), course.getClassTime(),
                        windowShort(course), stateNote(course)});
            } else {
                tableModel.addRow(new Object[]{
                        course.getCourseId(), course.getCourseName(),
                        course.getCourseNature(), course.getTeacherName(),
                        course.getDepartmentName(), course.getCredit(), capacity,
                        poolSummary(course), course.getSemesterName(), course.getClassTime(),
                        stateNote(course)});
            }
        }
    }

    private String quotaOf(CourseDto course) {
        if (CourseDto.ATTEMPT_RETAKE.equals(course.getAttemptType())) {
            return "重修 " + course.getRetakeEnrolled() + "/"
                    + course.getRetakeCapacity();
        }
        return "首修 " + course.getFirstAttemptEnrolled() + "/"
                + course.getFirstAttemptCapacity();
    }

    private String poolSummary(CourseDto course) {
        return course.getFirstAttemptEnrolled() + "/"
                + course.getFirstAttemptCapacity() + " · "
                + course.getRetakeEnrolled() + "/" + course.getRetakeCapacity();
    }

    private String stateNote(CourseDto course) {
        if (student) {
            if (course.getReason() != null && !course.getReason().isEmpty()) {
                return course.getReason();
            }
            return course.isSelected() ? "已选" : "可报名";
        }
        return course.isActive() ? "开放" : "停用";
    }

    private String attemptLabel(String attemptType) {
        return CourseDto.ATTEMPT_RETAKE.equals(attemptType) ? "重修" : "首修";
    }

    private String scheduleText(SectionScheduleDto schedule) {
        return schedule.getWeekStart() + "-" + schedule.getWeekEnd() + "周 "
                + weekdayName(schedule.getWeekday()) + " "
                + schedule.getPeriodStart() + "-" + schedule.getPeriodEnd()
                + " 节 · " + blankTo(schedule.getLocation(), "未填地点");
    }

    private String weekdayName(int weekday) {
        switch (weekday) {
            case 1: return "周一";
            case 2: return "周二";
            case 3: return "周三";
            case 4: return "周四";
            case 5: return "周五";
            case 6: return "周六";
            case 7: return "周日";
            default: return "周" + weekday;
        }
    }

    private void selectSelected() {
        final CourseDto course = selectedRow();
        if (course == null) {
            return;
        }
        if (course.getReason() != null) {
            SeuMessages.info(this, course.getReason());
            return;
        }
        if (!SeuMessages.confirm(this,
                "确定选择「" + course.getCourseName() + "」吗？")) {
            return;
        }
        runMutation("正在选课……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.selectCourse(course.getSectionId());
            }
        });
    }

    private void showRoster() {
        final CourseDto course = selectedRow();
        if (course == null) {
            return;
        }
        setBusy(true, "正在加载名单……");
        new SwingWorker<List<SectionRosterEntry>, Void>() {
            @Override
            protected List<SectionRosterEntry> doInBackground() throws Exception {
                return service.queryRoster(course.getSectionId());
            }

            @Override
            protected void done() {
                try {
                    List<SectionRosterEntry> entries = get();
                    int firstCount = 0;
                    int retakeCount = 0;
                    boolean administrator = effectiveRole == SubSystemRole.ADMIN;
                    String[] columns = administrator
                            ? new String[]{"学号", "姓名", "院系", "班级", "修读类型",
                                    "电话", "邮箱", "选课时间"}
                            : new String[]{"学号", "姓名", "院系", "班级", "修读类型",
                                    "电话", "邮箱"};
                    DefaultTableModel rosterModel = SeuTables.readOnlyModel(columns);
                    for (SectionRosterEntry entry : entries) {
                        if (CourseDto.ATTEMPT_RETAKE.equals(entry.getAttemptType())) {
                            retakeCount++;
                        } else {
                            firstCount++;
                        }
                        Object[] row = administrator
                                ? new Object[]{entry.getStudentId(), entry.getFullName(),
                                        entry.getDepartmentName(), entry.getClassName(),
                                        attemptLabel(entry.getAttemptType()),
                                        blankTo(entry.getPhone(), "—"),
                                        blankTo(entry.getEmail(), "—"),
                                        entry.getEnrollTime()}
                                : new Object[]{entry.getStudentId(), entry.getFullName(),
                                        entry.getDepartmentName(), entry.getClassName(),
                                        attemptLabel(entry.getAttemptType()),
                                        blankTo(entry.getPhone(), "—"),
                                        blankTo(entry.getEmail(), "—")};
                        rosterModel.addRow(row);
                    }
                    JTable rosterTable = SeuTables.create(rosterModel);
                    rosterTable.getColumnModel().getColumn(0).setPreferredWidth(110);
                    rosterTable.getColumnModel().getColumn(1).setPreferredWidth(110);
                    rosterTable.getColumnModel().getColumn(2).setPreferredWidth(170);
                    rosterTable.getColumnModel().getColumn(3).setPreferredWidth(160);
                    rosterTable.getColumnModel().getColumn(4).setPreferredWidth(70);
                    rosterTable.getColumnModel().getColumn(5).setPreferredWidth(120);
                    rosterTable.getColumnModel().getColumn(6).setPreferredWidth(150);
                    if (administrator) {
                        rosterTable.getColumnModel().getColumn(7).setPreferredWidth(170);
                    }
                    installTooltips(rosterTable);
                    JScrollPane scroll = SeuTables.scroll(rosterTable);
                    scroll.setPreferredSize(new java.awt.Dimension(900, 320));
                    JOptionPane.showMessageDialog(CourseCatalogPanel.this, scroll,
                            "选课名单 · " + course.getCourseName()
                                    + "（首修 " + firstCount + " · 重修 " + retakeCount + "）",
                            JOptionPane.PLAIN_MESSAGE);
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

    private void showDetails(CourseDto course) {
        if (course == null) {
            return;
        }
        StringBuilder html = new StringBuilder();
        html.append("<html><table cellpadding='3' cellspacing='0'>");
        detailRow(html, "课程编号", course.getCourseId());
        detailRow(html, "教学班", course.getSectionId());
        detailRow(html, "课程名称", course.getCourseName());
        detailRow(html, "课程性质", course.getCourseNature());
        detailRow(html, "授课教师", course.getTeacherName());
        detailRow(html, "开课院系", course.getDepartmentName());
        detailRow(html, "学分", String.valueOf(course.getCredit()));
        detailRow(html, "最大容量 / 已选",
                course.getCapacity() + " / " + course.getEnrolledCount());
        detailRow(html, "首修名额（软池）",
                course.getFirstAttemptEnrolled() + " / " + course.getFirstAttemptCapacity());
        detailRow(html, "重修名额（软池）",
                course.getRetakeEnrolled() + " / " + course.getRetakeCapacity());
        detailRow(html, "学期", course.getSemesterName());
        if (course.getSchedules() == null || course.getSchedules().isEmpty()) {
            detailRow(html, "上课时间", course.getClassTime());
            detailRow(html, "上课地点", blankTo(course.getLocation(), "未填写"));
        } else {
            for (SectionScheduleDto schedule : course.getSchedules()) {
                detailRow(html, "上课时段", scheduleText(schedule));
            }
        }
        detailRow(html, "选课窗口", windowFull(course));
        detailRow(html, "课程简介", blankTo(course.getDescription(), "无"));
        detailRow(html, "选课受众", audienceText(course));
        detailRow(html, "状态", course.isActive() ? "开放" : "停用");
        if (student) {
            detailRow(html, "修读类型", attemptLabel(course.getAttemptType()));
            detailRow(html, "当前状态", stateNote(course));
        }
        html.append("</table></html>");
        JOptionPane.showMessageDialog(this, html.toString(),
                "课程详情 · " + course.getCourseName(), JOptionPane.INFORMATION_MESSAGE);
    }

    private void detailRow(StringBuilder html, String label, String value) {
        html.append("<tr><td valign='top'><b>").append(label)
                .append("</b></td><td>")
                .append(value == null ? "" : value)
                .append("</td></tr>");
    }

    private void editSelected() {
        CourseDto course = selectedRow();
        if (course != null) {
            editSection(course);
        }
    }

    private void editSection(final CourseDto existing) {
        setBusy(true, "正在加载教师与院系……");
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                List<TeacherDto> teachers = academicService.queryTeachers(null);
                List<DepartmentDto> departments = academicService.queryDepartments(false);
                return new Object[]{teachers, departments};
            }

            @Override
            protected void done() {
                try {
                    Object[] options = get();
                    @SuppressWarnings("unchecked")
                    List<TeacherDto> teachers = (List<TeacherDto>) options[0];
                    @SuppressWarnings("unchecked")
                    List<DepartmentDto> departments = (List<DepartmentDto>) options[1];
                    final CourseDto edited = CourseEditorDialog.edit(
                            CourseCatalogPanel.this, existing, teachers, departments);
                    if (edited == null) {
                        setBusy(false, "已取消");
                        return;
                    }
                    runMutation("正在保存……", new IoAction() {
                        @Override
                        public void run() throws IOException {
                            service.saveCourse(edited);
                        }
                    });
                } catch (IllegalArgumentException e) {
                    showError(e.getMessage());
                    setBusy(false, "操作失败");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("加载被中断");
                    setBusy(false, "操作失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "操作失败");
                }
            }
        }.execute();
    }

    private void deleteSelected() {
        final CourseDto course = selectedRow();
        if (course == null || !SeuMessages.confirm(this,
                "确定删除「" + course.getCourseName() + "」这个教学班吗？"
                        + "仍有学生选课时会拒绝删除。")) {
            return;
        }
        runMutation("正在删除……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.deleteCourse(course.getSectionId());
            }
        });
    }

    private CourseDto selectedRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            SeuMessages.info(this, "请先选择一个教学班");
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
    }

    private void runMutation(final String status, final IoAction action) {
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
                    refresh();
                    if (student && onSelected != null) {
                        onSelected.run();
                    }
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
        keyword.setEnabled(!busy);
        natureFilter.setEnabled(!busy);
        includeInactive.setEnabled(!busy);
        selectButton.setEnabled(!busy);
        rosterButton.setEnabled(!busy);
        addButton.setEnabled(!busy);
        editButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy);
    }

    private String[] studentColumns() {
        return new String[]{"课程编号", "课程名称", "性质", "教师", "学分",
                "我的名额", "上课时间", "选课窗口", "状态"};
    }

    private int[] studentWidths() {
        return new int[]{90, 200, 60, 130, 60, 80, 150, 190, 110};
    }

    private String[] staffColumns() {
        return new String[]{"课程编号", "课程名称", "性质", "教师", "开课院系",
                "学分", "容量/已选", "首修/重修", "学期", "上课时间", "状态"};
    }

    private int[] staffWidths() {
        return new int[]{90, 180, 60, 120, 120, 60, 70, 90, 110, 140, 80};
    }

    private String windowShort(CourseDto course) {
        String start = shortTime(course.getSelectionStartTime());
        String end = shortTime(course.getSelectionEndTime());
        if (start == null && end == null) {
            return "不限";
        }
        return start + " ~ " + end;
    }

    private String windowFull(CourseDto course) {
        String start = course.getSelectionStartTime();
        String end = course.getSelectionEndTime();
        if ((start == null || start.trim().isEmpty())
                && (end == null || end.trim().isEmpty())) {
            return "不限";
        }
        return blankTo(start, "?") + " 至 " + blankTo(end, "?");
    }

    private String shortTime(String full) {
        if (full == null || full.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(full.trim(), WINDOW_TIME).format(WINDOW_SHORT);
        } catch (DateTimeParseException e) {
            return full.trim();
        }
    }

    private String audienceText(CourseDto course) {
        List<SectionAudienceDto> audiences = course.getAudiences();
        if (audiences == null || audiences.isEmpty()) {
            return "全校";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < audiences.size(); i++) {
            SectionAudienceDto audience = audiences.get(i);
            if (i > 0) {
                text.append("；");
            }
            if (SectionAudienceDto.SCOPE_ALL.equals(audience.getScopeType())) {
                text.append("全校");
            } else {
                text.append(blankTo(audience.getScopeValue(), "指定院系"));
            }
            text.append(yearText(audience));
        }
        return text.toString();
    }

    private String yearText(SectionAudienceDto audience) {
        Integer min = audience.getYearMin();
        Integer max = audience.getYearMax();
        if (min == null && max == null) {
            return " · 不限年份";
        }
        if (min == null) {
            return " · ≤" + max + " 级入学";
        }
        if (max == null) {
            return " · ≥" + min + " 级入学";
        }
        return " · " + min + "-" + max + " 级入学";
    }

    private void installTooltips(final JTable table) {
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                String tip = null;
                if (row >= 0 && column >= 0) {
                    Object value = table.getValueAt(row, column);
                    if (value != null) {
                        tip = String.valueOf(value);
                    }
                }
                table.setToolTipText(tip);
            }
        });
    }

    private String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? "操作失败" : cause.getMessage();
    }

    private void showError(String message) {
        SeuMessages.error(this, message);
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private interface IoAction {
        void run() throws IOException;
    }
}
