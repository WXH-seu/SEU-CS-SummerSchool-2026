package edu.seu.vcampus.client.service;

import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.common.dto.BookDto;
import edu.seu.vcampus.common.dto.BookQueryRequest;
import edu.seu.vcampus.common.dto.BookSummary;
import edu.seu.vcampus.common.dto.EntityIdRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Converts library UI actions into object-stream requests. */
public final class LibraryClientService {
    private final ClientConnection connection;
    private final String sessionToken;

    public LibraryClientService(ClientConnection connection, String sessionToken) {
        this.connection = connection;
        this.sessionToken = sessionToken;
    }

    public List<BookSummary> queryBooks(String keyword) throws IOException {
        return queryBooks(keyword, false);
    }

    public List<BookSummary> queryBooks(String keyword, boolean includeInactive)
            throws IOException {
        return listRequest(Operation.LIBRARY_BOOK_QUERY,
                new BookQueryRequest(keyword, includeInactive), BookSummary.class);
    }

    public void saveBook(BookDto book) throws IOException {
        request(Operation.LIBRARY_BOOK_SAVE, book);
    }

    public void deleteBook(String isbn) throws IOException {
        request(Operation.LIBRARY_BOOK_DELETE, new EntityIdRequest(isbn));
    }

    private <T> List<T> listRequest(Operation operation, Serializable body, Class<T> type)
            throws IOException {
        Object responseBody = request(operation, body).getBody();
        if (!(responseBody instanceof List)) {
            throw new IOException("服务器返回的数据格式不正确");
        }
        List<?> raw = (List<?>) responseBody;
        List<T> result = new ArrayList<T>();
        for (Object item : raw) {
            if (!type.isInstance(item)) {
                throw new IOException("服务器返回的数据类型不正确");
            }
            result.add(type.cast(item));
        }
        return result;
    }

    private ResponseMessage<?> request(Operation operation, Serializable body) throws IOException {
        ResponseMessage<?> response = connection.request(
                new RequestMessage<Serializable>(operation, sessionToken, body));
        if (!response.isSuccess()) {
            throw new IOException(response.getMessage());
        }
        return response;
    }
}
