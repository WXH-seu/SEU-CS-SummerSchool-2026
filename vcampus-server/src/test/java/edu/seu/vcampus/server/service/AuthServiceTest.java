package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.dto.UserImportResponse;
import edu.seu.vcampus.common.dto.UserOperationLog;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.server.dao.AccessOperationLogRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Exercises the account lifecycle, the super-admin-only account management,
 * the CSV batch-import channel and the audit log against a temporary database.
 */
public class AuthServiceTest {
    private static final Role ADMIN_ACTOR = Role.SUPER_ADMIN;
    private static final String OPERATOR = "superadmin";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private AuthService authService;
    private AuditService auditService;
    private SessionRegistry sessions;

    @Before
    public void setUp() throws Exception {
        PasswordHasher passwordHasher = new PasswordHasher();
        String db = new java.io.File(temporaryFolder.getRoot(), "auth-test.accdb").getAbsolutePath();
        AccessUserRepository repository = new AccessUserRepository(db, passwordHasher);
        auditService = new AuditService(new AccessOperationLogRepository(db));
        sessions = new SessionRegistry();
        authService = new AuthService(repository, passwordHasher, sessions, auditService);
    }

    @Test
    public void superAdminCreatesStudentAccount() throws Exception {
        AccountInfo created = authService.register(
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT),
                ADMIN_ACTOR, OPERATOR);
        assertNotNull(created);
        assertEquals("stu2026", created.getUserId());
        assertEquals(Role.STUDENT, created.getRole());
        assertTrue(created.isActive());
    }

    @Test
    public void rejectsDuplicateRegistration() throws Exception {
        RegisterRequest request =
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT);
        authService.register(request, ADMIN_ACTOR, OPERATOR);
        try {
            authService.register(request, ADMIN_ACTOR, OPERATOR);
            fail("duplicate registration must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.CONFLICT, e.getCode());
        }
    }

    @Test
    public void rejectsShortPasswordAndEmptyFields() throws Exception {
        try {
            authService.register(new RegisterRequest("u1", "123", "短密码", Role.STUDENT),
                    ADMIN_ACTOR, OPERATOR);
            fail("short password must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.INVALID_REQUEST, e.getCode());
        }
        try {
            authService.register(new RegisterRequest("  ", "secret123", "空账号", Role.STUDENT),
                    ADMIN_ACTOR, OPERATOR);
            fail("blank user id must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.INVALID_REQUEST, e.getCode());
        }
    }

    @Test
    public void adminAndTeacherCannotRegisterAccount() throws Exception {
        for (Role actor : new Role[]{Role.TEACHER, Role.ADMIN}) {
            try {
                authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT),
                        actor, "someone");
                fail(actor + " must not register accounts");
            } catch (AuthException e) {
                assertEquals(ResponseCode.FORBIDDEN, e.getCode());
            }
        }
    }

    @Test
    public void changesPasswordAndInvalidatesOldOne() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT),
                ADMIN_ACTOR, OPERATOR);
        authService.changePassword("stu2026", "secret123", "newpass456");
        assertNotNull(authService.login(new LoginRequest("stu2026", "newpass456")));
        try {
            authService.login(new LoginRequest("stu2026", "secret123"));
            fail("old password must no longer work");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
    }

    @Test
    public void deletesAccountAndRemovesSessions() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT),
                ADMIN_ACTOR, OPERATOR);
        LoginResponse session = authService.login(new LoginRequest("stu2026", "secret123"));
        authService.deleteAccount("stu2026", "secret123");
        assertNull(sessions.find(session.getSessionToken()));
        try {
            authService.login(new LoginRequest("stu2026", "secret123"));
            fail("deleted account must not log in");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
    }

    @Test
    public void updatesProfileAndListsUsers() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "旧名字", Role.STUDENT),
                ADMIN_ACTOR, OPERATOR);
        assertEquals("新名字",
                authService.updateProfile("stu2026", "新名字").getDisplayName());
        List<AccountInfo> users = authService.listUsers(Role.SUPER_ADMIN);
        assertTrue(users.size() >= 5);
    }

    @Test
    public void disablesAndEnablesAccount() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT),
                ADMIN_ACTOR, OPERATOR);
        authService.updateUserStatus("superadmin", "stu2026", false, Role.SUPER_ADMIN);
        try {
            authService.login(new LoginRequest("stu2026", "secret123"));
            fail("disabled account must not log in");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
        authService.updateUserStatus("superadmin", "stu2026", true, Role.SUPER_ADMIN);
        assertNotNull(authService.login(new LoginRequest("stu2026", "secret123")));
    }

    @Test
    public void superAdminCannotChangeOwnStatus() throws Exception {
        try {
            authService.updateUserStatus("superadmin", "superadmin", false, Role.SUPER_ADMIN);
            fail("changing own status must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.INVALID_REQUEST, e.getCode());
        }
    }

    @Test
    public void nonAdminCannotListOrManage() throws Exception {
        try {
            authService.listUsers(Role.STUDENT);
            fail("student must not list users");
        } catch (AuthException e) {
            assertEquals(ResponseCode.FORBIDDEN, e.getCode());
        }
        try {
            authService.updateUserStatus("admin", "stu2026", false, Role.ADMIN);
            fail("sub-system admin must not manage users");
        } catch (AuthException e) {
            assertEquals(ResponseCode.FORBIDDEN, e.getCode());
        }
    }

    @Test
    public void importsUsersFromCsvWithPerRowFailures() throws Exception {
        List<RegisterRequest> users = Arrays.asList(
                new RegisterRequest("s001", "secret123", "学生一", Role.STUDENT),
                new RegisterRequest("s002", "secret123", "学生二", Role.STUDENT),
                new RegisterRequest("s001", "secret123", "重复账号", Role.STUDENT),
                new RegisterRequest("s003", "12", "短密码", Role.STUDENT),
                new RegisterRequest("t001", "secret123", "教师一", Role.TEACHER));
        UserImportResponse response = authService.importUsers(
                new edu.seu.vcampus.common.dto.UserImportRequest(users), ADMIN_ACTOR, OPERATOR);
        assertEquals(3, response.getImported());
        assertEquals(2, response.getFailures().size());
        assertEquals("账号已存在", response.getFailures().get(0).getReason());
        assertNotNull(response.getFailures().get(1).getReason());
    }

    @Test
    public void replayingOldErrorsStayRejected() throws Exception {
        try {
            authService.login(new LoginRequest("nobody", "whatever1"));
            fail("unknown account must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
        assertFalse(authService.listUsers(Role.SUPER_ADMIN).isEmpty());
    }

    @Test
    public void recordsLoginAndOperationsAudit() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT),
                ADMIN_ACTOR, OPERATOR);
        try {
            authService.login(new LoginRequest("stu2026", "wrong"));
            fail("wrong password must fail");
        } catch (AuthException ignored) {
            // expected
        }
        authService.login(new LoginRequest("stu2026", "secret123"));

        List<UserOperationLog> logs = auditService.recent(50);
        boolean hasLoginFailed = false;
        boolean hasLoginSuccess = false;
        boolean hasRegister = false;
        for (UserOperationLog log : logs) {
            if ("LOGIN_FAILED".equals(log.getOperation()) && "stu2026".equals(log.getUserId())) {
                hasLoginFailed = true;
            }
            if ("LOGIN".equals(log.getOperation()) && log.isSuccess()) {
                hasLoginSuccess = true;
            }
            if ("REGISTER".equals(log.getOperation()) && "stu2026".equals(log.getTargetUserId())) {
                hasRegister = true;
            }
        }
        assertTrue(hasLoginFailed);
        assertTrue(hasLoginSuccess);
        assertTrue(hasRegister);
    }
}
