package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.enums.ResponseCode;

import java.io.Serializable;

/** Serializable response returned for one client request. */
public final class ResponseMessage<T extends Serializable> implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String requestId;
    private final ResponseCode code;
    private final String message;
    private final T body;

    private ResponseMessage(String requestId, ResponseCode code, String message, T body) {
        this.requestId = requestId;
        this.code = code;
        this.message = message;
        this.body = body;
    }

    public static <T extends Serializable> ResponseMessage<T> success(
            String requestId, String message, T body) {
        return new ResponseMessage<T>(requestId, ResponseCode.SUCCESS, message, body);
    }

    public static ResponseMessage<Serializable> failure(
            String requestId, ResponseCode code, String message) {
        return new ResponseMessage<Serializable>(requestId, code, message, null);
    }

    public String getRequestId() {
        return requestId;
    }

    public ResponseCode getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getBody() {
        return body;
    }

    public boolean isSuccess() {
        return code == ResponseCode.SUCCESS;
    }
}
