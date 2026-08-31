package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.service.AuthService;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.dao.UserAccount;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Routes protocol operations to small, testable services. */
public final class RequestDispatcher {
    private static final Logger LOGGER = Logger.getLogger(RequestDispatcher.class.getName());

    private final AuthService authService;
    private final SessionRegistry sessionRegistry;
    private final AcademicRequestHandler academicHandler;
    private final StoreRequestHandler storeHandler;

    public RequestDispatcher(AuthService authService, SessionRegistry sessionRegistry) {
        this(authService, sessionRegistry, null, null);
    }

    public RequestDispatcher(AuthService authService, SessionRegistry sessionRegistry,
                             AcademicRequestHandler academicHandler) {
        this(authService, sessionRegistry, academicHandler, null);
    }

    public RequestDispatcher(AuthService authService, SessionRegistry sessionRegistry,
                             AcademicRequestHandler academicHandler,
                             StoreRequestHandler storeHandler) {
        this.authService = authService;
        this.sessionRegistry = sessionRegistry;
        this.academicHandler = academicHandler;
        this.storeHandler = storeHandler;
    }

    public ResponseMessage<? extends Serializable> dispatch(RequestMessage<?> request) {
        if (request == null || request.getOperation() == null) {
            return ResponseMessage.failure(null, ResponseCode.INVALID_REQUEST, "请求不能为空");
        }
        try {
            if (request.getOperation() == Operation.PING) {
                return ResponseMessage.success(request.getRequestId(), "服务器运行正常", "PONG");
            }
            if (request.getOperation() == Operation.USER_LOGIN) {
                return login(request);
            }
            if (request.getOperation() == Operation.USER_LOGOUT) {
                authService.logout(request.getSessionToken());
                return ResponseMessage.success(request.getRequestId(), "已退出登录", "OK");
            }
            UserAccount actor = sessionRegistry.find(request.getSessionToken());
            if (actor == null) {
                return ResponseMessage.failure(request.getRequestId(),
                        ResponseCode.UNAUTHORIZED, "请先登录");
            }
            if (academicHandler != null && academicHandler.supports(request.getOperation())) {
                return academicHandler.handle(request, actor);
            }
            if (storeHandler != null && storeHandler.supports(request.getOperation())) {
                return storeHandler.handle(request, actor);
            }
            return ResponseMessage.failure(request.getRequestId(),
                    ResponseCode.NOT_IMPLEMENTED, "该模块接口已预留，尚未实现");
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

    private ResponseMessage<? extends Serializable> login(RequestMessage<?> request)
            throws SQLException {
        if (!(request.getBody() instanceof LoginRequest)) {
            return ResponseMessage.failure(request.getRequestId(),
                    ResponseCode.INVALID_REQUEST, "登录参数格式错误");
        }
        LoginResponse response = authService.login((LoginRequest) request.getBody());
        if (response == null) {
            return ResponseMessage.failure(request.getRequestId(),
                    ResponseCode.UNAUTHORIZED, "账号或密码错误");
        }
        return ResponseMessage.success(request.getRequestId(), "登录成功", response);
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
