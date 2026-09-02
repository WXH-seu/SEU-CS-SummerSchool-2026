package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.ProductDto;

import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;

/** 商店商品新增 / 编辑对话框，输入控件统一使用公共组件。 */
final class StoreEditors {
    private StoreEditors() {
    }

    static ProductDto editProduct(Component parent, ProductDto value) {
        JTextField id = field(value == null ? "" : value.getProductId(), value == null);
        JTextField name = field(value == null ? "" : value.getProductName(), true);
        JTextField category = field(value == null ? "" : value.getCategory(), true);
        JTextField description = field(value == null ? "" : value.getDescription(), true);
        JTextField price = field(value == null ? "" : String.valueOf(value.getPrice()), true);
        JTextField stock = field(value == null ? "" : String.valueOf(value.getStock()), true);
        JCheckBox active = new JCheckBox("上架", value == null || value.isActive());
        active.setFont(SeuTheme.bodyFont());
        active.setForeground(SeuTheme.TEXT);

        JPanel form = form(new String[]{"商品编号*", "商品名称*", "分类", "描述",
                        "单价*", "库存*", "状态"},
                new Component[]{id, name, category, description, price, stock, active});
        if (!confirm(parent, form, value == null ? "新增商品" : "编辑商品")) {
            return null;
        }
        return new ProductDto(text(id), text(name), text(category), text(description),
                parsePrice(price), parseStock(stock), active.isSelected());
    }

    private static JPanel form(String[] labels, Component[] components) {
        JPanel panel = new JPanel(new GridLayout(labels.length, 2, 8, 8));
        panel.setBorder(SeuTheme.empty(SeuTheme.SPACE_SM, 0, SeuTheme.SPACE_SM, 0));
        for (int i = 0; i < labels.length; i++) {
            panel.add(SeuLabels.field(labels[i]));
            panel.add(components[i]);
        }
        return panel;
    }

    private static boolean confirm(Component parent, JPanel form, String title) {
        return JOptionPane.showConfirmDialog(parent, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                == JOptionPane.OK_OPTION;
    }

    private static JTextField field(String value, boolean editable) {
        JTextField field = SeuFields.text(value == null ? "" : value, 18);
        field.setEditable(editable);
        return field;
    }

    private static String text(JTextField field) {
        return field.getText().trim();
    }

    private static BigDecimal parsePrice(JTextField field) {
        try {
            BigDecimal price = new BigDecimal(text(field));
            if (price.signum() < 0) {
                throw new IllegalArgumentException("单价必须大于等于 0");
            }
            return price;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("单价必须是数字");
        }
    }

    private static int parseStock(JTextField field) {
        try {
            int stock = Integer.parseInt(text(field));
            if (stock < 0) {
                throw new IllegalArgumentException("库存不能为负数");
            }
            return stock;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("库存必须是整数");
        }
    }
}
