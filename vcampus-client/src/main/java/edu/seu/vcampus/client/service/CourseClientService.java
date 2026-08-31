package edu.seu.vcampus.client.service;

import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseDropRequest;
import edu.seu.vcampus.common.dto.CourseEnrollmentDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.CourseSelectRequest;
import edu.seu.vcampus.common.dto.EntityIdRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Converts course selection UI actions into object-stream requests. */
public final class CourseClientService {
    private final ClientConnection connection;
    private final String sessionToken;

    public CourseClientService(ClientConnection connection, String sessionToken) {
        this.connection = connection;
        this.sessionToken = sessionToken;
    }

    public List<CourseDto> queryCourses(CourseQueryRequest query) throws IOException {
        return listRequest(Operation.COURSE_QUERY, query, CourseDto.class);
    }

    public List<CourseEnrollmentDto> querySchedule() throws IOException {
        return listRequest(Operation.SCHEDULE_QUERY, null, CourseEnrollmentDto.class);
    }

    public void selectCourse(String courseId) throws IOException {
        request(Operation.COURSE_SELECT, new CourseSelectRequest(courseId));
    }

    public void dropCourse(String enrollmentId) throws IOException {
        request(Operation.COURSE_DROP, new CourseDropRequest(enrollmentId));
    }

    public void saveCourse(CourseDto course) throws IOException {
        request(Operation.COURSE_SAVE, course);
    }

    public void deleteCourse(String courseId) throws IOException {
        request(Operation.COURSE_DELETE, new EntityIdRequest(courseId));
    }

    private <T> List<T> listRequest(Operation operation, Serializable body, Class<T> type)
            throws IOException {
        Object responseBody = request(operation, body).getBody();
        if (!(responseBody instanceof List)) {
            throw new IOException("服务器返回的数据格式不正确");
        }
        List<?> raw = (List<?>) responseBody;
        List<T> result = new ArrayList<T>();
        for (Object item : raw) {
            if (!type.isInstance(item)) {
                throw new IOException("服务器返回的数据类型不正确");
            }
            result.add(type.cast(item));
        }
        return result;
    }

    private ResponseMessage<?> request(Operation operation, Serializable body)
            throws IOException {
        ResponseMessage<?> response = connection.request(
                new RequestMessage<Serializable>(operation, sessionToken, body));
        if (!response.isSuccess()) {
            throw new IOException(response.getMessage());
        }
        return response;
    }
}
