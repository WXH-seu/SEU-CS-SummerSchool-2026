package edu.seu.vcampus.common.enums;

import java.io.Serializable;

/**
 * User roles shared across all required modules.
 *
 * <p>{@link #SUPER_ADMIN} is the global administrator: every administrator
 * account (both {@link #ADMIN} and {@link #SUPER_ADMIN}) can only be created by
 * an existing super administrator. {@link #ADMIN} is a sub-system
 * administrator (e.g. student-management or library) who manages students and
 * teachers but cannot create or manage other administrators.
 */
public enum Role implements Serializable {
    STUDENT,
    TEACHER,
    ADMIN,
    SUPER_ADMIN
}
