package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.LibraryClientService;
import edu.seu.vcampus.common.dto.BookDto;
import edu.seu.vcampus.common.dto.BookSummary;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Search and maintenance page for library titles and inventory. */
public final class LibraryPanel extends JPanel {
    private final LibraryClientService service;
    private final SubSystemRole effectiveRole;
    private final JTextField keyword = new JTextField(18);
    private final JCheckBox includeInactive = new JCheckBox("含已下架");
    private final JButton searchButton = new JButton("查询");
    private final JButton addButton = new JButton("新增");
    private final JButton editButton = new JButton("编辑");
    private final JButton deleteButton = new JButton("删除");
    private final JLabel statusLabel = new JLabel("准备就绪");
    private final DefaultTableModel tableModel = new DefaultTableModel() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private List<BookSummary> rows = new ArrayList<BookSummary>();

    public LibraryPanel(LibraryClientService service, Role role, Set<String> adminScopes) {
        super(new BorderLayout(0, 12));
        this.service = service;
        this.effectiveRole = SubSystems.effectiveRole(role, adminScopes, SubSystem.LIBRARY);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        buildUi();
        bindActions();
        refreshRows();
    }

    private void buildUi() {
        JPanel heading = new JPanel(new BorderLayout());
        JLabel title = new JLabel("图书馆（" + effectiveRole.getDisplayName() + "）");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        heading.add(title, BorderLayout.WEST);
        heading.add(statusLabel, BorderLayout.EAST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(new JLabel("关键字"));
        filters.add(keyword);
        filters.add(includeInactive);
        filters.add(searchButton);
        filters.add(addButton);
        filters.add(editButton);
        filters.add(deleteButton);

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.add(heading, BorderLayout.NORTH);
        north.add(filters, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        tableModel.setColumnIdentifiers(new String[]{
                "ISBN", "书名", "作者", "出版社", "分类", "可借", "馆藏", "状态"});
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        boolean administrator = effectiveRole == SubSystemRole.ADMIN;
        includeInactive.setVisible(administrator);
        addButton.setVisible(administrator);
        editButton.setVisible(administrator);
        deleteButton.setVisible(administrator);
    }

    private void bindActions() {
        searchButton.addActionListener(event -> refreshRows());
        keyword.addActionListener(event -> refreshRows());
        includeInactive.addActionListener(event -> refreshRows());
        addButton.addActionListener(event -> editBook(null));
        editButton.addActionListener(event -> editSelectedBook());
        deleteButton.addActionListener(event -> deleteSelectedBook());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    if (effectiveRole == SubSystemRole.ADMIN) {
                        editSelectedBook();
                    } else {
                        showSelectedDetails();
                    }
                }
            }
        });
    }

    private void refreshRows() {
        setBusy(true, "正在加载……");
        final String requestedKeyword = keyword.getText();
        final boolean requestedInactive = includeInactive.isSelected();
        new SwingWorker<List<BookSummary>, Void>() {
            @Override
            protected List<BookSummary> doInBackground() throws Exception {
                return service.queryBooks(requestedKeyword, requestedInactive);
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    renderRows();
                    statusLabel.setText("共 " + rows.size() + " 种图书");
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
        for (BookSummary book : rows) {
            tableModel.addRow(new Object[]{
                    book.getIsbn(),
                    book.getTitle(),
                    book.getAuthor(),
                    book.getPublisher(),
                    book.getCategory(),
                    Integer.valueOf(book.getAvailableCopies()),
                    Integer.valueOf(book.getTotalCopies()),
                    book.isActive() ? "在架" : "已下架"
            });
        }
    }

    private void editSelectedBook() {
        BookSummary book = selectedBook();
        if (book != null) {
            editBook(book);
        }
    }

    private void editBook(BookSummary existing) {
        final BookDto edited;
        try {
            edited = BookEditorDialog.edit(this, existing);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        if (edited == null) {
            return;
        }
        runMutation("正在保存图书……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.saveBook(edited);
            }
        });
    }

    private void deleteSelectedBook() {
        final BookSummary book = selectedBook();
        if (book == null || JOptionPane.showConfirmDialog(this,
                "确定删除图书「" + book.getTitle() + "」吗？仍有借阅记录时请改用下架。",
                "确认删除", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        runMutation("正在删除图书……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.deleteBook(book.getIsbn());
            }
        });
    }

    private BookSummary selectedBook() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一种图书", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
    }

    private void showSelectedDetails() {
        BookSummary book = selectedBook();
        if (book == null) {
            return;
        }
        String details = "ISBN：" + nullToEmpty(book.getIsbn())
                + "\n书名：" + nullToEmpty(book.getTitle())
                + "\n作者：" + nullToEmpty(book.getAuthor())
                + "\n出版社：" + nullToEmpty(book.getPublisher())
                + "\n分类：" + nullToEmpty(book.getCategory())
                + "\n可借 / 馆藏：" + book.getAvailableCopies() + " / " + book.getTotalCopies()
                + "\n状态：" + (book.isActive() ? "在架" : "已下架");
        JOptionPane.showMessageDialog(this, details, "图书详情",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void runMutation(final String status, final IoAction action) {
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
                    refreshRows();
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
        searchButton.setEnabled(!busy);
        keyword.setEnabled(!busy);
        includeInactive.setEnabled(!busy);
        addButton.setEnabled(!busy);
        editButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy);
    }

    private String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause();
        return cause == null || cause.getMessage() == null ? "操作失败" : cause.getMessage();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private interface IoAction {
        void run() throws IOException;
    }
}
