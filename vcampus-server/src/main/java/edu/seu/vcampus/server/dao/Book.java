package edu.seu.vcampus.server.dao;

/** Database representation of a library title. */
public final class Book {
    private final String isbn;
    private final String title;
    private final String author;
    private final String publisher;
    private final String category;
    private final boolean active;

    public Book(String isbn, String title, String author, String publisher,
                String category, boolean active) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.category = category;
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

    public boolean isActive() {
        return active;
    }
}
