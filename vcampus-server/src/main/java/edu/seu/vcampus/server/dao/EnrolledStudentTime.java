package edu.seu.vcampus.server.dao;

/**
 * One enrolled student of a section together with the class time of another
 * section that student also takes. Used by the edit-time conflict guard.
 */
public final class EnrolledStudentTime {
    private final String studentId;
    private final String fullName;
    private final String classTime;

    public EnrolledStudentTime(String studentId, String fullName, String classTime) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.classTime = classTime;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getClassTime() {
        return classTime;
    }
}
