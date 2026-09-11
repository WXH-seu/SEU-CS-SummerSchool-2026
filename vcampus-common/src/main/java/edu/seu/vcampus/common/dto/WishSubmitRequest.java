package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Request body used by patrons to submit a book recommendation. */
public final class WishSubmitRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String title;
    private final String author;

    public WishSubmitRequest(String title, String author) {
        this.title = title;
        this.author = author;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }
}
