package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** One reservation (hold) row shared by client and server. */
public final class ReserveDto implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_HELD = "HELD";
    public static final String STATUS_PICKED_UP = "PICKED_UP";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private final int reservationId;
    private final String userId;
    private final String displayName;
    private final String isbn;
    private final String title;
    private final String author;
    private final int copyId;
    private final String status;
    private final String applyTime;
    private final String reviewTime;
    private final String holdUntilTime;
    private final String pickupTime;
    private final int defaultCount;
    private final String suspendUntilTime;

    public ReserveDto(int reservationId, String userId, String displayName, String isbn,
                      String title, String author, int copyId, String status,
                      String applyTime, String reviewTime, String holdUntilTime,
                      String pickupTime, int defaultCount, String suspendUntilTime) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.displayName = displayName;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.copyId = copyId;
        this.status = status;
        this.applyTime = applyTime;
        this.reviewTime = reviewTime;
        this.holdUntilTime = holdUntilTime;
        this.pickupTime = pickupTime;
        this.defaultCount = Math.max(0, defaultCount);
        this.suspendUntilTime = suspendUntilTime;
    }

    public int getReservationId() {
        return reservationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getApplicantLabel() {
        String name = displayName == null ? "" : displayName.trim();
        String id = userId == null ? "" : userId.trim();
        if (!name.isEmpty() && !id.isEmpty()) {
            return name + "（" + id + "）";
        }
        return !name.isEmpty() ? name : id;
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

    public int getCopyId() {
        return copyId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isApproved() {
        return STATUS_APPROVED.equals(status);
    }

    public boolean isHeld() {
        return STATUS_HELD.equals(status);
    }

    public boolean isInProgress() {
        return isPending() || isApproved() || isHeld();
    }

    public String getApplyTime() {
        return applyTime;
    }

    public String getReviewTime() {
        return reviewTime;
    }

    public String getHoldUntilTime() {
        return holdUntilTime;
    }

    public String getPickupTime() {
        return pickupTime;
    }

    public int getDefaultCount() {
        return defaultCount;
    }

    public String getSuspendUntilTime() {
        return suspendUntilTime;
    }

    public String getStatusName() {
        if (STATUS_APPROVED.equals(status)) {
            return "待归还";
        }
        if (STATUS_HELD.equals(status)) {
            return "待取书";
        }
        if (STATUS_PICKED_UP.equals(status)) {
            return "已取书";
        }
        if (STATUS_REJECTED.equals(status)) {
            return "已拒绝";
        }
        if (STATUS_EXPIRED.equals(status)) {
            return "逾期未取";
        }
        return "待审";
    }
}
