package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.BookQueryRequest;
import edu.seu.vcampus.common.dto.BookSummary;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.server.dao.AccessBookRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises library query rules against the real Access schema and demo data. */
public class LibraryServiceIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private LibraryService service;
    @Before
    public void setUp() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        AccessUserRepository users = new AccessUserRepository(database, new PasswordHasher());
        AccessBookRepository books = new AccessBookRepository(database);
        service = new LibraryService(books);
    }

    @Test
    public void studentCanSearchDemoBooksAndSeeInventory() throws Exception {
        List<BookSummary> allBooks = service.queryBooks(
                "student", SubSystemRole.STUDENT, null);
        assertEquals(10, allBooks.size());

        List<BookSummary> matched = service.queryBooks("student", SubSystemRole.STUDENT,
                new BookQueryRequest("红楼梦"));
        assertEquals(1, matched.size());
        assertEquals("9787020008735", matched.get(0).getIsbn());
        assertEquals(1, matched.get(0).getAvailableCopies());
        assertEquals(2, matched.get(0).getTotalCopies());

        BookSummary math = findByIsbn(allBooks, "9787040396621");
        assertEquals(2, math.getAvailableCopies());
        assertEquals(3, math.getTotalCopies());
    }

    @Test
    public void rejectsQueryWithoutLogin() throws Exception {
        try {
            service.queryBooks(null, null, new BookQueryRequest("Java"));
            fail("Anonymous query should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.UNAUTHORIZED, expected.getResponseCode());
        }
    }

    @Test
    public void emptyKeywordReturnsEveryActiveTitle() throws Exception {
        List<BookSummary> books = service.queryBooks(
                "student", SubSystemRole.STUDENT, new BookQueryRequest(""));
        assertEquals(10, books.size());
        assertTrue(findByIsbn(books, "9787020024759").getAvailableCopies() >= 1);
    }

    private BookSummary findByIsbn(List<BookSummary> books, String isbn) {
        for (BookSummary book : books) {
            if (isbn.equals(book.getIsbn())) {
                return book;
            }
        }
        fail("Missing ISBN " + isbn);
        return null;
    }
}
