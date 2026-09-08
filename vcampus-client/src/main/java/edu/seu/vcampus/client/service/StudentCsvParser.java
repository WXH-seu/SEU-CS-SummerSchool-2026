package edu.seu.vcampus.client.service;

import edu.seu.vcampus.common.dto.StudentDto;

import java.util.ArrayList;
import java.util.List;

/** Parses the UTF-8 CSV format used by the academic student importer. */
public final class StudentCsvParser {
    private static final int COLUMN_COUNT = 11;

    private StudentCsvParser() {
    }

    public static List<StudentDto> parse(String content) {
        if (content == null) {
            throw new IllegalArgumentException("CSV 内容为空");
        }
        List<StudentDto> students = new ArrayList<StudentDto>();
        String[] lines = content.split("\\r?\\n");
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = stripBom(lines[lineIndex]);
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            List<String> columns = parseLine(line, lineIndex + 1);
            if (isHeader(columns)) {
                continue;
            }
            if (columns.size() != COLUMN_COUNT) {
                throw new IllegalArgumentException("第 " + (lineIndex + 1)
                        + " 行应包含 11 列，当前为 " + columns.size() + " 列");
            }
            int enrollmentYear;
            try {
                enrollmentYear = Integer.parseInt(columns.get(7).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "第 " + (lineIndex + 1) + " 行：入学年份必须是数字");
            }
            students.add(new StudentDto(cell(columns, 0), cell(columns, 1),
                    cell(columns, 2), cell(columns, 3), cell(columns, 4),
                    cell(columns, 5), cell(columns, 6), enrollmentYear,
                    cell(columns, 8), cell(columns, 9), cell(columns, 10)));
        }
        if (students.isEmpty()) {
            throw new IllegalArgumentException("未解析到任何学生学籍数据");
        }
        return students;
    }

    private static List<String> parseLine(String line, int lineNumber) {
        List<String> values = new ArrayList<String>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("第 " + lineNumber + " 行存在未闭合的双引号");
        }
        values.add(value.toString().trim());
        return values;
    }

    private static boolean isHeader(List<String> columns) {
        if (columns.isEmpty()) {
            return false;
        }
        String first = columns.get(0).trim();
        return "studentId".equalsIgnoreCase(first) || "学号".equals(first);
    }

    private static String cell(List<String> columns, int index) {
        return columns.get(index).trim();
    }

    private static String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
