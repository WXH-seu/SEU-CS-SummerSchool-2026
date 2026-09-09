package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.dto.SectionScheduleDto;

/** One enrolled student together with one schedule of another section. */
public final class EnrolledStudentSchedule {
    private final String studentId;
    private final String fullName;
    private final SectionScheduleDto schedule;

    public EnrolledStudentSchedule(String studentId, String fullName,
                                   SectionScheduleDto schedule) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.schedule = schedule;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getFullName() {
        return fullName;
    }

    public SectionScheduleDto getSchedule() {
        return schedule;
    }
}
