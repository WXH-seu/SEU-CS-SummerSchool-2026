package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.dto.UserImportFailure;
import edu.seu.vcampus.common.dto.UserImportRequest;
import edu.seu.vcampus.common.dto.UserImportResponse;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.dao.UserRepository;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.session.SessionRegistry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Authentication and account use cases independent from networking and Swing.
 *
 * <p><strong>Account management is super-admin only.</strong> Registering users
 * (single or via CSV) and managing account status requires
 * {@link Role#SUPER_ADMIN}. The sub-system administrator ({@link Role#ADMIN})
 * only operates business sub-systems (student management, library, etc.) and
 * has no account-management permission.
 *
 * <p><strong>Auditability.</strong> Every login attempt and every administrator
 * operation is forwarded to {@link AuditService}; recording is best-effort and
 * never blocks the business flow.
 *
 * <p>Passwords never cross this boundary in plaintext: only salted hashes are
 * persisted, and failure messages never reveal which credential was wrong.
 */
public final class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionRegistry sessionRegistry;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher,
                       SessionRegistry sessionRegistry, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionRegistry = sessionRegistry;
        this.auditService = auditService;
    }

    /**
     * Authenticates the user and creates a session. Every attempt is audited.
     */
    public LoginResponse login(LoginRequest request) throws SQLException, AuthException {
        String userId = request == null ? null : request.getUserId();
        try {
            if (request == null || isBlank(request.getUserId()) || isBlank(request.getPassword())) {
                throw new AuthException(ResponseCode.INVALID_REQUEST, "账号和密码不能为空");
            }
            UserAccount account = userRepository.findById(request.getUserId().trim());
            if (account == null
                    || !passwordHasher.matches(request.getPassword(),
                            account.getPasswordSalt(), account.getPasswordHash())) {
                throw new AuthException(ResponseCode.UNAUTHORIZED, "账号或密码错误");
            }
            if (!account.isActive()) {
                throw new AuthException(ResponseCode.UNAUTHORIZED, "账号已被禁用，请联系管理员");
            }
            String token = sessionRegistry.create(account);
            auditService.recordLogin(request.getUserId().trim(), true, "登录成功");
            return new LoginResponse(token, account.getUserId(),
                    account.getDisplayName(), account.getRole());
        } catch (AuthException e) {
            auditService.recordLogin(userId, false, e.getMessage());
            throw e;
        }
    }

    /**
     * Creates one account on behalf of a super administrator. No session is
     * created for the new user. Duplicate ids raise {@code CONFLICT}.
     */
    public AccountInfo register(RegisterRequest request, Role actorRole, String operatorId)
            throws SQLException, AuthException {
        requireSuperAdmin(actorRole);
        try {
            AccountInfo created = createAccount(request);
            auditService.recordOperation("REGISTER", operatorId, created.getUserId(),
                    true, "创建账号");
            return created;
        } catch (AuthException e) {
            auditService.recordOperation("REGISTER", operatorId, safeUserId(request),
                    false, e.getMessage());
            throw e;
        }
    }

    /**
     * Batch-registers users from a parsed CSV payload, tolerating per-row
     * failures. Only a super administrator may call this.
     */
    public UserImportResponse importUsers(UserImportRequest request, Role actorRole,
                                          String operatorId) throws SQLException, AuthException {
        requireSuperAdmin(actorRole);
        try {
            if (request == null || request.getUsers() == null) {
                throw new AuthException(ResponseCode.INVALID_REQUEST, "导入数据不能为空");
            }
            List<RegisterRequest> users = request.getUsers();
            int imported = 0;
            List<UserImportFailure> failures = new ArrayList<UserImportFailure>();
            for (int i = 0; i < users.size(); i++) {
                RegisterRequest one = users.get(i);
                try {
                    createAccount(one);
                    imported++;
                } catch (AuthException e) {
                    failures.add(new UserImportFailure(i + 1, safeUserId(one), e.getMessage()));
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "CSV import row " + (i + 1) + " failed in database", e);
                    failures.add(new UserImportFailure(i + 1, safeUserId(one), "数据库操作失败"));
                }
            }
            auditService.recordOperation("IMPORT_CSV", operatorId, null, true,
                    "导入成功 " + imported + "，失败 " + failures.size());
            return new UserImportResponse(imported, failures);
        } catch (AuthException e) {
            auditService.recordOperation("IMPORT_CSV", operatorId, null, false, e.getMessage());
            throw e;
        }
    }

    /** Returns non-sensitive information about the current account. */
    public AccountInfo getAccount(String userId) throws SQLException, AuthException {
        return toInfo(findActiveAccount(userId));
    }

    /** Updates the display name and returns the refreshed account. */
    public AccountInfo updateProfile(String userId, String displayName)
            throws SQLException, AuthException {
        String normalized = trimToNull(displayName);
        if (normalized == null) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "显示名不能为空");
        }
        findActiveAccount(userId);
        userRepository.updateDisplayName(userId, normalized);
        return toInfo(findActiveAccount(userId));
    }

    /** Verifies the old password and replaces it with the new one. */
    public void changePassword(String userId, String oldPassword, String newPassword)
            throws SQLException, AuthException {
        UserAccount account = findActiveAccount(userId);
        if (oldPassword == null
                || !passwordHasher.matches(oldPassword,
                        account.getPasswordSalt(), account.getPasswordHash())) {
            throw new AuthException(ResponseCode.UNAUTHORIZED, "原密码错误");
        }
        String normalized = trimToNull(newPassword);
        if (normalized == null || normalized.length() < MIN_PASSWORD_LENGTH) {
            throw new AuthException(ResponseCode.INVALID_REQUEST,
                    "新密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }
        String salt = passwordHasher.newSalt();
        userRepository.updatePassword(userId, passwordHasher.hash(normalized, salt), salt);
    }

    /**
     * Permanently removes the account after password confirmation. All sessions
     * of the user are invalidated. This self-service action is audited.
     */
    public void deleteAccount(String userId, String password)
            throws SQLException, AuthException {
        try {
            UserAccount account = findActiveAccount(userId);
            if (password == null
                    || !passwordHasher.matches(password,
                            account.getPasswordSalt(), account.getPasswordHash())) {
                throw new AuthException(ResponseCode.UNAUTHORIZED, "密码错误，无法注销账号");
            }
            userRepository.delete(userId);
            sessionRegistry.removeAllForUser(userId);
            auditService.recordOperation("DELETE", userId, userId, true, "注销账号");
        } catch (AuthException e) {
            auditService.recordOperation("DELETE", userId, userId, false, e.getMessage());
            throw e;
        }
    }

    /** Lists all accounts; super administrator only. */
    public List<AccountInfo> listUsers(Role actorRole) throws SQLException, AuthException {
        requireSuperAdmin(actorRole);
        List<AccountInfo> result = new ArrayList<AccountInfo>();
        for (UserAccount account : userRepository.findAll()) {
            result.add(toInfo(account));
        }
        return result;
    }

    /**
     * Enables or disables another account. Super administrator only; the caller
     * cannot change his or her own status.
     */
    public void updateUserStatus(String actorUserId, String targetUserId, boolean active,
                                 Role actorRole) throws SQLException, AuthException {
        requireSuperAdmin(actorRole);
        try {
            String target = trimToNull(targetUserId);
            if (target == null) {
                throw new AuthException(ResponseCode.INVALID_REQUEST, "目标账号不能为空");
            }
            if (target.equals(actorUserId)) {
                throw new AuthException(ResponseCode.INVALID_REQUEST, "不能修改自己的账号状态");
            }
            if (userRepository.findById(target) == null) {
                throw new AuthException(ResponseCode.NOT_FOUND, "账号不存在");
            }
            userRepository.updateActive(target, active);
            auditService.recordOperation("UPDATE_STATUS", actorUserId, target, true,
                    active ? "启用账号" : "禁用账号");
        } catch (AuthException e) {
            auditService.recordOperation("UPDATE_STATUS", actorUserId, targetUserId, false,
                    e.getMessage());
            throw e;
        }
    }

    public void logout(String sessionToken) {
        sessionRegistry.remove(sessionToken);
    }

    private AccountInfo createAccount(RegisterRequest request) throws SQLException, AuthException {
        if (request == null) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "注册信息不能为空");
        }
        String userId = trimToNull(request.getUserId());
        String password = trimToNull(request.getPassword());
        String displayName = trimToNull(request.getDisplayName());
        if (userId == null || password == null || displayName == null || request.getRole() == null) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "注册信息不完整");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new AuthException(ResponseCode.INVALID_REQUEST,
                    "密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (userRepository.findById(userId) != null) {
            throw new AuthException(ResponseCode.CONFLICT, "账号已存在");
        }
        String salt = passwordHasher.newSalt();
        UserAccount account = new UserAccount(userId,
                passwordHasher.hash(password, salt), salt, displayName,
                request.getRole(), true);
        userRepository.insert(account);
        return toInfo(account);
    }

    private void requireSuperAdmin(Role actorRole) throws AuthException {
        if (actorRole != Role.SUPER_ADMIN) {
            throw new AuthException(ResponseCode.FORBIDDEN, "仅超级管理员可以执行该操作");
        }
    }

    private UserAccount findActiveAccount(String userId) throws SQLException, AuthException {
        if (isBlank(userId)) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "账号不能为空");
        }
        UserAccount account = userRepository.findById(userId.trim());
        if (account == null) {
            throw new AuthException(ResponseCode.NOT_FOUND, "账号不存在");
        }
        return account;
    }

    private AccountInfo toInfo(UserAccount account) {
        return new AccountInfo(account.getUserId(), account.getDisplayName(),
                account.getRole(), account.isActive());
    }

    private String safeUserId(RegisterRequest request) {
        return request == null ? null : request.getUserId();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
