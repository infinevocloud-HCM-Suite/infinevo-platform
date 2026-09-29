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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Dated holiday entry belonging to a calendar (W-17) — {@code core.holiday}.
 *
 * <p>Supports date ranges (where {@code toDate >= fromDate}) and the restricted-holiday flag.
 */
@Entity
@Table(
        name = "holiday",
        schema = "core",
        indexes = {@Index(name = "idx_holiday_tenant_calendar_from", columnList = "tenant_id, calendar_id, from_date")})
public class Holiday {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "calendar_id", nullable = false, updatable = false)
    private UUID calendarId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "is_restricted", nullable = false)
    private boolean restricted = false;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected Holiday() {}

    public Holiday(
            UUID tenantId,
            UUID calendarId,
            String name,
            LocalDate fromDate,
            LocalDate toDate,
            boolean restricted,
            String description) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.calendarId = Objects.requireNonNull(calendarId, "calendarId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.fromDate = Objects.requireNonNull(fromDate, "fromDate must not be null");
        this.toDate = Objects.requireNonNull(toDate, "toDate must not be null");
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("to_date must be on or after from_date");
        }
        this.restricted = restricted;
        this.description = description;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = Objects.requireNonNull(fromDate, "fromDate must not be null");
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = Objects.requireNonNull(toDate, "toDate must not be null");
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
