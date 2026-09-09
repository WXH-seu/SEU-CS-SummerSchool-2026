package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/**
 * Request body used by administrators to approve or reject a wish.
 * {@code book} is required when {@code approved} is true and ignored otherwise.
 */
public final class WishReviewRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int wishId;
    private final boolean approved;
    private final BookDto book;

    public WishReviewRequest(int wishId, boolean approved, BookDto book) {
        this.wishId = wishId;
        this.approved = approved;
        this.book = book;
    }

    public int getWishId() {
        return wishId;
    }

    public boolean isApproved() {
        return approved;
    }

    public BookDto getBook() {
        return book;
    }
}
