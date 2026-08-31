package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseDropRequest;
import edu.seu.vcampus.common.dto.CourseEnrollmentDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.CourseSelectRequest;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;
import edu.seu.vcampus.server.dao.CourseRepository;
import edu.seu.vcampus.server.dao.UserAccount;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Business rules and permission checks for course selection. */
public final class CourseService {
    private static final DateTimeFormatter ENROLL_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CourseRepository repository;

    public CourseService(CourseRepository repository) {
        this.repository = repository;
    }

    public ArrayList<CourseDto> queryCourses(UserAccount actor, CourseQueryRequest query)
            throws SQLException, BusinessException {
        requireActor(actor);
        CourseQueryRequest effective = query;
        SubSystemRole effectiveRole = effectiveRole(actor);
        if (effectiveRole == SubSystemRole.STUDENT) {
            effective = withActiveOnly(query, true);
        } else if (effectiveRole == SubSystemRole.TEACHER) {
            String teacherId = repository.findTeacherIdByUserId(actor.getUserId());
            if (teacherId == null) {
                return new ArrayList<CourseDto>();
            }
            effective = withTeacher(query, teacherId);
        }
        return new ArrayList<CourseDto>(repository.findCourses(effective));
    }

    public ArrayList<CourseEnrollmentDto> querySchedule(UserAccount actor)
            throws SQLException, BusinessException {
        String studentId = requireEnrolledStudent(actor);
        return new ArrayList<CourseEnrollmentDto>(repository.findSchedule(studentId));
    }

    public void selectCourse(UserAccount actor, CourseSelectRequest request)
            throws SQLException, BusinessException {
        String studentId = requireEnrolledStudent(actor);
        requireId(request == null ? null : request.getCourseId(), "课程编号不能为空");
        CourseDto course = repository.findCourseById(request.getCourseId().trim());
        if (course == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "课程不存在");
        }
        if (!course.isActive()) {
            throw new BusinessException(ResponseCode.CONFLICT, "课程未开放选课");
        }
        if (repository.isEnrolled(studentId, course.getCourseId())) {
            throw new BusinessException(ResponseCode.CONFLICT, "不能重复选择同一门课程");
        }
        if (repository.countEnrolled(course.getCourseId()) >= course.getCapacity()) {
            throw new BusinessException(ResponseCode.CONFLICT, "课程容量已满");
        }
        if (repository.hasTimeConflict(studentId, course.getClassTime())) {
            throw new BusinessException(ResponseCode.CONFLICT, "与已选课程上课时间冲突");
        }
        repository.insertEnrollment(studentId, course.getCourseId(),
                UUID.randomUUID().toString().replace("-", ""),
                LocalDateTime.now().format(ENROLL_TIME_FORMAT));
    }

    public void dropCourse(UserAccount actor, CourseDropRequest request)
            throws SQLException, BusinessException {
        String studentId = requireEnrolledStudent(actor);
        requireId(request == null ? null : request.getEnrollmentId(), "选课记录编号不能为空");
        if (!repository.deleteEnrollment(studentId, request.getEnrollmentId().trim())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "选课记录不存在");
        }
    }

    public void saveCourse(UserAccount actor, CourseDto course)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        validateCourse(course);
        repository.saveCourse(course);
    }

    public void deleteCourse(UserAccount actor, String courseId)
            throws SQLException, BusinessException {
        requireAdmin(actor);
        requireId(courseId, "课程编号不能为空");
        courseId = courseId.trim();
        if (repository.courseHasEnrollments(courseId)) {
            throw new BusinessException(ResponseCode.CONFLICT, "课程仍有学生选课，请先停用");
        }
        if (!repository.deleteCourse(courseId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "课程记录不存在");
        }
    }

    private void validateCourse(CourseDto course) throws SQLException, BusinessException {
        if (course == null || isBlank(course.getCourseId()) || isBlank(course.getCourseName())
                || isBlank(course.getTeacherId()) || isBlank(course.getDepartmentId())
                || isBlank(course.getSemesterName()) || isBlank(course.getClassTime())) {
            throw invalid("课程编号、名称、教师、院系、学期和上课时间不能为空");
        }
        if (course.getCredit() <= 0 || course.getCredit() > 20) {
            throw invalid("学分必须在 0 到 20 之间");
        }
        if (course.getCapacity() <= 0 || course.getCapacity() > 1000) {
            throw invalid("容量必须在 1 到 1000 之间");
        }
        if (!repository.teacherExists(course.getTeacherId())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "授课教师不存在");
        }
        if (!repository.departmentExists(course.getDepartmentId())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "开课院系不存在");
        }
    }

    private String requireEnrolledStudent(UserAccount actor)
            throws SQLException, BusinessException {
        requireActor(actor);
        if (effectiveRole(actor) != SubSystemRole.STUDENT) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅学生可以操作选课");
        }
        StudentDto student = repository.findStudentByUserId(actor.getUserId());
        if (student == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "未找到该账号的学籍信息");
        }
        if (!"在读".equals(student.getStatus())) {
            throw new BusinessException(ResponseCode.CONFLICT, "仅在读学生可以选课");
        }
        return student.getStudentId();
    }

    private CourseQueryRequest withTeacher(CourseQueryRequest query, String teacherId) {
        return new CourseQueryRequest(keywordOf(query), departmentOf(query), teacherId,
                semesterOf(query), activeOf(query));
    }

    private CourseQueryRequest withActiveOnly(CourseQueryRequest query, boolean activeOnly) {
        return new CourseQueryRequest(keywordOf(query), departmentOf(query), teacherOf(query),
                semesterOf(query), activeOnly);
    }

    private String keywordOf(CourseQueryRequest query) {
        return query == null ? null : query.getKeyword();
    }

    private String departmentOf(CourseQueryRequest query) {
        return query == null ? null : query.getDepartmentId();
    }

    private String teacherOf(CourseQueryRequest query) {
        return query == null ? null : query.getTeacherId();
    }

    private String semesterOf(CourseQueryRequest query) {
        return query == null ? null : query.getSemesterName();
    }

    private boolean activeOf(CourseQueryRequest query) {
        return query != null && query.isActiveOnly();
    }

    private void requireActor(UserAccount actor) throws BusinessException {
        if (actor == null) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "请先登录");
        }
    }

    private SubSystemRole effectiveRole(UserAccount actor) {
        return SubSystems.effectiveRole(actor.getRole(), actor.getAdminScopes(), SubSystem.COURSE);
    }

    private void requireAdmin(UserAccount actor) throws BusinessException {
        requireActor(actor);
        if (effectiveRole(actor) != SubSystemRole.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅管理员可以维护课程");
        }
    }

    private void requireId(String id, String message) throws BusinessException {
        if (isBlank(id)) {
            throw invalid(message);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ResponseCode.INVALID_REQUEST, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
