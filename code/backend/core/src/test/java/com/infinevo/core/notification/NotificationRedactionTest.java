package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** An invitation token leaves the stored email once it has been delivered or given up on (W-24.2, B-5). */
class NotificationRedactionTest {

    private static final String TOKEN = "ab".repeat(32);
    private static final String BODY =
            "<p><a href=\"https://app.test/invitations/accept?token=" + TOKEN + "\">Accept the invitation</a></p>";

    private static Notification email(NotificationEvent event, String body) {
        return new Notification(
                UUID.randomUUID(),
                null,
                "someone@example.com",
                event,
                Channel.EMAIL,
                "subject",
                body,
                NotificationStatus.QUEUED,
                UUID.randomUUID(),
                Instant.now(),
                null,
                "system");
    }

    @Test
    @DisplayName("sent: the invitation token is replaced, the rest of the link kept")
    void sentInvitationIsRedacted() {
        Notification n = email(NotificationEvent.USER_INVITATION, BODY);
        assertThat(n.getBody()).contains(TOKEN);

        n.markSent(Instant.now(), "worker");

        assertThat(n.getBody())
                .doesNotContain(TOKEN)
                .contains("https://app.test/invitations/accept?" + Notification.REDACTED_TOKEN);
    }

    @Test
    @DisplayName("failed for good: redacted too; a transient retry keeps the token it still needs")
    void failedInvitationIsRedactedRetryIsNot() {
        Notification n = email(NotificationEvent.EMPLOYEE_INVITATION, BODY);
        n.retryAt(Instant.now().plusSeconds(60), "timeout", Instant.now(), "worker");
        assertThat(n.getBody()).contains(TOKEN);

        n.markFailed(Instant.now(), "rejected", "worker");
        assertThat(n.getBody()).doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("other events are stored exactly as rendered")
    void otherEventsUntouched() {
        Notification n = email(NotificationEvent.CREDENTIALS, BODY);
        n.markSent(Instant.now(), "worker");
        assertThat(n.getBody()).isEqualTo(BODY);
    }
}
