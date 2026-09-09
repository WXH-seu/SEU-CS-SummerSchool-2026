package edu.seu.vcampus.server.service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory store of password-reset verification codes with a time-to-live.
 * Each email maps to one code; a successful verification consumes it.
 */
public final class PasswordResetService {
    private final SecureRandom random = new SecureRandom();
    private final long ttlMillis;
    private final Map<String, Entry> entries = new HashMap<String, Entry>();

    public PasswordResetService(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /** Generates a 6-digit code and stores it for the email. */
    public synchronized String create(String email) {
        String code = String.format("%06d", random.nextInt(1000000));
        entries.put(email, new Entry(code, System.currentTimeMillis() + ttlMillis));
        return code;
    }

    /** Returns {@code true} and consumes the code when it is valid and unexpired. */
    public synchronized boolean verify(String email, String code) {
        Entry entry = entries.get(email);
        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() > entry.expiry) {
            entries.remove(email);
            return false;
        }
        if (!entry.code.equals(code)) {
            return false;
        }
        entries.remove(email);
        return true;
    }

    private static final class Entry {
        private final String code;
        private final long expiry;

        private Entry(String code, long expiry) {
            this.code = code;
            this.expiry = expiry;
        }
    }
}
