package edu.seu.vcampus.server.dao;

import java.sql.SQLException;
import java.util.List;

/** Persistence boundary for library books, copies and borrow records. */
public interface BookRepository {
    /**
     * Finds active books whose ISBN, title or author contains the keyword.
     * An empty or null keyword returns every active title.
     */
    List<Book> findBooks(String keyword) throws SQLException;

    Book findByIsbn(String isbn) throws SQLException;

    List<BookCopy> findCopiesByIsbn(String isbn) throws SQLException;

    int countAvailableCopies(String isbn) throws SQLException;

    List<BorrowRecord> findBorrowRecordsByUser(String userId) throws SQLException;
}
