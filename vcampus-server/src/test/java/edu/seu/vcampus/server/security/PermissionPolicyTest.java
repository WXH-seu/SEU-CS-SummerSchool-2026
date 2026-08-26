package edu.seu.vcampus.server.security;

import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.Role;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the default permission matrix and the extension API. */
public class PermissionPolicyTest {
    private final PermissionPolicy policy = new PermissionPolicy();

    @Test
    public void publicOperationsNeedNoSession() {
        assertTrue(policy.isPublic(Operation.PING));
        assertTrue(policy.isPublic(Operation.USER_LOGIN));
        assertFalse(policy.isPublic(Operation.USER_REGISTER));
        assertFalse(policy.isPublic(Operation.USER_LOGOUT));
    }

    @Test
    public void ownAccountOperationsAreOpenToEveryAuthenticatedRole() {
        for (Role role : Role.values()) {
            assertTrue(policy.allows(Operation.USER_ACCOUNT_QUERY, role));
            assertTrue(policy.allows(Operation.USER_PROFILE_UPDATE, role));
            assertTrue(policy.allows(Operation.USER_PASSWORD_CHANGE, role));
            assertTrue(policy.allows(Operation.USER_DELETE, role));
        }
    }

    @Test
    public void userAdministrationIsAdminOrSuperAdmin() {
        assertTrue(policy.allows(Operation.USER_LIST_QUERY, Role.ADMIN));
        assertTrue(policy.allows(Operation.USER_LIST_QUERY, Role.SUPER_ADMIN));
        assertTrue(policy.allows(Operation.USER_STATUS_UPDATE, Role.ADMIN));
        assertTrue(policy.allows(Operation.USER_STATUS_UPDATE, Role.SUPER_ADMIN));
        assertTrue(policy.allows(Operation.USER_REGISTER, Role.ADMIN));
        assertTrue(policy.allows(Operation.USER_REGISTER, Role.SUPER_ADMIN));
        assertTrue(policy.allows(Operation.USER_IMPORT_CSV, Role.ADMIN));
        assertTrue(policy.allows(Operation.USER_IMPORT_CSV, Role.SUPER_ADMIN));
        assertFalse(policy.allows(Operation.USER_LIST_QUERY, Role.STUDENT));
        assertFalse(policy.allows(Operation.USER_STATUS_UPDATE, Role.TEACHER));
        assertFalse(policy.allows(Operation.USER_REGISTER, Role.STUDENT));
        assertFalse(policy.allows(Operation.USER_IMPORT_CSV, Role.TEACHER));
    }

    @Test
    public void studentModuleDefaultsMatchTheDocumentedMatrix() {
        assertTrue(policy.allows(Operation.STUDENT_QUERY, Role.TEACHER));
        assertTrue(policy.allows(Operation.STUDENT_QUERY, Role.ADMIN));
        assertFalse(policy.allows(Operation.STUDENT_QUERY, Role.STUDENT));

        assertTrue(policy.allows(Operation.COURSE_SELECT, Role.STUDENT));
        assertFalse(policy.allows(Operation.COURSE_SELECT, Role.TEACHER));
        assertFalse(policy.allows(Operation.COURSE_SELECT, Role.ADMIN));

        assertTrue(policy.allows(Operation.LIBRARY_BORROW, Role.STUDENT));
        assertTrue(policy.allows(Operation.LIBRARY_BORROW, Role.TEACHER));

        assertTrue(policy.allows(Operation.STORE_PRODUCT_QUERY, Role.STUDENT));
        assertTrue(policy.allows(Operation.STORE_PRODUCT_QUERY, Role.ADMIN));
        assertTrue(policy.allows(Operation.STORE_PRODUCT_QUERY, Role.SUPER_ADMIN));

        // The super administrator is the global administrator with widest access.
        assertTrue(policy.allows(Operation.STUDENT_QUERY, Role.SUPER_ADMIN));
        assertTrue(policy.allows(Operation.COURSE_SELECT, Role.SUPER_ADMIN));
        assertTrue(policy.allows(Operation.LIBRARY_BORROW, Role.SUPER_ADMIN));
        assertTrue(policy.allows(Operation.STORE_ORDER_CREATE, Role.SUPER_ADMIN));
    }

    @Test
    public void unrestrictedOperationsAllowEveryRole() {
        assertTrue(policy.allows(Operation.COURSE_QUERY, Role.STUDENT));
        assertTrue(policy.allows(Operation.COURSE_QUERY, Role.TEACHER));
        assertTrue(policy.allows(Operation.COURSE_QUERY, Role.ADMIN));
        assertFalse(policy.allows(Operation.COURSE_QUERY, null));
    }

    @Test
    public void moduleOwnersCanAdjustTheMatrix() {
        assertFalse(policy.allows(Operation.LIBRARY_RETURN, Role.ADMIN));
        policy.require(Operation.LIBRARY_RETURN, Role.ADMIN);
        assertTrue(policy.allows(Operation.LIBRARY_RETURN, Role.ADMIN));
        assertFalse(policy.allows(Operation.LIBRARY_RETURN, Role.STUDENT));

        policy.require(Operation.COURSE_DROP);
        assertTrue(policy.allows(Operation.COURSE_DROP, Role.ADMIN));
    }

    @Test
    public void nullRoleNeverPassesRestrictedChecks() {
        assertFalse(policy.allows(Operation.USER_LIST_QUERY, null));
        assertFalse(policy.allows(Operation.USER_DELETE, null));
        assertFalse(policy.allows(null, Role.ADMIN));
    }
}
