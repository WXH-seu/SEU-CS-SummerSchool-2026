package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.dao.UserRepository;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.session.SessionRegistry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Authentication and account use cases independent from networking and Swing.
 *
 * <p>Passwords never cross this boundary in plaintext: only salted hashes are
 * persisted, and failure messages never reveal which credential was wrong.
 */
public final class AuthService {
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
     * Creates a new account and signs it in immediately. Duplicate ids raise
     * {@link AuthException} with code {@code CONFLICT}.
     */
    public LoginResponse register(RegisterRequest request) throws SQLException, AuthException {
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
        String token = sessionRegistry.create(account);
        return new LoginResponse(token, userId, displayName, account.getRole());
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

    /** Lists all accounts for the administrator without exposing hashes. */
    public List<AccountInfo> listUsers() throws SQLException {
        List<AccountInfo> result = new ArrayList<AccountInfo>();
        for (UserAccount account : userRepository.findAll()) {
            result.add(toInfo(account));
        }
        return result;
    }

    /**
     * Enables or disables another account. The caller cannot change his or her
     * own status, which prevents an administrator from locking the system out.
     */
    public void updateUserStatus(String actorUserId, String targetUserId, boolean active)
            throws SQLException, AuthException {
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
    }

    public void logout(String sessionToken) {
        sessionRegistry.remove(sessionToken);
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
