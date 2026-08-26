package edu.seu.vcampus.client.ui;

import edu.seu.vcampus.common.enums.Role;

/** Chinese labels for the shared {@link Role} enum. */
public final class RoleNames {
    private RoleNames() {
    }

    public static String of(Role role) {
        if (role == null) {
            return "未知";
        }
        switch (role) {
            case SUPER_ADMIN:
                return "超级管理员";
            case ADMIN:
                return "管理员";
            case TEACHER:
                return "教师";
            default:
                return "学生";
        }
    }
}
