package com.infinevo.worker.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import com.infinevo.worker.InfinevoWorkerApplication;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-86 integration test: runs SubscriptionOverdueJob against PostgreSQL with RLS.
 */
@SpringBootTest(classes = InfinevoWorkerApplication.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            SubscriptionWorkerTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class SubscriptionOverdueJobIT extends AbstractIntegrationTest {

    @Autowired
    private SubscriptionOverdueJob job;

    @Test
    @DisplayName("W-86: Three tenants (overdue, not overdue, no date) — only overdue active becomes PAST_DUE, "
            + "audit row written, list function beats RLS with no tenant bound")
    void execute_overdueTenantMarkedPastDue() throws Exception {
        LocalDate asOf = LocalDate.of(2026, 11, 23);

        UUID overdueTenant = SubscriptionWorkerTestSchema.insertTenant("Overdue Co " + UUID.randomUUID());
        SubscriptionWorkerTestSchema.insertSubscription(overdueTenant, "ACTIVE", asOf.minusDays(2));

        UUID futureTenant = SubscriptionWorkerTestSchema.insertTenant("Future Co " + UUID.randomUUID());
        SubscriptionWorkerTestSchema.insertSubscription(futureTenant, "ACTIVE", asOf.plusDays(10));

        UUID noDateTenant = SubscriptionWorkerTestSchema.insertTenant("No Date Co " + UUID.randomUUID());
        SubscriptionWorkerTestSchema.insertSubscription(noDateTenant, "ACTIVE", null);

        // Ensure no tenant context is bound before the sweep
        TenantContext.clear();

        // Run sweep
        job.execute(asOf);

        // Assert only overdueTenant transitioned to PAST_DUE
        assertThat(getSubscriptionStatus(overdueTenant)).isEqualTo("PAST_DUE");
        assertThat(getSubscriptionStatus(futureTenant)).isEqualTo("ACTIVE");
        assertThat(getSubscriptionStatus(noDateTenant)).isEqualTo("ACTIVE");

        // Assert audit log row was recorded for the status transition
        UUID subscriptionId = getSubscriptionId(overdueTenant);
        assertThat(hasAuditRow(subscriptionId, "PAST_DUE")).isTrue();
    }

    private static String getSubscriptionStatus(UUID tenantId) throws SQLException {
        try (Connection conn = SubscriptionWorkerTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT status FROM core.subscription WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private static UUID getSubscriptionId(UUID tenantId) throws SQLException {
        try (Connection conn = SubscriptionWorkerTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT id FROM core.subscription WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static boolean hasAuditRow(UUID subscriptionId, String expectedStatus) throws SQLException {
        try (Connection conn = SubscriptionWorkerTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT count(*) FROM core.audit_log
                         WHERE entity_table = 'subscription'
                           AND entity_id = ?
                           AND new_values->>'status' = ?
                        """)) {
            ps.setString(1, subscriptionId.toString());
            ps.setString(2, expectedStatus);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            }
        }
    }
}
