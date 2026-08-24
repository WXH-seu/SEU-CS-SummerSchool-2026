package edu.seu.vcampus.server.network;

import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.PasswordChangeRequest;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.dto.UserListResponse;
import edu.seu.vcampus.common.dto.UserStatusUpdateRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.dispatcher.RequestDispatcher;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.security.PermissionPolicy;
import edu.seu.vcampus.server.service.AuthService;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end test that starts a real {@link VCampusServer} over a temporary
 * Access database and drives the user module through the object-stream
 * protocol: register, query, change password, logout, login again, permission
 * denial, administrator management and deregistration.
 */
public class UserModuleIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private VCampusServer server;
    private int port;

    @Before
    public void startServer() throws Exception {
        PasswordHasher passwordHasher = new PasswordHasher();
        AccessUserRepository repository = new AccessUserRepository(
                new java.io.File(temporaryFolder.getRoot(), "integration.accdb")
                        .getAbsolutePath(), passwordHasher);
        SessionRegistry sessions = new SessionRegistry();
        AuthService authService = new AuthService(repository, passwordHasher, sessions);
        RequestDispatcher dispatcher =
                new RequestDispatcher(authService, sessions, new PermissionPolicy());
        server = new VCampusServer(0, 4, dispatcher);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.start();
                } catch (IOException e) {
                    throw new IllegalStateException("server failed to start", e);
                }
            }
        }, "integration-server");
        thread.setDaemon(true);
        thread.start();
        waitUntilAccepting();
    }

    @After
    public void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    private void waitUntilAccepting() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress("127.0.0.1", server.getLocalPort()), 200);
                port = server.getLocalPort();
                return;
            } catch (IOException ignored) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("server did not start listening in time");
    }

    @Test
    public void fullUserLifecycleOverTheWire() throws Exception {
        TestClient client = connect();

        // 1. Register a new student and get a session automatically.
        ResponseMessage<?> registered = client.request(new RequestMessage<RegisterRequest>(
                Operation.USER_REGISTER, null,
                new RegisterRequest("stu2026", "secret123", "新同学", Role.STUDENT)));
        assertEquals(ResponseCode.SUCCESS, registered.getCode());
        LoginResponse session = (LoginResponse) registered.getBody();
        String token = session.getSessionToken();

        // 2. Query own account.
        ResponseMessage<?> query = client.request(
                new RequestMessage<Serializable>(Operation.USER_ACCOUNT_QUERY, token, null));
        assertEquals(ResponseCode.SUCCESS, query.getCode());
        AccountInfo info = (AccountInfo) query.getBody();
        assertEquals("stu2026", info.getUserId());
        assertEquals(Role.STUDENT, info.getRole());

        // 3. Change password and log out.
        ResponseMessage<?> changed = client.request(new RequestMessage<PasswordChangeRequest>(
                Operation.USER_PASSWORD_CHANGE, token,
                new PasswordChangeRequest("secret123", "newpass456")));
        assertEquals(ResponseCode.SUCCESS, changed.getCode());
        assertEquals(ResponseCode.SUCCESS, client.request(
                new RequestMessage<Serializable>(Operation.USER_LOGOUT, token, null)).getCode());
        client.close();

        // 4. Old password is rejected, new password logs in.
        TestClient second = connect();
        ResponseMessage<?> oldLogin = second.request(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("stu2026", "secret123")));
        assertEquals(ResponseCode.UNAUTHORIZED, oldLogin.getCode());
        ResponseMessage<?> newLogin = second.request(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("stu2026", "newpass456")));
        assertEquals(ResponseCode.SUCCESS, newLogin.getCode());
        String studentToken = ((LoginResponse) newLogin.getBody()).getSessionToken();

        // 5. A student is forbidden from the administrator user list.
        ResponseMessage<?> forbidden = second.request(
                new RequestMessage<Serializable>(Operation.USER_LIST_QUERY, studentToken, null));
        assertEquals(ResponseCode.FORBIDDEN, forbidden.getCode());
        second.close();

        // 6. Administrator lists users and disables the student.
        TestClient adminClient = connect();
        ResponseMessage<?> adminLogin = adminClient.request(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("admin", "admin123")));
        assertEquals(ResponseCode.SUCCESS, adminLogin.getCode());
        String adminToken = ((LoginResponse) adminLogin.getBody()).getSessionToken();

        ResponseMessage<?> list = adminClient.request(
                new RequestMessage<Serializable>(Operation.USER_LIST_QUERY, adminToken, null));
        assertEquals(ResponseCode.SUCCESS, list.getCode());
        List<AccountInfo> users = ((UserListResponse) list.getBody()).getUsers();
        boolean containsNewUser = false;
        for (AccountInfo user : users) {
            if ("stu2026".equals(user.getUserId())) {
                containsNewUser = true;
            }
        }
        assertTrue(containsNewUser);

        ResponseMessage<?> disabled = adminClient.request(
                new RequestMessage<UserStatusUpdateRequest>(Operation.USER_STATUS_UPDATE,
                        adminToken, new UserStatusUpdateRequest("stu2026", false)));
        assertEquals(ResponseCode.SUCCESS, disabled.getCode());

        ResponseMessage<?> blockedLogin = adminClient.request(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("stu2026", "newpass456")));
        assertEquals(ResponseCode.UNAUTHORIZED, blockedLogin.getCode());

        // 7. Re-enable and deregister the account with its own password.
        assertEquals(ResponseCode.SUCCESS, adminClient.request(
                new RequestMessage<UserStatusUpdateRequest>(Operation.USER_STATUS_UPDATE,
                        adminToken, new UserStatusUpdateRequest("stu2026", true))).getCode());
        ResponseMessage<?> reLogin = adminClient.request(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("stu2026", "newpass456")));
        assertEquals(ResponseCode.SUCCESS, reLogin.getCode());
        String doomedToken = ((LoginResponse) reLogin.getBody()).getSessionToken();

        ResponseMessage<?> deleted = adminClient.request(
                new RequestMessage<edu.seu.vcampus.common.dto.DeleteAccountRequest>(
                        Operation.USER_DELETE, doomedToken,
                        new edu.seu.vcampus.common.dto.DeleteAccountRequest("newpass456")));
        assertEquals(ResponseCode.SUCCESS, deleted.getCode());

        ResponseMessage<?> gone = adminClient.request(new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("stu2026", "newpass456")));
        assertEquals(ResponseCode.UNAUTHORIZED, gone.getCode());
        adminClient.close();
    }

    @Test
    public void sessionTokenIsRequiredForProtectedOperations() throws Exception {
        TestClient client = connect();
        ResponseMessage<?> response = client.request(
                new RequestMessage<Serializable>(Operation.USER_ACCOUNT_QUERY, null, null));
        assertEquals(ResponseCode.UNAUTHORIZED, response.getCode());
        assertNotNull(response.getMessage());
        client.close();
    }

    private TestClient connect() throws IOException {
        return new TestClient("127.0.0.1", port);
    }

    /** Minimal protocol client used to drive the integration scenario. */
    private static final class TestClient implements Closeable {
        private final Socket socket;
        private final ObjectOutputStream output;
        private final ObjectInputStream input;

        private TestClient(String host, int port) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
        }

        private ResponseMessage<?> request(RequestMessage<?> request) throws IOException {
            output.writeObject(request);
            output.flush();
            output.reset();
            try {
                Object incoming = input.readObject();
                if (!(incoming instanceof ResponseMessage)) {
                    throw new IOException("unexpected response type");
                }
                return (ResponseMessage<?>) incoming;
            } catch (ClassNotFoundException e) {
                throw new IOException("cannot read response", e);
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
