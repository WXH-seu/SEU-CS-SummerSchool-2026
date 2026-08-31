package edu.seu.vcampus.server.session;

import edu.seu.vcampus.server.dao.UserAccount;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe in-memory session registry for connected clients. */
public final class SessionRegistry {
    private final ConcurrentMap<String, UserAccount> sessions =
            new ConcurrentHashMap<String, UserAccount>();

    public String create(UserAccount account) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, account);
        return token;
    }

    public UserAccount find(String token) {
        return token == null ? null : sessions.get(token);
    }

    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** Removes every session that belongs to the given user id. */
    public void removeAllForUser(String userId) {
        if (userId == null) {
            return;
        }
        for (String token : sessions.keySet()) {
            UserAccount account = sessions.get(token);
            if (account != null && userId.equals(account.getUserId())) {
                sessions.remove(token);
            }
        }
    }
}
