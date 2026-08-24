package edu.seu.vcampus.common.dto;

import edu.seu.vcampus.common.enums.Role;

import java.io.Serializable;

/** Session information returned after successful authentication. */
public final class LoginResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sessionToken;
    private final String userId;
    private final String displayName;
    private final Role role;

    public LoginResponse(String sessionToken, String userId, String displayName, Role role) {
        this.sessionToken = sessionToken;
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
    }

    public String getSessionToken() {
        return sessionToken;
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
}
