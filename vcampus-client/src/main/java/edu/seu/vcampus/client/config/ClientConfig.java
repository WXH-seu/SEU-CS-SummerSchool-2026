package edu.seu.vcampus.client.config;

import edu.seu.vcampus.common.config.ProtocolConstants;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Loads client endpoint settings from defaults and an optional external file. */
public final class ClientConfig {
    private static final String EXTERNAL_CONFIG_PROPERTY = "vcampus.client.config";

    private final String host;
    private final int port;

    private ClientConfig(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static ClientConfig load() throws IOException {
        Properties properties = new Properties();
        InputStream defaults = ClientConfig.class.getResourceAsStream("/client.properties");
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
        String host = properties.getProperty("server.host", ProtocolConstants.DEFAULT_HOST).trim();
        int port = Integer.parseInt(properties.getProperty(
                "server.port", String.valueOf(ProtocolConstants.DEFAULT_PORT)).trim());
        return new ClientConfig(host, port);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
