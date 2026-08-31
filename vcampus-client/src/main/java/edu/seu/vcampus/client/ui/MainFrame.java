package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.AcademicClientService;
import edu.seu.vcampus.client.service.CourseClientService;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.message.RequestMessage;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.Serializable;

/** Main navigation shell for the five required modules. */
public final class MainFrame extends JFrame {
    private static final String[] CARD_NAMES = {
            "home", "student", "course", "library", "store"
    };
    private static final String[] BUTTON_NAMES = {
            "首页", "学籍管理", "选课系统", "图书馆", "校园商店"
    };

    private final ClientConnection connection;
    private final LoginResponse session;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    public MainFrame(ClientConnection connection, LoginResponse session) {
        super("东南大学虚拟校园");
        this.connection = connection;
        this.session = session;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 650));
        setSize(1120, 720);
        setLocationRelativeTo(null);
        buildUi();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                logoutAndClose();
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
                session.getRole()), CARD_NAMES[1]);
        cards.add(new CourseManagementPanel(
                new CourseClientService(connection, session.getSessionToken()),
                new AcademicClientService(connection, session.getSessionToken()),
                session.getRole()), CARD_NAMES[2]);
        cards.add(new ModulePanel("图书馆",
                "图书查询、借阅、归还与库存管理。"), CARD_NAMES[3]);
        cards.add(new ModulePanel("校园商店",
                "商品、购物车、订单与库存管理。"), CARD_NAMES[4]);
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
            JButton button = new JButton(BUTTON_NAMES[i]);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.addActionListener(event -> cardLayout.show(cards, cardName));
            menu.add(button);
        }
        sidebar.add(menu, BorderLayout.CENTER);
        return sidebar;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 224, 230)),
                BorderFactory.createEmptyBorder(14, 24, 14, 24)));
        JLabel identity = new JLabel(session.getDisplayName() + "  ·  " + session.getRole().name());
        header.add(identity, BorderLayout.EAST);
        return header;
    }

    private void logoutAndClose() {
        try {
            connection.request(new RequestMessage<Serializable>(
                    Operation.USER_LOGOUT, session.getSessionToken(), null));
        } catch (Exception ignored) {
            // Closing the connection is sufficient if the server is unavailable.
        } finally {
            try {
                connection.close();
            } catch (IOException ignored) {
                // Window is already closed.
            }
        }
    }
}
