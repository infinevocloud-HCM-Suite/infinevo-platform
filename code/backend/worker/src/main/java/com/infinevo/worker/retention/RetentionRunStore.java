package com.infinevo.worker.retention;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Where the retention sweep records its runs (W-22.2): {@code core.retention_run}, written as
 * {@code retention_user}.
 *
 * <p><strong>Why not the JPA repository.</strong> JPA writes through the worker's own pool,
 * {@code worker_user}, which is a member of {@code app_user} — and {@code V094} revokes
 * {@code UPDATE} on {@code core.retention_run} from {@code app_user}, because the application must
 * not be able to rewrite the record of what was deleted. The spec gives {@code SELECT, INSERT,
 * UPDATE} on the table to {@code retention_user} alone, so the sweep writes its own record on its
 * own pool. {@link RetentionRunRepository} stays for reading.
 *
 * <p>Every statement binds the run's tenant in its own transaction: {@code retention_user} is
 * subject to the {@code tenant_isolation} policy like any other role.
 */
@Component
public class RetentionRunStore {

    private static final String BIND_TENANT = "SELECT set_config('app.current_tenant_id', ?, true)";

    /** A dry run never resumes a real run, nor a real run a dry one. */
    private static final String FIND_RUNNING = "SELECT id, rows_deleted, started_at FROM core.retention_run"
            + " WHERE tenant_id = ? AND target_table = ? AND cutoff_date = ? AND is_dry_run = ?"
            + " AND status = 'RUNNING' ORDER BY started_at DESC LIMIT 1";

    private static final String INSERT = "INSERT INTO core.retention_run (id, tenant_id, target_table,"
            + " cutoff_date, rows_deleted, is_dry_run, status, started_at, finished_at, created_by, updated_by)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'retention', 'retention')";

    private static final String CLOSE_STALE = "UPDATE core.retention_run SET status = 'PARTIAL',"
            + " finished_at = ?, updated_at = ?, updated_by = 'retention'"
            + " WHERE tenant_id = ? AND target_table = ? AND status = 'RUNNING' AND cutoff_date < ?";

    private static final String UPDATE = "UPDATE core.retention_run SET rows_deleted = ?, status = ?,"
            + " finished_at = ?, updated_at = ?, updated_by = 'retention' WHERE id = ? AND tenant_id = ?";

    private final DataSource dataSource;

    public RetentionRunStore(@Qualifier("retentionDataSource") DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    /** The latest unfinished run for this tenant, table, cutoff and kind, to resume. */
    public Optional<RetentionRun> findRunning(UUID tenantId, String targetTable, LocalDate cutoffDate, boolean dryRun) {
        return inTenant(tenantId, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(FIND_RUNNING)) {
                ps.setObject(1, tenantId);
                ps.setString(2, targetTable);
                ps.setDate(3, Date.valueOf(cutoffDate));
                ps.setBoolean(4, dryRun);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    RetentionRun run = new RetentionRun(
                            tenantId,
                            targetTable,
                            cutoffDate,
                            rs.getLong("rows_deleted"),
                            dryRun,
                            "RUNNING",
                            rs.getTimestamp("started_at").toInstant(),
                            null);
                    run.setId(rs.getObject("id", UUID.class));
                    return Optional.of(run);
                }
            }
        });
    }

    /** Writes a new run and gives it its id. */
    public RetentionRun insert(RetentionRun run) {
        if (run.getId() == null) {
            run.setId(UUID.randomUUID());
        }
        inTenant(run.getTenantId(), conn -> {
            try (PreparedStatement ps = conn.prepareStatement(INSERT)) {
                ps.setObject(1, run.getId());
                ps.setObject(2, run.getTenantId());
                ps.setString(3, run.getTargetTable());
                ps.setDate(4, Date.valueOf(run.getCutoffDate()));
                ps.setLong(5, run.getRowsDeleted());
                ps.setBoolean(6, run.isDryRun());
                ps.setString(7, run.getStatus());
                ps.setTimestamp(8, Timestamp.from(run.getStartedAt()));
                ps.setTimestamp(9, run.getFinishedAt() == null ? null : Timestamp.from(run.getFinishedAt()));
                return ps.executeUpdate();
            }
        });
        return run;
    }

    /** Records progress or the outcome: rows deleted so far, status, and when it finished. */
    public void update(RetentionRun run, Instant now) {
        inTenant(run.getTenantId(), conn -> {
            try (PreparedStatement ps = conn.prepareStatement(UPDATE)) {
                ps.setLong(1, run.getRowsDeleted());
                ps.setString(2, run.getStatus());
                ps.setTimestamp(3, run.getFinishedAt() == null ? null : Timestamp.from(run.getFinishedAt()));
                ps.setTimestamp(4, Timestamp.from(now));
                ps.setObject(5, run.getId());
                ps.setObject(6, run.getTenantId());
                return ps.executeUpdate();
            }
        });
        run.setUpdatedAt(now);
    }

    /** Closes runs left RUNNING under an earlier cutoff as PARTIAL; they can no longer be resumed. */
    public int closeStale(UUID tenantId, String targetTable, LocalDate currentCutoff, Instant now) {
        return inTenant(tenantId, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(CLOSE_STALE)) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setTimestamp(2, Timestamp.from(now));
                ps.setObject(3, tenantId);
                ps.setString(4, targetTable);
                ps.setDate(5, Date.valueOf(currentCutoff));
                return ps.executeUpdate();
            }
        });
    }

    private <T> T inTenant(UUID tenantId, SqlWork<T> work) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement bind = conn.prepareStatement(BIND_TENANT)) {
                    bind.setString(1, tenantId.toString());
                    bind.execute();
                }
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not record the retention run: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection conn) throws SQLException;
    }
}
