package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Request body used to borrow one title by ISBN. */
public final class BorrowRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String isbn;

    public BorrowRequest(String isbn) {
        this.isbn = isbn;
    }

    public String getIsbn() {
        return isbn;
    }
}
