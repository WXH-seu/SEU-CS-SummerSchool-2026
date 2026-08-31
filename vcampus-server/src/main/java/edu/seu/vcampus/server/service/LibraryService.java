package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.BookQueryRequest;
import edu.seu.vcampus.common.dto.BookSummary;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;
import edu.seu.vcampus.server.dao.Book;
import edu.seu.vcampus.server.dao.BookCopy;
import edu.seu.vcampus.server.dao.BookRepository;
import edu.seu.vcampus.server.dao.UserAccount;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Business rules and permission checks for library book queries. */
public final class LibraryService {
    private final BookRepository repository;

    public LibraryService(BookRepository repository) {
        this.repository = repository;
    }

    public ArrayList<BookSummary> queryBooks(UserAccount actor, BookQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actor);
        // Book lookup is a usage operation, open to every authenticated role
        // (student / teacher / admin). Authorization is based on the normalized
        // three-tier role for the library sub-system, so scoped authority set up
        // by the user module is honoured; admin-only library operations (when
        // added) must check effectiveRole(actor) == ADMIN.
        SubSystemRole effectiveRole = effectiveRole(actor);
        if (effectiveRole == null) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "无法识别的用户角色");
        }
        String keyword = query == null ? null : query.getKeyword();
        List<Book> books = repository.findBooks(keyword);
        ArrayList<BookSummary> result = new ArrayList<BookSummary>();
        for (Book book : books) {
            result.add(toSummary(book));
        }
        return result;
    }

    /** Resolves the three-tier role the actor is granted within the library. */
    private SubSystemRole effectiveRole(UserAccount actor) {
        return SubSystems.effectiveRole(actor.getRole(), actor.getAdminScopes(), SubSystem.LIBRARY);
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

    private void requireActor(UserAccount actor) throws BusinessException {
        if (actor == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "请先登录");
        }
    }
}
