package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Password confirmation required before an account can be deregistered. */
public final class DeleteAccountRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String password;

    public DeleteAccountRequest(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }
}
