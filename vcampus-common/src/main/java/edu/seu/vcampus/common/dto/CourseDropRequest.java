package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Request body used to drop one enrollment. */
public final class CourseDropRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String enrollmentId;

    public CourseDropRequest(String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }
}
