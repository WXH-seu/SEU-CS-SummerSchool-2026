package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.config.ClientConfig;
import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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

/** Login screen that keeps network operations away from the Swing event thread. */
public final class LoginFrame extends JFrame {
    private final ClientConfig config;
    private final JTextField userIdField = new JTextField("admin", 20);
    private final JPasswordField passwordField = new JPasswordField("admin123", 20);
    private final JButton loginButton = new JButton("登录");
    private final JLabel statusLabel = new JLabel(" ");

    public LoginFrame(ClientConfig config) {
        super("登录 - 东南大学虚拟校园");
        this.config = config;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        buildUi();
        pack();
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 22));
        root.setBorder(BorderFactory.createEmptyBorder(30, 42, 26, 42));

        JLabel title = new JLabel("东南大学虚拟校园", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 6, 7, 6);
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
        form.add(new JLabel("密码"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(passwordField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        form.add(loginButton, constraints);
        constraints.gridy = 3;
        form.add(statusLabel, constraints);
        root.add(form, BorderLayout.CENTER);

        JLabel hint = new JLabel("演示账号：admin / admin123", JLabel.CENTER);
        root.add(hint, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(loginButton);
        loginButton.addActionListener(event -> login());
    }

    private void login() {
        final String userId = userIdField.getText().trim();
        final char[] passwordChars = passwordField.getPassword();
        if (userId.isEmpty() || passwordChars.length == 0) {
            JOptionPane.showMessageDialog(this, "请输入账号和密码", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        setLoginEnabled(false, "正在连接服务器...");
        new SwingWorker<LoginResult, Void>() {
            @Override
            protected LoginResult doInBackground() throws Exception {
                String password = new String(passwordChars);
                Arrays.fill(passwordChars, '\0');
                ClientConnection connection = ClientConnection.connect(
                        config.getHost(), config.getPort());
                try {
                    RequestMessage<LoginRequest> request = new RequestMessage<LoginRequest>(
                            Operation.USER_LOGIN, null, new LoginRequest(userId, password));
                    ResponseMessage<?> response = connection.request(request);
                    if (!response.isSuccess() || !(response.getBody() instanceof LoginResponse)) {
                        connection.close();
                        throw new LoginFailureException(response.getMessage());
                    }
                    return new LoginResult(connection, (LoginResponse) response.getBody());
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
                    new MainFrame(result.connection, result.session).setVisible(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showFailure("登录被中断");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    showFailure(cause == null ? "登录失败" : cause.getMessage());
                }
            }
        }.execute();
    }

    private void setLoginEnabled(boolean enabled, String status) {
        loginButton.setEnabled(enabled);
        userIdField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        statusLabel.setText(status);
    }

    private void showFailure(String message) {
        setLoginEnabled(true, " ");
        JOptionPane.showMessageDialog(this, message, "登录失败", JOptionPane.ERROR_MESSAGE);
    }

    private static final class LoginResult {
        private final ClientConnection connection;
        private final LoginResponse session;

        private LoginResult(ClientConnection connection, LoginResponse session) {
            this.connection = connection;
            this.session = session;
        }
    }

    private static final class LoginFailureException extends Exception {
        private static final long serialVersionUID = 1L;

        private LoginFailureException(String message) {
            super(message == null ? "账号或密码错误" : message);
        }
    }
}
