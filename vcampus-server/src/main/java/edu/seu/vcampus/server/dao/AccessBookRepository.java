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
import java.util.Date;
import java.util.List;

/** Access implementation that creates library tables and demo data on first use. */
public final class AccessBookRepository implements BookRepository {
    private static final String BORROW_USER_FOREIGN_KEY = "fkBorrowRecordUser";
    private static final String DEMO_STUDENT_ID = "student";
    private static final String MATH_ISBN = "9787040396621";
    private static final String NOVEL_ISBN = "9787020008735";
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
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
            try (ResultSet result = statement.executeQuery()) {
                List<BorrowRecord> records = new ArrayList<BorrowRecord>();
                while (result.next()) {
                    records.add(mapRecord(result));
                }
                return records;
            }
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
                updateCopyStatus(connection, copyId.intValue(), BookCopy.STATUS_AVAILABLE);
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

    private String borrowSelectSql() {
        return "SELECT r.[recordId], r.[copyId], r.[userId], r.[borrowTime], r.[dueTime], "
                + "r.[returnTime], c.[isbn], b.[title], b.[author] "
                + "FROM [tblBorrowRecord] AS r "
                + "INNER JOIN [tblBookCopy] AS c ON r.[copyId] = c.[copyId] "
                + "INNER JOIN [tblBook] AS b ON c.[isbn] = b.[isbn]";
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
            migrateBorrowTimesToText(connection);
            ensureBorrowUserForeignKey(connection);
            if (countBooks(connection) == 0) {
                insertDemoData(connection);
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
        if (hasBorrowUserForeignKey(connection)) {
            execute(connection, "ALTER TABLE [tblBorrowRecord] DROP CONSTRAINT ["
                    + BORROW_USER_FOREIGN_KEY + "]");
        }
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
                + "CONSTRAINT [" + BORROW_USER_FOREIGN_KEY + "] FOREIGN KEY ([userId]) "
                + "REFERENCES [tblUser] ([userId]))";
        execute(connection, sql);
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

    private void insertDemoBorrows(Connection connection) throws SQLException {
        Date now = new Date();
        borrowCopy(connection, MATH_ISBN, DEMO_STUDENT_ID,
                formatTime(now), formatTime(new Date(now.getTime() + 30 * DAY_MILLIS)));
        borrowCopy(connection, NOVEL_ISBN, DEMO_STUDENT_ID,
                formatTime(new Date(now.getTime() - 40 * DAY_MILLIS)),
                formatTime(new Date(now.getTime() - 20 * DAY_MILLIS)));
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

    private int insertBorrowRecord(Connection connection, int copyId, String userId,
                                   String borrowTime, String dueTime)
            throws SQLException {
        String sql = "INSERT INTO [tblBorrowRecord] ([copyId], [userId], [borrowTime], "
                + "[dueTime], [returnTime]) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, copyId);
            statement.setString(2, userId);
            statement.setString(3, borrowTime);
            statement.setString(4, dueTime);
            statement.setNull(5, Types.VARCHAR);
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
                result.getString("author"));
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
