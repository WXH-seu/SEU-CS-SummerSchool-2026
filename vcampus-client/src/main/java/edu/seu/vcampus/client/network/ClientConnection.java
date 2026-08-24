package edu.seu.vcampus.client.network;

import edu.seu.vcampus.common.config.ProtocolConstants;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Synchronized long-lived connection used by the Swing client. */
public final class ClientConnection implements Closeable {
    private final Socket socket;
    private final ObjectOutputStream output;
    private final ObjectInputStream input;

    private ClientConnection(Socket socket, ObjectOutputStream output,
                             ObjectInputStream input) {
        this.socket = socket;
        this.output = output;
        this.input = input;
    }

    public static ClientConnection connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port),
                    ProtocolConstants.CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(ProtocolConstants.SOCKET_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
            return new ClientConnection(socket, output, input);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Keep the original connection failure.
            }
            throw e;
        }
    }

    public synchronized ResponseMessage<?> request(RequestMessage<? extends Serializable> request)
            throws IOException {
        output.writeObject(request);
        output.flush();
        output.reset();
        try {
            Object incoming = input.readObject();
            if (!(incoming instanceof ResponseMessage)) {
                throw new IOException("服务器返回了不支持的消息类型");
            }
            ResponseMessage<?> response = (ResponseMessage<?>) incoming;
            if (response.getRequestId() != null
                    && !request.getRequestId().equals(response.getRequestId())) {
                throw new IOException("服务器响应与请求不匹配");
            }
            return response;
        } catch (ClassNotFoundException e) {
            throw new IOException("无法解析服务器响应", e);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
