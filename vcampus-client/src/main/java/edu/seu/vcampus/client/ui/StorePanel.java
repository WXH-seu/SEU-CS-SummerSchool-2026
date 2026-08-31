package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Set;

/** Tabbed entry point for the campus store module. */
public final class StorePanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private final int ordersIndex;

    public StorePanel(StoreClientService service, Role role, Set<String> adminScopes) {
        super(new BorderLayout());
        boolean shopper = effectiveRole(role, adminScopes) != SubSystemRole.ADMIN;
        ordersIndex = shopper ? 2 : 1;

        tabs.addTab("商品", new StoreProductPanel(service, role, adminScopes));
        if (shopper) {
            tabs.addTab("购物车", new StoreCartPanel(service, new Runnable() {
                @Override
                public void run() {
                    showOrdersTab();
                }
            }));
        }
        tabs.addTab(shopper ? "我的订单" : "订单管理",
                new StoreOrderPanel(service, role, adminScopes));
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                refreshSelected();
            }
        });
        add(tabs, BorderLayout.CENTER);
    }

    private static SubSystemRole effectiveRole(Role role, Set<String> adminScopes) {
        return SubSystems.effectiveRole(role, adminScopes, SubSystem.STORE);
    }

    private void showOrdersTab() {
        tabs.setSelectedIndex(ordersIndex);
        Component selected = tabs.getSelectedComponent();
        if (selected instanceof StoreOrderPanel) {
            ((StoreOrderPanel) selected).refresh();
        }
    }

    private void refreshSelected() {
        Component selected = tabs.getSelectedComponent();
        if (selected instanceof StoreProductPanel) {
            ((StoreProductPanel) selected).refresh();
        } else if (selected instanceof StoreCartPanel) {
            ((StoreCartPanel) selected).refresh();
        } else if (selected instanceof StoreOrderPanel) {
            ((StoreOrderPanel) selected).refresh();
        }
    }
}
