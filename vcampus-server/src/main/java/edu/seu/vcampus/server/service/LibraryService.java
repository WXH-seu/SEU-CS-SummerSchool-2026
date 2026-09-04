package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.BookDto;
import edu.seu.vcampus.common.dto.BookQueryRequest;
import edu.seu.vcampus.common.dto.BookSummary;
import edu.seu.vcampus.common.dto.BorrowRecordDto;
import edu.seu.vcampus.common.dto.BorrowRequest;
import edu.seu.vcampus.common.dto.ReturnRequest;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.server.dao.Book;
import edu.seu.vcampus.server.dao.BookCopy;
import edu.seu.vcampus.server.dao.BookRepository;
import edu.seu.vcampus.server.dao.BorrowRecord;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Business rules and permission checks for library queries, borrowing and
 * catalog maintenance. The caller ({@code RequestDispatcher}) resolves the
 * session into a normalized {@link SubSystemRole} for the library, so this
 * class authorizes by that role instead of inspecting the raw account.
 */
public final class LibraryService {
    private static final int MIN_COPIES = 1;
    private static final int MAX_COPIES = 100;
    private static final int ISBN_MAX = 32;
    private static final int TITLE_MAX = 128;
    private static final int AUTHOR_MAX = 64;
    private static final int PUBLISHER_MAX = 64;
    private static final int CATEGORY_MAX = 32;
    private static final long LOAN_PERIOD_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final BookRepository repository;

    public LibraryService(BookRepository repository) {
        this.repository = repository;
    }

    public ArrayList<BookSummary> queryBooks(String actorUserId, SubSystemRole actorRole,
                                             BookQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        String keyword = query == null ? null : query.getKeyword();
        boolean includeInactive = query != null && query.isIncludeInactive()
                && actorRole == SubSystemRole.ADMIN;
        List<Book> books = repository.findBooks(keyword, includeInactive);
        ArrayList<BookSummary> result = new ArrayList<BookSummary>();
        for (Book book : books) {
            result.add(toSummary(book));
        }
        return result;
    }

    public BookDto saveBook(String actorUserId, SubSystemRole actorRole, BookDto book)
            throws SQLException, BusinessException {
        requireAdmin(actorRole);
        BookDto normalized = validateBook(book);
        int borrowed = repository.countBorrowedCopies(normalized.getIsbn());
        if (normalized.getTotalCopies() < borrowed) {
            throw new BusinessException(ResponseCode.CONFLICT,
                    "馆藏册数不能少于当前在借数量（" + borrowed + "）");
        }
        int removable = repository.countRemovableCopies(normalized.getIsbn());
        int currentTotal = repository.findCopiesByIsbn(normalized.getIsbn()).size();
        int minimumKeep = currentTotal - removable;
        if (normalized.getTotalCopies() < minimumKeep) {
            throw new BusinessException(ResponseCode.CONFLICT,
                    "部分副本已产生借阅记录，无法减少到该馆藏数量");
        }
        repository.saveBook(toBook(normalized), normalized.getTotalCopies());
        return toDto(repository.findByIsbn(normalized.getIsbn()));
    }

    public void deleteBook(String actorUserId, SubSystemRole actorRole, String isbn)
            throws SQLException, BusinessException {
        requireAdmin(actorRole);
        String normalizedIsbn = requireIsbn(isbn);
        if (repository.findByIsbn(normalizedIsbn) == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "图书不存在");
        }
        if (repository.countBorrowedCopies(normalizedIsbn) > 0) {
            throw new BusinessException(ResponseCode.CONFLICT, "仍有在借副本，请先下架");
        }
        if (repository.countBorrowRecordsByIsbn(normalizedIsbn) > 0) {
            throw new BusinessException(ResponseCode.CONFLICT, "存在借阅记录，请先下架");
        }
        if (!repository.deleteBook(normalizedIsbn)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "图书不存在");
        }
    }

    public ArrayList<BorrowRecordDto> queryBorrows(String actorUserId, SubSystemRole actorRole)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        if (actorRole == SubSystemRole.ADMIN) {
            return new ArrayList<BorrowRecordDto>();
        }
        List<BorrowRecord> records = repository.findBorrowRecordsByUser(actorUserId.trim());
        ArrayList<BorrowRecordDto> result = new ArrayList<BorrowRecordDto>();
        for (BorrowRecord record : records) {
            result.add(toRecordDto(record));
        }
        return result;
    }

    public BorrowRecordDto borrowBook(String actorUserId, SubSystemRole actorRole,
                                      BorrowRequest request)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        requirePatron(actorRole);
        String isbn = requireIsbn(request == null ? null : request.getIsbn());
        Book book = repository.findByIsbn(isbn);
        if (book == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "图书不存在");
        }
        if (!book.isActive()) {
            throw new BusinessException(ResponseCode.CONFLICT, "图书已下架，无法借阅");
        }
        if (repository.hasActiveBorrow(actorUserId.trim(), isbn)) {
            throw new BusinessException(ResponseCode.CONFLICT, "不能重复借阅同一图书");
        }
        Date now = new Date();
        BorrowRecord created = repository.borrowAvailableCopy(
                actorUserId.trim(), isbn, now, new Date(now.getTime() + LOAN_PERIOD_MILLIS));
        if (created == null) {
            throw new BusinessException(ResponseCode.CONFLICT, "暂无可借副本");
        }
        return toRecordDto(created);
    }

    public void returnBook(String actorUserId, SubSystemRole actorRole, ReturnRequest request)
            throws SQLException, BusinessException {
        requireActor(actorUserId, actorRole);
        requirePatron(actorRole);
        if (request == null || request.getRecordId() <= 0) {
            throw invalid("借阅记录编号无效");
        }
        BorrowRecord record = repository.findBorrowRecordById(request.getRecordId());
        if (record == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "借阅记录不存在");
        }
        if (!actorUserId.trim().equals(record.getUserId())) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "只能归还本人的借阅记录");
        }
        if (record.isReturned()) {
            throw new BusinessException(ResponseCode.CONFLICT, "该记录已归还");
        }
        if (!repository.returnBorrow(record.getRecordId(), new Date())) {
            throw new BusinessException(ResponseCode.CONFLICT, "该记录已归还");
        }
    }

    private BookDto validateBook(BookDto book) throws BusinessException {
        if (book == null) {
            throw invalid("图书资料不能为空");
        }
        String isbn = requireLength(book.getIsbn(), "ISBN", ISBN_MAX, true);
        String title = requireLength(book.getTitle(), "书名", TITLE_MAX, true);
        String author = requireLength(book.getAuthor(), "作者", AUTHOR_MAX, true);
        String publisher = requireLength(book.getPublisher(), "出版社", PUBLISHER_MAX, false);
        String category = requireLength(book.getCategory(), "分类", CATEGORY_MAX, false);
        int copies = book.getTotalCopies();
        if (copies < MIN_COPIES || copies > MAX_COPIES) {
            throw invalid("馆藏册数必须在 " + MIN_COPIES + " 到 " + MAX_COPIES + " 之间");
        }
        return new BookDto(isbn, title, author, publisher, category, copies, book.isActive());
    }

    private String requireIsbn(String isbn) throws BusinessException {
        return requireLength(isbn, "ISBN", ISBN_MAX, true);
    }

    private String requireLength(String value, String field, int max, boolean required)
            throws BusinessException {
        String trimmed = value == null ? "" : value.trim();
        if (required && trimmed.isEmpty()) {
            throw invalid(field + "不能为空");
        }
        if (trimmed.length() > max) {
            throw invalid(field + "长度不能超过 " + max + " 个字符");
        }
        return trimmed;
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
                copies.size(),
                book.isActive());
    }

    private BookDto toDto(Book book) throws SQLException {
        int total = repository.findCopiesByIsbn(book.getIsbn()).size();
        return new BookDto(book.getIsbn(), book.getTitle(), book.getAuthor(),
                book.getPublisher(), book.getCategory(), total, book.isActive());
    }

    private BorrowRecordDto toRecordDto(BorrowRecord record) {
        return new BorrowRecordDto(
                record.getRecordId(),
                record.getIsbn(),
                record.getTitle(),
                record.getAuthor(),
                formatTime(record.getBorrowTime()),
                formatTime(record.getDueTime()),
                formatTime(record.getReturnTime()),
                record.isOverdue(),
                record.isReturned());
    }

    private String formatTime(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat(TIME_PATTERN).format(date);
    }

    private Book toBook(BookDto book) {
        return new Book(book.getIsbn(), book.getTitle(), book.getAuthor(),
                book.getPublisher(), book.getCategory(), book.isActive());
    }

    private void requireActor(String actorUserId, SubSystemRole actorRole)
            throws BusinessException {
        if (actorUserId == null || actorUserId.trim().isEmpty() || actorRole == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "请先登录");
        }
    }

    private void requireAdmin(SubSystemRole actorRole) throws BusinessException {
        if (actorRole != SubSystemRole.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅管理员可以维护图书");
        }
    }

    private void requirePatron(SubSystemRole actorRole) throws BusinessException {
        if (actorRole == SubSystemRole.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "管理员不能借阅或归还图书");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ResponseCode.INVALID_REQUEST, message);
    }
}
