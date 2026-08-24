package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Administrator command that enables or disables another user account. */
public final class UserStatusUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final boolean active;

    public UserStatusUpdateRequest(String userId, boolean active) {
        this.userId = userId;
        this.active = active;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isActive() {
        return active;
    }
}
