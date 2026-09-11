package edu.seu.vcampus.client.service;

import edu.seu.vcampus.common.dto.StudentDto;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StudentCsvParserTest {
    @Test
    public void parsesHeaderQuotedNameAndBlankClassForAutoAssignment() {
        String csv = "studentId,userId,fullName,gender,birthDate,departmentId,classId,"
                + "enrollmentYear,status,phone,email\n"
                + "20260002,,\"张三,同学\",男,2008-01-01,CS,,2026,在读,,a@b.cn\n";
        List<StudentDto> students = StudentCsvParser.parse(csv);

        assertEquals(1, students.size());
        assertEquals("张三,同学", students.get(0).getFullName());
        assertTrue(students.get(0).getClassId().isEmpty());
    }

    @Test
    public void rejectsWrongColumnCount() {
        try {
            StudentCsvParser.parse("20260002,张三\n");
            fail("Invalid CSV must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("11 列"));
        }
    }
}
