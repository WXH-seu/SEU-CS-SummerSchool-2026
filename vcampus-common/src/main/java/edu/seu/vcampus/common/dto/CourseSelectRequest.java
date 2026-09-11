package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Request body used to select one course section. */
public final class CourseSelectRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sectionId;

    public CourseSelectRequest(String sectionId) {
        this.sectionId = sectionId;
    }

    public String getSectionId() {
        return sectionId;
    }
}
