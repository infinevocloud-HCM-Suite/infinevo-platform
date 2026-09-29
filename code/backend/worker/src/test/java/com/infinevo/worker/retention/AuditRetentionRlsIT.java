package com.infinevo.worker.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test proving row-level security isolation during audit retention sweeps (W-22.2).
 *
 * <p>Proves that:
 * <ul>
 *   <li>A retention sweep bound to Tenant A deletes no rows belonging to Tenant B</li>
 *   <li>An unbound execution deletes nothing because the RLS policy maps to NULL (fail-safe direction)</li>
 * </ul>
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@EnabledIfDockerAvailable
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, RetentionWorkerTestSchema.Initializer.class})
class AuditRetentionRlsIT extends AbstractIntegrationTest {

    @Autowired
    private AuditRetentionService retentionService;

    @Test
    @DisplayName("A sweep bound to tenant A deletes no row belonging to tenant B")
    void sweepBoundToTenantADeletesNoRowsOfTenantB() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Instant now = Instant.now();
        Instant eightYearsOld = now.minus(8 * 365L, ChronoUnit.DAYS);

        UUID tenantARowId;
        UUID tenantBRowId;

        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, tenantA, "Tenant A Retention", 84, 12);
            RetentionWorkerTestSchema.insertTenant(conn, tenantB, "Tenant B Retention", 84, 12);

            tenantARowId = RetentionWorkerTestSchema.insertAuditLog(conn, tenantA, "employee", "emp-A", eightYearsOld);
            tenantBRowId = RetentionWorkerTestSchema.insertAuditLog(conn, tenantB, "employee", "emp-B", eightYearsOld);
        }

        // Run sweep explicitly for Tenant A
        try {
            TenantContext.set(tenantA);
            retentionService.sweepTarget(tenantA, AuditRetentionService.TARGET_AUDIT_LOG, 84, false);
        } finally {
            TenantContext.clear();
        }

        // Verify Tenant A's row was deleted, and Tenant B's row is PRESERVED
        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            conn.setAutoCommit(false);

            // Check Tenant A
            TenantContext.set(tenantA);
            TenantContext.setForConnection(conn);
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.audit_log WHERE id = ?")) {
                ps.setObject(1, tenantARowId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("Tenant A row must be deleted").isFalse();
                }
            }

            // Check Tenant B
            TenantContext.set(tenantB);
            TenantContext.setForConnection(conn);
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.audit_log WHERE id = ?")) {
                ps.setObject(1, tenantBRowId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next())
                            .as("Tenant B row must NOT be deleted by Tenant A sweep")
                            .isTrue();
                }
            }
            conn.commit();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("An unbound sweep execution deletes nothing because RLS maps to NULL")
    void unboundSweepDeletesNothing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Instant eightYearsOld = Instant.now().minus(8 * 365L, ChronoUnit.DAYS);
        UUID rowId;

        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, tenantId, "Unbound Test Tenant", 84, 12);
            rowId = RetentionWorkerTestSchema.insertAuditLog(conn, tenantId, "employee", "emp-unbound", eightYearsOld);
        }

        // Ensure TenantContext is explicitly cleared (unbound)
        TenantContext.clear();

        // Attempting to delete when unbound: RLS should return 0 rows deleted
        try (Connection conn = RetentionWorkerTestSchema.retentionConnection()) {
            conn.setAutoCommit(false);
            // Notice: TenantContext.setForConnection is NOT called because unbound
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.audit_log WHERE occurred_at < ?")) {
                ps.setTimestamp(1, java.sql.Timestamp.from(Instant.now()));
                int deleted = ps.executeUpdate();
                assertThat(deleted)
                        .as("Unbound delete under RLS must affect 0 rows")
                        .isEqualTo(0);
            }
            conn.commit();
        }

        // Verify row still exists when bound to its tenant
        try {
            TenantContext.set(tenantId);
            try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
                conn.setAutoCommit(false);
                TenantContext.setForConnection(conn);
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.audit_log WHERE id = ?")) {
                    ps.setObject(1, rowId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).as("Row must still exist").isTrue();
                    }
                }
                conn.commit();
            }
        } finally {
            TenantContext.clear();
        }
    }
}
