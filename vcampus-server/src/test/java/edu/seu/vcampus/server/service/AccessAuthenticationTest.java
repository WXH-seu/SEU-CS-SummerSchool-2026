package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.server.dao.AccessOperationLogRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Verifies Access bootstrap and authentication together. */
public class AccessAuthenticationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsDatabaseAndAuthenticatesAllSeededPeople() throws Exception {
        File database = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        PasswordHasher passwordHasher = new PasswordHasher();
        AccessUserRepository repository =
                new AccessUserRepository(database.getAbsolutePath(), passwordHasher);
        AuditService auditService =
                new AuditService(new AccessOperationLogRepository(database.getAbsolutePath()));
        AuthService authService =
                new AuthService(repository, passwordHasher, new SessionRegistry(), auditService);

        assertTrue(database.isFile());
        assertEquals(11, repository.findAll().size());
        assertDemoLogin(authService, "student", "student123", Role.STUDENT);
        for (int i = 2; i <= 6; i++) {
            assertDemoLogin(authService, "student0" + i, "student123", Role.STUDENT);
        }
        assertDemoLogin(authService, "teacher", "teacher123", Role.TEACHER);
        assertDemoLogin(authService, "teacher02", "teacher123", Role.TEACHER);
        assertDemoLogin(authService, "teacher03", "teacher123", Role.TEACHER);

        try {
            authService.login(new LoginRequest("student", "wrong-password"));
            fail("wrong password must be rejected");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
    }

    private void assertDemoLogin(AuthService authService, String userId, String password,
                                 Role role) throws Exception {
        LoginResponse response = authService.login(new LoginRequest(userId, password));
        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(role, response.getRole());
    }
}
