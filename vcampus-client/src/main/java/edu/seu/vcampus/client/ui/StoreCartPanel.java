package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.common.dto.CartItemDto;
import edu.seu.vcampus.common.dto.OrderDto;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Shopping cart page: change quantity, remove lines and check out. */
public final class StoreCartPanel extends JPanel {
    private final StoreClientService service;
    private final Runnable onOrderCreated;
    private final JButton changeButton = new JButton("修改数量");
    private final JButton removeButton = new JButton("移除");
    private final JButton checkoutButton = new JButton("结算下单");
    private final JLabel statusLabel = new JLabel("准备就绪");
    private final JLabel totalLabel = new JLabel("合计：¥0.00");
    private final DefaultTableModel tableModel = new DefaultTableModel() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private List<CartItemDto> rows = new ArrayList<CartItemDto>();

    public StoreCartPanel(StoreClientService service, Runnable onOrderCreated) {
        super(new BorderLayout(0, 12));
        this.service = service;
        this.onOrderCreated = onOrderCreated;
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        buildUi();
        bindActions();
        refresh();
    }

    private void buildUi() {
        JPanel heading = new JPanel(new BorderLayout());
        JLabel title = new JLabel("我的购物车");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        heading.add(title, BorderLayout.WEST);
        heading.add(totalLabel, BorderLayout.EAST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(changeButton);
        actions.add(removeButton);
        actions.add(checkoutButton);
        actions.add(statusLabel);

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.add(heading, BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
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
        tableModel.setColumnIdentifiers(new String[]{
                "商品编号", "名称", "单价", "数量", "小计", "剩余库存"});
        BigDecimal total = BigDecimal.ZERO;
        for (CartItemDto item : rows) {
            total = total.add(item.getSubtotal());
            tableModel.addRow(new Object[]{item.getProductId(), item.getProductName(),
                    StoreFormat.money(item.getUnitPrice()), item.getQuantity(),
                    StoreFormat.money(item.getSubtotal()), item.getStock()});
        }
        totalLabel.setText("合计：¥" + StoreFormat.money(total));
    }

    private void changeSelectedQuantity() {
        final CartItemDto item = selectedItem();
        if (item == null) {
            return;
        }
        String input = JOptionPane.showInputDialog(this,
                "请输入新数量（1-" + Math.max(1, item.getStock()) + "，输入 0 表示移除）：",
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
        if (item == null || JOptionPane.showConfirmDialog(this,
                "确定将「" + item.getProductName() + "」移出购物车吗？",
                "确认移除", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
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
            JOptionPane.showMessageDialog(this, "购物车为空，请先添加商品", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "确认对购物车中的 " + rows.size()
                + " 种商品下单吗？下单后将扣减库存。", "确认下单",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
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
                    JOptionPane.showMessageDialog(StoreCartPanel.this,
                            "下单成功！订单号：" + order.getOrderId()
                                    + "\n金额：¥" + StoreFormat.money(order.getTotalAmount()),
                            "下单成功", JOptionPane.INFORMATION_MESSAGE);
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
            JOptionPane.showMessageDialog(this, "请先选择一件商品", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
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
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }

    private interface IoAction {
        void run() throws IOException;
    }
}
