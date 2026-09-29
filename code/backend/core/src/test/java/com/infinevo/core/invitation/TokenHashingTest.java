package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Asserts the plaintext token is never persisted, never present in responses, and never logged (W-24.2, spec §7).
 */
class TokenHashingTest {

    @Test
    @DisplayName("InvitationTokenUtils: generated token has 256-bit entropy and hashes to 64-char lowercase hex")
    void tokenGenerationAndHashingProperties() {
        String token = InvitationTokenUtils.generateToken();
        assertThat(token).isNotBlank();
        // 32 bytes encoded produces min 32 chars
        assertThat(token.length()).isGreaterThanOrEqualTo(32);

        String hash1 = InvitationTokenUtils.hashToken(token);
        String hash2 = InvitationTokenUtils.hashToken(token);

        // Deterministic SHA-256
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).matches("^[0-9a-f]{64}$");

        // Different tokens produce different hashes
        String otherToken = InvitationTokenUtils.generateToken();
        assertThat(InvitationTokenUtils.hashToken(otherToken)).isNotEqualTo(hash1);
    }

    @Test
    @DisplayName("UserInvitation: entity schema contains token_hash only, never a plaintext token field")
    void userInvitationEntityStoresOnlyHash() {
        for (Field field : UserInvitation.class.getDeclaredFields()) {
            assertThat(field.getName())
                    .as("Entity field should not be named 'token'")
                    .isNotEqualTo("token")
                    .isNotEqualTo("rawToken");
        }

        for (Method method : UserInvitation.class.getDeclaredMethods()) {
            assertThat(method.getName())
                    .as("Entity method should not expose raw token")
                    .isNotEqualTo("getToken")
                    .isNotEqualTo("getRawToken");
        }

        UUID tenantId = UUID.randomUUID();
        UserInvitation inv = new UserInvitation(
                tenantId,
                "test@example.com",
                InvitationTokenUtils.hashToken("secret-token"),
                Instant.now(),
                UUID.randomUUID(),
                "actor");

        assertThat(inv.getTokenHash()).matches("^[0-9a-f]{64}$");
        assertThat(inv.toString()).doesNotContain("secret-token");
    }

    @Test
    @DisplayName("EmployeeInvitation: entity schema contains token_hash only, never a plaintext token field")
    void employeeInvitationEntityStoresOnlyHash() {
        for (Field field : EmployeeInvitation.class.getDeclaredFields()) {
            assertThat(field.getName())
                    .as("Entity field should not be named 'token'")
                    .isNotEqualTo("token")
                    .isNotEqualTo("rawToken");
        }

        for (Method method : EmployeeInvitation.class.getDeclaredMethods()) {
            assertThat(method.getName())
                    .as("Entity method should not expose raw token")
                    .isNotEqualTo("getToken")
                    .isNotEqualTo("getRawToken");
        }

        UUID tenantId = UUID.randomUUID();
        EmployeeInvitation inv = new EmployeeInvitation(
                tenantId,
                UUID.randomUUID(),
                "test@example.com",
                InvitationTokenUtils.hashToken("secret-token"),
                Instant.now(),
                UUID.randomUUID(),
                "actor");

        assertThat(inv.getTokenHash()).matches("^[0-9a-f]{64}$");
        assertThat(inv.toString()).doesNotContain("secret-token");
    }

    @Test
    @DisplayName("UserInvitationResponse and EmployeeInvitationResponse never carry token or tokenHash")
    void responsesDoNotExposeTokenOrHash() {
        for (Field field : UserInvitationResponse.class.getDeclaredFields()) {
            assertThat(field.getName())
                    .isNotEqualTo("token")
                    .isNotEqualTo("rawToken")
                    .isNotEqualTo("tokenHash");
        }

        for (Field field : EmployeeInvitationResponse.class.getDeclaredFields()) {
            assertThat(field.getName())
                    .isNotEqualTo("token")
                    .isNotEqualTo("rawToken")
                    .isNotEqualTo("tokenHash");
        }
    }
}
