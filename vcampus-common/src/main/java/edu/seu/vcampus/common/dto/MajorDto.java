package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** Read-only major information from the reviewed SEU public-data snapshot. */
public final class MajorDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String majorId;
    private final String departmentId;
    private final String majorName;
    private final String degreeType;
    private final int durationYears;
    private final int sourceYear;
    private final String sourceUrl;
    private final boolean active;

    public MajorDto(String majorId, String departmentId, String majorName,
                    String degreeType, int durationYears, int sourceYear,
                    String sourceUrl, boolean active) {
        this.majorId = majorId;
        this.departmentId = departmentId;
        this.majorName = majorName;
        this.degreeType = degreeType;
        this.durationYears = durationYears;
        this.sourceYear = sourceYear;
        this.sourceUrl = sourceUrl;
        this.active = active;
    }

    public String getMajorId() { return majorId; }
    public String getDepartmentId() { return departmentId; }
    public String getMajorName() { return majorName; }
    public String getDegreeType() { return degreeType; }
    public int getDurationYears() { return durationYears; }
    public int getSourceYear() { return sourceYear; }
    public String getSourceUrl() { return sourceUrl; }
    public boolean isActive() { return active; }
}
