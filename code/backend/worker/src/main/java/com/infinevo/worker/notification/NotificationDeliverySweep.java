package com.infinevo.worker.notification;

import com.infinevo.core.notification.Channel;
import com.infinevo.core.notification.NotificationRepository;
import com.infinevo.core.notification.NotificationStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The outbox half of email delivery (W-20.2). The queue message is a nudge; the {@code QUEUED} row is
 * the promise. Every pass, per tenant:
 *
 * <ol>
 *   <li>claims left {@code SENDING} by a crash for longer than {@code stale-claim} go back to
 *       {@code QUEUED} — sent at least once, never lost;
 *   <li>emails whose retry time has come are tried again;
 *   <li>emails queued more than {@code grace} ago with no retry pending — their message never reached
 *       the queue (no producer in {@code app}, the queue down), or was lost on it — are sent.
 * </ol>
 *
 * <p>Sending goes through {@link NotificationDeliveryConsumer#deliver}, so the claim that makes a queue
 * redelivery harmless makes a sweep overlapping the queue harmless too.
 *
 * <p>{@code @Scheduled} with {@code @SchedulerLock}, the house pattern ({@code W-52}), so one replica
 * sweeps at a time; tenants come from {@code core.list_tenants_for_sweep()}, each then bound for RLS.
 */
@Component
public class NotificationDeliverySweep {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliverySweep.class);

    private final JdbcTemplate jdbcTemplate;
    private final NotificationRepository notifications;
    private final NotificationDeliveryConsumer consumer;
    private final TransactionTemplate transactions;
    private final Duration staleClaim;
    private final Duration grace;
    private final int batchSize;
    private final Clock clock;

    @Autowired
    public NotificationDeliverySweep(
            JdbcTemplate jdbcTemplate,
            NotificationRepository notifications,
            NotificationDeliveryConsumer consumer,
            PlatformTransactionManager transactionManager,
            @Value("${worker.notification.stale-claim:PT10M}") Duration staleClaim,
            @Value("${worker.notification.sweep-grace:PT2M}") Duration grace,
            @Value("${worker.notification.sweep-batch:50}") int batchSize) {
        this(
                jdbcTemplate,
                notifications,
                consumer,
                transactionManager,
                staleClaim,
                grace,
                batchSize,
                Clock.systemUTC());
    }

    NotificationDeliverySweep(
            JdbcTemplate jdbcTemplate,
            NotificationRepository notifications,
            NotificationDeliveryConsumer consumer,
            PlatformTransactionManager transactionManager,
            Duration staleClaim,
            Duration grace,
            int batchSize,
            Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.notifications = Objects.requireNonNull(notifications, "notifications must not be null");
        this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.staleClaim = Objects.requireNonNull(staleClaim, "staleClaim must not be null");
        this.grace = Objects.requireNonNull(grace, "grace must not be null");
        this.batchSize = Math.max(1, batchSize);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(
            fixedDelayString = "${worker.notification.sweep-interval:PT1M}",
            initialDelayString = "${worker.notification.sweep-interval:PT1M}")
    @SchedulerLock(name = "notification_delivery_sweep", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void sweep() {
        List<UUID> tenants = jdbcTemplate.query("SELECT tenant_id FROM core.list_tenants_for_sweep()", (rs, rowNum) ->
                (UUID) rs.getObject("tenant_id"));
        int sent = 0;
        for (UUID tenantId : tenants) {
            try {
                sent += sweepTenant(tenantId);
            } catch (RuntimeException e) {
                log.error("Notification delivery sweep failed for tenant {}", tenantId, e);
            }
        }
        if (sent > 0) {
            log.info("Notification delivery sweep sent {} email(s)", sent);
        }
    }

    /** One tenant's pass. Returns how many emails this pass sent. */
    int sweepTenant(UUID tenantId) {
        boolean wasBound = TenantContext.isBound();
        try {
            TenantContext.set(tenantId);
            Instant now = clock.instant();
            Integer released = transactions.execute(
                    status -> notifications.releaseStaleClaims(tenantId, now.minus(staleClaim), now));
            if (released != null && released > 0) {
                log.warn("Released {} stale delivery claim(s) in tenant {}", released, tenantId);
            }
            List<UUID> due = notifications.findDueForDelivery(
                    tenantId,
                    Channel.EMAIL,
                    NotificationStatus.QUEUED,
                    now.minus(grace),
                    now,
                    PageRequest.of(0, batchSize));
            int sent = 0;
            for (UUID id : due) {
                if (consumer.deliver(tenantId, id)) {
                    sent++;
                }
            }
            return sent;
        } finally {
            if (!wasBound) {
                TenantContext.clear();
            }
        }
    }
}
