package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.enums.Role;

/** Database representation of a user account. */
public final class UserAccount {
    private final String userId;
    private final String passwordHash;
    private final String passwordSalt;
    private final String displayName;
    private final Role role;
    private final boolean active;

    public UserAccount(String userId, String passwordHash, String passwordSalt,
                       String displayName, Role role, boolean active) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.displayName = displayName;
        this.role = role;
        this.active = active;
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
}
