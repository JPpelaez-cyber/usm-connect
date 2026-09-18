package com.usmconnect.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing.
 *
 * NOTE: For a real production deployment you would use bcrypt/argon2 (e.g.
 * via a library such as jBCrypt). This sandbox cannot reach Maven Central to
 * fetch that dependency, so we implement salted SHA-256 with a high work
 * factor (many rounds) instead. It is NOT as strong as bcrypt, but it is far
 * better than plaintext or a single unsalted hash, and is a drop-in spot to
 * swap in bcrypt later (see README).
 */
public final class PasswordUtil {

    private static final int ROUNDS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {}

    public static String randomSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String password, String saltB64) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] salt = Base64.getDecoder().decode(saltB64);
            byte[] result = password.getBytes("UTF-8");
            for (int r = 0; r < ROUNDS; r++) {
                digest.reset();
                digest.update(salt);
                digest.update(result);
                result = digest.digest();
            }
            return Base64.getEncoder().encodeToString(result);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verify(String password, String saltB64, String expectedHash) {
        String actual = hash(password, saltB64);
        return constantTimeEquals(actual, expectedHash);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public static String randomToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
