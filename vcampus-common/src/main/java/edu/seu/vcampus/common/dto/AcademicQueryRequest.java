package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Filter used by academic information queries. */
public final class AcademicQueryRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String keyword;
    private final String departmentId;
    private final String classId;
    private final boolean activeOnly;

    public AcademicQueryRequest(String keyword, String departmentId,
                                String classId, boolean activeOnly) {
        this.keyword = keyword;
        this.departmentId = departmentId;
        this.classId = classId;
        this.activeOnly = activeOnly;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getClassId() {
        return classId;
    }

    public boolean isActiveOnly() {
        return activeOnly;
    }
}
