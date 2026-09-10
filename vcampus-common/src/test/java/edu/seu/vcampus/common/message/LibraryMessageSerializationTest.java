package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.dto.BookDto;
import edu.seu.vcampus.common.dto.BookQueryRequest;
import edu.seu.vcampus.common.dto.BookSummary;
import edu.seu.vcampus.common.dto.BorrowRecordDto;
import edu.seu.vcampus.common.dto.BorrowRequest;
import edu.seu.vcampus.common.dto.ReserveDto;
import edu.seu.vcampus.common.dto.ReserveIdRequest;
import edu.seu.vcampus.common.dto.ReserveReviewRequest;
import edu.seu.vcampus.common.dto.ReturnRequest;
import edu.seu.vcampus.common.dto.WishDto;
import edu.seu.vcampus.common.dto.WishReviewRequest;
import edu.seu.vcampus.common.dto.WishSubmitRequest;
import edu.seu.vcampus.common.enums.Operation;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/** Ensures library DTOs remain compatible with the object-stream protocol. */
public class LibraryMessageSerializationTest {
    @Test
    public void serializesBookQueryRequest() throws Exception {
        RequestMessage<BookQueryRequest> request = new RequestMessage<BookQueryRequest>(
                Operation.LIBRARY_BOOK_QUERY, "session", new BookQueryRequest("红楼梦"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();

        assertEquals(Operation.LIBRARY_BOOK_QUERY, restored.getOperation());
        BookQueryRequest query = (BookQueryRequest) restored.getBody();
        assertEquals("红楼梦", query.getKeyword());
        assertEquals(false, query.isIncludeInactive());
    }

    @Test
    public void serializesBookSaveRequest() throws Exception {
        BookDto book = new BookDto("9787300000001", "测试图书", "测试作者",
                "测试出版社", "计算机", 2, true);
        RequestMessage<BookDto> request = new RequestMessage<BookDto>(
                Operation.LIBRARY_BOOK_SAVE, "session", book);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        BookDto restoredBook = (BookDto) restored.getBody();

        assertEquals(Operation.LIBRARY_BOOK_SAVE, restored.getOperation());
        assertEquals("9787300000001", restoredBook.getIsbn());
        assertEquals(2, restoredBook.getTotalCopies());
        assertEquals(true, restoredBook.isActive());
    }

    @Test
    public void serializesBookSummaryList() throws Exception {
        ArrayList<BookSummary> books = new ArrayList<BookSummary>();
        books.add(new BookSummary("9787020008735", "红楼梦", "曹雪芹",
                "人民文学出版社", "文学", 1, 2));
        ResponseMessage<ArrayList<BookSummary>> response =
                ResponseMessage.success("req-1", "操作成功", books);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(response);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        ResponseMessage<?> restored = (ResponseMessage<?>) input.readObject();
        @SuppressWarnings("unchecked")
        ArrayList<BookSummary> restoredBooks = (ArrayList<BookSummary>) restored.getBody();

        assertEquals(1, restoredBooks.size());
        assertEquals("9787020008735", restoredBooks.get(0).getIsbn());
        assertEquals(1, restoredBooks.get(0).getAvailableCopies());
        assertEquals(2, restoredBooks.get(0).getTotalCopies());
        assertEquals(true, restoredBooks.get(0).isActive());
    }

    @Test
    public void serializesBorrowRequestAndRecord() throws Exception {
        RequestMessage<BorrowRequest> request = new RequestMessage<BorrowRequest>(
                Operation.LIBRARY_BORROW, "session", new BorrowRequest("9787040202489"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        assertEquals(Operation.LIBRARY_BORROW, restored.getOperation());
        assertEquals("9787040202489", ((BorrowRequest) restored.getBody()).getIsbn());

        BorrowRecordDto record = new BorrowRecordDto(3, "9787040202489", "线性代数",
                "同济大学数学系", "student", "演示学生",
                "2026-09-04 12:00:00", "2026-09-18 12:00:00",
                "", false, false, 0);
        ResponseMessage<BorrowRecordDto> response =
                ResponseMessage.success("req-2", "操作成功", record);
        bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(response);
        input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        ResponseMessage<?> restoredResponse = (ResponseMessage<?>) input.readObject();
        BorrowRecordDto restoredRecord = (BorrowRecordDto) restoredResponse.getBody();
        assertEquals(3, restoredRecord.getRecordId());
        assertEquals("student", restoredRecord.getUserId());
        assertEquals("演示学生（student）", restoredRecord.getBorrowerLabel());
        assertEquals("在借", restoredRecord.getStatusName());
        assertEquals(false, restoredRecord.isReturned());
        assertEquals(0, restoredRecord.getRenewCount());
    }

    @Test
    public void serializesRenewRequest() throws Exception {
        RequestMessage<ReturnRequest> request = new RequestMessage<ReturnRequest>(
                Operation.LIBRARY_RENEW, "session", new ReturnRequest(8));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);
        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        assertEquals(Operation.LIBRARY_RENEW, restored.getOperation());
        assertEquals(8, ((ReturnRequest) restored.getBody()).getRecordId());
    }

    @Test
    public void serializesReturnRequest() throws Exception {
        RequestMessage<ReturnRequest> request = new RequestMessage<ReturnRequest>(
                Operation.LIBRARY_RETURN, "session", new ReturnRequest(8));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);
        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        assertEquals(Operation.LIBRARY_RETURN, restored.getOperation());
        assertEquals(8, ((ReturnRequest) restored.getBody()).getRecordId());
    }

    @Test
    public void serializesWishSubmitAndRecord() throws Exception {
        RequestMessage<WishSubmitRequest> request = new RequestMessage<WishSubmitRequest>(
                Operation.LIBRARY_WISH_SUBMIT, "session",
                new WishSubmitRequest("三体", "刘慈欣"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);
        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        assertEquals(Operation.LIBRARY_WISH_SUBMIT, restored.getOperation());
        WishSubmitRequest body = (WishSubmitRequest) restored.getBody();
        assertEquals("三体", body.getTitle());
        assertEquals("刘慈欣", body.getAuthor());

        WishDto wish = new WishDto(4, "teacher", "演示教师", "三体", "刘慈欣",
                WishDto.STATUS_PENDING, "2026-09-09 09:00:00", "", "");
        ResponseMessage<WishDto> response = ResponseMessage.success("req-3", "操作成功", wish);
        bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(response);
        input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        ResponseMessage<?> restoredResponse = (ResponseMessage<?>) input.readObject();
        WishDto restoredWish = (WishDto) restoredResponse.getBody();
        assertEquals(4, restoredWish.getWishId());
        assertEquals("演示教师（teacher）", restoredWish.getSubmitterLabel());
        assertEquals("待审", restoredWish.getStatusName());
        assertEquals(true, restoredWish.isPending());
    }

    @Test
    public void serializesWishReviewRequest() throws Exception {
        BookDto book = new BookDto("9787536692930", "三体", "刘慈欣",
                "重庆出版社", "科幻", 2, true);
        RequestMessage<WishReviewRequest> request = new RequestMessage<WishReviewRequest>(
                Operation.LIBRARY_WISH_REVIEW, "session",
                new WishReviewRequest(4, true, book));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);
        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        assertEquals(Operation.LIBRARY_WISH_REVIEW, restored.getOperation());
        WishReviewRequest body = (WishReviewRequest) restored.getBody();
        assertEquals(4, body.getWishId());
        assertEquals(true, body.isApproved());
        assertEquals("9787536692930", body.getBook().getIsbn());
    }

    @Test
    public void serializesReserveApplyAndRecord() throws Exception {
        RequestMessage<BorrowRequest> request = new RequestMessage<BorrowRequest>(
                Operation.LIBRARY_RESERVE_APPLY, "session", new BorrowRequest("9787020024759"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);
        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        assertEquals(Operation.LIBRARY_RESERVE_APPLY, restored.getOperation());
        assertEquals("9787020024759", ((BorrowRequest) restored.getBody()).getIsbn());

        ReserveDto reserve = new ReserveDto(6, "student", "演示学生", "9787020024759",
                "围城", "钱钟书", 12, ReserveDto.STATUS_HELD,
                "2026-09-09 09:00:00", "2026-09-09 10:00:00",
                "2026-09-16 10:00:00", "", 0, "");
        ResponseMessage<ReserveDto> response =
                ResponseMessage.success("req-4", "操作成功", reserve);
        bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(response);
        input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        ResponseMessage<?> restoredResponse = (ResponseMessage<?>) input.readObject();
        ReserveDto restoredReserve = (ReserveDto) restoredResponse.getBody();
        assertEquals(6, restoredReserve.getReservationId());
        assertEquals("演示学生（student）", restoredReserve.getApplicantLabel());
        assertEquals("待取书", restoredReserve.getStatusName());
        assertEquals(true, restoredReserve.isHeld());
        assertEquals(true, restoredReserve.isInProgress());
    }

    @Test
    public void serializesReserveReviewAndPickupRequests() throws Exception {
        RequestMessage<ReserveReviewRequest> review = new RequestMessage<ReserveReviewRequest>(
                Operation.LIBRARY_RESERVE_REVIEW, "session",
                new ReserveReviewRequest(6, true));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(review);
        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        assertEquals(Operation.LIBRARY_RESERVE_REVIEW, restored.getOperation());
        ReserveReviewRequest reviewBody = (ReserveReviewRequest) restored.getBody();
        assertEquals(6, reviewBody.getReservationId());
        assertEquals(true, reviewBody.isApproved());

        RequestMessage<ReserveIdRequest> pickup = new RequestMessage<ReserveIdRequest>(
                Operation.LIBRARY_RESERVE_PICKUP, "session", new ReserveIdRequest(6));
        bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(pickup);
        input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        restored = (RequestMessage<?>) input.readObject();
        assertEquals(Operation.LIBRARY_RESERVE_PICKUP, restored.getOperation());
        assertEquals(6, ((ReserveIdRequest) restored.getBody()).getReservationId());
    }
}
