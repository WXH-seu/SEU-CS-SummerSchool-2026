package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/**
 * One audience rule of a course section. A rule selects students whose
 * department matches {@code scopeValue} (or every student when the scope is
 * {@code ALL}) and whose enrollment year lies inside {@code yearMin..yearMax}
 * (open bounds mean unrestricted).
 */
public final class SectionAudienceDto implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_DEPARTMENT = "DEPARTMENT";

    private final String audienceId;
    private final String scopeType;
    private final String scopeValue;
    private final Integer yearMin;
    private final Integer yearMax;

    public SectionAudienceDto(String audienceId, String scopeType, String scopeValue,
                              Integer yearMin, Integer yearMax) {
        this.audienceId = audienceId;
        this.scopeType = scopeType;
        this.scopeValue = scopeValue;
        this.yearMin = yearMin;
        this.yearMax = yearMax;
    }

    public String getAudienceId() {
        return audienceId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public String getScopeValue() {
        return scopeValue;
    }

    public Integer getYearMin() {
        return yearMin;
    }

    public Integer getYearMax() {
        return yearMax;
    }
}
