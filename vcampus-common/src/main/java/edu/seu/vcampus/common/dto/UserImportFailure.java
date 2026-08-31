package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** One failed row of a CSV batch import, for reporting back to the UI. */
public final class UserImportFailure implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int row;
    private final String userId;
    private final String reason;

    public UserImportFailure(int row, String userId, String reason) {
        this.row = row;
        this.userId = userId;
        this.reason = reason;
    }

    /** One-based row number in the source CSV (excluding the header). */
    public int getRow() {
        return row;
    }

    public String getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }
}
