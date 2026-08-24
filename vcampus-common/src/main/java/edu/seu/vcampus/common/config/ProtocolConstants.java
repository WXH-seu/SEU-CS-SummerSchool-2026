package edu.seu.vcampus.common.config;

/** Shared defaults used by both client and server. */
public final class ProtocolConstants {
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 4444;
    public static final int CONNECT_TIMEOUT_MILLIS = 5000;
    public static final int SOCKET_TIMEOUT_MILLIS = 15000;

    private ProtocolConstants() {
    }
}
