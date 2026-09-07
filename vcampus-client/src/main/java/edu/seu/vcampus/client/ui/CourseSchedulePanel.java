package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.CourseClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.CourseEnrollmentDto;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** 学生个人课表。 */
final class CourseSchedulePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final CourseClientService service;
    private final Runnable onChange;
    private final JButton dropButton = SeuButtons.danger("退课");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final DefaultTableModel tableModel = SeuTables.readOnlyModel(new String[]{
            "课程编号", "课程名称", "性质", "教师", "学分", "上课时间", "地点", "选课时间"});
    private final JTable table = SeuTables.create(tableModel);
    private List<CourseEnrollmentDto> rows = new ArrayList<CourseEnrollmentDto>();

    CourseSchedulePanel(CourseClientService service, Runnable onChange) {
        super(new BorderLayout(0, SeuTheme.SPACE_MD));
        this.service = service;
        this.onChange = onChange;
        setBackground(SeuTheme.PAGE_BG);
        setBorder(SeuTheme.pageBorder());
        buildUi();
        bindActions();
        refresh();
    }

    private void buildUi() {
        JPanel toolbar = SeuPanels.toolbar();
        toolbar.add(dropButton);
        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading("我的课表", statusLabel), BorderLayout.NORTH);
        north.add(toolbar, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        JPanel card = SeuPanels.card();
        card.add(SeuTables.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
    }

    private void bindActions() {
        dropButton.addActionListener(event -> dropSelected());
    }

    void refresh() {
        statusLabel.setText("正在加载……");
        new SwingWorker<List<CourseEnrollmentDto>, Void>() {
            @Override
            protected List<CourseEnrollmentDto> doInBackground() throws Exception {
                return service.querySchedule();
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    renderRows();
                    statusLabel.setText("共 " + rows.size() + " 门课程");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("课表加载被中断");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    statusLabel.setText("课表加载失败");
                }
            }
        }.execute();
    }

    private void renderRows() {
        tableModel.setRowCount(0);
        for (CourseEnrollmentDto enrollment : rows) {
            tableModel.addRow(new Object[]{enrollment.getCourseId(),
                    enrollment.getCourseName(), enrollment.getCourseNature(),
                    enrollment.getTeacherName(), enrollment.getCredit(),
                    enrollment.getClassTime(), enrollment.getLocation(),
                    enrollment.getEnrollTime()});
        }
    }

    private void dropSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            SeuMessages.info(this, "请先在课表中选择一门课程");
            return;
        }
        final CourseEnrollmentDto enrollment =
                rows.get(table.convertRowIndexToModel(viewRow));
        if (!SeuMessages.confirm(this,
                "确定退选「" + enrollment.getCourseName() + "」吗？")) {
            return;
        }
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.dropCourse(enrollment.getEnrollmentId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refresh();
                    if (onChange != null) {
                        onChange.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("退课被中断");
                    setBusy(false);
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        dropButton.setEnabled(!busy);
    }

    private String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? "操作失败" : cause.getMessage();
    }

    private void showError(String message) {
        SeuMessages.error(this, message);
    }
}
