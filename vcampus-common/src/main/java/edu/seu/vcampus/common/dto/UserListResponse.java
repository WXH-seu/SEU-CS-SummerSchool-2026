package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Account list returned to administrators from the user management screen. */
public final class UserListResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<AccountInfo> users;

    public UserListResponse(List<AccountInfo> users) {
        this.users = new ArrayList<AccountInfo>(users);
    }

    public List<AccountInfo> getUsers() {
        return Collections.unmodifiableList(users);
    }
}
