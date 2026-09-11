package edu.seu.vcampus.common.enums;

import java.io.Serializable;

/**
 * The three-tier role a sub-system sees for the current session. The user
 * module normalises its four role values ({@link Role#SUPER_ADMIN},
 * {@link Role#SUBSYSADMIN}, {@link Role#TEACHER}, {@link Role#STUDENT}) plus the
 * scoped sub-system grants into one of these three values for a specific
 * sub-system, so sub-system modules only need to reason about
 * {@code 管理员 / 教师 / 学生} and stay unaware of the authorisation model.
 *
 * <p>Mapping (per sub-system):
 * <ul>
 *   <li>{@link Role#SUPER_ADMIN} or a {@link Role#SUBSYSADMIN} granted this
 *       sub-system &rarr; {@link #ADMIN}</li>
 *   <li>a {@link Role#SUBSYSADMIN} <em>not</em> granted this sub-system, or a
 *       {@link Role#TEACHER} &rarr; {@link #TEACHER}</li>
 *   <li>{@link Role#STUDENT} &rarr; {@link #STUDENT}</li>
 * </ul>
 */
public enum SubSystemRole implements Serializable {
    ADMIN("管理员"),
    TEACHER("教师"),
    STUDENT("学生");

    private final String displayName;

    SubSystemRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
