package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * 上课时间的规范文本，例如 {@code 周一 3-4 节（1-16周）}，多条时段用「；」连接。
     *
     * <p>服务端写库（{@code tblCourseSection.classTime}）与客户端展示共用本实现。
     * 该列同时参与文本冲突判定（逐字比较），两端各写一套格式会让同一个时间出现
     * 两种写法并导致冲突漏判，因此格式只能在这里定义。
     */
    public static String summary(List<SectionScheduleDto> slots) {
        if (slots == null || slots.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < slots.size(); i++) {
            SectionScheduleDto slot = slots.get(i);
            if (i > 0) {
                text.append('；');
            }
            text.append(weekdayName(slot.getWeekday())).append(' ')
                    .append(slot.getPeriodStart()).append('-')
                    .append(slot.getPeriodEnd()).append(" 节（")
                    .append(slot.getWeekStart()).append('-')
                    .append(slot.getWeekEnd()).append("周）");
        }
        return text.toString();
    }

    /** 上课地点的规范文本：去重后按出现顺序用「；」连接。 */
    public static String locationSummary(List<SectionScheduleDto> slots) {
        if (slots == null || slots.isEmpty()) {
            return "";
        }
        Set<String> locations = new LinkedHashSet<String>();
        for (SectionScheduleDto slot : slots) {
            if (slot.getLocation() != null && !slot.getLocation().trim().isEmpty()) {
                locations.add(slot.getLocation().trim());
            }
        }
        StringBuilder text = new StringBuilder();
        int index = 0;
        for (String location : locations) {
            if (index++ > 0) {
                text.append('；');
            }
            text.append(location);
        }
        return text.toString();
    }

    public static String weekdayName(int weekday) {
        switch (weekday) {
            case 1: return "周一";
            case 2: return "周二";
            case 3: return "周三";
            case 4: return "周四";
            case 5: return "周五";
            case 6: return "周六";
            case 7: return "周日";
            default: return "周" + weekday;
        }
    }
}
