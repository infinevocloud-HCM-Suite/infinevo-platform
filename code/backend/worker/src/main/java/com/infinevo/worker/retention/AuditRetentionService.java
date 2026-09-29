package com.infinevo.worker.retention;

import com.infinevo.shared.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service orchestrating audit log and notification retention sweeps (W-22.2).
 *
 * <p>Deletes expired rows past the tenant's configured retention window from two targets:
 * <ul>
 *   <li>{@code core.audit_log} (default 84 months / 7 years, minimum 12 months)</li>
 *   <li>{@code core.notification} (default 12 months / 1 year, minimum 1 month)</li>
 * </ul>
 *
 * <p>Connects via dedicated {@code retention_user} role (no {@code UPDATE} on targets, no access
 * outside {@code core}, cannot bypass RLS). Enumerates tenants using {@code core.list_tenants_for_sweep()}.
 * Deletions are batched, interruptible/resumable, and tracked in {@code core.retention_run}.
 */
@Service
public class AuditRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

    public static final String TARGET_AUDIT_LOG = "core.audit_log";
    public static final String TARGET_NOTIFICATION = "core.notification";

    public static final int DEFAULT_AUDIT_RETENTION_MONTHS = 84;
    public static final int DEFAULT_NOTIFICATION_RETENTION_MONTHS = 12;
    public static final int MINIMUM_AUDIT_RETENTION_MONTHS = 12;

    // Each names the tenant as well as relying on RLS: defence in depth, and the planner uses the
    // tenant-leading indexes (tenant_id, occurred_at) and (tenant_id, queued_at).
    private static final String DELETE_AUDIT_LOG_BATCH_SQL = "DELETE FROM core.audit_log WHERE id IN ("
            + "SELECT id FROM core.audit_log WHERE tenant_id = ? AND occurred_at < ? ORDER BY occurred_at ASC LIMIT ?)";

    private static final String DELETE_NOTIFICATION_BATCH_SQL = "DELETE FROM core.notification WHERE id IN ("
            + "SELECT id FROM core.notification WHERE tenant_id = ? AND queued_at < ? ORDER BY queued_at ASC LIMIT ?)";

    private static final String COUNT_AUDIT_LOG_SQL =
            "SELECT count(*) FROM core.audit_log WHERE tenant_id = ? AND occurred_at < ?";

    private static final String COUNT_NOTIFICATION_SQL =
            "SELECT count(*) FROM core.notification WHERE tenant_id = ? AND queued_at < ?";

    public record TenantSweepTarget(UUID tenantId, int auditRetentionMonths, int notificationRetentionMonths) {}

    private final DataSource retentionDataSource;
    private final JdbcTemplate retentionJdbcTemplate;
    private final RetentionRunStore runs;
    private final int batchSize;
    private final Clock clock;

    public AuditRetentionService(
            @Qualifier("retentionDataSource") DataSource retentionDataSource,
            @Qualifier("retentionJdbcTemplate") JdbcTemplate retentionJdbcTemplate,
            RetentionRunStore runs,
            @Value("${worker.retention.batch-size:500}") int batchSize,
            @Autowired(required = false) Clock clock) {
        this.retentionDataSource = Objects.requireNonNull(retentionDataSource, "retentionDataSource must not be null");
        this.retentionJdbcTemplate =
                Objects.requireNonNull(retentionJdbcTemplate, "retentionJdbcTemplate must not be null");
        this.runs = Objects.requireNonNull(runs, "runs must not be null");
        this.batchSize = batchSize > 0 ? batchSize : 500;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Lists tenants and their configured retention windows using {@code core.list_tenants_for_sweep()}.
     */
    public List<TenantSweepTarget> listTenantsForSweep() {
        return retentionJdbcTemplate.query(
                "SELECT tenant_id, audit_retention_months, notification_retention_months FROM core.list_tenants_for_sweep()",
                (rs, rowNum) -> new TenantSweepTarget(
                        (UUID) rs.getObject("tenant_id"),
                        rs.getInt("audit_retention_months"),
                        rs.getInt("notification_retention_months")));
    }

    /**
     * Runs retention sweep across all tenants returned by {@code core.list_tenants_for_sweep()}.
     */
    public List<RetentionRun> runSweep(boolean isDryRun) {
        List<TenantSweepTarget> targets = listTenantsForSweep();
        List<RetentionRun> allRuns = new ArrayList<>();

        for (TenantSweepTarget target : targets) {
            try {
                List<RetentionRun> runs = sweepTenant(
                        target.tenantId(),
                        target.auditRetentionMonths(),
                        target.notificationRetentionMonths(),
                        isDryRun);
                allRuns.addAll(runs);
            } catch (Exception e) {
                log.error("Failed retention sweep for tenant {}: {}", target.tenantId(), e.getMessage(), e);
            }
        }
        return allRuns;
    }

    /**
     * Sweeps expired rows for a specific tenant across both target tables under tenant context.
     */
    public List<RetentionRun> sweepTenant(
            UUID tenantId, int auditRetentionMonths, int notificationRetentionMonths, boolean isDryRun) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        boolean wasBound = TenantContext.isBound();
        try {
            TenantContext.set(tenantId);
            RetentionRun auditRun = sweepTarget(tenantId, TARGET_AUDIT_LOG, auditRetentionMonths, isDryRun);
            RetentionRun notifRun = sweepTarget(tenantId, TARGET_NOTIFICATION, notificationRetentionMonths, isDryRun);
            return List.of(auditRun, notifRun);
        } finally {
            if (!wasBound) {
                TenantContext.clear();
            }
        }
    }

    /**
     * Sweeps a specific target table for a tenant.
     */
    public RetentionRun sweepTarget(UUID tenantId, String targetTable, int retentionMonths, boolean isDryRun) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(targetTable, "targetTable must not be null");

        int effectiveMonths = retentionMonths;
        if (TARGET_AUDIT_LOG.equals(targetTable)) {
            effectiveMonths = Math.max(
                    MINIMUM_AUDIT_RETENTION_MONTHS,
                    retentionMonths > 0 ? retentionMonths : DEFAULT_AUDIT_RETENTION_MONTHS);
        } else if (TARGET_NOTIFICATION.equals(targetTable)) {
            effectiveMonths =
                    Math.max(1, retentionMonths > 0 ? retentionMonths : DEFAULT_NOTIFICATION_RETENTION_MONTHS);
        } else {
            throw new IllegalArgumentException("Unknown target table: " + targetTable);
        }

        LocalDate cutoffDate = calculateCutoffDate(effectiveMonths);
        Instant cutoffInstant = cutoffDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        // A run an earlier day left RUNNING can never be resumed - the cutoff has moved - so it is closed as
        // PARTIAL rather than left looking in progress forever.
        runs.closeStale(tenantId, targetTable, cutoffDate, clock.instant());

        // An interrupted run of the same kind for (tenantId, targetTable, cutoffDate) is resumed. A dry run
        // never picks up a real one: it would overwrite the real run's count and mark it COMPLETED.
        Optional<RetentionRun> existing = runs.findRunning(tenantId, targetTable, cutoffDate, isDryRun);

        RetentionRun run;
        if (existing.isPresent()) {
            run = existing.get();
            log.info("Resuming existing retention run {} for tenant {} on {}", run.getId(), tenantId, targetTable);
        } else {
            run = runs.insert(new RetentionRun(
                    tenantId, targetTable, cutoffDate, 0L, isDryRun, "RUNNING", clock.instant(), null));
        }

        if (isDryRun) {
            long count = TARGET_AUDIT_LOG.equals(targetTable)
                    ? countRows(COUNT_AUDIT_LOG_SQL, tenantId, cutoffInstant)
                    : countRows(COUNT_NOTIFICATION_SQL, tenantId, cutoffInstant);
            run.setRowsDeleted(count);
            run.setStatus("COMPLETED");
            run.setFinishedAt(clock.instant());
            runs.update(run, clock.instant());
            return run;
        }

        String deleteSql =
                TARGET_AUDIT_LOG.equals(targetTable) ? DELETE_AUDIT_LOG_BATCH_SQL : DELETE_NOTIFICATION_BATCH_SQL;

        long totalDeleted = run.getRowsDeleted();
        try {
            while (true) {
                int deleted = executeBatchDelete(deleteSql, tenantId, cutoffInstant, batchSize);
                totalDeleted += deleted;
                run.setRowsDeleted(totalDeleted);
                runs.update(run, clock.instant());

                if (deleted < batchSize) {
                    break;
                }
            }
            run.setStatus("COMPLETED");
            run.setFinishedAt(clock.instant());
            runs.update(run, clock.instant());
            return run;
        } catch (RuntimeException e) {
            run.setStatus("FAILED");
            run.setFinishedAt(clock.instant());
            try {
                runs.update(run, clock.instant());
            } catch (RuntimeException recordFailure) {
                e.addSuppressed(recordFailure);
            }
            throw e;
        }
    }

    public LocalDate calculateCutoffDate(int retentionMonths) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return today.minusMonths(retentionMonths);
    }

    private int executeBatchDelete(String sql, UUID tenantId, Instant cutoff, int limit) {
        try (Connection conn = retentionDataSource.getConnection()) {
            conn.setAutoCommit(false);
            if (TenantContext.isBound()) {
                TenantContext.setForConnection(conn);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(cutoff));
                ps.setInt(3, limit);
                int deleted = ps.executeUpdate();
                conn.commit();
                return deleted;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute retention batch delete: " + e.getMessage(), e);
        }
    }

    private long countRows(String sql, UUID tenantId, Instant cutoff) {
        try (Connection conn = retentionDataSource.getConnection()) {
            conn.setAutoCommit(false);
            if (TenantContext.isBound()) {
                TenantContext.setForConnection(conn);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(cutoff));
                try (ResultSet rs = ps.executeQuery()) {
                    long count = rs.next() ? rs.getLong(1) : 0L;
                    conn.commit();
                    return count;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count rows for retention: " + e.getMessage(), e);
        }
    }
}
