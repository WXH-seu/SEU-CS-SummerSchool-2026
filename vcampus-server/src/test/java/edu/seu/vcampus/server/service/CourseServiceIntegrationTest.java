package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseDropRequest;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.CourseSelectRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SectionAudienceDto;
import edu.seu.vcampus.common.dto.SectionScheduleDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.enums.Role;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Exercises the v2 course schema: catalog/section split, audience rules at
 * department granularity, selection windows, nature, teacher roster, role
 * checks and the per-course lock that prevents concurrent overselling.
 */
public class CourseServiceIntegrationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private CourseService service;
    private AccessDatabase database;
    private AccessCourseRepository courseRepository;
    private AccessUserRepository userRepository;
    private AcademicService academicService;
    private UserAccount admin;
    private UserAccount student;
    private UserAccount teacher;

    private SubSystemRole eff(UserAccount actor) {
        return SubSystems.effectiveRole(actor.getRole(), actor.getAdminScopes(), SubSystem.COURSE);
    }

    private SubSystemRole effAcademic(UserAccount actor) {
        return SubSystems.effectiveRole(actor.getRole(), actor.getAdminScopes(),
                SubSystem.STUDENT);
    }

    @Before
    public void setUp() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "vCampus.accdb");
        database = new AccessDatabase(file.getAbsolutePath());
        userRepository = new AccessUserRepository(database, new PasswordHasher());
        AccessAcademicRepository academics = new AccessAcademicRepository(database);
        courseRepository = new AccessCourseRepository(database);
        service = new CourseService(courseRepository);
        academicService = new AcademicService(academics, userRepository);
        admin = userRepository.findById("admin");
        student = userRepository.findById("student");
        teacher = userRepository.findById("teacher");
    }

    @Test
    public void seedsDemoSectionsAndRestrictsRoles() throws Exception {
        List<CourseDto> visible = service.queryCourses(
                student.getUserId(), eff(student), null);
        assertEquals(2, visible.size());
        CourseDto cs101 = byCourseId(visible, "CS101");
        CourseDto cs102 = byCourseId(visible, "CS102");
        assertTrue(cs101.getSectionId().startsWith("SEC"));
        assertTrue(cs101.isSelected());
        assertEquals("已选", cs101.getReason());
        assertFalse(cs102.isSelected());
        assertTrue(cs102.getReason() == null || cs102.getReason().isEmpty());
        assertEquals(1, service.querySchedule(student.getUserId(), eff(student)).size());
        assertTrue(service.queryCourses(teacher.getUserId(), eff(teacher), null).stream()
                .allMatch(course -> "T0001".equals(course.getTeacherId())));
        assertEquals(2, service.queryCourses(admin.getUserId(), eff(admin), null).size());
    }

    @Test
    public void upgradesPartiallyMigratedSchemaWithScheduleForeignKey() throws Exception {
        File legacyFile = new File(temporaryFolder.getRoot(), "partial-v2.accdb");
        AccessDatabase legacyDatabase = new AccessDatabase(legacyFile.getAbsolutePath());
        new AccessUserRepository(legacyDatabase, new PasswordHasher());
        new AccessAcademicRepository(legacyDatabase);
        try (Connection connection = legacyDatabase.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE [tblCourse] ("
                    + "[courseId] TEXT(20) NOT NULL PRIMARY KEY, "
                    + "[courseName] TEXT(64) NOT NULL, [credit] DOUBLE NOT NULL, "
                    + "[description] TEXT(255), [active] YESNO NOT NULL)");
            statement.execute("CREATE TABLE [tblCourseSection] ("
                    + "[sectionId] TEXT(24) NOT NULL PRIMARY KEY, "
                    + "[courseId] TEXT(20) NOT NULL, [teacherId] TEXT(20) NOT NULL, "
                    + "[departmentId] TEXT(16) NOT NULL, [capacity] INTEGER NOT NULL, "
                    + "CONSTRAINT [fkLegacySectionCourse] FOREIGN KEY ([courseId]) "
                    + "REFERENCES [tblCourse] ([courseId]))");
            statement.execute("CREATE TABLE [tblSectionSchedule] ("
                    + "[scheduleId] TEXT(32) NOT NULL PRIMARY KEY, "
                    + "[sectionId] TEXT(24) NOT NULL, "
                    + "CONSTRAINT [fkLegacyScheduleSection] FOREIGN KEY ([sectionId]) "
                    + "REFERENCES [tblCourseSection] ([sectionId]))");
        }

        AccessCourseRepository upgraded = new AccessCourseRepository(legacyDatabase);
        assertFalse(upgraded.findSections(null).isEmpty());
    }

    @Test
    public void duplicateAndWindowAndAudienceRulesAreEnforced() throws Exception {
        try {
            service.selectCourse(student.getUserId(), eff(student),
                    new CourseSelectRequest("SEC00000001"));
            fail("Duplicate selection should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }

        CourseDto future = service.saveCourse(admin.getUserId(), eff(admin),
                demoCourse("CS301", "未来课程", "2099-01-01 00:00", "2099-12-31 23:59",
                        "周五 1-2 节", 30, allAudience()));
        try {
            service.selectCourse(student.getUserId(), eff(student),
                    new CourseSelectRequest(future.getSectionId()));
            fail("Course outside its window should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertEquals("尚未开始选课", expected.getMessage());
        }

        academicService.saveDepartment(admin.getUserId(), effAcademic(admin),
                new DepartmentDto("EE", "电子科学与工程学院", "测试院系", true));
        academicService.saveTeacher(admin.getUserId(), effAcademic(admin),
                new TeacherDto("T0002", null, "测试教师二", "CS", "讲师", "", "", true));
        CourseDto eeOnly = service.saveCourse(admin.getUserId(), eff(admin),
                demoCourse("CS302", "仅电子学院", "2026-09-01 08:00", "2026-12-31 23:59",
                        "周六 1-2 节", 30,
                        Collections.singletonList(new SectionAudienceDto(
                                null, SectionAudienceDto.SCOPE_DEPARTMENT, "EE",
                                2024, 2028))));
        try {
            service.selectCourse(student.getUserId(), eff(student),
                    new CourseSelectRequest(eeOnly.getSectionId()));
            fail("A CS student should not select an EE-only section");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
        assertFalse(service.queryCourses(student.getUserId(), eff(student), null).stream()
                .anyMatch(course -> "CS302".equals(course.getCourseId())));
    }

    @Test
    public void capacityLimitIsEnforcedForTwoStudents() throws Exception {
        String student2 = createSecondStudent("stu2");
        CourseDto oneSeat = service.saveCourse(admin.getUserId(), eff(admin),
                demoCourse("CS201", "软件工程", "2026-09-01 08:00", "2026-12-31 23:59",
                        "周四 5-6 节", 1, allAudience()));

        service.selectCourse(student.getUserId(), eff(student),
                new CourseSelectRequest(oneSeat.getSectionId()));
        try {
            service.selectCourse(student2, SubSystemRole.STUDENT,
                    new CourseSelectRequest(oneSeat.getSectionId()));
            fail("Full section should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }
        assertEquals(1, courseRepository.countEnrolled(oneSeat.getSectionId()));
    }

    @Test
    public void concurrentSelectionNeverOversells() throws Exception {
        String student2 = createSecondStudent("stu2");
        CourseDto oneSeat = service.saveCourse(admin.getUserId(), eff(admin),
                demoCourse("CS204", "并发名额", "2026-09-01 08:00", "2026-12-31 23:59",
                        "周日 1-2 节", 1, allAudience()));
        final String sectionId = oneSeat.getSectionId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        final CountDownLatch start = new CountDownLatch(1);
        Callable<String> attempt = new Callable<String>() {
            @Override
            public String call() throws Exception {
                start.await();
                try {
                    service.selectCourse(student.getUserId(), eff(student),
                            new CourseSelectRequest(sectionId));
                    return "OK";
                } catch (BusinessException e) {
                    return e.getMessage();
                }
            }
        };
        Callable<String> attempt2 = new Callable<String>() {
            @Override
            public String call() throws Exception {
                start.await();
                try {
                    service.selectCourse(student2, SubSystemRole.STUDENT,
                            new CourseSelectRequest(sectionId));
                    return "OK";
                } catch (BusinessException e) {
                    return e.getMessage();
                }
            }
        };
        try {
            Future<String> first = pool.submit(attempt);
            Future<String> second = pool.submit(attempt2);
            start.countDown();
            String resultA = first.get();
            String resultB = second.get();
            assertEquals(1, (resultA.equals("OK") ? 1 : 0) + (resultB.equals("OK") ? 1 : 0));
            assertEquals(1, courseRepository.countEnrolled(sectionId));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void teacherCanQueryOwnRosterAndRolesAreEnforced() throws Exception {
        List<CourseDto> rosterSections =
                service.queryCourses(teacher.getUserId(), eff(teacher), null);
        assertFalse(rosterSections.isEmpty());
        String sectionId = rosterSections.get(0).getSectionId();
        // 演示班次的选课窗口还没结束，教师此时看不到名单（结束后才开放，
        // 见 teacherRosterOpensOnlyAfterSelectionWindowCloses）。
        try {
            service.queryRoster(teacher.getUserId(), eff(teacher), sectionId);
            fail("Roster should stay hidden while selection is still open");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("选课尚未结束"));
        }
        // 管理员不受选课窗口限制。
        assertFalse(service.queryRoster(
                admin.getUserId(), eff(admin), sectionId).isEmpty());
        try {
            service.queryRoster(student.getUserId(), eff(student), sectionId);
            fail("Student should not view rosters");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
        try {
            service.selectCourse(teacher.getUserId(), eff(teacher),
                    new CourseSelectRequest(sectionId));
            fail("Teacher should not select courses");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
        try {
            service.saveCourse(student.getUserId(), eff(student),
                    demoCourse("CS999", "越权课程", "2026-09-01 08:00",
                            "2026-12-31 23:59", "周日 3-4 节", 30, allAudience()));
            fail("Student should not maintain courses");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
        }
    }

    @Test
    public void changingClassTimeConflictingWithEnrolledStudentsIsRejected() throws Exception {
        CourseDto cs101 = byCourseId(service.queryCourses(
                admin.getUserId(), eff(admin), null), "CS101");
        CourseDto cs500 = service.saveCourse(admin.getUserId(), eff(admin),
                demoCourse("CS500", "系统分析", "2026-09-01 08:00", "2026-12-31 23:59",
                        "周五 7-8 节", 30, allAudience()));
        service.selectCourse(student.getUserId(), eff(student),
                new CourseSelectRequest(cs500.getSectionId()));

        try {
            service.saveCourse(admin.getUserId(), eff(admin),
                    withClassTime(cs101, "周五 7-8 节"));
            fail("Changing class time that conflicts with enrolled students should fail");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("冲突"));
        }

        CourseDto saved = service.saveCourse(admin.getUserId(), eff(admin),
                withClassTime(cs101, "周二 5-6 节"));
        assertEquals("周二 5-6 节", saved.getClassTime());
    }

    @Test
    public void changingClassTimeConflictingWithTeachersOtherSectionIsRejected() throws Exception {
        CourseDto cs102 = byCourseId(service.queryCourses(
                admin.getUserId(), eff(admin), null), "CS102");
        try {
            service.saveCourse(admin.getUserId(), eff(admin),
                    withSchedules(cs102, Collections.singletonList(slot(1, 3, 4, 1, 16))));
            fail("Teacher already teaches CS101 at the requested time");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("教师"));
        }
    }

    @Test
    public void strictPoolsRejectWhenOwnPoolIsFullEvenWithBufferLeft() throws Exception {
        String stu2 = createStudent("stu2", "20260003");
        String stu3 = createStudent("stu3", "20260004");
        CourseDto section = service.saveCourse(admin.getUserId(), eff(admin),
                demoCoursePools("CS601", "重修分流", "2026-09-01 08:00",
                        "2026-12-31 23:59", "周日 5-6 节", 10, 1, 1,
                        allAudience()));
        courseRepository.addCourseRecord("20260001", "CS601", "2025-2026-1", "未通过");
        String sectionId = section.getSectionId();

        // 20260001 has a failed record -> retake pool (capacity 1).
        service.selectCourse(student.getUserId(), eff(student),
                new CourseSelectRequest(sectionId));
        // stu2 is a first-attempt student -> first-attempt pool (capacity 1).
        service.selectCourse(stu2, SubSystemRole.STUDENT,
                new CourseSelectRequest(sectionId));
        assertEquals(2, courseRepository.countEnrolled(sectionId));
        try {
            // The first-attempt pool is full; the unused buffer must not be
            // available for self-service selection.
            service.selectCourse(stu3, SubSystemRole.STUDENT,
                    new CourseSelectRequest(sectionId));
            fail("A third first-attempt student should be rejected by the pool cap");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("名额已满"));
        }

        // 教师名单在选课结束后才开放：先把窗口收成“已结束”，再查名单。
        service.saveCourse(admin.getUserId(), eff(admin),
                withWindow(section, "2026-09-01 08:00", "2026-09-10 23:59"));
        List<edu.seu.vcampus.common.dto.SectionRosterEntry> roster =
                service.queryRoster(teacher.getUserId(), eff(teacher), sectionId);
        assertEquals(2, roster.size());
        long retakes = roster.stream()
                .filter(row -> CourseDto.ATTEMPT_RETAKE.equals(row.getAttemptType()))
                .count();
        assertEquals(1, retakes);
        assertTrue(roster.stream().anyMatch(row -> "13800000001".equals(row.getPhone())));
    }

    @Test
    public void poolCapacityCannotDropBelowEnrolledCount() throws Exception {
        CourseDto section = service.saveCourse(admin.getUserId(), eff(admin),
                demoCoursePools("CS602", "名额回退", "2026-09-01 08:00",
                        "2026-12-31 23:59", "周日 7-8 节", 10, 5, 5,
                        allAudience()));
        service.selectCourse(student.getUserId(), eff(student),
                new CourseSelectRequest(section.getSectionId()));

        try {
            service.saveCourse(admin.getUserId(), eff(admin),
                    withPools(section, 0, 10));
            fail("Pool capacity must not drop below the enrolled count");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.INVALID_REQUEST, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("不能低于已选人数"));
        }
    }

    @Test
    public void startupOnlyWarnsAboutInconsistentPoolData() throws Exception {
        String stu2 = createStudent("stu2", "20260003");
        CourseDto section = courseRepository.saveSection(
                demoCoursePools("CS603", "越界数据", "2026-09-01 08:00",
                        "2026-12-31 23:59", "周日 9-10 节", 10, 1, 0,
                        allAudience()));
        String sectionId = section.getSectionId();
        courseRepository.insertEnrollment("20260001", sectionId, "FIRST",
                "OVER-1", "2026-09-01 10:00:00");
        courseRepository.insertEnrollment("20260003", sectionId, "FIRST",
                "OVER-2", "2026-09-01 10:01:00");

        // Re-opening the repository runs the startup self-check; it must only
        // log a warning and keep serving data.
        final StringBuilder warning = new StringBuilder();
        Logger logger = Logger.getLogger(AccessCourseRepository.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warning.append(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        AccessCourseRepository reloaded;
        try {
            reloaded = new AccessCourseRepository(database);
            reloaded.warnAboutPoolInconsistency();
        } finally {
            logger.removeHandler(handler);
        }
        assertFalse(reloaded.findSections(null).isEmpty());
        assertEquals(2, reloaded.countAttempts(sectionId).getFirstAttemptCount());
        assertTrue(warning.toString().contains("选课名额不一致"));
    }

    @Test
    public void scheduleSlotsConflictOnWeekdayPeriodWeekOverlap() throws Exception {
        academicService.saveTeacher(admin.getUserId(), effAcademic(admin),
                new TeacherDto("T0002", null, "测试教师二", "CS", "讲师", "", "", true));
        academicService.saveTeacher(admin.getUserId(), effAcademic(admin),
                new TeacherDto("T0003", null, "测试教师三", "CS", "讲师", "", "", true));
        CourseDto first = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS701", "早课", "T0002",
                        slot(5, 7, 8, 1, 16)));
        service.selectCourse(student.getUserId(), eff(student),
                new CourseSelectRequest(first.getSectionId()));

        CourseDto overlap = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS702", "撞时段", "T0003",
                        slot(5, 7, 8, 8, 16)));
        try {
            service.selectCourse(student.getUserId(), eff(student),
                    new CourseSelectRequest(overlap.getSectionId()));
            fail("Overlapping weekday/period/week should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }

        CourseDto differentWeek = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS703", "错周课", "T0003",
                        slot(5, 7, 8, 17, 20)));
        service.selectCourse(student.getUserId(), eff(student),
                new CourseSelectRequest(differentWeek.getSectionId()));
        assertEquals(3, service.querySchedule(student.getUserId(), eff(student)).size());
    }

    @Test
    public void poolSumBeyondMaximumCapacityIsRejected() throws Exception {
        try {
            service.saveCourse(admin.getUserId(), eff(admin),
                    demoCoursePools("CS801", "名额超限", "2026-09-01 08:00",
                            "2026-12-31 23:59", "周六 11-12 节", 10, 8, 5,
                            allAudience()));
            fail("First-attempt plus retake capacity must not exceed the maximum");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.INVALID_REQUEST, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("之和"));
        }
    }

    @Test
    public void otherSectionOfSameCourseIsMarkedAndRejected() throws Exception {
        CourseDto first = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS901", "同一课程A班", slot(4, 9, 10, 1, 16)));
        service.selectCourse(student.getUserId(), eff(student),
                new CourseSelectRequest(first.getSectionId()));
        CourseDto second = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS901", "同一课程B班", slot(6, 9, 10, 1, 16)));

        CourseDto other = null;
        for (CourseDto row : service.queryCourses(student.getUserId(), eff(student), null)) {
            if ("CS901".equals(row.getCourseId())
                    && second.getSectionId().equals(row.getSectionId())) {
                other = row;
            }
        }
        assertNotNull(other);
        assertEquals("已选择同一门课程的其它教学班", other.getReason());

        try {
            service.selectCourse(student.getUserId(), eff(student),
                    new CourseSelectRequest(second.getSectionId()));
            fail("Selecting another section of the same course should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }
    }

    @Test
    public void natureFilterAndValidationWork() throws Exception {
        service.saveCourse(admin.getUserId(), eff(admin),
                demoCourse("CS401", "通识数学", "2026-09-01 08:00", "2026-12-31 23:59",
                        "周二 1-2 节", 30, allAudience()));
        CourseQueryRequest query = new CourseQueryRequest(
                null, null, null, null, false, "必修");
        List<CourseDto> required =
                service.queryCourses(admin.getUserId(), eff(admin), query);
        assertFalse(required.isEmpty());
        assertTrue(required.stream().allMatch(course -> "必修".equals(course.getCourseNature())));
        try {
            service.saveCourse(admin.getUserId(), eff(admin),
                    demoCourse("CS402", "坏性质", "2026-09-01 08:00", "2026-12-31 23:59",
                            "周二 3-4 节", 30, allAudience(), "超纲"));
            fail("Invalid nature should be rejected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.INVALID_REQUEST, expected.getResponseCode());
        }
    }

    @Test
    public void studentCanDropOwnEnrollmentAndAdminDeleteIsGuarded() throws Exception {
        try {
            service.deleteCourse(admin.getUserId(), eff(admin), "SEC00000001");
            fail("Section with enrollments should not be deleted");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }

        List<edu.seu.vcampus.common.dto.CourseEnrollmentDto> schedule =
                service.querySchedule(student.getUserId(), eff(student));
        assertFalse(schedule.isEmpty());
        String enrollmentId = schedule.get(0).getEnrollmentId();
        assertNotNull(enrollmentId);
        service.dropCourse(student.getUserId(), eff(student),
                new CourseDropRequest(enrollmentId));
        assertTrue(service.querySchedule(student.getUserId(), eff(student)).isEmpty());

        try {
            service.dropCourse(student.getUserId(), eff(student),
                    new CourseDropRequest(enrollmentId));
            fail("Dropping twice should fail");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.NOT_FOUND, expected.getResponseCode());
        }

        service.deleteCourse(admin.getUserId(), eff(admin), "SEC00000002");
        assertFalse(service.queryCourses(admin.getUserId(), eff(admin), null).stream()
                .anyMatch(course -> "SEC00000002".equals(course.getSectionId())));
    }

    @Test
    public void deletingSectionWithEnrollmentsLeavesSchedulesAndAudiencesIntact()
            throws Exception {
        CourseDto section = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS701", "删除原子性", slot(7, 1, 2, 1, 16)));
        String sectionId = section.getSectionId();
        service.selectCourse(student.getUserId(), eff(student),
                new CourseSelectRequest(sectionId));

        try {
            courseRepository.deleteSection(sectionId);
            fail("外键应当拒绝删除仍有选课记录的教学班");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("constraint"));
        }
        // 删除失败必须整体回滚：教学班、时段、受众和选课记录都保持原样，
        // 不能留下“教学班还在、排课和受众被清空”的半成品。
        assertNotNull(courseRepository.findSectionById(sectionId));
        assertEquals(1, courseRepository.findSchedules(sectionId).size());
        assertEquals(1,
                courseRepository.findSectionById(sectionId).getAudiences().size());
        assertEquals(1, courseRepository.countEnrolled(sectionId));
    }

    @Test
    public void concurrentDeleteAndSelectionNeverLeavesPartialState() throws Exception {
        CourseDto section = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS702", "删除并发", slot(7, 3, 4, 1, 16)));
        final String sectionId = section.getSectionId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        final CountDownLatch start = new CountDownLatch(1);
        Callable<String> selecting = new Callable<String>() {
            @Override
            public String call() throws Exception {
                start.await();
                try {
                    service.selectCourse(student.getUserId(), eff(student),
                            new CourseSelectRequest(sectionId));
                    return "OK";
                } catch (BusinessException e) {
                    return e.getMessage();
                }
            }
        };
        Callable<String> deleting = new Callable<String>() {
            @Override
            public String call() throws Exception {
                start.await();
                try {
                    service.deleteCourse(admin.getUserId(), eff(admin), sectionId);
                    return "OK";
                } catch (BusinessException e) {
                    return e.getMessage();
                }
            }
        };
        String selectResult;
        String deleteResult;
        try {
            Future<String> selected = pool.submit(selecting);
            Future<String> deleted = pool.submit(deleting);
            start.countDown();
            selectResult = selected.get();
            deleteResult = deleted.get();
        } finally {
            pool.shutdownNow();
        }
        // 两者只能有一个成功：要么选课成功、删除被拒；要么删除成功、选课拿到
        // “课程不存在”。两种结果都不允许留下半成品。
        assertEquals(1,
                (selectResult.equals("OK") ? 1 : 0) + (deleteResult.equals("OK") ? 1 : 0));
        if (courseRepository.findSectionById(sectionId) == null) {
            assertEquals(0, courseRepository.countEnrolled(sectionId));
        } else {
            assertEquals(1, courseRepository.findSchedules(sectionId).size());
            assertEquals(1, courseRepository.findSectionById(sectionId)
                    .getAudiences().size());
            assertEquals(1, courseRepository.countEnrolled(sectionId));
        }
    }

    @Test
    public void conflictGuardRunsEvenWhenClassTimeIsUnchanged() throws Exception {
        academicService.saveTeacher(admin.getUserId(), effAcademic(admin),
                new TeacherDto("T0002", null, "测试教师二", "CS", "讲师", "", "", true));
        CourseDto first = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS706", "冲突课程A", slot(7, 13, 14, 1, 16)));
        service.selectCourse(student.getUserId(), eff(student),
                new CourseSelectRequest(first.getSectionId()));

        // 直接写入一条同一时段的教学班选课记录，制造“数据层面已经冲突”的历史数据。
        // 换一位教师，避免被“同一教师同一时段”检查挡住（这里要验证的是学生侧冲突）。
        CourseDto second = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS707", "冲突课程B", "T0002",
                        slot(7, 13, 14, 1, 16)));
        courseRepository.insertEnrollment("20260001", second.getSectionId(), "FIRST",
                "CONFLICT-SEED-1", "2026-09-02 09:00:00");

        // 只调整名额、上课时间文本没变：仍然要检出冲突并拒绝保存。
        try {
            service.saveCourse(admin.getUserId(), eff(admin), withPools(second, 20, 10));
            fail("已选学生与当前排课冲突时，任何保存都应被拒绝");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("冲突"));
        }
    }

    @Test
    public void audienceChangeCannotExcludeEnrolledStudents() throws Exception {
        CourseDto section = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS703", "受众收紧", slot(7, 5, 6, 1, 16)));
        String sectionId = section.getSectionId();
        String stu = createStudent("stuAudience", "20260006");
        service.selectCourse(stu, SubSystemRole.STUDENT,
                new CourseSelectRequest(sectionId));

        // 该生入学年份为 2026，收窄到 2020-2024 就会把他排除在外。
        List<SectionAudienceDto> oldYearsOnly = Collections.singletonList(
                new SectionAudienceDto(null, SectionAudienceDto.SCOPE_ALL, null,
                        Integer.valueOf(2020), Integer.valueOf(2024)));
        try {
            service.saveCourse(admin.getUserId(), eff(admin),
                    withAudiences(section, oldYearsOnly));
            fail("收紧受众不能把已经选上这门课的学生排除在外");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.INVALID_REQUEST, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("未覆盖"));
        }
        assertEquals(SectionAudienceDto.SCOPE_ALL,
                courseRepository.findSectionById(sectionId).getAudiences().get(0)
                        .getScopeType());

        // 放宽受众（仍覆盖已选学生）可以正常保存。
        assertEquals(sectionId, service.saveCourse(admin.getUserId(), eff(admin),
                withAudiences(section, allAudience())).getSectionId());
        assertEquals(1, courseRepository.findSchedules(sectionId).size());
    }

    @Test
    public void teacherRosterOpensOnlyAfterSelectionWindowCloses() throws Exception {
        String teacherId = courseRepository.findTeacherIdByUserId(teacher.getUserId());
        CourseDto openSection = service.saveCourse(admin.getUserId(), eff(admin),
                demoScheduledCourse("CS704", "选课进行中", teacherId,
                        slot(7, 7, 8, 1, 16)));
        try {
            service.queryRoster(teacher.getUserId(), eff(teacher),
                    openSection.getSectionId());
            fail("选课进行中教师不应看到名单");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.FORBIDDEN, expected.getResponseCode());
            assertTrue(expected.getMessage().contains("选课结束后"));
        }
        assertEquals(0, service.queryRoster(admin.getUserId(), eff(admin),
                openSection.getSectionId()).size());

        CourseDto closedSection = service.saveCourse(admin.getUserId(), eff(admin),
                new CourseDto(null, "CS705", "选课已结束", "测试课程", teacherId,
                        null, "CS", null, 3.0, "必修", 30, 30, 0, 0, 0, 0, null,
                        "2026-2027-1", "", "", "2026-08-01 08:00", "2026-09-10 23:59",
                        true, false, null, allAudience(),
                        Collections.singletonList(slot(7, 9, 10, 1, 16))));
        // 窗口已结束，学生无法再通过服务选课，这里直接写入一条选课记录。
        courseRepository.insertEnrollment("20260001", closedSection.getSectionId(),
                "FIRST", "ROSTER-CLOSED-1", "2026-08-02 09:00:00");
        assertEquals(1, service.queryRoster(teacher.getUserId(), eff(teacher),
                closedSection.getSectionId()).size());
    }

    @Test
    public void concurrentSectionCreationCannotDoubleBookTheSameTeacherSlot()
            throws Exception {
        final String teacherId = courseRepository.findTeacherIdByUserId(teacher.getUserId());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        final CountDownLatch start = new CountDownLatch(1);
        Callable<String> first = createSectionTask(start, "CS801", "并发新建A", teacherId);
        Callable<String> second = createSectionTask(start, "CS802", "并发新建B", teacherId);
        String firstResult;
        String secondResult;
        try {
            Future<String> taskA = pool.submit(first);
            Future<String> taskB = pool.submit(second);
            start.countDown();
            firstResult = taskA.get();
            secondResult = taskB.get();
        } finally {
            pool.shutdownNow();
        }
        // 同一教师同一时段只能建出一个教学班：先建成功的那个占住时段，
        // 后一个必须被教师占用检查拒绝。
        assertEquals(1, (firstResult.equals("OK") ? 1 : 0)
                + (secondResult.equals("OK") ? 1 : 0));
    }

    private Callable<String> createSectionTask(final CountDownLatch start,
                                               final String courseId, final String courseName,
                                               final String teacherId) {
        return new Callable<String>() {
            @Override
            public String call() throws Exception {
                start.await();
                try {
                    service.saveCourse(admin.getUserId(), eff(admin),
                            demoScheduledCourse(courseId, courseName, teacherId,
                                    slot(7, 11, 12, 1, 16)));
                    return "OK";
                } catch (BusinessException e) {
                    return e.getMessage();
                }
            }
        };
    }

    @Test
    public void academicEntitiesReferencedByCourseV2CannotBeDeleted() throws Exception {
        try {
            academicService.deleteStudent(
                    admin.getUserId(), effAcademic(admin), "20260001");
            fail("Student with enrollments should be protected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }

        try {
            academicService.deleteTeacher(
                    admin.getUserId(), effAcademic(admin), "T0001");
            fail("Teacher with course sections should be protected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }

        academicService.saveDepartment(admin.getUserId(), effAcademic(admin),
                new DepartmentDto("COURSE-DEPT", "课程测试院系", "仅用于外键测试", true));
        CourseDto section = new CourseDto(null, "COURSE-D001", "跨院系课程", "测试课程",
                "T0001", null, "COURSE-DEPT", null, 2.0, "必修", 20, 0,
                "2026-2027-1", "周五 1-2 节", "教1-202",
                "2026-09-01 08:00", "2026-12-31 23:59",
                true, false, null, allAudience());
        service.saveCourse(admin.getUserId(), eff(admin), section);
        try {
            academicService.deleteDepartment(
                    admin.getUserId(), effAcademic(admin), "COURSE-DEPT");
            fail("Department with course sections should be protected");
        } catch (BusinessException expected) {
            assertEquals(ResponseCode.CONFLICT, expected.getResponseCode());
        }
    }

    private CourseDto byCourseId(List<CourseDto> sections, String courseId) {
        for (CourseDto section : sections) {
            if (courseId.equals(section.getCourseId())) {
                return section;
            }
        }
        throw new AssertionError("section not found: " + courseId);
    }

    private CourseDto withClassTime(CourseDto source, String classTime) {
        return new CourseDto(source.getSectionId(), source.getCourseId(),
                source.getCourseName(), source.getDescription(), source.getTeacherId(),
                source.getTeacherName(), source.getDepartmentId(), source.getDepartmentName(),
                source.getCredit(), source.getCourseNature(), source.getCapacity(),
                source.getEnrolledCount(), source.getSemesterName(), classTime,
                source.getLocation(), source.getSelectionStartTime(),
                source.getSelectionEndTime(), source.isActive(), source.isSelected(),
                source.getReason(), source.getAudiences());
    }

    private CourseDto withSchedules(CourseDto source, List<SectionScheduleDto> schedules) {
        return new CourseDto(source.getSectionId(), source.getCourseId(),
                source.getCourseName(), source.getDescription(), source.getTeacherId(),
                source.getTeacherName(), source.getDepartmentId(), source.getDepartmentName(),
                source.getCredit(), source.getCourseNature(), source.getCapacity(),
                source.getFirstAttemptCapacity(), source.getRetakeCapacity(),
                source.getFirstAttemptEnrolled(), source.getRetakeEnrolled(),
                source.getEnrolledCount(), source.getAttemptType(),
                source.getSemesterName(), source.getClassTime(), source.getLocation(),
                source.getSelectionStartTime(), source.getSelectionEndTime(),
                source.isActive(), source.isSelected(), source.getReason(),
                source.getAudiences(), schedules);
    }

    private CourseDto withPools(CourseDto source, int firstAttemptCapacity,
                                int retakeCapacity) {
        return new CourseDto(source.getSectionId(), source.getCourseId(),
                source.getCourseName(), source.getDescription(), source.getTeacherId(),
                source.getTeacherName(), source.getDepartmentId(), source.getDepartmentName(),
                source.getCredit(), source.getCourseNature(), source.getCapacity(),
                firstAttemptCapacity, retakeCapacity,
                source.getFirstAttemptEnrolled(), source.getRetakeEnrolled(),
                source.getEnrolledCount(), source.getAttemptType(),
                source.getSemesterName(), source.getClassTime(), source.getLocation(),
                source.getSelectionStartTime(), source.getSelectionEndTime(),
                source.isActive(), source.isSelected(), source.getReason(),
                source.getAudiences(), source.getSchedules());
    }

    private CourseDto withAudiences(CourseDto source, List<SectionAudienceDto> audiences) {
        return new CourseDto(source.getSectionId(), source.getCourseId(),
                source.getCourseName(), source.getDescription(), source.getTeacherId(),
                source.getTeacherName(), source.getDepartmentId(), source.getDepartmentName(),
                source.getCredit(), source.getCourseNature(), source.getCapacity(),
                source.getFirstAttemptCapacity(), source.getRetakeCapacity(),
                source.getFirstAttemptEnrolled(), source.getRetakeEnrolled(),
                source.getEnrolledCount(), source.getAttemptType(),
                source.getSemesterName(), source.getClassTime(), source.getLocation(),
                source.getSelectionStartTime(), source.getSelectionEndTime(),
                source.isActive(), source.isSelected(), source.getReason(),
                audiences, source.getSchedules());
    }

    private CourseDto withWindow(CourseDto source, String start, String end) {
        return new CourseDto(source.getSectionId(), source.getCourseId(),
                source.getCourseName(), source.getDescription(), source.getTeacherId(),
                source.getTeacherName(), source.getDepartmentId(), source.getDepartmentName(),
                source.getCredit(), source.getCourseNature(), source.getCapacity(),
                source.getFirstAttemptCapacity(), source.getRetakeCapacity(),
                source.getFirstAttemptEnrolled(), source.getRetakeEnrolled(),
                source.getEnrolledCount(), source.getAttemptType(),
                source.getSemesterName(), source.getClassTime(), source.getLocation(),
                start, end,
                source.isActive(), source.isSelected(), source.getReason(),
                source.getAudiences(), source.getSchedules());
    }

    private String createSecondStudent(String userId) throws Exception {
        return createStudent(userId, "20260003");
    }

    private String createStudent(String userId, String studentId) throws Exception {
        PasswordHasher hasher = new PasswordHasher();
        String salt = hasher.newSalt();
        userRepository.insert(new UserAccount(userId, hasher.hash("secret123", salt),
                salt, "第二名学生", Role.STUDENT, true));
        academicService.saveStudent(admin.getUserId(), effAcademic(admin),
                new StudentDto(studentId, userId, "第二名学生", "女",
                        "2008-03-04", "CS", "CS2026-01", 2026, "在读", "", ""));
        return userId;
    }

    private List<SectionAudienceDto> allAudience() {
        return Collections.singletonList(new SectionAudienceDto(
                null, SectionAudienceDto.SCOPE_ALL, null, null, null));
    }

    private CourseDto demoCourse(String courseId, String courseName,
                                 String start, String end, String classTime,
                                 int capacity, List<SectionAudienceDto> audiences) {
        return demoCourse(courseId, courseName, start, end, classTime,
                capacity, audiences, "必修");
    }

    private CourseDto demoCourse(String courseId, String courseName,
                                 String start, String end, String classTime,
                                 int capacity, List<SectionAudienceDto> audiences,
                                 String nature) {
        return new CourseDto(null, courseId, courseName, "测试课程",
                "T0001", null, "CS", null, 3.0, nature, capacity, 0,
                "2026-2027-1", classTime, "教1-201", start, end,
                true, false, null, audiences);
    }

    private CourseDto demoCoursePools(String courseId, String courseName,
                                      String start, String end, String classTime,
                                      int capacity, int firstAttemptCapacity,
                                      int retakeCapacity,
                                      List<SectionAudienceDto> audiences) {
        return new CourseDto(null, courseId, courseName, "测试课程",
                "T0001", null, "CS", null, 3.0, "必修",
                capacity, firstAttemptCapacity, retakeCapacity, 0, 0, 0, null,
                "2026-2027-1", classTime, "教1-201", start, end,
                true, false, null, audiences);
    }

    private SectionScheduleDto slot(int weekday, int periodStart, int periodEnd,
                                    int weekStart, int weekEnd) {
        return new SectionScheduleDto(weekday, periodStart, periodEnd,
                weekStart, weekEnd, "教1-301");
    }

    private CourseDto demoScheduledCourse(String courseId, String courseName,
                                          SectionScheduleDto schedule) {
        return demoScheduledCourse(courseId, courseName, "T0001", schedule);
    }

    private CourseDto demoScheduledCourse(String courseId, String courseName,
                                          String teacherId, SectionScheduleDto schedule) {
        return new CourseDto(null, courseId, courseName, "测试课程",
                teacherId, null, "CS", null, 3.0, "必修",
                30, 30, 0, 0, 0, 0, null,
                "2026-2027-1", "", "",
                "2026-09-01 08:00", "2026-12-31 23:59",
                true, false, null, allAudience(),
                java.util.Collections.singletonList(schedule));
    }

}
