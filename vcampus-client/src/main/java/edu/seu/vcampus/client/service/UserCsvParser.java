package edu.seu.vcampus.client.service;

import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.enums.Role;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the administrator CSV batch-registration format into
 * {@link RegisterRequest} instances.
 *
 * <p>Expected format (UTF-8, UTF-8 BOM tolerated, comma-separated):</p>
 *
 * <pre>
 *   账号,密码,显示名[,角色]
 * </pre>
 *
 * <p>The optional role column accepts {@code STUDENT}/{@code TEACHER}/{@code ADMIN}
 * or the Chinese equivalents {@code 学生}/{@code 教师}/{@code 管理员}; it defaults to
 * {@link Role#STUDENT} when omitted. Blank lines, {@code #} comments and an
 * optional header row are ignored. Any malformed row raises an
 * {@link IllegalArgumentException} carrying the row number.
 */
public final class UserCsvParser {
    private UserCsvParser() {
    }

    /** Parses CSV text into a list of registration requests. */
    public static List<RegisterRequest> parse(String content) {
        if (content == null) {
            throw new IllegalArgumentException("CSV 内容为空");
        }
        String[] lines = content.split("\\r?\\n");
        List<RegisterRequest> result = new ArrayList<RegisterRequest>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (columns.length < 3) {
                throw new IllegalArgumentException(
                        "第 " + (i + 1) + " 行：至少需要 账号,密码,显示名 三列");
            }
            String userId = trimCell(columns[0]);
            String password = trimCell(columns[1]);
            String displayName = trimCell(columns[2]);
            if (isHeader(columns)) {
                continue;
            }
            if (userId.isEmpty() || password.isEmpty() || displayName.isEmpty()) {
                throw new IllegalArgumentException(
                        "第 " + (i + 1) + " 行：账号、密码、显示名不能为空");
            }
            Role role = columns.length >= 4 ? parseRole(trimCell(columns[3])) : Role.STUDENT;
            result.add(new RegisterRequest(userId, password, displayName, role));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("未解析到任何用户数据");
        }
        return result;
    }

    private static boolean isHeader(String[] columns) {
        return "账号".equals(trimCell(columns[0])) || "userId".equalsIgnoreCase(trimCell(columns[0]));
    }

    private static Role parseRole(String value) {
        if (value == null || value.isEmpty()) {
            return Role.STUDENT;
        }
        if ("STUDENT".equalsIgnoreCase(value) || "学生".equals(value)) {
            return Role.STUDENT;
        }
        if ("TEACHER".equalsIgnoreCase(value) || "教师".equals(value)) {
            return Role.TEACHER;
        }
        if ("ADMIN".equalsIgnoreCase(value) || "管理员".equals(value)) {
            return Role.ADMIN;
        }
        throw new IllegalArgumentException("无法识别的角色：" + value);
    }

    private static String trimCell(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
