package com.infinevo.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.notification.Channel;
import com.infinevo.core.notification.NotificationRepository;
import com.infinevo.core.notification.NotificationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * W-20.2 — the outbox half of delivery: stale claims go back to the queue, and every due email is sent
 * through the same claim the queue consumer uses.
 */
class NotificationDeliverySweepTest {

    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    @Test
    @DisplayName("A pass releases stale claims, then sends every due email through the consumer's claim")
    void sweepReleasesStaleClaimsAndSendsDueEmails() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationDeliveryConsumer consumer = mock(NotificationDeliveryConsumer.class);
        NotificationDeliverySweep sweep = new NotificationDeliverySweep(
                mock(JdbcTemplate.class),
                repository,
                consumer,
                mock(PlatformTransactionManager.class),
                Duration.ofMinutes(10),
                Duration.ofMinutes(2),
                50,
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID tenantId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.findDueForDelivery(
                        eq(tenantId),
                        eq(Channel.EMAIL),
                        eq(NotificationStatus.QUEUED),
                        eq(NOW.minus(Duration.ofMinutes(2))),
                        eq(NOW),
                        any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(consumer.deliver(tenantId, first)).thenReturn(true);
        when(consumer.deliver(tenantId, second)).thenReturn(false);

        int sent = sweep.sweepTenant(tenantId);

        assertThat(sent).isEqualTo(1);
        verify(repository).releaseStaleClaims(tenantId, NOW.minus(Duration.ofMinutes(10)), NOW);
        verify(consumer).deliver(tenantId, first);
        verify(consumer).deliver(tenantId, second);
    }
}
