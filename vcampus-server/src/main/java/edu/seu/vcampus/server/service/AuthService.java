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
 * <p><strong>Registration policy:</strong> account creation is an
 * administrator-only operation. Students are batch-imported through the CSV
 * channel ({@link #importUsers}), while teachers and other administrators are
 * created manually ({@link #register}). A super administrator (global) may
 * create any role including other administrators, whereas a sub-system
 * administrator ({@link Role#ADMIN}) may only create students and teachers;
 * administrator accounts can only ever be created by a super administrator.
 *
 * <p><strong>Defence in depth:</strong> administrator-only methods take the
 * caller's role as an explicit {@code actorRole} argument and re-check it
 * internally. The dispatcher also performs the role check, so an accidental
 * bypass of the dispatcher can never escalate privileges. Beyond that, account
 * creation and status changes additionally apply a target-role gate.
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

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher,
                       SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Authenticates the user and creates a session. Throws
     * {@link AuthException#getCode()} {@code UNAUTHORIZED} with a generic
     * message when the account is missing or the password is wrong, and a
     * specific message when the account is disabled.
     */
    public LoginResponse login(LoginRequest request) throws SQLException, AuthException {
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
        return new LoginResponse(token, account.getUserId(),
                account.getDisplayName(), account.getRole());
    }

    /**
     * Creates one account on behalf of an administrator. The caller must be an
     * administrator, and only a super administrator may create another
     * administrator. No session is created for the new user. Duplicate ids
     * raise an {@link AuthException} with code {@code CONFLICT}.
     */
    public AccountInfo register(RegisterRequest request, Role actorRole)
            throws SQLException, AuthException {
        requireAnyAdmin(actorRole);
        return createAccount(request, actorRole);
    }

    /**
     * Batch-registers users from a parsed CSV payload, tolerating per-row
     * failures. Only administrators may call this; administrator rows still obey
     * the super-admin-only gate. Returns a summary with the number of imported
     * users and the list of failed rows.
     */
    public UserImportResponse importUsers(UserImportRequest request, Role actorRole)
            throws SQLException, AuthException {
        requireAnyAdmin(actorRole);
        if (request == null || request.getUsers() == null) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "导入数据不能为空");
        }
        List<RegisterRequest> users = request.getUsers();
        int imported = 0;
        List<UserImportFailure> failures = new ArrayList<UserImportFailure>();
        for (int i = 0; i < users.size(); i++) {
            RegisterRequest one = users.get(i);
            try {
                createAccount(one, actorRole);
                imported++;
            } catch (AuthException e) {
                failures.add(new UserImportFailure(i + 1, safeUserId(one), e.getMessage()));
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "CSV import row " + (i + 1) + " failed in database", e);
                failures.add(new UserImportFailure(i + 1, safeUserId(one), "数据库操作失败"));
            }
        }
        return new UserImportResponse(imported, failures);
    }

    /** Returns non-sensitive information about the current account. */
    public AccountInfo getAccount(String userId) throws SQLException, AuthException {
        UserAccount account = findActiveAccount(userId);
        return toInfo(account);
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
     * of the user are invalidated so other connected clients are signed out too.
     */
    public void deleteAccount(String userId, String password)
            throws SQLException, AuthException {
        UserAccount account = findActiveAccount(userId);
        if (password == null
                || !passwordHasher.matches(password,
                        account.getPasswordSalt(), account.getPasswordHash())) {
            throw new AuthException(ResponseCode.UNAUTHORIZED, "密码错误，无法注销账号");
        }
        userRepository.delete(userId);
        sessionRegistry.removeAllForUser(userId);
    }

    /** Lists all accounts for an administrator without exposing hashes. */
    public List<AccountInfo> listUsers(Role actorRole) throws SQLException, AuthException {
        requireAnyAdmin(actorRole);
        List<AccountInfo> result = new ArrayList<AccountInfo>();
        for (UserAccount account : userRepository.findAll()) {
            result.add(toInfo(account));
        }
        return result;
    }

    /**
     * Enables or disables an account. The caller must be an administrator and
     * cannot change his or her own status. A sub-system administrator may only
     * manage students and teachers; only a super administrator may manage other
     * administrator accounts.
     */
    public void updateUserStatus(String actorUserId, String targetUserId, boolean active,
                                 Role actorRole) throws SQLException, AuthException {
        requireAnyAdmin(actorRole);
        String target = trimToNull(targetUserId);
        if (target == null) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "目标账号不能为空");
        }
        if (target.equals(actorUserId)) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "不能修改自己的账号状态");
        }
        UserAccount targetAccount = userRepository.findById(target);
        if (targetAccount == null) {
            throw new AuthException(ResponseCode.NOT_FOUND, "账号不存在");
        }
        if (!canManageStatus(actorRole, targetAccount.getRole())) {
            throw new AuthException(ResponseCode.FORBIDDEN, "无权管理该角色的账号");
        }
        userRepository.updateActive(target, active);
    }

    public void logout(String sessionToken) {
        sessionRegistry.remove(sessionToken);
    }

    /**
     * Creates the account after validating the caller may create the requested
     * role. Only a super administrator can create {@link Role#ADMIN} or
     * {@link Role#SUPER_ADMIN} accounts.
     */
    private AccountInfo createAccount(RegisterRequest request, Role actorRole)
            throws SQLException, AuthException {
        if (request == null) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "注册信息不能为空");
        }
        String userId = trimToNull(request.getUserId());
        String password = trimToNull(request.getPassword());
        String displayName = trimToNull(request.getDisplayName());
        Role targetRole = request.getRole();
        if (userId == null || password == null || displayName == null || targetRole == null) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "注册信息不完整");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new AuthException(ResponseCode.INVALID_REQUEST,
                    "密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (!canCreate(actorRole, targetRole)) {
            throw new AuthException(ResponseCode.FORBIDDEN, "仅超级管理员可以创建管理员账号");
        }
        if (userRepository.findById(userId) != null) {
            throw new AuthException(ResponseCode.CONFLICT, "账号已存在");
        }
        String salt = passwordHasher.newSalt();
        UserAccount account = new UserAccount(userId,
                passwordHasher.hash(password, salt), salt, displayName,
                targetRole, true);
        userRepository.insert(account);
        return toInfo(account);
    }

    private boolean canCreate(Role actor, Role target) {
        if (actor == Role.SUPER_ADMIN) {
            return true;
        }
        if (actor == Role.ADMIN) {
            return target == Role.STUDENT || target == Role.TEACHER;
        }
        return false;
    }

    private boolean canManageStatus(Role actor, Role target) {
        if (actor == Role.SUPER_ADMIN) {
            return true;
        }
        if (actor == Role.ADMIN) {
            return target == Role.STUDENT || target == Role.TEACHER;
        }
        return false;
    }

    private void requireAnyAdmin(Role actorRole) throws AuthException {
        if (actorRole != Role.ADMIN && actorRole != Role.SUPER_ADMIN) {
            throw new AuthException(ResponseCode.FORBIDDEN, "仅管理员可以执行该操作");
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
