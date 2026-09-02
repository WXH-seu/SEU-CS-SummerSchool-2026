package edu.seu.vcampus.client.ui.components;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 应用中心卡片：图标字 + 标题 + 说明，点击进入模块（对齐门户应用入口）。
 */
public final class SeuAppTile extends JPanel {
    private final Runnable action;
    private boolean hovering;

    public SeuAppTile(String glyph, String title, String description, Runnable action) {
        super(new BorderLayout(0, SeuTheme.SPACE_SM));
        this.action = action;
        setOpaque(true);
        setBackground(SeuTheme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SeuTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(20, 16, 18, 16)));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(180, 160));

        JLabel icon = new JLabel(glyph == null ? "应用" : glyph, SwingConstants.CENTER);
        icon.setFont(SeuTheme.font(Font.BOLD, 28f));
        icon.setForeground(SeuTheme.PRIMARY_SOFT);
        icon.setOpaque(false);

        JLabel titleLabel = new JLabel(title == null ? "" : title, SwingConstants.CENTER);
        titleLabel.setFont(SeuTheme.font(Font.BOLD, 15f));
        titleLabel.setForeground(SeuTheme.TEXT);

        JLabel descLabel = new JLabel(
                "<html><div style='text-align:center;width:140px'>"
                        + (description == null ? "" : description) + "</div></html>",
                SwingConstants.CENTER);
        descLabel.setFont(SeuTheme.smallFont());
        descLabel.setForeground(SeuTheme.TEXT_MUTED);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 0, 10, 0);
        center.add(icon, constraints);
        constraints.gridy = 1;
        constraints.insets = new Insets(0, 0, 6, 0);
        center.add(titleLabel, constraints);
        constraints.gridy = 2;
        constraints.insets = new Insets(0, 0, 0, 0);
        center.add(descLabel, constraints);
        add(center, BorderLayout.CENTER);

        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovering = true;
                setBackground(new Color(0xF7FAF6));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(SeuTheme.PRIMARY_SOFT, 1),
                        BorderFactory.createEmptyBorder(20, 16, 18, 16)));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovering = false;
                setBackground(SeuTheme.SURFACE);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(SeuTheme.BORDER, 1),
                        BorderFactory.createEmptyBorder(20, 16, 18, 16)));
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (SeuAppTile.this.action != null) {
                    SeuAppTile.this.action.run();
                }
            }
        };
        addMouseListener(adapter);
        icon.addMouseListener(adapter);
        titleLabel.addMouseListener(adapter);
        descLabel.addMouseListener(adapter);
        center.addMouseListener(adapter);
    }

    public boolean isHovering() {
        return hovering;
    }
}
