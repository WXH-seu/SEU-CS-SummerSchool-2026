package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.DeleteAccountRequest;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.PasswordChangeRequest;
import edu.seu.vcampus.common.dto.ProfileUpdateRequest;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.dto.UserImportRequest;
import edu.seu.vcampus.common.dto.UserImportResponse;
import edu.seu.vcampus.common.dto.UserListResponse;
import edu.seu.vcampus.common.dto.UserOperationLogResponse;
import edu.seu.vcampus.common.dto.UserStatusUpdateRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.security.PermissionPolicy;
import edu.seu.vcampus.server.service.AuditService;
import edu.seu.vcampus.server.service.AuthException;
import edu.seu.vcampus.server.service.AuthService;
import edu.seu.vcampus.server.session.SessionRegistry;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes protocol operations to small, testable services.
 *
 * <p>Every request is checked in the same order: public operations are handled
 * directly, authenticated operations first require a valid session
 * ({@code UNAUTHORIZED}). Business sub-system operations are then delegated to
 * their module handlers (academic / library), which perform their own
 * per-module authorization; the remaining user-module operations are gated by
 * the shared {@link PermissionPolicy} and dispatched to {@link AuthService}.
 */
public final class RequestDispatcher {
    private static final Logger LOGGER = Logger.getLogger(RequestDispatcher.class.getName());

    private final AuthService authService;
    private final SessionRegistry sessionRegistry;
    private final PermissionPolicy permissionPolicy;
    private final AuditService auditService;
    private final AcademicRequestHandler academicHandler;
    private final CourseRequestHandler courseHandler;
    private final LibraryRequestHandler libraryHandler;

    public RequestDispatcher(AuthService authService, SessionRegistry sessionRegistry,
                             PermissionPolicy permissionPolicy, AuditService auditService,
                             AcademicRequestHandler academicHandler,
                             CourseRequestHandler courseHandler,
                             LibraryRequestHandler libraryHandler) {
        this.authService = authService;
        this.sessionRegistry = sessionRegistry;
        this.permissionPolicy = permissionPolicy;
        this.auditService = auditService;
        this.academicHandler = academicHandler;
        this.courseHandler = courseHandler;
        this.libraryHandler = libraryHandler;
    }

    /** Convenience constructor when no business sub-system handlers are wired. */
    public RequestDispatcher(AuthService authService, SessionRegistry sessionRegistry,
                             PermissionPolicy permissionPolicy, AuditService auditService) {
        this(authService, sessionRegistry, permissionPolicy, auditService,
                null, null, null);
    }

    public ResponseMessage<? extends Serializable> dispatch(RequestMessage<?> request) {
        if (request == null || request.getOperation() == null) {
            return ResponseMessage.failure(null, ResponseCode.INVALID_REQUEST, "请求不能为空");
        }
        try {
            Operation operation = request.getOperation();
            if (permissionPolicy.isPublic(operation)) {
                return dispatchPublic(request, operation);
            }
            UserAccount account = sessionRegistry.find(request.getSessionToken());
            if (account == null) {
                return ResponseMessage.failure(request.getRequestId(),
                        ResponseCode.UNAUTHORIZED, "请先登录");
            }
            if (academicHandler != null && academicHandler.supports(operation)) {
                return academicHandler.handle(request, account);
            }
            if (courseHandler != null && courseHandler.supports(operation)) {
                return courseHandler.handle(request, account);
            }
            if (libraryHandler != null && libraryHandler.supports(operation)) {
                return libraryHandler.handle(request, account);
            }
            if (!permissionPolicy.allows(operation, account.getRole(),
                    account.getAdminScopes())) {
                return ResponseMessage.failure(request.getRequestId(),
                        ResponseCode.FORBIDDEN, "您没有权限执行该操作");
            }
            return dispatchAuthenticated(request, operation, account);
        } catch (AuthException e) {
            return ResponseMessage.failure(request.getRequestId(), e.getCode(), e.getMessage());
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                return ResponseMessage.failure(request.getRequestId(),
                        ResponseCode.CONFLICT, "数据重复或仍被其他记录引用");
            }
            LOGGER.log(Level.SEVERE, "Database request failed", e);
            return ResponseMessage.failure(request.getRequestId(),
                    ResponseCode.SERVER_ERROR, "数据库操作失败");
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Request failed", e);
            return ResponseMessage.failure(request.getRequestId(),
                    ResponseCode.SERVER_ERROR, "服务器处理请求失败");
        }
    }

    private ResponseMessage<? extends Serializable> dispatchPublic(
            RequestMessage<?> request, Operation operation) throws SQLException, AuthException {
        if (operation == Operation.PING) {
            return ResponseMessage.success(request.getRequestId(), "服务器运行正常", "PONG");
        }
        if (operation == Operation.USER_LOGIN) {
            LoginRequest body = requireBody(request, LoginRequest.class);
            LoginResponse session = authService.login(body);
            return ResponseMessage.success(request.getRequestId(), "登录成功", session);
        }
        return ResponseMessage.failure(request.getRequestId(),
                ResponseCode.NOT_IMPLEMENTED, "该操作尚未实现");
    }

    private ResponseMessage<? extends Serializable> dispatchAuthenticated(
            RequestMessage<?> request, Operation operation, UserAccount account)
            throws SQLException, AuthException {
        switch (operation) {
            case USER_LOGOUT:
                authService.logout(request.getSessionToken());
                return ResponseMessage.success(request.getRequestId(), "已退出登录", "OK");
            case USER_REGISTER:
                RegisterRequest newUser = requireBody(request, RegisterRequest.class);
                AccountInfo created = authService.register(
                        newUser, account.getRole(), account.getUserId());
                return ResponseMessage.success(request.getRequestId(), "已创建账号", created);
            case USER_IMPORT_CSV:
                UserImportRequest importRequest = requireBody(request, UserImportRequest.class);
                UserImportResponse importResult = authService.importUsers(
                        importRequest, account.getRole(), account.getUserId());
                return ResponseMessage.success(request.getRequestId(), "导入完成", importResult);
            case USER_ACCOUNT_QUERY:
                return ResponseMessage.success(request.getRequestId(), "查询成功",
                        authService.getAccount(account.getUserId()));
            case USER_PROFILE_UPDATE:
                ProfileUpdateRequest profile = requireBody(request, ProfileUpdateRequest.class);
                AccountInfo updated = authService.updateProfile(
                        account.getUserId(), profile.getDisplayName());
                return ResponseMessage.success(request.getRequestId(), "资料已更新", updated);
            case USER_PASSWORD_CHANGE:
                PasswordChangeRequest password = requireBody(request, PasswordChangeRequest.class);
                authService.changePassword(account.getUserId(),
                        password.getOldPassword(), password.getNewPassword());
                return ResponseMessage.success(request.getRequestId(), "密码修改成功", "OK");
            case USER_DELETE:
                DeleteAccountRequest deletion = requireBody(request, DeleteAccountRequest.class);
                authService.deleteAccount(account.getUserId(), deletion.getPassword());
                return ResponseMessage.success(request.getRequestId(), "账号已注销", "OK");
            case USER_LIST_QUERY:
                List<AccountInfo> users = authService.listUsers(account.getRole());
                return ResponseMessage.success(request.getRequestId(), "查询成功",
                        new UserListResponse(users));
            case USER_STATUS_UPDATE:
                UserStatusUpdateRequest status = requireBody(request, UserStatusUpdateRequest.class);
                authService.updateUserStatus(account.getUserId(),
                        status.getUserId(), status.isActive(), account.getRole());
                return ResponseMessage.success(request.getRequestId(), "账号状态已更新", "OK");
            case USER_AUDIT_QUERY:
                return ResponseMessage.success(request.getRequestId(), "查询成功",
                        new UserOperationLogResponse(auditService.recent(200)));
            default:
                return ResponseMessage.failure(request.getRequestId(),
                        ResponseCode.NOT_IMPLEMENTED, "该模块接口已预留，尚未实现");
        }
    }

    private <T> T requireBody(RequestMessage<?> request, Class<T> type) throws AuthException {
        if (!type.isInstance(request.getBody())) {
            throw new AuthException(ResponseCode.INVALID_REQUEST, "请求参数格式错误");
        }
        return type.cast(request.getBody());
    }

    private boolean isConstraintViolation(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            String state = current.getSQLState();
            if (state != null && state.startsWith("23")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("constraint")) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }
}
