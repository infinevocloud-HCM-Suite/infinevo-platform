package com.infinevo.core.invitation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generates and hashes single-use bearer tokens for invitations (W-24.2, spec section 6).
 */
public final class InvitationTokenUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationTokenUtils() {}

    /**
     * Generates a 256-bit (32-byte) cryptographically secure random token formatted as a 64-character lowercase hex string.
     */
    public static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Hashes the plaintext token using SHA-256, returning a 64-character lowercase hex digest.
     */
    public static String hashToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token must not be null or blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
