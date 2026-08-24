package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.security.PermissionPolicy;
import edu.seu.vcampus.server.service.AuthService;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.Serializable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the dispatcher pipeline: public operations, session requirement,
 * role-based permission checks and user-module responses.
 */
public class RequestDispatcherTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private RequestDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        PasswordHasher passwordHasher = new PasswordHasher();
        AccessUserRepository repository = new AccessUserRepository(
                new java.io.File(temporaryFolder.getRoot(), "dispatcher-test.accdb")
                        .getAbsolutePath(), passwordHasher);
        SessionRegistry sessions = new SessionRegistry();
        AuthService authService = new AuthService(repository, passwordHasher, sessions);
        dispatcher = new RequestDispatcher(authService, sessions, new PermissionPolicy());
    }

    @Test
    public void pingWorksWithoutLogin() {
        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.PING, null, null));
        assertEquals(ResponseCode.SUCCESS, response.getCode());
    }

    @Test
    public void missingSessionIsRejectedWithUnauthorized() {
        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_ACCOUNT_QUERY, "bad-token", null));
        assertEquals(ResponseCode.UNAUTHORIZED, response.getCode());
        assertEquals("请先登录", response.getMessage());
    }

    @Test
    public void registerLoginAndQueryFlowWorks() {
        ResponseMessage<?> registered = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, null,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.SUCCESS, registered.getCode());
        String token = ((LoginResponse) registered.getBody()).getSessionToken();

        ResponseMessage<?> query = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_ACCOUNT_QUERY, token, null));
        assertEquals(ResponseCode.SUCCESS, query.getCode());

        ResponseMessage<?> duplicate = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, null,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.CONFLICT, duplicate.getCode());
    }

    @Test
    public void wrongLoginBodyIsInvalidRequest() {
        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_LOGIN, null, "not-a-login"));
        assertEquals(ResponseCode.INVALID_REQUEST, response.getCode());
    }

    @Test
    public void studentIsForbiddenFromAdminOperations() {
        ResponseMessage<?> login = dispatcher.dispatch(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("student", "student123")));
        String token = ((LoginResponse) login.getBody()).getSessionToken();

        ResponseMessage<?> forbidden = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_LIST_QUERY, token, null));
        assertEquals(ResponseCode.FORBIDDEN, forbidden.getCode());

        ResponseMessage<?> adminList = dispatcher.dispatch(
                new RequestMessage<LoginRequest>(
                        Operation.USER_LOGIN, null, new LoginRequest("admin", "admin123")));
        String adminToken = ((LoginResponse) adminList.getBody()).getSessionToken();
        ResponseMessage<?> allowed = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_LIST_QUERY, adminToken, null));
        assertEquals(ResponseCode.SUCCESS, allowed.getCode());
        assertNotNull(allowed.getBody());
    }

    @Test
    public void adminCanDisableStudentAccount() {
        ResponseMessage<?> login = dispatcher.dispatch(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("admin", "admin123")));
        String adminToken = ((LoginResponse) login.getBody()).getSessionToken();

        ResponseMessage<?> result = dispatcher.dispatch(
                new RequestMessage<edu.seu.vcampus.common.dto.UserStatusUpdateRequest>(
                        Operation.USER_STATUS_UPDATE, adminToken,
                        new edu.seu.vcampus.common.dto.UserStatusUpdateRequest("student", false)));
        assertEquals(ResponseCode.SUCCESS, result.getCode());

        ResponseMessage<?> disabledLogin = dispatcher.dispatch(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("student", "student123")));
        assertEquals(ResponseCode.UNAUTHORIZED, disabledLogin.getCode());
    }

    @Test
    public void unimplementedModuleOperationIsRejectedWithNotice() {
        ResponseMessage<?> login = dispatcher.dispatch(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("student", "student123")));
        String token = ((LoginResponse) login.getBody()).getSessionToken();

        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.COURSE_QUERY, token, null));
        assertEquals(ResponseCode.NOT_IMPLEMENTED, response.getCode());
        assertTrue(response.getMessage().contains("预留"));
    }
}
