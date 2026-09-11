package edu.seu.vcampus.common.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Batch of new academic records parsed from an administrator CSV file. */
public final class StudentImportRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<StudentDto> students;

    public StudentImportRequest(List<StudentDto> students) {
        if (students == null) {
            throw new IllegalArgumentException("students is required");
        }
        this.students = new ArrayList<StudentDto>(students);
    }

    public List<StudentDto> getStudents() {
        return Collections.unmodifiableList(students);
    }
}
