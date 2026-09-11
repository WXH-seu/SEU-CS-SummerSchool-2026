package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.LibraryClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.BookDto;
import edu.seu.vcampus.common.dto.WishDto;
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
 * 好书推荐页：师生提交许愿并查看审核结果；管理员批准引进或拒绝。
 */
public final class LibraryWishPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final LibraryClientService service;
    private final boolean adminView;
    private final Runnable onCatalogChanged;
    private final JButton refreshButton = SeuButtons.secondary("刷新");
    private final JButton submitButton = SeuButtons.primary("提交推荐");
    private final JButton approveButton = SeuButtons.accent("批准引进");
    private final JButton rejectButton = SeuButtons.danger("拒绝");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final DefaultTableModel tableModel;
    private final JTable table;
    private List<WishDto> rows = new ArrayList<WishDto>();

    public LibraryWishPanel(LibraryClientService service, SubSystemRole effectiveRole,
                            Runnable onCatalogChanged) {
        super(new BorderLayout(0, SeuTheme.SPACE_MD));
        this.service = service;
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        this.adminView = effectiveRole == SubSystemRole.ADMIN;
        this.onCatalogChanged = onCatalogChanged;
        this.tableModel = SeuTables.readOnlyModel(columnNames());
        this.table = SeuTables.create(tableModel);
        setBackground(SeuTheme.PAGE_BG);
        setBorder(SeuTheme.pageBorder());
        buildUi();
        bindActions();
    }

    private String[] columnNames() {
        if (adminView) {
            return new String[]{"申请人", "书名", "作者", "提交时间", "审核时间", "ISBN", "状态"};
        }
        return new String[]{"书名", "作者", "提交时间", "审核时间", "ISBN", "状态"};
    }

    private void buildUi() {
        JPanel actions = SeuPanels.toolbar();
        actions.add(refreshButton);
        if (adminView) {
            actions.add(approveButton);
            actions.add(rejectButton);
        } else {
            actions.add(submitButton);
        }

        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading("好书推荐", statusLabel), BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel card = SeuPanels.card();
        card.add(SeuTables.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
    }

    private void bindActions() {
        refreshButton.addActionListener(event -> refresh());
        if (adminView) {
            approveButton.addActionListener(event -> approveSelected());
            rejectButton.addActionListener(event -> rejectSelected());
            table.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    if (event.getClickCount() == 2) {
                        approveSelected();
                    }
                }
            });
            return;
        }
        submitButton.addActionListener(event -> submitWish());
    }

    public void refresh() {
        setBusy(true, "正在加载推荐……");
        new SwingWorker<List<WishDto>, Void>() {
            @Override
            protected List<WishDto> doInBackground() throws Exception {
                return service.queryWishes();
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
        for (WishDto wish : rows) {
            if (adminView) {
                tableModel.addRow(new Object[]{
                        wish.getSubmitterLabel(),
                        wish.getTitle(),
                        wish.getAuthor(),
                        nullToEmpty(wish.getSubmitTime()),
                        nullToEmpty(wish.getReviewTime()),
                        nullToEmpty(wish.getIsbn()),
                        wish.getStatusName()
                });
            } else {
                tableModel.addRow(new Object[]{
                        wish.getTitle(),
                        wish.getAuthor(),
                        nullToEmpty(wish.getSubmitTime()),
                        nullToEmpty(wish.getReviewTime()),
                        nullToEmpty(wish.getIsbn()),
                        wish.getStatusName()
                });
            }
        }
    }

    private void submitWish() {
        String[] values = WishSubmitDialog.prompt(this);
        if (values == null) {
            return;
        }
        if (values[0].isEmpty() || values[1].isEmpty()) {
            showError("书名和作者不能为空");
            return;
        }
        setBusy(true, "正在提交推荐……");
        final String title = values[0];
        final String author = values[1];
        new SwingWorker<WishDto, Void>() {
            @Override
            protected WishDto doInBackground() throws Exception {
                return service.submitWish(title, author);
            }

            @Override
            protected void done() {
                try {
                    WishDto created = get();
                    SeuMessages.info(LibraryWishPanel.this, "已提交推荐",
                            "「" + created.getTitle() + "」已进入待审列表");
                    refresh();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("提交被中断");
                    setBusy(false, "提交失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "提交失败");
                }
            }
        }.execute();
    }

    private void approveSelected() {
        final WishDto wish = selectedWish();
        if (wish == null) {
            return;
        }
        if (!wish.isPending()) {
            SeuMessages.info(this, "该推荐已审核");
            return;
        }
        final BookDto book;
        try {
            book = BookEditorDialog.createFromWish(this, wish.getTitle(), wish.getAuthor());
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        if (book == null) {
            return;
        }
        setBusy(true, "正在批准引进……");
        new SwingWorker<BookDto, Void>() {
            @Override
            protected BookDto doInBackground() throws Exception {
                return service.reviewWish(wish.getWishId(), true, book);
            }

            @Override
            protected void done() {
                try {
                    BookDto saved = get();
                    SeuMessages.info(LibraryWishPanel.this, "已批准引进",
                            "「" + saved.getTitle() + "」已写入书目，ISBN：" + saved.getIsbn());
                    if (onCatalogChanged != null) {
                        onCatalogChanged.run();
                    }
                    refresh();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("批准被中断");
                    setBusy(false, "批准失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "批准失败");
                }
            }
        }.execute();
    }

    private void rejectSelected() {
        final WishDto wish = selectedWish();
        if (wish == null) {
            return;
        }
        if (!wish.isPending()) {
            SeuMessages.info(this, "该推荐已审核");
            return;
        }
        if (!SeuMessages.confirm(this, "确定拒绝引进「" + wish.getTitle() + "」吗？书目不会改动。")) {
            return;
        }
        setBusy(true, "正在拒绝……");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.reviewWish(wish.getWishId(), false, null);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refresh();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("拒绝被中断");
                    setBusy(false, "拒绝失败");
                } catch (ExecutionException e) {
                    showError(messageOf(e));
                    setBusy(false, "拒绝失败");
                }
            }
        }.execute();
    }

    private WishDto selectedWish() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            SeuMessages.info(this, "请先选择一条推荐");
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        refreshButton.setEnabled(!busy);
        submitButton.setEnabled(!busy && !adminView);
        approveButton.setEnabled(!busy && adminView);
        rejectButton.setEnabled(!busy && adminView);
    }

    private String statusText() {
        int pending = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).isPending()) {
                pending++;
            }
        }
        if (adminView) {
            return "共 " + rows.size() + " 条推荐，待审 " + pending + " 条";
        }
        return "共 " + rows.size() + " 条推荐";
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
