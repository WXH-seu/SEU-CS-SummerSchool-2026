package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/**
 * One recurring meeting slot of a course section: weekday, period range and
 * week range (both inclusive) plus a location. A section may have several
 * slots; period and week ranges are contiguous only.
 */
public final class SectionScheduleDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int weekday;
    private final int periodStart;
    private final int periodEnd;
    private final int weekStart;
    private final int weekEnd;
    private final String location;

    public SectionScheduleDto(int weekday, int periodStart, int periodEnd,
                              int weekStart, int weekEnd, String location) {
        this.weekday = weekday;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
        this.location = location;
    }

    public int getWeekday() {
        return weekday;
    }

    public int getPeriodStart() {
        return periodStart;
    }

    public int getPeriodEnd() {
        return periodEnd;
    }

    public int getWeekStart() {
        return weekStart;
    }

    public int getWeekEnd() {
        return weekEnd;
    }

    public String getLocation() {
        return location;
    }
}
