package edu.seu.vcampus.client.ui.components;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;

/**
 * 门户二级导航条：深绿底，激活项金黄块（对齐信息服务门户「应用中心」样式）。
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
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        if (keys == null || labels == null || keys.length != labels.length || keys.length == 0) {
            throw new IllegalArgumentException("keys/labels required and must match");
        }
        this.keys = keys.clone();
        this.buttons = new JButton[keys.length];
        this.listener = listener;
        this.activeIndex = 0;

        setBackground(SeuTheme.PRIMARY);
        setBorder(BorderFactory.createEmptyBorder(0, SeuTheme.SPACE_LG, 0, SeuTheme.SPACE_LG));
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
            button.setMargin(new Insets(12, 22, 12, 22));
            button.setHorizontalAlignment(SwingConstants.CENTER);
            button.setPreferredSize(new Dimension(
                    Math.max(button.getPreferredSize().width + 28, 110), 44));
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
        refreshStyles();
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
