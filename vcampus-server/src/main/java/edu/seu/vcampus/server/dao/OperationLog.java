package edu.seu.vcampus.server.dao;

import java.util.Date;

/** Database record of a login attempt or an administrator operation. */
public final class OperationLog {
    private final long id;
    private final Date logTime;
    private final String userId;
    private final String operation;
    private final String targetUserId;
    private final String detail;
    private final boolean success;

    /**
     * @param id           database auto-generated id, {@code 0} for a new record
     * @param logTime      timestamp of the event
     * @param userId       acting user (or the attempted login account)
     * @param operation    operation name, e.g. {@code LOGIN}, {@code REGISTER}
     * @param targetUserId target account for administrator operations, may be null
     * @param detail       human-readable message / failure reason
     * @param success      whether the operation succeeded
     */
    public OperationLog(long id, Date logTime, String userId, String operation,
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
