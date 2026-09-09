package edu.seu.vcampus.server.dao;

import edu.seu.vcampus.server.database.AccessDatabase;
import edu.seu.vcampus.server.security.PasswordHasher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertTrue;

/** Verifies schema creation and non-destructive upgrades for the Access store tables. */
public class AccessStoreRepositoryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void addsUserForeignKeysToLegacyStoreTables() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "legacy-store.accdb");
        AccessDatabase database = new AccessDatabase(file.getAbsolutePath());
        new AccessUserRepository(database, new PasswordHasher());
        createLegacyTables(database);

        new AccessStoreRepository(database);

        assertTrue(hasUserForeignKey(database, "tblCartItem"));
        assertTrue(hasUserForeignKey(database, "tblOrder"));
        // A second startup must remain idempotent.
        new AccessStoreRepository(database);
    }

    private void createLegacyTables(AccessDatabase database) throws Exception {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE [tblProduct] ("
                    + "[productId] TEXT(20) NOT NULL PRIMARY KEY, "
                    + "[productName] TEXT(64) NOT NULL, [category] TEXT(32), "
                    + "[description] TEXT(255), [price] CURRENCY NOT NULL, "
                    + "[stock] INTEGER NOT NULL, [active] YESNO NOT NULL)");
            statement.execute("CREATE TABLE [tblCartItem] ("
                    + "[cartItemId] TEXT(40) NOT NULL PRIMARY KEY, "
                    + "[userId] TEXT(32) NOT NULL, [productId] TEXT(20) NOT NULL, "
                    + "[quantity] INTEGER NOT NULL)");
            statement.execute("CREATE TABLE [tblOrder] ("
                    + "[orderId] TEXT(40) NOT NULL PRIMARY KEY, "
                    + "[userId] TEXT(32) NOT NULL, [totalAmount] CURRENCY NOT NULL, "
                    + "[statusName] TEXT(16) NOT NULL, [orderTime] TEXT(19) NOT NULL)");
        }
    }

    private boolean hasUserForeignKey(AccessDatabase database, String table)
            throws Exception {
        try (Connection connection = database.openConnection();
             ResultSet keys = connection.getMetaData().getImportedKeys(null, null, table)) {
            while (keys.next()) {
                if ("userId".equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && "tblUser".equalsIgnoreCase(keys.getString("PKTABLE_NAME"))
                        && "userId".equalsIgnoreCase(keys.getString("PKCOLUMN_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
