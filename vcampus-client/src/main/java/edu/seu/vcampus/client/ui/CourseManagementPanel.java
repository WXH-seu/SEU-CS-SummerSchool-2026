package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.AcademicClientService;
import edu.seu.vcampus.client.service.CourseClientService;
import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseEnrollmentDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Course catalog, selection, schedule and maintenance page. */
public final class CourseManagementPanel extends JPanel {
    private final CourseClientService service;
    private final AcademicClientService academicService;
    private final SubSystemRole effectiveRole;

    private final JTextField keyword = new JTextField(10);
    private final JTextField semester = new JTextField(8);
    private final JTextField departmentId = new JTextField(7);
    private final JCheckBox activeOnly = new JCheckBox("只看开放课程");
    private final JButton searchButton = new JButton("查询");
    private final JButton selectButton = new JButton("选课");
    private final JButton addButton = new JButton("新增");
    private final JButton editButton = new JButton("编辑");
    private final JButton deleteButton = new JButton("删除");
    private final JLabel statusLabel = new JLabel("准备就绪");

    private final DefaultTableModel courseModel = readOnlyModel();
    private final JTable courseTable = new JTable(courseModel);
    private final DefaultTableModel scheduleModel = readOnlyModel();
    private final JTable scheduleTable = new JTable(scheduleModel);
    private final JButton dropButton = new JButton("退课");
    private final JLabel scheduleStatus = new JLabel("准备就绪");

    private List<CourseDto> courseRows = new ArrayList<CourseDto>();
    private List<CourseEnrollmentDto> scheduleRows = new ArrayList<CourseEnrollmentDto>();

    public CourseManagementPanel(CourseClientService service,
                                 AcademicClientService academicService,
                                 Role role, Set<String> adminScopes) {
        super(new BorderLayout(0, 12));
        this.service = service;
        this.academicService = academicService;
        this.effectiveRole = SubSystems.effectiveRole(
                role, adminScopes, SubSystem.COURSE);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        buildUi();
        bindActions();
        refreshCourses();
        if (effectiveRole == SubSystemRole.STUDENT) {
            refreshSchedule();
        }
    }

    private void buildUi() {
        JPanel heading = new JPanel(new BorderLayout());
        JLabel title = new JLabel("选课系统");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        heading.add(title, BorderLayout.WEST);
        heading.add(statusLabel, BorderLayout.EAST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(new JLabel("关键字"));
        filters.add(keyword);
        filters.add(new JLabel("学期"));
        filters.add(semester);
        filters.add(new JLabel("院系"));
        filters.add(departmentId);
        filters.add(activeOnly);
        filters.add(searchButton);
        filters.add(selectButton);
        filters.add(addButton);
        filters.add(editButton);
        filters.add(deleteButton);

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.add(heading, BorderLayout.NORTH);
        north.add(filters, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        courseTable.setFillsViewportHeight(true);
        courseTable.setAutoCreateRowSorter(true);
        tabs.addTab("课程目录", new JScrollPane(courseTable));
        if (effectiveRole == SubSystemRole.STUDENT) {
            JPanel schedulePage = new JPanel(new BorderLayout(0, 8));
            schedulePage.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JPanel scheduleToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            scheduleToolbar.add(dropButton);
            scheduleToolbar.add(scheduleStatus);
            schedulePage.add(scheduleToolbar, BorderLayout.NORTH);
            scheduleTable.setFillsViewportHeight(true);
            scheduleTable.setAutoCreateRowSorter(true);
            schedulePage.add(new JScrollPane(scheduleTable), BorderLayout.CENTER);
            tabs.addTab("我的课表", schedulePage);
        }
        add(tabs, BorderLayout.CENTER);

        boolean student = effectiveRole == SubSystemRole.STUDENT;
        boolean administrator = effectiveRole == SubSystemRole.ADMIN;
        selectButton.setVisible(student);
        dropButton.setVisible(student);
        addButton.setVisible(administrator);
        editButton.setVisible(administrator);
        deleteButton.setVisible(administrator);
        activeOnly.setSelected(student);
        activeOnly.setVisible(student);
    }

    private void bindActions() {
        searchButton.addActionListener(event -> refreshCourses());
        keyword.addActionListener(event -> refreshCourses());
        semester.addActionListener(event -> refreshCourses());
        departmentId.addActionListener(event -> refreshCourses());
        selectButton.addActionListener(event -> selectSelectedCourse());
        dropButton.addActionListener(event -> dropSelectedEnrollment());
        addButton.addActionListener(event -> editCourse(null));
        editButton.addActionListener(event -> editSelectedCourse());
        deleteButton.addActionListener(event -> deleteSelectedCourse());
    }

    private CourseQueryRequest currentQuery() {
        return new CourseQueryRequest(keyword.getText(), departmentId.getText(),
                null, semester.getText(), activeOnly.isSelected());
    }

    private void refreshCourses() {
        setBusy(true, "正在加载课程……");
        final CourseQueryRequest query = currentQuery();
        new SwingWorker<List<CourseDto>, Void>() {
            @Override
            protected List<CourseDto> doInBackground() throws Exception {
                return service.queryCourses(query);
            }

            @Override
            protected void done() {
                try {
                    courseRows = get();
                    renderCourses();
                    statusLabel.setText("共 " + courseRows.size() + " 门课程");
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

    private void renderCourses() {
        courseModel.setRowCount(0);
        courseModel.setColumnIdentifiers(new String[]{"课程编号", "课程名称", "教师", "院系",
                "学分", "容量", "已选人数", "学期", "上课时间", "地点", "状态"});
        for (CourseDto course : courseRows) {
            courseModel.addRow(new Object[]{course.getCourseId(), course.getCourseName(),
                    course.getTeacherName(), course.getDepartmentName(), course.getCredit(),
                    course.getCapacity(), course.getEnrolledCount(), course.getSemesterName(),
                    course.getClassTime(), course.getLocation(),
                    course.isActive() ? "开放" : "停用"});
        }
    }

    private void refreshSchedule() {
        scheduleStatus.setText("正在加载课表……");
        new SwingWorker<List<CourseEnrollmentDto>, Void>() {
            @Override
            protected List<CourseEnrollmentDto> doInBackground() throws Exception {
                return service.querySchedule();
            }

            @Override
            protected void done() {
                try {
                    scheduleRows = get();
                    renderSchedule();
                    scheduleStatus.setText("共 " + scheduleRows.size() + " 门课程");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("课表加载被中断");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    scheduleStatus.setText("课表加载失败");
                }
            }
        }.execute();
    }

    private void renderSchedule() {
        scheduleModel.setRowCount(0);
        scheduleModel.setColumnIdentifiers(new String[]{"课程编号", "课程名称", "教师",
                "学分", "上课时间", "地点", "选课时间"});
        for (CourseEnrollmentDto enrollment : scheduleRows) {
            scheduleModel.addRow(new Object[]{enrollment.getCourseId(),
                    enrollment.getCourseName(), enrollment.getTeacherName(),
                    enrollment.getCredit(), enrollment.getClassTime(), enrollment.getLocation(),
                    enrollment.getEnrollTime()});
        }
    }

    private void selectSelectedCourse() {
        final CourseDto course = selectedCourse();
        if (course == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "确定选择课程「" + course.getCourseName() + "」吗？",
                "确认选课", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        runMutation("正在选课……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.selectCourse(course.getCourseId());
            }
        });
    }

    private void dropSelectedEnrollment() {
        int viewRow = scheduleTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "请先在课表中选择一门课程", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final CourseEnrollmentDto enrollment =
                scheduleRows.get(scheduleTable.convertRowIndexToModel(viewRow));
        if (JOptionPane.showConfirmDialog(this,
                "确定退选课程「" + enrollment.getCourseName() + "」吗？",
                "确认退课", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        runMutation("正在退课……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.dropCourse(enrollment.getEnrollmentId());
            }
        });
    }

    private void editSelectedCourse() {
        CourseDto course = selectedCourse();
        if (course != null) {
            editCourse(course);
        }
    }

    private void editCourse(final CourseDto existing) {
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
                            CourseManagementPanel.this, existing, teachers, departments);
                    if (edited == null) {
                        setBusy(false, "已取消");
                        return;
                    }
                    runMutation("正在保存课程……", new IoAction() {
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

    private void deleteSelectedCourse() {
        final CourseDto course = selectedCourse();
        if (course == null || JOptionPane.showConfirmDialog(this,
                "确定删除课程「" + course.getCourseName() + "」吗？",
                "确认删除", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        runMutation("正在删除课程……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.deleteCourse(course.getCourseId());
            }
        });
    }

    private CourseDto selectedCourse() {
        int viewRow = courseTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一门课程", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return courseRows.get(courseTable.convertRowIndexToModel(viewRow));
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
                    refreshCourses();
                    if (effectiveRole == SubSystemRole.STUDENT) {
                        refreshSchedule();
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
        selectButton.setEnabled(!busy);
        addButton.setEnabled(!busy);
        editButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy);
        dropButton.setEnabled(!busy);
    }

    private DefaultTableModel readOnlyModel() {
        return new DefaultTableModel() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
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
