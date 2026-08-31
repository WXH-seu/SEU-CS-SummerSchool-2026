package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Summary of a CSV batch import, with counts and per-row failures. */
public final class UserImportResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int imported;
    private final List<UserImportFailure> failures;

    public UserImportResponse(int imported, List<UserImportFailure> failures) {
        this.imported = imported;
        this.failures = new ArrayList<UserImportFailure>(failures);
    }

    public int getImported() {
        return imported;
    }

    public List<UserImportFailure> getFailures() {
        return Collections.unmodifiableList(failures);
    }
}
