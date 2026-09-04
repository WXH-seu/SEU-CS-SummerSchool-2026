package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.LibraryClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.BorrowRecordDto;

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
 * 个人借阅记录与归还页；管理员视图列出全部在借副本及借阅人。
 */
public final class LibraryBorrowPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final LibraryClientService service;
    private final Runnable onReturned;
    private final boolean adminView;
    private final JButton refreshButton = SeuButtons.secondary("刷新");
    private final JButton returnButton = SeuButtons.primary("归还");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<BorrowRecordDto> rows = new ArrayList<BorrowRecordDto>();

    public LibraryBorrowPanel(LibraryClientService service, Runnable onReturned) {
        this(service, onReturned, false);
    }

    public LibraryBorrowPanel(LibraryClientService service, Runnable onReturned, boolean adminView) {
        super(new BorderLayout(0, SeuTheme.SPACE_MD));
        this.service = service;
        this.onReturned = onReturned;
        this.adminView = adminView;
        this.tableModel = SeuTables.readOnlyModel(columnNames());
        this.table = SeuTables.create(tableModel);
        setBackground(SeuTheme.PAGE_BG);
        setBorder(SeuTheme.pageBorder());
        buildUi();
        bindActions();
    }

    private String[] columnNames() {
        if (adminView) {
            return new String[]{"借阅人", "书名", "ISBN", "作者", "借出时间", "应还时间", "状态"};
        }
        return new String[]{"书名", "ISBN", "作者", "借出时间", "应还时间", "归还时间", "状态"};
    }

    private void buildUi() {
        JPanel actions = SeuPanels.toolbar();
        actions.add(refreshButton);
        if (!adminView) {
            actions.add(returnButton);
        }

        String heading = adminView ? "全部借阅" : "我的借阅";
        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading(heading, statusLabel), BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel card = SeuPanels.card();
        card.add(SeuTables.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
    }

    private void bindActions() {
        refreshButton.addActionListener(event -> refresh());
        if (adminView) {
            return;
        }
        returnButton.addActionListener(event -> returnSelected());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    returnSelected();
                }
            }
        });
    }

    public void refresh() {
        setBusy(true, "正在加载借阅记录……");
        new SwingWorker<List<BorrowRecordDto>, Void>() {
            @Override
            protected List<BorrowRecordDto> doInBackground() throws Exception {
                return service.queryBorrows();
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    renderRows();
                    String unit = adminView ? "条在借" : "条记录";
                    statusLabel.setText("共 " + rows.size() + " " + unit);
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
        for (BorrowRecordDto record : rows) {
            if (adminView) {
                tableModel.addRow(new Object[]{
                        record.getBorrowerLabel(),
                        record.getTitle(),
                        record.getIsbn(),
                        record.getAuthor(),
                        nullToEmpty(record.getBorrowTime()),
                        nullToEmpty(record.getDueTime()),
                        record.getStatusName()
                });
            } else {
                tableModel.addRow(new Object[]{
                        record.getTitle(),
                        record.getIsbn(),
                        record.getAuthor(),
                        nullToEmpty(record.getBorrowTime()),
                        nullToEmpty(record.getDueTime()),
                        nullToEmpty(record.getReturnTime()),
                        record.getStatusName()
                });
            }
        }
    }

    private void returnSelected() {
        final BorrowRecordDto record = selectedRecord();
        if (record == null) {
            return;
        }
        if (record.isReturned()) {
            SeuMessages.info(this, "该记录已经归还");
            return;
        }
        String hint = record.isOverdue() ? "该记录已逾期，" : "";
        if (!SeuMessages.confirm(this, hint + "确定归还「" + record.getTitle() + "」吗？")) {
            return;
        }
        setBusy(true, "正在归还……");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.returnBook(record.getRecordId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (onReturned != null) {
                        onReturned.run();
                    }
                    refresh();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("归还被中断");
                    setBusy(false, "归还失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "归还失败");
                }
            }
        }.execute();
    }

    private BorrowRecordDto selectedRecord() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            SeuMessages.info(this, "请先选择一条借阅记录");
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        refreshButton.setEnabled(!busy);
        returnButton.setEnabled(!busy && !adminView);
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
