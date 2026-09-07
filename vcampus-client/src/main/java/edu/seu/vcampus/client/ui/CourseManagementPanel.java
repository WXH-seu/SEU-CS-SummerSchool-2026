package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.AcademicClientService;
import edu.seu.vcampus.client.service.CourseClientService;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * 选课入口：开课目录；学生另有「我的课表」页。页面按门户公共组件规范构建，
 * 视觉与图书馆模块保持一致。
 */
public final class CourseManagementPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JTabbedPane tabs = new JTabbedPane();
    private final CourseCatalogPanel catalogPanel;
    private CourseSchedulePanel schedulePanel;

    public CourseManagementPanel(CourseClientService service,
                                 AcademicClientService academicService,
                                 SubSystemRole effectiveRole) {
        super(new BorderLayout());
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        setOpaque(false);
        catalogPanel = new CourseCatalogPanel(service, academicService, effectiveRole,
                new Runnable() {
                    @Override
                    public void run() {
                        showScheduleTab();
                    }
                });
        tabs.addTab("开课目录", catalogPanel);
        if (effectiveRole == SubSystemRole.STUDENT) {
            schedulePanel = new CourseSchedulePanel(service, new Runnable() {
                @Override
                public void run() {
                    catalogPanel.refresh();
                }
            });
            tabs.addTab("我的课表", schedulePanel);
        }
        add(tabs, BorderLayout.CENTER);
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                refreshSelected();
            }
        });
    }

    private void showScheduleTab() {
        if (schedulePanel != null) {
            tabs.setSelectedIndex(1);
            schedulePanel.refresh();
        }
    }

    private void refreshSelected() {
        Component selected = tabs.getSelectedComponent();
        if (selected instanceof CourseCatalogPanel) {
            ((CourseCatalogPanel) selected).refresh();
        } else if (selected instanceof CourseSchedulePanel) {
            ((CourseSchedulePanel) selected).refresh();
        }
    }
}
