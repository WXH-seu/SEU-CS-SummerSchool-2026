package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Book title plus inventory counts shared by client and server. */
public final class BookSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String isbn;
    private final String title;
    private final String author;
    private final String publisher;
    private final String category;
    private final int availableCopies;
    private final int totalCopies;

    public BookSummary(String isbn, String title, String author, String publisher,
                       String category, int availableCopies, int totalCopies) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.category = category;
        this.availableCopies = availableCopies;
        this.totalCopies = totalCopies;
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

    public int getAvailableCopies() {
        return availableCopies;
    }

    public int getTotalCopies() {
        return totalCopies;
    }
}
