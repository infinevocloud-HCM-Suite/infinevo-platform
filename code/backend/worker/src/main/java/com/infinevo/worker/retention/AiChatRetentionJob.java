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
 * Nightly job deleting AI helper chats older than 30 days, for every customer (W-114.10).
 *
 * <p>Protected by ShedLock so one worker replica runs it. Its lock name differs from the audit job's
 * ({@link AuditRetentionJob}) and it starts half an hour later.
 *
 * <p><strong>Real deletion by default, unlike the audit job.</strong> The audit job defaults to a dry run because a
 * deleted audit row is a legal record that cannot be recovered. A chat is not a record anyone must keep, and the
 * founder rule is that chats are really deleted before the helper is switched on, so a deployment that forgot a
 * setting must delete, not keep. {@code WORKER_AI_RETENTION_DRY_RUN=true} stops deleting at once.
 */
@Component
public class AiChatRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AiChatRetentionJob.class);

    private final AiChatRetentionService retentionService;
    private final boolean dryRun;

    public AiChatRetentionJob(
            AiChatRetentionService retentionService, @Value("${worker.ai-retention.dry-run:false}") boolean dryRun) {
        this.retentionService = Objects.requireNonNull(retentionService, "retentionService must not be null");
        this.dryRun = dryRun;
    }

    @Scheduled(cron = "${worker.ai-retention.cron:0 30 2 * * *}")
    @SchedulerLock(name = "ai_chat_retention_sweep", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    public void runRetentionSweep() {
        log.info("Starting AI chat retention sweep with cluster lock ({})", dryRun ? "DRY RUN" : "deleting rows");
        try {
            List<RetentionRun> runs = retentionService.runSweep(dryRun);
            long rows = runs.stream().mapToLong(RetentionRun::getRowsDeleted).sum();
            log.info(
                    "AI chat retention sweep completed: {} run(s), {} row(s) {}",
                    runs.size(),
                    rows,
                    dryRun ? "would be deleted" : "deleted");
        } catch (Exception e) {
            log.error("AI chat retention sweep failed: {}", e.getMessage(), e);
        }
    }
}
