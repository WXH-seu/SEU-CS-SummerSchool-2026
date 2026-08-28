package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.server.security.PasswordHasher;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Access implementation of the user repository using UCanAccess. */
public final class AccessUserRepository implements UserRepository {
    private static final String DRIVER_CLASS = "net.ucanaccess.jdbc.UcanaccessDriver";

    private final File databaseFile;
    private final PasswordHasher passwordHasher;

    public AccessUserRepository(String databasePath, PasswordHasher passwordHasher)
            throws SQLException {
        this.databaseFile = new File(databasePath).getAbsoluteFile();
        this.passwordHasher = passwordHasher;
        initializeDatabase();
    }

    @Override
    public UserAccount findById(String userId) throws SQLException {
        String sql = "SELECT [userId], [passwordHash], [passwordSalt], "
                + "[displayName], [roleName], [active] FROM [tblUser] WHERE [userId] = ?";
        try (Connection connection = openConnection(false);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapRow(result) : null;
            }
        }
    }

    @Override
    public List<UserAccount> findAll() throws SQLException {
        String sql = "SELECT [userId], [passwordHash], [passwordSalt], "
                + "[displayName], [roleName], [active] FROM [tblUser] ORDER BY [userId]";
        List<UserAccount> users = new ArrayList<UserAccount>();
        try (Connection connection = openConnection(false);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                users.add(mapRow(result));
            }
        }
        return users;
    }

    @Override
    public void insert(UserAccount account) throws SQLException {
        String sql = "INSERT INTO [tblUser] ([userId], [passwordHash], [passwordSalt], "
                + "[displayName], [roleName], [active]) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = openConnection(false);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.getUserId());
            statement.setString(2, account.getPasswordHash());
            statement.setString(3, account.getPasswordSalt());
            statement.setString(4, account.getDisplayName());
            statement.setString(5, account.getRole().name());
            statement.setBoolean(6, account.isActive());
            statement.executeUpdate();
        }
    }

    @Override
    public void updateDisplayName(String userId, String displayName) throws SQLException {
        String sql = "UPDATE [tblUser] SET [displayName] = ? WHERE [userId] = ?";
        try (Connection connection = openConnection(false);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, displayName);
            statement.setString(2, userId);
            statement.executeUpdate();
        }
    }

    @Override
    public void updatePassword(String userId, String passwordHash, String passwordSalt)
            throws SQLException {
        String sql = "UPDATE [tblUser] SET [passwordHash] = ?, [passwordSalt] = ? "
                + "WHERE [userId] = ?";
        try (Connection connection = openConnection(false);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setString(2, passwordSalt);
            statement.setString(3, userId);
            statement.executeUpdate();
        }
    }

    @Override
    public void updateActive(String userId, boolean active) throws SQLException {
        String sql = "UPDATE [tblUser] SET [active] = ? WHERE [userId] = ?";
        try (Connection connection = openConnection(false);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, active);
            statement.setString(2, userId);
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(String userId) throws SQLException {
        String sql = "DELETE FROM [tblUser] WHERE [userId] = ?";
        try (Connection connection = openConnection(false);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.executeUpdate();
        }
    }

    private UserAccount mapRow(ResultSet result) throws SQLException {
        return new UserAccount(
                result.getString("userId"),
                result.getString("passwordHash"),
                result.getString("passwordSalt"),
                result.getString("displayName"),
                parseRole(result.getString("roleName")),
                result.getBoolean("active"));
    }

    /** Maps a stored role name, tolerating the legacy {@code ADMIN} value. */
    private Role parseRole(String roleName) {
        if ("ADMIN".equalsIgnoreCase(roleName)) {
            return Role.SUBSYSADMIN;
        }
        return Role.valueOf(roleName);
    }

    private void initializeDatabase() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Cannot create database directory: " + parent);
        }
        boolean create = !databaseFile.exists();
        try (Connection connection = openConnection(create)) {
            if (!tableExists(connection, "tblUser")) {
                createUserTable(connection);
            }
            if (countUsers(connection) == 0) {
                insertDemoUsers(connection);
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
        insertUser(connection, "superadmin", "super123", "超级管理员", Role.SUPER_ADMIN);
        insertUser(connection, "admin", "admin123", "系统管理员", Role.SUBSYSADMIN);
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
        return databaseFile;
    }
}
