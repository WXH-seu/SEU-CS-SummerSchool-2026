package edu.seu.vcampus.server.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Owns the Access file location and creates JDBC connections for server DAOs. */
public final class AccessDatabase {
    private static final String DRIVER_CLASS = "net.ucanaccess.jdbc.UcanaccessDriver";

    private final File databaseFile;

    public AccessDatabase(String databasePath) throws SQLException {
        databaseFile = new File(databasePath).getAbsoluteFile();
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Cannot create database directory: " + parent);
        }
        loadDriver();
        boolean create = !databaseFile.exists();
        String url = jdbcUrl(create);
        try (Connection ignored = DriverManager.getConnection(url)) {
            // Opening the first connection creates and validates the Access file.
        }
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(false));
    }

    public File getDatabaseFile() {
        return databaseFile;
    }

    private String jdbcUrl(boolean create) {
        String url = "jdbc:ucanaccess://" + databaseFile.getAbsolutePath();
        return create ? url + ";newDatabaseVersion=V2010" : url;
    }

    private void loadDriver() throws SQLException {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new SQLException("UCanAccess driver not found", e);
        }
    }
}
