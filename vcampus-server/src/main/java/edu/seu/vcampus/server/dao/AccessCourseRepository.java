package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseEnrollmentDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.server.database.AccessDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Access-backed implementation of the course repository. */
public final class AccessCourseRepository implements CourseRepository {
    private final AccessDatabase database;

    public AccessCourseRepository(AccessDatabase database) throws SQLException {
        this.database = database;
        initializeSchema();
        seedDemoData();
    }

    @Override
    public List<CourseDto> findCourses(CourseQueryRequest query) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT c.[courseId], c.[courseName], c.[teacherId], "
                + "t.[fullName] AS [teacherName], c.[departmentId], d.[departmentName], "
                + "c.[credit], c.[capacity], c.[semesterName], c.[classTime], "
                + "c.[location], c.[description], c.[active] "
                + "FROM [tblCourse] c "
                + "LEFT JOIN [tblTeacher] t ON t.[teacherId] = c.[teacherId] "
                + "LEFT JOIN [tblDepartment] d ON d.[departmentId] = c.[departmentId] "
                + "WHERE 1=1");
        List<Object> parameters = new ArrayList<Object>();
        if (query != null) {
            if (!isBlank(query.getKeyword())) {
                sql.append(" AND (c.[courseId] LIKE ? OR c.[courseName] LIKE ?)");
                String pattern = "%" + query.getKeyword().trim() + "%";
                parameters.add(pattern);
                parameters.add(pattern);
            }
            addFilter(sql, parameters, "c.departmentId", query.getDepartmentId());
            addFilter(sql, parameters, "c.teacherId", query.getTeacherId());
            addFilter(sql, parameters, "c.semesterName", query.getSemesterName());
            if (query.isActiveOnly()) {
                sql.append(" AND c.[active] = ?");
                parameters.add(Boolean.TRUE);
            }
        }
        sql.append(" ORDER BY c.[courseId]");

        Map<String, Integer> counts = loadEnrollmentCounts();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<CourseDto> courses = new ArrayList<CourseDto>();
                while (result.next()) {
                    String courseId = result.getString("courseId");
                    int enrolled = counts.containsKey(courseId)
                            ? counts.get(courseId).intValue() : 0;
                    courses.add(readCourse(result, enrolled));
                }
                return courses; 
            }
        }
    }

    @Override
    public CourseDto findCourseById(String courseId) throws SQLException {
        String sql = "SELECT c.[courseId], c.[courseName], c.[teacherId], "
                + "t.[fullName] AS [teacherName], c.[departmentId], d.[departmentName], "
                + "c.[credit], c.[capacity], c.[semesterName], c.[classTime], "
                + "c.[location], c.[description], c.[active] "
                + "FROM [tblCourse] c "
                + "LEFT JOIN [tblTeacher] t ON t.[teacherId] = c.[teacherId] "
                + "LEFT JOIN [tblDepartment] d ON d.[departmentId] = c.[departmentId] "
                + "WHERE c.[courseId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readCourse(result, countEnrolled(courseId)) : null;
            }
        }
    }

    @Override
    public void saveCourse(CourseDto course) throws SQLException {
        String update = "UPDATE [tblCourse] SET [courseName]=?, [teacherId]=?, "
                + "[departmentId]=?, [credit]=?, [capacity]=?, [semesterName]=?, "
                + "[classTime]=?, [location]=?, [description]=?, [active]=? "
                + "WHERE [courseId]=?";
        String insert = "INSERT INTO [tblCourse] ([courseName], [teacherId], [departmentId], "
                + "[credit], [capacity], [semesterName], [classTime], [location], "
                + "[description], [active], [courseId]) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.openConnection()) {
            if (executeCourseSave(connection, update, course) == 0) {
                executeCourseSave(connection, insert, course);
            }
        }
    }

    @Override
    public boolean deleteCourse(String courseId) throws SQLException {
        String sql = "DELETE FROM [tblCourse] WHERE [courseId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean courseHasEnrollments(String courseId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblCourseEnrollment] WHERE [courseId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public boolean isEnrolled(String studentId, String courseId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblCourseEnrollment] "
                + "WHERE [studentId]=? AND [courseId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            statement.setString(2, courseId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public int countEnrolled(String courseId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblCourseEnrollment] WHERE [courseId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    @Override
    public boolean hasTimeConflict(String studentId, String classTime) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblCourseEnrollment] e "
                + "INNER JOIN [tblCourse] c ON c.[courseId] = e.[courseId] "
                + "WHERE e.[studentId]=? AND c.[classTime]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            statement.setString(2, classTime);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public void insertEnrollment(String studentId, String courseId,
                                 String enrollmentId, String enrollTime) throws SQLException {
        String sql = "INSERT INTO [tblCourseEnrollment] "
                + "([enrollmentId], [studentId], [courseId], [enrollTime]) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, enrollmentId);
            statement.setString(2, studentId);
            statement.setString(3, courseId);
            statement.setString(4, enrollTime);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean deleteEnrollment(String studentId, String enrollmentId) throws SQLException {
        String sql = "DELETE FROM [tblCourseEnrollment] "
                + "WHERE [enrollmentId]=? AND [studentId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, enrollmentId);
            statement.setString(2, studentId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public List<CourseEnrollmentDto> findSchedule(String studentId) throws SQLException {
        String sql = "SELECT e.[enrollmentId], e.[courseId], c.[courseName], "
                + "t.[fullName] AS [teacherName], c.[credit], c.[classTime], "
                + "c.[location], e.[enrollTime] "
                + "FROM [tblCourseEnrollment] e "
                + "INNER JOIN [tblCourse] c ON c.[courseId] = e.[courseId] "
                + "LEFT JOIN [tblTeacher] t ON t.[teacherId] = c.[teacherId] "
                + "WHERE e.[studentId] = ? ORDER BY e.[enrollTime]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                List<CourseEnrollmentDto> schedule = new ArrayList<CourseEnrollmentDto>();
                while (result.next()) {
                    schedule.add(new CourseEnrollmentDto(
                            result.getString("enrollmentId"),
                            result.getString("courseId"),
                            result.getString("courseName"),
                            result.getString("teacherName"),
                            result.getDouble("credit"),
                            result.getString("classTime"),
                            result.getString("location"),
                            result.getString("enrollTime")));
                }
                return schedule;
            }
        }
    }

    @Override
    public StudentDto findStudentByUserId(String userId) throws SQLException {
        String sql = "SELECT * FROM [tblStudent] WHERE [userId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new StudentDto(result.getString("studentId"),
                        result.getString("userId"), result.getString("fullName"),
                        result.getString("genderName"), result.getString("birthDate"),
                        result.getString("departmentId"), result.getString("classId"),
                        result.getInt("enrollmentYear"), result.getString("statusName"),
                        result.getString("phone"), result.getString("email"));
            }
        }
    }

    @Override
    public String findTeacherIdByUserId(String userId) throws SQLException {
        String sql = "SELECT [teacherId] FROM [tblTeacher] WHERE [userId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    @Override
    public boolean teacherExists(String teacherId) throws SQLException {
        return countReferences("tblTeacher", "teacherId", teacherId) > 0;
    }

    @Override
    public boolean departmentExists(String departmentId) throws SQLException {
        return countReferences("tblDepartment", "departmentId", departmentId) > 0;
    }

    private void initializeSchema() throws SQLException {
        try (Connection connection = database.openConnection()) {
            if (!tableExists(connection, "tblCourse")) {
                execute(connection, "CREATE TABLE [tblCourse] ("
                        + "[courseId] TEXT(20) NOT NULL PRIMARY KEY, "
                        + "[courseName] TEXT(64) NOT NULL, [teacherId] TEXT(20) NOT NULL, "
                        + "[departmentId] TEXT(16) NOT NULL, [credit] DOUBLE NOT NULL, "
                        + "[capacity] INTEGER NOT NULL, [semesterName] TEXT(32) NOT NULL, "
                        + "[classTime] TEXT(64) NOT NULL, [location] TEXT(64), "
                        + "[description] TEXT(255), [active] YESNO NOT NULL, "
                        + "CONSTRAINT [fkCourseTeacher] FOREIGN KEY ([teacherId]) "
                        + "REFERENCES [tblTeacher] ([teacherId]), "
                        + "CONSTRAINT [fkCourseDepartment] FOREIGN KEY ([departmentId]) "
                        + "REFERENCES [tblDepartment] ([departmentId]))");
            }
            if (!tableExists(connection, "tblCourseEnrollment")) {
                execute(connection, "CREATE TABLE [tblCourseEnrollment] ("
                        + "[enrollmentId] TEXT(32) NOT NULL PRIMARY KEY, "
                        + "[studentId] TEXT(20) NOT NULL, [courseId] TEXT(20) NOT NULL, "
                        + "[enrollTime] TEXT(19) NOT NULL, "
                        + "CONSTRAINT [uqEnrollmentCourseStudent] "
                        + "UNIQUE ([courseId], [studentId]), "
                        + "CONSTRAINT [fkEnrollmentStudent] FOREIGN KEY ([studentId]) "
                        + "REFERENCES [tblStudent] ([studentId]), "
                        + "CONSTRAINT [fkEnrollmentCourse] FOREIGN KEY ([courseId]) "
                        + "REFERENCES [tblCourse] ([courseId]))");
            }
        }
    }

    private void seedDemoData() throws SQLException {
        if (!exists("tblCourse", "courseId", "CS101")) {
            saveCourse(new CourseDto("CS101", "Java 程序设计", "T0001", "演示教师",
                    "CS", "计算机科学与工程学院", 3.0, 30, 0,
                    "2026-2027-1", "周一 3-4 节", "教1-101",
                    "Java 基础与面向对象编程", true));
        }
        if (!exists("tblCourse", "courseId", "CS102")) {
            saveCourse(new CourseDto("CS102", "数据结构", "T0001", "演示教师",
                    "CS", "计算机科学与工程学院", 4.0, 30, 0,
                    "2026-2027-1", "周三 1-2 节", "教2-203",
                    "线性表、树与图", true));
        }
        if (!isEnrolled("20260001", "CS101")) {
            insertEnrollment("20260001", "CS101",
                    "DEMO-ENROLL-001", "2026-08-25 09:00:00");
        }
    }

    private int executeCourseSave(Connection connection, String sql, CourseDto course)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, course.getCourseName());
            statement.setString(2, course.getTeacherId());
            statement.setString(3, course.getDepartmentId());
            statement.setDouble(4, course.getCredit());
            statement.setInt(5, course.getCapacity());
            statement.setString(6, course.getSemesterName());
            statement.setString(7, course.getClassTime());
            setNullableString(statement, 8, course.getLocation());
            setNullableString(statement, 9, course.getDescription());
            statement.setBoolean(10, course.isActive());
            statement.setString(11, course.getCourseId());
            return statement.executeUpdate();
        }
    }

    private CourseDto readCourse(ResultSet result, int enrolledCount) throws SQLException {
        return new CourseDto(result.getString("courseId"),
                result.getString("courseName"), result.getString("teacherId"),
                result.getString("teacherName"), result.getString("departmentId"),
                result.getString("departmentName"), result.getDouble("credit"),
                result.getInt("capacity"), enrolledCount,
                result.getString("semesterName"), result.getString("classTime"),
                result.getString("location"), result.getString("description"),
                result.getBoolean("active"));
    }

    private Map<String, Integer> loadEnrollmentCounts() throws SQLException {
        String sql = "SELECT [courseId], COUNT(*) FROM [tblCourseEnrollment] "
                + "GROUP BY [courseId]";
        Map<String, Integer> counts = new HashMap<String, Integer>();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    counts.put(result.getString(1), Integer.valueOf(result.getInt(2)));
                }
            }
        }
        return counts;
    }

    private int countReferences(String table, String column, String value) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [" + table + "] WHERE [" + column + "]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private boolean exists(String table, String idColumn, String id) throws SQLException {
        return countReferences(table, idColumn, id) > 0;
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, null, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void addFilter(StringBuilder sql, List<Object> parameters,
                           String qualifiedColumn, String value) {
        if (!isBlank(value)) {
            sql.append(" AND ").append(qualifiedColumn).append(" = ?");
            parameters.add(value.trim());
        }
    }

    private void bindParameters(PreparedStatement statement, List<Object> values)
            throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof Boolean) {
                statement.setBoolean(i + 1, ((Boolean) value).booleanValue());
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (isBlank(value)) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
