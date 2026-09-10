package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** One book-recommendation (wish) row shared by client and server. */
public final class WishDto implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private final int wishId;
    private final String userId;
    private final String displayName;
    private final String title;
    private final String author;
    private final String status;
    private final String submitTime;
    private final String reviewTime;
    private final String isbn;

    public WishDto(int wishId, String userId, String displayName, String title, String author,
                   String status, String submitTime, String reviewTime, String isbn) {
        this.wishId = wishId;
        this.userId = userId;
        this.displayName = displayName;
        this.title = title;
        this.author = author;
        this.status = status;
        this.submitTime = submitTime;
        this.reviewTime = reviewTime;
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

    /** Name plus login id, or whichever of the two is present. */
    public String getSubmitterLabel() {
        String name = displayName == null ? "" : displayName.trim();
        String id = userId == null ? "" : userId.trim();
        if (!name.isEmpty() && !id.isEmpty()) {
            return name + "（" + id + "）";
        }
        return !name.isEmpty() ? name : id;
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

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isApproved() {
        return STATUS_APPROVED.equals(status);
    }

    public String getSubmitTime() {
        return submitTime;
    }

    public String getReviewTime() {
        return reviewTime;
    }

    public String getIsbn() {
        return isbn;
    }

    /** Display status for tables: 待审 / 已批准 / 已拒绝. */
    public String getStatusName() {
        if (STATUS_APPROVED.equals(status)) {
            return "已批准";
        }
        if (STATUS_REJECTED.equals(status)) {
            return "已拒绝";
        }
        return "待审";
    }
}
