package edu.seu.vcampus.server.database;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessBookRepository;
import edu.seu.vcampus.server.dao.AccessCourseRepository;
import edu.seu.vcampus.server.dao.AccessCurriculumCatalogRepository;
import edu.seu.vcampus.server.dao.AccessOperationLogRepository;
import edu.seu.vcampus.server.dao.AccessStoreRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.service.CourseService;
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

/** Boots every module against one temporary Access file and checks core relations. */
public class FullDatabaseBootstrapTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void initializesEveryModuleAndRestartsIdempotently() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "full-vcampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());

        initializeAll(database);
        assertEquals(34, count(database, "tblDepartment"));
        assertEquals(9, count(database, "tblMajor"));
        assertEquals(66, count(database, "tblCatalogCourse"));
        assertEquals(11, count(database, "tblUser"));
        assertEquals(12, count(database, "tblSchoolClass"));
        assertEquals(120, count(database, "tblStudent"));
        assertEquals(12, count(database, "tblTeacher"));
        assertEquals(24, count(database, "tblCourse"));
        assertEquals(30, count(database, "tblCourseSection"));
        assertEquals(150, count(database, "tblCourseEnrollment"));
        assertEquals(21, count(database, "tblCourseRecord"));
        assertTrue(count(database, "tblProduct") >= 6);
        assertTrue(hasForeignKey(database, "tblStudent", "userId", "tblUser"));
        assertTrue(hasForeignKey(database, "tblCourseEnrollment", "studentId", "tblStudent"));
        assertTrue(hasForeignKey(database, "tblCourseEnrollment", "sectionId",
                "tblCourseSection"));
        assertTrue(hasForeignKey(database, "tblCourseSection", "teacherId", "tblTeacher"));
        assertTrue(hasForeignKey(database, "tblBorrowRecord", "userId", "tblUser"));
        assertTrue(hasForeignKey(database, "tblCartItem", "userId", "tblUser"));
        assertTrue(hasForeignKey(database, "tblOrder", "userId", "tblUser"));
        assertTrue(hasForeignKey(database, "tblMajorCourse", "courseId", "tblCatalogCourse"));

        initializeAll(database);
        assertEquals(34, count(database, "tblDepartment"));
        assertEquals(9, count(database, "tblMajor"));
        assertEquals(66, count(database, "tblCatalogCourse"));
        assertEquals(11, count(database, "tblUser"));
        assertEquals(120, count(database, "tblStudent"));
        assertEquals(30, count(database, "tblCourseSection"));
        assertEquals(150, count(database, "tblCourseEnrollment"));
    }

    @Test
    public void expandedPeopleHaveLinkedAccountsAndUsefulCourseChoices() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "expanded-vcampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        new AccessUserRepository(database, new PasswordHasher());
        AccessAcademicRepository academics = new AccessAcademicRepository(database);
        new AccessCurriculumCatalogRepository(database);
        academics.seedExpandedDemoData();
        AccessCourseRepository courses = new AccessCourseRepository(database);
        courses.seedExpandedDemoData();

        assertEquals(6, countWhere(database, "tblStudent", "[userId] IS NOT NULL"));
        assertEquals(3, countWhere(database, "tblTeacher", "[userId] IS NOT NULL"));
        CourseService service = new CourseService(courses);
        String[] studentUsers = {
                "student", "student02", "student03", "student04", "student05", "student06"
        };
        for (String userId : studentUsers) {
            assertTrue(academics.findStudentByUserId(userId) != null);
            assertTrue("Expected at least eight visible sections for " + userId,
                    service.queryCourses(userId, SubSystemRole.STUDENT, null).size() >= 8);
        }
        List<CourseDto> studentCourses = service.queryCourses(
                "student", SubSystemRole.STUDENT, null);
        assertEquals("与已选课程时间冲突",
                findSection(studentCourses, "DEMO-SEC-001").getReason());
        assertEquals("首修名额已满",
                findSection(studentCourses, "DEMO-SEC-002").getReason());
        assertEquals("尚未开始选课",
                findSection(studentCourses, "DEMO-SEC-017").getReason());
        assertEquals(CourseDto.ATTEMPT_RETAKE,
                findSection(studentCourses, "SEC00000002").getAttemptType());
        assertTrue(courses.findSections(null).size() >= 30);
    }

    private CourseDto findSection(List<CourseDto> sections, String sectionId) {
        for (CourseDto section : sections) {
            if (sectionId.equals(section.getSectionId())) {
                return section;
            }
        }
        throw new AssertionError("Missing section " + sectionId);
    }

    private void initializeAll(AccessDatabase database) throws Exception {
        new AccessUserRepository(database, new PasswordHasher());
        new AccessOperationLogRepository(database.getDatabaseFile().getAbsolutePath());
        AccessAcademicRepository academics = new AccessAcademicRepository(database);
        new AccessCurriculumCatalogRepository(database);
        academics.seedExpandedDemoData();
        AccessCourseRepository courses = new AccessCourseRepository(database);
        courses.seedExpandedDemoData();
        new AccessBookRepository(database);
        new AccessStoreRepository(database);
    }

    private int count(AccessDatabase database, String table) throws Exception {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM [" + table + "]")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private int countWhere(AccessDatabase database, String table, String condition)
            throws Exception {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM [" + table + "] WHERE " + condition)) {
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
