package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.LibraryClientService;
import edu.seu.vcampus.common.dto.BookSummary;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Search page for library titles and available inventory. */
public final class LibraryPanel extends JPanel {
    private final LibraryClientService service;
    private final SubSystemRole effectiveRole;
    private final JTextField keyword = new JTextField(18);
    private final JButton searchButton = new JButton("查询");
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

    public LibraryPanel(LibraryClientService service, SubSystemRole effectiveRole) {
        super(new BorderLayout(0, 12));
        this.service = service;
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        this.effectiveRole = effectiveRole;
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
        filters.add(searchButton);

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.add(heading, BorderLayout.NORTH);
        north.add(filters, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        tableModel.setColumnIdentifiers(new String[]{
                "ISBN", "书名", "作者", "出版社", "分类", "可借", "馆藏"});
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private void bindActions() {
        searchButton.addActionListener(event -> refreshRows());
        keyword.addActionListener(event -> refreshRows());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    showSelectedDetails();
                }
            }
        });
    }

    private void refreshRows() {
        setBusy(true, "正在加载……");
        final String requestedKeyword = keyword.getText();
        new SwingWorker<List<BookSummary>, Void>() {
            @Override
            protected List<BookSummary> doInBackground() throws Exception {
                return service.queryBooks(requestedKeyword);
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
                    Integer.valueOf(book.getTotalCopies())
            });
        }
    }

    private void showSelectedDetails() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        BookSummary book = rows.get(table.convertRowIndexToModel(viewRow));
        String details = "ISBN：" + nullToEmpty(book.getIsbn())
                + "\n书名：" + nullToEmpty(book.getTitle())
                + "\n作者：" + nullToEmpty(book.getAuthor())
                + "\n出版社：" + nullToEmpty(book.getPublisher())
                + "\n分类：" + nullToEmpty(book.getCategory())
                + "\n可借 / 馆藏：" + book.getAvailableCopies() + " / " + book.getTotalCopies();
        JOptionPane.showMessageDialog(this, details, "图书详情",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        searchButton.setEnabled(!busy);
        keyword.setEnabled(!busy);
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
}
