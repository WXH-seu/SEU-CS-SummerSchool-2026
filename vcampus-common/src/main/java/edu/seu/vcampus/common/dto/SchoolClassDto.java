package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** School class data shared by client and server. */
public final class SchoolClassDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String classId;
    private final String className;
    private final String departmentId;
    private final int gradeYear;
    private final String counselor;
    private final boolean active;

    public SchoolClassDto(String classId, String className, String departmentId,
                          int gradeYear, String counselor, boolean active) {
        this.classId = classId;
        this.className = className;
        this.departmentId = departmentId;
        this.gradeYear = gradeYear;
        this.counselor = counselor;
        this.active = active;
    }

    public String getClassId() {
        return classId;
    }

    public String getClassName() {
        return className;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public int getGradeYear() {
        return gradeYear;
    }

    public String getCounselor() {
        return counselor;
    }

    public boolean isActive() {
        return active;
    }
}
