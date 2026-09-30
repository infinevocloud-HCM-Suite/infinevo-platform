package com.infinevo.worker.leave;

import com.infinevo.core.leave.LeaveAccrualService;
import com.infinevo.core.leave.LeaveResetService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
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
 * Scheduled job to run leave accrual and reset processing under cluster lock (W-16.2, spec section 4 &amp; 13).
 */
@Component
public class LeaveAccrualJob {

    private static final Logger log = LoggerFactory.getLogger(LeaveAccrualJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final LeaveAccrualService accrualService;
    private final LeaveResetService resetService;

    public LeaveAccrualJob(
            JdbcTemplate jdbcTemplate, LeaveAccrualService accrualService, LeaveResetService resetService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.accrualService = Objects.requireNonNull(accrualService, "accrualService must not be null");
        this.resetService = Objects.requireNonNull(resetService, "resetService must not be null");
    }

    @Scheduled(cron = "${infinevo.scheduler.leave-accrual.cron:0 0 1 * * *}", zone = "UTC")
    @SchedulerLock(name = "LeaveAccrualJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        log.info("Starting leave accrual and reset job under cluster lock");
        execute(LocalDate.now(ZoneOffset.UTC));
        log.info("Finished leave accrual and reset job");
    }

    public void execute(LocalDate asOf) {
        TenantContext.clear();
        List<UUID> tenantIds = jdbcTemplate.query(
                "SELECT tenant_id FROM core.list_tenants_for_sweep()",
                (rs, rowNum) -> UUID.fromString(rs.getString("tenant_id")));

        for (UUID tenantId : tenantIds) {
            try {
                TenantContext.set(tenantId);
                accrualService.accrueAll(tenantId, asOf);
                resetService.resetAll(tenantId, asOf);
            } catch (Exception e) {
                log.error("Error running leave accrual/reset for tenant {}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
