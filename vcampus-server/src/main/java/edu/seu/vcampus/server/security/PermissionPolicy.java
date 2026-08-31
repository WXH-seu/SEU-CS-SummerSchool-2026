package edu.seu.vcampus.server.security;

import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.Role;
import edu.seu.vcampus.common.enums.SubSystem;
import edu.seu.vcampus.common.enums.SubSystems;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central role-based permission matrix.
 *
 * <p>Every operation is either <em>public</em> (reachable without a session),
 * restricted to one or more {@link Role}s, or open to any authenticated user.
 * The dispatcher consults this policy before executing any request so that
 * other modules (student, course, library, store) can rely on one consistent
 * permission check instead of implementing their own.
 *
 * <p>Module owners can adjust the matrix through
 * {@link #require(Operation, Role...)}; a call with no roles means "any
 * authenticated user". The sub-system administrator
 * ({@link Role#SUBSYSADMIN}) is scoped: {@link #effectiveRole} turns it into the
 * manager ({@link Role#SUBSYSADMIN}) inside a granted sub-system and into a
 * regular user ({@link Role#TEACHER}) elsewhere, so it can use but never
 * manage un-granted sub-systems. This class is thread-safe.
 */
public final class PermissionPolicy {
    private final Set<Operation> publicOperations =
            Collections.newSetFromMap(new ConcurrentHashMap<Operation, Boolean>());
    private final ConcurrentMap<Operation, Set<Role>> requirements =
            new ConcurrentHashMap<Operation, Set<Role>>();

    /** Creates the policy and installs the default matrix. */
    public PermissionPolicy() {
        installDefaults();
    }

    /**
     * Marks the operation as public: it may be invoked without a session token.
     * Login, registration and health checks belong here.
     */
    public void markPublic(Operation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }
        publicOperations.add(operation);
        requirements.remove(operation);
    }

    /**
     * Restricts the operation to the given roles. Passing no roles removes any
     * role restriction so that every authenticated user may call it.
     */
    public void require(Operation operation, Role... roles) {
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }
        if (roles == null || roles.length == 0) {
            requirements.remove(operation);
        } else {
            EnumSet<Role> allowed = EnumSet.noneOf(Role.class);
            Collections.addAll(allowed, roles);
            requirements.put(operation, Collections.unmodifiableSet(allowed));
        }
    }

    /** Returns {@code true} when the operation needs no session. */
    public boolean isPublic(Operation operation) {
        return publicOperations.contains(operation);
    }

    /**
     * Checks whether a logged-in user with the given role and granted sub-system
     * scopes may run the operation. For a sub-system administrator the
     * <em>effective</em> role is used: granted sub-systems keep the
     * {@link Role#SUBSYSADMIN} (manager) role, while sub-systems outside the
     * scopes are treated as {@link Role#TEACHER} so the administrator still has
     * ordinary usage rights but no management authority there. Operations
     * without an explicit role restriction are allowed for every authenticated
     * role; a {@code null} role never passes a protected operation.
     */
    public boolean allows(Operation operation, Role role, Set<String> adminScopes) {
        if (operation == null) {
            return false;
        }
        if (isPublic(operation)) {
            return true;
        }
        if (role == null) {
            return false;
        }
        Role effective = effectiveRole(role, adminScopes, operation);
        Set<Role> allowed = requirements.get(operation);
        return allowed == null || allowed.contains(effective);
    }

    /**
     * Resolves the role to use when checking an operation. A sub-system
     * administrator is the manager ({@link Role#SUBSYSADMIN}) inside a granted
     * sub-system and an ordinary user ({@link Role#TEACHER}) everywhere else.
     * All other roles are returned unchanged.
     */
    public Role effectiveRole(Role role, Set<String> adminScopes, Operation operation) {
        if (role != Role.SUBSYSADMIN) {
            return role;
        }
        SubSystem subSystem = SubSystems.of(operation);
        return SubSystems.isGranted(adminScopes, subSystem)
                ? Role.SUBSYSADMIN
                : Role.TEACHER;
    }

    /** Convenience overload: checks access with no granted sub-system scopes. */
    public boolean allows(Operation operation, Role role) {
        return allows(operation, role, Collections.<String>emptySet());
    }

    /** Returns the roles allowed for the operation, or {@code null} when unrestricted. */
    public Set<Role> requiredRoles(Operation operation) {
        return requirements.get(operation);
    }

    private void installDefaults() {
        markPublic(Operation.PING);
        markPublic(Operation.USER_LOGIN);

        // Account management is super-admin only. The sub-system admin (SUBSYSADMIN)
        // only operates business sub-systems and cannot register users or
        // change account status.
        require(Operation.USER_REGISTER, Role.SUPER_ADMIN);
        require(Operation.USER_IMPORT_CSV, Role.SUPER_ADMIN);
        require(Operation.USER_LIST_QUERY, Role.SUPER_ADMIN);
        require(Operation.USER_STATUS_UPDATE, Role.SUPER_ADMIN);
        require(Operation.USER_AUDIT_QUERY, Role.SUPER_ADMIN);

        // Any authenticated user may manage his or her own account.
        require(Operation.USER_LOGOUT);
        require(Operation.USER_ACCOUNT_QUERY);
        require(Operation.USER_PROFILE_UPDATE);
        require(Operation.USER_PASSWORD_CHANGE);
        require(Operation.USER_DELETE);

        // Default matrix for the remaining modules. Owners may adjust these
        // entries with require(...) while integrating their features. The
        // matrix is expressed in the three roles a sub-system management
        // operation actually needs: management/write operations are restricted
        // to the sub-system and super administrators, while usage operations
        // are open to ordinary users (student/teacher) as well as both
        // administrators. A sub-system administrator (SUBSYSADMIN) outside its
        // granted sub-systems is normalized to TEACHER by effectiveRole(...),
        // so it can use but never manage an un-granted sub-system.
        require(Operation.STUDENT_QUERY, Role.TEACHER, Role.SUBSYSADMIN, Role.SUPER_ADMIN);
        require(Operation.STUDENT_SAVE, Role.SUBSYSADMIN, Role.SUPER_ADMIN);
        require(Operation.COURSE_QUERY);
        require(Operation.COURSE_SELECT, Role.STUDENT, Role.SUPER_ADMIN);
        require(Operation.COURSE_DROP, Role.STUDENT, Role.SUPER_ADMIN);
        require(Operation.LIBRARY_BOOK_QUERY);
        require(Operation.LIBRARY_BOOK_SAVE, Role.SUBSYSADMIN, Role.SUPER_ADMIN);
        require(Operation.LIBRARY_BOOK_DELETE, Role.SUBSYSADMIN, Role.SUPER_ADMIN);
        require(Operation.LIBRARY_BORROW, Role.STUDENT, Role.TEACHER, Role.SUBSYSADMIN,
                Role.SUPER_ADMIN);
        require(Operation.LIBRARY_RETURN, Role.STUDENT, Role.TEACHER, Role.SUBSYSADMIN,
                Role.SUPER_ADMIN);
        require(Operation.STORE_PRODUCT_QUERY);
        require(Operation.STORE_ORDER_CREATE, Role.STUDENT, Role.TEACHER, Role.SUBSYSADMIN,
                Role.SUPER_ADMIN);
    }
}
