package edu.seu.vcampus.client.ui.components;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * 统一页面骨架：内容页、卡片、工具条、标题行。
 */
public final class SeuPanels {
    private SeuPanels() {
    }

    /** 带页面边距与浅灰底的根面板（BorderLayout）。 */
    public static JPanel page() {
        JPanel panel = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        panel.setBackground(SeuTheme.PAGE_BG);
        panel.setBorder(SeuTheme.pageBorder());
        return panel;
    }

    /** 白底圆角风格卡片（用边框模拟圆角，兼容 Java 8）。 */
    public static JPanel card() {
        JPanel panel = new JPanel(new BorderLayout(0, SeuTheme.SPACE_SM));
        panel.setBackground(SeuTheme.SURFACE);
        panel.setBorder(SeuTheme.cardBorder());
        panel.setOpaque(true);
        return panel;
    }

    /** 左对齐工具条 / 筛选行。 */
    public static JPanel toolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, SeuTheme.SPACE_SM, SeuTheme.SPACE_XS));
        panel.setOpaque(false);
        return panel;
    }

    /**
     * 标题行：左侧标题，右侧状态。
     *
     * @param title  主标题文案
     * @param status 可为 {@code null}，为 null 时不放置状态标签
     */
    public static JPanel heading(String title, JLabel status) {
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(SeuLabels.title(title), BorderLayout.WEST);
        if (status != null) {
            heading.add(status, BorderLayout.EAST);
        }
        return heading;
    }

    /**
     * 垂直堆叠若干行（筛选行 + 操作行），行间距统一。
     */
    public static JPanel stack(JComponent... rows) {
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        if (rows != null) {
            for (int i = 0; i < rows.length; i++) {
                if (rows[i] == null) {
                    continue;
                }
                rows[i].setAlignmentX(JComponent.LEFT_ALIGNMENT);
                stack.add(rows[i]);
                if (i < rows.length - 1) {
                    stack.add(Box.createRigidArea(new Dimension(0, SeuTheme.SPACE_SM)));
                }
            }
        }
        return stack;
    }

    /** 顶栏风格条：深绿底，白字区域用。 */
    public static JPanel brandBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(SeuTheme.PRIMARY);
        bar.setBorder(BorderFactory.createEmptyBorder(
                SeuTheme.SPACE_SM + 2, SeuTheme.SPACE_LG,
                SeuTheme.SPACE_SM + 2, SeuTheme.SPACE_LG));
        return bar;
    }

    /** 透明分隔用空面板，便于 BorderLayout 占位。 */
    public static JPanel gap(int height) {
        JPanel gap = new JPanel();
        gap.setOpaque(false);
        gap.setPreferredSize(new Dimension(1, height));
        return gap;
    }
}
