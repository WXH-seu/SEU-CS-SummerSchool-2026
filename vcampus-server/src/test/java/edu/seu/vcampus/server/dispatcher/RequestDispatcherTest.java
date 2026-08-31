package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.UserImportRequest;
import edu.seu.vcampus.common.dto.UserImportResponse;
import edu.seu.vcampus.common.dto.UserOperationLogResponse;
import edu.seu.vcampus.common.dto.UserStatusUpdateRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessOperationLogRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.security.PermissionPolicy;
import edu.seu.vcampus.server.service.AcademicService;
import edu.seu.vcampus.server.service.AuditService;
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
 * Verifies the dispatcher pipeline with the super-admin-only account management
 * policy: public operations, session requirement, role-based permission checks,
 * audit query and user-module responses.
 */
public class RequestDispatcherTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private RequestDispatcher dispatcher;

    @Before
    public void setUp() throws Exception {
        PasswordHasher passwordHasher = new PasswordHasher();
        String db = new java.io.File(temporaryFolder.getRoot(), "dispatcher-test.accdb")
                .getAbsolutePath();
        AccessDatabase database = new AccessDatabase(db);
        AccessUserRepository repository = new AccessUserRepository(database, passwordHasher);
        AcademicRequestHandler academicHandler = new AcademicRequestHandler(
                new AcademicService(new AccessAcademicRepository(database), repository));
        AuditService auditService = new AuditService(new AccessOperationLogRepository(db));
        SessionRegistry sessions = new SessionRegistry();
        AuthService authService = new AuthService(repository, passwordHasher, sessions, auditService);
        dispatcher = new RequestDispatcher(authService, sessions, new PermissionPolicy(),
                auditService, academicHandler, null, null);
    }

    private String login(String userId, String password) {
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
        String token = login("student", "student123");
        ResponseMessage<?> response = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, token,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.FORBIDDEN, response.getCode());
    }

    @Test
    public void adminCannotRegister() {
        String token = login("admin", "admin123");
        ResponseMessage<?> response = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, token,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.FORBIDDEN, response.getCode());
    }

    @Test
    public void superAdminRegistersThenUserLogsIn() {
        String superToken = login("superadmin", "super123");

        ResponseMessage<?> registered = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, superToken,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.SUCCESS, registered.getCode());
        AccountInfo created = (AccountInfo) registered.getBody();
        assertEquals("stu2026", created.getUserId());
        assertEquals(Role.STUDENT, created.getRole());

        ResponseMessage<?> duplicate = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, superToken,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.CONFLICT, duplicate.getCode());

        String studentToken = login("stu2026", "secret123");
        assertEquals(ResponseCode.SUCCESS, dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_ACCOUNT_QUERY, studentToken, null)).getCode());
    }

    @Test
    public void csvImportWorksForSuperAdmin() {
        String superToken = login("superadmin", "super123");
        UserImportRequest payload = new UserImportRequest(Arrays.asList(
                new RegisterRequest("s001", "secret123", "学生一", Role.STUDENT),
                new RegisterRequest("t001", "secret123", "教师一", Role.TEACHER)));
        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<UserImportRequest>(Operation.USER_IMPORT_CSV, superToken, payload));
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
        String token = login("student", "student123");
        ResponseMessage<?> forbidden = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_LIST_QUERY, token, null));
        assertEquals(ResponseCode.FORBIDDEN, forbidden.getCode());

        String superToken = login("superadmin", "super123");
        ResponseMessage<?> allowed = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_LIST_QUERY, superToken, null));
        assertEquals(ResponseCode.SUCCESS, allowed.getCode());
        assertNotNull(allowed.getBody());
    }

    @Test
    public void superAdminCanDisableStudentAccount() {
        String superToken = login("superadmin", "super123");
        ResponseMessage<?> result = dispatcher.dispatch(
                new RequestMessage<UserStatusUpdateRequest>(Operation.USER_STATUS_UPDATE, superToken,
                        new UserStatusUpdateRequest("student", false)));
        assertEquals(ResponseCode.SUCCESS, result.getCode());

        ResponseMessage<?> disabledLogin = dispatcher.dispatch(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("student", "student123")));
        assertEquals(ResponseCode.UNAUTHORIZED, disabledLogin.getCode());
    }

    @Test
    public void superAdminCanCreateAdministratorOverProtocol() {
        String superToken = login("superadmin", "super123");
        ResponseMessage<?> created = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, superToken,
                new RegisterRequest("admin2", "secret123", "管理员二", Role.SUBSYSADMIN,
                        java.util.Collections.singleton("student"))));
        assertEquals(ResponseCode.SUCCESS, created.getCode());
        assertEquals(Role.SUBSYSADMIN, ((AccountInfo) created.getBody()).getRole());
        assertTrue(((AccountInfo) created.getBody()).getAdminScopes().contains("student"));
    }

    @Test
    public void subsystemAdminCannotRegisterAdministrator() {
        String adminToken = login("admin", "admin123");
        ResponseMessage<?> response = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, adminToken,
                new RegisterRequest("admin2", "secret123", "管理员二", Role.SUBSYSADMIN)));
        assertEquals(ResponseCode.FORBIDDEN, response.getCode());
    }

    @Test
    public void scopedSubsystemAdminKeepsUsageButNotManagementOutsideScope() {
        String superToken = login("superadmin", "super123");
        dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, superToken,
                new RegisterRequest("adminLib", "secret123", "图书馆管理员", Role.SUBSYSADMIN,
                        java.util.Collections.singleton("library"))));

        String adminLibToken = login("adminLib", "secret123");

        // Out of scope management: saving a student record is admin-only and the
        // library administrator is not granted the student sub-system.
        ResponseMessage<?> studentSave = dispatcher.dispatch(new RequestMessage<RegisterRequest>(
                Operation.STUDENT_SAVE, adminLibToken, null));
        assertEquals(ResponseCode.FORBIDDEN, studentSave.getCode());

        // Out of scope usage: the administrator keeps ordinary (teacher) usage
        // rights, so it can borrow a book; the stub module reports it as
        // not implemented after passing the permission check.
        ResponseMessage<?> borrow = dispatcher.dispatch(new RequestMessage<Serializable>(
                Operation.LIBRARY_BORROW, adminLibToken, null));
        assertEquals(ResponseCode.NOT_IMPLEMENTED, borrow.getCode());
    }

    @Test
    public void studentQueriesOnlyOwnAcademicRecordAndCannotModifyIt() {
        String token = login("student", "student123");
        ResponseMessage<?> query = dispatcher.dispatch(
                new RequestMessage<AcademicQueryRequest>(Operation.STUDENT_QUERY, token,
                        new AcademicQueryRequest("其他学生", null, null, false)));
        assertEquals(ResponseCode.SUCCESS, query.getCode());
        List<?> rows = (List<?>) query.getBody();
        assertEquals(1, rows.size());
        assertEquals("20260001", ((StudentDto) rows.get(0)).getStudentId());

        ResponseMessage<?> save = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.STUDENT_SAVE, token, null));
        assertEquals(ResponseCode.FORBIDDEN, save.getCode());
    }

    @Test
    public void superAdminCanQueryAuditLog() {
        String superToken = login("superadmin", "super123");
        login("student", "student123");
        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.USER_AUDIT_QUERY, superToken, null));
        assertEquals(ResponseCode.SUCCESS, response.getCode());
        assertNotNull(((UserOperationLogResponse) response.getBody()).getLogs());
    }

    @Test
    public void unimplementedModuleOperationIsRejectedWithNotice() {
        String token = login("student", "student123");
        ResponseMessage<?> response = dispatcher.dispatch(
                new RequestMessage<Serializable>(Operation.STORE_ORDER_CREATE, token, null));
        assertEquals(ResponseCode.NOT_IMPLEMENTED, response.getCode());
        assertTrue(response.getMessage().contains("预留"));
    }
}
