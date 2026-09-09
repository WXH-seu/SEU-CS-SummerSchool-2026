package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Requests a password-reset verification code for the given registered email. */
public final class ForgotPasswordRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String email;

    public ForgotPasswordRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
