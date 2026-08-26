package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises the real Access schema, seed data, CRUD and role rules. */
public class AcademicServiceIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private AcademicService service;
    private UserAccount admin;
    private UserAccount studentAccount;

    @Before
    public void setUp() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        AccessUserRepository users = new AccessUserRepository(database, new PasswordHasher());
        AccessAcademicRepository academics = new AccessAcademicRepository(database);
        service = new AcademicService(academics, users);
        admin = users.findById("admin");
        studentAccount = users.findById("student");
    }

    @Test
    public void seedsDemoDataAndRestrictsStudentToOwnRecord() throws Exception {
        assertEquals(1, service.queryStudents(studentAccount, null).size());
        assertEquals("20260001",
                service.queryStudents(studentAccount, null).get(0).getStudentId());
        try {
            service.queryTeachers(studentAccount, null);
            fail("Student should not see teacher records");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
    }

    @Test
    public void adminMaintainsAcademicRecordsAndReferenceRules() throws Exception {
        service.saveDepartment(admin,
                new DepartmentDto("EE", "电子科学与工程学院", "测试院系", true));
        service.saveClass(admin,
                new SchoolClassDto("EE2026-01", "电子2026级1班", "EE", 2026, "", true));
        service.saveStudent(admin, new StudentDto("20260002", null, "测试学生", "女",
                "2008-02-03", "EE", "EE2026-01", 2026, "在读", "", ""));

        AcademicQueryRequest query = new AcademicQueryRequest(
                "测试学生", "EE", "EE2026-01", true);
        assertEquals(1, service.queryStudents(admin, query).size());
        try {
            service.deleteDepartment(admin, "EE");
            fail("Referenced department should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }
        service.deleteStudent(admin, "20260002");
        service.deleteClass(admin, "EE2026-01");
        service.deleteDepartment(admin, "EE");
        assertFalse(service.queryDepartments(admin, false).isEmpty());
        assertTrue(service.queryStudents(admin, query).isEmpty());
    }
}
