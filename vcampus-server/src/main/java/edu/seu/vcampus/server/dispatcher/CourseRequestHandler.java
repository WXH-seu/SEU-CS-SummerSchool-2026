package edu.seu.vcampus.server.dispatcher;

import edu.seu.vcampus.common.dto.CourseDropRequest;
import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.CourseSelectRequest;
import edu.seu.vcampus.common.dto.EntityIdRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.service.BusinessException;
import edu.seu.vcampus.server.service.CourseService;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.EnumSet;

/** Handles protocol operations owned by the course selection module. */
public final class CourseRequestHandler {
    private static final EnumSet<Operation> OPERATIONS = EnumSet.of(
            Operation.COURSE_QUERY, Operation.COURSE_SAVE, Operation.COURSE_DELETE,
            Operation.COURSE_SELECT, Operation.COURSE_DROP, Operation.SCHEDULE_QUERY);

    private final CourseService service;

    public CourseRequestHandler(CourseService service) {
        this.service = service;
    }

    public boolean supports(Operation operation) {
        return OPERATIONS.contains(operation);
    }

    public ResponseMessage<? extends Serializable> handle(
            RequestMessage<?> request, UserAccount actor) throws SQLException {
        try {
            switch (request.getOperation()) {
                case COURSE_QUERY:
                    return success(request, service.queryCourses(actor, queryBody(request)));
                case COURSE_SELECT:
                    service.selectCourse(actor, body(request, CourseSelectRequest.class));
                    return success(request, "OK");
                case COURSE_DROP:
                    service.dropCourse(actor, body(request, CourseDropRequest.class));
                    return success(request, "OK");
                case COURSE_SAVE:
                    service.saveCourse(actor, body(request, CourseDto.class));
                    return success(request, body(request, CourseDto.class));
                case COURSE_DELETE:
                    service.deleteCourse(actor, idBody(request));
                    return success(request, "OK");
                case SCHEDULE_QUERY:
                    return success(request, service.querySchedule(actor));
                default:
                    return ResponseMessage.failure(request.getRequestId(),
                            ResponseCode.NOT_IMPLEMENTED, "不支持的选课操作");
            }
        } catch (BusinessException e) {
            return ResponseMessage.failure(request.getRequestId(),
                    e.getResponseCode(), e.getMessage());
        }
    }

    private CourseQueryRequest queryBody(RequestMessage<?> request) throws BusinessException {
        if (request.getBody() == null) {
            return null;
        }
        return body(request, CourseQueryRequest.class);
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
