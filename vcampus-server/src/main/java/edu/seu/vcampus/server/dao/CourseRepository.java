package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseEnrollmentDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.SectionRosterEntry;
import edu.seu.vcampus.common.dto.StudentDto;

import java.sql.SQLException;
import java.util.List;

/** Persistence contract for course sections and their enrollments. */
public interface CourseRepository {
    List<CourseDto> findSections(CourseQueryRequest query) throws SQLException;

    CourseDto findSectionById(String sectionId) throws SQLException;

    /** Upserts catalog + section + audience rules; returns the saved section. */
    CourseDto saveSection(CourseDto section) throws SQLException;

    boolean deleteSection(String sectionId) throws SQLException;

    boolean sectionHasEnrollments(String sectionId) throws SQLException;

    List<SectionRosterEntry> findRoster(String sectionId) throws SQLException;

    boolean isEnrolled(String studentId, String sectionId) throws SQLException;

    boolean isEnrolledInCourse(String studentId, String courseId) throws SQLException;

    int countEnrolled(String sectionId) throws SQLException;

    boolean hasTimeConflict(String studentId, String classTime) throws SQLException;

    void insertEnrollment(String studentId, String sectionId,
                          String enrollmentId, String enrollTime) throws SQLException;

    boolean deleteEnrollment(String studentId, String enrollmentId) throws SQLException;

    List<CourseEnrollmentDto> findSchedule(String studentId) throws SQLException;

    StudentDto findStudentByUserId(String userId) throws SQLException;

    String findTeacherIdByUserId(String userId) throws SQLException;

    boolean teacherExists(String teacherId) throws SQLException;

    boolean departmentExists(String departmentId) throws SQLException;
}
