package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.dto.UserImportRequest;
import edu.seu.vcampus.common.dto.UserImportResponse;
import edu.seu.vcampus.common.dto.UserStatusUpdateRequest;
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
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the dispatcher pipeline with the admin-owned registration policy:
 * public operations, session requirement, role-based permission checks and the
 * user-module responses.
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

    private String login(Role seedRole, String password) {
        String userId = seedRole.name().toLowerCase();
        ResponseMessage<?> response = dispatcher.dispatch(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest(userId, password)));
        assertEquals(ResponseCode.SUCCESS, response.getCode());
        return ((LoginResponse) response.getBody()).getSessionToken();
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
    public void registerRequiresSession() {
        ResponseMessage<?> response = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, null,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.UNAUTHORIZED, response.getCode());
    }

    @Test
    public void studentCannotRegister() {
        String token = login(Role.STUDENT, "student123");
        ResponseMessage<?> response = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, token,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.FORBIDDEN, response.getCode());
    }

    @Test
    public void adminRegistersThenUserLogsIn() {
        String adminToken = login(Role.ADMIN, "admin123");

        ResponseMessage<?> registered = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, adminToken,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.SUCCESS, registered.getCode());
        AccountInfo created = (AccountInfo) registered.getBody();
        assertEquals("stu2026", created.getUserId());
        assertEquals(Role.STUDENT, created.getRole());

        ResponseMessage<?> duplicate = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, adminToken,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.CONFLICT, duplicate.getCode());

        ResponseMessage<?> studentLogin = dispatcher.dispatch(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("stu2026", "secret123")));
        assertEquals(ResponseCode.SUCCESS, studentLogin.getCode());
        String studentToken = ((LoginResponse) studentLogin.getBody()).getSessionToken();
        assertEquals(ResponseCode.SUCCESS, dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_ACCOUNT_QUERY, studentToken, null)).getCode());
    }

    @Test
    public void csvImportWorksForAdmin() {
        String adminToken = login(Role.ADMIN, "admin123");
        UserImportRequest payload = new UserImportRequest(Arrays.asList(
                new RegisterRequest("s001", "secret123", "学生一", Role.STUDENT),
                new RegisterRequest("t001", "secret123", "教师一", Role.TEACHER)));

        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<UserImportRequest>(Operation.USER_IMPORT_CSV, adminToken, payload));

        assertEquals(ResponseCode.SUCCESS, response.getCode());
        UserImportResponse result = (UserImportResponse) response.getBody();
        assertEquals(2, result.getImported());
        assertTrue(result.getFailures().isEmpty());
    }

    @Test
    public void wrongLoginBodyIsInvalidRequest() {
        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_LOGIN, null, "not-a-login"));
        assertEquals(ResponseCode.INVALID_REQUEST, response.getCode());
    }

    @Test
    public void studentIsForbiddenFromAdminOperations() {
        String token = login(Role.STUDENT, "student123");
        ResponseMessage<?> forbidden = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_LIST_QUERY, token, null));
        assertEquals(ResponseCode.FORBIDDEN, forbidden.getCode());

        String adminToken = login(Role.ADMIN, "admin123");
        ResponseMessage<?> allowed = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_LIST_QUERY, adminToken, null));
        assertEquals(ResponseCode.SUCCESS, allowed.getCode());
        assertNotNull(allowed.getBody());
    }

    @Test
    public void adminCanDisableStudentAccount() {
        String adminToken = login(Role.ADMIN, "admin123");
        ResponseMessage<?> result = dispatcher.dispatch(
                new RequestMessage<UserStatusUpdateRequest>(Operation.USER_STATUS_UPDATE, adminToken,
                        new UserStatusUpdateRequest("student", false)));
        assertEquals(ResponseCode.SUCCESS, result.getCode());

        ResponseMessage<?> disabledLogin = dispatcher.dispatch(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("student", "student123")));
        assertEquals(ResponseCode.UNAUTHORIZED, disabledLogin.getCode());
    }

    @Test
    public void superAdminCanCreateAdministratorOverProtocol() {
        ResponseMessage<?> loginRes = dispatcher.dispatch(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("superadmin", "super123")));
        assertEquals(ResponseCode.SUCCESS, loginRes.getCode());
        String superToken = ((LoginResponse) loginRes.getBody()).getSessionToken();

        ResponseMessage<?> created = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, superToken,
                new RegisterRequest("admin2", "secret123", "管理员二", Role.ADMIN)));
        assertEquals(ResponseCode.SUCCESS, created.getCode());
        assertEquals(Role.ADMIN, ((AccountInfo) created.getBody()).getRole());
    }

    @Test
    public void subsystemAdminCannotRegisterAdministrator() {
        String adminToken = login(Role.ADMIN, "admin123");
        ResponseMessage<?> response = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, adminToken,
                new RegisterRequest("admin2", "secret123", "管理员二", Role.ADMIN)));
        assertEquals(ResponseCode.FORBIDDEN, response.getCode());
    }

    @Test
    public void unimplementedModuleOperationIsRejectedWithNotice() {
        String token = login(Role.STUDENT, "student123");
        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.COURSE_QUERY, token, null));
        assertEquals(ResponseCode.NOT_IMPLEMENTED, response.getCode());
        assertTrue(response.getMessage().contains("预留"));
    }
}
