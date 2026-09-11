package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.OrderItemDto;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * 订单页：上方订单列表 + 下方选中订单的明细，管理员可更新订单状态。
 * 布局与控件统一使用 {@code ui.components} 公共组件。
 */
public final class StoreOrderPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final String[] STATUSES = {"待付款", "已付款", "已发货", "已完成", "已取消"};

    private final StoreClientService service;
    private final SubSystemRole effectiveRole;
    private final JButton refreshButton = SeuButtons.secondary("刷新");
    private final JButton statusButton = SeuButtons.primary("更新状态");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final DefaultTableModel orderModel = SeuTables.readOnlyModel(new String[]{
            "订单号", "下单账号", "总金额", "状态", "下单时间"});
    private final JTable orderTable = SeuTables.create(orderModel);
    private final DefaultTableModel itemModel = SeuTables.readOnlyModel(new String[]{
            "商品", "单价", "数量", "小计"});
    private final JTable itemTable = SeuTables.create(itemModel);
    private List<OrderDto> orders = new ArrayList<OrderDto>();

    public StoreOrderPanel(StoreClientService service, SubSystemRole effectiveRole) {
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
        refresh();
    }

    private void buildUi() {
        boolean administrator = effectiveRole == SubSystemRole.ADMIN;

        JPanel actions = SeuPanels.toolbar();
        actions.add(refreshButton);
        statusButton.setVisible(administrator);
        actions.add(statusButton);

        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading(administrator ? "全部订单" : "我的订单", statusLabel),
                BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        center.setOpaque(false);

        JPanel orderCard = SeuPanels.card();
        orderCard.add(SeuTables.scroll(orderTable), BorderLayout.CENTER);
        center.add(orderCard, BorderLayout.CENTER);

        // 明细卡片：限制高度，避免 BorderLayout 将订单表挤没。
        JPanel detailCard = SeuPanels.card();
        detailCard.setPreferredSize(new Dimension(100, 240));
        detailCard.setMinimumSize(new Dimension(100, 180));
        detailCard.add(SeuLabels.subtitle("订单明细"), BorderLayout.NORTH);
        detailCard.add(SeuTables.scroll(itemTable), BorderLayout.CENTER);
        center.add(detailCard, BorderLayout.SOUTH);

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
        orderModel.setDataVector(data, new String[]{
                "订单号", "下单账号", "总金额", "状态", "下单时间"});
        if (!orders.isEmpty()) {
            orderTable.clearSelection();
            orderTable.setRowSelectionInterval(0, 0);
            renderItems();
        } else {
            orderTable.clearSelection();
            itemModel.setRowCount(0);
        }
    }

    private void renderItems() {
        itemModel.setRowCount(0);
        OrderDto order = selectedOrder();
        if (order == null) {
            return;
        }
        for (OrderItemDto item : order.getItems()) {
            itemModel.addRow(new Object[]{
                    item.getProductName(),
                    StoreFormat.money(item.getUnitPrice()),
                    Integer.valueOf(item.getQuantity()),
                    StoreFormat.money(item.getSubtotal())
            });
        }
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
        SeuMessages.error(this, message);
    }
}
