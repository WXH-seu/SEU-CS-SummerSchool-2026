package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.common.enums.SubSystemRole;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;

/** Placeholder view that gives each module owner an isolated starting point. */
public final class ModulePanel extends JPanel {
    public ModulePanel(String title, String description) {
        this(title, description, null);
    }

    /** Creates a business-module placeholder with its already-normalised role. */
    public ModulePanel(String title, String description, SubSystemRole effectiveRole) {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(36, 40, 36, 40));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 26f));
        add(titleLabel, BorderLayout.NORTH);

        String roleDescription = effectiveRole == null ? ""
                : "<br><br>当前子系统角色：" + effectiveRole.getDisplayName();
        JLabel descriptionLabel = new JLabel(
                "<html><div style='text-align:center'>" + description + roleDescription
                        + "<br><br>模块接口已预留，可独立开发并接入。</div></html>",
                SwingConstants.CENTER);
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(16f));
        add(descriptionLabel, BorderLayout.CENTER);
    }
}
