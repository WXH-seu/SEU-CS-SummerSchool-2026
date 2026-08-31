package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.EntityIdRequest;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.service.AcademicService;
import edu.seu.vcampus.server.service.BusinessException;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.EnumSet;

/** Handles protocol operations owned by the academic module. */
public final class AcademicRequestHandler {
    private static final EnumSet<Operation> OPERATIONS = EnumSet.of(
            Operation.STUDENT_QUERY, Operation.STUDENT_SAVE, Operation.STUDENT_DELETE,
            Operation.TEACHER_QUERY, Operation.TEACHER_SAVE, Operation.TEACHER_DELETE,
            Operation.DEPARTMENT_QUERY, Operation.DEPARTMENT_SAVE, Operation.DEPARTMENT_DELETE,
            Operation.CLASS_QUERY, Operation.CLASS_SAVE, Operation.CLASS_DELETE);

    private final AcademicService service;

    public AcademicRequestHandler(AcademicService service) {
        this.service = service;
    }

    public boolean supports(Operation operation) {
        return OPERATIONS.contains(operation);
    }

    public ResponseMessage<? extends Serializable> handle(
            RequestMessage<?> request, String actorUserId, SubSystemRole actorRole)
            throws SQLException {
        try {
            switch (request.getOperation()) {
                case STUDENT_QUERY:
                    return success(request, service.queryStudents(
                            actorUserId, actorRole, queryBody(request)));
                case STUDENT_SAVE:
                    service.saveStudent(actorUserId, actorRole, body(request, StudentDto.class));
                    return success(request, body(request, StudentDto.class));
                case STUDENT_DELETE:
                    service.deleteStudent(actorUserId, actorRole, idBody(request));
                    return success(request, "OK");
                case TEACHER_QUERY:
                    return success(request, service.queryTeachers(
                            actorUserId, actorRole, queryBody(request)));
                case TEACHER_SAVE:
                    service.saveTeacher(actorUserId, actorRole, body(request, TeacherDto.class));
                    return success(request, body(request, TeacherDto.class));
                case TEACHER_DELETE:
                    service.deleteTeacher(actorUserId, actorRole, idBody(request));
                    return success(request, "OK");
                case DEPARTMENT_QUERY:
                    AcademicQueryRequest departmentQuery = queryBody(request);
                    return success(request, service.queryDepartments(actorUserId, actorRole,
                            departmentQuery != null && departmentQuery.isActiveOnly()));
                case DEPARTMENT_SAVE:
                    service.saveDepartment(
                            actorUserId, actorRole, body(request, DepartmentDto.class));
                    return success(request, body(request, DepartmentDto.class));
                case DEPARTMENT_DELETE:
                    service.deleteDepartment(actorUserId, actorRole, idBody(request));
                    return success(request, "OK");
                case CLASS_QUERY:
                    return success(request, service.queryClasses(
                            actorUserId, actorRole, queryBody(request)));
                case CLASS_SAVE:
                    service.saveClass(actorUserId, actorRole, body(request, SchoolClassDto.class));
                    return success(request, body(request, SchoolClassDto.class));
                case CLASS_DELETE:
                    service.deleteClass(actorUserId, actorRole, idBody(request));
                    return success(request, "OK");
                default:
                    return ResponseMessage.failure(request.getRequestId(),
                            ResponseCode.NOT_IMPLEMENTED, "不支持的学籍操作");
            }
        } catch (BusinessException e) {
            return ResponseMessage.failure(request.getRequestId(),
                    e.getResponseCode(), e.getMessage());
        }
    }

    private AcademicQueryRequest queryBody(RequestMessage<?> request) throws BusinessException {
        if (request.getBody() == null) {
            return null;
        }
        return body(request, AcademicQueryRequest.class);
    }

    private String idBody(RequestMessage<?> request) throws BusinessException {
        return body(request, EntityIdRequest.class).getEntityId();
    }

    private <T> T body(RequestMessage<?> request, Class<T> type) throws BusinessException {
        if (!type.isInstance(request.getBody())) {
            throw new BusinessException(ResponseCode.INVALID_REQUEST, "请求参数格式错误");
        }
        return type.cast(request.getBody());
    }

    private ResponseMessage<? extends Serializable> success(
            RequestMessage<?> request, Serializable body) {
        return ResponseMessage.success(request.getRequestId(), "操作成功", body);
    }
}
