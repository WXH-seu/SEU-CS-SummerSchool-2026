package edu.seu.vcampus.server.dao;

/** A catalog course imported from an official SEU curriculum snapshot. */
public final class Course {
    private final String courseId;
    private final String departmentId;
    private final String courseName;
    private final double credits;
    private final int lectureHours;
    private final int practiceHours;
    private final String courseType;
    private final int sourceYear;
    private final String sourceUrl;
    private final boolean active;

    public Course(String courseId, String departmentId, String courseName, double credits,
                  int lectureHours, int practiceHours, String courseType, int sourceYear,
                  String sourceUrl, boolean active) {
        this.courseId = courseId;
        this.departmentId = departmentId;
        this.courseName = courseName;
        this.credits = credits;
        this.lectureHours = lectureHours;
        this.practiceHours = practiceHours;
        this.courseType = courseType;
        this.sourceYear = sourceYear;
        this.sourceUrl = sourceUrl;
        this.active = active;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getCourseName() {
        return courseName;
    }

    public double getCredits() {
        return credits;
    }

    public int getLectureHours() {
        return lectureHours;
    }

    public int getPracticeHours() {
        return practiceHours;
    }

    public String getCourseType() {
        return courseType;
    }

    public int getSourceYear() {
        return sourceYear;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public boolean isActive() {
        return active;
    }
}
