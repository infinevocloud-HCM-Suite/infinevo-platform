package com.infinevo.payroll.fbp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Flexible Benefit Plan configuration entity in schema payroll (W-27.1).
 * One row per tenant under PostgreSQL Row-Level Security.
 */
@Entity
@Table(
        name = "fbp",
        schema = "payroll",
        indexes = {@Index(name = "uk_fbp_tenant", columnList = "tenant_id", unique = true)})
public class FbpPlan {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "window_opens_on")
    private LocalDate windowOpensOn;

    @Column(name = "window_closes_on")
    private LocalDate windowClosesOn;

    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "notify_on_release", nullable = false)
    private boolean notifyOnRelease = true;

    @Column(name = "notify_on_lock", nullable = false)
    private boolean notifyOnLock = true;

    @Column(name = "reminder_days_before_close", columnDefinition = "smallint[]", nullable = false)
    private short[] reminderDaysBeforeClose = new short[] {5, 1};

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected FbpPlan() {}

    public FbpPlan(UUID tenantId, String actor) {
        this.tenantId = tenantId;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDate getWindowOpensOn() {
        return windowOpensOn;
    }

    public void setWindowOpensOn(LocalDate windowOpensOn) {
        this.windowOpensOn = windowOpensOn;
    }

    public LocalDate getWindowClosesOn() {
        return windowClosesOn;
    }

    public void setWindowClosesOn(LocalDate windowClosesOn) {
        this.windowClosesOn = windowClosesOn;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public boolean isNotifyOnRelease() {
        return notifyOnRelease;
    }

    public void setNotifyOnRelease(boolean notifyOnRelease) {
        this.notifyOnRelease = notifyOnRelease;
    }

    public boolean isNotifyOnLock() {
        return notifyOnLock;
    }

    public void setNotifyOnLock(boolean notifyOnLock) {
        this.notifyOnLock = notifyOnLock;
    }

    public List<Integer> getReminderDaysBeforeClose() {
        if (reminderDaysBeforeClose == null) {
            return List.of();
        }
        List<Integer> list = new ArrayList<>(reminderDaysBeforeClose.length);
        for (short s : reminderDaysBeforeClose) {
            list.add((int) s);
        }
        return list;
    }

    public void setReminderDaysBeforeClose(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            this.reminderDaysBeforeClose = new short[0];
        } else {
            short[] arr = new short[days.size()];
            for (int i = 0; i < days.size(); i++) {
                arr[i] = days.get(i).shortValue();
            }
            this.reminderDaysBeforeClose = arr;
        }
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
        this.updatedBy = updatedBy != null ? updatedBy : ACTOR_SYSTEM;
    }
}
