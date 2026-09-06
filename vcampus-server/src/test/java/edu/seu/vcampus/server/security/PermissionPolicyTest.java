package edu.seu.vcampus.server.security;

import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystemRole;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the default permission matrix and the extension API. */
public class PermissionPolicyTest {
    private final PermissionPolicy policy = new PermissionPolicy();

    private static Set<String> scopes(String... keys) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(keys)));
    }

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
    public void accountManagementIsSuperAdminOnly() {
        assertTrue(policy.allows(Operation.USER_REGISTER, Role.SUPER_ADMIN));
        assertFalse(policy.allows(Operation.USER_REGISTER, Role.SUBSYSADMIN));
        assertFalse(policy.allows(Operation.USER_REGISTER, Role.STUDENT));

        assertTrue(policy.allows(Operation.USER_IMPORT_CSV, Role.SUPER_ADMIN));
        assertFalse(policy.allows(Operation.USER_IMPORT_CSV, Role.SUBSYSADMIN));

        assertTrue(policy.allows(Operation.USER_LIST_QUERY, Role.SUPER_ADMIN));
        assertFalse(policy.allows(Operation.USER_LIST_QUERY, Role.SUBSYSADMIN));

        assertTrue(policy.allows(Operation.USER_STATUS_UPDATE, Role.SUPER_ADMIN));
        assertFalse(policy.allows(Operation.USER_STATUS_UPDATE, Role.SUBSYSADMIN));

        assertTrue(policy.allows(Operation.USER_AUDIT_QUERY, Role.SUPER_ADMIN));
        assertFalse(policy.allows(Operation.USER_AUDIT_QUERY, Role.SUBSYSADMIN));
        assertFalse(policy.allows(Operation.USER_AUDIT_QUERY, Role.TEACHER));
    }

    @Test
    public void studentModuleDefaultsMatchTheDocumentedMatrix() {
        assertTrue(policy.allows(Operation.STUDENT_QUERY, Role.TEACHER));
        assertTrue(policy.allows(Operation.STUDENT_QUERY, Role.SUBSYSADMIN, scopes("student")));
        assertTrue(policy.allows(Operation.STUDENT_QUERY, Role.STUDENT));

        assertTrue(policy.allows(Operation.COURSE_SELECT, Role.STUDENT));
        assertFalse(policy.allows(Operation.COURSE_SELECT, Role.TEACHER));
        assertFalse(policy.allows(Operation.COURSE_SELECT, Role.SUBSYSADMIN));

        assertTrue(policy.allows(Operation.LIBRARY_BORROW, Role.STUDENT));
        assertTrue(policy.allows(Operation.LIBRARY_BORROW, Role.TEACHER));
        assertFalse(policy.allows(Operation.LIBRARY_BORROW, Role.SUPER_ADMIN));
        assertTrue(policy.allows(Operation.LIBRARY_BORROW_QUERY, Role.STUDENT));
        assertTrue(policy.allows(Operation.LIBRARY_BORROW_QUERY, Role.TEACHER));
        assertTrue(policy.allows(Operation.LIBRARY_BORROW_QUERY, Role.SUPER_ADMIN));

        assertTrue(policy.allows(Operation.STORE_PRODUCT_QUERY, Role.STUDENT));
        assertTrue(policy.allows(Operation.STORE_PRODUCT_QUERY, Role.SUBSYSADMIN, scopes("store")));
        assertTrue(policy.allows(Operation.STORE_PRODUCT_QUERY, Role.SUPER_ADMIN));

        // The super administrator is the global administrator with widest access.
        assertTrue(policy.allows(Operation.STUDENT_QUERY, Role.SUPER_ADMIN));
        assertTrue(policy.allows(Operation.COURSE_SELECT, Role.SUPER_ADMIN));
        assertFalse(policy.allows(Operation.LIBRARY_BORROW, Role.SUPER_ADMIN));
        assertTrue(policy.allows(Operation.STORE_ORDER_CREATE, Role.SUPER_ADMIN));
    }

    @Test
    public void restrictionsOpenToAuthenticatedRoles() {
        assertTrue(policy.allows(Operation.COURSE_QUERY, Role.STUDENT));
        assertTrue(policy.allows(Operation.COURSE_QUERY, Role.TEACHER));
        assertTrue(policy.allows(Operation.COURSE_QUERY, Role.SUBSYSADMIN, scopes("course")));
        assertTrue(policy.allows(Operation.COURSE_QUERY, Role.SUPER_ADMIN));
        assertFalse(policy.allows(Operation.COURSE_QUERY, null));
    }

    @Test
    public void subsystemAdminManagesGrantedUnGrantsOthers() {
        // Inside a granted sub-system the administrator is the manager.
        assertTrue(policy.allows(Operation.STUDENT_SAVE, Role.SUBSYSADMIN, scopes("student")));
        assertFalse(policy.allows(Operation.LIBRARY_RETURN, Role.SUBSYSADMIN, scopes("library")));
        assertFalse(policy.allows(Operation.LIBRARY_BORROW, Role.SUBSYSADMIN, scopes("library")));

        // Outside a granted sub-system it keeps ordinary teacher usage rights...
        assertTrue(policy.allows(Operation.LIBRARY_BORROW, Role.SUBSYSADMIN, scopes("student")));
        assertTrue(policy.allows(Operation.COURSE_QUERY, Role.SUBSYSADMIN, scopes("student")));

        // ...but cannot manage a sub-system it was not granted.
        assertFalse(policy.allows(Operation.STUDENT_SAVE, Role.SUBSYSADMIN, scopes("library")));
        assertFalse(policy.allows(Operation.STUDENT_SAVE, Role.SUBSYSADMIN));

        assertTrue(policy.allows(Operation.LIBRARY_BOOK_SAVE, Role.SUBSYSADMIN, scopes("library")));
        assertTrue(policy.allows(Operation.LIBRARY_BOOK_DELETE, Role.SUPER_ADMIN));
        assertFalse(policy.allows(Operation.LIBRARY_BOOK_SAVE, Role.STUDENT));
        assertFalse(policy.allows(Operation.LIBRARY_BOOK_SAVE, Role.TEACHER));
        assertFalse(policy.allows(Operation.LIBRARY_BOOK_SAVE, Role.SUBSYSADMIN, scopes("student")));
        assertFalse(policy.allows(Operation.LIBRARY_BOOK_DELETE, Role.SUBSYSADMIN, scopes("course")));
    }

    @Test
    public void moduleOwnersCanAdjustTheMatrix() {
        // SAVE is a management (admin-only) operation by default.
        assertFalse(policy.allows(Operation.STUDENT_SAVE, Role.SUBSYSADMIN));
        policy.requireSubSystem(Operation.STUDENT_SAVE, SubSystemRole.ADMIN);
        assertTrue(policy.allows(Operation.STUDENT_SAVE, Role.SUBSYSADMIN, scopes("student")));
        assertFalse(policy.allows(Operation.STUDENT_SAVE, Role.STUDENT));

        policy.requireSubSystem(Operation.COURSE_DROP);
        assertTrue(policy.allows(Operation.COURSE_DROP, Role.SUBSYSADMIN, scopes("course")));
    }

    @Test
    public void nullRoleNeverPassesRestrictedChecks() {
        assertFalse(policy.allows(Operation.USER_LIST_QUERY, null));
        assertFalse(policy.allows(Operation.USER_DELETE, null));
        assertFalse(policy.allows(null, Role.SUBSYSADMIN));
    }
}
