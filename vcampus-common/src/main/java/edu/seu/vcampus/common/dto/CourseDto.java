package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One course section row shared by client and server. A section binds one
 * offering (teacher / time / capacity / audience) to a catalog course, so the
 * row carries both catalog fields (name, credit) and offering fields.
 * {@code selected} and {@code reason} are computed for the requesting student
 * only; they stay {@code false} / {@code null} for staff and administrators.
 */
public final class CourseDto implements Serializable {
    private static final long serialVersionUID = 1L;

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
    private final int enrolledCount;
    private final String semesterName;
    private final String classTime;
    private final String location;
    private final String selectionStartTime;
    private final String selectionEndTime;
    private final boolean active;
    private final boolean selected;
    private final String reason;
    private final List<SectionAudienceDto> audiences;

    public CourseDto(String sectionId, String courseId, String courseName, String description,
                     String teacherId, String teacherName, String departmentId,
                     String departmentName, double credit, String courseNature,
                     int capacity, int enrolledCount, String semesterName, String classTime,
                     String location, String selectionStartTime, String selectionEndTime,
                     boolean active, boolean selected, String reason,
                     List<SectionAudienceDto> audiences) {
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
        this.enrolledCount = enrolledCount;
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

    public int getCapacity() {
        return capacity;
    }

    public int getEnrolledCount() {
        return enrolledCount;
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
}
