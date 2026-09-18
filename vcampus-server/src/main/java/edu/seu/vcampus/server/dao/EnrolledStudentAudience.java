package edu.seu.vcampus.server.dao;

/**
 * 一个已选学生的受众判定字段（院系 + 入学年份）。
 *
 * <p>管理员收紧教学班受众时，服务层用这些字段重新判定该班已选学生是否仍然符合
 * 受众规则，避免出现“名单里有人、但他已经不满足受众”的记录。
 */
public final class EnrolledStudentAudience {
    private final String studentId;
    private final String fullName;
    private final String departmentId;
    private final int enrollmentYear;

    public EnrolledStudentAudience(String studentId, String fullName,
                                   String departmentId, int enrollmentYear) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.departmentId = departmentId;
        this.enrollmentYear = enrollmentYear;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public int getEnrollmentYear() {
        return enrollmentYear;
    }
}
