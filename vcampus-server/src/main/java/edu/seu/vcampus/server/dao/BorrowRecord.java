package edu.seu.vcampus.server.dao;

import java.util.Date;

/** Database representation of a borrow / return record. */
public final class BorrowRecord {
    private final int recordId;
    private final int copyId;
    private final String userId;
    private final Date borrowTime;
    private final Date dueTime;
    private final Date returnTime;

    public BorrowRecord(int recordId, int copyId, String userId,
                        Date borrowTime, Date dueTime, Date returnTime) {
        this.recordId = recordId;
        this.copyId = copyId;
        this.userId = userId;
        this.borrowTime = borrowTime;
        this.dueTime = dueTime;
        this.returnTime = returnTime;
    }

    public int getRecordId() {
        return recordId;
    }

    public int getCopyId() {
        return copyId;
    }

    public String getUserId() {
        return userId;
    }

    public Date getBorrowTime() {
        return borrowTime;
    }

    public Date getDueTime() {
        return dueTime;
    }

    public Date getReturnTime() {
        return returnTime;
    }

    public boolean isReturned() {
        return returnTime != null;
    }

    public boolean isOverdue() {
        return returnTime == null && dueTime != null && dueTime.before(new Date());
    }
}
