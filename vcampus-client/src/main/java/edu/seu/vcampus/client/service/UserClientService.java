package edu.seu.vcampus.client.service;

import edu.seu.vcampus.client.network.ClientConnection;
import edu.seu.vcampus.common.dto.AccountInfo;
import edu.seu.vcampus.common.dto.DeleteAccountRequest;
import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.common.dto.PasswordChangeRequest;
import edu.seu.vcampus.common.dto.ProfileUpdateRequest;
import edu.seu.vcampus.common.dto.RegisterRequest;
import edu.seu.vcampus.common.dto.UserListResponse;
import edu.seu.vcampus.common.dto.UserStatusUpdateRequest;
import edu.seu.vcampus.common.enums.Operation;
import edu.seu.vcampus.common.enums.ResponseCode;
import edu.seu.vcampus.common.message.RequestMessage;
import edu.seu.vcampus.common.message.ResponseMessage;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * Client-side facade that converts user-module UI actions into protocol
 * requests. Every method throws {@link ClientServiceException} carrying the
 * server response code and message when the operation is rejected.
 */
public final class UserClientService {
    private final ClientConnection connection;

    public UserClientService(ClientConnection connection) {
        this.connection = connection;
    }

    /** Sends the credentials and returns the created session. */
    public LoginResponse login(String userId, String password)
            throws IOException, ClientServiceException {
        ResponseMessage<?> response = send(Operation.USER_LOGIN, null,
                new LoginRequest(userId, password));
        return (LoginResponse) response.getBody();
    }

    /** Registers a new account and signs it in immediately. */
    public LoginResponse register(RegisterRequest request)
            throws IOException, ClientServiceException {
        ResponseMessage<?> response = send(Operation.USER_REGISTER, null, request);
        return (LoginResponse) response.getBody();
    }

    /** Returns the current account information. */
    public AccountInfo queryAccount(String sessionToken)
            throws IOException, ClientServiceException {
        ResponseMessage<?> response = send(Operation.USER_ACCOUNT_QUERY, sessionToken, null);
        return (AccountInfo) response.getBody();
    }

    /** Updates the display name and returns the refreshed account. */
    public AccountInfo updateProfile(String sessionToken, String displayName)
            throws IOException, ClientServiceException {
        ResponseMessage<?> response = send(Operation.USER_PROFILE_UPDATE, sessionToken,
                new ProfileUpdateRequest(displayName));
        return (AccountInfo) response.getBody();
    }

    /** Verifies the old password and replaces it with the new one. */
    public void changePassword(String sessionToken, String oldPassword, String newPassword)
            throws IOException, ClientServiceException {
        send(Operation.USER_PASSWORD_CHANGE, sessionToken,
                new PasswordChangeRequest(oldPassword, newPassword));
    }

    /** Deregisters the current account after password confirmation. */
    public void deleteAccount(String sessionToken, String password)
            throws IOException, ClientServiceException {
        send(Operation.USER_DELETE, sessionToken, new DeleteAccountRequest(password));
    }

    /** Lists all accounts; only administrators are allowed to call this. */
    public List<AccountInfo> listUsers(String sessionToken)
            throws IOException, ClientServiceException {
        ResponseMessage<?> response = send(Operation.USER_LIST_QUERY, sessionToken, null);
        return ((UserListResponse) response.getBody()).getUsers();
    }

    /** Enables or disables another account; administrators only. */
    public void updateUserStatus(String sessionToken, String userId, boolean active)
            throws IOException, ClientServiceException {
        send(Operation.USER_STATUS_UPDATE, sessionToken,
                new UserStatusUpdateRequest(userId, active));
    }

    /** Ends the server-side session. */
    public void logout(String sessionToken) throws IOException, ClientServiceException {
        send(Operation.USER_LOGOUT, sessionToken, null);
    }

    private <T extends Serializable> ResponseMessage<?> send(
            Operation operation, String sessionToken, T body)
            throws IOException, ClientServiceException {
        ResponseMessage<?> response =
                connection.request(new RequestMessage<T>(operation, sessionToken, body));
        if (!response.isSuccess()) {
            String message = response.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "服务器拒绝了请求";
            }
            throw new ClientServiceException(
                    response.getCode() == null ? ResponseCode.SERVER_ERROR : response.getCode(),
                    message);
        }
        return response;
    }
}
