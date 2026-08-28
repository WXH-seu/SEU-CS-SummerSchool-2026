package edu.seu.vcampus.common.message;

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
        assertEquals("红楼梦", ((BookQueryRequest) restored.getBody()).getKeyword());
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
    }
}
