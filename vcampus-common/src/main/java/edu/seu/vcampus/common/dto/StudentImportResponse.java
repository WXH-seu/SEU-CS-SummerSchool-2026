package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Counts and row-level failures returned after a student batch import. */
public final class StudentImportResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int imported;
    private final List<StudentImportFailure> failures;

    public StudentImportResponse(int imported, List<StudentImportFailure> failures) {
        this.imported = imported;
        this.failures = new ArrayList<StudentImportFailure>(failures);
    }

    public int getImported() { return imported; }
    public List<StudentImportFailure> getFailures() {
        return Collections.unmodifiableList(failures);
    }
}
