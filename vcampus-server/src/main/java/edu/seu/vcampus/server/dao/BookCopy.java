package edu.seu.vcampus.server.dao;

/** Database representation of one physical copy of a book. */
public final class BookCopy {
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_BORROWED = "BORROWED";

    private final int copyId;
    private final String isbn;
    private final String copyStatus;

    public BookCopy(int copyId, String isbn, String copyStatus) {
        this.copyId = copyId;
        this.isbn = isbn;
        this.copyStatus = copyStatus;
    }

    public int getCopyId() {
        return copyId;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getCopyStatus() {
        return copyStatus;
    }

    public boolean isAvailable() {
        return STATUS_AVAILABLE.equals(copyStatus);
    }
}
