package edu.seu.vcampus.common.dto;

import edu.seu.vcampus.common.enums.Role;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/** Non-sensitive account information shown on the account management screen. */
public final class AccountInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String displayName;
    private final Role role;
    private final boolean active;

    /** Sub-system keys granted to a sub-system administrator; empty otherwise. */
    private final Set<String> adminScopes;

    public AccountInfo(String userId, String displayName, Role role, boolean active) {
        this(userId, displayName, role, active, Collections.<String>emptySet());
    }

    public AccountInfo(String userId, String displayName, Role role, boolean active,
                       Set<String> adminScopes) {
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
        this.active = active;
        this.adminScopes = adminScopes == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(adminScopes);
    }

    public String getUserId() {
        return userId;
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
}
