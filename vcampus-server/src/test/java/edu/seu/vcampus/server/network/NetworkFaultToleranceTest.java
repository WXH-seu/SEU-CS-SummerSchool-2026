package edu.seu.vcampus.server.network;

import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.dao.AccessOperationLogRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.dispatcher.RequestDispatcher;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.security.PermissionPolicy;
import edu.seu.vcampus.server.service.AuditService;
import edu.seu.vcampus.server.service.AuthService;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Network fault-tolerance tests against a real in-process server: invalid
 * messages, abrupt client disconnects, session handling and parallel clients
 * must never take the server down.
 */
public class NetworkFaultToleranceTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private VCampusServer server;
    private Thread serverThread;
    private int port;

    @Before
    public void setUp() throws Exception {
        File databaseFile = new File(temporaryFolder.getRoot(), "fault.accdb");
        String databasePath = databaseFile.getAbsolutePath();
        PasswordHasher hasher = new PasswordHasher();
        AccessDatabase database = new AccessDatabase(databasePath);
        AccessUserRepository users = new AccessUserRepository(database, hasher);
        AuditService auditService =
                new AuditService(new AccessOperationLogRepository(databasePath));
        SessionRegistry sessions = new SessionRegistry();
        AuthService authService =
                new AuthService(users, hasher, sessions, auditService);
        RequestDispatcher dispatcher = new RequestDispatcher(
                authService, sessions, new PermissionPolicy(), auditService);
        port = freePort();
        server = new VCampusServer(port, 4, dispatcher);
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.start();
                } catch (IOException ignored) {
                    // Server socket closed during shutdown.
                }
            }
        }, "vcampus-fault-test-server");
        serverThread.setDaemon(true);
        serverThread.start();
        waitUntilAccepting();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void pingOverSocketSucceeds() throws Exception {
        Object[] streams = openStreams();
        try {
            ResponseMessage<?> response = roundTrip(streams,
                    new RequestMessage<Serializable>(Operation.PING, null, null));
            assertEquals(ResponseCode.SUCCESS, response.getCode());
        } finally {
            close(streams);
        }
    }

    @Test
    public void invalidMessageIsRejectedWithoutStoppingServer() throws Exception {
        Object[] streams = openStreams();
        try {
            ((ObjectOutputStream) streams[1]).writeObject("not-a-request");
            ((ObjectOutputStream) streams[1]).flush();
            Object incoming = ((ObjectInputStream) streams[2]).readObject();
            assertTrue(incoming instanceof ResponseMessage);
            assertEquals(ResponseCode.INVALID_REQUEST,
                    ((ResponseMessage<?>) incoming).getCode());
        } finally {
            close(streams);
        }

        Object[] again = openStreams();
        try {
            assertEquals(ResponseCode.SUCCESS, roundTrip(again,
                    new RequestMessage<Serializable>(Operation.PING, null, null)).getCode());
        } finally {
            close(again);
        }
    }

    @Test
    public void protectedOperationWithoutSessionIsUnauthorized() throws Exception {
        Object[] streams = openStreams();
        try {
            ResponseMessage<?> response = roundTrip(streams,
                    new RequestMessage<Serializable>(Operation.COURSE_QUERY, null, null));
            assertEquals(ResponseCode.UNAUTHORIZED, response.getCode());
        } finally {
            close(streams);
        }
    }

    @Test
    public void loginThenProtectedOperationPassesSessionCheck() throws Exception {
        Object[] streams = openStreams();
        try {
            ResponseMessage<?> login = roundTrip(streams,
                    new RequestMessage<LoginRequest>(Operation.USER_LOGIN, null,
                            new LoginRequest("student", "student123")));
            assertEquals(ResponseCode.SUCCESS, login.getCode());
            String token = ((LoginResponse) login.getBody()).getSessionToken();
            ResponseMessage<?> query = roundTrip(streams,
                    new RequestMessage<Serializable>(Operation.COURSE_QUERY, token, null));
            // The in-process dispatcher wires no business handlers, so a valid
            // session reaches the "not implemented yet" branch instead of
            // UNAUTHORIZED, which proves the session was accepted.
            assertEquals(ResponseCode.NOT_IMPLEMENTED, query.getCode());
        } finally {
            close(streams);
        }
    }

    @Test
    public void abruptDisconnectDoesNotStopOtherClients() throws Exception {
        Object[] dropped = openStreams();
        ((Socket) dropped[0]).close();

        Object[] streams = openStreams();
        try {
            assertEquals(ResponseCode.SUCCESS, roundTrip(streams,
                    new RequestMessage<Serializable>(Operation.PING, null, null)).getCode());
        } finally {
            close(streams);
        }
    }

    @Test
    public void parallelClientsAreServedConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Callable<ResponseCode> ping = new Callable<ResponseCode>() {
                @Override
                public ResponseCode call() throws Exception {
                    Object[] streams = openStreams();
                    try {
                        return roundTrip(streams,
                                new RequestMessage<Serializable>(
                                        Operation.PING, null, null)).getCode();
                    } finally {
                        close(streams);
                    }
                }
            };
            Future<ResponseCode> first = pool.submit(ping);
            Future<ResponseCode> second = pool.submit(ping);
            Future<ResponseCode> third = pool.submit(ping);
            assertEquals(ResponseCode.SUCCESS, first.get());
            assertEquals(ResponseCode.SUCCESS, second.get());
            assertEquals(ResponseCode.SUCCESS, third.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private Object[] openStreams() throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setTcpNoDelay(true);
        ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
        output.flush();
        ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
        return new Object[]{socket, output, input};
    }

    private ResponseMessage<?> roundTrip(Object[] streams, RequestMessage<?> request)
            throws IOException, ClassNotFoundException {
        ObjectOutputStream output = (ObjectOutputStream) streams[1];
        ObjectInputStream input = (ObjectInputStream) streams[2];
        output.writeObject(request);
        output.flush();
        output.reset();
        Object incoming = input.readObject();
        assertTrue(incoming instanceof ResponseMessage);
        return (ResponseMessage<?>) incoming;
    }

    private void close(Object[] streams) {
        try {
            ((Socket) streams[0]).close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    private void waitUntilAccepting() throws Exception {
        for (int i = 0; i < 40; i++) {
            try (Socket probe = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(250);
            }
        }
        throw new IllegalStateException("server did not start on port " + port);
    }

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }
}
