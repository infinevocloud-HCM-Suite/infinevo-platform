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
 * Deletes AI helper chats older than the retention window, customer by customer (W-114.10).
 *
 * <p>The three chat tables of W-114.3 are swept in this order:
 * <ol>
 *   <li>{@code core.ai_message} - by its own {@code created_at}, so a session kept alive for months does not keep
 *       old messages;</li>
 *   <li>{@code core.ai_action_intent} - by {@code created_at}, whatever its status;</li>
 *   <li>{@code core.ai_session} - by its last activity, {@code last_message_at} or {@code created_at} when that is
 *       empty. Its remaining messages and intents go with it through {@code ON DELETE CASCADE}.</li>
 * </ol>
 *
 * <p>The cutoff is midnight UTC, {@code days} back (30 for every customer). The pattern is that of
 * {@link AuditRetentionService}: customers come from {@code core.list_tenants_for_sweep()}, every batch runs as
 * {@code retention_user} with the tenant bound ({@code AuditRetentionService.executeBatchDelete}), one customer
 * failing is logged and the next one runs ({@code AuditRetentionService.runSweep}), and each table and customer
 * records a {@code core.retention_run} row through {@link RetentionRunStore}.
 */
@Service
public class AiChatRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AiChatRetentionService.class);

    public static final String TARGET_AI_MESSAGE = "core.ai_message";
    public static final String TARGET_AI_ACTION_INTENT = "core.ai_action_intent";
    public static final String TARGET_AI_SESSION = "core.ai_session";

    /** The order the tables are swept in, for every customer. */
    public static final List<String> TARGETS = List.of(TARGET_AI_MESSAGE, TARGET_AI_ACTION_INTENT, TARGET_AI_SESSION);

    public static final int DEFAULT_RETENTION_DAYS = 30;
    public static final int MINIMUM_RETENTION_DAYS = 1;

    // Each names the tenant as well as relying on RLS: defence in depth, and the planner uses the tenant-leading
    // indexes (tenant_id, created_at) of V230.
    private static final String DELETE_MESSAGE_BATCH_SQL = "DELETE FROM core.ai_message WHERE id IN ("
            + "SELECT id FROM core.ai_message WHERE tenant_id = ? AND created_at < ? ORDER BY created_at ASC LIMIT ?)";

    private static final String DELETE_INTENT_BATCH_SQL = "DELETE FROM core.ai_action_intent WHERE id IN ("
            + "SELECT id FROM core.ai_action_intent WHERE tenant_id = ? AND created_at < ?"
            + " ORDER BY created_at ASC LIMIT ?)";

    private static final String DELETE_SESSION_BATCH_SQL = "DELETE FROM core.ai_session WHERE id IN ("
            + "SELECT id FROM core.ai_session WHERE tenant_id = ? AND COALESCE(last_message_at, created_at) < ?"
            + " ORDER BY COALESCE(last_message_at, created_at) ASC LIMIT ?)";

    private static final String COUNT_MESSAGE_SQL =
            "SELECT count(*) FROM core.ai_message WHERE tenant_id = ? AND created_at < ?";

    private static final String COUNT_INTENT_SQL =
            "SELECT count(*) FROM core.ai_action_intent WHERE tenant_id = ? AND created_at < ?";

    private static final String COUNT_SESSION_SQL =
            "SELECT count(*) FROM core.ai_session WHERE tenant_id = ? AND COALESCE(last_message_at, created_at) < ?";

    private final DataSource retentionDataSource;
    private final JdbcTemplate retentionJdbcTemplate;
    private final RetentionRunStore runs;
    private final int retentionDays;
    private final int batchSize;
    private final Clock clock;

    public AiChatRetentionService(
            @Qualifier("retentionDataSource") DataSource retentionDataSource,
            @Qualifier("retentionJdbcTemplate") JdbcTemplate retentionJdbcTemplate,
            RetentionRunStore runs,
            @Value("${worker.ai-retention.days:30}") int retentionDays,
            @Value("${worker.ai-retention.batch-size:500}") int batchSize,
            @Autowired(required = false) Clock clock) {
        if (retentionDays < MINIMUM_RETENTION_DAYS) {
            throw new IllegalArgumentException(
                    "worker.ai-retention.days must be at least " + MINIMUM_RETENTION_DAYS + ", was " + retentionDays);
        }
        this.retentionDataSource = Objects.requireNonNull(retentionDataSource, "retentionDataSource must not be null");
        this.retentionJdbcTemplate =
                Objects.requireNonNull(retentionJdbcTemplate, "retentionJdbcTemplate must not be null");
        this.runs = Objects.requireNonNull(runs, "runs must not be null");
        this.retentionDays = retentionDays;
        this.batchSize = batchSize > 0 ? batchSize : 500;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /** Every customer to sweep, from {@code core.list_tenants_for_sweep()}, with no tenant bound. */
    public List<UUID> listTenantsForSweep() {
        return retentionJdbcTemplate.query("SELECT tenant_id FROM core.list_tenants_for_sweep()", (rs, rowNum) ->
                (UUID) rs.getObject("tenant_id"));
    }

    /** Sweeps every customer; one failing is logged and the next one runs. A dry run counts and deletes nothing. */
    public List<RetentionRun> runSweep(boolean isDryRun) {
        List<RetentionRun> allRuns = new ArrayList<>();
        for (UUID tenantId : listTenantsForSweep()) {
            try {
                allRuns.addAll(sweepTenant(tenantId, isDryRun));
            } catch (Exception e) {
                log.error("Failed AI chat retention sweep for tenant {}: {}", tenantId, e.getMessage(), e);
            }
        }
        return allRuns;
    }

    /** Sweeps the three chat tables of one customer, in order, under that customer's tenant. */
    public List<RetentionRun> sweepTenant(UUID tenantId, boolean isDryRun) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        boolean wasBound = TenantContext.isBound();
        try {
            TenantContext.set(tenantId);
            List<RetentionRun> tenantRuns = new ArrayList<>(TARGETS.size());
            for (String target : TARGETS) {
                RetentionRun run = sweepTarget(tenantId, target, isDryRun);
                log.info(
                        "AI chat retention: tenant {} {} {} row(s) {}",
                        tenantId,
                        target,
                        run.getRowsDeleted(),
                        isDryRun ? "would be deleted" : "deleted");
                tenantRuns.add(run);
            }
            return tenantRuns;
        } finally {
            if (!wasBound) {
                TenantContext.clear();
            }
        }
    }

    /** Sweeps one chat table of one customer and records the run. */
    public RetentionRun sweepTarget(UUID tenantId, String targetTable, boolean isDryRun) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(targetTable, "targetTable must not be null");
        String deleteSql = deleteSql(targetTable);
        String countSql = countSql(targetTable);

        LocalDate cutoffDate = calculateCutoffDate();
        Instant cutoffInstant = cutoffInstant(cutoffDate);

        // As AuditRetentionService.sweepTarget: a RUNNING run of an earlier day is closed as PARTIAL, an
        // interrupted run of the same kind for the same cutoff is resumed, and a dry run never takes over a real one.
        runs.closeStale(tenantId, targetTable, cutoffDate, clock.instant());
        Optional<RetentionRun> existing = runs.findRunning(tenantId, targetTable, cutoffDate, isDryRun);

        RetentionRun run;
        if (existing.isPresent()) {
            run = existing.get();
            log.info("Resuming AI chat retention run {} for tenant {} on {}", run.getId(), tenantId, targetTable);
        } else {
            run = runs.insert(new RetentionRun(
                    tenantId, targetTable, cutoffDate, 0L, isDryRun, "RUNNING", clock.instant(), null));
        }

        try {
            if (isDryRun) {
                run.setRowsDeleted(countRows(countSql, tenantId, cutoffInstant));
            } else {
                long totalDeleted = run.getRowsDeleted();
                while (true) {
                    int deleted = executeBatchDelete(deleteSql, tenantId, cutoffInstant, batchSize);
                    totalDeleted += deleted;
                    run.setRowsDeleted(totalDeleted);
                    runs.update(run, clock.instant());
                    if (deleted < batchSize) {
                        break;
                    }
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

    /** Today in UTC, {@code days} back. Rows older than the start of this date are deleted. */
    public LocalDate calculateCutoffDate() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return today.minusDays(retentionDays);
    }

    /** The start of the cutoff date, midnight UTC. */
    public static Instant cutoffInstant(LocalDate cutoffDate) {
        return cutoffDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    private static String deleteSql(String targetTable) {
        return switch (targetTable) {
            case TARGET_AI_MESSAGE -> DELETE_MESSAGE_BATCH_SQL;
            case TARGET_AI_ACTION_INTENT -> DELETE_INTENT_BATCH_SQL;
            case TARGET_AI_SESSION -> DELETE_SESSION_BATCH_SQL;
            default -> throw new IllegalArgumentException("Unknown target table: " + targetTable);
        };
    }

    private static String countSql(String targetTable) {
        return switch (targetTable) {
            case TARGET_AI_MESSAGE -> COUNT_MESSAGE_SQL;
            case TARGET_AI_ACTION_INTENT -> COUNT_INTENT_SQL;
            case TARGET_AI_SESSION -> COUNT_SESSION_SQL;
            default -> throw new IllegalArgumentException("Unknown target table: " + targetTable);
        };
    }

    // AuditRetentionService.executeBatchDelete: one batch, one transaction, the tenant bound on the connection.
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
            throw new IllegalStateException("Failed to execute AI chat retention batch delete: " + e.getMessage(), e);
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
            throw new IllegalStateException("Failed to count rows for AI chat retention: " + e.getMessage(), e);
        }
    }
}
