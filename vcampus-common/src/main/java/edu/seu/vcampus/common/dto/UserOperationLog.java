package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.Date;

/** One audit record: a login attempt or an administrator operation. */
public final class UserOperationLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;
    private final Date logTime;
    private final String userId;
    private final String operation;
    private final String targetUserId;
    private final String detail;
    private final boolean success;

    public UserOperationLog(long id, Date logTime, String userId, String operation,
                            String targetUserId, String detail, boolean success) {
        this.id = id;
        this.logTime = logTime;
        this.userId = userId;
        this.operation = operation;
        this.targetUserId = targetUserId;
        this.detail = detail;
        this.success = success;
    }

    public long getId() {
        return id;
    }

    public Date getLogTime() {
        return logTime;
    }

    public String getUserId() {
        return userId;
    }

    public String getOperation() {
        return operation;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isSuccess() {
        return success;
    }
}
