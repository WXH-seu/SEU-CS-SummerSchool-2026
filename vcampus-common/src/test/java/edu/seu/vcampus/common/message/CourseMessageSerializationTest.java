package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseSelectRequest;
import edu.seu.vcampus.common.enums.Operation;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;

/** Ensures course DTOs remain compatible with the object-stream protocol. */
public class CourseMessageSerializationTest {
    @Test
    public void serializesCourseSelectRequest() throws Exception {
        CourseSelectRequest select = new CourseSelectRequest("CS101");
        RequestMessage<CourseSelectRequest> request = new RequestMessage<CourseSelectRequest>(
                Operation.COURSE_SELECT, "session", select);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();

        assertEquals(Operation.COURSE_SELECT, restored.getOperation());
        assertEquals("CS101", ((CourseSelectRequest) restored.getBody()).getCourseId());
    }

    @Test
    public void serializesCourseCatalogResponse() throws Exception {
        CourseDto course = new CourseDto("CS101", "Java 程序设计", "T0001", "演示教师",
                "CS", "计算机科学与工程学院", 3.0, 30, 1,
                "2026-2027-1", "周一 3-4 节", "教1-101", "Java 基础", true);
        ResponseMessage<CourseDto> response =
                ResponseMessage.success("req-1", "操作成功", course);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(response);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        ResponseMessage<?> restored = (ResponseMessage<?>) input.readObject();

        assertEquals("req-1", restored.getRequestId());
        CourseDto restoredCourse = (CourseDto) restored.getBody();
        assertEquals("CS101", restoredCourse.getCourseId());
        assertEquals(30, restoredCourse.getCapacity());
        assertEquals(1, restoredCourse.getEnrolledCount());
    }
}
