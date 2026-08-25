package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Access implementation of the user repository using UCanAccess. */
public final class AccessUserRepository implements UserRepository {
    private final AccessDatabase database;
    private final PasswordHasher passwordHasher;

    public AccessUserRepository(String databasePath, PasswordHasher passwordHasher)
            throws SQLException {
        this(new AccessDatabase(databasePath), passwordHasher);
    }

    public AccessUserRepository(AccessDatabase database, PasswordHasher passwordHasher)
            throws SQLException {
        this.database = database;
        this.passwordHasher = passwordHasher;
        initializeDatabase();
    }

    @Override
    public UserAccount findById(String userId) throws SQLException {
        String sql = "SELECT [userId], [passwordHash], [passwordSalt], "
                + "[displayName], [roleName], [active] FROM [tblUser] WHERE [userId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new UserAccount(
                        result.getString("userId"),
                        result.getString("passwordHash"),
                        result.getString("passwordSalt"),
                        result.getString("displayName"),
                        Role.valueOf(result.getString("roleName")),
                        result.getBoolean("active"));
            }
        }
    }

    private void initializeDatabase() throws SQLException {
        try (Connection connection = database.openConnection()) {
            if (!tableExists(connection, "tblUser")) {
                createUserTable(connection);
            }
            if (countUsers(connection) == 0) {
                insertDemoUsers(connection);
            }
        }
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

    private void createUserTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE [tblUser] ("
                + "[userId] TEXT(32) NOT NULL PRIMARY KEY, "
                + "[passwordHash] TEXT(128) NOT NULL, "
                + "[passwordSalt] TEXT(64) NOT NULL, "
                + "[displayName] TEXT(64) NOT NULL, "
                + "[roleName] TEXT(16) NOT NULL, "
                + "[active] YESNO NOT NULL)";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int countUsers(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM [tblUser]")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void insertDemoUsers(Connection connection) throws SQLException {
        insertUser(connection, "admin", "admin123", "系统管理员", Role.ADMIN);
        insertUser(connection, "student", "student123", "演示学生", Role.STUDENT);
        insertUser(connection, "teacher", "teacher123", "演示教师", Role.TEACHER);
    }

    private void insertUser(Connection connection, String userId, String password,
                            String displayName, Role role) throws SQLException {
        String salt = passwordHasher.newSalt();
        String sql = "INSERT INTO [tblUser] ([userId], [passwordHash], [passwordSalt], "
                + "[displayName], [roleName], [active]) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, passwordHasher.hash(password, salt));
            statement.setString(3, salt);
            statement.setString(4, displayName);
            statement.setString(5, role.name());
            statement.setBoolean(6, true);
            statement.executeUpdate();
        }
    }

    public File getDatabaseFile() {
        return database.getDatabaseFile();
    }
}
