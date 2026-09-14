package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTables;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.ProductDto;
import edu.seu.vcampus.common.dto.StoreQueryRequest;
import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * 商品浏览与维护页：学生 / 教师可检索并加入购物车，管理员可维护商品。
 * 布局与控件统一使用 {@code ui.components} 公共组件。
 */
public final class StoreProductPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final StoreClientService service;
    private final SubSystemRole effectiveRole;
    private final JTextField keyword = SeuFields.text(18);
    private final JComboBox<String> categoryBox = SeuFields.combo(new String[]{"全部"});
    private boolean updatingCategories;
    private final JCheckBox includeInactive = new JCheckBox("含已下架");
    private final JButton searchButton = SeuButtons.primary("查询");
    private final JButton addToCartButton = SeuButtons.secondary("加入购物车");
    private final JButton imageButton = SeuButtons.secondary("查看图片");
    private final JButton addButton = SeuButtons.secondary("新增");
    private final JButton editButton = SeuButtons.secondary("编辑");
    private final JButton deleteButton = SeuButtons.danger("删除");
    private final JLabel statusLabel = SeuLabels.status("准备就绪");
    private final DefaultTableModel tableModel = SeuTables.readOnlyModel(new String[]{
            "商品编号", "名称", "分类", "描述", "单价", "库存", "上架", "图片"});
    private final JTable table = SeuTables.create(tableModel);
    private List<ProductDto> rows = new ArrayList<ProductDto>();
    private List<String> categories = new ArrayList<String>();

    public StoreProductPanel(StoreClientService service, SubSystemRole effectiveRole) {
        super(new BorderLayout(0, SeuTheme.SPACE_MD));
        this.service = service;
        if (effectiveRole == null) {
            throw new IllegalArgumentException("effectiveRole is required");
        }
        this.effectiveRole = effectiveRole;
        setBackground(SeuTheme.PAGE_BG);
        setBorder(SeuTheme.pageBorder());
        buildUi();
        bindActions();
        refresh();
    }

    private void buildUi() {
        SeuFields.setPlaceholder(keyword, "编号 / 名称");
        includeInactive.setFont(SeuTheme.bodyFont());
        includeInactive.setForeground(SeuTheme.TEXT);
        includeInactive.setOpaque(false);

        JPanel filters = SeuPanels.toolbar();
        filters.add(SeuLabels.field("关键字"));
        filters.add(keyword);
        filters.add(SeuLabels.field("分类"));
        filters.add(categoryBox);
        filters.add(includeInactive);
        filters.add(searchButton);
        filters.add(addToCartButton);
        filters.add(imageButton);
        filters.add(addButton);
        filters.add(editButton);
        filters.add(deleteButton);

        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(SeuPanels.heading("校园商店 · 商品", statusLabel), BorderLayout.NORTH);
        north.add(filters, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel card = SeuPanels.card();
        card.add(SeuTables.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        boolean administrator = effectiveRole == SubSystemRole.ADMIN;
        includeInactive.setVisible(administrator);
        addToCartButton.setVisible(!administrator);
        addButton.setVisible(administrator);
        editButton.setVisible(administrator);
        deleteButton.setVisible(administrator);
    }

    private void bindActions() {
        searchButton.addActionListener(event -> refresh());
        keyword.addActionListener(event -> refresh());
        categoryBox.addActionListener(event -> {
            if (!updatingCategories) {
                refresh();
            }
        });
        includeInactive.addActionListener(event -> refresh());
        addToCartButton.addActionListener(event -> addSelectedToCart());
        imageButton.addActionListener(event -> showSelectedImage());
        addButton.addActionListener(event -> editProduct(null));
        editButton.addActionListener(event -> editSelected());
        deleteButton.addActionListener(event -> deleteSelected());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2
                        && effectiveRole == SubSystemRole.ADMIN) {
                    editSelected();
                } else if (event.getClickCount() == 2) {
                    showSelectedImage();
                }
            }
        });
    }

    public void refresh() {
        setBusy(true, "正在加载商品……");
        final String keywordText = keyword.getText();
        final String selectedCategory = currentCategory();
        final boolean activeOnly = !includeInactive.isSelected();
        new SwingWorker<List<ProductDto>, Void>() {
            @Override
            protected List<ProductDto> doInBackground() throws Exception {
                categories = service.queryCategories();
                return service.queryProducts(new StoreQueryRequest(
                        keywordText, selectedCategory, activeOnly));
            }

            @Override
            protected void done() {
                try {
                    rows = get();
                    rebuildCategoryItems(selectedCategory);
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

    private String currentCategory() {
        Object selected = categoryBox.getSelectedItem();
        return selected == null || "全部".equals(String.valueOf(selected))
                ? null : String.valueOf(selected).trim();
    }

    private void rebuildCategoryItems(String previous) {
        updatingCategories = true;
        try {
            java.util.List<String> items = new ArrayList<String>();
            items.add("全部");
            for (String category : categories) {
                if (category != null && !category.trim().isEmpty()
                        && !items.contains(category)) {
                    items.add(category);
                }
            }
            categoryBox.setModel(new javax.swing.DefaultComboBoxModel<String>(
                    items.toArray(new String[items.size()])));
            if (previous != null && items.contains(previous)) {
                categoryBox.setSelectedItem(previous);
            } else {
                categoryBox.setSelectedItem("全部");
            }
        } finally {
            updatingCategories = false;
        }
    }

    private void renderRows() {
        tableModel.setRowCount(0);
        for (ProductDto product : rows) {
            tableModel.addRow(new Object[]{
                    product.getProductId(),
                    product.getProductName(),
                    product.getCategory(),
                    product.getDescription(),
                    StoreFormat.money(product.getPrice()),
                    Integer.valueOf(product.getStock()),
                    product.isActive() ? "在架" : "已下架",
                    hasImage(product) ? "有图" : "无图"
            });
        }
    }

    private boolean hasImage(ProductDto product) {
        return product.getImagePath() != null && !product.getImagePath().trim().isEmpty();
    }

    private void showSelectedImage() {
        ProductDto product = selectedProduct();
        if (product != null) {
            showProductImage(product);
        }
    }

    /**
     * 显示商品图片。图片路径可以是绝对路径，也可以是相对客户端运行目录的路径，
     * 例如 {@code vcampus-client/images/P001.png}；未设置或文件缺失时给出提示。
     */
    private void showProductImage(ProductDto product) {
        if (!hasImage(product)) {
            SeuMessages.info(this, "该商品暂未设置图片",
                    "商品「" + product.getProductName() + "」还没有图片。\n"
                            + "管理员可在「编辑商品」的“图片路径”里填写，例如：\n"
                            + "vcampus-client/images/P001.png（相对仓库根目录）\n"
                            + "或 C:\\图片\\P001.png（绝对路径）。");
            return;
        }
        File file = new File(product.getImagePath().trim());
        if (!file.isFile()) {
            SeuMessages.error(this, "图片文件不存在：" + file.getAbsolutePath()
                    + "\n请确认路径是否正确（可在「编辑商品」中修改）。");
            return;
        }
        ImageIcon icon = new ImageIcon(file.getAbsolutePath());
        if (icon.getIconWidth() <= 0) {
            SeuMessages.error(this, "无法读取图片文件：" + file.getAbsolutePath());
            return;
        }
        int maxWidth = 420;
        int maxHeight = 320;
        double scale = Math.min(1.0, Math.min(
                (double) maxWidth / icon.getIconWidth(),
                (double) maxHeight / icon.getIconHeight()));
        Image scaled = icon.getImage().getScaledInstance(
                (int) Math.round(icon.getIconWidth() * scale),
                (int) Math.round(icon.getIconHeight() * scale),
                Image.SCALE_SMOOTH);
        JLabel caption = SeuLabels.subtitle(product.getProductName() + "（"
                + StoreFormat.money(product.getPrice()) + " / 库存 " + product.getStock() + "）");
        JPanel content = new JPanel(new BorderLayout(0, SeuTheme.SPACE_SM));
        content.add(caption, BorderLayout.NORTH);
        content.add(new JLabel(new ImageIcon(scaled)), BorderLayout.CENTER);
        content.add(SeuLabels.muted(file.getAbsolutePath()), BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, content, "商品图片",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void addSelectedToCart() {
        final ProductDto product = selectedProduct();
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
        if (selected == null || !SeuMessages.confirm(this,
                "确定删除商品「" + selected.getProductName() + "」吗？")) {
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
            SeuMessages.info(this, "请先选择一件商品");
            return null;
        }
        return rows.get(table.convertRowIndexToModel(viewRow));
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
                    SeuMessages.info(StoreProductPanel.this, successMessage);
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
        categoryBox.setEnabled(!busy);
        includeInactive.setEnabled(!busy);
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
        SeuMessages.error(this, message);
    }

    private interface IoAction {
        void run() throws IOException;
    }
}
