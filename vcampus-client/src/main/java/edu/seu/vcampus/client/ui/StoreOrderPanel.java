package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.OrderDto;
import edu.seu.vcampus.common.dto.OrderItemDto;
import edu.seu.vcampus.common.dto.OrderQueryRequest;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * 订单页：可按状态 / 分类 / 时间筛选，上方订单列表 + 下方明细，
 * 管理员可更新订单状态。布局与控件统一使用 {@code ui.components} 公共组件。
 */
public final class StoreOrderPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** 订单创建时即为已付款，因此更新状态不再提供「待付款」。 */
    private static final String[] STATUSES = {"已付款", "已发货", "已完成", "已取消"};
    private static final String[] TIME_RANGES = {"全部", "今天", "近7天", "近30天"};

    private final StoreClientService service;
    private final SubSystemRole effectiveRole;
    private final JComboBox<String> statusBox = SeuFields.combo(
            new String[]{"全部", "已付款", "已发货", "已完成", "已取消"});
    private final JComboBox<String> categoryBox = SeuFields.combo(new String[]{"全部"});
    private final JComboBox<String> timeBox = SeuFields.combo(TIME_RANGES);
    private final JButton queryButton = SeuButtons.primary("查询");
    private final JButton refreshButton = SeuButtons.secondary("刷新");
    private final JButton statusButton = SeuButtons.primary("更新状态");
    private final JButton cancelButton = SeuButtons.danger("取消订单");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final DefaultTableModel orderModel = SeuTables.readOnlyModel(new String[]{
            "订单号", "下单账号", "总金额", "状态", "下单时间"});
    private final JTable orderTable = SeuTables.create(orderModel);
    private final DefaultTableModel itemModel = SeuTables.readOnlyModel(new String[]{
            "商品", "单价", "数量", "小计"});
    private final JTable itemTable = SeuTables.create(itemModel);
    private List<OrderDto> orders = new ArrayList<OrderDto>();
    private List<String> categories = new ArrayList<String>();
    private boolean updatingFilters;

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

        JPanel filters = SeuPanels.toolbar();
        filters.add(SeuLabels.field("状态"));
        filters.add(statusBox);
        filters.add(SeuLabels.field("分类"));
        filters.add(categoryBox);
        filters.add(SeuLabels.field("时间"));
        filters.add(timeBox);
        filters.add(queryButton);
        filters.add(refreshButton);
        statusButton.setVisible(administrator);
        filters.add(statusButton);
        cancelButton.setVisible(!administrator);
        filters.add(cancelButton);

        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading(administrator ? "全部订单" : "我的订单", statusLabel),
                BorderLayout.NORTH);
        north.add(filters, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel orderCard = SeuPanels.card();
        // 最小高度取小一些：学生/教师界面顶部还有「校园钱包」条，订单页可用高度更小，
        // 若最小高度之和超过可用高度，分隔条会被夹死，表现为“拖不动”。
        orderCard.setMinimumSize(new Dimension(120, 120));
        orderCard.add(SeuTables.scroll(orderTable), BorderLayout.CENTER);

        JPanel detailCard = SeuPanels.card();
        detailCard.setMinimumSize(new Dimension(120, 90));
        detailCard.add(SeuLabels.subtitle("订单明细"), BorderLayout.NORTH);
        detailCard.add(SeuTables.scroll(itemTable), BorderLayout.CENTER);

        // 用可拖拽的分隔条：默认订单表占约 2/3，用户可自行调整，窗口变矮也不会把订单表挤没。
        final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, orderCard, detailCard);
        split.setResizeWeight(0.65);
        split.setDividerSize(8);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setBorder(null);
        split.setOpaque(false);
        split.addComponentListener(new ComponentAdapter() {
            private boolean initialized;

            @Override
            public void componentResized(ComponentEvent event) {
                if (!initialized && split.getHeight() > 240) {
                    split.setDividerLocation(0.65);
                    initialized = true;
                }
            }
        });
        add(split, BorderLayout.CENTER);
    }

    private void bindActions() {
        queryButton.addActionListener(event -> refresh());
        refreshButton.addActionListener(event -> refresh());
        statusButton.addActionListener(event -> updateSelectedStatus());
        cancelButton.addActionListener(event -> cancelSelectedOrder());
        statusBox.addActionListener(event -> {
            if (!updatingFilters) {
                refresh();
            }
        });
        categoryBox.addActionListener(event -> {
            if (!updatingFilters) {
                refresh();
            }
        });
        timeBox.addActionListener(event -> {
            if (!updatingFilters) {
                refresh();
            }
        });
        orderTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                renderItems();
            }
        });
    }

    public void refresh() {
        setBusy(true, "正在加载订单……");
        final String statusText = selectedValue(statusBox);
        final String categoryText = selectedValue(categoryBox);
        final String timeText = selectedValue(timeBox);
        new SwingWorker<List<OrderDto>, Void>() {
            @Override
            protected List<OrderDto> doInBackground() throws Exception {
                categories = service.queryCategories();
                return service.queryOrders(new OrderQueryRequest(
                        statusText, categoryText, timeText));
            }

            @Override
            protected void done() {
                try {
                    orders = get();
                    rebuildCategoryItems(categoryText);
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

    private String selectedValue(JComboBox<String> box) {
        Object selected = box.getSelectedItem();
        if (selected == null) {
            return null;
        }
        String value = String.valueOf(selected).trim();
        return value.isEmpty() || "全部".equals(value) ? null : value;
    }

    private void rebuildCategoryItems(String previous) {
        updatingFilters = true;
        try {
            List<String> items = new ArrayList<String>();
            items.add("全部");
            for (String category : categories) {
                if (category != null && !category.trim().isEmpty()
                        && !items.contains(category)) {
                    items.add(category);
                }
            }
            categoryBox.setModel(new javax.swing.DefaultComboBoxModel<String>(
                    items.toArray(new String[items.size()])));
            categoryBox.setSelectedItem(previous != null && items.contains(previous)
                    ? previous : "全部");
        } finally {
            updatingFilters = false;
        }
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
            SeuMessages.info(this, "请先选择一笔订单");
            return;
        }
        List<String> options = new ArrayList<String>(Arrays.asList(STATUSES));
        String current = order.getStatusName();
        if (current != null && !options.contains(current)) {
            options.add(0, current);
        }
        Object chosen = JOptionPane.showInputDialog(this, "请选择新状态",
                "更新订单状态", JOptionPane.QUESTION_MESSAGE, null,
                options.toArray(new String[options.size()]), current);
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

    /** 用户取消本人订单：仅「已付款」状态可取消，服务端会退回库存与余额。 */
    private void cancelSelectedOrder() {
        final OrderDto order = selectedOrder();
        if (order == null) {
            SeuMessages.info(this, "请先选择一笔订单");
            return;
        }
        if (!"已付款".equals(order.getStatusName())) {
            SeuMessages.info(this, "仅「已付款」订单可以取消",
                    "当前状态：" + order.getStatusName() + "。\n"
                            + "已发货或已完成的订单请联系管理员处理。");
            return;
        }
        if (!SeuMessages.confirm(this,
                "确定取消订单 " + order.getOrderId() + " 吗？\n"
                        + "取消后将退回商品库存与订单金额 ¥"
                        + StoreFormat.money(order.getTotalAmount()) + " 到你的校园钱包。")) {
            return;
        }
        setBusy(true, "正在取消订单……");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.cancelOrder(order.getOrderId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    SeuMessages.info(StoreOrderPanel.this, "订单已取消",
                            "订单 " + order.getOrderId() + " 已取消，"
                                    + "库存与 ¥" + StoreFormat.money(order.getTotalAmount())
                                    + " 已退回校园钱包。");
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

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        queryButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
        statusButton.setEnabled(!busy);
        cancelButton.setEnabled(!busy);
        statusBox.setEnabled(!busy);
        categoryBox.setEnabled(!busy);
        timeBox.setEnabled(!busy);
    }

    private String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? "操作失败" : cause.getMessage();
    }

    private void showError(String message) {
        SeuMessages.error(this, message);
    }
}
