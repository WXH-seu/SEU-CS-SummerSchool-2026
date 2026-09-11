package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.TeacherDto;

import java.sql.SQLException;
import java.util.List;

/** Persistence contract for academic information. */
public interface AcademicRepository {
    List<StudentDto> findStudents(AcademicQueryRequest query) throws SQLException;
    StudentDto findStudentByUserId(String userId) throws SQLException;
    boolean studentExists(String studentId) throws SQLException;
    void saveStudent(StudentDto student) throws SQLException;
    boolean deleteStudent(String studentId) throws SQLException;

    List<TeacherDto> findTeachers(AcademicQueryRequest query) throws SQLException;
    void saveTeacher(TeacherDto teacher) throws SQLException;
    boolean deleteTeacher(String teacherId) throws SQLException;

    List<DepartmentDto> findDepartments(AcademicQueryRequest query) throws SQLException;
    void saveDepartment(DepartmentDto department) throws SQLException;
    boolean deleteDepartment(String departmentId) throws SQLException;

    List<SchoolClassDto> findClasses(AcademicQueryRequest query) throws SQLException;
    void saveClass(SchoolClassDto schoolClass) throws SQLException;
    boolean deleteClass(String classId) throws SQLException;

    boolean departmentExists(String departmentId) throws SQLException;
    boolean classExists(String classId) throws SQLException;
    boolean classBelongsToDepartment(String classId, String departmentId) throws SQLException;
    String findAvailableClassId(String departmentId, int gradeYear) throws SQLException;
    /** True when the student still has course, library, cart or order records. */
    boolean studentIsReferenced(String studentId) throws SQLException;
    /** True when the teacher still has taught sections, library, cart or order records. */
    boolean teacherIsReferenced(String teacherId) throws SQLException;
    boolean departmentIsReferenced(String departmentId) throws SQLException;
    boolean classIsReferenced(String classId) throws SQLException;
}
