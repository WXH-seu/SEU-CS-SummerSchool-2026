package edu.seu.vcampus.client.service;

import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.enums.Role;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Verifies the CSV batch-registration format parser. */
public class UserCsvParserTest {

    @Test
    public void parsesWithDefaultStudentRole() {
        List<RegisterRequest> users = UserCsvParser.parse("s001,secret123,学生一");
        assertEquals(1, users.size());
        assertEquals("s001", users.get(0).getUserId());
        assertEquals(Role.STUDENT, users.get(0).getRole());
    }

    @Test
    public void parsesExplicitRolesAndChineseNames() {
        List<RegisterRequest> users = UserCsvParser.parse(
                "s001,secret123,学生一,STUDENT\n"
                        + "t001,secret123,教师一,教师\n"
                        + "a001,secret123,管理员,ADMIN\n"
                        + "a002,secret123,管理员,管理员");
        assertEquals(4, users.size());
        assertEquals(Role.STUDENT, users.get(0).getRole());
        assertEquals(Role.TEACHER, users.get(1).getRole());
        assertEquals(Role.ADMIN, users.get(2).getRole());
        assertEquals(Role.ADMIN, users.get(3).getRole());
    }

    @Test
    public void skipsBlankCommentAndHeaderLines() {
        List<RegisterRequest> users = UserCsvParser.parse(
                "账号,密码,显示名,角色\n"
                        + "# 这是注释\n"
                        + "\n"
                        + "s001,secret123,学生一\n"
                        + "   \n"
                        + "s002,secret123,学生二,学生");
        assertEquals(2, users.size());
        assertEquals("s001", users.get(0).getUserId());
        assertEquals("s002", users.get(1).getUserId());
        assertEquals(Role.STUDENT, users.get(1).getRole());
    }

    @Test
    public void parsesWithUtf8BomHeader() {
        List<RegisterRequest> users = UserCsvParser.parse(
                "\uFEFF账号,密码,显示名,角色\ns001,secret123,学生一,STUDENT");
        assertEquals(1, users.size());
        assertEquals("s001", users.get(0).getUserId());
        assertEquals(Role.STUDENT, users.get(0).getRole());
    }

    @Test
    public void rejectsMalformedLine() {
        try {
            UserCsvParser.parse("s001,secret123");
            fail("two-column row must be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("至少需要"));
        }
    }

    @Test
    public void rejectsUnknownRole() {
        try {
            UserCsvParser.parse("s001,secret123,学生,superuser");
            fail("unknown role must be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("角色"));
        }
    }
}
