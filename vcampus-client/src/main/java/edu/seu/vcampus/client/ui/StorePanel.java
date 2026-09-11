package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.BalanceRechargeRequest;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

/** 校园商店入口：页签 + 购物者可见的校园余额条与充值入口。 */
public final class StorePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final String[] RECHARGE_CHANNELS = {"一卡通充值", "微信", "银行卡"};

    private final StoreClientService service;
    private final boolean shopper;
    private final JTabbedPane tabs = new JTabbedPane();
    private final int ordersIndex;
    private final JLabel balanceLabel = new JLabel("校园余额：¥—");
    private final JButton rechargeButton = SeuButtons.secondary("充值");

    public StorePanel(StoreClientService service, SubSystemRole effectiveRole) {
        super(new BorderLayout());
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        this.service = service;
        this.shopper = effectiveRole != SubSystemRole.ADMIN;
        setOpaque(false);
        ordersIndex = shopper ? 2 : 1;

        tabs.addTab("商品", new StoreProductPanel(service, effectiveRole));
        if (shopper) {
            tabs.addTab("购物车", new StoreCartPanel(service, new Runnable() {
                @Override
                public void run() {
                    showOrdersTab();
                    refreshBalance();
                }
            }));
        }
        tabs.addTab(shopper ? "我的订单" : "订单管理",
                new StoreOrderPanel(service, effectiveRole));
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                refreshSelected();
                if (shopper) {
                    refreshBalance();
                }
            }
        });

        if (shopper) {
            add(buildBalanceBar(), BorderLayout.NORTH);
        }
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildBalanceBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(SeuTheme.empty(SeuTheme.SPACE_SM, SeuTheme.SPACE_LG,
                SeuTheme.SPACE_XS, SeuTheme.SPACE_LG));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, SeuTheme.SPACE_SM, 0));
        left.setOpaque(false);
        balanceLabel.setFont(SeuTheme.bodyFont());
        balanceLabel.setForeground(SeuTheme.TEXT);
        left.add(SeuLabels.field("校园钱包"));
        left.add(balanceLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(rechargeButton);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        rechargeButton.addActionListener(event -> openRechargeDialog());
        refreshBalance();
        return bar;
    }

    private void refreshBalance() {
        new SwingWorker<BigDecimal, Void>() {
            @Override
            protected BigDecimal doInBackground() throws Exception {
                return service.queryBalance();
            }

            @Override
            protected void done() {
                try {
                    BigDecimal balance = get();
                    balanceLabel.setText("校园余额：¥" + StoreFormat.money(balance));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    balanceLabel.setText("校园余额：获取失败");
                }
            }
        }.execute();
    }

    private void openRechargeDialog() {
        final JTextField amountField = SeuFields.text(12);
        final JComboBox<String> channelBox = SeuFields.combo(RECHARGE_CHANNELS);

        JPanel form = new JPanel(new java.awt.GridLayout(3, 2, 8, 8));
        form.setBorder(SeuTheme.empty(SeuTheme.SPACE_SM, 0, SeuTheme.SPACE_SM, 0));
        form.add(SeuLabels.field("充值金额（元）*"));
        form.add(amountField);
        form.add(SeuLabels.field("支付渠道*"));
        form.add(channelBox);
        form.add(SeuLabels.muted("提示"));
        form.add(SeuLabels.muted("单次最高 10000 元，仅支持正数且最多两位小数"));

        if (JOptionPane.showConfirmDialog(this, form, "校园钱包充值",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }

        final BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim());
        } catch (NumberFormatException e) {
            SeuMessages.error(this, "充值金额必须是数字");
            return;
        }
        if (amount.signum() <= 0) {
            SeuMessages.error(this, "充值金额必须大于 0");
            return;
        }
        if (amount.scale() > 2) {
            SeuMessages.error(this, "充值金额最多保留两位小数");
            return;
        }
        if (amount.compareTo(new BigDecimal("10000.00")) > 0) {
            SeuMessages.error(this, "单次充值金额不能超过 10000 元");
            return;
        }
        final String channel = String.valueOf(channelBox.getSelectedItem());

        rechargeButton.setEnabled(false);
        new SwingWorker<BigDecimal, Void>() {
            @Override
            protected BigDecimal doInBackground() throws Exception {
                return service.rechargeBalance(new BalanceRechargeRequest(amount, channel));
            }

            @Override
            protected void done() {
                try {
                    BigDecimal balance = get();
                    balanceLabel.setText("校园余额：¥" + StoreFormat.money(balance));
                    SeuMessages.info(StorePanel.this, "充值成功",
                            channel + " 到账 ¥" + StoreFormat.money(amount)
                                    + "，当前余额：¥" + StoreFormat.money(balance));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    SeuMessages.error(StorePanel.this, "充值被中断");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    SeuMessages.error(StorePanel.this,
                            cause == null || cause.getMessage() == null
                                    ? "充值失败" : cause.getMessage());
                } finally {
                    rechargeButton.setEnabled(true);
                }
            }
        }.execute();
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
