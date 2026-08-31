package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Filter used by course catalog queries. */
public final class CourseQueryRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String keyword;
    private final String departmentId;
    private final String teacherId;
    private final String semesterName;
    private final boolean activeOnly;

    public CourseQueryRequest(String keyword, String departmentId, String teacherId,
                              String semesterName, boolean activeOnly) {
        this.keyword = keyword;
        this.departmentId = departmentId;
        this.teacherId = teacherId;
        this.semesterName = semesterName;
        this.activeOnly = activeOnly;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public String getSemesterName() {
        return semesterName;
    }

    public boolean isActiveOnly() {
        return activeOnly;
    }
}
