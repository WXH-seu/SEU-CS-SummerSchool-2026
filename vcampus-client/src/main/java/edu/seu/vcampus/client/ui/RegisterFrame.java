package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.config.ClientConfig;
import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.ClientServiceException;
import edu.seu.vcampus.client.service.UserClientService;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.enums.Role;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Registration screen. A new account is created with a chosen role and signed
 * in automatically when registration succeeds.
 */
public final class RegisterFrame extends JFrame {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final ClientConfig config;
    private final LoginFrame loginFrame;
    private final JTextField userIdField = new JTextField(16);
    private final JTextField displayNameField = new JTextField(16);
    private final JPasswordField passwordField = new JPasswordField(16);
    private final JPasswordField confirmField = new JPasswordField(16);
    private final JComboBox<String> roleBox = new JComboBox<String>(
            new String[]{"学生", "教师", "管理员"});
    private final JButton registerButton = new JButton("注册");
    private final JLabel statusLabel = new JLabel(" ");

    public RegisterFrame(ClientConfig config, LoginFrame loginFrame) {
        super("注册新账号 - 东南大学虚拟校园");
        this.config = config;
        this.loginFrame = loginFrame;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        buildUi();
        pack();
        setLocationRelativeTo(loginFrame);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBorder(BorderFactory.createEmptyBorder(28, 40, 24, 40));

        JLabel title = new JLabel("注册新账号", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        form.add(new JLabel("账号"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(userIdField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        form.add(new JLabel("显示名"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(displayNameField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        form.add(new JLabel("密码"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(passwordField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.weightx = 0;
        form.add(new JLabel("确认密码"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(confirmField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.weightx = 0;
        form.add(new JLabel("角色"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(roleBox, constraints);

        JPanel buttons = new JPanel(new GridBagLayout());
        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.insets = new Insets(4, 8, 4, 8);
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 0;
        buttons.add(registerButton, buttonConstraints);
        buttonConstraints.gridx = 1;
        JButton cancelButton = new JButton("返回");
        buttons.add(cancelButton, buttonConstraints);
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 1;
        buttonConstraints.gridwidth = 2;
        buttons.add(statusLabel, buttonConstraints);

        form.add(buttons, constraints);
        constraints.gridwidth = 2;
        constraints.gridy = 5;
        root.add(form, BorderLayout.CENTER);

        setContentPane(root);
        getRootPane().setDefaultButton(registerButton);
        registerButton.addActionListener(event -> register());
        cancelButton.addActionListener(event -> dispose());
    }

    private void register() {
        final String userId = userIdField.getText().trim();
        final String displayName = displayNameField.getText().trim();
        final char[] passwordChars = passwordField.getPassword();
        final char[] confirmChars = confirmField.getPassword();
        if (userId.isEmpty() || displayName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入账号和显示名", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (passwordChars.length < MIN_PASSWORD_LENGTH) {
            JOptionPane.showMessageDialog(this,
                    "密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!Arrays.equals(passwordChars, confirmChars)) {
            JOptionPane.showMessageDialog(this, "两次输入的密码不一致", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        final Role role = selectedRole();
        setFormEnabled(false, "正在注册...");
        new SwingWorker<LoginResult, Void>() {
            @Override
            protected LoginResult doInBackground() throws Exception {
                String password = new String(passwordChars);
                Arrays.fill(passwordChars, '\0');
                Arrays.fill(confirmChars, '\0');
                ClientConnection connection = ClientConnection.connect(
                        config.getHost(), config.getPort());
                try {
                    UserClientService service = new UserClientService(connection);
                    LoginResponse session = service.register(
                            new RegisterRequest(userId, password, displayName, role));
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
                    if (loginFrame != null) {
                        loginFrame.dispose();
                    }
                    new MainFrame(config, result.connection, result.session).setVisible(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showFailure("注册被中断");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof ClientServiceException) {
                        showFailure(cause.getMessage());
                    } else {
                        showFailure(cause == null ? "注册失败" : "无法连接服务器：" + cause.getMessage());
                    }
                }
            }
        }.execute();
    }

    private Role selectedRole() {
        int index = roleBox.getSelectedIndex();
        if (index == 1) {
            return Role.TEACHER;
        }
        if (index == 2) {
            return Role.ADMIN;
        }
        return Role.STUDENT;
    }

    private void setFormEnabled(boolean enabled, String status) {
        registerButton.setEnabled(enabled);
        userIdField.setEnabled(enabled);
        displayNameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmField.setEnabled(enabled);
        roleBox.setEnabled(enabled);
        statusLabel.setText(status);
    }

    private void showFailure(String message) {
        setFormEnabled(true, " ");
        JOptionPane.showMessageDialog(this, message, "注册失败", JOptionPane.ERROR_MESSAGE);
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
