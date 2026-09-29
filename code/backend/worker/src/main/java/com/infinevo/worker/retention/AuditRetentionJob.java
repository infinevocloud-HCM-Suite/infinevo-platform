package com.infinevo.worker.retention;

import java.util.List;
import java.util.Objects;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job executing the multi-tenant audit and notification retention sweep (W-22.2).
 *
 * <p>Protected by ShedLock to ensure single execution across worker replicas.
 * Connects as {@code retention_user} and sweeps rows older than each tenant's configured window.
 *
 * <p><strong>Dry run by default (manager's review item B-2).</strong> Deleted audit rows cannot be
 * recovered (spec §10), so the first production sweep must be a dry run — one that reports what it
 * would delete without deleting it ({@code AuditRetentionService.runSweep(true)}). A cutoff bug found
 * only after a real sweep has already run is unrecoverable; found in a dry run's log, it costs nothing.
 * {@code worker.retention.dry-run} defaults to {@code true} for exactly that reason: an operator who
 * forgets to set it gets the safe behaviour, not the destructive one. Turning real deletion on is a
 * deliberate {@code WORKER_RETENTION_DRY_RUN=false}, made once the dry run's log has been read.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditRetentionService retentionService;
    private final boolean dryRun;

    public AuditRetentionJob(
            AuditRetentionService retentionService, @Value("${worker.retention.dry-run:true}") boolean dryRun) {
        this.retentionService = Objects.requireNonNull(retentionService, "retentionService must not be null");
        this.dryRun = dryRun;
    }

    @Scheduled(cron = "${worker.retention.cron:0 0 2 * * *}")
    @SchedulerLock(name = "audit_retention_sweep", lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
    public void runRetentionSweep() {
        log.info("Starting audit retention sweep with cluster lock ({})", dryRun ? "DRY RUN" : "deleting rows");
        try {
            List<RetentionRun> runs = retentionService.runSweep(dryRun);
            long rows = runs.stream().mapToLong(RetentionRun::getRowsDeleted).sum();
            log.info(
                    "Audit retention sweep completed successfully: {} run(s), {} row(s) {}",
                    runs.size(),
                    rows,
                    dryRun ? "would be deleted" : "deleted");
        } catch (Exception e) {
            log.error("Audit retention sweep failed: {}", e.getMessage(), e);
        }
    }
}
