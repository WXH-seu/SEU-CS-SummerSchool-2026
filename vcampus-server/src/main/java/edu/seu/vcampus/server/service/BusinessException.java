package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.enums.ResponseCode;

/** Expected business failure that can be safely returned to a client. */
public final class BusinessException extends Exception {
    private static final long serialVersionUID = 1L;

    private final ResponseCode responseCode;

    public BusinessException(ResponseCode responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }
}
