package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises the full account lifecycle against a temporary Access database. */
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
    public void registersAndSignsInNewUser() throws Exception {
        LoginResponse response = authService.register(
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT));

        assertNotNull(response);
        assertEquals("stu2026", response.getUserId());
        assertEquals(Role.STUDENT, response.getRole());
        assertNotNull(sessions.find(response.getSessionToken()));
    }

    @Test
    public void rejectsDuplicateRegistration() throws Exception {
        RegisterRequest request =
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT);
        authService.register(request);
        try {
            authService.register(request);
            fail("duplicate registration must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.CONFLICT, e.getCode());
        }
    }

    @Test
    public void rejectsShortPasswordAndEmptyFields() throws Exception {
        try {
            authService.register(new RegisterRequest("u1", "123", "短密码", Role.STUDENT));
            fail("short password must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.INVALID_REQUEST, e.getCode());
        }
        try {
            authService.register(new RegisterRequest("  ", "secret123", "空账号", Role.STUDENT));
            fail("blank user id must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.INVALID_REQUEST, e.getCode());
        }
    }

    @Test
    public void changesPasswordAndInvalidatesOldOne() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT));

        authService.changePassword("stu2026", "secret123", "newpass456");

        LoginResponse withNew = authService.login(new LoginRequest("stu2026", "newpass456"));
        assertNotNull(withNew);
        try {
            authService.login(new LoginRequest("stu2026", "secret123"));
            fail("old password must no longer work");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
        try {
            authService.changePassword("stu2026", "wrong-old", "whatever1");
            fail("wrong old password must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
    }

    @Test
    public void deletesAccountAndRemovesSessions() throws Exception {
        LoginResponse session = authService.register(
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT));

        authService.deleteAccount("stu2026", "secret123");

        assertNull(sessions.find(session.getSessionToken()));
        try {
            authService.login(new LoginRequest("stu2026", "secret123"));
            fail("deleted account must not log in");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
        try {
            authService.deleteAccount("stu2026", "secret123");
            fail("deleting an unknown account must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.NOT_FOUND, e.getCode());
        }
    }

    @Test
    public void updatesProfileAndListsUsers() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "旧名字", Role.STUDENT));

        AccountInfo updated = authService.updateProfile("stu2026", "新名字");
        assertEquals("新名字", updated.getDisplayName());
        assertEquals("新名字", authService.getAccount("stu2026").getDisplayName());

        List<AccountInfo> users = authService.listUsers();
        assertTrue(users.size() >= 4);
        boolean seededAdmin = false;
        boolean registered = false;
        for (AccountInfo user : users) {
            if ("admin".equals(user.getUserId())) {
                seededAdmin = true;
            }
            if ("stu2026".equals(user.getUserId())) {
                registered = true;
            }
        }
        assertTrue(seededAdmin);
        assertTrue(registered);
    }

    @Test
    public void disablesAndEnablesAccount() throws Exception {
        authService.register(new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT));

        authService.updateUserStatus("admin", "stu2026", false);
        try {
            authService.login(new LoginRequest("stu2026", "secret123"));
            fail("disabled account must not log in");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }

        authService.updateUserStatus("admin", "stu2026", true);
        assertNotNull(authService.login(new LoginRequest("stu2026", "secret123")));
    }

    @Test
    public void adminCannotChangeOwnStatus() throws Exception {
        try {
            authService.updateUserStatus("admin", "admin", false);
            fail("changing own status must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.INVALID_REQUEST, e.getCode());
        }
    }

    @Test
    public void rejectsDisabledAndMissingAccountsOnLogin() throws Exception {
        try {
            authService.login(new LoginRequest("nobody", "whatever1"));
            fail("unknown account must fail");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
        assertFalse(authService.listUsers().isEmpty());
    }
}
