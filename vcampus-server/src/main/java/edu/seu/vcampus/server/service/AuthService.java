package edu.seu.vcampus.server.service;

import edu.seu.vcampus.common.dto.LoginRequest;
import edu.seu.vcampus.common.dto.LoginResponse;
import edu.seu.vcampus.server.dao.UserAccount;
import edu.seu.vcampus.server.dao.UserRepository;
import edu.seu.vcampus.server.security.PasswordHasher;
import edu.seu.vcampus.server.session.SessionRegistry;

import java.sql.SQLException;

/** Authentication use cases independent from networking and Swing. */
public final class AuthService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionRegistry sessionRegistry;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher,
                       SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.sessionRegistry = sessionRegistry;
    }

    public LoginResponse login(LoginRequest request) throws SQLException {
        if (request == null || isBlank(request.getUserId()) || isBlank(request.getPassword())) {
            return null;
        }
        UserAccount account = userRepository.findById(request.getUserId().trim());
        if (account == null || !account.isActive()) {
            return null;
        }
        if (!passwordHasher.matches(request.getPassword(),
                account.getPasswordSalt(), account.getPasswordHash())) {
            return null;
        }
        String token = sessionRegistry.create(account);
        return new LoginResponse(token, account.getUserId(),
                account.getDisplayName(), account.getRole());
    }

    public void logout(String sessionToken) {
        sessionRegistry.remove(sessionToken);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
