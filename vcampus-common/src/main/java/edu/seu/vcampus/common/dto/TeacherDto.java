package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Teacher data shared by client and server. */
public final class TeacherDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String teacherId;
    private final String userId;
    private final String fullName;
    private final String departmentId;
    private final String title;
    private final String phone;
    private final String email;
    private final boolean active;

    public TeacherDto(String teacherId, String userId, String fullName,
                      String departmentId, String title, String phone,
                      String email, boolean active) {
        this.teacherId = teacherId;
        this.userId = userId;
        this.fullName = fullName;
        this.departmentId = departmentId;
        this.title = title;
        this.phone = phone;
        this.email = email;
        this.active = active;
    }

    public String getTeacherId() { return teacherId; }
    public String getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getDepartmentId() { return departmentId; }
    public String getTitle() { return title; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
}
