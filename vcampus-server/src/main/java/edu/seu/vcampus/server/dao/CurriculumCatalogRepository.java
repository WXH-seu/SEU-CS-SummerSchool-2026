package edu.seu.vcampus.server.dao;

import java.sql.SQLException;
import java.util.List;

/** Read and bootstrap operations for departments, majors and catalog courses. */
public interface CurriculumCatalogRepository {
    CatalogImportSummary importBundledSnapshot() throws SQLException;

    List<Major> findMajors(String departmentId) throws SQLException;

    List<CatalogCourse> findCoursesForMajor(String majorId) throws SQLException;
}
