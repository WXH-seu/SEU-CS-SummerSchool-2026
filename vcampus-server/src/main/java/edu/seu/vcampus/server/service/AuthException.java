package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.enums.ResponseCode;

/**
 * Expected business failure raised by the authentication service. The dispatcher
 * translates the embedded {@link ResponseCode} into a protocol response without
 * logging sensitive details.
 */
public final class AuthException extends Exception {
    private static final long serialVersionUID = 1L;

    private final ResponseCode code;

    public AuthException(ResponseCode code, String message) {
        super(message);
        this.code = code;
    }

    public ResponseCode getCode() {
        return code;
    }
}
