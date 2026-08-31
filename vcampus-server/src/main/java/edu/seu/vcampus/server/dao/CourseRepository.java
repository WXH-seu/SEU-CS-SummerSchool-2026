package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseEnrollmentDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.StudentDto;

import java.sql.SQLException;
import java.util.List;

/** Persistence contract for course selection. */
public interface CourseRepository {
    List<CourseDto> findCourses(CourseQueryRequest query) throws SQLException;

    CourseDto findCourseById(String courseId) throws SQLException;

    void saveCourse(CourseDto course) throws SQLException;

    boolean deleteCourse(String courseId) throws SQLException;

    boolean courseHasEnrollments(String courseId) throws SQLException;

    boolean isEnrolled(String studentId, String courseId) throws SQLException;

    int countEnrolled(String courseId) throws SQLException;

    boolean hasTimeConflict(String studentId, String classTime) throws SQLException;

    void insertEnrollment(String studentId, String courseId,
                          String enrollmentId, String enrollTime) throws SQLException;

    boolean deleteEnrollment(String studentId, String enrollmentId) throws SQLException;

    List<CourseEnrollmentDto> findSchedule(String studentId) throws SQLException;

    StudentDto findStudentByUserId(String userId) throws SQLException;

    String findTeacherIdByUserId(String userId) throws SQLException;

    boolean teacherExists(String teacherId) throws SQLException;

    boolean departmentExists(String departmentId) throws SQLException;
}
