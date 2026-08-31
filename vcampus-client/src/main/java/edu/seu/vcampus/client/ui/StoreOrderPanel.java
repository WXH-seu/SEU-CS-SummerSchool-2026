package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.OrderItemDto;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Order list page with per‑order lines and admin status management. */
public final class StoreOrderPanel extends JPanel {
    private static final long serialVersionUID = 1L;  // 【添加】修复序列化警告
    
    private static final String[] STATUSES = {"待付款", "已付款", "已发货", "已完成", "已取消"};  // 【修复】大括号改成小括号
    
    private final StoreClientService service;
    private final Role role;
    private final Set<String> adminScopes;
    private final JButton refreshButton = new JButton("刷新");
    private final JButton statusButton = new JButton("更新状态");
    private final JLabel statusLabel = new JLabel("准备就绪");

    private final DefaultTableModel orderModel;
    private final JTable orderTable;

    private final DefaultTableModel itemModel;
    private final JTable itemTable;

    private List<OrderDto> orders = new ArrayList<OrderDto>();

    public StoreOrderPanel(StoreClientService service, Role role, Set<String> adminScopes) {
        super(new BorderLayout(0, 12));
        this.service = service;
        this.role = role;
        this.adminScopes = adminScopes;
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        orderModel = new DefaultTableModel(
            new String[]{"订单号", "下单账号", "总金额", "状态", "下单时间"}, 0
        ) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        orderTable = new JTable(orderModel);

        itemModel = new DefaultTableModel(
            new String[]{"商品", "单价", "数量", "小计"}, 0
        ) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        itemTable = new JTable(itemModel);

        buildUi();
        bindActions();
        refresh();
    }

    private void buildUi() {
        JPanel heading = new JPanel(new BorderLayout());
        JLabel title = new JLabel(effectiveRole() == SubSystemRole.ADMIN ? "全部订单" : "我的订单");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        heading.add(title, BorderLayout.WEST);
        heading.add(statusLabel, BorderLayout.EAST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(refreshButton);
        statusButton.setVisible(effectiveRole() == SubSystemRole.ADMIN);
        actions.add(statusButton);

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.add(heading, BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));

        JPanel orderContainer = new JPanel();
        orderContainer.setLayout(new BorderLayout());
        orderContainer.setPreferredSize(new Dimension(100, 240));

        orderTable.setFillsViewportHeight(true);
        orderTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        orderTable.setRowHeight(26);
        orderTable.setFont(orderTable.getFont().deriveFont(14f));

        JScrollPane orderScroll = new JScrollPane(orderTable);
        orderScroll.setViewportView(orderTable);
        orderContainer.add(orderScroll, BorderLayout.CENTER);

        center.add(orderContainer, BorderLayout.CENTER);

        JPanel items = new JPanel(new BorderLayout(0, 6));
        items.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JLabel itemsTitle = new JLabel("订单明细");
        itemsTitle.setFont(itemsTitle.getFont().deriveFont(Font.BOLD, 14f));
        items.add(itemsTitle, BorderLayout.NORTH);

        itemTable.setFillsViewportHeight(true);
        itemTable.setRowHeight(24);
        itemTable.setFont(itemTable.getFont().deriveFont(14f));
        items.add(new JScrollPane(itemTable), BorderLayout.CENTER);
        center.add(items, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);
    }

    private void bindActions() {
        refreshButton.addActionListener(event -> refresh());
        statusButton.addActionListener(event -> updateSelectedStatus());
        orderTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                renderItems();
            }
        });
    }

    public void refresh() {
        setBusy(true, "正在加载订单……");
        new SwingWorker<List<OrderDto>, Void>() {
            @Override
            protected List<OrderDto> doInBackground() throws Exception {
                return service.queryOrders();
            }

            @Override
            protected void done() {
                try {
                    orders = get();
                    renderOrders();
                    statusLabel.setText("共 " + orders.size() + " 笔订单");
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

    private void renderOrders() {
        Object[][] data = new Object[orders.size()][5];
        for (int i = 0; i < orders.size(); i++) {
            OrderDto order = orders.get(i);
            data[i] = new Object[]{
                    order.getOrderId(),
                    order.getUserId(),
                    StoreFormat.money(order.getTotalAmount()),
                    order.getStatusName(),
                    order.getOrderTime()
            };
        }
        orderModel.setDataVector(data, new String[]{"订单号", "下单账号", "总金额", "状态", "下单时间"});

        orderTable.revalidate();
        orderTable.repaint();
        orderTable.updateUI();

        if (!orders.isEmpty()) {
            orderTable.clearSelection();
            orderTable.setRowSelectionInterval(0, 0);
            renderItems();
        } else {
            orderTable.clearSelection();
            itemModel.setRowCount(0);
            itemTable.revalidate();
            itemTable.repaint();
        }
    }

    private void renderItems() {
        itemModel.setRowCount(0);
        OrderDto order = selectedOrder();
        if (order == null) {
            itemTable.revalidate();
            itemTable.repaint();
            return;
        }

        Object[][] data = new Object[order.getItems().size()][4];
        for (int i = 0; i < order.getItems().size(); i++) {
            OrderItemDto item = order.getItems().get(i);
            data[i] = new Object[]{
                    item.getProductName(),
                    StoreFormat.money(item.getUnitPrice()),
                    item.getQuantity(),
                    StoreFormat.money(item.getSubtotal())
            };
        }
        itemModel.setDataVector(data, new String[]{"商品", "单价", "数量", "小计"});

        itemTable.revalidate();
        itemTable.repaint();
        itemTable.updateUI();
    }

    private void updateSelectedStatus() {
        final OrderDto order = selectedOrder();
        if (order == null) {
            return;
        }
        Object chosen = JOptionPane.showInputDialog(this, "请选择新状态",
                "更新订单状态", JOptionPane.QUESTION_MESSAGE, null,
                STATUSES, order.getStatusName());
        if (chosen == null) {
            return;
        }
        final String newStatus = String.valueOf(chosen);
        setBusy(true, "正在更新状态……");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.updateOrderStatus(order.getOrderId(), newStatus);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refresh();
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

    private OrderDto selectedOrder() {
        int viewRow = orderTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = orderTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= orders.size()) {
            return null;
        }
        return orders.get(modelRow);
    }

    private SubSystemRole effectiveRole() {
        return SubSystems.effectiveRole(role, adminScopes, SubSystem.STORE);
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        refreshButton.setEnabled(!busy);
        statusButton.setEnabled(!busy);
    }

    private String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? "操作失败" : cause.getMessage();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }
}
