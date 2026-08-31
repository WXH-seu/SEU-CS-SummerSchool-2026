package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.config.ClientConfig;
import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.AcademicClientService;
import edu.seu.vcampus.client.service.CourseClientService;
import edu.seu.vcampus.client.service.LibraryClientService;
import edu.seu.vcampus.client.service.StoreClientService;
import edu.seu.vcampus.client.service.UserClientService;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;

import javax.swing.BorderFactory;
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
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * Main navigation shell for the five required modules. The header shows the
 * current identity together with account-management and logout actions, and
 * module buttons the current role cannot use are disabled with an explicit
 * permission hint. This mirrors the server-side {@code PermissionPolicy}:
 * a sub-system administrator may only open the business sub-systems granted in
 * his or her {@code adminScopes}.
 */
public final class MainFrame extends JFrame {
    private static final String[] CARD_NAMES = {
            "home", "student", "course", "library", "store"
    };
    private static final String[] BUTTON_NAMES = {
            "首页", "学籍管理", "选课系统", "图书馆", "校园商店"
    };

    private final ClientConfig config;
    private final ClientConnection connection;
    private final LoginResponse session;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JLabel identityLabel = new JLabel();
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
        root.add(createSidebar(), BorderLayout.WEST);
        root.add(createHeader(), BorderLayout.NORTH);

        cards.add(new ModulePanel("欢迎使用虚拟校园",
                "统一入口已经连接到 Java 8 C/S 服务端。"), CARD_NAMES[0]);
        cards.add(new AcademicManagementPanel(
                new AcademicClientService(connection, session.getSessionToken()),
                session.getRole(), session.getAdminScopes()), CARD_NAMES[1]);
        cards.add(new CourseManagementPanel(
                new CourseClientService(connection, session.getSessionToken()),
                new AcademicClientService(connection, session.getSessionToken()),
                session.getRole(), session.getAdminScopes()), CARD_NAMES[2]);
        cards.add(new LibraryPanel(
                new LibraryClientService(connection, session.getSessionToken())), CARD_NAMES[3]);
        cards.add(new StorePanel(
                new StoreClientService(connection, session.getSessionToken()),
                session.getRole(), session.getAdminScopes()), CARD_NAMES[4]);
        root.add(cards, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(210, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 14, 20, 14));

        JLabel brand = new JLabel("vCampus", SwingConstants.CENTER);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 24f));
        brand.setBorder(BorderFactory.createEmptyBorder(6, 0, 20, 0));
        sidebar.add(brand, BorderLayout.NORTH);

        JPanel menu = new JPanel(new GridLayout(0, 1, 0, 10));
        for (int i = 0; i < BUTTON_NAMES.length; i++) {
            final String cardName = CARD_NAMES[i];
            JButton button = new JButton(buildModuleLabel(cardName, BUTTON_NAMES[i]));
            button.setHorizontalAlignment(SwingConstants.LEFT);
            if (!canEnterModule(cardName, session.getRole())) {
                button.setEnabled(false);
                button.setToolTipText("当前角色无权访问" + BUTTON_NAMES[i] + "模块");
            }
            button.addActionListener(event -> cardLayout.show(cards, cardName));
            menu.add(button);
        }
        sidebar.add(menu, BorderLayout.CENTER);
        return sidebar;
    }

    /**
     * Builds the sidebar label for a module, appending "（管理员）" when the
     * current session is an administrator of that sub-system. This mirrors the
     * normalized three-tier role that sub-system modules observe
     * ({@link SubSystems#effectiveRole}).
     */
    private String buildModuleLabel(String cardName, String baseName) {
        SubSystem subSystem = SubSystems.byKey(cardName);
        if (subSystem != null && SubSystems.effectiveRole(
                session.getRole(), session.getAdminScopes(), subSystem) == SubSystemRole.ADMIN) {
            return baseName + "（管理员）";
        }
        return baseName;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 224, 230)),
                BorderFactory.createEmptyBorder(12, 24, 12, 24)));

        JLabel appTitle = new JLabel("东南大学虚拟校园");
        appTitle.setFont(appTitle.getFont().deriveFont(Font.BOLD, 16f));
        header.add(appTitle, BorderLayout.WEST);

        JPanel identityPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        identityLabel.setText(buildIdentityText());
        identityPanel.add(identityLabel);

        JButton accountButton = new JButton("账号管理");
        accountButton.addActionListener(event ->
                new AccountFrame(config, MainFrame.this, connection, session).setVisible(true));
        identityPanel.add(accountButton);

        JButton logoutButton = new JButton("退出登录");
        logoutButton.addActionListener(event -> logout());
        identityPanel.add(logoutButton);

        header.add(identityPanel, BorderLayout.EAST);
        return header;
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

    /**
     * Client-side mirror of the module entries in the server-side permission
     * matrix. Keep this in sync with {@code PermissionPolicy} when modules are
     * integrated. A sub-system administrator keeps ordinary usage rights in
     * every sub-system (it is normalized to a teacher there) and only gains
     * management authority in its granted sub-systems, so it may enter every
     * module.
     */
    private static boolean canEnterModule(String cardName, Role role) {
        if (role == Role.SUBSYSADMIN) {
            return true;
        }
        if ("student".equals(cardName)) {
            return role == Role.TEACHER || role == Role.SUPER_ADMIN;
        }
        return true;
    }
}
