package edu.seu.vcampus.client.ui.components;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
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
        styleTextField(field, 34);
        return field;
    }

    public static JTextField text(String initial, int columns) {
        JTextField field = text(columns);
        field.setText(initial == null ? "" : initial);
        return field;
    }

    public static JPasswordField password(int columns) {
        JPasswordField field = new JPasswordField(columns);
        styleTextField(field, 34);
        return field;
    }

    /** 身份认证页用的大号圆角输入框。 */
    public static JTextField pillText(int columns) {
        JTextField field = new JTextField(columns);
        stylePillField(field);
        return field;
    }

    /** 身份认证页用的大号圆角密码框。 */
    public static JPasswordField pillPassword(int columns) {
        JPasswordField field = new JPasswordField(columns);
        stylePillField(field);
        return field;
    }

    /**
     * 密码框右侧附带显示/隐藏切换，整体仍呈圆角条。
     */
    public static JPanel pillPasswordWithToggle(final JPasswordField field) {
        stylePillField(field);
        field.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 8));

        final JButton toggle = SeuButtons.link("显示");
        toggle.setForeground(SeuTheme.TEXT_MUTED);
        toggle.addActionListener(event -> {
            if (field.getEchoChar() == 0) {
                field.setEchoChar('\u2022');
                toggle.setText("显示");
            } else {
                field.setEchoChar((char) 0);
                toggle.setText("隐藏");
            }
        });

        JPanel wrap = new JPanel(new BorderLayout(4, 0));
        wrap.setOpaque(true);
        wrap.setBackground(SeuTheme.SURFACE);
        wrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SeuTheme.FIELD_BORDER, 1),
                BorderFactory.createEmptyBorder(2, 4, 2, 8)));
        wrap.setPreferredSize(new Dimension(320, 48));
        wrap.putClientProperty("JComponent.roundRect", Boolean.TRUE);
        wrap.add(field, BorderLayout.CENTER);
        wrap.add(toggle, BorderLayout.EAST);
        return wrap;
    }

    public static <T> JComboBox<T> combo(T[] items) {
        JComboBox<T> combo = new JComboBox<T>(items);
        combo.setFont(SeuTheme.bodyFont());
        combo.setBackground(SeuTheme.SURFACE);
        combo.setForeground(SeuTheme.TEXT);
        combo.setPreferredSize(new Dimension(combo.getPreferredSize().width, 34));
        return combo;
    }

    private static void styleTextField(JTextField field, int height) {
        field.setFont(SeuTheme.bodyFont());
        field.setForeground(SeuTheme.TEXT);
        field.setBackground(SeuTheme.SURFACE);
        field.setCaretColor(SeuTheme.PRIMARY);
        field.setMargin(new Insets(6, 10, 6, 10));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, height));
        field.putClientProperty("JTextField.placeholderText", null);
    }

    private static void stylePillField(JTextField field) {
        styleTextField(field, 48);
        field.setFont(SeuTheme.font(java.awt.Font.PLAIN, 15f));
        field.setMargin(new Insets(10, 18, 10, 18));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SeuTheme.FIELD_BORDER, 1),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));
        field.putClientProperty("JComponent.roundRect", Boolean.TRUE);
        field.setPreferredSize(new Dimension(320, 48));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
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
