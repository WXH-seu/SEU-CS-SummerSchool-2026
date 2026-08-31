package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.BookDto;
import edu.seu.vcampus.common.dto.BookQueryRequest;
import edu.seu.vcampus.common.dto.EntityIdRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.service.BusinessException;
import edu.seu.vcampus.server.service.LibraryService;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.EnumSet;

/** Handles protocol operations owned by the library module. */
public final class LibraryRequestHandler {
    private static final EnumSet<Operation> OPERATIONS = EnumSet.of(
            Operation.LIBRARY_BOOK_QUERY, Operation.LIBRARY_BOOK_SAVE,
            Operation.LIBRARY_BOOK_DELETE);

    private final LibraryService service;

    public LibraryRequestHandler(LibraryService service) {
        this.service = service;
    }

    public boolean supports(Operation operation) {
        return OPERATIONS.contains(operation);
    }

    public ResponseMessage<? extends Serializable> handle(
            RequestMessage<?> request, String actorUserId, SubSystemRole actorRole)
            throws SQLException {
        try {
            switch (request.getOperation()) {
                case LIBRARY_BOOK_QUERY:
                    return success(request, service.queryBooks(actor, queryBody(request)));
                case LIBRARY_BOOK_SAVE:
                    return success(request, service.saveBook(actor, body(request, BookDto.class)));
                case LIBRARY_BOOK_DELETE:
                    service.deleteBook(actor, idBody(request));
                    return success(request, "OK");
                default:
                    return ResponseMessage.failure(request.getRequestId(),
                            ResponseCode.NOT_IMPLEMENTED, "不支持的图书馆操作");
            }
        } catch (BusinessException e) {
            return ResponseMessage.failure(request.getRequestId(),
                    e.getResponseCode(), e.getMessage());
        }
    }

    private BookQueryRequest queryBody(RequestMessage<?> request) throws BusinessException {
        if (request.getBody() == null) {
            return null;
        }
        return body(request, BookQueryRequest.class);
    }

    private String idBody(RequestMessage<?> request) throws BusinessException {
        return body(request, EntityIdRequest.class).getEntityId();
    }

    private <T> T body(RequestMessage<?> request, Class<T> type) throws BusinessException {
        if (!type.isInstance(request.getBody())) {
            throw new BusinessException(ResponseCode.INVALID_REQUEST, "请求参数格式错误");
        }
        return type.cast(request.getBody());
    }

    private ResponseMessage<? extends Serializable> success(
            RequestMessage<?> request, Serializable body) {
        return ResponseMessage.success(request.getRequestId(), "操作成功", body);
    }
}
