package com.infinevo.worker.scheduler;

import com.infinevo.payroll.scheduled.ScheduledEarningService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * W-73.6 §4: nightly, under cluster lock, materialises the current period's scheduled earnings for
 * every tenant — the safety net for a run created before the schedule was added, which
 * {@code PayRunServiceImpl.create} therefore never saw. Writing is idempotent on
 * {@code (scheduled_earning_id, period)}, so a night that finds nothing new writes nothing.
 */
@Component
public class ScheduledEarningJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledEarningJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final ScheduledEarningService scheduledEarningService;

    public ScheduledEarningJob(JdbcTemplate jdbcTemplate, ScheduledEarningService scheduledEarningService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.scheduledEarningService =
                Objects.requireNonNull(scheduledEarningService, "scheduledEarningService must not be null");
    }

    @Scheduled(cron = "${infinevo.scheduler.scheduled-earning.cron:0 30 1 * * *}", zone = "UTC")
    @SchedulerLock(name = "ScheduledEarningJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        log.info("Starting scheduled earning materialisation under cluster lock");
        int written = execute(YearMonth.from(LocalDate.now(ZoneOffset.UTC)));
        log.info("Finished scheduled earning materialisation: {} pay input(s) written", written);
    }

    /** Every tenant in turn, each bound for its own call; one tenant's failure does not stop the rest. */
    public int execute(YearMonth period) {
        TenantContext.clear();
        List<UUID> tenantIds = jdbcTemplate.query(
                "SELECT tenant_id FROM core.list_tenants_for_sweep()",
                (rs, rowNum) -> UUID.fromString(rs.getString("tenant_id")));

        int written = 0;
        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.set(tenantId);
                written += scheduledEarningService.materialise(period);
            } catch (Exception e) {
                log.error("Error materialising scheduled earnings for tenant {} in {}", tenantId, period, e);
            } finally {
                TenantContext.clear();
            }
        }
        return written;
    }
}
