package edu.seu.vcampus.client.ui.components;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;

/**
 * 统一按钮工厂。主按钮对应门户绿色「搜索」，次按钮为白底描边，危险按钮用于删除。
 */
public final class SeuButtons {
    private SeuButtons() {
    }

    /** 绿色主按钮（查询 / 保存 / 确认）。 */
    public static JButton primary(String text) {
        return styled(text, SeuTheme.PRIMARY, Color.WHITE, SeuTheme.PRIMARY_HOVER, true);
    }

    /** 白底描边次按钮（编辑 / 取消 / 普通操作）。 */
    public static JButton secondary(String text) {
        JButton button = styled(text, SeuTheme.SURFACE, SeuTheme.TEXT, new Color(0xF3F4F6), false);
        button.putClientProperty("JButton.buttonType", "bordered");
        return button;
    }

    /** 金黄强调按钮（门户导航激活态，可用于首页入口等）。 */
    public static JButton accent(String text) {
        return styled(text, SeuTheme.ACCENT, SeuTheme.TEXT, SeuTheme.ACCENT_HOVER, false);
    }

    /** 危险操作按钮（删除）。 */
    public static JButton danger(String text) {
        return styled(text, SeuTheme.DANGER, Color.WHITE, new Color(0xA93226), true);
    }

    /** 无边框文字按钮（「更多」「链接」类）。 */
    public static JButton link(String text) {
        JButton button = new JButton(text);
        button.setFont(SeuTheme.bodyFont());
        button.setForeground(SeuTheme.PRIMARY);
        button.setBackground(SeuTheme.SURFACE);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(4, 6, 4, 6));
        return button;
    }

    /** 深绿顶栏上的浅色文字按钮（账号管理 / 退出登录）。 */
    public static JButton headerLink(String text) {
        JButton button = link(text);
        button.setForeground(Color.WHITE);
        button.setBackground(SeuTheme.PRIMARY);
        return button;
    }

    /** 身份认证页大号圆角登录按钮。 */
    public static JButton pillPrimary(String text) {
        JButton button = styled(text, SeuTheme.LOGIN_GREEN, Color.WHITE,
                SeuTheme.LOGIN_GREEN_HOVER, true);
        button.setFont(SeuTheme.font(java.awt.Font.BOLD, 18f));
        button.setPreferredSize(new Dimension(320, 48));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        button.setMargin(new Insets(10, 24, 10, 24));
        return button;
    }

    private static JButton styled(String text, Color background, Color foreground,
                                  Color hover, boolean makeDefault) {
        JButton button = new JButton(text);
        button.setFont(SeuTheme.bodyFont());
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(7, 16, 7, 16));
        button.setPreferredSize(new Dimension(
                Math.max(button.getPreferredSize().width, 72),
                34));
        button.putClientProperty("JButton.buttonType", "roundRect");
        if (makeDefault) {
            button.putClientProperty("JComponent.roundRect", Boolean.TRUE);
        }
        button.addChangeListener(event -> {
            if (!button.isEnabled()) {
                return;
            }
            if (button.getModel().isRollover() || button.getModel().isPressed()) {
                button.setBackground(hover);
            } else {
                button.setBackground(background);
            }
        });
        return button;
    }
}
