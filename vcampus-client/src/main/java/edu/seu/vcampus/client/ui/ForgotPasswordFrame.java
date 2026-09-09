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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** 忘记密码：输入邮箱 → 服务端发验证码 → 校验验证码并重置密码。 */
public final class ForgotPasswordFrame extends JFrame {
    private final ClientConfig config;
    private final JTextField emailField = SeuFields.pillText(20);
    private final JTextField codeField = SeuFields.pillText(20);
    private final JPasswordField newPasswordField = SeuFields.pillPassword(20);
    private final JPasswordField confirmField = SeuFields.pillPassword(20);
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
        root.setBorder(BorderFactory.createEmptyBorder(24, 40, 24, 40));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 6, 7, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // 标题（跨两列、居中）
        JLabel title = SeuLabels.title("找回密码");
        title.setFont(SeuTheme.font(Font.BOLD, 18f));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        form.add(title, c);

        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.gridy = 1;
        c.gridx = 0;
        form.add(label("邮箱"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        form.add(emailField, c);

        c.gridy = 2;
        c.gridx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        form.add(label("验证码"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        form.add(codeField, c);

        c.gridy = 3;
        c.gridx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        form.add(label("新密码"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        form.add(newPasswordField, c);

        c.gridy = 4;
        c.gridx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        form.add(label("确认新密码"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        form.add(confirmField, c);

        // 发送验证码按钮（跨两列）
        c.gridy = 5;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        sendButton.setFont(SeuTheme.font(Font.PLAIN, 15f));
        form.add(sendButton, c);

        // 重置密码按钮（跨两列）
        c.anchor = GridBagConstraints.NORTH;
        c.gridy = 6;
        form.add(resetButton, c);

        // 状态提示
        c.gridy = 7;
        c.fill = GridBagConstraints.NONE;
        statusLabel.setHorizontalAlignment(JLabel.CENTER);
        form.add(statusLabel, c);

        // 外层居中
        JPanel frame = new JPanel(new GridBagLayout());
        frame.setOpaque(false);
        frame.add(form, new GridBagConstraints());
        root.add(frame, BorderLayout.CENTER);
        setContentPane(root);

        SeuFields.setPlaceholder(emailField, "请输入已绑定的邮箱");
        SeuFields.setPlaceholder(codeField, "请输入邮箱中的验证码");
        SeuFields.setPlaceholder(newPasswordField, "请输入新密码");
        SeuFields.setPlaceholder(confirmField, "请再次输入新密码");

        sendButton.addActionListener(event -> sendCode());
        resetButton.addActionListener(event -> resetPassword());
    }

    private JLabel label(String text) {
        JLabel label = SeuLabels.field(text);
        label.setFont(SeuTheme.bodyFont());
        return label;
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
            String detail = cause == null ? "" : cause.getMessage();
            SeuMessages.error(this, "无法连接服务器，请确认服务端已启动"
                    + (detail == null || detail.trim().isEmpty() ? "" : "（" + detail + "）"));
        }
    }
}
