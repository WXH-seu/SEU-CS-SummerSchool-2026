package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.BookDto;
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

/** Exercises library query and catalog-maintenance rules against Access demo data. */
public class LibraryServiceIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private LibraryService service;
    private UserAccount studentAccount;
    private UserAccount teacherAccount;
    private UserAccount adminAccount;

    @Before
    public void setUp() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        AccessUserRepository users = new AccessUserRepository(database, new PasswordHasher());
        AccessBookRepository books = new AccessBookRepository(database);
        service = new LibraryService(books);
        studentAccount = users.findById("student");
        teacherAccount = users.findById("teacher");
        adminAccount = users.findById("admin");
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

    @Test
    public void adminCanCreateUpdateDeactivateAndDeleteBooks() throws Exception {
        BookDto created = service.saveBook(adminAccount, new BookDto(
                "9787300000001", "测试图书", "测试作者", "测试出版社", "计算机", 2, true));
        assertEquals(2, created.getTotalCopies());
        assertEquals(11, service.queryBooks(studentAccount, null).size());

        BookDto updated = service.saveBook(adminAccount, new BookDto(
                "9787300000001", "测试图书（修订）", "测试作者", "测试出版社", "计算机", 3, true));
        assertEquals("测试图书（修订）", updated.getTitle());
        assertEquals(3, updated.getTotalCopies());

        service.saveBook(adminAccount, new BookDto(
                "9787300000001", "测试图书（修订）", "测试作者", "测试出版社", "计算机", 3, false));
        assertEquals(10, service.queryBooks(studentAccount, null).size());
        assertEquals(11, service.queryBooks(adminAccount, new BookQueryRequest("", true)).size());
        assertEquals(10, service.queryBooks(studentAccount, new BookQueryRequest("", true)).size());

        service.deleteBook(adminAccount, "9787300000001");
        assertEquals(10, service.queryBooks(adminAccount, new BookQueryRequest("", true)).size());
    }

    @Test
    public void studentAndTeacherCannotMaintainCatalog() throws Exception {
        BookDto book = new BookDto("9787300000002", "越权图书", "作者", "出版社", "教材", 1, true);
        try {
            service.saveBook(studentAccount, book);
            fail("Student should not maintain books");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
        try {
            service.saveBook(teacherAccount, book);
            fail("Teacher should not maintain books");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
        try {
            service.deleteBook(studentAccount, "9787020024759");
            fail("Student should not delete books");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
    }

    @Test
    public void cannotDeleteOrShrinkBooksWithBorrowHistory() throws Exception {
        try {
            service.deleteBook(adminAccount, "9787020008735");
            fail("Book with borrow records should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }

        try {
            service.saveBook(adminAccount, new BookDto(
                    "9787020008735", "红楼梦", "曹雪芹", "人民文学出版社", "文学", 0, true));
            fail("Copy count below minimum should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.INVALID_REQUEST, expected.getResponseCode());
        }

        BookDto reduced = service.saveBook(adminAccount, new BookDto(
                "9787111544937", "计算机网络（第7版）", "谢希仁", "电子工业出版社", "计算机", 1, true));
        assertEquals(1, reduced.getTotalCopies());
        service.deleteBook(adminAccount, "9787020024759");
        assertEquals(9, service.queryBooks(studentAccount, null).size());
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
