package edu.seu.vcampus.server.dao;

import java.util.Date;

/** Database representation of a hold / reservation request. */
public final class BookReservation {
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
    private final Integer copyId;
    private final String status;
    private final Date applyTime;
    private final Date reviewTime;
    private final Date holdUntilTime;
    private final Date pickupTime;
    private final int defaultCount;
    private final Date suspendUntilTime;

    public BookReservation(int reservationId, String userId, String displayName, String isbn,
                           String title, String author, Integer copyId, String status,
                           Date applyTime, Date reviewTime, Date holdUntilTime, Date pickupTime,
                           int defaultCount, Date suspendUntilTime) {
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

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public Integer getCopyId() {
        return copyId;
    }

    public String getStatus() {
        return status;
    }

    public Date getApplyTime() {
        return applyTime;
    }

    public Date getReviewTime() {
        return reviewTime;
    }

    public Date getHoldUntilTime() {
        return holdUntilTime;
    }

    public Date getPickupTime() {
        return pickupTime;
    }

    public int getDefaultCount() {
        return defaultCount;
    }

    public Date getSuspendUntilTime() {
        return suspendUntilTime;
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
}
