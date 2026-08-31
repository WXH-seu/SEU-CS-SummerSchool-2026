package edu.seu.vcampus.client.service;

import edu.seu.vcampus.common.enums.ResponseCode;

/** Business failure returned by the server and surfaced to the Swing UI. */
public final class ClientServiceException extends Exception {
    private static final long serialVersionUID = 1L;

    private final ResponseCode code;

    public ClientServiceException(ResponseCode code, String message) {
        super(message);
        this.code = code;
    }

    public ResponseCode getCode() {
        return code;
    }
}
