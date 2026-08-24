package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.enums.Operation;

import java.io.Serializable;
import java.util.UUID;

/** Serializable request sent from a client to the server. */
public final class RequestMessage<T extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String requestId;
    private final Operation operation;
    private final String sessionToken;
    private final T body;

    public RequestMessage(Operation operation, String sessionToken, T body) {
        this(UUID.randomUUID().toString(), operation, sessionToken, body);
    }

    public RequestMessage(String requestId, Operation operation, String sessionToken, T body) {
        if (requestId == null || operation == null) {
            throw new IllegalArgumentException("requestId and operation are required");
        }
        this.requestId = requestId;
        this.operation = operation;
        this.sessionToken = sessionToken;
        this.body = body;
    }

    public String getRequestId() {
        return requestId;
    }

    public Operation getOperation() {
        return operation;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public T getBody() {
        return body;
    }
}
