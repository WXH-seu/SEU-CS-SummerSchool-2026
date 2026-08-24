package edu.seu.vcampus.server.security;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PasswordHasherTest {
    @Test
    public void hashesAndVerifiesPassword() {
        PasswordHasher hasher = new PasswordHasher();
        String salt = hasher.newSalt();
        String hash = hasher.hash("admin123", salt);

        assertTrue(hasher.matches("admin123", salt, hash));
        assertFalse(hasher.matches("wrong", salt, hash));
    }
}
