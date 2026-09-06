package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseDropRequest;
import edu.seu.vcampus.common.dto.CourseSelectRequest;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystemRole;
import edu.seu.vcampus.common.enums.SubSystems;
import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessCourseRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises the real Access course schema, business rules and role checks. */
public class CourseServiceIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private CourseService service;
    private AccessCourseRepository courseRepository;
    private AcademicService academicService;
    private UserAccount admin;
    private UserAccount student;
    private UserAccount teacher;

    private SubSystemRole eff(UserAccount actor) {
        return SubSystems.effectiveRole(actor.getRole(), actor.getAdminScopes(), SubSystem.COURSE);
    }

    @Before
    public void setUp() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        AccessUserRepository users = new AccessUserRepository(database, new PasswordHasher());
        AccessAcademicRepository academics = new AccessAcademicRepository(database);
        courseRepository = new AccessCourseRepository(database);
        service = new CourseService(courseRepository);
        academicService = new AcademicService(academics, users);
        admin = users.findById("admin");
        student = users.findById("student");
        teacher = users.findById("teacher");
    }

    @Test
    public void seedsDemoCoursesAndRestrictsTeacherToOwnCourses() throws Exception {
        assertEquals(2, service.queryCourses(student.getUserId(), eff(student),  null).size());
        assertEquals(1, service.querySchedule(student.getUserId(), eff(student)).size());
        assertEquals("CS101", service.querySchedule(student.getUserId(), eff(student)).get(0).getCourseId());

        assertTrue(service.queryCourses(teacher.getUserId(), eff(teacher),  null).stream()
                .allMatch(course -> "T0001".equals(course.getTeacherId())));
        assertEquals(2, service.queryCourses(teacher.getUserId(), eff(teacher),  null).size());
    }

    @Test
    public void duplicateSelectionIsRejected() throws Exception {
        try {
            service.selectCourse(student.getUserId(), eff(student),  new CourseSelectRequest("CS101"));
            fail("Duplicate selection should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }
    }

    @Test
    public void capacityLimitIsEnforced() throws Exception {
        service.saveCourse(admin.getUserId(), eff(admin),  demoCourse("CS201", "软件工程", 1));
        service.selectCourse(student.getUserId(), eff(student),  new CourseSelectRequest("CS201"));

        academicService.saveStudent(admin.getUserId(), eff(admin),  new StudentDto("20260003", null, "第二名学生",
                "女", "2008-03-04", "CS", "CS2026-01", 2026, "在读", "", ""));
        courseRepository.insertEnrollment("20260003", "CS201",
                "FILL-001", "2026-08-25 10:00:00");

        try {
            service.selectCourse(student.getUserId(), eff(student),  new CourseSelectRequest("CS201"));
            fail("Full course should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }
    }

    @Test
    public void timeConflictIsEnforced() throws Exception {
        service.saveCourse(admin.getUserId(), eff(admin),  demoCourse("CS202", "计算机网络",
                "周一 3-4 节", 30));
        try {
            service.selectCourse(student.getUserId(), eff(student),  new CourseSelectRequest("CS202"));
            fail("Time conflict should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }
    }

    @Test
    public void studentCanDropOwnEnrollment() throws Exception {
        assertEquals(1, service.querySchedule(student.getUserId(), eff(student)).size());
        String enrollmentId = service.querySchedule(student.getUserId(), eff(student)).get(0).getEnrollmentId();
        service.dropCourse(student.getUserId(), eff(student),  new CourseDropRequest(enrollmentId));
        assertTrue(service.querySchedule(student.getUserId(), eff(student)).isEmpty());

        try {
            service.dropCourse(student.getUserId(), eff(student),  new CourseDropRequest(enrollmentId));
            fail("Dropping the same enrollment twice should fail");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.NOT_FOUND, expected.getResponseCode());
        }
    }

    @Test
    public void studentWithEnrollmentsCannotBeDeleted() throws Exception {
        try {
            academicService.deleteStudent(admin.getUserId(), eff(admin),  "20260001");
            fail("Student with enrollments should be protected by the foreign key");
        } catch (SQLException expected) {
            // The tblCourseEnrollment foreign key blocks the deletion.
        }
    }

    @Test
    public void adminMaintainsCoursesAndRolesAreEnforced() throws Exception {
        service.saveCourse(admin.getUserId(), eff(admin),  demoCourse("CS201", "软件工程", 30));
        assertEquals(3, service.queryCourses(admin.getUserId(), eff(admin),  null).size());

        try {
            service.selectCourse(teacher.getUserId(), eff(teacher),  new CourseSelectRequest("CS201"));
            fail("Teacher should not select courses");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
        try {
            service.saveCourse(student.getUserId(), eff(student),  demoCourse("CS203", "数据库", 30));
            fail("Student should not maintain courses");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
        try {
            service.deleteCourse(admin.getUserId(), eff(admin),  "CS101");
            fail("Course with enrollments should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }

        service.deleteCourse(admin.getUserId(), eff(admin),  "CS201");
        assertFalse(service.queryCourses(admin.getUserId(), eff(admin),  null).stream()
                .anyMatch(course -> "CS201".equals(course.getCourseId())));
    }

    private CourseDto demoCourse(String courseId, String courseName, int capacity) {
        return demoCourse(courseId, courseName, "周四 5-6 节", capacity);
    }

    private CourseDto demoCourse(String courseId, String courseName,
                                 String classTime, int capacity) {
        return new CourseDto(courseId, courseName, "T0001", "演示教师",
                "CS", "计算机科学与工程学院", 3.0, capacity, 0,
                "2026-2027-1", classTime, "教1-201", "测试课程", true);
    }
}
