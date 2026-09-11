package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Page of audit log records returned to a super administrator. */
public final class UserOperationLogResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<UserOperationLog> logs;

    public UserOperationLogResponse(List<UserOperationLog> logs) {
        this.logs = new ArrayList<UserOperationLog>(logs);
    }

    public List<UserOperationLog> getLogs() {
        return Collections.unmodifiableList(logs);
    }
}
