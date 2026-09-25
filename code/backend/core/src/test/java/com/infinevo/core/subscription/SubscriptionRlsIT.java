package com.infinevo.core.subscription;

import static com.infinevo.core.subscription.SubscriptionTestSchema.TENANT_A;
import static com.infinevo.core.subscription.SubscriptionTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-12.1 — tenant A cannot read tenant B's subscription as {@code app_user} (spec section 7).
 */
@SpringBootTest(classes = SubscriptionTestApp.class)
class SubscriptionRlsIT extends AbstractIntegrationTest {

    @Autowired
    private SubscriptionService subscriptionService;

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
    void seedSubscriptions() throws SQLException {
        SubscriptionTestSchema.clearAll();

        try (Connection conn = SubscriptionTestSchema.migrationConnection()) {
            // Seed Subscription for Tenant A
            UUID subA = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription (id, tenant_id, status, started_on) VALUES (?, ?, 'ACTIVE', CURRENT_DATE)")) {
                ps.setObject(1, subA);
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription_module (id, tenant_id, subscription_id, module, granted_on) VALUES (?, ?, ?, 'PAYROLL', CURRENT_DATE)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_A);
                ps.setObject(3, subA);
                ps.executeUpdate();
            }

            // Seed Subscription for Tenant B
            UUID subB = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription (id, tenant_id, status, started_on) VALUES (?, ?, 'ACTIVE', CURRENT_DATE)")) {
                ps.setObject(1, subB);
                ps.setObject(2, TENANT_B);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription_module (id, tenant_id, subscription_id, module, granted_on) VALUES (?, ?, ?, 'HRMS', CURRENT_DATE)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_B);
                ps.setObject(3, subB);
                ps.executeUpdate();
            }
        }
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Raw app_user connection bound to tenant A cannot see tenant B's subscription or modules")
    void rawAppUserConnection_cannotSeeCrossTenantSubscription() throws SQLException {
        try (Connection conn = SubscriptionTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = '" + TENANT_A + "'");
            }

            // Tenant A can see its own subscription
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.subscription WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_A);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }

            // Tenant A cannot see Tenant B's subscription
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.subscription WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_B);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }

            // Tenant A cannot see Tenant B's module
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.subscription_module WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_B);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
            conn.rollback();
        }
    }

    @Test
    @DisplayName(
            "SubscriptionService returns tenant A subscription and throws not found on tenant B when bound to tenant A")
    void subscriptionService_enforcesTenantIsolation() {
        TenantContext.set(TENANT_A);

        SubscriptionResponse response = subscriptionService.getSubscription(TENANT_A);
        assertThat(response).isNotNull();
        assertThat(response.tenantId()).isEqualTo(TENANT_A);

        // Through RLS, querying Tenant B returns empty
        assertThatThrownBy(() -> subscriptionService.getSubscription(TENANT_B))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }
}
