package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.StudentImportRequest;
import edu.seu.vcampus.common.dto.StudentImportResponse;
import edu.seu.vcampus.common.dto.StudentProfileUpdateRequest;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessBookRepository;
import edu.seu.vcampus.server.dao.AccessStoreRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises the real Access schema, seed data, CRUD and role rules. */
public class AcademicServiceIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private AcademicService service;
    private File databaseFile;

    @Before
    public void setUp() throws Exception {
        databaseFile = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(databaseFile.getAbsolutePath());
        AccessUserRepository users = new AccessUserRepository(database, new PasswordHasher());
        AccessAcademicRepository academics = new AccessAcademicRepository(database);
        service = new AcademicService(academics, users);
    }

    @Test
    public void seedsDemoDataAndRestrictsStudentToOwnRecord() throws Exception {
        assertEquals(1, service.queryStudents("student", SubSystemRole.STUDENT, null).size());
        assertEquals("20260001",
                service.queryStudents("student", SubSystemRole.STUDENT, null).get(0).getStudentId());
        AcademicQueryRequest anotherStudent = new AcademicQueryRequest(
                "不存在的其他学生", null, null, false);
        assertEquals("20260001", service.queryStudents(
                "student", SubSystemRole.STUDENT, anotherStudent).get(0).getStudentId());
        try {
            service.queryTeachers("student", SubSystemRole.STUDENT, null);
            fail("Student should not see teacher records");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
        try {
            service.deleteStudent("student", SubSystemRole.STUDENT, "20260001");
            fail("Student should not modify academic records");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
    }

    @Test
    public void adminMaintainsAcademicRecordsAndReferenceRules() throws Exception {
        service.saveDepartment("admin", SubSystemRole.ADMIN,
                new DepartmentDto("EE", "电子科学与工程学院", "测试院系", true));
        service.saveClass("admin", SubSystemRole.ADMIN,
                new SchoolClassDto("EE2026-01", "电子2026级1班", "EE", 2026, "", true));
        service.saveStudent("admin", SubSystemRole.ADMIN,
                new StudentDto("20260002", null, "测试学生", "女",
                "2008-02-03", "EE", "EE2026-01", 2026, "在读", "", ""));

        AcademicQueryRequest query = new AcademicQueryRequest(
                "测试学生", "EE", "EE2026-01", true);
        assertEquals(1, service.queryStudents("admin", SubSystemRole.ADMIN, query).size());
        try {
            service.deleteDepartment("admin", SubSystemRole.ADMIN, "EE");
            fail("Referenced department should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }
        service.deleteStudent("admin", SubSystemRole.ADMIN, "20260002");
        service.deleteClass("admin", SubSystemRole.ADMIN, "EE2026-01");
        service.deleteDepartment("admin", SubSystemRole.ADMIN, "EE");
        assertFalse(service.queryDepartments("admin", SubSystemRole.ADMIN, false).isEmpty());
        assertTrue(service.queryStudents("admin", SubSystemRole.ADMIN, query).isEmpty());
    }

    @Test
    public void studentUpdatesOnlyAllowedPersonalFields() throws Exception {
        StudentDto before = service.queryStudents(
                "student", SubSystemRole.STUDENT, null).get(0);
        StudentDto updated = service.updateOwnProfile("student", SubSystemRole.STUDENT,
                new StudentProfileUpdateRequest(
                        "女", "2008-02-02", "13900000000", "new@vcampus.local"));

        assertEquals(before.getStudentId(), updated.getStudentId());
        assertEquals(before.getDepartmentId(), updated.getDepartmentId());
        assertEquals(before.getClassId(), updated.getClassId());
        assertEquals("女", updated.getGender());
        assertEquals("13900000000", updated.getPhone());
        assertEquals("new@vcampus.local", updated.getEmail());
    }

    @Test
    public void autoAssignsLeastFilledClassAndBatchReportsFailures() throws Exception {
        service.saveDepartment("admin", SubSystemRole.ADMIN,
                new DepartmentDto("TEST", "自动分班测试学院", "", true));
        service.saveClass("admin", SubSystemRole.ADMIN,
                new SchoolClassDto("TEST-01", "自动分班测试1班",
                        "TEST", 2026, "", 1, true));
        service.saveClass("admin", SubSystemRole.ADMIN,
                new SchoolClassDto("TEST-02", "自动分班测试2班",
                        "TEST", 2026, "", 2, true));

        service.saveStudent("admin", SubSystemRole.ADMIN, student("20269901", "甲"));
        assertEquals("TEST-01", service.queryStudents("admin", SubSystemRole.ADMIN,
                new AcademicQueryRequest("20269901", null, null, false))
                .get(0).getClassId());

        StudentImportResponse response = service.importStudents(
                "admin", SubSystemRole.ADMIN, new StudentImportRequest(Arrays.asList(
                        student("20269902", "乙"), student("20269901", "重复学号"))));
        assertEquals(1, response.getImported());
        assertEquals(1, response.getFailures().size());
        assertEquals("TEST-02", service.queryStudents("admin", SubSystemRole.ADMIN,
                new AcademicQueryRequest("20269902", null, null, false))
                .get(0).getClassId());
    }

    @Test
    public void cannotDeleteStudentOrTeacherWithLibraryRecords() throws Exception {
        new AccessBookRepository(new AccessDatabase(databaseFile.getAbsolutePath()));
        try {
            service.deleteStudent("admin", SubSystemRole.ADMIN, "20260001");
            fail("Student with borrow and wish records should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("借阅"));
        }
        try {
            service.deleteTeacher("admin", SubSystemRole.ADMIN, "T0001");
            fail("Teacher with a pending wish should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("借阅"));
        }

        service.saveStudent("admin", SubSystemRole.ADMIN,
                new StudentDto("20260099", null, "无图书馆记录学生", "男",
                        "2008-01-01", "CS", "CS2026-01", 2026, "在读", "", ""));
        service.deleteStudent("admin", SubSystemRole.ADMIN, "20260099");
        assertTrue(service.queryStudents("admin", SubSystemRole.ADMIN,
                new AcademicQueryRequest("20260099", null, null, false)).isEmpty());
    }

    @Test
    public void cannotDeleteStudentOrTeacherWithStoreRecords() throws Exception {
        AccessStoreRepository store = new AccessStoreRepository(
                new AccessDatabase(databaseFile.getAbsolutePath()));
        store.upsertCartItem("student", "P001", 1);
        store.upsertCartItem("teacher", "P002", 1);
        store.createOrder("teacher", Collections.singleton("P002"));

        try {
            service.deleteStudent("admin", SubSystemRole.ADMIN, "20260001");
            fail("Student with cart records should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("购物车"));
        }
        try {
            service.deleteTeacher("admin", SubSystemRole.ADMIN, "T0001");
            fail("Teacher with order records should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("订单"));
        }
    }

    private StudentDto student(String studentId, String name) {
        return new StudentDto(studentId, null, name, "男", "2008-01-01",
                "TEST", "", 2026, "在读", "", "");
    }
}
