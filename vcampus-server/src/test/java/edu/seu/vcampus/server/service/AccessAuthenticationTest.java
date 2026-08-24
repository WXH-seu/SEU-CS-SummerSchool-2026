package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
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
    public void createsDatabaseAndAuthenticatesSeededStudent() throws Exception {
        File database = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        PasswordHasher passwordHasher = new PasswordHasher();
        AccessUserRepository repository =
                new AccessUserRepository(database.getAbsolutePath(), passwordHasher);
        AuthService authService =
                new AuthService(repository, passwordHasher, new SessionRegistry());

        LoginResponse response = authService.login(
                new LoginRequest("student", "student123"));

        assertTrue(database.isFile());
        assertNotNull(response);
        assertEquals("student", response.getUserId());
        assertEquals(Role.STUDENT, response.getRole());

        try {
            authService.login(new LoginRequest("student", "wrong-password"));
            fail("wrong password must be rejected");
        } catch (AuthException e) {
            assertEquals(ResponseCode.UNAUTHORIZED, e.getCode());
        }
    }
}
