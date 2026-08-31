package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** New display name submitted from the account management screen. */
public final class ProfileUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String displayName;

    public ProfileUpdateRequest(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
