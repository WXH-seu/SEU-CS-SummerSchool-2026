package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.BookQueryRequest;
import edu.seu.vcampus.common.dto.BookSummary;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.server.dao.Book;
import edu.seu.vcampus.server.dao.BookCopy;
import edu.seu.vcampus.server.dao.BookRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Business rules and permission checks for library book queries. */
public final class LibraryService {
    private final BookRepository repository;

    public LibraryService(BookRepository repository) {
        this.repository = repository;
    }

    public ArrayList<BookSummary> queryBooks(String actorUserId, SubSystemRole actorRole,
                                             BookQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        String keyword = query == null ? null : query.getKeyword();
        List<Book> books = repository.findBooks(keyword);
        ArrayList<BookSummary> result = new ArrayList<BookSummary>();
        for (Book book : books) {
            result.add(toSummary(book));
        }
        return result;
    }

    private BookSummary toSummary(Book book) throws SQLException {
        List<BookCopy> copies = repository.findCopiesByIsbn(book.getIsbn());
        int available = 0;
        for (BookCopy copy : copies) {
            if (copy.isAvailable()) {
                available++;
            }
        }
        return new BookSummary(
                book.getIsbn(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                book.getCategory(),
                available,
                copies.size());
    }

    private void requireActor(String actorUserId, SubSystemRole actorRole)
            throws BusinessException {
        if (actorUserId == null || actorUserId.trim().isEmpty() || actorRole == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "请先登录");
        }
    }
}
