package edu.seu.vcampus.server.dao;

import java.sql.SQLException;

/** Persistence boundary for user accounts. */
public interface UserRepository {
    UserAccount findById(String userId) throws SQLException;
}
