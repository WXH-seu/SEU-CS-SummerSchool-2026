package edu.seu.vcampus.common.message;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseSelectRequest;
import edu.seu.vcampus.common.dto.SectionAudienceDto;
import edu.seu.vcampus.common.enums.Operation;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/** Ensures course DTOs remain compatible with the object-stream protocol. */
public class CourseMessageSerializationTest {
    @Test
    public void serializesCourseSelectRequest() throws Exception {
        CourseSelectRequest select = new CourseSelectRequest("SEC00000001");
        RequestMessage<CourseSelectRequest> request = new RequestMessage<CourseSelectRequest>(
                Operation.COURSE_SELECT, "session", select);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(request);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        RequestMessage<?> restored = (RequestMessage<?>) input.readObject();

        assertEquals(Operation.COURSE_SELECT, restored.getOperation());
        assertEquals("SEC00000001",
                ((CourseSelectRequest) restored.getBody()).getSectionId());
    }

    @Test
    public void serializesCourseSectionResponse() throws Exception {
        CourseDto course = section("SEC00000001", "CS101", "Java 程序设计", 3.0, true);
        ResponseMessage<CourseDto> response =
                ResponseMessage.success("req-1", "操作成功", course);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(response);

        ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()));
        ResponseMessage<?> restored = (ResponseMessage<?>) input.readObject();

        assertEquals("req-1", restored.getRequestId());
        CourseDto restoredCourse = (CourseDto) restored.getBody();
        assertEquals("SEC00000001", restoredCourse.getSectionId());
        assertEquals("CS101", restoredCourse.getCourseId());
        assertEquals("必修", restoredCourse.getCourseNature());
        assertEquals(30, restoredCourse.getCapacity());
        assertEquals(1, restoredCourse.getEnrolledCount());
        assertEquals("已选", restoredCourse.getReason());
    }

    static CourseDto section(String sectionId, String courseId, String courseName,
                             double credit, boolean selected) {
        return new CourseDto(sectionId, courseId, courseName, "Java 基础",
                "T0001", "演示教师", "CS", "计算机科学与工程学院", credit, "必修",
                30, 1, "2026-2027-1", "周一 3-4 节", "教1-101",
                "2026-09-01 08:00", "2026-12-31 23:59", true, selected,
                selected ? "已选" : null,
                Collections.singletonList(new SectionAudienceDto(
                        null, SectionAudienceDto.SCOPE_ALL, null, null, null)));
    }
}
