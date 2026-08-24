package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Credentials submitted by the login screen. */
public final class LoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String password;

    public LoginRequest(String userId, String password) {
        this.userId = userId;
        this.password = password;
    }

    public String getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }
}
