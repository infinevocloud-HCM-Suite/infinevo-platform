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
 * Integration test proving audit log retention sweep (W-22.2).
 *
 * <p>Proves that with audit rows at 6 years and 8 years old, only the 8-year rows are removed,
 * while rows inside the 7-year retention window remain untouched.
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@EnabledIfDockerAvailable
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, RetentionWorkerTestSchema.Initializer.class})
class AuditRetentionIT extends AbstractIntegrationTest {

    @Autowired
    private AuditRetentionService retentionService;

    @Autowired
    private RetentionRunRepository retentionRunRepository;

    @Test
    @DisplayName("With rows at 6 years and 8 years old, only the 8-year rows are deleted")
    void onlyExpiredAuditRowsAreDeleted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant sixYearsOld = now.minus(6 * 365L, ChronoUnit.DAYS);
        Instant eightYearsOld = now.minus(8 * 365L, ChronoUnit.DAYS);

        UUID sixYearRowId;
        UUID eightYearRowId;

        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, tenantId, "Audit Retention Test Tenant", 84, 12);
            sixYearRowId = RetentionWorkerTestSchema.insertAuditLog(conn, tenantId, "employee", "emp-6yr", sixYearsOld);
            eightYearRowId =
                    RetentionWorkerTestSchema.insertAuditLog(conn, tenantId, "employee", "emp-8yr", eightYearsOld);
        }

        try {
            TenantContext.set(tenantId);
            RetentionRun run =
                    retentionService.sweepTarget(tenantId, AuditRetentionService.TARGET_AUDIT_LOG, 84, false);

            assertThat(run.getStatus()).isEqualTo("COMPLETED");
            assertThat(run.getRowsDeleted()).isGreaterThanOrEqualTo(1L);

            // Verify via direct query on core.audit_log using migrationConnection under tenant context
            try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
                conn.setAutoCommit(false);
                TenantContext.setForConnection(conn);

                // 6-year-old row must STILL EXIST
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.audit_log WHERE id = ?")) {
                    ps.setObject(1, sixYearRowId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("6-year-old row must NOT be deleted")
                                .isTrue();
                    }
                }

                // 8-year-old row must be DELETED
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.audit_log WHERE id = ?")) {
                    ps.setObject(1, eightYearRowId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("8-year-old row must be deleted")
                                .isFalse();
                    }
                }
                conn.commit();
            }
        } finally {
            TenantContext.clear();
        }
    }
}
