package edu.seu.vcampus.server.network;

import edu.seu.vcampus.server.dispatcher.RequestDispatcher;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Multi-client TCP server backed by a bounded worker pool. */
public final class VCampusServer implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(VCampusServer.class.getName());

    private final int port;
    private final RequestDispatcher dispatcher;
    private final ExecutorService workers;

    /** 当前在线连接数，供连接/断开日志与运维观测使用。 */
    private final AtomicInteger activeConnections = new AtomicInteger();

    private volatile boolean running;
    private ServerSocket serverSocket;

    public VCampusServer(int port, int workerThreads, RequestDispatcher dispatcher) {
        this.port = port;
        this.dispatcher = dispatcher;
        this.workers = Executors.newFixedThreadPool(workerThreads);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        LOGGER.info("vCampus server started on port " + getLocalPort());
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                workers.submit(new ClientHandler(socket, dispatcher, activeConnections));
            } catch (IOException e) {
                if (running) {
                    throw e;
                }
            }
        }
    }

    /** Returns the actual listening port, useful when port 0 was requested. */
    public int getLocalPort() {
        return serverSocket == null ? port : serverSocket.getLocalPort();
    }

    /** Returns how many client connections are currently held open. */
    public int getActiveConnections() {
        return activeConnections.get();
    }

    @Override
    public void close() throws IOException {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        LOGGER.info("vCampus server stopping, active connections: " + getActiveConnections());
        workers.shutdownNow();
    }
}
