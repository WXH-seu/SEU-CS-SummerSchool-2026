package edu.seu.vcampus.server.database;

import edu.seu.vcampus.server.dao.AccessAcademicRepository;
import edu.seu.vcampus.server.dao.AccessBookRepository;
import edu.seu.vcampus.server.dao.AccessCourseRepository;
import edu.seu.vcampus.server.dao.AccessCurriculumCatalogRepository;
import edu.seu.vcampus.server.dao.AccessOperationLogRepository;
import edu.seu.vcampus.server.dao.AccessStoreRepository;
import edu.seu.vcampus.server.dao.AccessUserRepository;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Boots every module against one temporary Access file and checks core relations. */
public class FullDatabaseBootstrapTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void initializesEveryModuleAndRestartsIdempotently() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "full-vcampus.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());

        initializeAll(database);
        assertEquals(34, count(database, "tblDepartment"));
        assertEquals(9, count(database, "tblMajor"));
        assertEquals(66, count(database, "tblCatalogCourse"));
        assertTrue(count(database, "tblUser") >= 4);
        assertTrue(count(database, "tblProduct") >= 6);
        assertTrue(hasForeignKey(database, "tblStudent", "userId", "tblUser"));
        assertTrue(hasForeignKey(database, "tblCourseEnrollment", "studentId", "tblStudent"));
        assertTrue(hasForeignKey(database, "tblBorrowRecord", "userId", "tblUser"));
        assertTrue(hasForeignKey(database, "tblCartItem", "userId", "tblUser"));
        assertTrue(hasForeignKey(database, "tblOrder", "userId", "tblUser"));
        assertTrue(hasForeignKey(database, "tblMajorCourse", "courseId", "tblCatalogCourse"));

        initializeAll(database);
        assertEquals(34, count(database, "tblDepartment"));
        assertEquals(9, count(database, "tblMajor"));
        assertEquals(66, count(database, "tblCatalogCourse"));
    }

    private void initializeAll(AccessDatabase database) throws Exception {
        new AccessUserRepository(database, new PasswordHasher());
        new AccessOperationLogRepository(database.getDatabaseFile().getAbsolutePath());
        new AccessAcademicRepository(database);
        new AccessCurriculumCatalogRepository(database);
        new AccessCourseRepository(database);
        new AccessBookRepository(database);
        new AccessStoreRepository(database);
    }

    private int count(AccessDatabase database, String table) throws Exception {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM [" + table + "]")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private boolean hasForeignKey(AccessDatabase database, String table, String column,
                                  String parentTable) throws Exception {
        try (Connection connection = database.openConnection();
             ResultSet keys = connection.getMetaData().getImportedKeys(null, null, table)) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && parentTable.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
