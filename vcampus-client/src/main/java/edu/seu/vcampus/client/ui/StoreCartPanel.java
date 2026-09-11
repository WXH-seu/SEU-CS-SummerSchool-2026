package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.CartItemDto;
import edu.seu.vcampus.common.dto.OrderCreateRequest;
import edu.seu.vcampus.common.dto.OrderDto;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * 购物车页：勾选商品、修改数量、移除与结算下单。
 * 结算前先弹收银台确认支付，取消则不发请求、不生成订单、不扣库存。
 */
public final class StoreCartPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final StoreClientService service;
    private final Runnable onOrderCreated;
    private final JButton selectAllButton = SeuButtons.link("全选 / 全不选");
    private final JButton changeButton = SeuButtons.secondary("修改数量");
    private final JButton removeButton = SeuButtons.danger("移除");
    private final JButton checkoutButton = SeuButtons.primary("结算下单");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final JLabel totalLabel = new JLabel("合计（已选 0 种）：¥0.00");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"选择", "商品编号", "名称", "单价", "数量", "小计", "剩余库存"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : Object.class;
        }
    };
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
        actions.add(selectAllButton);
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
        selectAllButton.addActionListener(event -> toggleSelectAll());
        changeButton.addActionListener(event -> changeSelectedQuantity());
        removeButton.addActionListener(event -> removeChecked());
        checkoutButton.addActionListener(event -> checkOut());
        tableModel.addTableModelListener(event -> updateTotal());
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
        for (CartItemDto item : rows) {
            tableModel.addRow(new Object[]{
                    Boolean.TRUE,
                    item.getProductId(),
                    item.getProductName(),
                    StoreFormat.money(item.getUnitPrice()),
                    Integer.valueOf(item.getQuantity()),
                    StoreFormat.money(item.getSubtotal()),
                    Integer.valueOf(item.getStock())
            });
        }
        updateTotal();
    }

    private void updateTotal() {
        if (tableModel.getRowCount() != rows.size()) {
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        int selected = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                total = total.add(rows.get(i).getSubtotal());
                selected++;
            }
        }
        totalLabel.setText("合计（已选 " + selected + " 种）：¥" + StoreFormat.money(total));
    }

    private void toggleSelectAll() {
        boolean allSelected = true;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (!Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                allSelected = false;
                break;
            }
        }
        boolean next = !allSelected;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(Boolean.valueOf(next), i, 0);
        }
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

    private void removeChecked() {
        final List<String> checkedProductIds = new ArrayList<String>();
        final List<String> checkedNames = new ArrayList<String>();
        for (int i = 0; i < rows.size(); i++) {
            if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                checkedProductIds.add(rows.get(i).getProductId());
                checkedNames.add(rows.get(i).getProductName());
            }
        }
        if (checkedProductIds.isEmpty()) {
            SeuMessages.info(this, "请先勾选要移除的商品");
            return;
        }
        if (!SeuMessages.confirm(this,
                "确定将勾选的 " + checkedNames.size()
                        + " 种商品（" + String.join("、", checkedNames) + "）移出购物车吗？")) {
            return;
        }
        setBusy(true, "正在移除……");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (String productId : checkedProductIds) {
                    service.updateCart(productId, 0);
                }
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

    private void checkOut() {
        if (rows.isEmpty()) {
            SeuMessages.info(this, "购物车为空，请先添加商品");
            return;
        }
        final List<String> selectedProductIds = new ArrayList<String>();
        final List<String> selectedLines = new ArrayList<String>();
        BigDecimal amount = BigDecimal.ZERO;
        for (int i = 0; i < rows.size(); i++) {
            if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                CartItemDto item = rows.get(i);
                selectedProductIds.add(item.getProductId());
                selectedLines.add(item.getProductName() + "  ×" + item.getQuantity()
                        + "    ¥" + StoreFormat.money(item.getSubtotal()));
                amount = amount.add(item.getSubtotal());
            }
        }
        if (selectedProductIds.isEmpty()) {
            SeuMessages.info(this, "请先勾选要结算的商品");
            return;
        }
        final BigDecimal total = amount;
        // 先取最新余额，再弹结算预览：余额不足当场拦截，不发下单请求。
        setBusy(true, "正在获取账户余额……");
        new SwingWorker<BigDecimal, Void>() {
            @Override
            protected BigDecimal doInBackground() throws Exception {
                return service.queryBalance();
            }

            @Override
            protected void done() {
                try {
                    BigDecimal balance = get();
                    setBusy(false, statusLabel.getText());
                    showCheckoutPreview(selectedProductIds, selectedLines, total, balance);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("获取余额被中断");
                    setBusy(false, "结算失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "结算失败");
                }
            }
        }.execute();
    }

    /** 结算预览：所选商品、合计、当前余额与支付后余额；余额不足直接提示并留在购物车。 */
    private void showCheckoutPreview(List<String> selectedProductIds,
                                     List<String> selectedLines,
                                     BigDecimal total, BigDecimal balance) {
        if (balance.compareTo(total) < 0) {
            SeuMessages.error(this, "余额不足，结算失败\n"
                    + "应付金额：¥" + StoreFormat.money(total) + "\n"
                    + "当前余额：¥" + StoreFormat.money(balance) + "\n"
                    + "还差：¥" + StoreFormat.money(total.subtract(balance)) + "\n\n"
                    + "请先点击右上角「充值」完成充值；购物车商品与库存保持不变。");
            return;
        }
        JTextArea itemArea = new JTextArea(String.join("\n", selectedLines));
        itemArea.setEditable(false);
        itemArea.setFont(SeuTheme.bodyFont());
        itemArea.setBackground(SeuTheme.SURFACE);
        itemArea.setForeground(SeuTheme.TEXT);
        itemArea.setBorder(SeuTheme.empty(SeuTheme.SPACE_SM, SeuTheme.SPACE_SM,
                SeuTheme.SPACE_SM, SeuTheme.SPACE_SM));
        JScrollPane itemScroll = new JScrollPane(itemArea);
        itemScroll.setPreferredSize(new Dimension(400,
                Math.min(180, 26 * selectedLines.size() + 24)));

        JPanel summary = new JPanel(new GridLayout(3, 2, 8, 4));
        summary.setOpaque(false);
        summary.add(SeuLabels.field("合计金额"));
        summary.add(SeuLabels.field("¥" + StoreFormat.money(total)));
        summary.add(SeuLabels.field("当前余额"));
        summary.add(SeuLabels.field("¥" + StoreFormat.money(balance)));
        summary.add(SeuLabels.field("支付后余额"));
        summary.add(SeuLabels.field("¥" + StoreFormat.money(balance.subtract(total))));

        JPanel content = new JPanel(new BorderLayout(0, SeuTheme.SPACE_SM));
        content.add(SeuLabels.subtitle("所选商品（" + selectedLines.size() + " 种）"),
                BorderLayout.NORTH);
        content.add(itemScroll, BorderLayout.CENTER);
        content.add(summary, BorderLayout.SOUTH);

        if (JOptionPane.showConfirmDialog(this, content, "收银台 - 确认支付",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }
        pay(selectedProductIds);
    }

    private void pay(final List<String> selectedProductIds) {
        setBusy(true, "正在支付并创建订单……");
        new SwingWorker<OrderDto, Void>() {
            @Override
            protected OrderDto doInBackground() throws Exception {
                return service.createOrder(new OrderCreateRequest(selectedProductIds));
            }

            @Override
            protected void done() {
                try {
                    OrderDto order = get();
                    SeuMessages.info(StoreCartPanel.this, "支付成功",
                            "订单号：" + order.getOrderId()
                                    + "\n状态：已付款"
                                    + "\n金额：¥" + StoreFormat.money(order.getTotalAmount()));
                    onOrderCreated.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("支付被中断");
                    setBusy(false, "支付失败");
                } catch (ExecutionException e) {
                    // 明确告知失败原因（商品被抢购 / 余额不足等），并刷新购物车重新同步库存与余额。
                    SeuMessages.error(StoreCartPanel.this, "结算失败：" + messageOf(e)
                            + "\n\n已为你刷新购物车，请确认库存与余额后重试。");
                    refresh();
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
        selectAllButton.setEnabled(!busy);
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
