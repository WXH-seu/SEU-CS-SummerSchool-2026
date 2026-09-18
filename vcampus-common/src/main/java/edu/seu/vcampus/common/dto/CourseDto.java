package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One course section row shared by client and server. A section binds one
 * offering (teacher / capacity / audience) to a catalog course, so the row
 * carries both catalog fields (name, credit) and offering fields.
 *
 * <p>Capacity is split into a hard maximum ({@code capacity}) plus strict
 * pools for first attempts ({@code firstAttemptCapacity}) and retakes
 * ({@code retakeCapacity}). A student may select only while their own pool has
 * room; seats not assigned to either pool are reserved for administrative
 * adjustment and are not open to student self-service.
 *
 * <p>{@code attemptType}, {@code firstAttemptEnrolled} and
 * {@code retakeEnrolled} let the client render the quota that applies to the
 * requesting student; {@code selected} and {@code reason} are computed for
 * that student only.
 */
public final class CourseDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter SELECTION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static final String ATTEMPT_FIRST = "FIRST";
    public static final String ATTEMPT_RETAKE = "RETAKE";

    private final String sectionId;
    private final String courseId;
    private final String courseName;
    private final String description;
    private final String teacherId;
    private final String teacherName;
    private final String departmentId;
    private final String departmentName;
    private final double credit;
    private final String courseNature;
    private final int capacity;
    private final int firstAttemptCapacity;
    private final int retakeCapacity;
    private final int firstAttemptEnrolled;
    private final int retakeEnrolled;
    private final int enrolledCount;
    private final String attemptType;
    private final String semesterName;
    private final String classTime;
    private final String location;
    private final String selectionStartTime;
    private final String selectionEndTime;
    private final boolean active;
    private final boolean selected;
    private final String reason;
    private final List<SectionAudienceDto> audiences;
    private final List<SectionScheduleDto> schedules;

    public CourseDto(String sectionId, String courseId, String courseName, String description,
                     String teacherId, String teacherName, String departmentId,
                     String departmentName, double credit, String courseNature,
                     int capacity, int enrolledCount, String semesterName, String classTime,
                     String location, String selectionStartTime, String selectionEndTime,
                     boolean active, boolean selected, String reason,
                     List<SectionAudienceDto> audiences) {
        this(sectionId, courseId, courseName, description,
                teacherId, teacherName, departmentId, departmentName, credit, courseNature,
                capacity, capacity, 0, 0, 0, enrolledCount, null,
                semesterName, classTime, location, selectionStartTime, selectionEndTime,
                active, selected, reason, audiences,
                new ArrayList<SectionScheduleDto>());
    }

    public CourseDto(String sectionId, String courseId, String courseName, String description,
                     String teacherId, String teacherName, String departmentId,
                     String departmentName, double credit, String courseNature,
                     int capacity, int enrolledCount, String semesterName, String classTime,
                     String location, String selectionStartTime, String selectionEndTime,
                     boolean active, boolean selected, String reason,
                     List<SectionAudienceDto> audiences,
                     List<SectionScheduleDto> schedules) {
        this(sectionId, courseId, courseName, description,
                teacherId, teacherName, departmentId, departmentName, credit, courseNature,
                capacity, capacity, 0, 0, 0, enrolledCount, null,
                semesterName, classTime, location, selectionStartTime, selectionEndTime,
                active, selected, reason, audiences, schedules);
    }

    public CourseDto(String sectionId, String courseId, String courseName, String description,
                     String teacherId, String teacherName, String departmentId,
                     String departmentName, double credit, String courseNature,
                     int capacity, int firstAttemptCapacity, int retakeCapacity,
                     int firstAttemptEnrolled, int retakeEnrolled, int enrolledCount,
                     String attemptType, String semesterName, String classTime,
                     String location, String selectionStartTime, String selectionEndTime,
                     boolean active, boolean selected, String reason,
                     List<SectionAudienceDto> audiences,
                     List<SectionScheduleDto> schedules) {
        this(sectionId, courseId, courseName, description,
                teacherId, teacherName, departmentId, departmentName, credit, courseNature,
                capacity, firstAttemptCapacity, retakeCapacity,
                firstAttemptEnrolled, retakeEnrolled, enrolledCount, attemptType,
                semesterName, classTime, location, selectionStartTime, selectionEndTime,
                active, selected, reason, audiences, schedules, true);
    }

    public CourseDto(String sectionId, String courseId, String courseName, String description,
                     String teacherId, String teacherName, String departmentId,
                     String departmentName, double credit, String courseNature,
                     int capacity, int firstAttemptCapacity, int retakeCapacity,
                     int firstAttemptEnrolled, int retakeEnrolled, int enrolledCount,
                     String attemptType, String semesterName, String classTime,
                     String location, String selectionStartTime, String selectionEndTime,
                     boolean active, boolean selected, String reason,
                     List<SectionAudienceDto> audiences) {
        this(sectionId, courseId, courseName, description,
                teacherId, teacherName, departmentId, departmentName, credit, courseNature,
                capacity, firstAttemptCapacity, retakeCapacity,
                firstAttemptEnrolled, retakeEnrolled, enrolledCount, attemptType,
                semesterName, classTime, location, selectionStartTime, selectionEndTime,
                active, selected, reason, audiences,
                new ArrayList<SectionScheduleDto>());
    }

    private CourseDto(String sectionId, String courseId, String courseName, String description,
                      String teacherId, String teacherName, String departmentId,
                      String departmentName, double credit, String courseNature,
                      int capacity, int firstAttemptCapacity, int retakeCapacity,
                      int firstAttemptEnrolled, int retakeEnrolled, int enrolledCount,
                      String attemptType, String semesterName, String classTime,
                      String location, String selectionStartTime, String selectionEndTime,
                      boolean active, boolean selected, String reason,
                      List<SectionAudienceDto> audiences,
                      List<SectionScheduleDto> schedules, boolean ignored) {
        this.sectionId = sectionId;
        this.courseId = courseId;
        this.courseName = courseName;
        this.description = description;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.credit = credit;
        this.courseNature = courseNature;
        this.capacity = capacity;
        this.firstAttemptCapacity = firstAttemptCapacity;
        this.retakeCapacity = retakeCapacity;
        this.firstAttemptEnrolled = firstAttemptEnrolled;
        this.retakeEnrolled = retakeEnrolled;
        this.enrolledCount = enrolledCount;
        this.attemptType = attemptType;
        this.semesterName = semesterName;
        this.classTime = classTime;
        this.location = location;
        this.selectionStartTime = selectionStartTime;
        this.selectionEndTime = selectionEndTime;
        this.active = active;
        this.selected = selected;
        this.reason = reason;
        this.audiences = audiences == null
                ? new ArrayList<SectionAudienceDto>()
                : new ArrayList<SectionAudienceDto>(audiences);
        this.schedules = schedules == null
                ? new ArrayList<SectionScheduleDto>()
                : new ArrayList<SectionScheduleDto>(schedules);
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getDescription() {
        return description;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public double getCredit() {
        return credit;
    }

    public String getCourseNature() {
        return courseNature;
    }

    /** 最大容量（总人数硬上限）。 */
    public int getCapacity() {
        return capacity;
    }

    /** 首修严格名额上限。 */
    public int getFirstAttemptCapacity() {
        return firstAttemptCapacity;
    }

    /** 重修严格名额上限。 */
    public int getRetakeCapacity() {
        return retakeCapacity;
    }

    public int getFirstAttemptEnrolled() {
        return firstAttemptEnrolled;
    }

    public int getRetakeEnrolled() {
        return retakeEnrolled;
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    /** 当前请求学生的修读类型；非学生视角为 {@code null}。 */
    public String getAttemptType() {
        return attemptType;
    }

    public String getSemesterName() {
        return semesterName;
    }

    public String getClassTime() {
        return classTime;
    }

    public String getLocation() {
        return location;
    }

    public String getSelectionStartTime() {
        return selectionStartTime;
    }

    public String getSelectionEndTime() {
        return selectionEndTime;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSelected() {
        return selected;
    }

    /** {@code null} means selectable; otherwise the reason the button is disabled. */
    public String getReason() {
        return reason;
    }

    public List<SectionAudienceDto> getAudiences() {
        return Collections.unmodifiableList(audiences);
    }

    public List<SectionScheduleDto> getSchedules() {
        return Collections.unmodifiableList(schedules);
    }

    /**
     * 该教学班的选课是否已经结束。
     *
     * <p>只有填写了选课结束时间、且当前时间已经晚于它，才算“已结束”。未设置选课窗口
     * （不限时间）的班次永远返回 {@code false}：学生随时可能选课，教师的选课名单也
     * 一直不开放。服务端与客户端共用本方法，避免同一条规则出现两种实现。
     */
    public boolean isSelectionClosed() {
        if (selectionEndTime == null || selectionEndTime.trim().isEmpty()) {
            return false;
        }
        try {
            return LocalDateTime.now().isAfter(
                    LocalDateTime.parse(selectionEndTime.trim(), SELECTION_TIME_FORMAT));
        } catch (DateTimeParseException e) {
            // 非法格式由服务端保存时校验；这里按“未结束”处理，宁可保守。
            return false;
        }
    }
}
