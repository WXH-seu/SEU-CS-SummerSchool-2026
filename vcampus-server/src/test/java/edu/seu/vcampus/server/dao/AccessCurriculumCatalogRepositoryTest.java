package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies the reviewed SEU snapshot import without touching the project database. */
public class AccessCurriculumCatalogRepositoryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsCatalogAndImportsReviewedSnapshotIdempotently() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "catalog.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        new AccessUserRepository(database, new PasswordHasher());
        new AccessAcademicRepository(database);

        AccessCurriculumCatalogRepository repository =
                new AccessCurriculumCatalogRepository(database);
        CatalogImportSummary summary = repository.getImportSummary();

        assertEquals(34, summary.getDepartmentCount());
        assertEquals(9, summary.getMajorCount());
        assertEquals(66, summary.getCourseCount());
        assertEquals(98, summary.getMajorCourseCount());
        assertEquals(34, count(database, "tblDepartment"));
        assertEquals(7, count(database, "tblCatalogSource"));
        assertEquals(9, count(database, "tblMajor"));
        assertEquals(66, count(database, "tblCatalogCourse"));
        assertEquals(98, count(database, "tblMajorCourse"));

        List<Major> computerMajors = repository.findMajors("CS");
        assertEquals(1, computerMajors.size());
        assertEquals("计算机科学与技术", computerMajors.get(0).getMajorName());
        assertTrue(computerMajors.get(0).getSourceUrl().startsWith("https://cs.seu.edu.cn/"));

        List<CatalogCourse> computerCourses = repository.findCoursesForMajor("080901");
        assertTrue(computerCourses.size() >= 10);
        assertTrue(containsCourse(computerCourses, "B71S0032", "编译原理"));

        new AccessCurriculumCatalogRepository(database);
        assertEquals(34, count(database, "tblDepartment"));
        assertEquals(66, count(database, "tblCatalogCourse"));
        assertEquals(98, count(database, "tblMajorCourse"));
        assertTrue(hasForeignKey(database, "tblMajorCourse", "majorId", "tblMajor"));
        assertTrue(hasForeignKey(database, "tblMajorCourse", "courseId", "tblCatalogCourse"));
    }

    private boolean containsCourse(List<CatalogCourse> courses, String id, String name) {
        for (CatalogCourse course : courses) {
            if (id.equals(course.getCourseId()) && name.equals(course.getCourseName())) {
                return true;
            }
        }
        return false;
    }

    private int count(AccessDatabase database, String table) throws Exception {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM [" + table + "]")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private boolean hasForeignKey(AccessDatabase database, String table, String column,
                                  String parentTable) throws Exception {
        try (Connection connection = database.openConnection();
             ResultSet keys = connection.getMetaData().getImportedKeys(null, null, table)) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && parentTable.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
