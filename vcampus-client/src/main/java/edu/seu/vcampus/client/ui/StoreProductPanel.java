package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Product browsing page with a shop cart entry and admin maintenance. */
public final class StoreProductPanel extends JPanel {
    private final StoreClientService service;
    private final Role role;
    private final Set<String> adminScopes;
    private final JTextField keyword = new JTextField(10);
    private final JTextField category = new JTextField(8);
    private final JButton searchButton = new JButton("查询");
    private final JButton addToCartButton = new JButton("加入购物车");
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
    private List<ProductDto> rows = new ArrayList<ProductDto>();

    public StoreProductPanel(StoreClientService service, Role role, Set<String> adminScopes) {
        super(new BorderLayout(0, 12));
        this.service = service;
        this.role = role;
        this.adminScopes = adminScopes;
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        buildUi();
        bindActions();
        refresh();
    }

    private void buildUi() {
        JPanel heading = new JPanel(new BorderLayout());
        JLabel title = new JLabel("校园商店 · 商品");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        heading.add(title, BorderLayout.WEST);
        heading.add(statusLabel, BorderLayout.EAST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(new JLabel("关键字"));
        filters.add(keyword);
        filters.add(new JLabel("分类"));
        filters.add(category);
        filters.add(searchButton);
        filters.add(addToCartButton);
        filters.add(addButton);
        filters.add(editButton);
        filters.add(deleteButton);

        JPanel north = new JPanel(new BorderLayout(0, 14));
        north.add(heading, BorderLayout.NORTH);
        north.add(filters, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);

        boolean administrator = effectiveRole() == SubSystemRole.ADMIN;
        addToCartButton.setVisible(!administrator);
        addButton.setVisible(administrator);
        editButton.setVisible(administrator);
        deleteButton.setVisible(administrator);
    }

    private void bindActions() {
        searchButton.addActionListener(event -> refresh());
        keyword.addActionListener(event -> refresh());
        category.addActionListener(event -> refresh());
        addToCartButton.addActionListener(event -> addSelectedToCart());
        addButton.addActionListener(event -> editProduct(null));
        editButton.addActionListener(event -> editSelected());
        deleteButton.addActionListener(event -> deleteSelected());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && effectiveRole() == SubSystemRole.ADMIN) {
                    editSelected();
                }
            }
        });
    }

    public void refresh() {
        setBusy(true, "正在加载商品……");
        final StoreQueryRequest query = new StoreQueryRequest(
                keyword.getText(), category.getText(), true);
        new SwingWorker<List<ProductDto>, Void>() {
            @Override
            protected List<ProductDto> doInBackground() throws Exception {
                return service.queryProducts(query);
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    renderRows();
                    statusLabel.setText("共 " + rows.size() + " 件商品");
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
                "商品编号", "名称", "分类", "描述", "单价", "库存", "上架"});
        for (ProductDto product : rows) {
            tableModel.addRow(new Object[]{product.getProductId(), product.getProductName(),
                    product.getCategory(), product.getDescription(),
                    StoreFormat.money(product.getPrice()), product.getStock(),
                    product.isActive() ? "是" : "否"});
        }
    }

    private void addSelectedToCart() {
        ProductDto product = selectedProduct();
        if (product == null) {
            return;
        }
        String input = JOptionPane.showInputDialog(this,
                "请输入购买数量（剩余库存 " + product.getStock() + "）：", "1");
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
        if (quantity <= 0) {
            showError("数量必须大于 0");
            return;
        }
        runMutation("正在加入购物车……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.updateCart(product.getProductId(), quantity);
            }
        }, "已加入购物车");
    }

    private void editProduct(ProductDto existing) {
        try {
            final ProductDto edited = StoreEditors.editProduct(this, existing);
            if (edited == null) {
                return;
            }
            runMutation("正在保存……", new IoAction() {
                @Override
                public void run() throws IOException {
                    service.saveProduct(edited);
                }
            }, "商品已保存");
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void editSelected() {
        ProductDto selected = selectedProduct();
        if (selected != null) {
            editProduct(selected);
        }
    }

    private void deleteSelected() {
        final ProductDto selected = selectedProduct();
        if (selected == null || JOptionPane.showConfirmDialog(this,
                "确定删除商品「" + selected.getProductName() + "」吗？",
                "确认删除", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        runMutation("正在删除……", new IoAction() {
            @Override
            public void run() throws IOException {
                service.deleteProduct(selected.getProductId());
            }
        }, "商品已删除");
    }

    private ProductDto selectedProduct() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一件商品", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
    }

    private SubSystemRole effectiveRole() {
        return SubSystems.effectiveRole(role, adminScopes, SubSystem.STORE);
    }

    private void runMutation(String status, final IoAction action, String successMessage) {
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
                    JOptionPane.showMessageDialog(StoreProductPanel.this,
                            successMessage, "操作成功", JOptionPane.INFORMATION_MESSAGE);
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
        addToCartButton.setEnabled(!busy);
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

    private interface IoAction {
        void run() throws IOException;
    }
}
