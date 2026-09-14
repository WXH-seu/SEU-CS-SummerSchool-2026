package edu.seu.vcampus.server.network;

import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.dispatcher.RequestDispatcher;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles one long-lived client connection on a worker thread.
 *
 * <p>连接建立与断开时会在应用层输出 INFO 日志（对端地址、当前在线连接数、连接时长），
 * 便于在不依赖 netstat 的情况下观察“谁连过、同时在线几人”。
 */
public final class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final AtomicInteger activeConnections;

    public ClientHandler(Socket socket, RequestDispatcher dispatcher,
                         AtomicInteger activeConnections) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.activeConnections = activeConnections;
    }

    @Override
    public void run() {
        final String peer = peerOf(socket);
        final long startedAt = System.currentTimeMillis();
        final int online = activeConnections.incrementAndGet();
        LOGGER.info("[连接] 客户端接入 " + peer + "，当前在线 " + online + " 个连接");
        try (Socket client = socket;
             ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream())) {
            output.flush();
            try (ObjectInputStream input = new ObjectInputStream(client.getInputStream())) {
                handleRequests(input, output);
            }
        } catch (EOFException e) {
            LOGGER.fine("Client closed the connection: " + peer);
        } catch (SocketException e) {
            LOGGER.log(Level.FINE, "Client connection ended: " + peer, e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Client connection failed: " + peer, e);
        } finally {
            long seconds = (System.currentTimeMillis() - startedAt) / 1000L;
            int remaining = Math.max(0, activeConnections.decrementAndGet());
            LOGGER.info("[断开] 客户端离开 " + peer + "，连接时长 " + seconds
                    + " 秒，当前在线 " + remaining + " 个连接");
        }
    }

    /** 对端标识：地址:端口；无法获取时返回占位符。 */
    private static String peerOf(Socket socket) {
        if (socket == null || socket.getInetAddress() == null) {
            return "未知地址";
        }
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    private void handleRequests(ObjectInputStream input, ObjectOutputStream output)
            throws IOException {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Object incoming = input.readObject();
                ResponseMessage<?> response;
                if (incoming instanceof RequestMessage) {
                    response = dispatcher.dispatch((RequestMessage<?>) incoming);
                } else {
                    response = ResponseMessage.failure(null,
                            ResponseCode.INVALID_REQUEST, "不支持的消息类型");
                }
                output.writeObject(response);
                output.flush();
                output.reset();
            } catch (ClassNotFoundException e) {
                ResponseMessage<?> response = ResponseMessage.failure(null,
                        ResponseCode.INVALID_REQUEST, "无法识别消息内容");
                output.writeObject(response);
                output.flush();
            }
        }
    }
}
