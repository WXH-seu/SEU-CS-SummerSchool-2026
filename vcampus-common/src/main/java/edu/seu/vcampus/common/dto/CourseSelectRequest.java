package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Request body used to select one course. */
public final class CourseSelectRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String courseId;

    public CourseSelectRequest(String courseId) {
        this.courseId = courseId;
    }

    public String getCourseId() {
        return courseId;
    }
}
