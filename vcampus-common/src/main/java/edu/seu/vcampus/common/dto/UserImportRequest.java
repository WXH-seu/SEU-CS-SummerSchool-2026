package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Batch registration request produced by the CSV import channel. It carries a
 * list of {@link RegisterRequest} parsed from a CSV file, each representing one
 * user that an administrator wants to create.
 */
public final class UserImportRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<RegisterRequest> users;

    public UserImportRequest(List<RegisterRequest> users) {
        this.users = new ArrayList<RegisterRequest>(users);
    }

    public List<RegisterRequest> getUsers() {
        return Collections.unmodifiableList(users);
    }
}
