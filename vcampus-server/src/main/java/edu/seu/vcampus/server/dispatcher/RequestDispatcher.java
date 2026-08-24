package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.service.AuthService;
import edu.seu.vcampus.server.session.SessionRegistry;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Routes protocol operations to small, testable services. */
public final class RequestDispatcher {
    private static final Logger LOGGER = Logger.getLogger(RequestDispatcher.class.getName());

    private final AuthService authService;
    private final SessionRegistry sessionRegistry;

    public RequestDispatcher(AuthService authService, SessionRegistry sessionRegistry) {
        this.authService = authService;
        this.sessionRegistry = sessionRegistry;
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
            if (sessionRegistry.find(request.getSessionToken()) == null) {
                return ResponseMessage.failure(request.getRequestId(),
                        ResponseCode.UNAUTHORIZED, "请先登录");
            }
            return ResponseMessage.failure(request.getRequestId(),
                    ResponseCode.NOT_IMPLEMENTED, "该模块接口已预留，尚未实现");
        } catch (SQLException e) {
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
}
