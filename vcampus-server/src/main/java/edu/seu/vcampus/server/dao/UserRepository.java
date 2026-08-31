package edu.seu.vcampus.server.dao;

import java.sql.SQLException;
import java.util.List;

/**
 * Persistence boundary for user accounts. Implementations must never expose
 * plaintext passwords; only salted hashes are stored and returned.
 */
public interface UserRepository {

    /** Returns the account with the given id, or {@code null} when absent. */
    UserAccount findById(String userId) throws SQLException;

    /** Returns every account in creation-independent order. */
    List<UserAccount> findAll() throws SQLException;

    /** Inserts a new account. The caller must reject duplicate ids first. */
    void insert(UserAccount account) throws SQLException;

    /** Updates the display name of the given account. */
    void updateDisplayName(String userId, String displayName) throws SQLException;

    /** Replaces the password hash and salt of the given account. */
    void updatePassword(String userId, String passwordHash, String passwordSalt)
            throws SQLException;

    /** Enables or disables the given account. */
    void updateActive(String userId, boolean active) throws SQLException;

    /** Permanently deletes the given account. */
    void delete(String userId) throws SQLException;
}
