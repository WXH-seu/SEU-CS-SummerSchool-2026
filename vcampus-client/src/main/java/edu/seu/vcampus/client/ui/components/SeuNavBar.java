package edu.seu.vcampus.client.ui.components;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

/**
 * 门户二级导航条：深绿底，激活项金黄块（对齐信息服务门户「应用中心」样式）。
 * 页签按可用宽度均分，窗口缩小时仍保持全部可见。
 */
public final class SeuNavBar extends JPanel {
    /** 导航选中回调。 */
    public interface SelectionListener {
        void onSelected(int index, String key);
    }

    private final String[] keys;
    private final JButton[] buttons;
    private final SelectionListener listener;
    private int activeIndex;

    public SeuNavBar(String[] keys, String[] labels, SelectionListener listener) {
        super(new GridLayout(1, keys == null ? 1 : keys.length, 0, 0));
        if (keys == null || labels == null || keys.length != labels.length || keys.length == 0) {
            throw new IllegalArgumentException("keys/labels required and must match");
        }
        this.keys = keys.clone();
        this.buttons = new JButton[keys.length];
        this.listener = listener;
        this.activeIndex = 0;

        setBackground(SeuTheme.PRIMARY);
        setOpaque(true);

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            JButton button = new JButton(labels[i]);
            button.setFont(SeuTheme.font(Font.BOLD, 14f));
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setContentAreaFilled(true);
            button.setOpaque(true);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setHorizontalAlignment(SwingConstants.CENTER);
            button.setToolTipText(labels[i]);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent event) {
                    setActiveIndex(index);
                    if (SeuNavBar.this.listener != null) {
                        SeuNavBar.this.listener.onSelected(index, SeuNavBar.this.keys[index]);
                    }
                }
            });
            buttons[i] = button;
            add(button);
        }
        syncMetrics();
        refreshStyles();
    }

    /** 按当前字号重算导航条高度，窗口缩放后由 {@link SeuUiScale} 调用。 */
    public void syncMetrics() {
        setBorder(BorderFactory.createEmptyBorder(0, SeuTheme.scaled(SeuTheme.SPACE_LG),
                0, SeuTheme.scaled(SeuTheme.SPACE_LG)));
        int padV = SeuTheme.scaled(12);
        int padH = SeuTheme.scaled(8);
        int height = SeuTheme.scaled(44);
        for (int i = 0; i < buttons.length; i++) {
            JButton button = buttons[i];
            button.setMargin(new Insets(padV, padH, padV, padH));
            button.setPreferredSize(new Dimension(1, height));
            button.setMinimumSize(new Dimension(1, height));
        }
        revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        int height = barHeight();
        if (size == null) {
            return new Dimension(0, height);
        }
        return new Dimension(size.width, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, barHeight());
    }

    private int barHeight() {
        Insets insets = getInsets();
        return SeuTheme.scaled(44) + insets.top + insets.bottom;
    }

    public void setActiveKey(String key) {
        if (key == null) {
            return;
        }
        for (int i = 0; i < keys.length; i++) {
            if (key.equals(keys[i])) {
                setActiveIndex(i);
                return;
            }
        }
    }

    public void setActiveIndex(int index) {
        if (index < 0 || index >= buttons.length) {
            return;
        }
        activeIndex = index;
        refreshStyles();
    }

    public int getActiveIndex() {
        return activeIndex;
    }

    public String getActiveKey() {
        return keys[activeIndex];
    }

    private void refreshStyles() {
        for (int i = 0; i < buttons.length; i++) {
            boolean active = i == activeIndex;
            JButton button = buttons[i];
            if (active) {
                button.setBackground(SeuTheme.ACCENT);
                button.setForeground(SeuTheme.TEXT);
            } else {
                button.setBackground(SeuTheme.PRIMARY);
                button.setForeground(Color.WHITE);
            }
        }
    }
}
