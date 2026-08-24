package edu.seu.vcampus.common.enums;

import java.io.Serializable;

/** Stable result codes returned by the server. */
public enum ResponseCode implements Serializable {
    SUCCESS,
    INVALID_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    NOT_IMPLEMENTED,
    SERVER_ERROR
}
