package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Old and new password submitted when the user changes his or her password. */
public final class PasswordChangeRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String oldPassword;
    private final String newPassword;

    public PasswordChangeRequest(String oldPassword, String newPassword) {
        this.oldPassword = oldPassword;
        this.newPassword = newPassword;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }
}
