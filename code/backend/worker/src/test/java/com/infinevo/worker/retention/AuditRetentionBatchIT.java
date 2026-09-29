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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test proving batched and resumable audit retention sweeps (W-22.2).
 *
 * <p>Proves that when a sweep is interrupted and resumed:
 * <ul>
 *   <li>The existing in-progress run is resumed rather than duplicated</li>
 *   <li>Previously deleted rows are not double-counted</li>
 *   <li>Total {@code rows_deleted} accurately reflects the total items removed</li>
 * </ul>
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@EnabledIfDockerAvailable
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, RetentionWorkerTestSchema.Initializer.class})
class AuditRetentionBatchIT extends AbstractIntegrationTest {

    @Autowired
    private AuditRetentionService retentionService;

    @Autowired
    private RetentionRunRepository retentionRunRepository;

    @Test
    @DisplayName("An interrupted sweep resumes and does not double-count rows_deleted")
    void interruptedSweepResumesWithoutDoubleCounting() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant eightYearsOld = now.minus(8 * 365L, ChronoUnit.DAYS);

        LocalDate cutoffDate = retentionService.calculateCutoffDate(84);

        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, tenantId, "Batch Retention Test", 84, 12);
            // Insert 6 remaining expired rows
            for (int i = 0; i < 6; i++) {
                RetentionWorkerTestSchema.insertAuditLog(conn, tenantId, "employee", "emp-batch-" + i, eightYearsOld);
            }
        }

        // Simulate an interrupted run where 4 rows were already deleted earlier
        RetentionRun inProgressRun = new RetentionRun(
                tenantId,
                AuditRetentionService.TARGET_AUDIT_LOG,
                cutoffDate,
                4L, // 4 rows deleted in batch 1 before interruption
                false,
                "RUNNING",
                now.minusSeconds(60),
                null);
        try {
            TenantContext.set(tenantId);
            inProgressRun = retentionRunRepository.save(inProgressRun);

            // Now resume the sweep: should find inProgressRun and delete the remaining 6 rows
            RetentionRun resumedRun =
                    retentionService.sweepTarget(tenantId, AuditRetentionService.TARGET_AUDIT_LOG, 84, false);

            assertThat(resumedRun.getId()).isEqualTo(inProgressRun.getId());
            assertThat(resumedRun.getStatus()).isEqualTo("COMPLETED");
            // 4 previously deleted + 6 newly deleted = 10 total (no double-counting)
            assertThat(resumedRun.getRowsDeleted()).isEqualTo(10L);

            // Verify all expired rows in the database are now gone
            try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
                conn.setAutoCommit(false);
                TenantContext.setForConnection(conn);
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.audit_log WHERE tenant_id = ?")) {
                    ps.setObject(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getLong(1)).isEqualTo(0L);
                    }
                }
                conn.commit();
            }
        } finally {
            TenantContext.clear();
        }
    }
}
