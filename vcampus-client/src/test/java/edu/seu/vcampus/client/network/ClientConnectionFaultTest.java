package edu.seu.vcampus.client.network;

import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.message.RequestMessage;
import org.junit.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Client-side fault handling: an unavailable server is reported quickly, an
 * unexpected response object is rejected, and a silent server triggers the
 * configured read timeout instead of hanging forever.
 */
public class ClientConnectionFaultTest {
    @Test
    public void unreachableServerFailsFast() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        try {
            ClientConnection.connect("127.0.0.1", closedPort);
            fail("Connecting to a closed port should fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    @Test(timeout = 30000)
    public void unexpectedResponseObjectIsRejected() throws Exception {
        ServerSocket stub = new ServerSocket(0);
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (Socket socket = stub.accept();
                     ObjectOutputStream output =
                             new ObjectOutputStream(socket.getOutputStream())) {
                    output.flush();
                    try (ObjectInputStream input =
                                 new ObjectInputStream(socket.getInputStream())) {
                        input.readObject();
                        output.writeObject("not-a-response");
                        output.flush();
                    }
                } catch (Exception ignored) {
                    // Client closes the connection after reporting the failure.
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        try {
            ClientConnection connection =
                    ClientConnection.connect("127.0.0.1", stub.getLocalPort());
            try {
                connection.request(new RequestMessage<Serializable>(
                        Operation.PING, null, null));
                fail("Client should reject a non-response object");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("不支持"));
            } finally {
                connection.close();
            }
        } finally {
            stub.close();
        }
    }

    @Test(timeout = 40000)
    public void silentServerTriggersReadTimeout() throws Exception {
        ServerSocket stub = new ServerSocket(0);
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (Socket socket = stub.accept();
                     ObjectOutputStream output =
                             new ObjectOutputStream(socket.getOutputStream())) {
                    output.flush();
                    try (ObjectInputStream input =
                                 new ObjectInputStream(socket.getInputStream())) {
                        input.readObject();
                        Thread.sleep(25000);
                    }
                } catch (Exception ignored) {
                    // Stub just keeps the connection open without answering.
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        try {
            ClientConnection connection =
                    ClientConnection.connect("127.0.0.1", stub.getLocalPort());
            long started = System.currentTimeMillis();
            try {
                connection.request(new RequestMessage<Serializable>(
                        Operation.PING, null, null));
                fail("Silent server should trigger the read timeout");
            } catch (IOException expected) {
                long elapsed = System.currentTimeMillis() - started;
                assertTrue("timeout should follow the configured 15s window",
                        elapsed >= 14000);
            } finally {
                connection.close();
            }
        } finally {
            stub.close();
        }
    }
}
