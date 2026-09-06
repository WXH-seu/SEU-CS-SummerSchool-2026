package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** One borrow / return row shared by client and server. */
public final class BorrowRecordDto implements Serializable {
    private static final long serialVersionUID = 2L;

    private final int recordId;
    private final String isbn;
    private final String title;
    private final String author;
    private final String userId;
    private final String displayName;
    private final String borrowTime;
    private final String dueTime;
    private final String returnTime;
    private final boolean overdue;
    private final boolean returned;

    public BorrowRecordDto(int recordId, String isbn, String title, String author,
                           String userId, String displayName,
                           String borrowTime, String dueTime, String returnTime,
                           boolean overdue, boolean returned) {
        this.recordId = recordId;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.userId = userId;
        this.displayName = displayName;
        this.borrowTime = borrowTime;
        this.dueTime = dueTime;
        this.returnTime = returnTime;
        this.overdue = overdue;
        this.returned = returned;
    }

    public int getRecordId() {
        return recordId;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Name plus login id, or whichever of the two is present. */
    public String getBorrowerLabel() {
        String name = displayName == null ? "" : displayName.trim();
        String id = userId == null ? "" : userId.trim();
        if (!name.isEmpty() && !id.isEmpty()) {
            return name + "（" + id + "）";
        }
        return !name.isEmpty() ? name : id;
    }

    public String getBorrowTime() {
        return borrowTime;
    }

    public String getDueTime() {
        return dueTime;
    }

    public String getReturnTime() {
        return returnTime;
    }

    public boolean isOverdue() {
        return overdue;
    }

    public boolean isReturned() {
        return returned;
    }

    /** Display status for tables: 已还 / 逾期 / 在借. */
    public String getStatusName() {
        if (returned) {
            return "已还";
        }
        return overdue ? "逾期" : "在借";
    }
}
