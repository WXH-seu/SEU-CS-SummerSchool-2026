package edu.seu.vcampus.common.enums;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Helpers for working with {@link SubSystem} scopes: key lookup, validation and
 * the operation-to-sub-system mapping used by the server permission policy.
 */
public final class SubSystems {
    private SubSystems() {
    }

    /** Returns every sub-system key, as an unmodifiable set. */
    public static Set<String> keys() {
        Set<String> keys = new HashSet<String>();
        for (SubSystem subSystem : SubSystem.values()) {
            keys.add(subSystem.getKey());
        }
        return Collections.unmodifiableSet(keys);
    }

    /** Resolves a sub-system by its key, or {@code null} when unknown. */
    public static SubSystem byKey(String key) {
        if (key == null) {
            return null;
        }
        for (SubSystem subSystem : SubSystem.values()) {
            if (subSystem.getKey().equals(key)) {
                return subSystem;
            }
        }
        return null;
    }

    public static boolean isValidKey(String key) {
        return byKey(key) != null;
    }

    /**
     * Computes the three-tier role to expose to a sub-system for the current
     * session. This is the transparency boundary that hides the four role
     * values and the scoped sub-system grants from sub-system modules.
     */
    public static SubSystemRole effectiveRole(Role role, Set<String> adminScopes,
                                              SubSystem subSystem) {
        if (role == Role.SUPER_ADMIN) {
            return SubSystemRole.ADMIN;
        }
        if (role == Role.SUBSYSADMIN) {
            return isGranted(adminScopes, subSystem)
                    ? SubSystemRole.ADMIN
                    : SubSystemRole.TEACHER;
        }
        if (role == Role.TEACHER) {
            return SubSystemRole.TEACHER;
        }
        return SubSystemRole.STUDENT;
    }

    /** Whether a sub-system scope is present in the granted scopes. */
    public static boolean isGranted(Set<String> adminScopes, SubSystem subSystem) {
        return subSystem != null && adminScopes != null
                && adminScopes.contains(subSystem.getKey());
    }

    /**
     * Returns the sub-system that an operation belongs to, or {@code null} for
     * operations that are not tied to any business sub-system (public, account
     * self-service and account-management operations).
     */
    public static SubSystem of(Operation operation) {
        if (operation == null) {
            return null;
        }
        switch (operation) {
            case STUDENT_QUERY:
            case STUDENT_SAVE:
                return SubSystem.STUDENT;
            case COURSE_QUERY:
            case COURSE_SELECT:
            case COURSE_DROP:
                return SubSystem.COURSE;
            case LIBRARY_BOOK_QUERY:
            case LIBRARY_BORROW:
            case LIBRARY_RETURN:
                return SubSystem.LIBRARY;
            case STORE_PRODUCT_QUERY:
            case STORE_PRODUCT_SAVE:
            case STORE_PRODUCT_DELETE:
            case STORE_CART_QUERY:
            case STORE_CART_UPDATE:
            case STORE_ORDER_CREATE:
            case STORE_ORDER_QUERY:
            case STORE_ORDER_STATUS:
                return SubSystem.STORE;
            default:
                return null;
        }
    }
}
