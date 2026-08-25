package edu.seu.vcampus.client.service;

import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import org.junit.Assume;
import org.junit.Test;

import java.io.Serializable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Optional live-server smoke test for login and academic queries. */
public class NetworkAcademicSmokeTest {
    @Test
    public void logsInAndQueriesStudentOverSocket() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("vcampus.network.test"));
        ClientConnection connection = ClientConnection.connect("127.0.0.1", 4444);
        try {
            ResponseMessage<?> login = connection.request(
                    new RequestMessage<LoginRequest>(Operation.USER_LOGIN, null,
                            new LoginRequest("student", "student123")));
            assertTrue(login.isSuccess());
            LoginResponse session = (LoginResponse) login.getBody();
            AcademicClientService service =
                    new AcademicClientService(connection, session.getSessionToken());
            assertFalse(service.queryStudents(null).isEmpty());
            connection.request(new RequestMessage<Serializable>(
                    Operation.USER_LOGOUT, session.getSessionToken(), null));
        } finally {
            connection.close();
        }
    }
}
