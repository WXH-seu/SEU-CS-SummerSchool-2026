package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.config.ClientConfig;
import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.ClientServiceException;
import edu.seu.vcampus.client.service.UserClientService;
import edu.seu.vcampus.client.ui.components.SeuButtons;
import edu.seu.vcampus.client.ui.components.SeuFields;
import edu.seu.vcampus.client.ui.components.SeuLabels;
import edu.seu.vcampus.client.ui.components.SeuMessages;
import edu.seu.vcampus.client.ui.components.SeuTheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** 忘记密码：输入邮箱 → 服务端发验证码 → 校验验证码并重置密码。 */
public final class ForgotPasswordFrame extends JFrame {
    private final ClientConfig config;
    private final JTextField emailField = SeuFields.pillText(24);
    private final JTextField codeField = SeuFields.pillText(24);
    private final JPasswordField newPasswordField = new JPasswordField(24);
    private final JPasswordField confirmField = new JPasswordField(24);
    private final JButton sendButton = SeuButtons.pillPrimary("发送验证码");
    private final JButton resetButton = SeuButtons.pillPrimary("重置密码");
    private final JLabel statusLabel = SeuLabels.muted(" ");

    public ForgotPasswordFrame(ClientConfig config) {
        super("忘记密码 - 东南大学虚拟校园");
        this.config = config;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        buildUi();
        pack();
        setMinimumSize(new Dimension(460, getHeight()));
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        root.setBorder(SeuTheme.empty(28, 48, 24, 48));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel title = SeuLabels.title("找回密码");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(title);
        center.add(Box.createVerticalStrut(20));

        SeuFields.setPlaceholder(emailField, "请输入已绑定的邮箱");
        center.add(labelRow("邮箱", emailField));
        center.add(Box.createVerticalStrut(8));
        sendButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(sendButton);
        center.add(Box.createVerticalStrut(18));

        center.add(labelRow("验证码", codeField));
        center.add(labelRow("新密码", newPasswordField));
        center.add(labelRow("确认新密码", confirmField));
        center.add(Box.createVerticalStrut(12));
        resetButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(resetButton);
        center.add(Box.createVerticalStrut(10));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(statusLabel);

        JPanel frame = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        frame.setOpaque(false);
        frame.add(center);
        root.add(frame, BorderLayout.CENTER);
        setContentPane(root);

        sendButton.addActionListener(event -> sendCode());
        resetButton.addActionListener(event -> resetPassword());
    }

    private JPanel labelRow(String label, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        JLabel l = SeuLabels.field(label);
        l.setVerticalAlignment(SwingConstants.CENTER);
        row.add(l);
        row.add(field);
        row.setMaximumSize(new Dimension(380, 36));
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        return row;
    }

    private void sendCode() {
        final String email = emailField.getText().trim();
        if (email.isEmpty()) {
            SeuMessages.info(this, "请输入邮箱");
            return;
        }
        setBusy(true, "正在发送验证码…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ClientConnection connection = ClientConnection.connect(config.getHost(), config.getPort());
                try {
                    new UserClientService(connection).forgotPassword(email);
                    return null;
                } finally {
                    try {
                        connection.close();
                    } catch (Exception ignored) {
                        // Preserve the original failure.
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    setBusy(false, "验证码已发送，请查收邮箱");
                    SeuMessages.info(ForgotPasswordFrame.this, "验证码已发送，请查收邮箱");
                } catch (Exception e) {
                    setBusy(false, " ");
                    showError(e);
                }
            }
        }.execute();
    }

    private void resetPassword() {
        final String email = emailField.getText().trim();
        final String code = codeField.getText().trim();
        final char[] p1 = newPasswordField.getPassword();
        final char[] p2 = confirmField.getPassword();
        if (email.isEmpty() || code.isEmpty() || p1.length == 0 || p2.length == 0) {
            SeuMessages.info(this, "请填写邮箱、验证码与新密码");
            return;
        }
        if (!Arrays.equals(p1, p2)) {
            SeuMessages.error(this, "两次输入的新密码不一致");
            return;
        }
        final String newPassword = new String(p1);
        Arrays.fill(p1, '\0');
        Arrays.fill(p2, '\0');
        setBusy(true, "正在重置密码…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ClientConnection connection = ClientConnection.connect(config.getHost(), config.getPort());
                try {
                    new UserClientService(connection).resetPassword(email, code, newPassword);
                    return null;
                } finally {
                    try {
                        connection.close();
                    } catch (Exception ignored) {
                        // Preserve the original failure.
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    setBusy(false, " ");
                    SeuMessages.info(ForgotPasswordFrame.this, "密码已重置，请用新密码登录");
                    dispose();
                } catch (Exception e) {
                    setBusy(false, " ");
                    showError(e);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String status) {
        sendButton.setEnabled(!busy);
        resetButton.setEnabled(!busy);
        emailField.setEnabled(!busy);
        codeField.setEnabled(!busy);
        newPasswordField.setEnabled(!busy);
        confirmField.setEnabled(!busy);
        statusLabel.setText(status == null ? " " : status);
    }

    private void showError(Exception e) {
        Throwable cause = e;
        if (cause instanceof ExecutionException) {
            cause = cause.getCause();
        }
        if (cause instanceof ClientServiceException) {
            SeuMessages.error(this, cause.getMessage());
        } else {
            SeuMessages.error(this, "无法连接服务器：" + (cause == null ? "未知错误" : cause.getMessage()));
        }
    }
}
