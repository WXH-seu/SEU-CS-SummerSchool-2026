package edu.seu.vcampus.server.config;

import edu.seu.vcampus.common.config.ProtocolConstants;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Loads server settings from classpath defaults and an optional external file. */
public final class ServerConfig {
    private static final String EXTERNAL_CONFIG_PROPERTY = "vcampus.server.config";

    private final int port;
    private final int workerThreads;
    private final String databasePath;

    private ServerConfig(int port, int workerThreads, String databasePath) {
        this.port = port;
        this.workerThreads = workerThreads;
        this.databasePath = databasePath;
    }

    public static ServerConfig load() throws IOException {
        Properties properties = new Properties();
        InputStream defaults = ServerConfig.class.getResourceAsStream("/server.properties");
        if (defaults != null) {
            try {
                properties.load(defaults);
            } finally {
                defaults.close();
            }
        }
        String externalPath = System.getProperty(EXTERNAL_CONFIG_PROPERTY);
        if (externalPath != null && !externalPath.trim().isEmpty()) {
            InputStream external = new FileInputStream(externalPath);
            try {
                properties.load(external);
            } finally {
                external.close();
            }
        }
        int port = parsePositiveInt(properties.getProperty("server.port"),
                ProtocolConstants.DEFAULT_PORT, "server.port");
        int workerThreads = parsePositiveInt(properties.getProperty("server.workerThreads"),
                16, "server.workerThreads");
        String databasePath = properties.getProperty(
                "database.path", "vcampus-database/vCampus.accdb").trim();
        return new ServerConfig(port, workerThreads, databasePath);
    }

    private static int parsePositiveInt(String value, int defaultValue, String key) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value.trim());
        if (parsed <= 0) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return parsed;
    }

    public int getPort() {
        return port;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public String getDatabasePath() {
        return databasePath;
    }
}
