package edu.seu.vcampus.server.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** Creates and verifies salted password hashes using Java 8 APIs. */
public final class PasswordHasher {
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hash(String password, String encodedSalt) {
        if (password == null || encodedSalt == null) {
            throw new IllegalArgumentException("password and salt are required");
        }
        try {
            byte[] salt = Base64.getDecoder().decode(encodedSalt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            try {
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
                return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    public boolean matches(String password, String encodedSalt, String expectedHash) {
        String actualHash = hash(password, encodedSalt);
        byte[] actual = actualHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] expected = expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(actual, expected);
    }
}
