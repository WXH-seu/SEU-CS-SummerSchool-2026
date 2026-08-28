package edu.seu.vcampus.server.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Access implementation of the audit log using UCanAccess. */
public final class AccessOperationLogRepository implements OperationLogRepository {
    private static final String DRIVER_CLASS = "net.ucanaccess.jdbc.UcanaccessDriver";

    private final File databaseFile;

    public AccessOperationLogRepository(String databasePath) throws SQLException {
        this.databaseFile = new File(databasePath).getAbsoluteFile();
        initializeDatabase();
    }

    @Override
    public void insert(OperationLog log) throws SQLException {
        String sql = "INSERT INTO [tblOperationLog] ([logTime], [userId], [operation], "
                + "[targetUserId], [detail], [success]) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = openConnection(false);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, new Timestamp(log.getLogTime().getTime()));
            statement.setString(2, log.getUserId());
            statement.setString(3, log.getOperation());
            statement.setString(4, log.getTargetUserId());
            statement.setString(5, log.getDetail());
            statement.setBoolean(6, log.isSuccess());
            statement.executeUpdate();
        }
    }

    @Override
    public List<OperationLog> findRecent(int limit) throws SQLException {
        String sql = "SELECT [logId], [logTime], [userId], [operation], [targetUserId], "
                + "[detail], [success] FROM [tblOperationLog] ORDER BY [logId] DESC";
        List<OperationLog> result = new ArrayList<OperationLog>();
        try (Connection connection = openConnection(false);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next() && result.size() < limit) {
                result.add(mapRow(rows));
            }
        }
        return result;
    }

    private void initializeDatabase() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Cannot create database directory: " + parent);
        }
        boolean create = !databaseFile.exists();
        try (Connection connection = openConnection(create)) {
            if (!tableExists(connection, "tblOperationLog")) {
                createLogTable(connection);
            }
        }
    }

    private Connection openConnection(boolean create) throws SQLException {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new SQLException("UCanAccess driver not found", e);
        }
        String url = "jdbc:ucanaccess://" + databaseFile.getAbsolutePath();
        if (create) {
            url += ";newDatabaseVersion=V2010";
        }
        return DriverManager.getConnection(url);
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void createLogTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE [tblOperationLog] ("
                + "[logId] AUTOINCREMENT PRIMARY KEY, "
                + "[logTime] DATETIME NOT NULL, "
                + "[userId] TEXT(32), "
                + "[operation] TEXT(32) NOT NULL, "
                + "[targetUserId] TEXT(32), "
                + "[detail] TEXT(255), "
                + "[success] YESNO NOT NULL)";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private OperationLog mapRow(ResultSet rows) throws SQLException {
        Timestamp timestamp = rows.getTimestamp("logTime");
        long id = rows.getLong("logId");
        return new OperationLog(id,
                timestamp == null ? new java.util.Date(0) : new java.util.Date(timestamp.getTime()),
                rows.getString("userId"),
                rows.getString("operation"),
                rows.getString("targetUserId"),
                rows.getString("detail"),
                rows.getBoolean("success"));
    }

    public File getDatabaseFile() {
        return databaseFile;
    }
}
