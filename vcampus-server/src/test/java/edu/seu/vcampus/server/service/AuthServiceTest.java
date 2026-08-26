package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.dto.UserImportResponse;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
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

/** Exercises the account lifecycle, the admin-only registration policy and the
 *  CSV batch-import channel against a temporary Access database. */
public class AuthServiceTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private AuthService authService;
    private SessionRegistry sessions;

    @Before
    public void setUp() throws Exception {
        PasswordHasher passwordHasher = new PasswordHasher();
        AccessUserRepository repository = new AccessUserRepository(
                new java.io.File(temporaryFolder.getRoot(), "auth-test.accdb").getAbsolutePath(),
                passwordHasher);
        sessions = new SessionRegistry();
        authService = new AuthService(repository, passwordHasher, sessions);
    }

    @Test
    public void adminCreatesStudentAccount() throws Exception {
        AccountInfo created = authService.register(
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT), Role.ADMIN);

        assertNotNull(created);
        assertEquals("stu2026", created.getUserId());
        assertEquals(Role.STUDENT, created.getRole());
        assertTrue(created.isActive());
    }

    @Test
    public void rejectsDuplicateRegistration() throws Exception {
        RegisterRequest request =
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT);
        authService.register(request, Role.ADMIN);
        try {
            authService.register(request, Role.ADMIN);
            fail("duplicate registration must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.CONFLICT, e.getCode());
        }
    }

    @Test
    public void rejectsShortPasswordAndEmptyFields() throws Exception {
        try {
            authService.register(new RegisterRequest("u1", "123", "短密码", Role.STUDENT), Role.ADMIN);
            fail("short password must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.INVALID_REQUEST, e.getCode());
        }
        try {
            authService.register(new RegisterRequest("  ", "secret123", "空账号", Role.STUDENT), Role.ADMIN);
            fail("blank user id must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.INVALID_REQUEST, e.getCode());
        }
    }

    @Test
    public void nonAdminCannotRegisterAccount() throws Exception {
        try {
            authService.register(
                    new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT), Role.TEACHER);
            fail("teacher must not register accounts");
        } catch (AuthException e) {
            assertEquals(ResponseCode.FORBIDDEN, e.getCode());
        }
    }

    @Test
    public void changesPasswordAndInvalidatesOldOne() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT), Role.ADMIN);

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
        authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT), Role.ADMIN);
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
        authService.register(new RegisterRequest("stu2026", "secret123", "旧名字", Role.STUDENT), Role.ADMIN);

        assertEquals("新名字",
                authService.updateProfile("stu2026", "新名字").getDisplayName());

        List<AccountInfo> users = authService.listUsers(Role.ADMIN);
        assertTrue(users.size() >= 4);
        boolean registered = false;
        for (AccountInfo user : users) {
            if ("stu2026".equals(user.getUserId())) {
                registered = true;
            }
        }
        assertTrue(registered);
    }

    @Test
    public void disablesAndEnablesAccount() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT), Role.ADMIN);

        authService.updateUserStatus("admin", "stu2026", false, Role.ADMIN);
        try {
            authService.login(new LoginRequest("stu2026", "secret123"));
            fail("disabled account must not log in");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }

        authService.updateUserStatus("admin", "stu2026", true, Role.ADMIN);
        assertNotNull(authService.login(new LoginRequest("stu2026", "secret123")));
    }

    @Test
    public void adminCannotChangeOwnStatus() throws Exception {
        try {
            authService.updateUserStatus("admin", "admin", false, Role.ADMIN);
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
            authService.updateUserStatus("teacher", "stu2026", false, Role.TEACHER);
            fail("teacher must not manage users");
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
        UserImportResponse response =
                authService.importUsers(new edu.seu.vcampus.common.dto.UserImportRequest(users), Role.ADMIN);

        assertEquals(3, response.getImported());
        assertEquals(2, response.getFailures().size());
        assertEquals("账号已存在", response.getFailures().get(0).getReason());
        assertNotNull(response.getFailures().get(1).getReason());
    }

    @Test
    public void subsystemAdminCannotCreateAdministrator() throws Exception {
        try {
            authService.register(new RegisterRequest("admin2", "secret123", "管理员二", Role.ADMIN), Role.ADMIN);
            fail("sub-system admin must not create an admin");
        } catch (AuthException e) {
            assertEquals(ResponseCode.FORBIDDEN, e.getCode());
        }
        try {
            authService.register(new RegisterRequest("root2", "secret123", "超管二", Role.SUPER_ADMIN), Role.ADMIN);
            fail("sub-system admin must not create a super admin");
        } catch (AuthException e) {
            assertEquals(ResponseCode.FORBIDDEN, e.getCode());
        }
    }

    @Test
    public void superAdminCanCreateAdministrators() throws Exception {
        AccountInfo admin = authService.register(
                new RegisterRequest("admin2", "secret123", "管理员二", Role.ADMIN), Role.SUPER_ADMIN);
        assertEquals(Role.ADMIN, admin.getRole());

        AccountInfo superAdmin = authService.register(
                new RegisterRequest("root2", "secret123", "超管二", Role.SUPER_ADMIN), Role.SUPER_ADMIN);
        assertEquals(Role.SUPER_ADMIN, superAdmin.getRole());
    }

    @Test
    public void subsystemAdminCannotManageAdministrator() throws Exception {
        try {
            authService.updateUserStatus("admin", "superadmin", false, Role.ADMIN);
            fail("sub-system admin must not manage a super admin");
        } catch (AuthException e) {
            assertEquals(ResponseCode.FORBIDDEN, e.getCode());
        }
    }

    @Test
    public void replayingOldErrorsStayRejected() throws Exception {
        try {
            authService.login(new LoginRequest("nobody", "whatever1"));
            fail("unknown account must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
        assertFalse(authService.listUsers(Role.ADMIN).isEmpty());
    }
}
