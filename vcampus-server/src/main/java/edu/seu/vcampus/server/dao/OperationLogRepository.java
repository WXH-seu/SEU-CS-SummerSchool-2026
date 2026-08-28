package edu.seu.vcampus.server.dao;

import java.sql.SQLException;
import java.util.List;

/** Persistence boundary for the audit log of logins and administrator operations. */
public interface OperationLogRepository {

    /** Appends one audit record. */
    void insert(OperationLog log) throws SQLException;

    /** Returns the most recent records, newest first, capped at {@code limit}. */
    List<OperationLog> findRecent(int limit) throws SQLException;
}
