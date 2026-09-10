package edu.seu.vcampus.server.dao;

import java.util.Date;

/** Database representation of a patron book-recommendation (wish). */
public final class BookWish {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private final int wishId;
    private final String userId;
    private final String displayName;
    private final String title;
    private final String author;
    private final String status;
    private final Date submitTime;
    private final Date reviewTime;
    private final String reviewerUserId;
    private final String isbn;

    public BookWish(int wishId, String userId, String displayName, String title, String author,
                    String status, Date submitTime, Date reviewTime, String reviewerUserId,
                    String isbn) {
        this.wishId = wishId;
        this.userId = userId;
        this.displayName = displayName;
        this.title = title;
        this.author = author;
        this.status = status;
        this.submitTime = submitTime;
        this.reviewTime = reviewTime;
        this.reviewerUserId = reviewerUserId;
        this.isbn = isbn;
    }

    public int getWishId() {
        return wishId;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getStatus() {
        return status;
    }

    public Date getSubmitTime() {
        return submitTime;
    }

    public Date getReviewTime() {
        return reviewTime;
    }

    public String getReviewerUserId() {
        return reviewerUserId;
    }

    public String getIsbn() {
        return isbn;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }
}
