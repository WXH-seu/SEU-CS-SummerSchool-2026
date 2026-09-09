package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Filters the reviewed SEU major and curriculum catalog. */
public final class CatalogQueryRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String keyword;
    private final String departmentId;
    private final String majorId;
    private final boolean activeOnly;

    public CatalogQueryRequest(String keyword, String departmentId,
                               String majorId, boolean activeOnly) {
        this.keyword = keyword;
        this.departmentId = departmentId;
        this.majorId = majorId;
        this.activeOnly = activeOnly;
    }

    public String getKeyword() { return keyword; }
    public String getDepartmentId() { return departmentId; }
    public String getMajorId() { return majorId; }
    public boolean isActiveOnly() { return activeOnly; }
}
