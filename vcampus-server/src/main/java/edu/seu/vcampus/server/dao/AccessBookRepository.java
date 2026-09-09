package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.server.database.AccessDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** Access implementation that creates library tables and demo data on first use. */
public final class AccessBookRepository implements BookRepository {
    private static final String BORROW_USER_FOREIGN_KEY = "fkBorrowRecordUser";
    private static final String WISH_USER_FOREIGN_KEY = "fkBookWishUser";
    private static final String RESERVATION_USER_FOREIGN_KEY = "fkReservationUser";
    private static final String PATRON_USER_FOREIGN_KEY = "fkReservationPatronUser";
    private static final String DEMO_STUDENT_ID = "student";
    private static final String DEMO_TEACHER_ID = "teacher";
    private static final String DEMO_ADMIN_ID = "admin";
    private static final String MATH_ISBN = "9787040396621";
    private static final String NOVEL_ISBN = "9787020008735";
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
    private static final int LOAN_PERIOD_DAYS = 30;
    private static final int HOLD_PERIOD_DAYS = 7;
    private static final int MAX_DEFAULTS = 3;
    private static final int SUSPEND_DAYS = 30;
    private static final int DEMO_OVERDUE_BORROW_DAYS_AGO = 40;
    private static final int DEMO_CURRENT_BORROW_DAYS_AGO = 25;
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final AccessDatabase database;

    public AccessBookRepository(AccessDatabase database) throws SQLException {
        this.database = database;
        initializeDatabase();
    }

    @Override
    public List<Book> findBooks(String keyword, boolean includeInactive) throws SQLException {
        String pattern = toLikePattern(keyword);
        String sql = "SELECT [isbn], [title], [author], [publisher], [category], [active] "
                + "FROM [tblBook] WHERE "
                + (includeInactive ? "" : "[active] = true AND ")
                + "([isbn] LIKE ? OR [title] LIKE ? OR [author] LIKE ?) "
                + "ORDER BY [title]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            try (ResultSet result = statement.executeQuery()) {
                List<Book> books = new ArrayList<Book>();
                while (result.next()) {
                    books.add(mapBook(result));
                }
                return books;
            }
        }
    }

    @Override
    public Book findByIsbn(String isbn) throws SQLException {
        if (isBlank(isbn)) {
            return null;
        }
        String sql = "SELECT [isbn], [title], [author], [publisher], [category], [active] "
                + "FROM [tblBook] WHERE [isbn] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapBook(result) : null;
            }
        }
    }

    @Override
    public List<BookCopy> findCopiesByIsbn(String isbn) throws SQLException {
        String sql = "SELECT [copyId], [isbn], [copyStatus] FROM [tblBookCopy] "
                + "WHERE [isbn] = ? ORDER BY [copyId]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            try (ResultSet result = statement.executeQuery()) {
                List<BookCopy> copies = new ArrayList<BookCopy>();
                while (result.next()) {
                    copies.add(mapCopy(result));
                }
                return copies;
            }
        }
    }

    @Override
    public int countAvailableCopies(String isbn) throws SQLException {
        return countCopiesByStatus(isbn, BookCopy.STATUS_AVAILABLE);
    }

    @Override
    public int countBorrowedCopies(String isbn) throws SQLException {
        return countCopiesByStatus(isbn, BookCopy.STATUS_BORROWED);
    }

    @Override
    public int countBorrowRecordsByIsbn(String isbn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblBorrowRecord] AS r "
                + "INNER JOIN [tblBookCopy] AS c ON r.[copyId] = c.[copyId] "
                + "WHERE c.[isbn] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    @Override
    public int countRemovableCopies(String isbn) throws SQLException {
        try (Connection connection = database.openConnection()) {
            return countRemovableCopies(connection, isbn);
        }
    }

    @Override
    public void saveBook(Book book, int desiredCopies) throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (updateBook(connection, book) == 0) {
                    insertBook(connection, book.getIsbn(), book.getTitle(), book.getAuthor(),
                            book.getPublisher(), book.getCategory(), book.isActive());
                }
                int total = countCopies(connection, book.getIsbn());
                for (int i = total; i < desiredCopies; i++) {
                    insertCopy(connection, book.getIsbn(), BookCopy.STATUS_AVAILABLE);
                }
                int toRemove = total - desiredCopies;
                for (int i = 0; i < toRemove; i++) {
                    if (!deleteOneRemovableCopy(connection, book.getIsbn())) {
                        throw new SQLException("No removable copy left for ISBN " + book.getIsbn());
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean deleteBook(String isbn) throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                deleteCopies(connection, isbn);
                boolean deleted = deleteBookRow(connection, isbn);
                connection.commit();
                return deleted;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<BorrowRecord> findBorrowRecordsByUser(String userId) throws SQLException {
        String sql = borrowSelectSql() + " WHERE r.[userId] = ? ORDER BY r.[borrowTime] DESC";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            return mapRecords(statement);
        }
    }

    @Override
    public List<BorrowRecord> findActiveBorrowRecords() throws SQLException {
        String sql = borrowSelectSql()
                + " WHERE (r.[returnTime] IS NULL OR r.[returnTime] = '')"
                + " ORDER BY r.[borrowTime] DESC";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return mapRecords(statement);
        }
    }

    @Override
    public BorrowRecord findBorrowRecordById(int recordId) throws SQLException {
        try (Connection connection = database.openConnection()) {
            return findBorrowRecordById(connection, recordId);
        }
    }

    private BorrowRecord findBorrowRecordById(Connection connection, int recordId)
            throws SQLException {
        String sql = borrowSelectSql() + " WHERE r.[recordId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, recordId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapRecord(result) : null;
            }
        }
    }

    @Override
    public boolean hasActiveBorrow(String userId, String isbn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblBorrowRecord] AS r "
                + "INNER JOIN [tblBookCopy] AS c ON r.[copyId] = c.[copyId] "
                + "WHERE r.[userId] = ? AND c.[isbn] = ? "
                + "AND (r.[returnTime] IS NULL OR r.[returnTime] = '')";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, isbn);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public boolean hasOverdueBorrow(String userId) throws SQLException {
        if (isBlank(userId)) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM [tblBorrowRecord] "
                + "WHERE [userId] = ? AND ([returnTime] IS NULL OR [returnTime] = '') "
                + "AND [dueTime] < ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, formatTime(new Date()));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public BorrowRecord borrowAvailableCopy(String userId, String isbn, Date borrowTime,
                                            Date dueTime) throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                int copyId = findFirstCopyId(connection, isbn, BookCopy.STATUS_AVAILABLE);
                if (copyId < 0) {
                    connection.rollback();
                    return null;
                }
                updateCopyStatus(connection, copyId, BookCopy.STATUS_BORROWED);
                int recordId = insertBorrowRecord(connection, copyId, userId,
                        formatTime(borrowTime), formatTime(dueTime));
                BorrowRecord created = findBorrowRecordById(connection, recordId);
                if (created == null) {
                    throw new SQLException("Inserted borrow record was not found: " + recordId);
                }
                connection.commit();
                return created;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean returnBorrow(int recordId, Date returnTime) throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer copyId = findCopyIdForOpenRecord(connection, recordId);
                if (copyId == null) {
                    connection.rollback();
                    return false;
                }
                String sql = "UPDATE [tblBorrowRecord] SET [returnTime] = ? "
                        + "WHERE [recordId] = ? AND ([returnTime] IS NULL OR [returnTime] = '')";
                int updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, formatTime(returnTime));
                    statement.setInt(2, recordId);
                    updated = statement.executeUpdate();
                }
                if (updated == 0) {
                    connection.rollback();
                    return false;
                }
                Integer reservationId = findApprovedReservationIdForCopy(connection, copyId.intValue());
                if (reservationId != null) {
                    updateCopyStatus(connection, copyId.intValue(), BookCopy.STATUS_HELD);
                    Date holdUntil = new Date(returnTime.getTime() + HOLD_PERIOD_DAYS * DAY_MILLIS);
                    markReservationHeld(connection, reservationId.intValue(), returnTime, holdUntil);
                } else {
                    updateCopyStatus(connection, copyId.intValue(), BookCopy.STATUS_AVAILABLE);
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean renewBorrow(int recordId, Date newDueTime, int expectedRenewCount,
                               int newRenewCount) throws SQLException {
        if (newDueTime == null || newRenewCount < 0) {
            return false;
        }
        String sql = "UPDATE [tblBorrowRecord] SET [dueTime] = ?, [renewCount] = ? "
                + "WHERE [recordId] = ? AND [renewCount] = ? "
                + "AND ([returnTime] IS NULL OR [returnTime] = '')";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, formatTime(newDueTime));
            statement.setInt(2, newRenewCount);
            statement.setInt(3, recordId);
            statement.setInt(4, expectedRenewCount);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean isRenewalBlockedByReservation(int copyId) throws SQLException {
        if (copyId <= 0) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM [tblReservation] "
                + "WHERE [copyId] = ? AND [status] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, copyId);
            statement.setString(2, BookReservation.STATUS_APPROVED);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public List<BookWish> findWishesByUser(String userId) throws SQLException {
        if (isBlank(userId)) {
            return new ArrayList<BookWish>();
        }
        String sql = wishSelectSql() + " WHERE w.[userId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId.trim());
            return sortWishes(mapWishes(statement));
        }
    }

    @Override
    public List<BookWish> findAllWishes() throws SQLException {
        String sql = wishSelectSql();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return sortWishes(mapWishes(statement));
        }
    }

    @Override
    public BookWish findWishById(int wishId) throws SQLException {
        if (wishId <= 0) {
            return null;
        }
        String sql = wishSelectSql() + " WHERE w.[wishId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, wishId);
            List<BookWish> rows = mapWishes(statement);
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    @Override
    public boolean hasPendingWish(String userId, String title, String author) throws SQLException {
        if (isBlank(userId) || isBlank(title) || isBlank(author)) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM [tblBookWish] "
                + "WHERE [userId] = ? AND [title] = ? AND [author] = ? AND [status] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId.trim());
            statement.setString(2, title.trim());
            statement.setString(3, author.trim());
            statement.setString(4, BookWish.STATUS_PENDING);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public BookWish insertWish(String userId, String title, String author, Date submitTime)
            throws SQLException {
        String sql = "INSERT INTO [tblBookWish] ([userId], [title], [author], [status], "
                + "[submitTime], [reviewTime], [reviewerUserId], [isbn]) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        int generatedId;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, userId);
            statement.setString(2, title);
            statement.setString(3, author);
            statement.setString(4, BookWish.STATUS_PENDING);
            statement.setString(5, formatTime(submitTime));
            statement.setNull(6, Types.VARCHAR);
            statement.setNull(7, Types.VARCHAR);
            statement.setNull(8, Types.VARCHAR);
            statement.executeUpdate();
            generatedId = readGeneratedId(statement);
            if (generatedId <= 0) {
                generatedId = lookupLatestWishId(connection, userId, title, author);
            }
        }
        return findWishById(generatedId);
    }

    @Override
    public boolean markWishApproved(int wishId, String reviewerUserId, String isbn, Date reviewTime)
            throws SQLException {
        return updateWishStatus(wishId, BookWish.STATUS_APPROVED, reviewerUserId, isbn, reviewTime);
    }

    @Override
    public boolean markWishRejected(int wishId, String reviewerUserId, Date reviewTime)
            throws SQLException {
        return updateWishStatus(wishId, BookWish.STATUS_REJECTED, reviewerUserId, null, reviewTime);
    }

    @Override
    public int expireHeldReservations(Date now) throws SQLException {
        if (now == null) {
            now = new Date();
        }
        final Date cutoff = now;
        String cutoffText = formatTime(cutoff);
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                List<Integer> reservationIds = new ArrayList<Integer>();
                List<Integer> copyIds = new ArrayList<Integer>();
                List<String> userIds = new ArrayList<String>();
                String select = "SELECT [reservationId], [copyId], [userId] FROM [tblReservation] "
                        + "WHERE [status] = ? AND [holdUntilTime] IS NOT NULL "
                        + "AND [holdUntilTime] <> '' AND [holdUntilTime] < ?";
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setString(1, BookReservation.STATUS_HELD);
                    statement.setString(2, cutoffText);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            reservationIds.add(Integer.valueOf(result.getInt("reservationId")));
                            copyIds.add(Integer.valueOf(result.getInt("copyId")));
                            userIds.add(result.getString("userId"));
                        }
                    }
                }
                for (int i = 0; i < reservationIds.size(); i++) {
                    updateCopyStatus(connection, copyIds.get(i).intValue(),
                            BookCopy.STATUS_AVAILABLE);
                    markReservationExpired(connection, reservationIds.get(i).intValue());
                    recordDefault(connection, userIds.get(i), cutoff);
                }
                connection.commit();
                return reservationIds.size();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<BookReservation> findReservationsByUser(String userId) throws SQLException {
        if (isBlank(userId)) {
            return new ArrayList<BookReservation>();
        }
        String sql = reservationSelectSql() + " WHERE r.[userId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId.trim());
            return sortReservations(mapReservations(statement));
        }
    }

    @Override
    public List<BookReservation> findAllReservations() throws SQLException {
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(reservationSelectSql())) {
            return sortReservations(mapReservations(statement));
        }
    }

    @Override
    public BookReservation findReservationById(int reservationId) throws SQLException {
        if (reservationId <= 0) {
            return null;
        }
        String sql = reservationSelectSql() + " WHERE r.[reservationId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reservationId);
            List<BookReservation> rows = mapReservations(statement);
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    @Override
    public boolean hasActiveReservation(String userId, String isbn) throws SQLException {
        if (isBlank(userId) || isBlank(isbn)) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM [tblReservation] WHERE [userId] = ? AND [isbn] = ? "
                + "AND ([status] = ? OR [status] = ? OR [status] = ?)";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId.trim());
            statement.setString(2, isbn.trim());
            statement.setString(3, BookReservation.STATUS_PENDING);
            statement.setString(4, BookReservation.STATUS_APPROVED);
            statement.setString(5, BookReservation.STATUS_HELD);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public BookReservation insertReservation(String userId, String isbn, Date applyTime)
            throws SQLException {
        String sql = "INSERT INTO [tblReservation] ([userId], [isbn], [copyId], [status], "
                + "[applyTime], [reviewTime], [reviewerUserId], [holdUntilTime], [pickupTime]) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int generatedId;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, userId);
            statement.setString(2, isbn);
            statement.setNull(3, Types.INTEGER);
            statement.setString(4, BookReservation.STATUS_PENDING);
            statement.setString(5, formatTime(applyTime));
            statement.setNull(6, Types.VARCHAR);
            statement.setNull(7, Types.VARCHAR);
            statement.setNull(8, Types.VARCHAR);
            statement.setNull(9, Types.VARCHAR);
            statement.executeUpdate();
            generatedId = readGeneratedId(statement);
            if (generatedId <= 0) {
                generatedId = lookupLatestReservationId(connection, userId, isbn);
            }
        }
        return findReservationById(generatedId);
    }

    @Override
    public boolean markReservationApproved(int reservationId, String reviewerUserId, int copyId,
                                           Date reviewTime) throws SQLException {
        String sql = "UPDATE [tblReservation] SET [status] = ?, [reviewTime] = ?, "
                + "[reviewerUserId] = ?, [copyId] = ? "
                + "WHERE [reservationId] = ? AND [status] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, BookReservation.STATUS_APPROVED);
            statement.setString(2, formatTime(reviewTime));
            statement.setString(3, reviewerUserId);
            statement.setInt(4, copyId);
            statement.setInt(5, reservationId);
            statement.setString(6, BookReservation.STATUS_PENDING);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean markReservationRejected(int reservationId, String reviewerUserId, Date reviewTime)
            throws SQLException {
        String sql = "UPDATE [tblReservation] SET [status] = ?, [reviewTime] = ?, "
                + "[reviewerUserId] = ? WHERE [reservationId] = ? AND [status] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, BookReservation.STATUS_REJECTED);
            statement.setString(2, formatTime(reviewTime));
            statement.setString(3, reviewerUserId);
            statement.setInt(4, reservationId);
            statement.setString(5, BookReservation.STATUS_PENDING);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public Integer findEarliestReservableCopy(String isbn) throws SQLException {
        if (isBlank(isbn)) {
            return null;
        }
        String sql = "SELECT c.[copyId] FROM ([tblBookCopy] AS c "
                + "INNER JOIN [tblBorrowRecord] AS r ON c.[copyId] = r.[copyId]) "
                + "WHERE c.[isbn] = ? AND c.[copyStatus] = ? "
                + "AND (r.[returnTime] IS NULL OR r.[returnTime] = '') "
                + "AND NOT EXISTS (SELECT 1 FROM [tblReservation] AS x "
                + "WHERE x.[copyId] = c.[copyId] AND x.[status] = ?) "
                + "ORDER BY r.[dueTime] ASC";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn.trim());
            statement.setString(2, BookCopy.STATUS_BORROWED);
            statement.setString(3, BookReservation.STATUS_APPROVED);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return Integer.valueOf(result.getInt(1));
            }
        }
    }

    @Override
    public Date findPatronSuspendUntil(String userId) throws SQLException {
        if (isBlank(userId)) {
            return null;
        }
        String sql = "SELECT [suspendUntil] FROM [tblReservationPatron] WHERE [userId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId.trim());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? parseTime(result.getString("suspendUntil")) : null;
            }
        }
    }

    @Override
    public int findPatronDefaultCount(String userId) throws SQLException {
        if (isBlank(userId)) {
            return 0;
        }
        String sql = "SELECT [defaultCount] FROM [tblReservationPatron] WHERE [userId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId.trim());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    @Override
    public void clearExpiredSuspension(String userId, Date now) throws SQLException {
        Date until = findPatronSuspendUntil(userId);
        if (until == null || now == null || !until.before(now)) {
            return;
        }
        String sql = "UPDATE [tblReservationPatron] SET [defaultCount] = 0, [suspendUntil] = ? "
                + "WHERE [userId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setNull(1, Types.VARCHAR);
            statement.setString(2, userId.trim());
            statement.executeUpdate();
        }
    }

    @Override
    public BorrowRecord pickupHeldReservation(int reservationId, String borrowerUserId,
                                              Date borrowTime, Date dueTime)
            throws SQLException {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                BookReservation reservation = findReservationByIdOn(connection, reservationId);
                if (reservation == null || !reservation.isHeld() || reservation.getCopyId() == null) {
                    connection.rollback();
                    return null;
                }
                int copyId = reservation.getCopyId().intValue();
                updateCopyStatus(connection, copyId, BookCopy.STATUS_BORROWED);
                int recordId = insertBorrowRecord(connection, copyId, borrowerUserId,
                        formatTime(borrowTime), formatTime(dueTime));
                markReservationPickedUp(connection, reservationId, borrowTime);
                BorrowRecord created = findBorrowRecordById(connection, recordId);
                if (created == null) {
                    throw new SQLException("Inserted borrow record was not found: " + recordId);
                }
                connection.commit();
                return created;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Moves an open record's due time without changing {@code renewCount}.
     * Used by tests to place a loan inside or outside the renewal window.
     */
    public boolean forceDueTime(int recordId, Date dueTime) throws SQLException {
        if (dueTime == null) {
            return false;
        }
        String sql = "UPDATE [tblBorrowRecord] SET [dueTime] = ? "
                + "WHERE [recordId] = ? AND ([returnTime] IS NULL OR [returnTime] = '')";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, formatTime(dueTime));
            statement.setInt(2, recordId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Moves a held reservation's deadline. Used by tests to expire a hold.
     */
    public boolean forceHoldUntil(int reservationId, Date holdUntil) throws SQLException {
        if (holdUntil == null) {
            return false;
        }
        String sql = "UPDATE [tblReservation] SET [holdUntilTime] = ? "
                + "WHERE [reservationId] = ? AND [status] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, formatTime(holdUntil));
            statement.setInt(2, reservationId);
            statement.setString(3, BookReservation.STATUS_HELD);
            return statement.executeUpdate() > 0;
        }
    }

    /** Used by tests to place a patron at a given default / suspension state. */
    public void forcePatronDefaults(String userId, int defaultCount, Date suspendUntil)
            throws SQLException {
        try (Connection connection = database.openConnection()) {
            ensurePatronRow(connection, userId);
            String sql = "UPDATE [tblReservationPatron] SET [defaultCount] = ?, [suspendUntil] = ? "
                    + "WHERE [userId] = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, defaultCount);
                if (suspendUntil == null) {
                    statement.setNull(2, Types.VARCHAR);
                } else {
                    statement.setString(2, formatTime(suspendUntil));
                }
                statement.setString(3, userId);
                statement.executeUpdate();
            }
        }
    }

    private String borrowSelectSql() {
        return "SELECT r.[recordId], r.[copyId], r.[userId], r.[borrowTime], r.[dueTime], "
                + "r.[returnTime], r.[renewCount], c.[isbn], b.[title], b.[author], u.[displayName] "
                + "FROM (([tblBorrowRecord] AS r "
                + "INNER JOIN [tblBookCopy] AS c ON r.[copyId] = c.[copyId]) "
                + "INNER JOIN [tblBook] AS b ON c.[isbn] = b.[isbn]) "
                + "INNER JOIN [tblUser] AS u ON r.[userId] = u.[userId]";
    }

    private List<BorrowRecord> mapRecords(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<BorrowRecord> records = new ArrayList<BorrowRecord>();
            while (result.next()) {
                records.add(mapRecord(result));
            }
            return records;
        }
    }

    private Integer findCopyIdForOpenRecord(Connection connection, int recordId)
            throws SQLException {
        String sql = "SELECT [copyId] FROM [tblBorrowRecord] "
                + "WHERE [recordId] = ? AND ([returnTime] IS NULL OR [returnTime] = '')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, recordId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return Integer.valueOf(result.getInt(1));
            }
        }
    }

    private void initializeDatabase() throws SQLException {
        try (Connection connection = database.openConnection()) {
            if (!tableExists(connection, "tblUser")) {
                throw new SQLException(
                        "tblUser must be initialized before the library repository");
            }
            if (!tableExists(connection, "tblBook")) {
                createBookTable(connection);
            }
            if (!tableExists(connection, "tblBookCopy")) {
                createBookCopyTable(connection);
            }
            if (!tableExists(connection, "tblBorrowRecord")) {
                createBorrowRecordTable(connection);
            }
            if (!tableExists(connection, "tblBookWish")) {
                createBookWishTable(connection);
            }
            if (!tableExists(connection, "tblReservation")) {
                createReservationTable(connection);
            }
            if (!tableExists(connection, "tblReservationPatron")) {
                createReservationPatronTable(connection);
            }
            migrateBorrowTimesToText(connection);
            ensureBorrowUserForeignKey(connection);
            ensureRenewCountColumn(connection);
            if (countBooks(connection) == 0) {
                insertDemoData(connection);
            }
            alignDemoLoanPeriods(connection);
            if (countWishes(connection) == 0) {
                insertDemoWishes(connection);
            }
        }
    }

    /**
     * UCanAccess 5 cannot reliably UPDATE Access DATETIME columns, so borrow
     * times use the same controlled TEXT format as enrollment timestamps.
     * Existing DATETIME tables are rebuilt and demo borrows are re-seeded.
     */
    private void migrateBorrowTimesToText(Connection connection) throws SQLException {
        if (!tableExists(connection, "tblBorrowRecord") || borrowTimesAreText(connection)) {
            return;
        }
        // UCanAccess 5 rejects DROP CONSTRAINT outside Hibernate create mode.
        // tblBorrowRecord is the referencing table, so DROP TABLE removes the FK.
        execute(connection, "DROP TABLE [tblBorrowRecord]");
        createBorrowRecordTable(connection);
        resetBorrowedCopies(connection);
        if (bookExists(connection, MATH_ISBN) && bookExists(connection, NOVEL_ISBN)) {
            insertDemoBorrows(connection);
        }
    }

    private boolean borrowTimesAreText(Connection connection) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, null, null)) {
            while (columns.next()) {
                if ("tblBorrowRecord".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && "borrowTime".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    int dataType = columns.getInt("DATA_TYPE");
                    return dataType == Types.VARCHAR || dataType == Types.CHAR
                            || dataType == Types.LONGVARCHAR || dataType == Types.NVARCHAR
                            || dataType == Types.CLOB;
                }
            }
        }
        return true;
    }

    private void resetBorrowedCopies(Connection connection) throws SQLException {
        String sql = "UPDATE [tblBookCopy] SET [copyStatus] = ? WHERE [copyStatus] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, BookCopy.STATUS_AVAILABLE);
            statement.setString(2, BookCopy.STATUS_BORROWED);
            statement.executeUpdate();
        }
    }

    private boolean bookExists(Connection connection, String isbn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblBook] WHERE [isbn] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void createBookTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE [tblBook] ("
                + "[isbn] TEXT(32) NOT NULL PRIMARY KEY, "
                + "[title] TEXT(128) NOT NULL, "
                + "[author] TEXT(64) NOT NULL, "
                + "[publisher] TEXT(64), "
                + "[category] TEXT(32), "
                + "[active] YESNO NOT NULL)";
        execute(connection, sql);
    }

    private void createBookCopyTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE [tblBookCopy] ("
                + "[copyId] COUNTER PRIMARY KEY, "
                + "[isbn] TEXT(32) NOT NULL, "
                + "[copyStatus] TEXT(16) NOT NULL)";
        execute(connection, sql);
    }

    private void createBorrowRecordTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE [tblBorrowRecord] ("
                + "[recordId] COUNTER PRIMARY KEY, "
                + "[copyId] LONG NOT NULL, "
                + "[userId] TEXT(32) NOT NULL, "
                + "[borrowTime] TEXT(19) NOT NULL, "
                + "[dueTime] TEXT(19) NOT NULL, "
                + "[returnTime] TEXT(19), "
                + "[renewCount] INTEGER NOT NULL, "
                + "CONSTRAINT [" + BORROW_USER_FOREIGN_KEY + "] FOREIGN KEY ([userId]) "
                + "REFERENCES [tblUser] ([userId]))";
        execute(connection, sql);
    }

    private void createBookWishTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE [tblBookWish] ("
                + "[wishId] COUNTER PRIMARY KEY, "
                + "[userId] TEXT(32) NOT NULL, "
                + "[title] TEXT(128) NOT NULL, "
                + "[author] TEXT(64) NOT NULL, "
                + "[status] TEXT(16) NOT NULL, "
                + "[submitTime] TEXT(19) NOT NULL, "
                + "[reviewTime] TEXT(19), "
                + "[reviewerUserId] TEXT(32), "
                + "[isbn] TEXT(32), "
                + "CONSTRAINT [" + WISH_USER_FOREIGN_KEY + "] FOREIGN KEY ([userId]) "
                + "REFERENCES [tblUser] ([userId]))";
        execute(connection, sql);
    }

    private void createReservationTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE [tblReservation] ("
                + "[reservationId] COUNTER PRIMARY KEY, "
                + "[userId] TEXT(32) NOT NULL, "
                + "[isbn] TEXT(32) NOT NULL, "
                + "[copyId] LONG, "
                + "[status] TEXT(16) NOT NULL, "
                + "[applyTime] TEXT(19) NOT NULL, "
                + "[reviewTime] TEXT(19), "
                + "[reviewerUserId] TEXT(32), "
                + "[holdUntilTime] TEXT(19), "
                + "[pickupTime] TEXT(19), "
                + "CONSTRAINT [" + RESERVATION_USER_FOREIGN_KEY + "] FOREIGN KEY ([userId]) "
                + "REFERENCES [tblUser] ([userId]))";
        execute(connection, sql);
    }

    private void createReservationPatronTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE [tblReservationPatron] ("
                + "[userId] TEXT(32) NOT NULL PRIMARY KEY, "
                + "[defaultCount] INTEGER NOT NULL, "
                + "[suspendUntil] TEXT(19), "
                + "CONSTRAINT [" + PATRON_USER_FOREIGN_KEY + "] FOREIGN KEY ([userId]) "
                + "REFERENCES [tblUser] ([userId]))";
        execute(connection, sql);
    }

    /** Adds renewal count when upgrading a database created before version 1.5. */
    private void ensureRenewCountColumn(Connection connection) throws SQLException {
        if (!tableExists(connection, "tblBorrowRecord") || hasColumn(connection, "tblBorrowRecord",
                "renewCount")) {
            return;
        }
        execute(connection, "ALTER TABLE [tblBorrowRecord] ADD COLUMN [renewCount] INTEGER");
        execute(connection, "UPDATE [tblBorrowRecord] SET [renewCount] = 0 "
                + "WHERE [renewCount] IS NULL");
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, null, null)) {
            while (columns.next()) {
                if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Adds the user relation when upgrading a database created before version 1.3. */
    private void ensureBorrowUserForeignKey(Connection connection) throws SQLException {
        if (hasBorrowUserForeignKey(connection)) {
            return;
        }
        execute(connection, "ALTER TABLE [tblBorrowRecord] ADD CONSTRAINT ["
                + BORROW_USER_FOREIGN_KEY + "] FOREIGN KEY ([userId]) "
                + "REFERENCES [tblUser] ([userId])");
    }

    private boolean hasBorrowUserForeignKey(Connection connection) throws SQLException {
        try (ResultSet keys = connection.getMetaData().getImportedKeys(
                null, null, "tblBorrowRecord")) {
            while (keys.next()) {
                if ("userId".equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && "tblUser".equalsIgnoreCase(keys.getString("PKTABLE_NAME"))
                        && "userId".equalsIgnoreCase(keys.getString("PKCOLUMN_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private int countBooks(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM [tblBook]")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void insertDemoData(Connection connection) throws SQLException {
        insertBookWithCopies(connection, MATH_ISBN, "高等数学（上册）",
                "同济大学数学系", "高等教育出版社", "教材", 3);
        insertBookWithCopies(connection, "9787111544937", "计算机网络（第7版）",
                "谢希仁", "电子工业出版社", "计算机", 2);
        insertBookWithCopies(connection, "9787111544938", "Java核心技术 卷I",
                "Cay S. Horstmann", "机械工业出版社", "计算机", 2);
        insertBookWithCopies(connection, "9787101003048", "史记",
                "司马迁", "中华书局", "文学", 2);
        insertBookWithCopies(connection, NOVEL_ISBN, "红楼梦",
                "曹雪芹", "人民文学出版社", "文学", 2);
        insertBookWithCopies(connection, "9787040202489", "线性代数",
                "同济大学数学系", "高等教育出版社", "教材", 2);
        insertBookWithCopies(connection, "9787111407010", "算法导论",
                "Thomas H. Cormen", "机械工业出版社", "计算机", 2);
        insertBookWithCopies(connection, "9787302423287", "深入理解计算机系统",
                "Randal E. Bryant", "机械工业出版社", "计算机", 2);
        insertBookWithCopies(connection, "9787040396638", "概率论与数理统计",
                "浙江大学", "高等教育出版社", "教材", 2);
        insertBookWithCopies(connection, "9787020024759", "围城",
                "钱钟书", "人民文学出版社", "文学", 1);
        insertDemoBorrows(connection);
    }

    private int countWishes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM [tblBookWish]")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void insertDemoWishes(Connection connection) throws SQLException {
        Date now = new Date();
        insertWishRow(connection, DEMO_TEACHER_ID, "三体", "刘慈欣",
                BookWish.STATUS_PENDING, formatTime(now), null, null, null);
        insertWishRow(connection, DEMO_STUDENT_ID, "百年孤独", "加西亚·马尔克斯",
                BookWish.STATUS_REJECTED,
                formatTime(new Date(now.getTime() - DAY_MILLIS)),
                formatTime(now), DEMO_ADMIN_ID, null);
    }

    private void insertWishRow(Connection connection, String userId, String title, String author,
                               String status, String submitTime, String reviewTime,
                               String reviewerUserId, String isbn) throws SQLException {
        String sql = "INSERT INTO [tblBookWish] ([userId], [title], [author], [status], "
                + "[submitTime], [reviewTime], [reviewerUserId], [isbn]) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, title);
            statement.setString(3, author);
            statement.setString(4, status);
            statement.setString(5, submitTime);
            if (reviewTime == null) {
                statement.setNull(6, Types.VARCHAR);
            } else {
                statement.setString(6, reviewTime);
            }
            if (reviewerUserId == null) {
                statement.setNull(7, Types.VARCHAR);
            } else {
                statement.setString(7, reviewerUserId);
            }
            if (isbn == null) {
                statement.setNull(8, Types.VARCHAR);
            } else {
                statement.setString(8, isbn);
            }
            statement.executeUpdate();
        }
    }

    private void insertDemoBorrows(Connection connection) throws SQLException {
        Date now = new Date();
        long loanMillis = loanPeriodMillis();
        Date currentBorrowed = new Date(now.getTime() - DEMO_CURRENT_BORROW_DAYS_AGO * DAY_MILLIS);
        borrowCopy(connection, MATH_ISBN, DEMO_STUDENT_ID,
                formatTime(currentBorrowed),
                formatTime(new Date(currentBorrowed.getTime() + loanMillis)));
        Date overdueBorrowed = new Date(now.getTime() - DEMO_OVERDUE_BORROW_DAYS_AGO * DAY_MILLIS);
        borrowCopy(connection, NOVEL_ISBN, DEMO_STUDENT_ID,
                formatTime(overdueBorrowed),
                formatTime(new Date(overdueBorrowed.getTime() + loanMillis)));
    }

    /**
     * Older seeds used a 20-day loan. Rewrite the two demo student's open
     * records so dueTime is always borrowTime plus {@value #LOAN_PERIOD_DAYS} days.
     */
    private void alignDemoLoanPeriods(Connection connection) throws SQLException {
        if (!tableExists(connection, "tblBorrowRecord")) {
            return;
        }
        String sql = "SELECT r.[recordId], r.[borrowTime], r.[dueTime], r.[renewCount] "
                + "FROM [tblBorrowRecord] AS r "
                + "INNER JOIN [tblBookCopy] AS c ON r.[copyId] = c.[copyId] "
                + "WHERE r.[userId] = ? AND (c.[isbn] = ? OR c.[isbn] = ?) "
                + "AND (r.[returnTime] IS NULL OR r.[returnTime] = '')";
        List<Integer> recordIds = new ArrayList<Integer>();
        List<String> dueTimes = new ArrayList<String>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DEMO_STUDENT_ID);
            statement.setString(2, MATH_ISBN);
            statement.setString(3, NOVEL_ISBN);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (result.getInt("renewCount") > 0) {
                        continue;
                    }
                    Date borrowTime = parseTime(result.getString("borrowTime"));
                    if (borrowTime == null) {
                        continue;
                    }
                    String expectedDue = formatTime(new Date(borrowTime.getTime() + loanPeriodMillis()));
                    String actualDue = result.getString("dueTime");
                    if (expectedDue.equals(actualDue)) {
                        continue;
                    }
                    recordIds.add(Integer.valueOf(result.getInt("recordId")));
                    dueTimes.add(expectedDue);
                }
            }
        }
        for (int i = 0; i < recordIds.size(); i++) {
            updateBorrowDueTime(connection, recordIds.get(i).intValue(), dueTimes.get(i));
        }
    }

    private long loanPeriodMillis() {
        return LOAN_PERIOD_DAYS * DAY_MILLIS;
    }

    private void insertBookWithCopies(Connection connection, String isbn, String title,
                                      String author, String publisher, String category,
                                      int copyCount) throws SQLException {
        insertBook(connection, isbn, title, author, publisher, category, true);
        for (int i = 0; i < copyCount; i++) {
            insertCopy(connection, isbn, BookCopy.STATUS_AVAILABLE);
        }
    }

    private int updateBook(Connection connection, Book book) throws SQLException {
        String sql = "UPDATE [tblBook] SET [title]=?, [author]=?, [publisher]=?, "
                + "[category]=?, [active]=? WHERE [isbn]=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, book.getTitle());
            statement.setString(2, book.getAuthor());
            statement.setString(3, book.getPublisher());
            statement.setString(4, book.getCategory());
            statement.setBoolean(5, book.isActive());
            statement.setString(6, book.getIsbn());
            return statement.executeUpdate();
        }
    }

    private void insertBook(Connection connection, String isbn, String title, String author,
                            String publisher, String category, boolean active)
            throws SQLException {
        String sql = "INSERT INTO [tblBook] ([isbn], [title], [author], [publisher], "
                + "[category], [active]) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            statement.setString(2, title);
            statement.setString(3, author);
            statement.setString(4, publisher);
            statement.setString(5, category);
            statement.setBoolean(6, active);
            statement.executeUpdate();
        }
    }

    private int countCopiesByStatus(String isbn, String copyStatus) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblBookCopy] "
                + "WHERE [isbn] = ? AND [copyStatus] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            statement.setString(2, copyStatus);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private int countCopies(Connection connection, String isbn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblBookCopy] WHERE [isbn] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private int countRemovableCopies(Connection connection, String isbn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblBookCopy] AS c "
                + "WHERE c.[isbn] = ? AND c.[copyStatus] = ? "
                + "AND NOT EXISTS (SELECT 1 FROM [tblBorrowRecord] AS r "
                + "WHERE r.[copyId] = c.[copyId])";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            statement.setString(2, BookCopy.STATUS_AVAILABLE);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private boolean deleteOneRemovableCopy(Connection connection, String isbn)
            throws SQLException {
        String findSql = "SELECT MIN(c.[copyId]) FROM [tblBookCopy] AS c "
                + "WHERE c.[isbn] = ? AND c.[copyStatus] = ? "
                + "AND NOT EXISTS (SELECT 1 FROM [tblBorrowRecord] AS r "
                + "WHERE r.[copyId] = c.[copyId])";
        Integer copyId = null;
        try (PreparedStatement statement = connection.prepareStatement(findSql)) {
            statement.setString(1, isbn);
            statement.setString(2, BookCopy.STATUS_AVAILABLE);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    int value = result.getInt(1);
                    if (!result.wasNull()) {
                        copyId = Integer.valueOf(value);
                    }
                }
            }
        }
        if (copyId == null) {
            return false;
        }
        String deleteSql = "DELETE FROM [tblBookCopy] WHERE [copyId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            statement.setInt(1, copyId.intValue());
            return statement.executeUpdate() > 0;
        }
    }

    private void deleteCopies(Connection connection, String isbn) throws SQLException {
        String sql = "DELETE FROM [tblBookCopy] WHERE [isbn] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            statement.executeUpdate();
        }
    }

    private boolean deleteBookRow(Connection connection, String isbn) throws SQLException {
        String sql = "DELETE FROM [tblBook] WHERE [isbn] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            return statement.executeUpdate() > 0;
        }
    }

    private void insertCopy(Connection connection, String isbn, String copyStatus)
            throws SQLException {
        String sql = "INSERT INTO [tblBookCopy] ([isbn], [copyStatus]) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            statement.setString(2, copyStatus);
            statement.executeUpdate();
        }
    }

    private void borrowCopy(Connection connection, String isbn, String userId,
                            String borrowTime, String dueTime) throws SQLException {
        int copyId = findFirstCopyId(connection, isbn, BookCopy.STATUS_AVAILABLE);
        if (copyId < 0) {
            throw new SQLException("No available copy for ISBN " + isbn);
        }
        updateCopyStatus(connection, copyId, BookCopy.STATUS_BORROWED);
        insertBorrowRecord(connection, copyId, userId, borrowTime, dueTime);
    }

    private int findFirstCopyId(Connection connection, String isbn, String copyStatus)
            throws SQLException {
        String sql = "SELECT MIN([copyId]) FROM [tblBookCopy] "
                + "WHERE [isbn] = ? AND [copyStatus] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            statement.setString(2, copyStatus);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return -1;
                }
                int copyId = result.getInt(1);
                return result.wasNull() ? -1 : copyId;
            }
        }
    }

    private void updateCopyStatus(Connection connection, int copyId, String copyStatus)
            throws SQLException {
        String sql = "UPDATE [tblBookCopy] SET [copyStatus] = ? WHERE [copyId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, copyStatus);
            statement.setInt(2, copyId);
            statement.executeUpdate();
        }
    }

    private void updateBorrowDueTime(Connection connection, int recordId, String dueTime)
            throws SQLException {
        String sql = "UPDATE [tblBorrowRecord] SET [dueTime] = ? WHERE [recordId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dueTime);
            statement.setInt(2, recordId);
            statement.executeUpdate();
        }
    }

    private int insertBorrowRecord(Connection connection, int copyId, String userId,
                                   String borrowTime, String dueTime)
            throws SQLException {
        String sql = "INSERT INTO [tblBorrowRecord] ([copyId], [userId], [borrowTime], "
                + "[dueTime], [returnTime], [renewCount]) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, copyId);
            statement.setString(2, userId);
            statement.setString(3, borrowTime);
            statement.setString(4, dueTime);
            statement.setNull(5, Types.VARCHAR);
            statement.setInt(6, 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    int generated = keys.getInt(1);
                    if (!keys.wasNull() && generated > 0) {
                        return generated;
                    }
                }
            }
        }
        return lookupLatestRecordId(connection, copyId, userId);
    }

    private int lookupLatestRecordId(Connection connection, int copyId, String userId)
            throws SQLException {
        String sql = "SELECT MAX([recordId]) FROM [tblBorrowRecord] "
                + "WHERE [copyId] = ? AND [userId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, copyId);
            statement.setString(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Inserted borrow record was not found");
                }
                int recordId = result.getInt(1);
                if (result.wasNull()) {
                    throw new SQLException("Inserted borrow record was not found");
                }
                return recordId;
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Book mapBook(ResultSet result) throws SQLException {
        return new Book(
                result.getString("isbn"),
                result.getString("title"),
                result.getString("author"),
                result.getString("publisher"),
                result.getString("category"),
                result.getBoolean("active"));
    }

    private BookCopy mapCopy(ResultSet result) throws SQLException {
        return new BookCopy(
                result.getInt("copyId"),
                result.getString("isbn"),
                result.getString("copyStatus"));
    }

    private BorrowRecord mapRecord(ResultSet result) throws SQLException {
        return new BorrowRecord(
                result.getInt("recordId"),
                result.getInt("copyId"),
                result.getString("userId"),
                parseTime(result.getString("borrowTime")),
                parseTime(result.getString("dueTime")),
                parseTime(result.getString("returnTime")),
                result.getString("isbn"),
                result.getString("title"),
                result.getString("author"),
                result.getString("displayName"),
                result.getInt("renewCount"));
    }

    private String wishSelectSql() {
        return "SELECT w.[wishId], w.[userId], u.[displayName], w.[title], w.[author], "
                + "w.[status], w.[submitTime], w.[reviewTime], w.[reviewerUserId], w.[isbn] "
                + "FROM [tblBookWish] AS w INNER JOIN [tblUser] AS u ON w.[userId] = u.[userId]";
    }

    private List<BookWish> mapWishes(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<BookWish> wishes = new ArrayList<BookWish>();
            while (result.next()) {
                wishes.add(mapWish(result));
            }
            return wishes;
        }
    }

    private BookWish mapWish(ResultSet result) throws SQLException {
        return new BookWish(
                result.getInt("wishId"),
                result.getString("userId"),
                result.getString("displayName"),
                result.getString("title"),
                result.getString("author"),
                result.getString("status"),
                parseTime(result.getString("submitTime")),
                parseTime(result.getString("reviewTime")),
                result.getString("reviewerUserId"),
                result.getString("isbn"));
    }

    private List<BookWish> sortWishes(List<BookWish> wishes) {
        Collections.sort(wishes, new Comparator<BookWish>() {
            @Override
            public int compare(BookWish left, BookWish right) {
                int pending = (left.isPending() ? 0 : 1) - (right.isPending() ? 0 : 1);
                if (pending != 0) {
                    return pending;
                }
                Date leftTime = left.getSubmitTime();
                Date rightTime = right.getSubmitTime();
                if (leftTime == null && rightTime == null) {
                    return 0;
                }
                if (leftTime == null) {
                    return 1;
                }
                if (rightTime == null) {
                    return -1;
                }
                return rightTime.compareTo(leftTime);
            }
        });
        return wishes;
    }

    private boolean updateWishStatus(int wishId, String status, String reviewerUserId,
                                     String isbn, Date reviewTime) throws SQLException {
        String sql = "UPDATE [tblBookWish] SET [status] = ?, [reviewTime] = ?, "
                + "[reviewerUserId] = ?, [isbn] = ? "
                + "WHERE [wishId] = ? AND [status] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, formatTime(reviewTime));
            statement.setString(3, reviewerUserId);
            if (isbn == null || isbn.trim().isEmpty()) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, isbn.trim());
            }
            statement.setInt(5, wishId);
            statement.setString(6, BookWish.STATUS_PENDING);
            return statement.executeUpdate() > 0;
        }
    }

    private int readGeneratedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                int generated = keys.getInt(1);
                if (!keys.wasNull() && generated > 0) {
                    return generated;
                }
            }
        }
        return -1;
    }

    private int lookupLatestWishId(Connection connection, String userId, String title, String author)
            throws SQLException {
        String sql = "SELECT MAX([wishId]) FROM [tblBookWish] "
                + "WHERE [userId] = ? AND [title] = ? AND [author] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, title);
            statement.setString(3, author);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Inserted book wish was not found");
                }
                int wishId = result.getInt(1);
                if (result.wasNull()) {
                    throw new SQLException("Inserted book wish was not found");
                }
                return wishId;
            }
        }
    }

    private String reservationSelectSql() {
        return "SELECT r.[reservationId], r.[userId], u.[displayName], r.[isbn], b.[title], "
                + "b.[author], r.[copyId], r.[status], r.[applyTime], r.[reviewTime], "
                + "r.[holdUntilTime], r.[pickupTime], p.[defaultCount], p.[suspendUntil] "
                + "FROM ((([tblReservation] AS r "
                + "INNER JOIN [tblBook] AS b ON r.[isbn] = b.[isbn]) "
                + "INNER JOIN [tblUser] AS u ON r.[userId] = u.[userId]) "
                + "LEFT JOIN [tblReservationPatron] AS p ON r.[userId] = p.[userId])";
    }

    private List<BookReservation> mapReservations(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<BookReservation> rows = new ArrayList<BookReservation>();
            while (result.next()) {
                rows.add(mapReservation(result));
            }
            return rows;
        }
    }

    private BookReservation mapReservation(ResultSet result) throws SQLException {
        int copyId = result.getInt("copyId");
        Integer copy = result.wasNull() || copyId <= 0 ? null : Integer.valueOf(copyId);
        int defaultCount = result.getInt("defaultCount");
        if (result.wasNull()) {
            defaultCount = 0;
        }
        return new BookReservation(
                result.getInt("reservationId"),
                result.getString("userId"),
                result.getString("displayName"),
                result.getString("isbn"),
                result.getString("title"),
                result.getString("author"),
                copy,
                result.getString("status"),
                parseTime(result.getString("applyTime")),
                parseTime(result.getString("reviewTime")),
                parseTime(result.getString("holdUntilTime")),
                parseTime(result.getString("pickupTime")),
                defaultCount,
                parseTime(result.getString("suspendUntil")));
    }

    private List<BookReservation> sortReservations(List<BookReservation> rows) {
        Collections.sort(rows, new Comparator<BookReservation>() {
            @Override
            public int compare(BookReservation left, BookReservation right) {
                int progress = (left.isInProgress() ? 0 : 1) - (right.isInProgress() ? 0 : 1);
                if (progress != 0) {
                    return progress;
                }
                Date leftTime = left.getApplyTime();
                Date rightTime = right.getApplyTime();
                if (leftTime == null && rightTime == null) {
                    return 0;
                }
                if (leftTime == null) {
                    return 1;
                }
                if (rightTime == null) {
                    return -1;
                }
                return rightTime.compareTo(leftTime);
            }
        });
        return rows;
    }

    private BookReservation findReservationByIdOn(Connection connection, int reservationId)
            throws SQLException {
        String sql = reservationSelectSql() + " WHERE r.[reservationId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reservationId);
            List<BookReservation> rows = mapReservations(statement);
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    private Integer findApprovedReservationIdForCopy(Connection connection, int copyId)
            throws SQLException {
        String sql = "SELECT [reservationId] FROM [tblReservation] "
                + "WHERE [copyId] = ? AND [status] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, copyId);
            statement.setString(2, BookReservation.STATUS_APPROVED);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return Integer.valueOf(result.getInt(1));
            }
        }
    }

    private void markReservationHeld(Connection connection, int reservationId, Date holdStart,
                                     Date holdUntil) throws SQLException {
        String sql = "UPDATE [tblReservation] SET [status] = ?, [holdUntilTime] = ? "
                + "WHERE [reservationId] = ? AND [status] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, BookReservation.STATUS_HELD);
            statement.setString(2, formatTime(holdUntil));
            statement.setInt(3, reservationId);
            statement.setString(4, BookReservation.STATUS_APPROVED);
            statement.executeUpdate();
        }
    }

    private void markReservationExpired(Connection connection, int reservationId)
            throws SQLException {
        String sql = "UPDATE [tblReservation] SET [status] = ? "
                + "WHERE [reservationId] = ? AND [status] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, BookReservation.STATUS_EXPIRED);
            statement.setInt(2, reservationId);
            statement.setString(3, BookReservation.STATUS_HELD);
            statement.executeUpdate();
        }
    }

    private void markReservationPickedUp(Connection connection, int reservationId, Date pickupTime)
            throws SQLException {
        String sql = "UPDATE [tblReservation] SET [status] = ?, [pickupTime] = ? "
                + "WHERE [reservationId] = ? AND [status] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, BookReservation.STATUS_PICKED_UP);
            statement.setString(2, formatTime(pickupTime));
            statement.setInt(3, reservationId);
            statement.setString(4, BookReservation.STATUS_HELD);
            statement.executeUpdate();
        }
    }

    private void recordDefault(Connection connection, String userId, Date now) throws SQLException {
        ensurePatronRow(connection, userId);
        int next = findPatronDefaultCountOn(connection, userId) + 1;
        String suspend = next >= MAX_DEFAULTS
                ? formatTime(new Date(now.getTime() + SUSPEND_DAYS * DAY_MILLIS))
                : null;
        String sql = "UPDATE [tblReservationPatron] SET [defaultCount] = ?, [suspendUntil] = ? "
                + "WHERE [userId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, next);
            if (suspend == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, suspend);
            }
            statement.setString(3, userId);
            statement.executeUpdate();
        }
    }

    private void ensurePatronRow(Connection connection, String userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblReservationPatron] WHERE [userId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next() && result.getInt(1) > 0) {
                    return;
                }
            }
        }
        String insert = "INSERT INTO [tblReservationPatron] ([userId], [defaultCount], "
                + "[suspendUntil]) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, userId);
            statement.setInt(2, 0);
            statement.setNull(3, Types.VARCHAR);
            statement.executeUpdate();
        }
    }

    private int findPatronDefaultCountOn(Connection connection, String userId) throws SQLException {
        String sql = "SELECT [defaultCount] FROM [tblReservationPatron] WHERE [userId] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private int lookupLatestReservationId(Connection connection, String userId, String isbn)
            throws SQLException {
        String sql = "SELECT MAX([reservationId]) FROM [tblReservation] "
                + "WHERE [userId] = ? AND [isbn] = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, isbn);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Inserted reservation was not found");
                }
                int id = result.getInt(1);
                if (result.wasNull()) {
                    throw new SQLException("Inserted reservation was not found");
                }
                return id;
            }
        }
    }

    private String formatTime(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat(TIME_PATTERN).format(date);
    }

    private Date parseTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat(TIME_PATTERN).parse(value.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    private String toLikePattern(String keyword) {
        if (isBlank(keyword)) {
            return "%";
        }
        String escaped = keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
