package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CatalogCourseDto;
import edu.seu.vcampus.common.dto.CatalogQueryRequest;
import edu.seu.vcampus.common.dto.MajorDto;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessCurriculumCatalogRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises read-only catalog filtering with every effective academic role. */
public class CurriculumCatalogServiceIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void studentsCanQueryMajorsAndFilteredCurriculumCourses() throws Exception {
        CurriculumCatalogService service = createService();

        List<MajorDto> majors = service.queryMajors("student", SubSystemRole.STUDENT,
                new CatalogQueryRequest("计算机", "CS", null, true));
        assertEquals(1, majors.size());
        assertEquals("080901", majors.get(0).getMajorId());

        List<CatalogCourseDto> courses = service.queryCourses(
                "student", SubSystemRole.STUDENT,
                new CatalogQueryRequest("编译", "CS", "080901", true));
        assertEquals(1, courses.size());
        assertEquals("编译原理", courses.get(0).getCourseName());
        assertTrue(courses.get(0).getRecommendedYear() > 0);
        assertFalse(courses.get(0).getSourceUrl().isEmpty());
    }

    @Test
    public void courseQueryRequiresMajorCode() throws Exception {
        CurriculumCatalogService service = createService();
        try {
            service.queryCourses("teacher", SubSystemRole.TEACHER,
                    new CatalogQueryRequest(null, null, null, false));
            fail("Major code should be required");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.INVALID_REQUEST, expected.getResponseCode());
        }
    }

    private CurriculumCatalogService createService() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "catalog-service.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        new AccessUserRepository(database, new PasswordHasher());
        new AccessAcademicRepository(database);
        return new CurriculumCatalogService(
                new AccessCurriculumCatalogRepository(database));
    }
}
