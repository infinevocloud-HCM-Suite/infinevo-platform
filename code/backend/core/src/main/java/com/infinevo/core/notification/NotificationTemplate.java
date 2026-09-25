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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One version of the wording for one event on one channel, in one tenant (W-20.1) —
 * {@code core.notification_template}, {@code V038__notification_template.sql}.
 *
 * <p>The template in force is the active row with the latest {@code effective_from} on or before today.
 * An edit is a new row from today, so the wording that produced an old notification is still on record.
 */
@Entity
@Table(
        name = "notification_template",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_notification_template_tenant_event_channel",
                    columnList = "tenant_id, event, channel, effective_from"),
            @Index(
                    name = "uk_notification_template_tenant_version",
                    columnList = "tenant_id, event, channel, locale, effective_from",
                    unique = true)
        })
public class NotificationTemplate {

    public static final String ACTOR_SYSTEM = "system";
    public static final String DEFAULT_LOCALE = "en";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 64, updatable = false)
    private NotificationEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16, updatable = false)
    private Channel channel;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "locale", nullable = false, length = 8, updatable = false)
    private String locale = DEFAULT_LOCALE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected NotificationTemplate() {}

    NotificationTemplate(
            UUID tenantId, NotificationEvent event, Channel channel, LocalDate effectiveFrom, String actor) {
        this.tenantId = tenantId;
        this.event = event;
        this.channel = channel;
        this.effectiveFrom = effectiveFrom;
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

    /** Package-private: only {@code NotificationTemplateServiceImpl} calls it, after validation. */
    void apply(String subject, String body, boolean active, String actor) {
        this.subject = subject;
        this.body = body;
        this.active = active;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
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

    public String getLocale() {
        return locale;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
