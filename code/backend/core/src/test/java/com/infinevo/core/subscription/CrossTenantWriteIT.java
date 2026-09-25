package com.infinevo.core.subscription;

import static com.infinevo.core.subscription.SubscriptionTestSchema.TENANT_A;
import static com.infinevo.core.subscription.SubscriptionTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-12.1 — Cross-tenant writes through SECURITY DEFINER functions vs direct RLS refusal (spec section 7).
 *
 * <p>As {@code app_user} bound to tenant A, a direct {@code INSERT} into tenant B's
 * {@code core.subscription_module} is refused by RLS, and {@code core.set_subscription_modules(B, ...)}
 * succeeds — the function is the only door.
 */
@SpringBootTest(classes = SubscriptionTestApp.class)
class CrossTenantWriteIT extends AbstractIntegrationTest {

    private UUID subB;

    @BeforeAll
    static void applySchema() throws Exception {
        SubscriptionTestSchema.apply();
        SubscriptionTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        SubscriptionTestSchema.clearAll();
    }

    @BeforeEach
    void seedTenantBSubscription() throws SQLException {
        SubscriptionTestSchema.clearAll();

        try (Connection conn = SubscriptionTestSchema.migrationConnection()) {
            subB = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription (id, tenant_id, status, started_on) VALUES (?, ?, 'ACTIVE', CURRENT_DATE)")) {
                ps.setObject(1, subB);
                ps.setObject(2, TENANT_B);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName(
            "Bound to tenant A, direct INSERT into tenant B's subscription_module is refused by RLS, but core.set_subscription_modules succeeds")
    void directInsert_refusedByRls_functionSucceeds() throws SQLException {
        try (Connection conn = SubscriptionTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = '" + TENANT_A + "'");
            }

            // 1. Direct INSERT into Tenant B's subscription_module MUST fail due to RLS
            UUID directModuleId = UUID.randomUUID();
            assertThatThrownBy(() -> {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO core.subscription_module (id, tenant_id, subscription_id, module, granted_on) "
                                        + "VALUES (?, ?, ?, 'HRMS', CURRENT_DATE)")) {
                            ps.setObject(1, directModuleId);
                            ps.setObject(2, TENANT_B);
                            ps.setObject(3, subB);
                            ps.executeUpdate();
                        }
                    })
                    .isInstanceOf(SQLException.class);

            conn.rollback(); // Clear failed transaction state
        }

        // 2. Call core.set_subscription_modules(TENANT_B, ARRAY['HRMS', 'PAYROLL']) as app_user
        try (Connection conn = SubscriptionTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = '" + TENANT_A + "'");
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT core.set_subscription_modules(?, ?)")) {
                ps.setObject(1, TENANT_B);
                Array array = conn.createArrayOf("text", new String[] {"HRMS", "PAYROLL"});
                ps.setArray(2, array);
                ps.execute();
            }

            conn.commit();
        }

        // 3. Verify via migration connection (bypasses RLS) that Tenant B's modules were written
        try (Connection conn = SubscriptionTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT module FROM core.subscription_module WHERE tenant_id = ? AND revoked_on IS NULL ORDER BY module")) {
            ps.setObject(1, TENANT_B);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("module")).isEqualTo("HRMS");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("module")).isEqualTo("PAYROLL");
                assertThat(rs.next()).isFalse();
            }
        }
    }
}
