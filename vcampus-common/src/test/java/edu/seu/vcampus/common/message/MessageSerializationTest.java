package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.enums.Operation;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;

public class MessageSerializationTest {
    @Test
    public void requestCanRoundTripThroughObjectStream() throws Exception {
        RequestMessage<LoginRequest> request = new RequestMessage<LoginRequest>(
                Operation.USER_LOGIN, null, new LoginRequest("admin", "admin123"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes);
        output.writeObject(request);
        output.flush();

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();

        assertEquals(request.getRequestId(), restored.getRequestId());
        assertEquals(Operation.USER_LOGIN, restored.getOperation());
    }
}
