package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Display name / email submitted from the account management screen. */
public final class ProfileUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String displayName;
    private final String email;

    public ProfileUpdateRequest(String displayName) {
        this(displayName, "");
    }

    public ProfileUpdateRequest(String displayName, String email) {
        this.displayName = displayName;
        this.email = email == null ? "" : email;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 联系邮箱，可为空；用于「忘记密码」找回。 */
    public String getEmail() {
        return email;
    }
}
