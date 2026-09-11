package edu.seu.vcampus.server.dao;

/** Counts from one idempotent curriculum snapshot import. */
public final class CatalogImportSummary {
    private final int departmentCount;
    private final int majorCount;
    private final int courseCount;
    private final int majorCourseCount;

    public CatalogImportSummary(int departmentCount, int majorCount, int courseCount,
                                int majorCourseCount) {
        this.departmentCount = departmentCount;
        this.majorCount = majorCount;
        this.courseCount = courseCount;
        this.majorCourseCount = majorCourseCount;
    }

    public int getDepartmentCount() {
        return departmentCount;
    }

    public int getMajorCount() {
        return majorCount;
    }

    public int getCourseCount() {
        return courseCount;
    }

    public int getMajorCourseCount() {
        return majorCourseCount;
    }
}
