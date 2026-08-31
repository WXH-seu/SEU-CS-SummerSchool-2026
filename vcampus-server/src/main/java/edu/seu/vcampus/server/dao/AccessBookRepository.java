package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.server.database.AccessDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
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

    private final AccessDatabase database;

    public AccessBookRepository(AccessDatabase database) throws SQLException {
        this.database = database;
        initializeDatabase();
    }

    @Override
    public List<Book> findBooks(String keyword) throws SQLException {
        String pattern = toLikePattern(keyword);
        String sql = "SELECT [isbn], [title], [author], [publisher], [category], [active] "
                + "FROM [tblBook] WHERE [active] = true "
                + "AND ([isbn] LIKE ? OR [title] LIKE ? OR [author] LIKE ?) "
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
        String sql = "SELECT COUNT(*) FROM [tblBookCopy] "
                + "WHERE [isbn] = ? AND [copyStatus] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            statement.setString(2, BookCopy.STATUS_AVAILABLE);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    @Override
    public List<BorrowRecord> findBorrowRecordsByUser(String userId) throws SQLException {
        String sql = "SELECT [recordId], [copyId], [userId], [borrowTime], [dueTime], "
                + "[returnTime] FROM [tblBorrowRecord] WHERE [userId] = ? "
                + "ORDER BY [borrowTime] DESC";
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
            ensureBorrowUserForeignKey(connection);
            if (countBooks(connection) == 0) {
                insertDemoData(connection);
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
                + "[borrowTime] DATETIME NOT NULL, "
                + "[dueTime] DATETIME NOT NULL, "
                + "[returnTime] DATETIME, "
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

        Timestamp now = new Timestamp(System.currentTimeMillis());
        borrowCopy(connection, MATH_ISBN, DEMO_STUDENT_ID, now,
                new Timestamp(now.getTime() + 14 * DAY_MILLIS));
        borrowCopy(connection, NOVEL_ISBN, DEMO_STUDENT_ID,
                new Timestamp(now.getTime() - 40 * DAY_MILLIS),
                new Timestamp(now.getTime() - 20 * DAY_MILLIS));
    }

    private void insertBookWithCopies(Connection connection, String isbn, String title,
                                      String author, String publisher, String category,
                                      int copyCount) throws SQLException {
        insertBook(connection, isbn, title, author, publisher, category);
        for (int i = 0; i < copyCount; i++) {
            insertCopy(connection, isbn, BookCopy.STATUS_AVAILABLE);
        }
    }

    private void insertBook(Connection connection, String isbn, String title, String author,
                            String publisher, String category) throws SQLException {
        String sql = "INSERT INTO [tblBook] ([isbn], [title], [author], [publisher], "
                + "[category], [active]) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            statement.setString(2, title);
            statement.setString(3, author);
            statement.setString(4, publisher);
            statement.setString(5, category);
            statement.setBoolean(6, true);
            statement.executeUpdate();
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
                            Timestamp borrowTime, Timestamp dueTime) throws SQLException {
        int copyId = findFirstCopyId(connection, isbn, BookCopy.STATUS_AVAILABLE);
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
                    throw new SQLException("No copy found for ISBN " + isbn);
                }
                int copyId = result.getInt(1);
                if (result.wasNull()) {
                    throw new SQLException("No available copy for ISBN " + isbn);
                }
                return copyId;
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

    private void insertBorrowRecord(Connection connection, int copyId, String userId,
                                    Timestamp borrowTime, Timestamp dueTime)
            throws SQLException {
        String sql = "INSERT INTO [tblBorrowRecord] ([copyId], [userId], [borrowTime], "
                + "[dueTime], [returnTime]) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, copyId);
            statement.setString(2, userId);
            statement.setTimestamp(3, borrowTime);
            statement.setTimestamp(4, dueTime);
            statement.setNull(5, Types.TIMESTAMP);
            statement.executeUpdate();
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
        Timestamp borrowTime = result.getTimestamp("borrowTime");
        Timestamp dueTime = result.getTimestamp("dueTime");
        Timestamp returnTime = result.getTimestamp("returnTime");
        return new BorrowRecord(
                result.getInt("recordId"),
                result.getInt("copyId"),
                result.getString("userId"),
                toDate(borrowTime),
                toDate(dueTime),
                toDate(returnTime));
    }

    private Date toDate(Timestamp timestamp) {
        return timestamp == null ? null : new Date(timestamp.getTime());
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
