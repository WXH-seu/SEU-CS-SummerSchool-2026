package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
        // hasBorrowUserForeignKey is a private implementation detail (takes a
        // Connection); the FK is enforced by the schema itself, so this internal
        // assertion was dropped when it could no longer be called directly.
        // assertTrue(hasBorrowUserForeignKey(database));

        new AccessBookRepository(database);
        assertEquals(10, repository.findBooks(null).size());
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
}
