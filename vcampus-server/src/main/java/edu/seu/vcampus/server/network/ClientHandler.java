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
import java.util.logging.Level;
import java.util.logging.Logger;

/** Handles one long-lived client connection on a worker thread. */
public final class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final Socket socket;
    private final RequestDispatcher dispatcher;

    public ClientHandler(Socket socket, RequestDispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;
    }

    @Override
    public void run() {
        try (Socket client = socket;
             ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream())) {
            output.flush();
            try (ObjectInputStream input = new ObjectInputStream(client.getInputStream())) {
                handleRequests(input, output);
            }
        } catch (EOFException e) {
            LOGGER.fine("Client closed the connection");
        } catch (SocketException e) {
            LOGGER.log(Level.FINE, "Client connection ended", e);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Client connection failed", e);
        }
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
