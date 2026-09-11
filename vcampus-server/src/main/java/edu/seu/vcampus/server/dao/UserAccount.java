package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.enums.Role;

import java.util.Collections;
import java.util.Set;

/** Database representation of a user account. */
public final class UserAccount {
    private final String userId;
    private final String passwordHash;
    private final String passwordSalt;
    private final String displayName;
    private final Role role;
    private final boolean active;

    /** Sub-system keys granted to a sub-system administrator; empty otherwise. */
    private final Set<String> adminScopes;

    /** 学院/院系（学生），用于学籍自动分班；非学生为空。 */
    private final String department;

    /** 联系邮箱（用于「忘记密码」），可为空。 */
    private final String email;

    public UserAccount(String userId, String passwordHash, String passwordSalt,
                       String displayName, Role role, boolean active) {
        this(userId, passwordHash, passwordSalt, displayName, role, active,
                Collections.<String>emptySet(), "", "");
    }

    public UserAccount(String userId, String passwordHash, String passwordSalt,
                       String displayName, Role role, boolean active, Set<String> adminScopes) {
        this(userId, passwordHash, passwordSalt, displayName, role, active, adminScopes, "", "");
    }

    public UserAccount(String userId, String passwordHash, String passwordSalt,
                       String displayName, Role role, boolean active, Set<String> adminScopes,
                       String department) {
        this(userId, passwordHash, passwordSalt, displayName, role, active, adminScopes,
                department, "");
    }

    public UserAccount(String userId, String passwordHash, String passwordSalt,
                       String displayName, Role role, boolean active, Set<String> adminScopes,
                       String department, String email) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.displayName = displayName;
        this.role = role;
        this.active = active;
        this.adminScopes = adminScopes == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(adminScopes);
        this.department = department == null ? "" : department;
        this.email = email == null ? "" : email;
    }

    public String getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    /** Sub-system keys this administrator may manage; empty for other roles. */
    public Set<String> getAdminScopes() {
        return adminScopes;
    }

    /** 学院/院系（学生），非空供学籍自动分班；其他角色为空。 */
    public String getDepartment() {
        return department;
    }

    /** 联系邮箱（用于「忘记密码」），可为空。 */
    public String getEmail() {
        return email;
    }
}
