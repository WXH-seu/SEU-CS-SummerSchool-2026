package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Student status data shared by client and server. */
public final class StudentDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String studentId;
    private final String userId;
    private final String fullName;
    private final String gender;
    private final String birthDate;
    private final String departmentId;
    private final String classId;
    private final int enrollmentYear;
    private final String status;
    private final String phone;
    private final String email;

    public StudentDto(String studentId, String userId, String fullName, String gender,
                      String birthDate, String departmentId, String classId,
                      int enrollmentYear, String status, String phone, String email) {
        this.studentId = studentId;
        this.userId = userId;
        this.fullName = fullName;
        this.gender = gender;
        this.birthDate = birthDate;
        this.departmentId = departmentId;
        this.classId = classId;
        this.enrollmentYear = enrollmentYear;
        this.status = status;
        this.phone = phone;
        this.email = email;
    }

    public String getStudentId() { return studentId; }
    public String getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getGender() { return gender; }
    public String getBirthDate() { return birthDate; }
    public String getDepartmentId() { return departmentId; }
    public String getClassId() { return classId; }
    public int getEnrollmentYear() { return enrollmentYear; }
    public String getStatus() { return status; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
}
