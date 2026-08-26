package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.common.dto.AcademicQueryRequest;
import edu.seu.vcampus.common.dto.DepartmentDto;
import edu.seu.vcampus.common.dto.SchoolClassDto;
import edu.seu.vcampus.common.dto.StudentDto;
import edu.seu.vcampus.common.dto.TeacherDto;
import edu.seu.vcampus.server.database.AccessDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Access-backed implementation of the academic repository. */
public final class AccessAcademicRepository implements AcademicRepository {
    private final AccessDatabase database;

    public AccessAcademicRepository(AccessDatabase database) throws SQLException {
        this.database = database;
        initializeSchema();
        seedDemoData();
    }

    @Override
    public List<StudentDto> findStudents(AcademicQueryRequest query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM [tblStudent] WHERE 1=1");
        List<Object> parameters = new ArrayList<Object>();
        if (query != null) {
            if (!isBlank(query.getKeyword())) {
                sql.append(" AND ([studentId] LIKE ? OR [fullName] LIKE ?)");
                String pattern = "%" + query.getKeyword().trim() + "%";
                parameters.add(pattern);
                parameters.add(pattern);
            }
            addFilter(sql, parameters, "departmentId", query.getDepartmentId());
            addFilter(sql, parameters, "classId", query.getClassId());
            if (query.isActiveOnly()) {
                sql.append(" AND [statusName] = ?");
                parameters.add("在读");
            }
        }
        sql.append(" ORDER BY [studentId]");
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<StudentDto> students = new ArrayList<StudentDto>();
                while (result.next()) {
                    students.add(readStudent(result));
                }
                return students;
            }
        }
    }

    @Override
    public StudentDto findStudentByUserId(String userId) throws SQLException {
        String sql = "SELECT * FROM [tblStudent] WHERE [userId] = ?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readStudent(result) : null;
            }
        }
    }

    @Override
    public void saveStudent(StudentDto student) throws SQLException {
        String update = "UPDATE [tblStudent] SET [userId]=?, [fullName]=?, [genderName]=?, "
                + "[birthDate]=?, [departmentId]=?, [classId]=?, [enrollmentYear]=?, "
                + "[statusName]=?, [phone]=?, [email]=? WHERE [studentId]=?";
        String insert = "INSERT INTO [tblStudent] ([userId], [fullName], [genderName], "
                + "[birthDate], [departmentId], [classId], [enrollmentYear], [statusName], "
                + "[phone], [email], [studentId]) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.openConnection()) {
            if (executeStudentSave(connection, update, student) == 0) {
                executeStudentSave(connection, insert, student);
            }
        }
    }

    @Override
    public boolean deleteStudent(String studentId) throws SQLException {
        return deleteById("tblStudent", "studentId", studentId);
    }

    @Override
    public List<TeacherDto> findTeachers(AcademicQueryRequest query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM [tblTeacher] WHERE 1=1");
        List<Object> parameters = new ArrayList<Object>();
        if (query != null) {
            if (!isBlank(query.getKeyword())) {
                sql.append(" AND ([teacherId] LIKE ? OR [fullName] LIKE ?)");
                String pattern = "%" + query.getKeyword().trim() + "%";
                parameters.add(pattern);
                parameters.add(pattern);
            }
            addFilter(sql, parameters, "departmentId", query.getDepartmentId());
            if (query.isActiveOnly()) {
                sql.append(" AND [active] = ?");
                parameters.add(Boolean.TRUE);
            }
        }
        sql.append(" ORDER BY [teacherId]");
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<TeacherDto> teachers = new ArrayList<TeacherDto>();
                while (result.next()) {
                    teachers.add(new TeacherDto(result.getString("teacherId"),
                            result.getString("userId"), result.getString("fullName"),
                            result.getString("departmentId"), result.getString("titleName"),
                            result.getString("phone"), result.getString("email"),
                            result.getBoolean("active")));
                }
                return teachers;
            }
        }
    }

    @Override
    public void saveTeacher(TeacherDto teacher) throws SQLException {
        String update = "UPDATE [tblTeacher] SET [userId]=?, [fullName]=?, "
                + "[departmentId]=?, [titleName]=?, [phone]=?, [email]=?, [active]=? "
                + "WHERE [teacherId]=?";
        String insert = "INSERT INTO [tblTeacher] ([userId], [fullName], [departmentId], "
                + "[titleName], [phone], [email], [active], [teacherId]) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.openConnection()) {
            if (executeTeacherSave(connection, update, teacher) == 0) {
                executeTeacherSave(connection, insert, teacher);
            }
        }
    }

    @Override
    public boolean deleteTeacher(String teacherId) throws SQLException {
        return deleteById("tblTeacher", "teacherId", teacherId);
    }

    @Override
    public List<DepartmentDto> findDepartments(boolean activeOnly) throws SQLException {
        String sql = "SELECT * FROM [tblDepartment]"
                + (activeOnly ? " WHERE [active] = ?" : "")
                + " ORDER BY [departmentId]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (activeOnly) {
                statement.setBoolean(1, true);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<DepartmentDto> departments = new ArrayList<DepartmentDto>();
                while (result.next()) {
                    departments.add(new DepartmentDto(result.getString("departmentId"),
                            result.getString("departmentName"),
                            result.getString("description"), result.getBoolean("active")));
                }
                return departments;
            }
        }
    }

    @Override
    public void saveDepartment(DepartmentDto department) throws SQLException {
        String update = "UPDATE [tblDepartment] SET [departmentName]=?, [description]=?, "
                + "[active]=? WHERE [departmentId]=?";
        String insert = "INSERT INTO [tblDepartment] ([departmentName], [description], "
                + "[active], [departmentId]) VALUES (?, ?, ?, ?)";
        try (Connection connection = database.openConnection()) {
            if (executeDepartmentSave(connection, update, department) == 0) {
                executeDepartmentSave(connection, insert, department);
            }
        }
    }

    @Override
    public boolean deleteDepartment(String departmentId) throws SQLException {
        return deleteById("tblDepartment", "departmentId", departmentId);
    }

    @Override
    public List<SchoolClassDto> findClasses(AcademicQueryRequest query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM [tblSchoolClass] WHERE 1=1");
        List<Object> parameters = new ArrayList<Object>();
        if (query != null) {
            if (!isBlank(query.getKeyword())) {
                sql.append(" AND ([classId] LIKE ? OR [className] LIKE ?)");
                String pattern = "%" + query.getKeyword().trim() + "%";
                parameters.add(pattern);
                parameters.add(pattern);
            }
            addFilter(sql, parameters, "departmentId", query.getDepartmentId());
            if (query.isActiveOnly()) {
                sql.append(" AND [active] = ?");
                parameters.add(Boolean.TRUE);
            }
        }
        sql.append(" ORDER BY [classId]");
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParameters(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                List<SchoolClassDto> classes = new ArrayList<SchoolClassDto>();
                while (result.next()) {
                    classes.add(new SchoolClassDto(result.getString("classId"),
                            result.getString("className"), result.getString("departmentId"),
                            result.getInt("gradeYear"), result.getString("counselor"),
                            result.getBoolean("active")));
                }
                return classes;
            }
        }
    }

    @Override
    public void saveClass(SchoolClassDto schoolClass) throws SQLException {
        String update = "UPDATE [tblSchoolClass] SET [className]=?, [departmentId]=?, "
                + "[gradeYear]=?, [counselor]=?, [active]=? WHERE [classId]=?";
        String insert = "INSERT INTO [tblSchoolClass] ([className], [departmentId], "
                + "[gradeYear], [counselor], [active], [classId]) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.openConnection()) {
            if (executeClassSave(connection, update, schoolClass) == 0) {
                executeClassSave(connection, insert, schoolClass);
            }
        }
    }

    @Override
    public boolean deleteClass(String classId) throws SQLException {
        return deleteById("tblSchoolClass", "classId", classId);
    }

    @Override
    public boolean departmentExists(String departmentId) throws SQLException {
        return exists("tblDepartment", "departmentId", departmentId);
    }

    @Override
    public boolean classExists(String classId) throws SQLException {
        return exists("tblSchoolClass", "classId", classId);
    }

    @Override
    public boolean classBelongsToDepartment(String classId, String departmentId)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM [tblSchoolClass] WHERE [classId]=? "
                + "AND [departmentId]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, classId);
            statement.setString(2, departmentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    @Override
    public boolean departmentIsReferenced(String departmentId) throws SQLException {
        return countReferences("tblSchoolClass", "departmentId", departmentId) > 0
                || countReferences("tblStudent", "departmentId", departmentId) > 0
                || countReferences("tblTeacher", "departmentId", departmentId) > 0;
    }

    @Override
    public boolean classIsReferenced(String classId) throws SQLException {
        return countReferences("tblStudent", "classId", classId) > 0;
    }

    private void initializeSchema() throws SQLException {
        try (Connection connection = database.openConnection()) {
            if (!tableExists(connection, "tblDepartment")) {
                execute(connection, "CREATE TABLE [tblDepartment] ("
                        + "[departmentId] TEXT(16) NOT NULL PRIMARY KEY, "
                        + "[departmentName] TEXT(64) NOT NULL, [description] TEXT(255), "
                        + "[active] YESNO NOT NULL, CONSTRAINT [uqDepartmentName] "
                        + "UNIQUE ([departmentName]))");
            }
            if (!tableExists(connection, "tblSchoolClass")) {
                execute(connection, "CREATE TABLE [tblSchoolClass] ("
                        + "[classId] TEXT(20) NOT NULL PRIMARY KEY, "
                        + "[className] TEXT(64) NOT NULL, [departmentId] TEXT(16) NOT NULL, "
                        + "[gradeYear] INTEGER NOT NULL, [counselor] TEXT(64), "
                        + "[active] YESNO NOT NULL, CONSTRAINT [uqClassName] UNIQUE ([className]), "
                        + "CONSTRAINT [fkClassDepartment] FOREIGN KEY ([departmentId]) "
                        + "REFERENCES [tblDepartment] ([departmentId]))");
            }
            if (!tableExists(connection, "tblStudent")) {
                execute(connection, "CREATE TABLE [tblStudent] ("
                        + "[studentId] TEXT(20) NOT NULL PRIMARY KEY, [userId] TEXT(32), "
                        + "[fullName] TEXT(64) NOT NULL, [genderName] TEXT(16) NOT NULL, "
                        + "[birthDate] TEXT(10), [departmentId] TEXT(16) NOT NULL, "
                        + "[classId] TEXT(20) NOT NULL, [enrollmentYear] INTEGER NOT NULL, "
                        + "[statusName] TEXT(16) NOT NULL, [phone] TEXT(24), [email] TEXT(128), "
                        + "CONSTRAINT [uqStudentUser] UNIQUE ([userId]), "
                        + "CONSTRAINT [fkStudentUser] FOREIGN KEY ([userId]) "
                        + "REFERENCES [tblUser] ([userId]), "
                        + "CONSTRAINT [fkStudentDepartment] FOREIGN KEY ([departmentId]) "
                        + "REFERENCES [tblDepartment] ([departmentId]), "
                        + "CONSTRAINT [fkStudentClass] FOREIGN KEY ([classId]) "
                        + "REFERENCES [tblSchoolClass] ([classId]))");
            }
            if (!tableExists(connection, "tblTeacher")) {
                execute(connection, "CREATE TABLE [tblTeacher] ("
                        + "[teacherId] TEXT(20) NOT NULL PRIMARY KEY, [userId] TEXT(32), "
                        + "[fullName] TEXT(64) NOT NULL, [departmentId] TEXT(16) NOT NULL, "
                        + "[titleName] TEXT(32), [phone] TEXT(24), [email] TEXT(128), "
                        + "[active] YESNO NOT NULL, CONSTRAINT [uqTeacherUser] UNIQUE ([userId]), "
                        + "CONSTRAINT [fkTeacherUser] FOREIGN KEY ([userId]) "
                        + "REFERENCES [tblUser] ([userId]), "
                        + "CONSTRAINT [fkTeacherDepartment] FOREIGN KEY ([departmentId]) "
                        + "REFERENCES [tblDepartment] ([departmentId]))");
            }
        }
    }

    private void seedDemoData() throws SQLException {
        if (!departmentExists("CS")) {
            saveDepartment(new DepartmentDto("CS", "计算机科学与工程学院",
                    "虚拟校园演示院系", true));
        }
        if (!classExists("CS2026-01")) {
            saveClass(new SchoolClassDto("CS2026-01", "计算机2026级1班",
                    "CS", 2026, "演示辅导员", true));
        }
        if (!exists("tblStudent", "studentId", "20260001")) {
            saveStudent(new StudentDto("20260001", "student", "演示学生", "男",
                    "2008-01-01", "CS", "CS2026-01", 2026, "在读",
                    "13800000001", "student@vcampus.local"));
        }
        if (!exists("tblTeacher", "teacherId", "T0001")) {
            saveTeacher(new TeacherDto("T0001", "teacher", "演示教师", "CS",
                    "讲师", "13800000002", "teacher@vcampus.local", true));
        }
    }

    private int executeStudentSave(Connection connection, String sql, StudentDto student)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableString(statement, 1, student.getUserId());
            statement.setString(2, student.getFullName());
            statement.setString(3, student.getGender());
            setNullableString(statement, 4, student.getBirthDate());
            statement.setString(5, student.getDepartmentId());
            statement.setString(6, student.getClassId());
            statement.setInt(7, student.getEnrollmentYear());
            statement.setString(8, student.getStatus());
            setNullableString(statement, 9, student.getPhone());
            setNullableString(statement, 10, student.getEmail());
            statement.setString(11, student.getStudentId());
            return statement.executeUpdate();
        }
    }

    private int executeTeacherSave(Connection connection, String sql, TeacherDto teacher)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableString(statement, 1, teacher.getUserId());
            statement.setString(2, teacher.getFullName());
            statement.setString(3, teacher.getDepartmentId());
            setNullableString(statement, 4, teacher.getTitle());
            setNullableString(statement, 5, teacher.getPhone());
            setNullableString(statement, 6, teacher.getEmail());
            statement.setBoolean(7, teacher.isActive());
            statement.setString(8, teacher.getTeacherId());
            return statement.executeUpdate();
        }
    }

    private int executeDepartmentSave(Connection connection, String sql,
                                      DepartmentDto department) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, department.getDepartmentName());
            setNullableString(statement, 2, department.getDescription());
            statement.setBoolean(3, department.isActive());
            statement.setString(4, department.getDepartmentId());
            return statement.executeUpdate();
        }
    }

    private int executeClassSave(Connection connection, String sql,
                                 SchoolClassDto schoolClass) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schoolClass.getClassName());
            statement.setString(2, schoolClass.getDepartmentId());
            statement.setInt(3, schoolClass.getGradeYear());
            setNullableString(statement, 4, schoolClass.getCounselor());
            statement.setBoolean(5, schoolClass.isActive());
            statement.setString(6, schoolClass.getClassId());
            return statement.executeUpdate();
        }
    }

    private StudentDto readStudent(ResultSet result) throws SQLException {
        return new StudentDto(result.getString("studentId"), result.getString("userId"),
                result.getString("fullName"), result.getString("genderName"),
                result.getString("birthDate"),
                result.getString("departmentId"), result.getString("classId"),
                result.getInt("enrollmentYear"), result.getString("statusName"),
                result.getString("phone"), result.getString("email"));
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

    private boolean deleteById(String table, String idColumn, String id) throws SQLException {
        String sql = "DELETE FROM [" + table + "] WHERE [" + idColumn + "]=?";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            return statement.executeUpdate() > 0;
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

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void addFilter(StringBuilder sql, List<Object> parameters,
                           String column, String value) {
        if (!isBlank(value)) {
            sql.append(" AND [").append(column).append("] = ?");
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
