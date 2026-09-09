package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.dto.CourseDto;
import edu.seu.vcampus.common.dto.CourseEnrollmentDto;
import edu.seu.vcampus.common.dto.CourseQueryRequest;
import edu.seu.vcampus.common.dto.SectionAudienceDto;
import edu.seu.vcampus.common.dto.SectionRosterEntry;
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
import java.util.UUID;

/**
 * Access-backed implementation of the course repository. Schema v2 keeps the
 * catalog ({@code tblCourse}) separate from the section ({@code tblCourseSection})
 * and stores audience rules in {@code tblSectionAudience}. When an older v1
 * course schema is detected the course-owned demo tables are rebuilt; every
 * table is auto-seeded afterwards so no manual migration is required.
 */
public final class AccessCourseRepository implements CourseRepository {
    private static final int SECTION_ID_LENGTH = 24;

    private final AccessDatabase database;

    public AccessCourseRepository(AccessDatabase database) throws SQLException {
        this.database = database;
        initializeSchema();
        seedDemoData();
    }

    @Override
    public List<CourseDto> findSections(CourseQueryRequest query) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT s.[sectionId], s.[courseId], c.[courseName], c.[description], "
                + "c.[credit], s.[teacherId], t.[fullName] AS [teacherName], "
                + "s.[departmentId], d.[departmentName], s.[courseNature], s.[capacity], "
                + "s.[semesterName], s.[classTime], s.[location], "
                + "s.[selectionStartTime], s.[selectionEndTime], s.[active] "
                + "FROM [tblCourseSection] s "
                + "INNER JOIN [tblCourse] c ON c.[courseId] = s.[courseId] "
                + "LEFT JOIN [tblTeacher] t ON t.[teacherId] = s.[teacherId] "
                + "LEFT JOIN [tblDepartment] d ON d.[departmentId] = s.[departmentId] "
                + "WHERE 1=1");
        List<Object> parameters = new ArrayList<Object>();
        if (query != null) {
            if (!isBlank(query.getKeyword())) {
                sql.append(" AND (c.[courseId] LIKE ? OR c.[courseName] LIKE ? "
                        + "OR t.[fullName] LIKE ?)");
                String pattern = "%" + query.getKeyword().trim() + "%";
                parameters.add(pattern);
                parameters.add(pattern);
                parameters.add(pattern);
            }
            addFilter(sql, parameters, "s.departmentId", query.getDepartmentId());
            addFilter(sql, parameters, "s.teacherId", query.getTeacherId());
            addFilter(sql, parameters, "s.semesterName", query.getSemesterName());
            addFilter(sql, parameters, "s.courseNature", query.getNature());
            if (query.isActiveOnly()) {
                sql.append(" AND s.[active] = ?");
                parameters.add(Boolean.TRUE);
            }
        }
        sql.append(" ORDER BY s.[semesterName], c.[courseId], s.[sectionId]");

        Map<String, Integer> counts = loadEnrollmentCounts();
        Map<String, List<SectionAudienceDto>> audiences = loadAudiences();
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<CourseDto> sections = new ArrayList<CourseDto>();
                while (result.next()) {
                    String sectionId = result.getString("sectionId");
                    sections.add(readSection(result, enrolledOf(counts, sectionId),
                            audiencesOf(audiences, sectionId), false, null));
                }
                return sections;
            }
        }
    }

    @Override
    public CourseDto findSectionById(String sectionId) throws SQLException {
        String sql = "SELECT s.[sectionId], s.[courseId], c.[courseName], c.[description], "
                + "c.[credit], s.[teacherId], t.[fullName] AS [teacherName], "
                + "s.[departmentId], d.[departmentName], s.[courseNature], s.[capacity], "
                + "s.[semesterName], s.[classTime], s.[location], "
                + "s.[selectionStartTime], s.[selectionEndTime], s.[active] "
                + "FROM [tblCourseSection] s "
                + "INNER JOIN [tblCourse] c ON c.[courseId] = s.[courseId] "
                + "LEFT JOIN [tblTeacher] t ON t.[teacherId] = s.[teacherId] "
                + "LEFT JOIN [tblDepartment] d ON d.[departmentId] = s.[departmentId] "
                + "WHERE s.[sectionId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sectionId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return readSection(result, countEnrolled(sectionId),
                        loadAudiences(sectionId), false, null);
            }
        }
    }

    @Override
    public CourseDto saveSection(CourseDto section) throws SQLException {
        String sectionId = isBlank(section.getSectionId())
                ? newSectionId() : section.getSectionId().trim();
        saveCatalog(section);
        saveSectionRow(section, sectionId);
        replaceAudiences(sectionId, section.getAudiences());
        return findSectionById(sectionId);
    }

    @Override
    public boolean deleteSection(String sectionId) throws SQLException {
        try (Connection connection = database.openConnection()) {
            deleteBy(connection, "tblSectionAudience", "sectionId", sectionId);
            String sql = "DELETE FROM [tblCourseSection] WHERE [sectionId]=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, sectionId);
                return statement.executeUpdate() > 0;
            }
        }
    }

    @Override
    public boolean sectionHasEnrollments(String sectionId) throws SQLException {
        return countReferences("tblCourseEnrollment", "sectionId", sectionId) > 0;
    }

    @Override
    public List<SectionRosterEntry> findRoster(String sectionId) throws SQLException {
        String sql = "SELECT e.[sectionId], s.[courseId], c.[courseName], e.[studentId], "
                + "st.[fullName], dep.[departmentName], cl.[className], e.[enrollTime] "
                + "FROM [tblCourseEnrollment] e "
                + "INNER JOIN [tblCourseSection] s ON s.[sectionId] = e.[sectionId] "
                + "INNER JOIN [tblCourse] c ON c.[courseId] = s.[courseId] "
                + "INNER JOIN [tblStudent] st ON st.[studentId] = e.[studentId] "
                + "LEFT JOIN [tblDepartment] dep ON dep.[departmentId] = st.[departmentId] "
                + "LEFT JOIN [tblSchoolClass] cl ON cl.[classId] = st.[classId] "
                + "WHERE e.[sectionId] = ? ORDER BY st.[studentId]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sectionId);
            try (ResultSet result = statement.executeQuery()) {
                List<SectionRosterEntry> roster = new ArrayList<SectionRosterEntry>();
                while (result.next()) {
                    roster.add(new SectionRosterEntry(
                            result.getString("sectionId"),
                            result.getString("courseId"),
                            result.getString("courseName"),
                            result.getString("studentId"),
                            result.getString("fullName"),
                            result.getString("departmentName"),
                            result.getString("className"),
                            result.getString("enrollTime")));
                }
                return roster;
            }
        }
    }

    @Override
    public List<EnrolledStudentTime> findEnrolledStudentsOtherClassTimes(
            String sectionId) throws SQLException {
        String sql = "SELECT DISTINCT e1.[studentId], st.[fullName], s2.[classTime] "
                + "FROM [tblCourseEnrollment] e1 "
                + "INNER JOIN [tblStudent] st ON st.[studentId] = e1.[studentId] "
                + "INNER JOIN [tblCourseEnrollment] e2 "
                + "ON e2.[studentId] = e1.[studentId] "
                + "AND e2.[sectionId] <> e1.[sectionId] "
                + "INNER JOIN [tblCourseSection] s2 ON s2.[sectionId] = e2.[sectionId] "
                + "WHERE e1.[sectionId] = ? ORDER BY e1.[studentId], s2.[classTime]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sectionId);
            try (ResultSet result = statement.executeQuery()) {
                List<EnrolledStudentTime> rows = new ArrayList<EnrolledStudentTime>();
                while (result.next()) {
                    rows.add(new EnrolledStudentTime(
                            result.getString("studentId"), result.getString("fullName"),
                            result.getString("classTime")));
                }
                return rows;
            }
        }
    }

    @Override
    public boolean isEnrolled(String studentId, String sectionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblCourseEnrollment] "
                + "WHERE [studentId]=? AND [sectionId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            statement.setString(2, sectionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public boolean isEnrolledInCourse(String studentId, String courseId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblCourseEnrollment] e "
                + "INNER JOIN [tblCourseSection] s ON s.[sectionId] = e.[sectionId] "
                + "WHERE e.[studentId]=? AND s.[courseId]=?";
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
    public int countEnrolled(String sectionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblCourseEnrollment] WHERE [sectionId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sectionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    @Override
    public List<String> findStudentEnrolledClassTimes(String studentId)
            throws SQLException {
        String sql = "SELECT s.[classTime] FROM [tblCourseEnrollment] e "
                + "INNER JOIN [tblCourseSection] s ON s.[sectionId] = e.[sectionId] "
                + "WHERE e.[studentId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                List<String> times = new ArrayList<String>();
                while (result.next()) {
                    times.add(result.getString(1));
                }
                return times;
            }
        }
    }

    @Override
    public List<String> findTeacherSectionClassTimes(
            String teacherId, String excludeSectionId) throws SQLException {
        String sql = "SELECT [classTime] FROM [tblCourseSection] "
                + "WHERE [teacherId] = ? AND [sectionId] <> ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, teacherId);
            statement.setString(2, excludeSectionId);
            try (ResultSet result = statement.executeQuery()) {
                List<String> times = new ArrayList<String>();
                while (result.next()) {
                    times.add(result.getString(1));
                }
                return times;
            }
        }
    }

    @Override
    public void insertEnrollment(String studentId, String sectionId,
                                 String enrollmentId, String enrollTime) throws SQLException {
        String sql = "INSERT INTO [tblCourseEnrollment] "
                + "([enrollmentId], [studentId], [sectionId], [enrollTime]) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, enrollmentId);
            statement.setString(2, studentId);
            statement.setString(3, sectionId);
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
        String sql = "SELECT e.[enrollmentId], e.[sectionId], s.[courseId], c.[courseName], "
                + "s.[courseNature], t.[fullName] AS [teacherName], c.[credit], "
                + "s.[classTime], s.[location], e.[enrollTime] "
                + "FROM [tblCourseEnrollment] e "
                + "INNER JOIN [tblCourseSection] s ON s.[sectionId] = e.[sectionId] "
                + "INNER JOIN [tblCourse] c ON c.[courseId] = s.[courseId] "
                + "LEFT JOIN [tblTeacher] t ON t.[teacherId] = s.[teacherId] "
                + "WHERE e.[studentId] = ? ORDER BY e.[enrollTime]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                List<CourseEnrollmentDto> schedule = new ArrayList<CourseEnrollmentDto>();
                while (result.next()) {
                    schedule.add(new CourseEnrollmentDto(
                            result.getString("enrollmentId"),
                            result.getString("sectionId"),
                            result.getString("courseId"),
                            result.getString("courseName"),
                            result.getString("courseNature"),
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

    private void saveCatalog(CourseDto section) throws SQLException {
        String update = "UPDATE [tblCourse] SET [courseName]=?, [credit]=?, "
                + "[description]=? WHERE [courseId]=?";
        String insert = "INSERT INTO [tblCourse] ([courseId], [courseName], [credit], "
                + "[description], [active]) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = database.openConnection()) {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, section.getCourseName().trim());
                statement.setDouble(2, section.getCredit());
                setNullableString(statement, 3, section.getDescription());
                statement.setString(4, section.getCourseId().trim());
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, section.getCourseId().trim());
                    statement.setString(2, section.getCourseName().trim());
                    statement.setDouble(3, section.getCredit());
                    setNullableString(statement, 4, section.getDescription());
                    statement.setBoolean(5, true);
                    statement.executeUpdate();
                }
            }
        }
    }

    private void saveSectionRow(CourseDto section, String sectionId) throws SQLException {
        String update = "UPDATE [tblCourseSection] SET [courseId]=?, [teacherId]=?, "
                + "[departmentId]=?, [semesterName]=?, [classTime]=?, [location]=?, "
                + "[capacity]=?, [courseNature]=?, [selectionStartTime]=?, "
                + "[selectionEndTime]=?, [active]=? WHERE [sectionId]=?";
        String insert = "INSERT INTO [tblCourseSection] ([sectionId], [courseId], "
                + "[teacherId], [departmentId], [semesterName], [classTime], [location], "
                + "[capacity], [courseNature], [selectionStartTime], [selectionEndTime], "
                + "[active]) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.openConnection()) {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                bindSectionRow(statement, section, sectionId, false);
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    bindSectionRow(statement, section, sectionId, true);
                    statement.executeUpdate();
                }
            }
        }
    }

    private void bindSectionRow(PreparedStatement statement, CourseDto section,
                                String sectionId, boolean insert) throws SQLException {
        if (insert) {
            statement.setString(1, sectionId);
            statement.setString(2, section.getCourseId().trim());
            statement.setString(3, section.getTeacherId().trim());
            statement.setString(4, section.getDepartmentId().trim());
            statement.setString(5, section.getSemesterName().trim());
            statement.setString(6, section.getClassTime().trim());
            setNullableString(statement, 7, section.getLocation());
            statement.setInt(8, section.getCapacity());
            statement.setString(9, section.getCourseNature().trim());
            setNullableString(statement, 10, section.getSelectionStartTime());
            setNullableString(statement, 11, section.getSelectionEndTime());
            statement.setBoolean(12, section.isActive());
        } else {
            statement.setString(1, section.getCourseId().trim());
            statement.setString(2, section.getTeacherId().trim());
            statement.setString(3, section.getDepartmentId().trim());
            statement.setString(4, section.getSemesterName().trim());
            statement.setString(5, section.getClassTime().trim());
            setNullableString(statement, 6, section.getLocation());
            statement.setInt(7, section.getCapacity());
            statement.setString(8, section.getCourseNature().trim());
            setNullableString(statement, 9, section.getSelectionStartTime());
            setNullableString(statement, 10, section.getSelectionEndTime());
            statement.setBoolean(11, section.isActive());
            statement.setString(12, sectionId);
        }
    }

    private void replaceAudiences(String sectionId, List<SectionAudienceDto> audiences)
            throws SQLException {
        try (Connection connection = database.openConnection()) {
            deleteBy(connection, "tblSectionAudience", "sectionId", sectionId);
            if (audiences == null) {
                return;
            }
            String sql = "INSERT INTO [tblSectionAudience] "
                    + "([audienceId], [sectionId], [scopeType], [scopeValue], "
                    + "[yearMin], [yearMax]) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (SectionAudienceDto audience : audiences) {
                    statement.setString(1, UUID.randomUUID().toString().replace("-", ""));
                    statement.setString(2, sectionId);
                    statement.setString(3, audience.getScopeType());
                    setNullableString(statement, 4, audience.getScopeValue());
                    setNullableInteger(statement, 5, audience.getYearMin());
                    setNullableInteger(statement, 6, audience.getYearMax());
                    statement.executeUpdate();
                }
            }
        }
    }

    private List<SectionAudienceDto> loadAudiences(String sectionId) throws SQLException {
        String sql = "SELECT [audienceId], [sectionId], [scopeType], [scopeValue], "
                + "[yearMin], [yearMax] FROM [tblSectionAudience] WHERE [sectionId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sectionId);
            try (ResultSet result = statement.executeQuery()) {
                List<SectionAudienceDto> audiences = new ArrayList<SectionAudienceDto>();
                while (result.next()) {
                    audiences.add(readAudience(result));
                }
                return audiences;
            }
        }
    }

    private Map<String, List<SectionAudienceDto>> loadAudiences() throws SQLException {
        Map<String, List<SectionAudienceDto>> bySection =
                new HashMap<String, List<SectionAudienceDto>>();
        String sql = "SELECT [audienceId], [sectionId], [scopeType], [scopeValue], "
                + "[yearMin], [yearMax] FROM [tblSectionAudience]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String sectionId = result.getString("sectionId");
                    List<SectionAudienceDto> list = bySection.get(sectionId);
                    if (list == null) {
                        list = new ArrayList<SectionAudienceDto>();
                        bySection.put(sectionId, list);
                    }
                    list.add(readAudience(result));
                }
            }
        }
        return bySection;
    }

    private Map<String, Integer> loadEnrollmentCounts() throws SQLException {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        String sql = "SELECT [sectionId], COUNT(*) FROM [tblCourseEnrollment] "
                + "GROUP BY [sectionId]";
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

    private CourseDto readSection(ResultSet result, int enrolledCount,
                                  List<SectionAudienceDto> audiences,
                                  boolean selected, String reason) throws SQLException {
        return new CourseDto(result.getString("sectionId"),
                result.getString("courseId"), result.getString("courseName"),
                result.getString("description"), result.getString("teacherId"),
                result.getString("teacherName"), result.getString("departmentId"),
                result.getString("departmentName"), result.getDouble("credit"),
                result.getString("courseNature"), result.getInt("capacity"), enrolledCount,
                result.getString("semesterName"), result.getString("classTime"),
                result.getString("location"), result.getString("selectionStartTime"),
                result.getString("selectionEndTime"), result.getBoolean("active"),
                selected, reason, audiences);
    }

    private SectionAudienceDto readAudience(ResultSet result) throws SQLException {
        Integer yearMin = result.getObject("yearMin") == null
                ? null : Integer.valueOf(result.getInt("yearMin"));
        Integer yearMax = result.getObject("yearMax") == null
                ? null : Integer.valueOf(result.getInt("yearMax"));
        return new SectionAudienceDto(result.getString("audienceId"),
                result.getString("scopeType"), result.getString("scopeValue"),
                yearMin, yearMax);
    }

    private void initializeSchema() throws SQLException {
        try (Connection connection = database.openConnection()) {
            boolean legacy = tableExists(connection, "tblCourseEnrollment")
                    && !columnExists(connection, "tblCourseEnrollment", "sectionId");
            if (legacy || !tableExists(connection, "tblCourseSection")) {
                dropIfExists(connection, "tblCourseEnrollment");
                dropIfExists(connection, "tblSectionAudience");
                dropIfExists(connection, "tblCourseSection");
                dropIfExists(connection, "tblCourse");
                createCourseTables(connection);
            }
        }
    }

    private void createCourseTables(Connection connection) throws SQLException {
        execute(connection, "CREATE TABLE [tblCourse] ("
                + "[courseId] TEXT(20) NOT NULL PRIMARY KEY, "
                + "[courseName] TEXT(64) NOT NULL, [credit] DOUBLE NOT NULL, "
                + "[description] TEXT(255), [active] YESNO NOT NULL)");
        execute(connection, "CREATE TABLE [tblCourseSection] ("
                + "[sectionId] TEXT(24) NOT NULL PRIMARY KEY, "
                + "[courseId] TEXT(20) NOT NULL, [teacherId] TEXT(20) NOT NULL, "
                + "[departmentId] TEXT(16) NOT NULL, [semesterName] TEXT(32) NOT NULL, "
                + "[classTime] TEXT(64) NOT NULL, [location] TEXT(64), "
                + "[capacity] INTEGER NOT NULL, [courseNature] TEXT(16) NOT NULL, "
                + "[selectionStartTime] TEXT(16), [selectionEndTime] TEXT(16), "
                + "[active] YESNO NOT NULL, "
                + "CONSTRAINT [fkSectionCourse] FOREIGN KEY ([courseId]) "
                + "REFERENCES [tblCourse] ([courseId]), "
                + "CONSTRAINT [fkSectionTeacher] FOREIGN KEY ([teacherId]) "
                + "REFERENCES [tblTeacher] ([teacherId]), "
                + "CONSTRAINT [fkSectionDepartment] FOREIGN KEY ([departmentId]) "
                + "REFERENCES [tblDepartment] ([departmentId]))");
        execute(connection, "CREATE TABLE [tblSectionAudience] ("
                + "[audienceId] TEXT(32) NOT NULL PRIMARY KEY, "
                + "[sectionId] TEXT(24) NOT NULL, "
                + "[scopeType] TEXT(12) NOT NULL, [scopeValue] TEXT(16), "
                + "[yearMin] INTEGER, [yearMax] INTEGER, "
                + "CONSTRAINT [fkAudienceSection] FOREIGN KEY ([sectionId]) "
                + "REFERENCES [tblCourseSection] ([sectionId]))");
        execute(connection, "CREATE TABLE [tblCourseEnrollment] ("
                + "[enrollmentId] TEXT(32) NOT NULL PRIMARY KEY, "
                + "[studentId] TEXT(20) NOT NULL, [sectionId] TEXT(24) NOT NULL, "
                + "[enrollTime] TEXT(19) NOT NULL, "
                + "CONSTRAINT [uqEnrollmentSectionStudent] "
                + "UNIQUE ([sectionId], [studentId]), "
                + "CONSTRAINT [fkEnrollmentStudent] FOREIGN KEY ([studentId]) "
                + "REFERENCES [tblStudent] ([studentId]), "
                + "CONSTRAINT [fkEnrollmentSection] FOREIGN KEY ([sectionId]) "
                + "REFERENCES [tblCourseSection] ([sectionId]))");
    }

    private void seedDemoData() throws SQLException {
        if (!exists("tblCourseSection", "sectionId", "SEC00000001")) {
            CourseDto javaSection = new CourseDto("SEC00000001", "CS101", "Java 程序设计",
                    "Java 基础与面向对象编程", "T0001", null, "CS", null,
                    3.0, "必修", 30, 0, "2026-2027-1", "周一 3-4 节", "教1-101",
                    "2026-09-01 08:00", "2026-12-31 23:59", true, false, null,
                    java.util.Collections.singletonList(new SectionAudienceDto(
                            null, SectionAudienceDto.SCOPE_ALL, null, null, null)));
            saveSection(javaSection);
        }
        if (!exists("tblCourseSection", "sectionId", "SEC00000002")) {
            CourseDto dataSection = new CourseDto("SEC00000002", "CS102", "数据结构",
                    "线性表、树与图", "T0001", null, "CS", null,
                    4.0, "限选", 30, 0, "2026-2027-1", "周三 1-2 节", "教2-203",
                    "2026-09-01 08:00", "2026-12-31 23:59", true, false, null,
                    java.util.Collections.singletonList(new SectionAudienceDto(
                            null, SectionAudienceDto.SCOPE_DEPARTMENT, "CS",
                            2024, 2028)));
            saveSection(dataSection);
        }
        if (!isEnrolled("20260001", "SEC00000001")) {
            insertEnrollment("20260001", "SEC00000001",
                    "DEMO-ENROLL-001", "2026-08-25 09:00:00");
        }
    }

    private boolean exists(String table, String idColumn, String id) throws SQLException {
        return countReferences(table, idColumn, id) > 0;
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

    private boolean columnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(
                null, null, tableName, "%")) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void dropIfExists(Connection connection, String tableName) throws SQLException {
        if (tableExists(connection, tableName)) {
            execute(connection, "DROP TABLE [" + tableName + "]");
        }
    }

    private void deleteBy(Connection connection, String table, String column, String value)
            throws SQLException {
        String sql = "DELETE FROM [" + table + "] WHERE [" + column + "]=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.executeUpdate();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int enrolledOf(Map<String, Integer> counts, String sectionId) {
        Integer count = counts.get(sectionId);
        return count == null ? 0 : count.intValue();
    }

    private List<SectionAudienceDto> audiencesOf(
            Map<String, List<SectionAudienceDto>> audiences, String sectionId) {
        List<SectionAudienceDto> list = audiences.get(sectionId);
        return list == null ? new ArrayList<SectionAudienceDto>() : list;
    }

    private String newSectionId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "SEC" + uuid.substring(0, SECTION_ID_LENGTH - 3);
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

    private void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value.intValue());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
