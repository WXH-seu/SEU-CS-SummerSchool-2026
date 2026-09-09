package edu.seu.vcampus.common.dto;

import edu.seu.vcampus.common.enums.Role;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/** Registration form submitted when a new account is created. */
public final class RegisterRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String password;
    private final String displayName;
    private final Role role;

    /** Sub-system keys granted when the role is {@link Role#SUBSYSADMIN}. */
    private final Set<String> adminScopes;

    /** 学院/院系（学生），用于学籍模块自动分班；CSV 批量导入学生时填写。 */
    private final String department;

    /** 联系邮箱，用于「忘记密码」找回与找回校验；可为空。 */
    private final String email;

    public RegisterRequest(String userId, String password, String displayName, Role role) {
        this(userId, password, displayName, role, Collections.<String>emptySet(), "", "");
    }

    public RegisterRequest(String userId, String password, String displayName, Role role,
                           Set<String> adminScopes) {
        this(userId, password, displayName, role, adminScopes, "", "");
    }

    public RegisterRequest(String userId, String password, String displayName, Role role,
                           String department) {
        this(userId, password, displayName, role, Collections.<String>emptySet(), department, "");
    }

    public RegisterRequest(String userId, String password, String displayName, Role role,
                           Set<String> adminScopes, String department) {
        this(userId, password, displayName, role, adminScopes, department, "");
    }

    public RegisterRequest(String userId, String password, String displayName, Role role,
                           String department, String email) {
        this(userId, password, displayName, role, Collections.<String>emptySet(), department, email);
    }

    public RegisterRequest(String userId, String password, String displayName, Role role,
                           Set<String> adminScopes, String department, String email) {
        this.userId = userId;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.adminScopes = adminScopes == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(adminScopes);
        this.department = department == null ? "" : department;
        this.email = email == null ? "" : email;
    }

    public String getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    /** Sub-system keys to grant when the role is {@link Role#SUBSYSADMIN}. */
    public Set<String> getAdminScopes() {
        return adminScopes;
    }

    /** 学院/院系（学生），非空时供学籍模块自动分班；其他角色一般为空。 */
    public String getDepartment() {
        return department;
    }

    /** 联系邮箱（用于「忘记密码」），可为空。 */
    public String getEmail() {
        return email;
    }
}
