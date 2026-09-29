package com.infinevo.worker.notification;

import com.infinevo.core.notification.Notification;
import com.infinevo.core.notification.NotificationRepository;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.queue.QueueConsumer;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends email notifications (W-20.2): one attempt per queue message or sweep pass, never a loop.
 *
 * <pre>
 * claim        QUEUED -> SENDING, counting the attempt; 0 rows means someone else has it or it is done
 * send         DeliveryClient, outside any transaction
 * record       SENT with sent_at
 *              transient failure: back to QUEUED with next_attempt_at, doubling from the initial
 *                backoff, until max-attempts; then FAILED with the reason
 *              permanent failure: FAILED with the reason
 * </pre>
 *
 * <p><strong>Idempotent under concurrent redelivery</strong> (D-50: Storage Queue may deliver a message
 * twice, even at once). The claim is one conditional {@code UPDATE}, so exactly one delivery sends.
 *
 * <p><strong>Retries wait minutes, not milliseconds.</strong> A provider outage outlasts any in-thread
 * sleep, so a transient failure goes back to the outbox with a time, and
 * {@link NotificationDeliverySweep} tries again when it comes. The consumer thread is never held.
 *
 * <p>A failure of this worker itself — the database unreachable — is thrown, so the queue redelivers
 * the message. Delivery failures are recorded on the row and the message is done with.
 */
@Component
public class NotificationDeliveryConsumer implements QueueConsumer<String> {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryConsumer.class);

    public static final String QUEUE_NAME = "notification";

    private static final String ACTOR = "worker";

    private final NotificationRepository notifications;
    private final DeliveryClient deliveryClient;
    private final TransactionTemplate transactions;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Clock clock;

    @Autowired
    public NotificationDeliveryConsumer(
            NotificationRepository notifications,
            DeliveryClient deliveryClient,
            PlatformTransactionManager transactionManager,
            @Value("${worker.notification.max-attempts:5}") int maxAttempts,
            @Value("${worker.notification.initial-backoff:PT1M}") Duration initialBackoff) {
        this(notifications, deliveryClient, transactionManager, maxAttempts, initialBackoff, Clock.systemUTC());
    }

    NotificationDeliveryConsumer(
            NotificationRepository notifications,
            DeliveryClient deliveryClient,
            PlatformTransactionManager transactionManager,
            int maxAttempts,
            Duration initialBackoff,
            Clock clock) {
        this.notifications = Objects.requireNonNull(notifications, "notifications must not be null");
        this.deliveryClient = Objects.requireNonNull(deliveryClient, "deliveryClient must not be null");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoff = initialBackoff == null || initialBackoff.isNegative() || initialBackoff.isZero()
                ? Duration.ofMinutes(1)
                : initialBackoff;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public String getQueueName() {
        return QUEUE_NAME;
    }

    @Override
    public void onMessage(QueueMessage<String> message) {
        Objects.requireNonNull(message, "message must not be null");
        UUID tenantId = message.getTenantId();
        String payload = message.getPayload();
        UUID notificationId = UUID.fromString(payload == null || payload.isBlank() ? message.getJobId() : payload);

        if (message.getCorrelationId() != null) {
            MDC.put(MdcLoggingContext.CORRELATION_ID_KEY, message.getCorrelationId());
        }
        MDC.put(MdcLoggingContext.TENANT_ID_KEY, tenantId.toString());
        try {
            deliver(tenantId, notificationId);
        } finally {
            MDC.remove(MdcLoggingContext.CORRELATION_ID_KEY);
            MDC.remove(MdcLoggingContext.TENANT_ID_KEY);
        }
    }

    /**
     * One delivery attempt for one notification, from a queue message or the sweep.
     *
     * @return true if this call sent it
     */
    public boolean deliver(UUID tenantId, UUID notificationId) {
        boolean wasBound = TenantContext.isBound();
        try {
            TenantContext.set(tenantId);
            Integer claimed = transactions.execute(
                    status -> notifications.claimForDelivery(notificationId, tenantId, clock.instant()));
            if (claimed == null || claimed == 0) {
                log.info("Notification {} is not waiting to be sent; nothing to do", notificationId);
                return false;
            }
            Notification notification = notifications
                    .findByIdAndTenantId(notificationId, tenantId)
                    .orElseThrow(
                            () -> new IllegalStateException("Claimed notification " + notificationId + " vanished"));
            return attempt(notification);
        } finally {
            if (!wasBound) {
                TenantContext.clear();
            }
        }
    }

    private boolean attempt(Notification notification) {
        String address = notification.getRecipientEmail();
        if (address == null || address.isBlank()) {
            record(notification, n -> n.markFailed(clock.instant(), "no recipient address", ACTOR));
            log.error("Notification {} has no recipient address; marked FAILED", notification.getId());
            return false;
        }
        try {
            deliveryClient.sendEmail(address, notification.getSubject(), notification.getBody());
        } catch (DeliveryException e) {
            failed(notification, e.getMessage(), e.isTransient());
            return false;
        } catch (RuntimeException e) {
            // Unexpected, so not known to be final: retried like a transient failure, within the limit.
            log.warn("Notification {} hit an unexpected error while sending", notification.getId(), e);
            failed(notification, e.getClass().getSimpleName(), true);
            return false;
        }
        record(notification, n -> n.markSent(clock.instant(), ACTOR));
        log.info("Notification {} sent on attempt {}", notification.getId(), notification.getAttemptCount());
        return true;
    }

    private void failed(Notification notification, String reason, boolean isTransient) {
        int attempts = notification.getAttemptCount();
        if (!isTransient || attempts >= maxAttempts) {
            String why = isTransient ? "gave up after " + attempts + " attempts: " + reason : reason;
            record(notification, n -> n.markFailed(clock.instant(), why, ACTOR));
            log.error("Notification {} marked FAILED: {}", notification.getId(), why);
            return;
        }
        Instant next = clock.instant().plus(backoffAfter(attempts));
        record(notification, n -> n.retryAt(next, reason, clock.instant(), ACTOR));
        log.warn("Notification {} attempt {} failed; retrying from {}", notification.getId(), attempts, next);
    }

    /** initial, 2x, 4x … after the first, second, third attempt. */
    Duration backoffAfter(int attempts) {
        int doublings = Math.min(Math.max(attempts - 1, 0), 10);
        return initialBackoff.multipliedBy(1L << doublings);
    }

    private void record(Notification notification, Consumer<Notification> change) {
        transactions.executeWithoutResult(status -> {
            change.accept(notification);
            notifications.save(notification);
        });
    }
}
