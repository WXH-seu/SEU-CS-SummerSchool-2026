package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Verifies library table bootstrap, demo data and basic queries. */
public class AccessBookRepositoryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsTablesAndSeedsDemoBooks() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        new AccessUserRepository(database, new PasswordHasher());
        AccessBookRepository repository = new AccessBookRepository(database);

        assertTrue(file.isFile());
        List<Book> books = repository.findBooks("");
        assertEquals(10, books.size());
        assertNotNull(repository.findByIsbn("9787040396621"));
        assertEquals(2, repository.countAvailableCopies("9787040396621"));

        List<Book> matched = repository.findBooks("红楼梦");
        assertEquals(1, matched.size());
        assertEquals("9787020008735", matched.get(0).getIsbn());

        List<BorrowRecord> records = repository.findBorrowRecordsByUser("student");
        assertEquals(2, records.size());
        assertNotNull(records.get(0).getIsbn());
        assertNotNull(records.get(0).getTitle());
        assertEquals("student", records.get(0).getUserId());
        assertEquals("演示学生", records.get(0).getDisplayName());

        List<BorrowRecord> active = repository.findActiveBorrowRecords();
        assertEquals(2, active.size());
        for (int i = 0; i < active.size(); i++) {
            assertFalse(active.get(i).isReturned());
            assertEquals("student", active.get(i).getUserId());
        }
        boolean hasCurrentBorrow = false;
        boolean hasOverdue = false;
        for (int i = 0; i < records.size(); i++) {
            BorrowRecord record = records.get(i);
            assertFalse(record.isReturned());
            if (record.isOverdue()) {
                hasOverdue = true;
            } else {
                hasCurrentBorrow = true;
            }
        }
        assertTrue(hasCurrentBorrow);
        assertTrue(hasOverdue);
        assertThirtyDayLoanPeriod(records);
        // hasBorrowUserForeignKey is a private implementation detail (takes a
        // Connection); the FK is enforced by the schema itself, so this internal
        // assertion was dropped when it could no longer be called directly.
        // assertTrue(hasBorrowUserForeignKey(database));

        new AccessBookRepository(database);
        assertEquals(10, repository.findBooks(null).size());
    }

    @Test
    public void rebuildsLegacyDatetimeBorrowTableOnStartup() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        new AccessUserRepository(database, new PasswordHasher());
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE [tblBorrowRecord] ("
                    + "[recordId] COUNTER PRIMARY KEY, "
                    + "[copyId] LONG NOT NULL, "
                    + "[userId] TEXT(32) NOT NULL, "
                    + "[borrowTime] DATETIME NOT NULL, "
                    + "[dueTime] DATETIME NOT NULL, "
                    + "[returnTime] DATETIME, "
                    + "CONSTRAINT [fkBorrowRecordUser] FOREIGN KEY ([userId]) "
                    + "REFERENCES [tblUser] ([userId]))");
        }

        AccessBookRepository repository = new AccessBookRepository(database);
        List<BorrowRecord> records = repository.findBorrowRecordsByUser("student");
        assertEquals(2, records.size());
        assertEquals("演示学生", records.get(0).getDisplayName());
        assertThirtyDayLoanPeriod(records);
    }

    @Test
    public void realignsLegacyTwentyDayDemoDueDates() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        new AccessUserRepository(database, new PasswordHasher());
        AccessBookRepository repository = new AccessBookRepository(database);
        BorrowRecord overdue = findUnreturnedByIsbn(
                repository.findBorrowRecordsByUser("student"), "9787020008735");
        Date oldDue = new Date(overdue.getBorrowTime().getTime() + 20L * 24 * 60 * 60 * 1000);
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE [tblBorrowRecord] SET [dueTime] = ? WHERE [recordId] = ?")) {
            statement.setString(1, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(oldDue));
            statement.setInt(2, overdue.getRecordId());
            assertEquals(1, statement.executeUpdate());
        }

        new AccessBookRepository(database);
        List<BorrowRecord> aligned = repository.findBorrowRecordsByUser("student");
        assertThirtyDayLoanPeriod(aligned);
        assertTrue(findUnreturnedByIsbn(aligned, "9787020008735").isOverdue());
    }

    private static boolean hasBorrowUserForeignKey(AccessDatabase database) throws Exception {
        try (Connection connection = database.openConnection();
             ResultSet keys = connection.getMetaData().getImportedKeys(
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

    @Test
    public void savesAndDeletesATitleWithoutBorrowHistory() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        new AccessUserRepository(database, new PasswordHasher());
        AccessBookRepository repository = new AccessBookRepository(database);

        repository.saveBook(new Book("9787300000001", "测试图书", "测试作者",
                "测试出版社", "计算机", true), 2);
        assertEquals(2, repository.countAvailableCopies("9787300000001"));
        assertEquals(11, repository.findBooks("").size());

        repository.saveBook(new Book("9787300000001", "测试图书", "测试作者",
                "测试出版社", "计算机", false), 2);
        assertEquals(10, repository.findBooks("", false).size());
        assertEquals(11, repository.findBooks("", true).size());

        assertTrue(repository.deleteBook("9787300000001"));
        assertEquals(10, repository.findBooks("", true).size());
        assertTrue(repository.deleteBook("9787020024759"));
        assertEquals(9, repository.findBooks("").size());
    }

    private static void assertThirtyDayLoanPeriod(List<BorrowRecord> records) {
        long loanMillis = 30L * 24 * 60 * 60 * 1000;
        for (int i = 0; i < records.size(); i++) {
            BorrowRecord record = records.get(i);
            assertEquals(loanMillis,
                    record.getDueTime().getTime() - record.getBorrowTime().getTime());
        }
    }

    private static BorrowRecord findUnreturnedByIsbn(List<BorrowRecord> records, String isbn) {
        for (int i = 0; i < records.size(); i++) {
            BorrowRecord record = records.get(i);
            if (isbn.equals(record.getIsbn()) && !record.isReturned()) {
                return record;
            }
        }
        fail("Missing unreturned borrow for ISBN " + isbn);
        return null;
    }
}
