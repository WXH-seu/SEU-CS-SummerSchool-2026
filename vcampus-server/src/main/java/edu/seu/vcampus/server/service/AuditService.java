package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.UserOperationLog;
import edu.seu.vcampus.server.dao.OperationLog;
import edu.seu.vcampus.server.dao.OperationLogRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records login attempts and administrator operations for auditability.
 *
 * <p>Recording is <strong>best-effort</strong>: a failure to persist the audit
 * record is logged and swallowed so that it can never block the business
 * operation itself (login, registration, status change, etc.).
 */
public final class AuditService {
    private static final Logger LOGGER = Logger.getLogger(AuditService.class.getName());

    private final OperationLogRepository logRepository;

    public AuditService(OperationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /** Records a login attempt (successful or not). */
    public void recordLogin(String userId, boolean success, String detail) {
        insert(userId, success ? "LOGIN" : "LOGIN_FAILED", null, success, detail);
    }

    /** Records an administrator operation (registration, CSV import, status change). */
    public void recordOperation(String operation, String operatorId,
                                String targetUserId, boolean success, String detail) {
        insert(operatorId, operation, targetUserId, success, detail);
    }

    /** Returns the {@code limit} most recent records, newest first. */
    public List<UserOperationLog> recent(int limit) throws SQLException {
        List<UserOperationLog> result = new ArrayList<UserOperationLog>();
        for (OperationLog log : logRepository.findRecent(limit)) {
            result.add(new UserOperationLog(log.getId(), log.getLogTime(), log.getUserId(),
                    log.getOperation(), log.getTargetUserId(), log.getDetail(), log.isSuccess()));
        }
        return result;
    }

    private void insert(String userId, String operation, String targetUserId,
                        boolean success, String detail) {
        try {
            String safeDetail = detail == null ? "" : detail;
            if (safeDetail.length() > 255) {
                safeDetail = safeDetail.substring(0, 255);
            }
            logRepository.insert(new OperationLog(0, new Date(), userId, operation,
                    targetUserId, safeDetail, success));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to persist audit log for " + operation, e);
        }
    }
}
