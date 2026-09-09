package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.LibraryClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.BorrowRecordDto;
import edu.seu.vcampus.common.dto.ReserveDto;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * 预约委托页：师生查看申请并取书；管理员审核、办理取书。
 */
public final class LibraryReservePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final LibraryClientService service;
    private final boolean adminView;
    private final Runnable onChanged;
    private final JButton refreshButton = SeuButtons.secondary("刷新");
    private final JButton approveButton = SeuButtons.accent("批准");
    private final JButton rejectButton = SeuButtons.danger("拒绝");
    private final JButton pickupButton = SeuButtons.primary("取书");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<ReserveDto> rows = new ArrayList<ReserveDto>();

    public LibraryReservePanel(LibraryClientService service, SubSystemRole effectiveRole,
                               Runnable onChanged) {
        super(new BorderLayout(0, SeuTheme.SPACE_MD));
        this.service = service;
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        this.adminView = effectiveRole == SubSystemRole.ADMIN;
        this.onChanged = onChanged;
        this.tableModel = SeuTables.readOnlyModel(columnNames());
        this.table = SeuTables.create(tableModel);
        setBackground(SeuTheme.PAGE_BG);
        setBorder(SeuTheme.pageBorder());
        buildUi();
        bindActions();
    }

    private String[] columnNames() {
        if (adminView) {
            return new String[]{"申请人", "书名", "ISBN", "提交时间", "保管截止", "违约", "状态"};
        }
        return new String[]{"书名", "ISBN", "提交时间", "保管截止", "违约", "状态"};
    }

    private void buildUi() {
        JPanel actions = SeuPanels.toolbar();
        actions.add(refreshButton);
        if (adminView) {
            actions.add(approveButton);
            actions.add(rejectButton);
        }
        actions.add(pickupButton);

        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading(adminView ? "预约委托" : "我的预约", statusLabel),
                BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel card = SeuPanels.card();
        card.add(SeuTables.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
    }

    private void bindActions() {
        refreshButton.addActionListener(event -> refresh());
        pickupButton.addActionListener(event -> pickupSelected());
        if (adminView) {
            approveButton.addActionListener(event -> reviewSelected(true));
            rejectButton.addActionListener(event -> reviewSelected(false));
        }
    }

    public void refresh() {
        setBusy(true, "正在加载预约……");
        new SwingWorker<List<ReserveDto>, Void>() {
            @Override
            protected List<ReserveDto> doInBackground() throws Exception {
                return service.queryReservations();
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    renderRows();
                    statusLabel.setText(statusText());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("加载被中断");
                } catch (ExecutionException e) {
                    statusLabel.setText(messageOf(e));
                } finally {
                    setBusy(false, statusLabel.getText());
                }
            }
        }.execute();
    }

    private void renderRows() {
        tableModel.setRowCount(0);
        for (ReserveDto row : rows) {
            if (adminView) {
                tableModel.addRow(new Object[]{
                        row.getApplicantLabel(),
                        row.getTitle(),
                        row.getIsbn(),
                        nullToEmpty(row.getApplyTime()),
                        nullToEmpty(row.getHoldUntilTime()),
                        defaultLabel(row),
                        row.getStatusName()
                });
            } else {
                tableModel.addRow(new Object[]{
                        row.getTitle(),
                        row.getIsbn(),
                        nullToEmpty(row.getApplyTime()),
                        nullToEmpty(row.getHoldUntilTime()),
                        defaultLabel(row),
                        row.getStatusName()
                });
            }
        }
    }

    private void reviewSelected(final boolean approved) {
        final ReserveDto row = selectedRow();
        if (row == null) {
            return;
        }
        if (!row.isPending()) {
            SeuMessages.info(this, "该预约已审核");
            return;
        }
        String action = approved ? "批准" : "拒绝";
        if (!SeuMessages.confirm(this, "确定" + action + "「" + row.getTitle() + "」的预约吗？"
                + (approved ? "命中应还日最早的一册后，该册不可续借。" : ""))) {
            return;
        }
        setBusy(true, "正在" + action + "……");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.reviewReservation(row.getReservationId(), approved);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                    refresh();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError(action + "被中断");
                    setBusy(false, action + "失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, action + "失败");
                }
            }
        }.execute();
    }

    private void pickupSelected() {
        final ReserveDto row = selectedRow();
        if (row == null) {
            return;
        }
        if (!row.isHeld()) {
            SeuMessages.info(this, "只有锁定保管中的预约可以取书");
            return;
        }
        if (!SeuMessages.confirm(this, "确定领取「" + row.getTitle() + "」吗？借期 30 天。")) {
            return;
        }
        setBusy(true, "正在取书……");
        new SwingWorker<BorrowRecordDto, Void>() {
            @Override
            protected BorrowRecordDto doInBackground() throws Exception {
                return service.pickupReservation(row.getReservationId());
            }

            @Override
            protected void done() {
                try {
                    BorrowRecordDto record = get();
                    SeuMessages.info(LibraryReservePanel.this, "取书成功",
                            "已借出「" + record.getTitle() + "」\n应还时间：" + record.getDueTime());
                    if (onChanged != null) {
                        onChanged.run();
                    }
                    refresh();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("取书被中断");
                    setBusy(false, "取书失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "取书失败");
                }
            }
        }.execute();
    }

    private ReserveDto selectedRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            SeuMessages.info(this, "请先选择一条预约");
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        refreshButton.setEnabled(!busy);
        approveButton.setEnabled(!busy && adminView);
        rejectButton.setEnabled(!busy && adminView);
        pickupButton.setEnabled(!busy);
    }

    private String statusText() {
        int pending = 0;
        int held = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).isPending()) {
                pending++;
            }
            if (rows.get(i).isHeld()) {
                held++;
            }
        }
        return "共 " + rows.size() + " 条，待审 " + pending + " 条，待取 " + held + " 条";
    }

    private String defaultLabel(ReserveDto row) {
        String label = row.getDefaultCount() + " / 3";
        String until = row.getSuspendUntilTime();
        if (until != null && !until.trim().isEmpty()) {
            return label + "（停权至 " + until + "）";
        }
        return label;
    }

    private String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? "操作失败" : cause.getMessage();
    }

    private void showError(String message) {
        SeuMessages.error(this, message);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
