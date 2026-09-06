package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** One row of a student schedule returned by the server. */
public final class CourseEnrollmentDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String enrollmentId;
    private final String courseId;
    private final String courseName;
    private final String teacherName;
    private final double credit;
    private final String classTime;
    private final String location;
    private final String enrollTime;

    public CourseEnrollmentDto(String enrollmentId, String courseId, String courseName,
                               String teacherName, double credit, String classTime,
                               String location, String enrollTime) {
        this.enrollmentId = enrollmentId;
        this.courseId = courseId;
        this.courseName = courseName;
        this.teacherName = teacherName;
        this.credit = credit;
        this.classTime = classTime;
        this.location = location;
        this.enrollTime = enrollTime;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public double getCredit() {
        return credit;
    }

    public String getClassTime() {
        return classTime;
    }

    public String getLocation() {
        return location;
    }

    public String getEnrollTime() {
        return enrollTime;
    }
}
