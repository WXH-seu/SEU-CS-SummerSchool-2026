package edu.seu.vcampus.client.service;

import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.EntityIdRequest;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Converts academic UI actions into object-stream requests. */
public final class AcademicClientService {
    private final ClientConnection connection;
    private final String sessionToken;

    public AcademicClientService(ClientConnection connection, String sessionToken) {
        this.connection = connection;
        this.sessionToken = sessionToken;
    }

    public List<StudentDto> queryStudents(AcademicQueryRequest query) throws IOException {
        return listRequest(Operation.STUDENT_QUERY, query, StudentDto.class);
    }

    public List<TeacherDto> queryTeachers(AcademicQueryRequest query) throws IOException {
        return listRequest(Operation.TEACHER_QUERY, query, TeacherDto.class);
    }

    public List<DepartmentDto> queryDepartments(boolean activeOnly) throws IOException {
        AcademicQueryRequest query = new AcademicQueryRequest(null, null, null, activeOnly);
        return listRequest(Operation.DEPARTMENT_QUERY, query, DepartmentDto.class);
    }

    public List<SchoolClassDto> queryClasses(AcademicQueryRequest query) throws IOException {
        return listRequest(Operation.CLASS_QUERY, query, SchoolClassDto.class);
    }

    public void saveStudent(StudentDto student) throws IOException {
        request(Operation.STUDENT_SAVE, student);
    }

    public void saveTeacher(TeacherDto teacher) throws IOException {
        request(Operation.TEACHER_SAVE, teacher);
    }

    public void saveDepartment(DepartmentDto department) throws IOException {
        request(Operation.DEPARTMENT_SAVE, department);
    }

    public void saveClass(SchoolClassDto schoolClass) throws IOException {
        request(Operation.CLASS_SAVE, schoolClass);
    }

    public void deleteStudent(String id) throws IOException {
        request(Operation.STUDENT_DELETE, new EntityIdRequest(id));
    }

    public void deleteTeacher(String id) throws IOException {
        request(Operation.TEACHER_DELETE, new EntityIdRequest(id));
    }

    public void deleteDepartment(String id) throws IOException {
        request(Operation.DEPARTMENT_DELETE, new EntityIdRequest(id));
    }

    public void deleteClass(String id) throws IOException {
        request(Operation.CLASS_DELETE, new EntityIdRequest(id));
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

    private ResponseMessage<?> request(Operation operation, Serializable body) throws IOException {
        ResponseMessage<?> response = connection.request(
                new RequestMessage<Serializable>(operation, sessionToken, body));
        if (!response.isSuccess()) {
            throw new IOException(response.getMessage());
        }
        return response;
    }
}
