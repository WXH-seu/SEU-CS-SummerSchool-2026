package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Verifies the emailed code and resets the password for the given account. */
public final class ResetPasswordRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String email;
    private final String code;
    private final String newPassword;

    public ResetPasswordRequest(String email, String code, String newPassword) {
        this.email = email;
        this.code = code;
        this.newPassword = newPassword;
    }

    public String getEmail() {
        return email;
    }

    public String getCode() {
        return code;
    }

    public String getNewPassword() {
        return newPassword;
    }
}
