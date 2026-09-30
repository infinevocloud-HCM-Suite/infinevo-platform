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

    @Test
    @DisplayName("creating an invitation logs nothing that carries the token, even when composing the email fails")
    @SuppressWarnings("unchecked")
    void tokenNeverLogged() throws Exception {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(InvitationServiceImpl.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
                new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        ch.qos.logback.classic.Level previous = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);

        UserInvitationRepository invitations = org.mockito.Mockito.mock(UserInvitationRepository.class);
        org.mockito.Mockito.when(invitations.save(org.mockito.ArgumentMatchers.any(UserInvitation.class)))
                .thenAnswer(call -> {
                    UserInvitation inv = call.getArgument(0);
                    InvitationServiceTest.setId(inv, UUID.randomUUID());
                    return inv;
                });
        com.infinevo.core.notification.NotificationService notifications =
                org.mockito.Mockito.mock(com.infinevo.core.notification.NotificationService.class);
        // A composer failure whose message quotes the data it was given - the link, and so the token
        org.mockito.Mockito.when(notifications.compose(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    throw new IllegalStateException("could not render " + call.getArgument(2));
                });

        InvitationServiceImpl service = new InvitationServiceImpl(
                invitations,
                org.mockito.Mockito.mock(UserInvitationRoleRepository.class),
                org.mockito.Mockito.mock(EmployeeInvitationRepository.class),
                org.mockito.Mockito.mock(com.infinevo.core.authz.RoleRepository.class),
                org.mockito.Mockito.mock(com.infinevo.core.authz.RoleService.class),
                org.mockito.Mockito.mock(com.infinevo.core.authz.UserRoleRepository.class),
                org.mockito.Mockito.mock(com.infinevo.core.employee.EmployeeRepository.class),
                org.mockito.Mockito.mock(com.infinevo.shared.identity.UserAccountRepository.class),
                org.mockito.Mockito.mock(com.infinevo.shared.identity.UserProfileSyncService.class),
                org.mockito.Mockito.mock(KeycloakProvisioningService.class),
                org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class),
                InvitationServiceTest.LINK_BASE);
        Field field = InvitationServiceImpl.class.getDeclaredField("notificationService");
        field.setAccessible(true);
        field.set(service, notifications);

        com.infinevo.shared.tenant.TenantContext.set(UUID.randomUUID());
        try {
            service.createUserInvitation(
                    new UserInvitationRequest("log@example.com", java.util.Set.of()), UUID.randomUUID());

            org.mockito.ArgumentCaptor<java.util.Map<String, Object>> data =
                    org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
            org.mockito.Mockito.verify(notifications)
                    .compose(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), data.capture());
            String link = (String) data.getValue().get("link");
            String token = link.substring(link.indexOf("token=") + "token=".length());
            assertThat(token).hasSize(64);

            assertThat(logs.list).isNotEmpty();
            assertThat(logs.list).noneSatisfy(event -> assertThat(event.getFormattedMessage())
                    .contains(token));
            assertThat(logs.list).noneSatisfy(event -> assertThat(String.valueOf(event.getThrowableProxy()))
                    .contains(token));
        } finally {
            com.infinevo.shared.tenant.TenantContext.clear();
            logger.detachAppender(logs);
            logger.setLevel(previous);
        }
    }
}
