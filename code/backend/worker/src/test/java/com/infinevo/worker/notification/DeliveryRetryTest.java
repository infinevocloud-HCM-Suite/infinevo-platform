package com.infinevo.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.notification.Channel;
import com.infinevo.core.notification.Notification;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationRepository;
import com.infinevo.core.notification.NotificationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * W-20.2 spec section 7 — a transient failure is retried with backoff, a permanent one dead-letters,
 * a success is never retried; plus the claim that makes a redelivered message harmless.
 *
 * <p>One attempt per call: a retry is a time on the row ({@code next_attempt_at}), taken up by the
 * delivery sweep, not a sleep in the consumer thread.
 */
class DeliveryRetryTest {

    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    private NotificationRepository repository;
    private DeliveryClient deliveryClient;
    private NotificationDeliveryConsumer consumer;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        deliveryClient = mock(DeliveryClient.class);
        consumer = new NotificationDeliveryConsumer(
                repository,
                deliveryClient,
                mock(PlatformTransactionManager.class),
                3,
                Duration.ofMinutes(1),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("A success is sent once and marked SENT with its time")
    void successIsSent() {
        Notification notification = claimed(1);

        assertThat(consumer.deliver(tenantId, notificationId)).isTrue();

        verify(deliveryClient).sendEmail("test@example.com", "Timesheet Reminder", "Please submit your timesheet");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(NOW);
        verify(repository).save(notification);
    }

    @Test
    @DisplayName("Already claimed, sent or failed: the redelivered message sends nothing")
    void unclaimedIsNotSent() {
        when(repository.claimForDelivery(notificationId, tenantId, NOW)).thenReturn(0);

        assertThat(consumer.deliver(tenantId, notificationId)).isFalse();

        verify(deliveryClient, never()).sendEmail(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("A transient failure goes back to QUEUED with the next attempt one backoff later")
    void transientFailureIsRetriedLater() {
        Notification notification = claimed(1);
        doThrow(DeliveryException.transientFailure("Brevo server error (503)"))
                .when(deliveryClient)
                .sendEmail(anyString(), anyString(), anyString());

        consumer.deliver(tenantId, notificationId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(notification.getLastError()).contains("503");
    }

    @Test
    @DisplayName("The backoff doubles: 1, 2, 4 minutes after the first, second, third attempt")
    void backoffDoubles() {
        assertThat(consumer.backoffAfter(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(consumer.backoffAfter(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(consumer.backoffAfter(3)).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    @DisplayName("A transient failure on the last allowed attempt dead-letters to FAILED, saying why")
    void transientFailureExhaustingAttemptsDeadLetters() {
        Notification notification = claimed(3);
        doThrow(DeliveryException.transientFailure("Brevo rate limit exceeded (429)"))
                .when(deliveryClient)
                .sendEmail(anyString(), anyString(), anyString());

        consumer.deliver(tenantId, notificationId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getLastError())
                .contains("gave up after 3 attempts")
                .contains("429");
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("A permanent failure dead-letters at once, without a retry")
    void permanentFailureDeadLetters() {
        Notification notification = claimed(1);
        doThrow(DeliveryException.permanentFailure("Brevo rejected email (400)"))
                .when(deliveryClient)
                .sendEmail(anyString(), anyString(), anyString());

        consumer.deliver(tenantId, notificationId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getNextAttemptAt()).isNull();
        assertThat(notification.getLastError()).contains("400");
    }

    @Test
    @DisplayName("No recipient address: FAILED, and the provider is never called")
    void noAddressFails() {
        Notification notification = claimed(1);
        ReflectionTestUtils.setField(notification, "recipientEmail", null);

        consumer.deliver(tenantId, notificationId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(deliveryClient, never()).sendEmail(any(), any(), any());
    }

    /** The row as the claim leaves it: SENDING, with the attempt counted. */
    private Notification claimed(int attemptCount) {
        Notification notification = new Notification(
                tenantId,
                UUID.randomUUID(),
                "test@example.com",
                NotificationEvent.TIMESHEET_REMINDER,
                Channel.EMAIL,
                "Timesheet Reminder",
                "Please submit your timesheet",
                NotificationStatus.SENDING,
                UUID.randomUUID(),
                NOW.minusSeconds(300),
                null,
                "system");
        ReflectionTestUtils.setField(notification, "id", notificationId);
        ReflectionTestUtils.setField(notification, "attemptCount", attemptCount);
        when(repository.claimForDelivery(eq(notificationId), eq(tenantId), any()))
                .thenReturn(1);
        when(repository.findByIdAndTenantId(notificationId, tenantId)).thenReturn(Optional.of(notification));
        return notification;
    }
}
