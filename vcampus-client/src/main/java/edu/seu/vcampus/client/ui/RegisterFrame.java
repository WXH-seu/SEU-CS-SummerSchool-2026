package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.ClientServiceException;
import edu.seu.vcampus.client.service.UserClientService;
import edu.seu.vcampus.common.dto.AccountInfo;
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
 * Administrator-only user registration screen. It creates a single account of
 * any role (student, teacher or admin) on behalf of the logged-in
 * administrator; the new user never receives a session here.
 */
public final class RegisterFrame extends JFrame {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final ClientConnection connection;
    private final String sessionToken;
    private final AccountFrame parent;
    private final JTextField userIdField = new JTextField(16);
    private final JTextField displayNameField = new JTextField(16);
    private final JPasswordField passwordField = new JPasswordField(16);
    private final JComboBox<String> roleBox = new JComboBox<String>(
            new String[]{"学生", "教师", "管理员", "超级管理员"});
    private final JButton registerButton = new JButton("创建账号");
    private final JLabel statusLabel = new JLabel(" ");

    public RegisterFrame(ClientConnection connection, String sessionToken, AccountFrame parent) {
        super("注册用户 - 管理员");
        this.connection = connection;
        this.sessionToken = sessionToken;
        this.parent = parent;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        buildUi();
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBorder(BorderFactory.createEmptyBorder(26, 38, 22, 38));

        JLabel title = new JLabel("注册用户", JLabel.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 21f));
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
        form.add(new JLabel("初始密码"), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(passwordField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 3;
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
        JButton cancelButton = new JButton("关闭");
        buttons.add(cancelButton, buttonConstraints);
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 1;
        buttonConstraints.gridwidth = 2;
        buttons.add(statusLabel, buttonConstraints);

        form.add(buttons, constraints);
        constraints.gridy = 4;
        constraints.gridwidth = 2;
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
        if (userId.isEmpty() || displayName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入账号和显示名", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (passwordChars.length < MIN_PASSWORD_LENGTH) {
            JOptionPane.showMessageDialog(this,
                    "初始密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        final Role role = selectedRole();
        setFormEnabled(false, "正在创建...");
        new SwingWorker<AccountInfo, Void>() {
            @Override
            protected AccountInfo doInBackground() throws Exception {
                String password = new String(passwordChars);
                Arrays.fill(passwordChars, '\0');
                UserClientService service = new UserClientService(connection);
                return service.register(
                        new RegisterRequest(userId, password, displayName, role), sessionToken);
            }

            @Override
            protected void done() {
                try {
                    AccountInfo created = get();
                    JOptionPane.showMessageDialog(RegisterFrame.this,
                            "已创建账号 " + created.getUserId() + "（"
                                    + RoleNames.of(created.getRole()) + "）", "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    if (parent != null) {
                        parent.refreshUserList();
                    }
                    dispose();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showFailure("操作被中断");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof ClientServiceException) {
                        showFailure(cause.getMessage());
                    } else {
                        showFailure(cause == null ? "创建失败" : "网络错误：" + cause.getMessage());
                    }
                }
            }
        }.execute();
    }

    private Role selectedRole() {
        int index = roleBox.getSelectedIndex();
        if (index == 3) {
            return Role.SUPER_ADMIN;
        }
        if (index == 2) {
            return Role.ADMIN;
        }
        if (index == 1) {
            return Role.TEACHER;
        }
        return Role.STUDENT;
    }

    private void setFormEnabled(boolean enabled, String status) {
        registerButton.setEnabled(enabled);
        userIdField.setEnabled(enabled);
        displayNameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        roleBox.setEnabled(enabled);
        statusLabel.setText(status);
    }

    private void showFailure(String message) {
        setFormEnabled(true, " ");
        JOptionPane.showMessageDialog(this, message, "创建失败", JOptionPane.ERROR_MESSAGE);
    }
}
