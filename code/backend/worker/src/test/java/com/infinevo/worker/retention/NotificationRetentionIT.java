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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test proving notification retention sweep (W-22.2).
 *
 * <p>Proves that:
 * <ul>
 *   <li>For a standard tenant (12-month window), 13-month notifications are deleted and 11-month ones survive</li>
 *   <li>A tenant with {@code notification_retention_months = 24} keeps both 11 and 13-month notifications</li>
 *   <li>{@code sweepTenant} produces exactly one {@code retention_run} row per target table</li>
 * </ul>
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@EnabledIfDockerAvailable
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, RetentionWorkerTestSchema.Initializer.class})
class NotificationRetentionIT extends AbstractIntegrationTest {

    @Autowired
    private AuditRetentionService retentionService;

    @Test
    @DisplayName("13-month notifications are swept while 11-month survive, and 24-month override keeps both")
    void notificationRetentionWindowsHonoured() throws Exception {
        UUID standardTenant = UUID.randomUUID();
        UUID extendedTenant = UUID.randomUUID();

        Instant now = Instant.now();
        Instant elevenMonthsOld = now.minus(335L, ChronoUnit.DAYS); // ~11 months
        Instant thirteenMonthsOld = now.minus(395L, ChronoUnit.DAYS); // ~13 months

        UUID std11Row;
        UUID std13Row;
        UUID ext11Row;
        UUID ext13Row;

        try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
            RetentionWorkerTestSchema.insertTenant(conn, standardTenant, "Standard Retention Tenant", 84, 12);
            RetentionWorkerTestSchema.insertTenant(conn, extendedTenant, "Extended Retention Tenant", 84, 24);

            std11Row = RetentionWorkerTestSchema.insertNotification(conn, standardTenant, elevenMonthsOld);
            std13Row = RetentionWorkerTestSchema.insertNotification(conn, standardTenant, thirteenMonthsOld);

            ext11Row = RetentionWorkerTestSchema.insertNotification(conn, extendedTenant, elevenMonthsOld);
            ext13Row = RetentionWorkerTestSchema.insertNotification(conn, extendedTenant, thirteenMonthsOld);
        }

        // 1. Sweep standard tenant (12-month window)
        List<RetentionRun> stdRuns = retentionService.sweepTenant(standardTenant, 84, 12, false);
        assertThat(stdRuns).hasSize(2);
        assertThat(stdRuns)
                .extracting(RetentionRun::getTargetTable)
                .containsExactlyInAnyOrder(
                        AuditRetentionService.TARGET_AUDIT_LOG, AuditRetentionService.TARGET_NOTIFICATION);

        // Verify standard tenant notification rows
        try {
            TenantContext.set(standardTenant);
            try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
                conn.setAutoCommit(false);
                TenantContext.setForConnection(conn);

                // 11-month-old notification must SURVIVE
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.notification WHERE id = ?")) {
                    ps.setObject(1, std11Row);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("11-month notification must survive")
                                .isTrue();
                    }
                }

                // 13-month-old notification must be DELETED
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.notification WHERE id = ?")) {
                    ps.setObject(1, std13Row);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("13-month notification must be swept")
                                .isFalse();
                    }
                }
                conn.commit();
            }
        } finally {
            TenantContext.clear();
        }

        // 2. Sweep extended tenant (24-month window override)
        List<RetentionRun> extRuns = retentionService.sweepTenant(extendedTenant, 84, 24, false);
        assertThat(extRuns).hasSize(2);

        // Verify extended tenant notification rows (both must survive)
        try {
            TenantContext.set(extendedTenant);
            try (Connection conn = RetentionWorkerTestSchema.migrationConnection()) {
                conn.setAutoCommit(false);
                TenantContext.setForConnection(conn);

                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.notification WHERE id = ?")) {
                    ps.setObject(1, ext11Row);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("11-month notification in 24m tenant must survive")
                                .isTrue();
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.notification WHERE id = ?")) {
                    ps.setObject(1, ext13Row);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("13-month notification in 24m tenant must survive")
                                .isTrue();
                    }
                }
                conn.commit();
            }
        } finally {
            TenantContext.clear();
        }
    }
}
