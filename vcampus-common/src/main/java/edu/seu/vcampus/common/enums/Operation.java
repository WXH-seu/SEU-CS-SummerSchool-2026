package edu.seu.vcampus.common.enums;

import java.io.Serializable;

/** Operations supported by the vCampus object-stream protocol. */
public enum Operation implements Serializable {
    PING,
    USER_LOGIN,
    USER_LOGOUT,
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
