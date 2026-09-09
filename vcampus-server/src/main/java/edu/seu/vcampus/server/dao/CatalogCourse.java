package edu.seu.vcampus.server.dao;

/** A catalog course imported from an official SEU curriculum snapshot. */
public final class CatalogCourse {
    private final String courseId;
    private final String departmentId;
    private final String courseName;
    private final double credits;
    private final int lectureHours;
    private final int practiceHours;
    private final String courseType;
    private final int recommendedYear;
    private final int recommendedSemester;
    private final boolean required;
    private final int sourceYear;
    private final String sourceUrl;
    private final boolean active;

    public CatalogCourse(String courseId, String departmentId, String courseName, double credits,
                         int lectureHours, int practiceHours, String courseType,
                         int recommendedYear, int recommendedSemester, boolean required,
                         int sourceYear, String sourceUrl, boolean active) {
        this.courseId = courseId;
        this.departmentId = departmentId;
        this.courseName = courseName;
        this.credits = credits;
        this.lectureHours = lectureHours;
        this.practiceHours = practiceHours;
        this.courseType = courseType;
        this.recommendedYear = recommendedYear;
        this.recommendedSemester = recommendedSemester;
        this.required = required;
        this.sourceYear = sourceYear;
        this.sourceUrl = sourceUrl;
        this.active = active;
    }

    public String getCourseId() { return courseId; }
    public String getDepartmentId() { return departmentId; }
    public String getCourseName() { return courseName; }
    public double getCredits() { return credits; }
    public int getLectureHours() { return lectureHours; }
    public int getPracticeHours() { return practiceHours; }
    public String getCourseType() { return courseType; }
    public int getRecommendedYear() { return recommendedYear; }
    public int getRecommendedSemester() { return recommendedSemester; }
    public boolean isRequired() { return required; }
    public int getSourceYear() { return sourceYear; }
    public String getSourceUrl() { return sourceUrl; }
    public boolean isActive() { return active; }
}
