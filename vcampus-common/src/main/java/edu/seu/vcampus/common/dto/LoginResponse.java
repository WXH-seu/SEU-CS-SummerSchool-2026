package edu.seu.vcampus.common.dto;

import edu.seu.vcampus.common.enums.Role;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/** Session information returned after successful authentication. */
public final class LoginResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sessionToken;
    private final String userId;
    private final String displayName;
    private final Role role;

    /** Sub-system keys granted to a sub-system administrator; empty otherwise. */
    private final Set<String> adminScopes;

    public LoginResponse(String sessionToken, String userId, String displayName, Role role) {
        this(sessionToken, userId, displayName, role, Collections.<String>emptySet());
    }

    public LoginResponse(String sessionToken, String userId, String displayName, Role role,
                         Set<String> adminScopes) {
        this.sessionToken = sessionToken;
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
        this.adminScopes = adminScopes == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(adminScopes);
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    /** Sub-system keys this administrator may manage; empty for other roles. */
    public Set<String> getAdminScopes() {
        return adminScopes;
    }
}
