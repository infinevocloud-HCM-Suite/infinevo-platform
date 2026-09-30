package com.infinevo.core.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One notification to one recipient on one channel (W-20.1) — {@code core.notification},
 * {@code V039__notification.sql}.
 *
 * <p><strong>The rendered text is stored, never re-rendered.</strong> A template edited next month must
 * not change what an employee was told last month — the reasoning of {@code W-18.2}'s stamp — and
 * {@link #getTemplateId()} says which version produced it.
 *
 * <p>For email the row is also the outbox: it is written {@code QUEUED}, and {@code W-20.2} sends what
 * is still {@code QUEUED} whether or not the queue message that nudged it arrived.
 */
@Entity
@Table(
        name = "notification",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_notification_tenant_recipient_read",
                    columnList = "tenant_id, recipient_employee_id, read_at"),
            @Index(name = "idx_notification_tenant_status_queued", columnList = "tenant_id, status, queued_at"),
            @Index(name = "idx_notification_tenant_template", columnList = "tenant_id, template_id")
        })
public class Notification {

    public static final String ACTOR_SYSTEM = "system";

    /**
     * An invitation email's single-use token (W-24.2): 64 lowercase hex characters after {@code token=}. It
     * is a bearer credential, so the stored copy keeps it only while the email is waiting to be sent.
     */
    private static final Pattern INVITATION_TOKEN = Pattern.compile("token=[0-9a-f]{64}");

    public static final String REDACTED_TOKEN = "token=[redacted]";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "recipient_employee_id", updatable = false)
    private UUID recipientEmployeeId;

    @Column(name = "recipient_email", length = 255, updatable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 64, updatable = false)
    private NotificationEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16, updatable = false)
    private Channel channel;

    @Column(name = "subject", length = 255, updatable = false)
    private String subject;

    /**
     * Written once at compose time. The one later change is {@link #redactSecrets()}, which removes a
     * credential from an invitation email once it has been delivered or given up on.
     */
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "template_id", updatable = false)
    private UUID templateId;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "subject_ref", length = 128, updatable = false)
    private String subjectRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    /** Delivery attempts made (W-20.2); counted when a delivery claims the row. */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** When a transient failure may be tried again; the delivery sweep waits for it. */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** Why the last attempt failed. Never the recipient's address. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    /** When the provider accepted the email. */
    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() {}

    public Notification(
            UUID tenantId,
            UUID recipientEmployeeId,
            String recipientEmail,
            NotificationEvent event,
            Channel channel,
            String subject,
            String body,
            NotificationStatus status,
            UUID templateId,
            Instant queuedAt,
            String subjectRef,
            String actor) {
        this.tenantId = tenantId;
        this.recipientEmployeeId = recipientEmployeeId;
        this.recipientEmail = recipientEmail;
        this.event = event;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.templateId = templateId;
        this.queuedAt = queuedAt;
        this.subjectRef = subjectRef;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** First read wins; reading it again changes nothing. */
    void markRead(Instant when, String actor) {
        if (readAt == null) {
            readAt = when;
            updatedBy = actor;
            updatedAt = when;
        }
    }

    /** The provider accepted it. */
    public void markSent(Instant when, String actor) {
        redactSecrets();
        this.status = NotificationStatus.SENT;
        this.sentAt = when;
        this.nextAttemptAt = null;
        this.lastError = null;
        this.updatedAt = when;
        this.updatedBy = actor;
    }

    /** Given up on — the dead letter a human can see, with the reason. */
    public void markFailed(Instant when, String reason, String actor) {
        redactSecrets();
        this.status = NotificationStatus.FAILED;
        this.nextAttemptAt = null;
        this.lastError = truncate(reason);
        this.updatedAt = when;
        this.updatedBy = actor;
    }

    /** A transient failure: back to the outbox, to be tried again from {@code next}. */
    public void retryAt(Instant next, String reason, Instant when, String actor) {
        this.status = NotificationStatus.QUEUED;
        this.nextAttemptAt = next;
        this.lastError = truncate(reason);
        this.updatedAt = when;
        this.updatedBy = actor;
    }

    /**
     * Removes an invitation token from the stored body. The outbox row is the only way the text reaches
     * the delivery worker, so it holds the token until then; once the email is sent or has failed for
     * good nothing needs it, and the row keeps the link without the credential.
     */
    void redactSecrets() {
        if (body != null
                && (event == NotificationEvent.USER_INVITATION || event == NotificationEvent.EMPLOYEE_INVITATION)) {
            body = INVITATION_TOKEN.matcher(body).replaceAll(REDACTED_TOKEN);
        }
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getRecipientEmployeeId() {
        return recipientEmployeeId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public NotificationEvent getEvent() {
        return event;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public String getSubjectRef() {
        return subjectRef;
    }
}
