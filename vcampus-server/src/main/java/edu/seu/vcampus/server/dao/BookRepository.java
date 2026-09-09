package edu.seu.vcampus.server.dao;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/** Persistence boundary for library books, copies and borrow records. */
public interface BookRepository {
    /**
     * Finds books whose ISBN, title or author contains the keyword.
     * An empty or null keyword returns every matching title.
     * Inactive titles are omitted unless {@code includeInactive} is true.
     */
    List<Book> findBooks(String keyword, boolean includeInactive) throws SQLException;

    /** Same as {@link #findBooks(String, boolean)} with inactive titles omitted. */
    default List<Book> findBooks(String keyword) throws SQLException {
        return findBooks(keyword, false);
    }

    Book findByIsbn(String isbn) throws SQLException;

    List<BookCopy> findCopiesByIsbn(String isbn) throws SQLException;

    int countAvailableCopies(String isbn) throws SQLException;

    int countBorrowedCopies(String isbn) throws SQLException;

    int countBorrowRecordsByIsbn(String isbn) throws SQLException;

    int countRemovableCopies(String isbn) throws SQLException;

    /**
     * Inserts or updates catalog fields, then adjusts the physical copy count.
     * Extra copies are inserted as available; reductions only remove available
     * copies that have never been borrowed.
     */
    void saveBook(Book book, int desiredCopies) throws SQLException;

    /**
     * Deletes a title and its copies. Callers must first ensure no borrow
     * records exist for those copies.
     */
    boolean deleteBook(String isbn) throws SQLException;

    List<BorrowRecord> findBorrowRecordsByUser(String userId) throws SQLException;

    /** Every copy that has not been returned, newest borrow first. */
    List<BorrowRecord> findActiveBorrowRecords() throws SQLException;

    BorrowRecord findBorrowRecordById(int recordId) throws SQLException;

    /** Whether the user already has an unreturned copy of this ISBN. */
    boolean hasActiveBorrow(String userId, String isbn) throws SQLException;

    /** Whether the user has any unreturned copy past its due time. */
    boolean hasOverdueBorrow(String userId) throws SQLException;

    /**
     * Borrows one available copy in a short transaction.
     * Returns {@code null} when no available copy exists.
     */
    BorrowRecord borrowAvailableCopy(String userId, String isbn, Date borrowTime, Date dueTime)
            throws SQLException;

    /**
     * Marks the record returned and frees the copy. Returns {@code false} when
     * the record is missing or already returned.
     */
    boolean returnBorrow(int recordId, Date returnTime) throws SQLException;

    /**
     * Extends the due date and stores the new renewal count for an open record.
     * Returns {@code false} when the record is missing, already returned, or the
     * stored renewal count no longer matches {@code expectedRenewCount}.
     */
    boolean renewBorrow(int recordId, Date newDueTime, int expectedRenewCount, int newRenewCount)
            throws SQLException;

    /**
     * Whether an approved reservation currently blocks renewal of this copy.
     * Returns {@code false} until the reservation tables are connected.
     */
    boolean isRenewalBlockedByReservation(int copyId) throws SQLException;
}
