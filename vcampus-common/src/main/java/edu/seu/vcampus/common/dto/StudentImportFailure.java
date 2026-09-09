package edu.seu.vcampus.common.dto;

import java.io.Serializable;

/** One rejected academic-record row in a batch import. */
public final class StudentImportFailure implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int row;
    private final String studentId;
    private final String reason;

    public StudentImportFailure(int row, String studentId, String reason) {
        this.row = row;
        this.studentId = studentId;
        this.reason = reason;
    }

    public int getRow() { return row; }
    public String getStudentId() { return studentId; }
    public String getReason() { return reason; }
}
