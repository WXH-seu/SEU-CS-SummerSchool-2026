package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseDropRequest;
import edu.seu.vcampus.common.dto.CourseEnrollmentDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.CourseSelectRequest;
import edu.seu.vcampus.common.dto.SectionAudienceDto;
import edu.seu.vcampus.common.dto.SectionRosterEntry;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.server.dao.CourseRepository;
import edu.seu.vcampus.server.dao.EnrolledStudentTime;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Business rules, audience checks and authorization for course selection.
 *
 * <p>The whole check-then-insert sequence of {@link #selectCourse} is guarded
 * by a per-course JVM lock. This serialises concurrent requests for the same
 * catalog course inside one server process, so two students cannot both take
 * the last free seat. The unique database constraint on
 * {@code (sectionId, studentId)} remains as a second line of defence.
 */
public final class CourseService {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter ENROLL_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> NATURES = new HashSet<String>(
            Arrays.asList("必修", "限选", "任选", "通选"));

    private final CourseRepository repository;
    private final ConcurrentMap<String, Object> courseLocks =
            new ConcurrentHashMap<String, Object>();

    public CourseService(CourseRepository repository) {
        this.repository = repository;
    }

    /**
     * Course directory. Students only see active sections whose audience
     * matches them while they are in an active enrolment state, and each row
     * carries the reason the selection button is disabled ({@code null} means
     * selectable). Teachers only see their own sections; administrators see
     * every section.
     */
    public ArrayList<CourseDto> queryCourses(String userId, SubSystemRole effectiveRole,
                                             CourseQueryRequest query)
            throws SQLException, BusinessException {
        requireId(userId, "账号不能为空");
        if (effectiveRole == SubSystemRole.STUDENT) {
            return queryVisibleForStudent(userId, query);
        }
        if (effectiveRole == SubSystemRole.TEACHER) {
            String teacherId = repository.findTeacherIdByUserId(userId);
            if (teacherId == null) {
                return new ArrayList<CourseDto>();
            }
            return annotateAll(repository.findSections(withTeacher(query, teacherId)), null);
        }
        return annotateAll(repository.findSections(query), null);
    }

    public ArrayList<CourseEnrollmentDto> querySchedule(String userId,
                                                        SubSystemRole effectiveRole)
            throws SQLException, BusinessException {
        String studentId = requireEnrolledStudent(userId, effectiveRole);
        return new ArrayList<CourseEnrollmentDto>(repository.findSchedule(studentId));
    }

    public void selectCourse(String userId, SubSystemRole effectiveRole,
                             CourseSelectRequest request)
            throws SQLException, BusinessException {
        String studentId = requireEnrolledStudent(userId, effectiveRole);
        requireId(request == null ? null : request.getSectionId(), "教学班编号不能为空");
        String sectionId = request.getSectionId().trim();

        CourseDto preview = repository.findSectionById(sectionId);
        if (preview == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "课程不存在");
        }
        StudentDto student = repository.findStudentByUserId(userId);
        Object lock = lockFor(preview.getCourseId());
        synchronized (lock) {
            CourseDto course = repository.findSectionById(sectionId);
            if (course == null) {
                throw new BusinessException(ResponseCode.NOT_FOUND, "课程不存在");
            }
            if (!course.isActive()) {
                throw new BusinessException(ResponseCode.CONFLICT, "课程未开放选课");
            }
            if (!matchesAudience(course, student.getDepartmentId(), student.getEnrollmentYear())) {
                throw new BusinessException(ResponseCode.FORBIDDEN, "该课程不面向你的院系或年级");
            }
            String windowReason = windowReason(course);
            if (windowReason != null) {
                throw new BusinessException(ResponseCode.CONFLICT, windowReason);
            }
            if (repository.isEnrolled(studentId, sectionId)) {
                throw new BusinessException(ResponseCode.CONFLICT, "不能重复选择同一教学班");
            }
            if (repository.isEnrolledInCourse(studentId, course.getCourseId())) {
                throw new BusinessException(ResponseCode.CONFLICT,
                        "你已经选了同一门课程的其他教学班");
            }
            if (hasConflictWithEnrolled(studentId, course.getClassTime())) {
                throw new BusinessException(ResponseCode.CONFLICT, "与已选课程上课时间冲突");
            }
            String attemptType = repository.hasCompletedCourse(
                    studentId, course.getCourseId())
                    ? CourseDto.ATTEMPT_RETAKE : CourseDto.ATTEMPT_FIRST;
            enforcePoolCapacity(course, attemptType);
            repository.insertEnrollment(studentId, sectionId, attemptType,
                    UUID.randomUUID().toString().replace("-", ""),
                    LocalDateTime.now().format(ENROLL_TIME_FORMAT));
        }
    }

    private void enforcePoolCapacity(CourseDto course, String attemptType)
            throws SQLException, BusinessException {
        edu.seu.vcampus.server.dao.AttemptCounts counts =
                repository.countAttempts(course.getSectionId());
        boolean poolFree = CourseDto.ATTEMPT_FIRST.equals(attemptType)
                ? counts.getFirstAttemptCount() < course.getFirstAttemptCapacity()
                : counts.getRetakeCount() < course.getRetakeCapacity();
        boolean totalFree = counts.getTotal() < course.getCapacity();
        if (!poolFree && !totalFree) {
            String label = CourseDto.ATTEMPT_FIRST.equals(attemptType) ? "首修" : "重修";
            throw new BusinessException(ResponseCode.CONFLICT, label + "名额已满");
        }
    }

    public void dropCourse(String userId, SubSystemRole effectiveRole, CourseDropRequest request)
            throws SQLException, BusinessException {
        String studentId = requireEnrolledStudent(userId, effectiveRole);
        requireId(request == null ? null : request.getEnrollmentId(), "选课记录编号不能为空");
        if (!repository.deleteEnrollment(studentId, request.getEnrollmentId().trim())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "选课记录不存在");
        }
    }

    /** Saves a catalog course, its section and the audience rules together. */
    public CourseDto saveCourse(String userId, SubSystemRole effectiveRole, CourseDto course)
            throws SQLException, BusinessException {
        requireAdmin(effectiveRole);
        validateCourse(course);
        String classTime = course.getClassTime().trim();
        String sectionId = course == null ? null : course.getSectionId();
        if (!isBlank(sectionId)) {
            synchronized (lockFor(course.getCourseId())) {
                CourseDto existing = repository.findSectionById(sectionId.trim());
                if (existing != null && classTimeChanged(existing, course)) {
                    List<EnrolledStudentTime> conflicts =
                            new ArrayList<EnrolledStudentTime>();
                    for (EnrolledStudentTime row
                            : repository.findEnrolledStudentsOtherClassTimes(
                                    sectionId.trim())) {
                        if (timesConflict(classTime, row.getClassTime())) {
                            conflicts.add(row);
                        }
                    }
                    if (!conflicts.isEmpty()) {
                        throw conflictFor(conflicts);
                    }
                }
                ensureTeacherAvailable(course.getTeacherId(), classTime, sectionId.trim());
                return repository.saveSection(course);
            }
        }
        ensureTeacherAvailable(course.getTeacherId(), classTime, "");
        return repository.saveSection(course);
    }

    private void ensureTeacherAvailable(String teacherId, String classTime,
                                        String excludeSectionId)
            throws SQLException, BusinessException {
        for (String taughtTime : repository.findTeacherSectionClassTimes(
                teacherId, excludeSectionId)) {
            if (timesConflict(classTime, taughtTime)) {
                throw new BusinessException(ResponseCode.CONFLICT,
                        "该教师在该时段已有其他教学班上课，未保存");
            }
        }
    }

    private boolean classTimeChanged(CourseDto existing, CourseDto next) {
        String oldTime = existing.getClassTime();
        String newTime = next == null ? null : next.getClassTime();
        return oldTime == null
                ? newTime != null
                : !oldTime.trim().equals(
                        newTime == null ? null : newTime.trim());
    }

    private BusinessException conflictFor(List<EnrolledStudentTime> conflicts) {
        EnrolledStudentTime first = conflicts.get(0);
        String message = "调课后学生 " + first.getStudentId()
                + "（" + first.getFullName() + "）将与已选课程时间冲突，未保存";
        if (conflicts.size() > 1) {
            message += "；另有 " + (conflicts.size() - 1) + " 名学生同样冲突";
        }
        return new BusinessException(ResponseCode.CONFLICT, message);
    }

    private boolean hasConflictWithEnrolled(String studentId, String candidateTime)
            throws SQLException {
        for (String enrolledTime : repository.findStudentEnrolledClassTimes(studentId)) {
            if (timesConflict(candidateTime, enrolledTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Canonical上课时间冲突规则：两端时间非空且去除首尾空白后完全相等即冲突。
     * 选课校验与管理员调课校验都必须经由本方法判断，今后如需支持“周次 + 节次
     * 区间重叠”等更细规则，只需修改此处。
     */
    private boolean timesConflict(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return first.trim().equals(second.trim());
    }

    public void deleteCourse(String userId, SubSystemRole effectiveRole, String sectionId)
            throws SQLException, BusinessException {
        requireAdmin(effectiveRole);
        requireId(sectionId, "教学班编号不能为空");
        sectionId = sectionId.trim();
        if (repository.sectionHasEnrollments(sectionId)) {
            throw new BusinessException(ResponseCode.CONFLICT, "课程仍有学生选课，请先停用");
        }
        if (!repository.deleteSection(sectionId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "课程记录不存在");
        }
    }

    /** Roster of one section; teachers may only open their own sections. */
    public ArrayList<SectionRosterEntry> queryRoster(String userId,
                                                     SubSystemRole effectiveRole,
                                                     String sectionId)
            throws SQLException, BusinessException {
        requireId(userId, "账号不能为空");
        requireId(sectionId, "教学班编号不能为空");
        sectionId = sectionId.trim();
        if (effectiveRole != SubSystemRole.ADMIN && effectiveRole != SubSystemRole.TEACHER) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "无权查看选课名单");
        }
        CourseDto course = repository.findSectionById(sectionId);
        if (course == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "课程不存在");
        }
        if (effectiveRole == SubSystemRole.TEACHER) {
            String teacherId = repository.findTeacherIdByUserId(userId);
            if (teacherId == null || !teacherId.equals(course.getTeacherId())) {
                throw new BusinessException(ResponseCode.FORBIDDEN, "只能查看本人课程的名单");
            }
        }
        return new ArrayList<SectionRosterEntry>(repository.findRoster(sectionId));
    }

    private ArrayList<CourseDto> queryVisibleForStudent(String userId,
                                                        CourseQueryRequest query)
            throws SQLException, BusinessException {
        StudentDto student = repository.findStudentByUserId(userId);
        if (student == null || !"在读".equals(student.getStatus())) {
            return new ArrayList<CourseDto>();
        }
        List<CourseDto> sections = repository.findSections(withActiveOnly(query, true));
        Set<String> enrolledSectionIds = new HashSet<String>();
        Set<String> enrolledClassTimes = new HashSet<String>();
        for (CourseEnrollmentDto enrollment : repository.findSchedule(student.getStudentId())) {
            enrolledSectionIds.add(enrollment.getSectionId());
            enrolledClassTimes.add(enrollment.getClassTime());
        }
        ArrayList<CourseDto> visible = new ArrayList<CourseDto>();
        for (CourseDto section : sections) {
            if (!matchesAudience(section, student.getDepartmentId(),
                    student.getEnrollmentYear())) {
                continue;
            }
            boolean selected = enrolledSectionIds.contains(section.getSectionId());
            String attemptType = repository.hasCompletedCourse(
                    student.getStudentId(), section.getCourseId())
                    ? CourseDto.ATTEMPT_RETAKE : CourseDto.ATTEMPT_FIRST;
            CourseDto annotated = annotate(section, attemptType);
            visible.add(withStudentState(annotated, selected, enrolledClassTimes));
        }
        return visible;
    }

    private CourseDto withStudentState(CourseDto course, boolean selected,
                                       Set<String> enrolledClassTimes)
            throws BusinessException {
        String reason = null;
        if (selected) {
            reason = "已选";
        } else {
            reason = windowReason(course);
            if (reason == null && !poolFree(course) && !totalFree(course)) {
                reason = (CourseDto.ATTEMPT_RETAKE.equals(course.getAttemptType())
                        ? "重修" : "首修") + "名额已满";
            }
            if (reason == null && course.getClassTime() != null
                    && enrolledClassTimes.contains(course.getClassTime())) {
                reason = "与已选课程时间冲突";
            }
        }
        return copyFull(course, course.getAttemptType(), selected, reason);
    }

    private boolean poolFree(CourseDto course) {
        if (CourseDto.ATTEMPT_RETAKE.equals(course.getAttemptType())) {
            return course.getRetakeEnrolled() < course.getRetakeCapacity();
        }
        return course.getFirstAttemptEnrolled() < course.getFirstAttemptCapacity();
    }

    private boolean totalFree(CourseDto course) {
        return course.getEnrolledCount() < course.getCapacity();
    }

    private ArrayList<CourseDto> annotateAll(List<CourseDto> sections, String attemptType)
            throws SQLException {
        ArrayList<CourseDto> annotated = new ArrayList<CourseDto>();
        for (CourseDto section : sections) {
            annotated.add(annotate(section, attemptType));
        }
        return annotated;
    }

    private CourseDto annotate(CourseDto section, String attemptType) throws SQLException {
        edu.seu.vcampus.server.dao.AttemptCounts counts =
                repository.countAttempts(section.getSectionId());
        return copyFull(section, attemptType, counts.getFirstAttemptCount(),
                counts.getRetakeCount(), counts.getTotal(), section.isSelected(),
                section.getReason());
    }

    private CourseDto copyFull(CourseDto source, String attemptType,
                               boolean selected, String reason) {
        return copyFull(source, attemptType, source.getFirstAttemptEnrolled(),
                source.getRetakeEnrolled(), source.getEnrolledCount(), selected, reason);
    }

    private CourseDto copyFull(CourseDto source, String attemptType,
                               int firstEnrolled, int retakeEnrolled, int enrolled,
                               boolean selected, String reason) {
        return new CourseDto(source.getSectionId(), source.getCourseId(),
                source.getCourseName(), source.getDescription(), source.getTeacherId(),
                source.getTeacherName(), source.getDepartmentId(), source.getDepartmentName(),
                source.getCredit(), source.getCourseNature(), source.getCapacity(),
                source.getFirstAttemptCapacity(), source.getRetakeCapacity(),
                firstEnrolled, retakeEnrolled, enrolled, attemptType,
                source.getSemesterName(), source.getClassTime(), source.getLocation(),
                source.getSelectionStartTime(), source.getSelectionEndTime(),
                source.isActive(), selected, reason, source.getAudiences());
    }

    private String windowReason(CourseDto course) throws BusinessException {
        String start = course.getSelectionStartTime();
        String end = course.getSelectionEndTime();
        if (isBlank(start) && isBlank(end)) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!isBlank(start) && now.isBefore(parseTime(start, "选课开始时间"))) {
            return "尚未开始选课";
        }
        if (!isBlank(end) && now.isAfter(parseTime(end, "选课结束时间"))) {
            return "选课已结束";
        }
        return null;
    }

    private LocalDateTime parseTime(String value, String label) throws BusinessException {
        try {
            return LocalDateTime.parse(value.trim(), TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResponseCode.INVALID_REQUEST,
                    label + "必须使用 yyyy-MM-dd HH:mm 格式");
        }
    }

    private boolean matchesAudience(CourseDto course, String departmentId,
                                    int enrollmentYear) {
        List<SectionAudienceDto> audiences = course.getAudiences();
        if (audiences == null || audiences.isEmpty()) {
            return true;
        }
        for (SectionAudienceDto audience : audiences) {
            if (SectionAudienceDto.SCOPE_DEPARTMENT.equals(audience.getScopeType())
                    && (departmentId == null
                    || !departmentId.equals(audience.getScopeValue()))) {
                continue;
            }
            if (audience.getYearMin() != null
                    && enrollmentYear < audience.getYearMin().intValue()) {
                continue;
            }
            if (audience.getYearMax() != null
                    && enrollmentYear > audience.getYearMax().intValue()) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void validateCourse(CourseDto course) throws SQLException, BusinessException {
        if (course == null || isBlank(course.getCourseId()) || isBlank(course.getCourseName())
                || isBlank(course.getTeacherId()) || isBlank(course.getDepartmentId())
                || isBlank(course.getSemesterName()) || isBlank(course.getClassTime())) {
            throw invalid("课程编号、名称、教师、开课院系、学期和上课时间不能为空");
        }
        if (course.getCredit() <= 0 || course.getCredit() > 20) {
            throw invalid("学分必须在 0 到 20 之间");
        }
        if (course.getCapacity() <= 0 || course.getCapacity() > 1000) {
            throw invalid("容量必须在 1 到 1000 之间");
        }
        if (course.getFirstAttemptCapacity() < 0
                || course.getFirstAttemptCapacity() > course.getCapacity()
                || course.getRetakeCapacity() < 0
                || course.getRetakeCapacity() > course.getCapacity()) {
            throw invalid("首修 / 重修名额必须在 0 到最大容量之间");
        }
        if (!NATURES.contains(course.getCourseNature())) {
            throw invalid("课程性质必须为必修、限选、任选或通选");
        }
        validateWindow(course);
        validateAudiences(course);
        if (!repository.teacherExists(course.getTeacherId())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "授课教师不存在");
        }
        if (!repository.departmentExists(course.getDepartmentId())) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "开课院系不存在");
        }
    }

    private void validateWindow(CourseDto course) throws BusinessException {
        String start = course.getSelectionStartTime();
        String end = course.getSelectionEndTime();
        if (isBlank(start) && isBlank(end)) {
            return;
        }
        if (isBlank(start) || isBlank(end)) {
            throw invalid("选课开始与结束时间必须同时填写");
        }
        LocalDateTime startTime = parseTime(start, "选课开始时间");
        LocalDateTime endTime = parseTime(end, "选课结束时间");
        if (startTime.isAfter(endTime)) {
            throw invalid("选课结束时间不能早于开始时间");
        }
    }

    private void validateAudiences(CourseDto course) throws SQLException, BusinessException {
        List<SectionAudienceDto> audiences = course.getAudiences();
        if (audiences == null || audiences.isEmpty()) {
            throw invalid("至少需要一条选课受众规则");
        }
        for (SectionAudienceDto audience : audiences) {
            if (audience == null) {
                throw invalid("受众规则不完整");
            }
            String scopeType = audience.getScopeType();
            if (SectionAudienceDto.SCOPE_ALL.equals(scopeType)) {
                continue;
            }
            if (!SectionAudienceDto.SCOPE_DEPARTMENT.equals(scopeType)
                    || isBlank(audience.getScopeValue())) {
                throw invalid("受众范围必须为全校或指定院系");
            }
            if (!repository.departmentExists(audience.getScopeValue())) {
                throw new BusinessException(ResponseCode.NOT_FOUND, "受众院系不存在");
            }
            if (audience.getYearMin() != null && audience.getYearMax() != null
                    && audience.getYearMin().intValue() > audience.getYearMax().intValue()) {
                throw invalid("受众入学年份区间填写有误");
            }
        }
    }

    private Object lockFor(String courseId) {
        return courseLocks.computeIfAbsent(courseId, new java.util.function.Function<String, Object>() {
            @Override
            public Object apply(String key) {
                return new Object();
            }
        });
    }

    private String requireEnrolledStudent(String userId, SubSystemRole effectiveRole)
            throws SQLException, BusinessException {
        requireId(userId, "账号不能为空");
        if (effectiveRole != SubSystemRole.STUDENT) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅学生可以操作选课");
        }
        StudentDto student = repository.findStudentByUserId(userId);
        if (student == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND, "未找到该账号的学籍信息");
        }
        if (!"在读".equals(student.getStatus())) {
            throw new BusinessException(ResponseCode.CONFLICT, "仅在读学生可以选课");
        }
        return student.getStudentId();
    }

    private void requireAdmin(SubSystemRole effectiveRole) throws BusinessException {
        if (effectiveRole != SubSystemRole.ADMIN) {
            throw new BusinessException(ResponseCode.FORBIDDEN, "仅管理员可以维护课程");
        }
    }

    private CourseQueryRequest withTeacher(CourseQueryRequest query, String teacherId) {
        return new CourseQueryRequest(keywordOf(query), departmentOf(query), teacherId,
                semesterOf(query), activeOf(query), natureOf(query));
    }

    private CourseQueryRequest withActiveOnly(CourseQueryRequest query, boolean activeOnly) {
        return new CourseQueryRequest(keywordOf(query), departmentOf(query), teacherOf(query),
                semesterOf(query), activeOnly, natureOf(query));
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

    private String natureOf(CourseQueryRequest query) {
        return query == null ? null : query.getNature();
    }

    private boolean activeOf(CourseQueryRequest query) {
        return query != null && query.isActiveOnly();
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
