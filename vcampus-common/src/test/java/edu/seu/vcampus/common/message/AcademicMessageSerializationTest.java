package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.enums.Operation;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;

/** Ensures academic DTOs remain compatible with the object-stream protocol. */
public class AcademicMessageSerializationTest {
    @Test
    public void serializesStudentSaveRequest() throws Exception {
        StudentDto student = new StudentDto("20260002", null, "测试学生", "女",
                "2008-02-03", "CS", "CS2026-01", 2026, "在读", null, null);
        RequestMessage<StudentDto> request = new RequestMessage<StudentDto>(
                Operation.STUDENT_SAVE, "session", student);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();

        assertEquals(Operation.STUDENT_SAVE, restored.getOperation());
        assertEquals("20260002", ((StudentDto) restored.getBody()).getStudentId());
    }
}
