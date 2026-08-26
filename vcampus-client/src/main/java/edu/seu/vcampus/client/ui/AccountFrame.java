package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.config.ClientConfig;
import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.ClientServiceException;
import edu.seu.vcampus.client.service.UserCsvParser;
import edu.seu.vcampus.client.service.UserClientService;
import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.UserImportFailure;
import edu.seu.vcampus.common.dto.UserImportResponse;
import edu.seu.vcampus.common.enums.Role;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Account management screen: shows the current account, allows the user to
 * update the display name, change the password and deregister the account.
 * Administrators additionally see a user management tab to enable or disable
 * accounts.
 */
public final class AccountFrame extends JFrame {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final ClientConfig config;
    private final MainFrame mainFrame;
    private final ClientConnection connection;
    private final UserClientService service;
    private final LoginResponse session;

    private final JLabel userIdLabel = new JLabel();
    private final JLabel displayNameLabel = new JLabel();
    private final JLabel roleLabel = new JLabel();
    private final JLabel activeLabel = new JLabel();

    private final JTextField displayNameField = new JTextField(16);
    private final JPasswordField oldPasswordField = new JPasswordField(16);
    private final JPasswordField newPasswordField = new JPasswordField(16);
    private final JPasswordField confirmPasswordField = new JPasswordField(16);
    private final JPasswordField deletePasswordField = new JPasswordField(16);

    private final DefaultTableModel userTableModel = new DefaultTableModel(
            new String[]{"账号", "显示名", "角色", "状态"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable userTable = new JTable(userTableModel);

    public AccountFrame(ClientConfig config, MainFrame mainFrame,
                        ClientConnection connection, LoginResponse session) {
        super("账号管理 - " + session.getDisplayName());
        this.config = config;
        this.mainFrame = mainFrame;
        this.connection = connection;
        this.service = new UserClientService(connection);
        this.session = session;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(680, 560);
        setLocationRelativeTo(mainFrame);
        buildUi();
    }

    private void buildUi() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("我的账号", createProfileTab());
        tabs.addTab("修改资料", createEditTab());
        tabs.addTab("注销账号", createDeregisterTab());
        if (session.getRole() == Role.ADMIN) {
            tabs.addTab("用户管理", createUserAdminTab());
        }
        setContentPane(tabs);
    }

    private JPanel createProfileTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(10, 8, 10, 8);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0;
        constraints.gridy = 0;
        panel.add(new JLabel("账号"), constraints);
        constraints.gridx = 1;
        panel.add(userIdLabel, constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(new JLabel("显示名"), constraints);
        constraints.gridx = 1;
        panel.add(displayNameLabel, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        panel.add(new JLabel("角色"), constraints);
        constraints.gridx = 1;
        panel.add(roleLabel, constraints);
        constraints.gridx = 0;
        constraints.gridy = 3;
        panel.add(new JLabel("状态"), constraints);
        constraints.gridx = 1;
        panel.add(activeLabel, constraints);

        JButton refreshButton = new JButton("刷新");
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.CENTER;
        panel.add(refreshButton, constraints);
        refreshButton.addActionListener(event -> refreshAccount());

        JLabel tip = new JLabel("账号、角色与状态由系统统一管理，不可自行修改。");
        tip.setFont(tip.getFont().deriveFont(Font.PLAIN, 12f));
        constraints.gridy = 5;
        panel.add(tip, constraints);

        refreshAccount();
        return panel;
    }

    private JPanel createEditTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 8, 6, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        JLabel nameTitle = new JLabel("修改显示名");
        nameTitle.setFont(nameTitle.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(nameTitle, constraints);
        constraints.gridwidth = 1;
        constraints.gridy = 1;
        panel.add(new JLabel("新显示名"), constraints);
        constraints.gridx = 1;
        panel.add(displayNameField, constraints);
        constraints.gridy = 2;
        JButton saveNameButton = new JButton("保存显示名");
        panel.add(saveNameButton, constraints);
        saveNameButton.addActionListener(event -> updateProfile());

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;
        JLabel passwordTitle = new JLabel("修改密码");
        passwordTitle.setFont(passwordTitle.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(passwordTitle, constraints);
        constraints.gridwidth = 1;
        constraints.gridy = 4;
        panel.add(new JLabel("原密码"), constraints);
        constraints.gridx = 1;
        panel.add(oldPasswordField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 5;
        panel.add(new JLabel("新密码"), constraints);
        constraints.gridx = 1;
        panel.add(newPasswordField, constraints);
        constraints.gridx = 0;
        constraints.gridy = 6;
        panel.add(new JLabel("确认新密码"), constraints);
        constraints.gridx = 1;
        panel.add(confirmPasswordField, constraints);
        constraints.gridy = 7;
        JButton changePasswordButton = new JButton("修改密码");
        panel.add(changePasswordButton, constraints);
        changePasswordButton.addActionListener(event -> changePassword());
        return panel;
    }

    private JPanel createDeregisterTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 8, 8, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        JLabel title = new JLabel("注销当前账号");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(title, constraints);
        constraints.gridwidth = 1;
        constraints.gridy = 1;
        panel.add(new JLabel("登录密码"), constraints);
        constraints.gridx = 1;
        panel.add(deletePasswordField, constraints);
        constraints.gridy = 2;
        JButton deleteButton = new JButton("注销账号");
        panel.add(deleteButton, constraints);
        deleteButton.addActionListener(event -> deleteAccount());

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;
        JLabel warning = new JLabel("<html>警告：注销后账号将被<b>永久删除</b>，"
                + "该账号的选课、借阅与订单数据将无法继续使用，且操作不可撤销。</html>");
        panel.add(warning, constraints);
        return panel;
    }

    private JPanel createUserAdminTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton refreshButton = new JButton("刷新列表");
        JButton addButton = new JButton("注册用户");
        JButton importButton = new JButton("批量导入CSV");
        JButton enableButton = new JButton("启用选中账号");
        JButton disableButton = new JButton("禁用选中账号");
        actions.add(refreshButton);
        actions.add(addButton);
        actions.add(importButton);
        actions.add(enableButton);
        actions.add(disableButton);
        panel.add(actions, BorderLayout.SOUTH);

        refreshButton.addActionListener(event -> loadUsers());
        addButton.addActionListener(event ->
                new RegisterFrame(connection, session.getSessionToken(), this).setVisible(true));
        importButton.addActionListener(event -> importCsv());
        enableButton.addActionListener(event -> changeUserStatus(true));
        disableButton.addActionListener(event -> changeUserStatus(false));
        loadUsers();
        return panel;
    }

    /** Reloads the user list; invoked after a new user is created. */
    public void refreshUserList() {
        loadUsers();
    }

    private void importCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择学生批量注册 CSV 文件");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = chooser.getSelectedFile();
        final List<edu.seu.vcampus.common.dto.RegisterRequest> parsed;
        try {
            parsed = UserCsvParser.parse(readUtf8(file));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "读取文件失败：" + e.getMessage(), "导入失败",
                    JOptionPane.ERROR_MESSAGE);
            return;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "CSV 格式错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        setBusy(true);
        new SwingWorker<UserImportResponse, Void>() {
            @Override
            protected UserImportResponse doInBackground() throws Exception {
                return service.importUsers(parsed, session.getSessionToken());
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    UserImportResponse response = get();
                    presentImportResult(response);
                    loadUsers();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError("批量导入失败", e);
                }
            }
        }.execute();
    }

    private void presentImportResult(UserImportResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("成功导入 ").append(response.getImported()).append(" 个账号。");
        List<UserImportFailure> failures = response.getFailures();
        if (!failures.isEmpty()) {
            message.append("\n失败 ").append(failures.size()).append(" 个：");
            for (UserImportFailure failure : failures) {
                message.append("\n第 ").append(failure.getRow()).append(" 行 ")
                        .append(failure.getUserId() == null ? "" : failure.getUserId())
                        .append("：").append(failure.getReason());
            }
        }
        JOptionPane.showMessageDialog(this, message.toString(), "导入结果",
                failures.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private String readUtf8(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private void refreshAccount() {
        setBusy(true);
        new SwingWorker<AccountInfo, Void>() {
            @Override
            protected AccountInfo doInBackground() throws Exception {
                return service.queryAccount(session.getSessionToken());
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    AccountInfo info = get();
                    renderInfo(info);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError("查询账号失败", e);
                }
            }
        }.execute();
    }

    private void renderInfo(AccountInfo info) {
        userIdLabel.setText(info.getUserId());
        displayNameLabel.setText(info.getDisplayName());
        roleLabel.setText(RoleNames.of(info.getRole()));
        activeLabel.setText(info.isActive() ? "正常" : "已禁用");
    }

    private void updateProfile() {
        final String displayName = displayNameField.getText().trim();
        if (displayName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入新的显示名", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        setBusy(true);
        new SwingWorker<AccountInfo, Void>() {
            @Override
            protected AccountInfo doInBackground() throws Exception {
                return service.updateProfile(session.getSessionToken(), displayName);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    AccountInfo info = get();
                    renderInfo(info);
                    mainFrame.updateDisplayName(info.getDisplayName());
                    JOptionPane.showMessageDialog(AccountFrame.this,
                            "显示名已更新为：" + info.getDisplayName(), "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError("更新失败", e);
                }
            }
        }.execute();
    }

    private void changePassword() {
        final char[] oldChars = oldPasswordField.getPassword();
        final char[] newChars = newPasswordField.getPassword();
        final char[] confirmChars = confirmPasswordField.getPassword();
        if (oldChars.length == 0 || newChars.length == 0) {
            JOptionPane.showMessageDialog(this, "请填写原密码和新密码", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (newChars.length < MIN_PASSWORD_LENGTH) {
            JOptionPane.showMessageDialog(this,
                    "新密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!Arrays.equals(newChars, confirmChars)) {
            JOptionPane.showMessageDialog(this, "两次输入的新密码不一致", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.changePassword(session.getSessionToken(),
                        new String(oldChars), new String(newChars));
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                Arrays.fill(oldChars, '\0');
                Arrays.fill(newChars, '\0');
                Arrays.fill(confirmChars, '\0');
                try {
                    get();
                    oldPasswordField.setText("");
                    newPasswordField.setText("");
                    confirmPasswordField.setText("");
                    JOptionPane.showMessageDialog(AccountFrame.this,
                            "密码修改成功，下次登录请使用新密码", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError("密码修改失败", e);
                }
            }
        }.execute();
    }

    private void deleteAccount() {
        final char[] passwordChars = deletePasswordField.getPassword();
        if (passwordChars.length == 0) {
            JOptionPane.showMessageDialog(this, "请输入登录密码确认注销", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "确认注销账号 " + session.getUserId() + " ？该操作不可撤销。",
                "注销账号", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.deleteAccount(session.getSessionToken(), new String(passwordChars));
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                Arrays.fill(passwordChars, '\0');
                try {
                    get();
                    JOptionPane.showMessageDialog(AccountFrame.this,
                            "账号已注销，感谢使用虚拟校园", "注销成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    returnToLogin();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError("注销失败", e);
                }
            }
        }.execute();
    }

    private void loadUsers() {
        setBusy(true);
        new SwingWorker<List<AccountInfo>, Void>() {
            @Override
            protected List<AccountInfo> doInBackground() throws Exception {
                return service.listUsers(session.getSessionToken());
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    List<AccountInfo> users = get();
                    userTableModel.setRowCount(0);
                    for (AccountInfo user : users) {
                        userTableModel.addRow(new Object[]{
                                user.getUserId(),
                                user.getDisplayName(),
                                RoleNames.of(user.getRole()),
                                user.isActive() ? "正常" : "已禁用"});
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError("加载用户列表失败", e);
                }
            }
        }.execute();
    }

    private void changeUserStatus(final boolean active) {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先在列表中选择一个账号", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String userId = String.valueOf(userTableModel.getValueAt(row, 0));
        if (userId.equals(session.getUserId())) {
            JOptionPane.showMessageDialog(this, "不能修改自己的账号状态", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String action = active ? "启用" : "禁用";
        int choice = JOptionPane.showConfirmDialog(this,
                "确认" + action + "账号 " + userId + " ？", action + "账号",
                JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        setBusy(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.updateUserStatus(session.getSessionToken(), userId, active);
                return null;
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    get();
                    JOptionPane.showMessageDialog(AccountFrame.this,
                            "账号 " + userId + " 已" + action, "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                    loadUsers();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError(action + "账号失败", e);
                }
            }
        }.execute();
    }

    private void returnToLogin() {
        mainFrame.dispose();
        dispose();
        try {
            connection.close();
        } catch (Exception ignored) {
            // Window is already closing.
        }
        new LoginFrame(config).setVisible(true);
    }

    private void setBusy(boolean busy) {
        setEnabled(!busy);
    }

    private void showError(String title, ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof ClientServiceException) {
            JOptionPane.showMessageDialog(this, cause.getMessage(), title,
                    JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    cause == null ? "网络错误" : "网络错误：" + cause.getMessage(), title,
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
