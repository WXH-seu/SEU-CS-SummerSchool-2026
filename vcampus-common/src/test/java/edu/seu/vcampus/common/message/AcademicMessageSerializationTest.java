package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.CatalogQueryRequest;
import edu.seu.vcampus.common.dto.StudentProfileUpdateRequest;
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

    @Test
    public void serializesCatalogQueryRequest() throws Exception {
        CatalogQueryRequest query = new CatalogQueryRequest(
                "编译", "CS", "080901", true);
        RequestMessage<CatalogQueryRequest> request =
                new RequestMessage<CatalogQueryRequest>(
                        Operation.CATALOG_COURSE_QUERY, "session", query);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        CatalogQueryRequest restoredQuery = (CatalogQueryRequest) restored.getBody();

        assertEquals(Operation.CATALOG_COURSE_QUERY, restored.getOperation());
        assertEquals("080901", restoredQuery.getMajorId());
        assertEquals("编译", restoredQuery.getKeyword());
    }

    @Test
    public void serializesStudentProfileUpdate() throws Exception {
        StudentProfileUpdateRequest profile = new StudentProfileUpdateRequest(
                "女", "2008-01-02", "13900000000", "student@example.com");
        RequestMessage<StudentProfileUpdateRequest> request =
                new RequestMessage<StudentProfileUpdateRequest>(
                        Operation.STUDENT_PROFILE_UPDATE, "session", profile);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();
        assertEquals(Operation.STUDENT_PROFILE_UPDATE, restored.getOperation());
        assertEquals("13900000000",
                ((StudentProfileUpdateRequest) restored.getBody()).getPhone());
    }
}
