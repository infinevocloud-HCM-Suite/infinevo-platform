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
import java.time.LocalTime;
import java.util.UUID;

/**
 * A reminder rule configured per tenant (W-20.2) — {@code core.reminder_rule},
 * {@code V093__reminder_rule.sql}.
 *
 * <p>Consolidates the six legacy reminder tables across HRMS and Payroll into a single
 * tenant-scoped rule model. Evaluated by {@code ReminderEvaluator} on {@code worker}.
 */
@Entity
@Table(
        name = "reminder_rule",
        schema = "core",
        indexes = {
            @Index(name = "idx_reminder_rule_tenant_event_active", columnList = "tenant_id, event, is_active"),
            @Index(
                    name = "idx_reminder_rule_tenant_active_last_exec",
                    columnList = "tenant_id, is_active, last_executed_at")
        })
public class ReminderRule {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 64)
    private NotificationEvent event;

    @Column(name = "audience", nullable = false, length = 32)
    private String audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "anchor", nullable = false, length = 32)
    private Anchor anchor;

    @Column(name = "offset_days", nullable = false)
    private int offsetDays;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "send_at_local_time", nullable = false)
    private LocalTime sendAtLocalTime;

    @Column(name = "repeat_every_days")
    private Integer repeatEveryDays;

    @Column(name = "max_repeats")
    private Integer maxRepeats;

    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    @Column(name = "repeat_count", nullable = false)
    private int repeatCount = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected ReminderRule() {}

    public ReminderRule(
            UUID tenantId,
            NotificationEvent event,
            String audience,
            Anchor anchor,
            int offsetDays,
            Integer dayOfWeek,
            LocalTime sendAtLocalTime,
            Integer repeatEveryDays,
            Integer maxRepeats,
            String actor) {
        this.tenantId = tenantId;
        this.event = event;
        this.audience = audience;
        this.anchor = anchor;
        this.offsetDays = offsetDays;
        this.dayOfWeek = dayOfWeek;
        this.sendAtLocalTime = sendAtLocalTime;
        this.repeatEveryDays = repeatEveryDays;
        this.maxRepeats = maxRepeats;
        this.createdBy = actor != null ? actor : ACTOR_SYSTEM;
        this.updatedBy = actor != null ? actor : ACTOR_SYSTEM;
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

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public NotificationEvent getEvent() {
        return event;
    }

    public void setEvent(NotificationEvent event) {
        this.event = event;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
    }

    public int getOffsetDays() {
        return offsetDays;
    }

    public void setOffsetDays(int offsetDays) {
        this.offsetDays = offsetDays;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getSendAtLocalTime() {
        return sendAtLocalTime;
    }

    public void setSendAtLocalTime(LocalTime sendAtLocalTime) {
        this.sendAtLocalTime = sendAtLocalTime;
    }

    public Integer getRepeatEveryDays() {
        return repeatEveryDays;
    }

    public void setRepeatEveryDays(Integer repeatEveryDays) {
        this.repeatEveryDays = repeatEveryDays;
    }

    public Integer getMaxRepeats() {
        return maxRepeats;
    }

    public void setMaxRepeats(Integer maxRepeats) {
        this.maxRepeats = maxRepeats;
    }

    public Instant getLastExecutedAt() {
        return lastExecutedAt;
    }

    public void setLastExecutedAt(Instant lastExecutedAt) {
        this.lastExecutedAt = lastExecutedAt;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public void setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
