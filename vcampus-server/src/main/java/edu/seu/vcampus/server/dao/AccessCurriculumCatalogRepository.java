package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.server.database.AccessDatabase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Access-backed catalog with an idempotent, reviewed SEU public-data import. */
public final class AccessCurriculumCatalogRepository implements CurriculumCatalogRepository {
    private static final String RESOURCE_ROOT = "/seed/seu/";

    private final AccessDatabase database;
    private CatalogImportSummary importSummary;

    public AccessCurriculumCatalogRepository(AccessDatabase database) throws SQLException {
        this.database = database;
        initializeSchema();
        importSummary = importBundledSnapshot();
    }

    public CatalogImportSummary getImportSummary() {
        return importSummary;
    }

    @Override
    public CatalogImportSummary importBundledSnapshot() throws SQLException {
        List<Map<String, String>> sources = readCsv("sources.csv");
        List<Map<String, String>> departments = readCsv("departments.csv");
        List<Map<String, String>> majors = readCsv("majors.csv");
        List<Map<String, String>> courses = readCsv("courses.csv");
        List<Map<String, String>> majorCourses = readCsv("major_courses.csv");
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                importSources(connection, sources);
                importDepartments(connection, departments);
                importMajors(connection, majors);
                importCourses(connection, courses);
                importMajorCourses(connection, majorCourses);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        importSummary = new CatalogImportSummary(departments.size(), majors.size(),
                courses.size(), majorCourses.size());
        return importSummary;
    }

    @Override
    public List<Major> findMajors(String departmentId) throws SQLException {
        String sql = "SELECT * FROM [tblMajor]"
                + (isBlank(departmentId) ? "" : " WHERE [departmentId]=?")
                + " ORDER BY [majorId]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!isBlank(departmentId)) {
                statement.setString(1, departmentId.trim());
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Major> majors = new ArrayList<Major>();
                while (result.next()) {
                    majors.add(new Major(result.getString("majorId"),
                            result.getString("departmentId"), result.getString("majorName"),
                            result.getString("degreeType"), result.getInt("durationYears"),
                            result.getInt("sourceYear"), result.getString("sourceUrl"),
                            result.getBoolean("active")));
                }
                return majors;
            }
        }
    }

    @Override
    public List<CatalogCourse> findCoursesForMajor(String majorId) throws SQLException {
        String sql = "SELECT c.*, mc.[recommendedYear], mc.[recommendedSemester], "
                + "mc.[required] FROM [tblCatalogCourse] c INNER JOIN [tblMajorCourse] mc "
                + "ON c.[courseId]=mc.[courseId] WHERE mc.[majorId]=? "
                + "ORDER BY mc.[recommendedYear], mc.[recommendedSemester], c.[courseId]";
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, required(majorId, "majorId"));
            try (ResultSet result = statement.executeQuery()) {
                List<CatalogCourse> courses = new ArrayList<CatalogCourse>();
                while (result.next()) {
                    courses.add(new CatalogCourse(result.getString("courseId"),
                            result.getString("departmentId"), result.getString("courseName"),
                            result.getDouble("credits"), result.getInt("lectureHours"),
                            result.getInt("practiceHours"), result.getString("courseType"),
                            result.getInt("recommendedYear"),
                            result.getInt("recommendedSemester"), result.getBoolean("required"),
                            result.getInt("sourceYear"), result.getString("sourceUrl"),
                            result.getBoolean("active")));
                }
                return courses;
            }
        }
    }

    private void initializeSchema() throws SQLException {
        try (Connection connection = database.openConnection()) {
            if (!tableExists(connection, "tblDepartment")) {
                throw new SQLException("tblDepartment must be initialized before curriculum catalog");
            }
            ensureDepartmentColumn(connection, "homepageUrl", "TEXT(255)");
            ensureDepartmentColumn(connection, "sourceUrl", "TEXT(255)");
            ensureDepartmentColumn(connection, "collectedAt", "TEXT(10)");
            if (!tableExists(connection, "tblCatalogSource")) {
                execute(connection, "CREATE TABLE [tblCatalogSource] ("
                        + "[sourceId] TEXT(20) NOT NULL PRIMARY KEY, "
                        + "[sourceType] TEXT(16) NOT NULL, [title] TEXT(128) NOT NULL, "
                        + "[sourceYear] INTEGER NOT NULL, [sourceUrl] TEXT(255) NOT NULL, "
                        + "[collectedAt] TEXT(10) NOT NULL)");
            }
            if (!tableExists(connection, "tblMajor")) {
                execute(connection, "CREATE TABLE [tblMajor] ("
                        + "[majorId] TEXT(16) NOT NULL PRIMARY KEY, "
                        + "[departmentId] TEXT(16) NOT NULL, [majorName] TEXT(64) NOT NULL, "
                        + "[degreeType] TEXT(16) NOT NULL, [durationYears] INTEGER NOT NULL, "
                        + "[sourceYear] INTEGER NOT NULL, [sourceId] TEXT(20) NOT NULL, "
                        + "[sourceUrl] TEXT(255) NOT NULL, [active] YESNO NOT NULL, "
                        + "CONSTRAINT [uqMajorDepartmentName] UNIQUE ([departmentId], [majorName]), "
                        + "CONSTRAINT [fkMajorDepartment] FOREIGN KEY ([departmentId]) "
                        + "REFERENCES [tblDepartment] ([departmentId]), "
                        + "CONSTRAINT [fkMajorSource] FOREIGN KEY ([sourceId]) "
                        + "REFERENCES [tblCatalogSource] ([sourceId]))");
            }
            if (!tableExists(connection, "tblCatalogCourse")) {
                execute(connection, "CREATE TABLE [tblCatalogCourse] ("
                        + "[courseId] TEXT(20) NOT NULL PRIMARY KEY, "
                        + "[departmentId] TEXT(16) NOT NULL, [courseName] TEXT(128) NOT NULL, "
                        + "[credits] DOUBLE NOT NULL, [lectureHours] INTEGER NOT NULL, "
                        + "[practiceHours] INTEGER NOT NULL, [courseType] TEXT(32) NOT NULL, "
                        + "[sourceYear] INTEGER NOT NULL, [sourceId] TEXT(20) NOT NULL, "
                        + "[sourceUrl] TEXT(255) NOT NULL, [active] YESNO NOT NULL, "
                        + "CONSTRAINT [fkCatalogCourseDepartment] FOREIGN KEY ([departmentId]) "
                        + "REFERENCES [tblDepartment] ([departmentId]), "
                        + "CONSTRAINT [fkCatalogCourseSource] FOREIGN KEY ([sourceId]) "
                        + "REFERENCES [tblCatalogSource] ([sourceId]))");
            }
            if (!tableExists(connection, "tblMajorCourse")) {
                execute(connection, "CREATE TABLE [tblMajorCourse] ("
                        + "[majorId] TEXT(16) NOT NULL, [courseId] TEXT(20) NOT NULL, "
                        + "[recommendedYear] INTEGER NOT NULL, "
                        + "[recommendedSemester] INTEGER NOT NULL, [required] YESNO NOT NULL, "
                        + "CONSTRAINT [pkMajorCourse] PRIMARY KEY ([majorId], [courseId]), "
                        + "CONSTRAINT [fkMajorCourseMajor] FOREIGN KEY ([majorId]) "
                        + "REFERENCES [tblMajor] ([majorId]), "
                        + "CONSTRAINT [fkMajorCourseCourse] FOREIGN KEY ([courseId]) "
                        + "REFERENCES [tblCatalogCourse] ([courseId]))");
            }
        }
    }

    private void ensureDepartmentColumn(Connection connection, String name, String type)
            throws SQLException {
        if (!columnExists(connection, "tblDepartment", name)) {
            execute(connection, "ALTER TABLE [tblDepartment] ADD COLUMN [" + name + "] " + type);
        }
    }

    private void importSources(Connection connection, List<Map<String, String>> rows)
            throws SQLException {
        String update = "UPDATE [tblCatalogSource] SET [sourceType]=?, [title]=?, "
                + "[sourceYear]=?, [sourceUrl]=?, [collectedAt]=? WHERE [sourceId]=?";
        String insert = "INSERT INTO [tblCatalogSource] ([sourceType], [title], [sourceYear], "
                + "[sourceUrl], [collectedAt], [sourceId]) VALUES (?, ?, ?, ?, ?, ?)";
        for (Map<String, String> row : rows) {
            String id = value(row, "sourceId");
            Object[] values = new Object[]{value(row, "sourceType"), value(row, "title"),
                    integer(row, "sourceYear"), value(row, "sourceUrl"),
                    value(row, "collectedAt"), id};
            upsert(connection, update, insert, values);
        }
    }

    private void importDepartments(Connection connection, List<Map<String, String>> rows)
            throws SQLException {
        String update = "UPDATE [tblDepartment] SET [departmentName]=?, [description]=?, "
                + "[active]=?, [homepageUrl]=?, [sourceUrl]=?, [collectedAt]=? "
                + "WHERE [departmentId]=?";
        String insert = "INSERT INTO [tblDepartment] ([departmentName], [description], [active], "
                + "[homepageUrl], [sourceUrl], [collectedAt], [departmentId]) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        for (Map<String, String> row : rows) {
            String name = value(row, "departmentName");
            Object[] values = new Object[]{name, "东南大学官网公开院系", bool(row, "active"),
                    value(row, "homepageUrl"), value(row, "sourceUrl"),
                    value(row, "collectedAt"), value(row, "departmentId")};
            upsert(connection, update, insert, values);
        }
    }

    private void importMajors(Connection connection, List<Map<String, String>> rows)
            throws SQLException {
        String update = "UPDATE [tblMajor] SET [departmentId]=?, [majorName]=?, [degreeType]=?, "
                + "[durationYears]=?, [sourceYear]=?, [sourceId]=?, [sourceUrl]=?, [active]=? "
                + "WHERE [majorId]=?";
        String insert = "INSERT INTO [tblMajor] ([departmentId], [majorName], [degreeType], "
                + "[durationYears], [sourceYear], [sourceId], [sourceUrl], [active], [majorId]) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        for (Map<String, String> row : rows) {
            Object[] values = new Object[]{value(row, "departmentId"), value(row, "majorName"),
                    value(row, "degreeType"), integer(row, "durationYears"),
                    integer(row, "sourceYear"), value(row, "sourceId"),
                    value(row, "sourceUrl"), bool(row, "active"), value(row, "majorId")};
            upsert(connection, update, insert, values);
        }
    }

    private void importCourses(Connection connection, List<Map<String, String>> rows)
            throws SQLException {
        String update = "UPDATE [tblCatalogCourse] SET [departmentId]=?, [courseName]=?, [credits]=?, "
                + "[lectureHours]=?, [practiceHours]=?, [courseType]=?, [sourceYear]=?, "
                + "[sourceId]=?, [sourceUrl]=?, [active]=? WHERE [courseId]=?";
        String insert = "INSERT INTO [tblCatalogCourse] ([departmentId], [courseName], [credits], "
                + "[lectureHours], [practiceHours], [courseType], [sourceYear], [sourceId], "
                + "[sourceUrl], [active], [courseId]) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        for (Map<String, String> row : rows) {
            Object[] values = new Object[]{value(row, "departmentId"), value(row, "courseName"),
                    decimal(row, "credits"), integer(row, "lectureHours"),
                    integer(row, "practiceHours"), value(row, "courseType"),
                    integer(row, "sourceYear"), value(row, "sourceId"),
                    value(row, "sourceUrl"), bool(row, "active"), value(row, "courseId")};
            upsert(connection, update, insert, values);
        }
    }

    private void importMajorCourses(Connection connection, List<Map<String, String>> rows)
            throws SQLException {
        String update = "UPDATE [tblMajorCourse] SET [recommendedYear]=?, "
                + "[recommendedSemester]=?, [required]=? WHERE [majorId]=? AND [courseId]=?";
        String insert = "INSERT INTO [tblMajorCourse] ([recommendedYear], "
                + "[recommendedSemester], [required], [majorId], [courseId]) "
                + "VALUES (?, ?, ?, ?, ?)";
        for (Map<String, String> row : rows) {
            Object[] values = new Object[]{integer(row, "recommendedYear"),
                    integer(row, "recommendedSemester"), bool(row, "required"),
                    value(row, "majorId"), value(row, "courseId")};
            upsert(connection, update, insert, values);
        }
    }

    private void upsert(Connection connection, String updateSql, String insertSql,
                        Object[] values) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(updateSql)) {
            bind(update, values);
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            bind(insert, values);
            insert.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Integer) {
                statement.setInt(i + 1, ((Integer) value).intValue());
            } else if (value instanceof Double) {
                statement.setDouble(i + 1, ((Double) value).doubleValue());
            } else if (value instanceof Boolean) {
                statement.setBoolean(i + 1, ((Boolean) value).booleanValue());
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private List<Map<String, String>> readCsv(String fileName) throws SQLException {
        InputStream stream = AccessCurriculumCatalogRepository.class.getResourceAsStream(
                RESOURCE_ROOT + fileName);
        if (stream == null) {
            throw new SQLException("Missing catalog resource: " + fileName);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new SQLException("Empty catalog resource: " + fileName);
            }
            List<String> headers = parseCsvLine(removeBom(headerLine));
            List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                if (values.size() != headers.size()) {
                    throw new SQLException("Invalid CSV column count in " + fileName
                            + " line " + lineNumber);
                }
                Map<String, String> row = new LinkedHashMap<String, String>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), values.get(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new SQLException("Cannot read catalog resource: " + fileName, e);
        }
    }

    private List<String> parseCsvLine(String line) throws SQLException {
        List<String> values = new ArrayList<String>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(ch);
            }
        }
        if (quoted) {
            throw new SQLException("Unclosed quote in catalog CSV");
        }
        values.add(value.toString());
        return values;
    }

    private String value(Map<String, String> row, String column) throws SQLException {
        if (!row.containsKey(column)) {
            throw new SQLException("Missing catalog CSV column: " + column);
        }
        return row.get(column).trim();
    }

    private int integer(Map<String, String> row, String column) throws SQLException {
        try {
            return Integer.parseInt(value(row, column));
        } catch (NumberFormatException e) {
            throw new SQLException("Invalid integer in catalog column: " + column, e);
        }
    }

    private double decimal(Map<String, String> row, String column) throws SQLException {
        try {
            return Double.parseDouble(value(row, column));
        } catch (NumberFormatException e) {
            throw new SQLException("Invalid decimal in catalog column: " + column, e);
        }
    }

    private boolean bool(Map<String, String> row, String column) throws SQLException {
        String text = value(row, column);
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new SQLException("Invalid boolean in catalog column: " + column);
    }

    private String required(String value, String field) throws SQLException {
        if (isBlank(value)) {
            throw new SQLException(field + " must not be blank");
        }
        return value.trim();
    }

    private String removeBom(String text) {
        return text.length() > 0 && text.charAt(0) == '\ufeff' ? text.substring(1) : text;
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
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, tableName, null)) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
