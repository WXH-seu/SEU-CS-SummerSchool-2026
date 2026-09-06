package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Course catalog data shared by client and server. */
public final class CourseDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String courseId;
    private final String courseName;
    private final String teacherId;
    private final String teacherName;
    private final String departmentId;
    private final String departmentName;
    private final double credit;
    private final int capacity;
    private final int enrolledCount;
    private final String semesterName;
    private final String classTime;
    private final String location;
    private final String description;
    private final boolean active;

    public CourseDto(String courseId, String courseName, String teacherId, String teacherName,
                     String departmentId, String departmentName, double credit, int capacity,
                     int enrolledCount, String semesterName, String classTime, String location,
                     String description, boolean active) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.credit = credit;
        this.capacity = capacity;
        this.enrolledCount = enrolledCount;
        this.semesterName = semesterName;
        this.classTime = classTime;
        this.location = location;
        this.description = description;
        this.active = active;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getCourseName() {
        return courseName;
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

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
