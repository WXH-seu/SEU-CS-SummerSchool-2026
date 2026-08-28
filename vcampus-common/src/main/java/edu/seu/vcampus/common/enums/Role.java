package edu.seu.vcampus.common.enums;

import java.io.Serializable;

/**
 * User roles shared across all required modules.
 *
 * <p>{@link #SUPER_ADMIN} is the global administrator: every administrator
 * account (both {@link #SUBSYSADMIN} and {@link #SUPER_ADMIN}) can only be
 * created by an existing super administrator. {@link #SUBSYSADMIN} is a
 * sub-system administrator (e.g. student-management or library) who operates a
 * business sub-system but has <strong>no</strong> account-management rights.
 */
public enum Role implements Serializable {
    STUDENT,
    TEACHER,
    SUBSYSADMIN,
    SUPER_ADMIN
}
