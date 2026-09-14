package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.client.service.AcademicClientService;
import edu.seu.vcampus.client.service.ClientServiceException;
import edu.seu.vcampus.client.service.UserClientService;
import edu.seu.vcampus.client.ui.components.SeuTheme;
import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystem;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Administrator-only user registration screen. It creates a single account of
 * any role (student, teacher or admin) on behalf of the logged-in
 * administrator; the new user never receives a session here.
 *
 * <p>因为学籍模块的“自动分班”以学生学院为依据，服务端对学生账号强制要求学院，
 * 所以本界面在角色为“学生”时必须选择学院（与 CSV 批量导入的第 5 列一致）。
 */
public final class RegisterFrame extends JFrame {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final ClientConnection connection;
    private final String sessionToken;
    private final AccountPanel parent;
    private final JTextField userIdField = new JTextField(16);
    private final JTextField displayNameField = new JTextField(16);
    private final JPasswordField passwordField = new JPasswordField(16);
    private final JComboBox<String> roleBox = new JComboBox<String>(
            new String[]{"学生", "教师", "子系统管理员", "超级管理员"});
    private final JLabel departmentLabel = new JLabel("学院");
    private final JComboBox<DepartmentItem> departmentBox = new JComboBox<DepartmentItem>();
    private final JPanel scopePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    private final List<JCheckBox> scopeChecks = new ArrayList<JCheckBox>();
    private final JButton registerButton = new JButton("创建账号");
    private final JLabel statusLabel = new JLabel(" ");

    public RegisterFrame(ClientConnection connection, String sessionToken, AccountPanel parent) {
        super("注册用户 - 管理员");
        this.connection = connection;
        this.sessionToken = sessionToken;
        this.parent = parent;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        buildUi();
        loadDepartments();
        applyRoleDependentState();
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBorder(BorderFactory.createEmptyBorder(26, 38, 22, 38));

        JLabel title = new JLabel("注册用户", JLabel.CENTER);
        title.setFont(SeuTheme.font(Font.BOLD, 21f));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        addRow(form, constraints, 0, new JLabel("账号"), userIdField);
        addRow(form, constraints, 1, new JLabel("显示名"), displayNameField);
        addRow(form, constraints, 2, new JLabel("初始密码"), passwordField);
        addRow(form, constraints, 3, new JLabel("角色"), roleBox);
        addRow(form, constraints, 4, departmentLabel, departmentBox);
        addRow(form, constraints, 5, new JLabel("可管理子系统"), scopePanel);

        constraints.gridx = 1;
        constraints.gridy = 6;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        JLabel scopeHint = new JLabel("学院仅学生必填（用于学籍自动分班）；可管理子系统仅子系统管理员需要勾选，可多选");
        scopeHint.setFont(SeuTheme.font(Font.PLAIN, 11f));
        form.add(scopeHint, constraints);
        constraints.gridwidth = 1;
        constraints.weightx = 0;

        for (SubSystem subSystem : SubSystem.values()) {
            JCheckBox checkBox = new JCheckBox(subSystem.getDisplayName());
            checkBox.setActionCommand(subSystem.getKey());
            scopeChecks.add(checkBox);
            scopePanel.add(checkBox);
        }
        roleBox.addActionListener(event -> applyRoleDependentState());

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

        constraints.gridx = 0;
        constraints.gridy = 7;
        constraints.gridwidth = 2;
        form.add(buttons, constraints);

        setContentPane(root);
        getRootPane().setDefaultButton(registerButton);
        registerButton.addActionListener(event -> register());
        cancelButton.addActionListener(event -> dispose());

        root.add(form, BorderLayout.CENTER);
    }

    /** 统一的“标签 + 控件”两列行，避免手工维护 gridy 造成的行重叠。 */
    private void addRow(JPanel form, GridBagConstraints constraints, int row,
                        java.awt.Component label, java.awt.Component field) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(field, constraints);
    }

    /** 异步加载可选学院（仅启用的院系），供学生注册时选择。 */
    private void loadDepartments() {
        departmentBox.setEnabled(false);
        new SwingWorker<List<DepartmentDto>, Void>() {
            @Override
            protected List<DepartmentDto> doInBackground() throws Exception {
                return new AcademicClientService(connection, sessionToken).queryDepartments(true);
            }

            @Override
            protected void done() {
                try {
                    List<DepartmentDto> departments = get();
                    departmentBox.removeAllItems();
                    for (DepartmentDto department : departments) {
                        departmentBox.addItem(new DepartmentItem(
                                department.getDepartmentId(), department.getDepartmentName()));
                    }
                    if (departmentBox.getItemCount() == 0) {
                        statusLabel.setText("暂无可用学院，请先在学籍管理中维护院系");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("学院列表加载被中断");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    statusLabel.setText("学院列表加载失败："
                            + (cause == null ? "未知错误" : cause.getMessage()));
                } finally {
                    applyRoleDependentState();
                }
            }
        }.execute();
    }

    /** 学院仅对“学生”必填；子系统勾选仅对“子系统管理员”可用。 */
    private void applyRoleDependentState() {
        boolean student = selectedRole() == Role.STUDENT;
        departmentLabel.setEnabled(student);
        departmentBox.setEnabled(student);
        if (!student) {
            departmentBox.setSelectedIndex(departmentBox.getItemCount() > 0 ? 0 : -1);
        }
        setScopeEnabled(selectedRole() == Role.SUBSYSADMIN);
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
        final Set<String> scopes = selectedScopes();
        if (role == Role.SUBSYSADMIN && scopes.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "子系统管理员至少需要勾选一个可管理子系统", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String department = selectedDepartmentId();
        if (role == Role.STUDENT && department == null) {
            JOptionPane.showMessageDialog(this,
                    departmentBox.getItemCount() == 0
                            ? "还没有可用的学院，请先在「学籍管理」中维护院系后再注册学生"
                            : "学生必须填写学院（院系），用于学籍自动分班", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        setFormEnabled(false, "正在创建...");
        new SwingWorker<AccountInfo, Void>() {
            @Override
            protected AccountInfo doInBackground() throws Exception {
                String password = new String(passwordChars);
                Arrays.fill(passwordChars, '\0');
                UserClientService service = new UserClientService(connection);
                String departmentToSend = role == Role.STUDENT ? department : "";
                return service.register(new RegisterRequest(
                        userId, password, displayName, role, scopes, departmentToSend), sessionToken);
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
            return Role.SUBSYSADMIN;
        }
        if (index == 1) {
            return Role.TEACHER;
        }
        return Role.STUDENT;
    }

    /** 返回所选院系编号；未选择或列表为空时返回 {@code null}。 */
    private String selectedDepartmentId() {
        DepartmentItem item = (DepartmentItem) departmentBox.getSelectedItem();
        return item == null || item.id.isEmpty() ? null : item.id;
    }

    private Set<String> selectedScopes() {
        Set<String> scopes = new LinkedHashSet<String>();
        for (JCheckBox checkBox : scopeChecks) {
            if (checkBox.isSelected()) {
                scopes.add(checkBox.getActionCommand());
            }
        }
        return scopes;
    }

    private void setScopeEnabled(boolean enabled) {
        for (JCheckBox checkBox : scopeChecks) {
            checkBox.setEnabled(enabled);
            if (!enabled) {
                checkBox.setSelected(false);
            }
        }
    }

    private void setFormEnabled(boolean enabled, String status) {
        registerButton.setEnabled(enabled);
        userIdField.setEnabled(enabled);
        displayNameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        roleBox.setEnabled(enabled);
        if (enabled) {
            applyRoleDependentState();
        } else {
            departmentBox.setEnabled(false);
            departmentLabel.setEnabled(false);
            setScopeEnabled(false);
        }
        statusLabel.setText(status);
    }

    private void showFailure(String message) {
        setFormEnabled(true, " ");
        JOptionPane.showMessageDialog(this, message, "创建失败", JOptionPane.ERROR_MESSAGE);
    }

    /** 学院下拉项：显示“编号 - 名称”，提交时使用编号。 */
    private static final class DepartmentItem {
        private final String id;
        private final String name;

        private DepartmentItem(String id, String name) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
        }

        @Override
        public String toString() {
            return name.isEmpty() ? id : id + " - " + name;
        }
    }
}
