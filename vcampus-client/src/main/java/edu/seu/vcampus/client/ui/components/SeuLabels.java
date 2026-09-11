package edu.seu.vcampus.client.ui.components;

import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * 统一标题、状态与辅助文字标签。
 */
public final class SeuLabels {
    private SeuLabels() {
    }

    /** 页面主标题（约 24pt 加粗）。 */
    public static JLabel title(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(SeuTheme.titleFont());
        label.setForeground(SeuTheme.TEXT);
        return label;
    }

    /** 区块小标题。 */
    public static JLabel subtitle(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(SeuTheme.subtitleFont());
        label.setForeground(SeuTheme.TEXT);
        return label;
    }

    /** 表单字段标签。 */
    public static JLabel field(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(SeuTheme.bodyFont());
        label.setForeground(SeuTheme.TEXT);
        return label;
    }

    /** 右侧状态 / 计数提示（灰色）。 */
    public static JLabel status(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(SeuTheme.smallFont());
        label.setForeground(SeuTheme.TEXT_MUTED);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    /** 辅助说明文字。 */
    public static JLabel muted(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(SeuTheme.smallFont());
        label.setForeground(SeuTheme.TEXT_MUTED);
        return label;
    }
}
