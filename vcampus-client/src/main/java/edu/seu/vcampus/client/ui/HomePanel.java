package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.ui.components.SeuAppTile;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;

/**
 * 门户首页「应用中心」：以卡片展示四个业务模块入口。
 */
public final class HomePanel extends JPanel {
    /** 模块跳转回调，参数为 CardLayout 名称。 */
    public interface Navigator {
        void openModule(String cardName);
    }

    public HomePanel(LoginResponse session, Navigator navigator) {
        super(new BorderLayout(0, SeuTheme.SPACE_MD));
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        if (navigator == null) {
            throw new IllegalArgumentException("navigator is required");
        }
        setBackground(SeuTheme.PAGE_BG);
        setBorder(SeuTheme.pageBorder());
        buildUi(session, navigator);
    }

    private void buildUi(LoginResponse session, final Navigator navigator) {
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(SeuLabels.title("应用中心"), BorderLayout.WEST);
        heading.add(SeuLabels.status("欢迎，" + nullToEmpty(session.getDisplayName())),
                BorderLayout.EAST);

        JPanel sectionTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sectionTitle.setOpaque(false);
        JLabel allApps = SeuLabels.subtitle("全部应用");
        allApps.setForeground(SeuTheme.PRIMARY);
        sectionTitle.add(allApps);

        JPanel grid = new JPanel(new GridLayout(1, 4, SeuTheme.SPACE_MD, SeuTheme.SPACE_MD));
        grid.setOpaque(false);
        grid.add(tile("学", "学籍管理", roleHint(session, SubSystem.STUDENT), "student", navigator));
        grid.add(tile("课", "选课系统", roleHint(session, SubSystem.COURSE), "course", navigator));
        grid.add(tile("书", "图书馆", roleHint(session, SubSystem.LIBRARY), "library", navigator));
        grid.add(tile("店", "校园商店", roleHint(session, SubSystem.STORE), "store", navigator));

        JPanel card = SeuPanels.card();
        JPanel cardBody = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        cardBody.setOpaque(false);
        cardBody.add(sectionTitle, BorderLayout.NORTH);
        cardBody.add(grid, BorderLayout.CENTER);
        cardBody.add(SeuLabels.muted("点击卡片进入对应模块。管理员标识表示你在该子系统拥有维护权限。"),
                BorderLayout.SOUTH);
        card.add(cardBody, BorderLayout.CENTER);

        JPanel north = new JPanel(new BorderLayout(0, SeuTheme.SPACE_MD));
        north.setOpaque(false);
        north.add(heading, BorderLayout.NORTH);
        north.add(SeuLabels.muted("东南大学虚拟校园 · 统一业务入口"), BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(card, BorderLayout.CENTER);
    }

    private SeuAppTile tile(String glyph, String title, String description,
                            final String cardName, final Navigator navigator) {
        return new SeuAppTile(glyph, title, description, new Runnable() {
            @Override
            public void run() {
                navigator.openModule(cardName);
            }
        });
    }

    private String roleHint(LoginResponse session, SubSystem subSystem) {
        SubSystemRole role = SubSystems.effectiveRole(
                session.getRole(), session.getAdminScopes(), subSystem);
        if (role == SubSystemRole.ADMIN) {
            return "管理员 · 可维护";
        }
        if (role == SubSystemRole.TEACHER) {
            return "教师 · 可使用";
        }
        return "学生 · 可使用";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
