package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** One student on the roster of a course section. */
public final class SectionRosterEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sectionId;
    private final String courseId;
    private final String courseName;
    private final String studentId;
    private final String fullName;
    private final String departmentName;
    private final String className;
    private final String attemptType;
    private final String phone;
    private final String email;
    private final String enrollTime;

    public SectionRosterEntry(String sectionId, String courseId, String courseName,
                              String studentId, String fullName, String departmentName,
                              String className, String enrollTime) {
        this(sectionId, courseId, courseName, studentId, fullName, departmentName,
                className, null, null, null, enrollTime);
    }

    public SectionRosterEntry(String sectionId, String courseId, String courseName,
                              String studentId, String fullName, String departmentName,
                              String className, String attemptType, String phone,
                              String email, String enrollTime) {
        this.sectionId = sectionId;
        this.courseId = courseId;
        this.courseName = courseName;
        this.studentId = studentId;
        this.fullName = fullName;
        this.departmentName = departmentName;
        this.className = className;
        this.attemptType = attemptType;
        this.phone = phone;
        this.email = email;
        this.enrollTime = enrollTime;
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

    public String getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getClassName() {
        return className;
    }

    public String getAttemptType() {
        return attemptType;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getEnrollTime() {
        return enrollTime;
    }
}
