package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-65.2 spec §7: As app_user bound to Infinevo, direct INSERT into core.impersonation_session
 * for a customer tenant is refused; open_impersonation_session succeeds.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class ImpersonationRlsIT extends AbstractIntegrationTest {

    private UUID customerTenant;
    private UUID staffUserId;
    private UUID targetUserId;

    @BeforeEach
    void setUp() throws SQLException {
        customerTenant = AuthzTestSchema.insertTenant("Customer RLS " + UUID.randomUUID());
        staffUserId = UUID.randomUUID();
        AuthzTestSchema.insertMember(PlatformTenant.DEFAULT_PLATFORM_TENANT_ID, staffUserId, "staff@infinevo.local");
        targetUserId = AuthzTestSchema.insertMember(customerTenant, UUID.randomUUID(), "target@customer.local");
    }

    @Test
    @DisplayName("app_user bound to Infinevo cannot directly INSERT an impersonation session for customer tenant")
    void directInsertForOtherTenantRefusedByRls() throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            bindTenant(conn, PlatformTenant.DEFAULT_PLATFORM_TENANT_ID);

            assertThatThrownBy(() -> {
                        try (PreparedStatement ps = conn.prepareStatement(
                                """
                                INSERT INTO core.impersonation_session
                                    (id, tenant_id, platform_user_id, target_user_account_id, reason, expires_at, created_by, updated_by)
                                VALUES (?, ?, ?, ?, 'Bypassing function', ?, 'staff', 'staff')
                                """)) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, customerTenant);
                            ps.setObject(3, staffUserId);
                            ps.setObject(4, targetUserId);
                            ps.setTimestamp(5, Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
                            ps.executeUpdate();
                        }
                    })
                    .as("Direct INSERT into core.impersonation_session across tenants violates RLS policy")
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("app_user cannot directly UPDATE or DELETE rows in core.impersonation_session (revoked permissions)")
    void directUpdateAndDeleteRefusedByGrants() throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            bindTenant(conn, customerTenant);

            assertThatThrownBy(() -> {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE core.impersonation_session SET reason = 'tampered' WHERE tenant_id = ?")) {
                            ps.setObject(1, customerTenant);
                            ps.executeUpdate();
                        }
                    })
                    .as("app_user does not hold UPDATE on core.impersonation_session")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> {
                        try (PreparedStatement ps =
                                conn.prepareStatement("DELETE FROM core.impersonation_session WHERE tenant_id = ?")) {
                            ps.setObject(1, customerTenant);
                            ps.executeUpdate();
                        }
                    })
                    .as("app_user does not hold DELETE on core.impersonation_session")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    @DisplayName("open, resolve, and close impersonation sessions succeed via SECURITY DEFINER functions")
    void lifecycleViaFunctionsSucceeds() throws SQLException {
        UUID sessionId;

        try (Connection conn = AuthzTestSchema.appConnection()) {
            bindTenant(conn, PlatformTenant.DEFAULT_PLATFORM_TENANT_ID);

            // 1. Open impersonation session
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT session_id, tenant_id, user_account_id, user_email, expires_at FROM core.open_impersonation(?, ?, ?, NULL, 'Investigating customer ticket')")) {
                ps.setObject(1, customerTenant);
                ps.setObject(2, staffUserId);
                ps.setObject(3, targetUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    sessionId = rs.getObject("session_id", UUID.class);
                    assertThat(sessionId).isNotNull();
                }
            }

            // 2. Resolve impersonation session
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT session_id, tenant_id, platform_user_id, target_user_account_id, target_email, action_codes FROM core.resolve_impersonation(?, ?)")) {
                ps.setObject(1, sessionId);
                ps.setObject(2, staffUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getObject("tenant_id", UUID.class)).isEqualTo(customerTenant);
                    assertThat(rs.getObject("target_user_account_id", UUID.class))
                            .isEqualTo(targetUserId);
                    assertThat(rs.getString("target_email")).isEqualTo("target@customer.local");
                }
            }

            // 3. Close impersonation session
            try (PreparedStatement ps = conn.prepareStatement("SELECT core.close_impersonation(?, ?)")) {
                ps.setObject(1, sessionId);
                ps.setObject(2, staffUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean(1)).isTrue();
                }
            }

            // 4. Resolving closed session yields nothing
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM core.resolve_impersonation(?, ?)")) {
                ps.setObject(1, sessionId);
                ps.setObject(2, staffUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isFalse();
                }
            }
        }
    }

    /**
     * Session-level, not transaction-local: the connection is in autocommit, so a local setting would
     * be gone by the next statement and RLS would see no tenant at all. The connection is closed after
     * each test, which ends the session.
     */
    private void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }
}
