package com.infinevo.core.holiday;

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
import java.util.Objects;
import java.util.UUID;

/**
 * Calendar assignment to a work location (W-17) — {@code core.holiday_calendar_location}.
 *
 * <p>Enforces that a work location resolves to exactly one holiday calendar per tenant
 * via unique index {@code idx_holiday_calendar_location_tenant_location}.
 */
@Entity
@Table(
        name = "holiday_calendar_location",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_holiday_calendar_location_tenant_location",
                    columnList = "tenant_id, work_location_id",
                    unique = true),
            @Index(name = "idx_holiday_calendar_location_tenant_calendar", columnList = "tenant_id, calendar_id")
        })
public class HolidayCalendarLocation {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "calendar_id", nullable = false)
    private UUID calendarId;

    @Column(name = "work_location_id", nullable = false)
    private UUID workLocationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected HolidayCalendarLocation() {}

    public HolidayCalendarLocation(UUID tenantId, UUID calendarId, UUID workLocationId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.calendarId = Objects.requireNonNull(calendarId, "calendarId must not be null");
        this.workLocationId = Objects.requireNonNull(workLocationId, "workLocationId must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
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

    public UUID getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(UUID calendarId) {
        this.calendarId = Objects.requireNonNull(calendarId, "calendarId must not be null");
    }

    public UUID getWorkLocationId() {
        return workLocationId;
    }

    public void setWorkLocationId(UUID workLocationId) {
        this.workLocationId = Objects.requireNonNull(workLocationId, "workLocationId must not be null");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
