package com.infinevo.core.notification;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Notifications (W-20.1) — and the one method every module calls to notify anyone
 * ({@code 12-core-contracts.md:105}).
 *
 * <p>Composition happens here, in {@code app}; delivery is {@code W-20.2}'s, in {@code worker}. The
 * {@value #QUEUE} queue joins them — {@code 09-build-order.md:202}, "composed in app, sent by worker".
 * Nothing in this ticket sends an email.
 */
public interface NotificationService {

    /** The Storage Queue an email notification's id is put on (the fourth beside payrun, import, report). */
    String QUEUE = "notification";

    /** Data key: where to send the email when the recipient is not an employee yet — an invitation. */
    String RECIPIENT_EMAIL = "recipient_email";

    /** Data key, optional: what the notification is about, e.g. {@code leave_request:<id>}. */
    String SUBJECT_REF = "subject_ref";

    /**
     * Renders the event's templates and writes one notification per channel that can reach the
     * recipient: in-app when there is an employee, email when there is an address — the
     * {@value #RECIPIENT_EMAIL} value, or else the employee's work email. The email is queued for
     * delivery once this transaction commits.
     *
     * @param recipientEmployeeId a live employee in the bound tenant, or {@code null} when
     *     {@value #RECIPIENT_EMAIL} is supplied
     * @param data a value for every placeholder the event's templates use ({@link NotificationEvent})
     * @return the ids written, in channel order
     * @throws TemplateMissingException the tenant has no active template for a channel — an error, never
     *     a silent skip (spec section 7)
     * @throws TemplateRenderer.MissingValueException a placeholder has no value; nothing is written
     */
    List<UUID> compose(NotificationEvent event, UUID recipientEmployeeId, Map<String, Object> data);

    /** The caller's in-app notifications, newest first. Empty while the caller is linked to no employee. */
    Page<NotificationResponse> mine(boolean unreadOnly, Pageable pageable);

    /** Marks one of the caller's in-app notifications read. Someone else's is not found. */
    NotificationResponse markRead(UUID id);

    /** The request cannot be applied. Maps to {@code 400} with per-field detail. */
    class ValidationException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Map<String, String> fieldErrors;

        public ValidationException(Map<String, String> fieldErrors) {
            super("The request was not valid: " + fieldErrors);
            this.fieldErrors = Map.copyOf(fieldErrors);
        }

        public Map<String, String> fieldErrors() {
            return fieldErrors;
        }
    }

    /** No such notification of the caller's in the bound tenant. Maps to {@code 404}. */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(UUID id) {
            super("No notification " + id + " for you in this tenant");
        }
    }

    /** The tenant has no active template for an event on a channel it must use. */
    class TemplateMissingException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public TemplateMissingException(NotificationEvent event, Channel channel) {
            super("No active " + channel + " template for " + event + " in this tenant");
        }
    }
}
