package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.server.database.AccessDatabase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
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

        new AccessBookRepository(database);
        assertEquals(10, repository.findBooks(null).size());
    }
}
