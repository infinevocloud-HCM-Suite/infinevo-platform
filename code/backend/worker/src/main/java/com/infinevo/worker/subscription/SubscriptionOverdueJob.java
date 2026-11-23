package com.infinevo.worker.subscription;

import com.infinevo.core.subscription.SubscriptionService;
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
 * Scheduled job to sweep active customer subscriptions past their current period end date (W-86).
 *
 * <p>Runs nightly at 01:00 UTC under cluster lock. Reads overdue tenant IDs using the SECURITY DEFINER function
 * {@code core.list_overdue_subscriptions(date)}, and marks each tenant PAST_DUE. Failures on individual tenants
 * are logged and do not halt processing of subsequent tenants.
 */
@Component
public class SubscriptionOverdueJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionOverdueJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final SubscriptionService subscriptionService;

    public SubscriptionOverdueJob(JdbcTemplate jdbcTemplate, SubscriptionService subscriptionService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService must not be null");
    }

    @Scheduled(cron = "${infinevo.scheduler.subscription-overdue.cron:0 0 1 * * *}", zone = "UTC")
    @SchedulerLock(name = "SubscriptionOverdueJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        log.info("Starting nightly subscription overdue sweep under cluster lock");
        execute(LocalDate.now(ZoneOffset.UTC));
        log.info("Finished nightly subscription overdue sweep");
    }

    public void execute(LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf must not be null");
        TenantContext.clear();
        List<UUID> tenantIds = jdbcTemplate.query(
                "SELECT tenant_id FROM core.list_overdue_subscriptions(?)",
                (rs, rowNum) -> UUID.fromString(rs.getString("tenant_id")),
                java.sql.Date.valueOf(asOf));

        for (UUID tenantId : tenantIds) {
            try {
                subscriptionService.markPastDue(tenantId, asOf);
            } catch (Exception e) {
                log.error("Error marking subscription past due for tenant {}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
