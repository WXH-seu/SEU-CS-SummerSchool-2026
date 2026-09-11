package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Catalog and inventory payload used to create or update a library title. */
public final class BookDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String isbn;
    private final String title;
    private final String author;
    private final String publisher;
    private final String category;
    private final int totalCopies;
    private final boolean active;

    public BookDto(String isbn, String title, String author, String publisher,
                   String category, int totalCopies, boolean active) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.category = category;
        this.totalCopies = totalCopies;
        this.active = active;
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

    public String getPublisher() {
        return publisher;
    }

    public String getCategory() {
        return category;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public boolean isActive() {
        return active;
    }
}
