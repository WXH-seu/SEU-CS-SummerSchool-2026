package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.CartItemDto;
import edu.seu.vcampus.common.dto.OrderDto;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * 购物车页：修改数量、移除与整单结算。布局与控件统一使用公共组件。
 */
public final class StoreCartPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final StoreClientService service;
    private final Runnable onOrderCreated;
    private final JButton changeButton = SeuButtons.secondary("修改数量");
    private final JButton removeButton = SeuButtons.danger("移除");
    private final JButton checkoutButton = SeuButtons.primary("结算下单");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final JLabel totalLabel = new JLabel("合计：¥0.00");
    private final DefaultTableModel tableModel = SeuTables.readOnlyModel(new String[]{
            "商品编号", "名称", "单价", "数量", "小计", "剩余库存"});
    private final JTable table = SeuTables.create(tableModel);
    private List<CartItemDto> rows = new ArrayList<CartItemDto>();

    public StoreCartPanel(StoreClientService service, Runnable onOrderCreated) {
        super(new BorderLayout(0, SeuTheme.SPACE_MD));
        this.service = service;
        this.onOrderCreated = onOrderCreated;
        setBackground(SeuTheme.PAGE_BG);
        setBorder(SeuTheme.pageBorder());
        buildUi();
        bindActions();
        refresh();
    }

    private void buildUi() {
        totalLabel.setFont(SeuTheme.bodyFont());
        totalLabel.setForeground(SeuTheme.TEXT);

        JPanel actions = SeuPanels.toolbar();
        actions.add(changeButton);
        actions.add(removeButton);
        actions.add(checkoutButton);
        actions.add(totalLabel);

        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading("我的购物车", statusLabel), BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel card = SeuPanels.card();
        card.add(SeuTables.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
    }

    private void bindActions() {
        changeButton.addActionListener(event -> changeSelectedQuantity());
        removeButton.addActionListener(event -> removeSelected());
        checkoutButton.addActionListener(event -> checkOut());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    changeSelectedQuantity();
                }
            }
        });
    }

    public void refresh() {
        setBusy(true, "正在加载购物车……");
        new SwingWorker<List<CartItemDto>, Void>() {
            @Override
            protected List<CartItemDto> doInBackground() throws Exception {
                return service.queryCart();
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    renderRows();
                    statusLabel.setText("共 " + rows.size() + " 种商品");
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
        tableModel.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;
        for (CartItemDto item : rows) {
            total = total.add(item.getSubtotal());
            tableModel.addRow(new Object[]{
                    item.getProductId(),
                    item.getProductName(),
                    StoreFormat.money(item.getUnitPrice()),
                    Integer.valueOf(item.getQuantity()),
                    StoreFormat.money(item.getSubtotal()),
                    Integer.valueOf(item.getStock())
            });
        }
        totalLabel.setText("合计：¥" + StoreFormat.money(total));
    }

    private void changeSelectedQuantity() {
        final CartItemDto item = selectedItem();
        if (item == null) {
            return;
        }
        String input = JOptionPane.showInputDialog(this,
                "请输入新数量（1-" + Math.max(1, item.getStock())
                        + "，输入 0 表示移除）：",
                String.valueOf(item.getQuantity()));
        if (input == null) {
            return;
        }
        final int quantity;
        try {
            quantity = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            showError("数量必须是整数");
            return;
        }
        if (quantity < 0) {
            showError("数量不能为负数");
            return;
        }
        runMutation("正在修改数量……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.updateCart(item.getProductId(), quantity);
            }
        });
    }

    private void removeSelected() {
        final CartItemDto item = selectedItem();
        if (item == null || !SeuMessages.confirm(this,
                "确定将「" + item.getProductName() + "」移出购物车吗？")) {
            return;
        }
        runMutation("正在移除……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.updateCart(item.getProductId(), 0);
            }
        });
    }

    private void checkOut() {
        if (rows.isEmpty()) {
            SeuMessages.info(this, "购物车为空，请先添加商品");
            return;
        }
        if (!SeuMessages.confirm(this,
                "确认对购物车中的 " + rows.size()
                        + " 种商品下单吗？下单后将扣减库存。")) {
            return;
        }
        setBusy(true, "正在创建订单……");
        new SwingWorker<OrderDto, Void>() {
            @Override
            protected OrderDto doInBackground() throws Exception {
                return service.createOrder();
            }

            @Override
            protected void done() {
                try {
                    OrderDto order = get();
                    SeuMessages.info(StoreCartPanel.this, "下单成功",
                            "订单号：" + order.getOrderId()
                                    + "\n金额：¥" + StoreFormat.money(order.getTotalAmount()));
                    onOrderCreated.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("下单被中断");
                    setBusy(false, "下单失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "下单失败");
                }
            }
        }.execute();
    }

    private CartItemDto selectedItem() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            SeuMessages.info(this, "请先选择一件商品");
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
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
        changeButton.setEnabled(!busy);
        removeButton.setEnabled(!busy);
        checkoutButton.setEnabled(!busy);
    }

    private String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? "操作失败" : cause.getMessage();
    }

    private void showError(String message) {
        SeuMessages.error(this, message);
    }

    private interface IoAction {
        void run() throws IOException;
    }
}
