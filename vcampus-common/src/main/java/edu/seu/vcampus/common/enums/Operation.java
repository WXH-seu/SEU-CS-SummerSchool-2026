package edu.seu.vcampus.common.enums;

import java.io.Serializable;

/** Operations supported by the vCampus object-stream protocol. */
public enum Operation implements Serializable {
    PING,
    USER_LOGIN,
    USER_LOGOUT,
    USER_REGISTER,
    USER_IMPORT_CSV,
    USER_ACCOUNT_QUERY,
    USER_PROFILE_UPDATE,
    USER_PASSWORD_CHANGE,
    USER_DELETE,
    USER_LIST_QUERY,
    USER_STATUS_UPDATE,
    USER_AUDIT_QUERY,
    STUDENT_QUERY,
    STUDENT_SAVE,
    COURSE_QUERY,
    COURSE_SELECT,
    COURSE_DROP,
    LIBRARY_BOOK_QUERY,
    LIBRARY_BORROW,
    LIBRARY_RETURN,
    STORE_PRODUCT_QUERY,
    STORE_ORDER_CREATE
}
