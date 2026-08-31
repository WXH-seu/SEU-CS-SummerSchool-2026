package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.dto.BookDto;
import edu.seu.vcampus.common.dto.BookQueryRequest;
import edu.seu.vcampus.common.dto.BookSummary;
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
}
