package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.config.ClientConfig;
import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.ClientServiceException;
import edu.seu.vcampus.client.service.UserClientService;
import edu.seu.vcampus.client.ui.components.SeuBrandHeader;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.LoginResponse;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * 身份认证中心风格登录页。网络请求在 {@link SwingWorker} 中执行。
 * 「记住密码」仅作版式展示；「修改密码」提示登录后前往账号管理。
 */
public final class LoginFrame extends JFrame {
    private final ClientConfig config;
    private final JTextField userIdField = SeuFields.pillText(22);
    private final JPasswordField passwordField = new JPasswordField(22);
    private final JPanel passwordRow = SeuFields.pillPasswordWithToggle(passwordField);
    private final JButton loginButton = SeuButtons.pillPrimary("登 录");
    private final JCheckBox rememberPassword = new JCheckBox("记住密码");
    private final JButton changePasswordLink = SeuButtons.link("修改密码");
    private final JLabel statusLabel = SeuLabels.muted(" ");

    public LoginFrame(ClientConfig config) {
        super("身份认证中心 - 东南大学虚拟校园");
        this.config = config;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        buildUi();
        pack();
        setMinimumSize(new Dimension(460, getHeight()));
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        root.setBorder(SeuTheme.empty(36, 56, 28, 56));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        SeuBrandHeader brand = new SeuBrandHeader();
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(brand);
        center.add(Box.createVerticalStrut(28));

        JLabel authTitle = SeuBrandHeader.authTitle();
        center.add(authTitle);
        center.add(Box.createVerticalStrut(36));

        SeuFields.setPlaceholder(userIdField, "请输入账号");
        SeuFields.setPlaceholder(passwordField, "请输入密码");
        // 演示方便：预填常用账号，版式仍对齐认证中心
        userIdField.setText("admin");
        passwordField.setText("admin123");

        userIdField.setAlignmentX(Component.CENTER_ALIGNMENT);
        passwordRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(userIdField);
        center.add(Box.createVerticalStrut(16));
        center.add(passwordRow);
        center.add(Box.createVerticalStrut(28));
        center.add(loginButton);
        center.add(Box.createVerticalStrut(18));

        rememberPassword.setFont(SeuTheme.bodyFont());
        rememberPassword.setForeground(SeuTheme.TEXT);
        rememberPassword.setOpaque(false);
        rememberPassword.setFocusPainted(false);
        changePasswordLink.setForeground(SeuTheme.LOGIN_GREEN);
        changePasswordLink.setFont(SeuTheme.bodyFont());

        JPanel extras = new JPanel(new BorderLayout());
        extras.setOpaque(false);
        extras.setMaximumSize(new Dimension(320, 36));
        extras.setAlignmentX(Component.CENTER_ALIGNMENT);
        extras.add(rememberPassword, BorderLayout.WEST);
        extras.add(changePasswordLink, BorderLayout.EAST);
        center.add(extras);
        center.add(Box.createVerticalStrut(10));
        center.add(statusLabel);
        center.add(Box.createVerticalStrut(16));

        JLabel hint = SeuLabels.muted(
                "<html><div style='text-align:center;width:340px'>"
                        + "演示账号：admin / admin123，student / student123，teacher / teacher123。"
                        + "<br>账号由管理员统一创建，如需新账号请联系管理员。"
                        + "</div></html>");
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(hint);

        // 外层再包一层，保证窗口较宽时内容仍居中
        JPanel frame = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        frame.setOpaque(false);
        frame.add(center);
        root.add(frame, BorderLayout.CENTER);
        setContentPane(root);

        getRootPane().setDefaultButton(loginButton);
        loginButton.addActionListener(event -> login());
        changePasswordLink.addActionListener(event -> SeuMessages.info(this,
                "请先登录，再在右上角「账号管理」中修改密码。"));
        rememberPassword.addActionListener(event -> {
            if (rememberPassword.isSelected()) {
                SeuMessages.info(this, "当前版本仅作界面展示，不会在本地保存密码。");
                rememberPassword.setSelected(false);
            }
        });
    }

    private void login() {
        final String userId = userIdField.getText().trim();
        final char[] passwordChars = passwordField.getPassword();
        if (userId.isEmpty() || passwordChars.length == 0) {
            SeuMessages.info(this, "请输入账号和密码");
            return;
        }
        setLoginEnabled(false, "正在连接服务器…");
        new SwingWorker<LoginResult, Void>() {
            @Override
            protected LoginResult doInBackground() throws Exception {
                String password = new String(passwordChars);
                Arrays.fill(passwordChars, '\0');
                ClientConnection connection = ClientConnection.connect(
                        config.getHost(), config.getPort());
                try {
                    UserClientService service = new UserClientService(connection);
                    LoginResponse session = service.login(userId, password);
                    return new LoginResult(connection, session);
                } catch (Exception e) {
                    try {
                        connection.close();
                    } catch (Exception ignored) {
                        // Keep the original failure.
                    }
                    throw e;
                }
            }

            @Override
            protected void done() {
                try {
                    LoginResult result = get();
                    dispose();
                    new MainFrame(config, result.connection, result.session).setVisible(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showFailure("登录被中断");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof ClientServiceException) {
                        showFailure(cause.getMessage());
                    } else {
                        showFailure(cause == null ? "登录失败" : "无法连接服务器：" + cause.getMessage());
                    }
                }
            }
        }.execute();
    }

    private void setLoginEnabled(boolean enabled, String status) {
        loginButton.setEnabled(enabled);
        userIdField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        rememberPassword.setEnabled(enabled);
        changePasswordLink.setEnabled(enabled);
        statusLabel.setText(status == null ? " " : status);
    }

    private void showFailure(String message) {
        setLoginEnabled(true, " ");
        SeuMessages.error(this, message);
    }

    private static final class LoginResult {
        private final ClientConnection connection;
        private final LoginResponse session;

        private LoginResult(ClientConnection connection, LoginResponse session) {
            this.connection = connection;
            this.session = session;
        }
    }
}
