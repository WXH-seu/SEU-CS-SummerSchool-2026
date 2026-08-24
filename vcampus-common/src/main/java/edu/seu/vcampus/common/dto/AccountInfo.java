package edu.seu.vcampus.common.dto;

import edu.seu.vcampus.common.enums.Role;

import java.io.Serializable;

/** Non-sensitive account information shown on the account management screen. */
public final class AccountInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String displayName;
    private final Role role;
    private final boolean active;

    public AccountInfo(String userId, String displayName, Role role, boolean active) {
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
        this.active = active;
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
}
