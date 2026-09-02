package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.config.ClientConfig;
import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.AcademicClientService;
import edu.seu.vcampus.client.service.CourseClientService;
import edu.seu.vcampus.client.service.LibraryClientService;
import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.client.service.UserClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuNavBar;
import edu.seu.vcampus.client.ui.components.SeuPanels;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * 门户主壳：深绿顶栏、金黄激活导航、内容区 CardLayout。
 * 各业务页只接收当前子系统有效角色；服务端 {@code PermissionPolicy} 仍是最终鉴权。
 */
public final class MainFrame extends JFrame {
    private static final String[] CARD_NAMES = {
            "home", "student", "course", "library", "store"
    };
    private static final String[] NAV_LABELS = {
            "应用中心", "学籍管理", "选课系统", "图书馆", "校园商店"
    };

    private final ClientConfig config;
    private final ClientConnection connection;
    private final LoginResponse session;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JLabel identityLabel = new JLabel();
    private SeuNavBar navBar;
    private String displayName;

    public MainFrame(ClientConfig config, ClientConnection connection, LoginResponse session) {
        super("东南大学虚拟校园");
        this.config = config;
        this.connection = connection;
        this.session = session;
        this.displayName = session.getDisplayName();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 650));
        setSize(1120, 720);
        setLocationRelativeTo(null);
        buildUi();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closeConnectionQuietly();
            }
        });
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(SeuTheme.PAGE_BG);

        JPanel chrome = new JPanel(new BorderLayout());
        chrome.setOpaque(false);
        chrome.add(createHeader(), BorderLayout.NORTH);
        chrome.add(createNavBar(), BorderLayout.SOUTH);
        root.add(chrome, BorderLayout.NORTH);

        cards.setBackground(SeuTheme.PAGE_BG);
        cards.add(new HomePanel(session, new HomePanel.Navigator() {
            @Override
            public void openModule(String cardName) {
                showCard(cardName);
            }
        }), CARD_NAMES[0]);
        cards.add(new AcademicManagementPanel(
                new AcademicClientService(connection, session.getSessionToken()),
                effectiveRole(SubSystem.STUDENT)), CARD_NAMES[1]);
        cards.add(new CourseManagementPanel(
                new CourseClientService(connection, session.getSessionToken()),
                new AcademicClientService(connection, session.getSessionToken()),
                effectiveRole(SubSystem.COURSE)), CARD_NAMES[2]);
        cards.add(new LibraryPanel(
                new LibraryClientService(connection, session.getSessionToken()),
                effectiveRole(SubSystem.LIBRARY)), CARD_NAMES[3]);
        cards.add(new StorePanel(
                new StoreClientService(connection, session.getSessionToken()),
                session.getRole(), session.getAdminScopes()), CARD_NAMES[4]);
        root.add(cards, BorderLayout.CENTER);
        setContentPane(root);
        showCard(CARD_NAMES[0]);
    }

    private JPanel createHeader() {
        JPanel header = SeuPanels.brandBar();

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        JLabel university = new JLabel("东南大学");
        university.setFont(SeuTheme.font(Font.BOLD, 18f));
        university.setForeground(Color.WHITE);
        JLabel divider = new JLabel("|");
        divider.setForeground(new Color(255, 255, 255, 160));
        JLabel portal = new JLabel("虚拟校园");
        portal.setFont(SeuTheme.font(Font.PLAIN, 16f));
        portal.setForeground(Color.WHITE);
        brand.add(university);
        brand.add(divider);
        brand.add(portal);
        header.add(brand, BorderLayout.WEST);

        JLabel slogan = new JLabel("涉密不上网，上网不涉密", SwingConstants.CENTER);
        slogan.setFont(SeuTheme.font(Font.BOLD, 13f));
        slogan.setForeground(new Color(0xF5C6C2));
        header.add(slogan, BorderLayout.CENTER);

        JPanel identityPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        identityPanel.setOpaque(false);
        identityLabel.setText(buildIdentityText());
        identityLabel.setFont(SeuTheme.bodyFont());
        identityLabel.setForeground(Color.WHITE);
        identityPanel.add(identityLabel);

        JButton accountButton = SeuButtons.headerLink("账号管理");
        accountButton.addActionListener(event ->
                new AccountFrame(config, MainFrame.this, connection, session).setVisible(true));
        identityPanel.add(accountButton);

        JButton logoutButton = SeuButtons.headerLink("退出登录");
        logoutButton.addActionListener(event -> logout());
        identityPanel.add(logoutButton);

        header.add(identityPanel, BorderLayout.EAST);
        return header;
    }

    private SeuNavBar createNavBar() {
        String[] labels = new String[NAV_LABELS.length];
        for (int i = 0; i < NAV_LABELS.length; i++) {
            labels[i] = buildModuleLabel(CARD_NAMES[i], NAV_LABELS[i]);
        }
        navBar = new SeuNavBar(CARD_NAMES, labels, new SeuNavBar.SelectionListener() {
            @Override
            public void onSelected(int index, String key) {
                cardLayout.show(cards, key);
            }
        });
        return navBar;
    }

    private void showCard(String cardName) {
        if (cardName == null) {
            return;
        }
        cardLayout.show(cards, cardName);
        if (navBar != null) {
            navBar.setActiveKey(cardName);
        }
    }

    /**
     * 导航导航标签；子系统管理员在对应模块追加「（管理员）」。
     */
    private String buildModuleLabel(String cardName, String baseName) {
        if ("home".equals(cardName)) {
            return baseName;
        }
        SubSystem subSystem = SubSystems.byKey(cardName);
        if (subSystem != null && effectiveRole(subSystem) == SubSystemRole.ADMIN) {
            return baseName + "（管理员）";
        }
        return baseName;
    }

    /** Refreshes the header after the display name has been changed. */
    public void updateDisplayName(String newDisplayName) {
        this.displayName = newDisplayName;
        identityLabel.setText(buildIdentityText());
    }

    private String buildIdentityText() {
        return displayName + "（" + RoleNames.of(session.getRole()) + "）";
    }

    private void logout() {
        final UserClientService service = new UserClientService(connection);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    service.logout(session.getSessionToken());
                } catch (Exception ignored) {
                    // Closing the connection is sufficient when the server is gone.
                }
                return null;
            }

            @Override
            protected void done() {
                dispose();
                closeConnectionQuietly();
                new LoginFrame(config).setVisible(true);
            }
        }.execute();
    }

    private void closeConnectionQuietly() {
        try {
            connection.close();
        } catch (IOException ignored) {
            // Window is already closed.
        }
    }

    /** Converts the login role once, before handing control to a business UI. */
    private SubSystemRole effectiveRole(SubSystem subSystem) {
        return SubSystems.effectiveRole(
                session.getRole(), session.getAdminScopes(), subSystem);
    }
}
