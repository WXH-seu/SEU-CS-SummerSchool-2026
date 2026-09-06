package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.LibraryClientService;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * 图书馆入口：书目检索；学生和教师看「我的借阅」，管理员看「全部借阅」。
 */
public final class LibraryPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JTabbedPane tabs = new JTabbedPane();
    private final LibraryCatalogPanel catalogPanel;
    private final LibraryBorrowPanel borrowPanel;

    public LibraryPanel(LibraryClientService service, SubSystemRole effectiveRole) {
        super(new BorderLayout());
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        setOpaque(false);
        boolean admin = effectiveRole == SubSystemRole.ADMIN;
        catalogPanel = new LibraryCatalogPanel(service, effectiveRole, new Runnable() {
            @Override
            public void run() {
                showBorrowTab();
            }
        });
        tabs.addTab("书目", catalogPanel);
        borrowPanel = new LibraryBorrowPanel(service, new Runnable() {
            @Override
            public void run() {
                catalogPanel.refresh();
            }
        }, admin);
        tabs.addTab(admin ? "全部借阅" : "我的借阅", borrowPanel);
        add(tabs, BorderLayout.CENTER);
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                refreshSelected();
            }
        });
    }

    private void showBorrowTab() {
        tabs.setSelectedIndex(1);
        borrowPanel.refresh();
    }

    private void refreshSelected() {
        Component selected = tabs.getSelectedComponent();
        if (selected instanceof LibraryCatalogPanel) {
            ((LibraryCatalogPanel) selected).refresh();
        } else if (selected instanceof LibraryBorrowPanel) {
            ((LibraryBorrowPanel) selected).refresh();
        }
    }
}
