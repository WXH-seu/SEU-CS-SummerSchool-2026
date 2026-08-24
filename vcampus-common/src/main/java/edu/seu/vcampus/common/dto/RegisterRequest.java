package edu.seu.vcampus.common.dto;

import edu.seu.vcampus.common.enums.Role;

import java.io.Serializable;

/** Registration form submitted when a new account is created. */
public final class RegisterRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String password;
    private final String displayName;
    private final Role role;

    public RegisterRequest(String userId, String password, String displayName, Role role) {
        this.userId = userId;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
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
}
