package edu.seu.vcampus.common.dto;

import edu.seu.vcampus.common.enums.Role;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies that every user-module DTO survives object-stream round trips. */
public class UserDtoSerializationTest {
    @Test
    public void registerRequestRoundTrips() throws Exception {
        RegisterRequest request =
                new RegisterRequest("stu2026", "secret123", "新同学", Role.TEACHER);
        RegisterRequest restored = roundTrip(request);
        assertEquals("stu2026", restored.getUserId());
        assertEquals("secret123", restored.getPassword());
        assertEquals("新同学", restored.getDisplayName());
        assertEquals(Role.TEACHER, restored.getRole());
    }

    @Test
    public void accountInfoAndListRoundTrip() throws Exception {
        AccountInfo student =
                new AccountInfo("stu2026", "新同学", Role.STUDENT, true);
        AccountInfo admin = new AccountInfo("admin", "管理员", Role.ADMIN, true);
        UserListResponse response = new UserListResponse(Arrays.asList(student, admin));

        UserListResponse restored = roundTrip(response);
        List<AccountInfo> users = restored.getUsers();
        assertEquals(2, users.size());
        assertFalse(users.contains(null));
        assertTrue(users.get(0).isActive());
    }

    @Test
    public void smallRequestsRoundTrip() throws Exception {
        assertEquals("新名字",
                roundTrip(new ProfileUpdateRequest("新名字")).getDisplayName());
        assertEquals("old123",
                roundTrip(new PasswordChangeRequest("old123", "new456")).getOldPassword());
        assertFalse(roundTrip(new DeleteAccountRequest("secret123")).getPassword().isEmpty());
        assertFalse(roundTrip(new UserStatusUpdateRequest("stu2026", false)).isActive());
    }

    @Test
    public void importDtosRoundTrip() throws Exception {
        UserImportRequest request = roundTrip(new UserImportRequest(Arrays.asList(
                new RegisterRequest("s001", "secret123", "学生一", Role.STUDENT),
                new RegisterRequest("t001", "secret123", "教师一", Role.TEACHER))));
        assertEquals(2, request.getUsers().size());

        UserImportResponse response = roundTrip(new UserImportResponse(3,
                Arrays.asList(new UserImportFailure(1, "s001", "账号已存在"))));
        assertEquals(3, response.getImported());
        assertEquals(1, response.getFailures().size());
        assertEquals("账号已存在", response.getFailures().get(0).getReason());
    }

    @Test
    public void auditDtosRoundTrip() throws Exception {
        Date now = new Date();
        UserOperationLog log = new UserOperationLog(7, now, "superadmin", "REGISTER",
                "stu2026", "创建账号", true);
        UserOperationLog restored = roundTrip(log);
        assertEquals(7, restored.getId());
        assertEquals("REGISTER", restored.getOperation());
        assertEquals("stu2026", restored.getTargetUserId());
        assertTrue(restored.isSuccess());

        UserOperationLogResponse response =
                roundTrip(new UserOperationLogResponse(Arrays.asList(log)));
        assertEquals(1, response.getLogs().size());
        assertEquals("superadmin", response.getLogs().get(0).getUserId());
    }

    @SuppressWarnings("unchecked")
    private <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes);
        output.writeObject(value);
        output.flush();
        ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        return (T) input.readObject();
    }
}
