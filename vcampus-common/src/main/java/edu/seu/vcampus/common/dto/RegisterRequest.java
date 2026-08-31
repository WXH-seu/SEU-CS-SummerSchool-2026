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

    public RegisterRequest(String userId, String password, String displayName, Role role) {
        this(userId, password, displayName, role, Collections.<String>emptySet());
    }

    public RegisterRequest(String userId, String password, String displayName, Role role,
                           Set<String> adminScopes) {
        this.userId = userId;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.adminScopes = adminScopes == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(adminScopes);
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
}
