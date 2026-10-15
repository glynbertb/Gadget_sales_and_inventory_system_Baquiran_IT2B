package com.app.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Salted PBKDF2 password hashing (JDK built-in, no extra dependency).
 * Stored format: pbkdf2$iterations$saltBase64$hashBase64
 * Legacy plain-text values are still accepted at login and upgraded automatically.
 */
public final class PasswordUtil {

    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordUtil() { }

    public static String hash(String raw) {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] h = derive(raw.toCharArray(), salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(h);
    }

    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX + "$");
    }

    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) return false;
        if (!isHashed(stored)) {
            return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
        }
        String[] p = stored.split("\\$");
        if (p.length != 4) return false;
        try {
            int iterations = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(expected, derive(raw.toCharArray(), salt, iterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        }
    }
}
