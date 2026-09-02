package edu.seu.vcampus.client.ui.components;

import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.awt.Insets;

/**
 * 统一输入控件工厂，对齐门户搜索框的圆角白底风格。
 */
public final class SeuFields {
    private SeuFields() {
    }

    public static JTextField text(int columns) {
        JTextField field = new JTextField(columns);
        styleTextField(field);
        return field;
    }

    public static JTextField text(String initial, int columns) {
        JTextField field = text(columns);
        field.setText(initial == null ? "" : initial);
        return field;
    }

    public static JPasswordField password(int columns) {
        JPasswordField field = new JPasswordField(columns);
        styleTextField(field);
        return field;
    }

    public static <T> JComboBox<T> combo(T[] items) {
        JComboBox<T> combo = new JComboBox<T>(items);
        combo.setFont(SeuTheme.bodyFont());
        combo.setBackground(SeuTheme.SURFACE);
        combo.setForeground(SeuTheme.TEXT);
        combo.setPreferredSize(new Dimension(combo.getPreferredSize().width, 34));
        return combo;
    }

    private static void styleTextField(JTextField field) {
        field.setFont(SeuTheme.bodyFont());
        field.setForeground(SeuTheme.TEXT);
        field.setBackground(SeuTheme.SURFACE);
        field.setCaretColor(SeuTheme.PRIMARY);
        field.setMargin(new Insets(6, 10, 6, 10));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 34));
        field.putClientProperty("JTextField.placeholderText", null);
    }

    /**
     * 为 FlatLaf 文本框设置占位提示（Java 8 / FlatLaf 3 支持）。
     */
    public static void setPlaceholder(JTextField field, String placeholder) {
        if (field != null) {
            field.putClientProperty("JTextField.placeholderText", placeholder);
        }
    }
}
