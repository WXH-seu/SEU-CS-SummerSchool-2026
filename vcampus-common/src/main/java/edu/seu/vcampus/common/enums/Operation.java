package edu.seu.vcampus.common.enums;

import java.io.Serializable;

/** Operations supported by the vCampus object-stream protocol. */
public enum Operation implements Serializable {
    PING,
    USER_LOGIN,
    USER_LOGOUT,
    STUDENT_QUERY,
    STUDENT_SAVE,
    STUDENT_DELETE,
    TEACHER_QUERY,
    TEACHER_SAVE,
    TEACHER_DELETE,
    DEPARTMENT_QUERY,
    DEPARTMENT_SAVE,
    DEPARTMENT_DELETE,
    CLASS_QUERY,
    CLASS_SAVE,
    CLASS_DELETE,
    COURSE_QUERY,
    COURSE_SELECT,
    COURSE_DROP,
    LIBRARY_BOOK_QUERY,
    LIBRARY_BORROW,
    LIBRARY_RETURN,
    STORE_PRODUCT_QUERY,
    STORE_ORDER_CREATE
}
