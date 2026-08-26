package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Department data shared by client and server. */
public final class DepartmentDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String departmentId;
    private final String departmentName;
    private final String description;
    private final boolean active;

    public DepartmentDto(String departmentId, String departmentName,
                         String description, boolean active) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.description = description;
        this.active = active;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
