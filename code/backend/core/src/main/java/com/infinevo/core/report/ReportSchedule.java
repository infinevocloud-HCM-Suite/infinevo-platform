package com.infinevo.core.report;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * A report definition run on a cadence and emailed as a signed link (W-23.2), in
 * {@code core.report_schedule}, isolated by row-level security on {@code tenant_id}.
 *
 * <p><strong>{@link Audited}.</strong> Decision 1 of the spec allows recipients outside the tenant on
 * condition that every one is recorded in the audit log. Auditing the row records the recipient list,
 * with its values, each time a schedule is created or changed.
 *
 * <p><strong>The id is assigned</strong>, because {@code PUT /report-schedules/{id}} lets the client
 * name it. That makes this a {@link Persistable}: with an assigned id Spring Data cannot tell a new row
 * from an existing one and would merge, which Hibernate refuses for a row that does not exist yet.
 * {@link #isNew} answers from whether the row was loaded — the pattern of {@code Document}.
 */
@Entity
@Table(name = "report_schedule", schema = "core")
@Audited
public class ReportSchedule implements Persistable<UUID> {

    public static final String DAILY = "DAILY";
    public static final String WEEKLY = "WEEKLY";
    public static final String MONTHLY = "MONTHLY";

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    static final String ACTOR_SYSTEM = "system";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    /**
     * Whoever last confirmed, by saving this schedule, that they hold the definition's
     * {@code required_action} (manager's review item B-4). {@code null} only for a row saved
     * before this column existed, or saved by a non-JWT actor (a test, a migration); the evaluator
     * treats that the same as a check that fails.
     */
    @Column(name = "owner_user_account_id")
    private UUID ownerUserAccountId;

    @Column(name = "cadence", nullable = false, length = 16)
    private String cadence;

    @Column(name = "day_of_period")
    private Integer dayOfPeriod;

    @Column(name = "send_at_local_time", nullable = false)
    private LocalTime sendAtLocalTime;

    /** A JSON object of filter name to value, or null. Mapped as JSON: a plain string is refused by jsonb. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters", columnDefinition = "jsonb")
    private String filters;

    @Column(name = "recipient_emails", nullable = false, columnDefinition = "text")
    private String recipientEmails;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_run_status", length = 16)
    private String lastRunStatus;

    @Column(name = "last_document_id")
    private UUID lastDocumentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Transient
    private boolean isNew = true;

    protected ReportSchedule() {}

    public ReportSchedule(
            UUID tenantId,
            UUID definitionId,
            String cadence,
            Integer dayOfPeriod,
            LocalTime sendAtLocalTime,
            String filters,
            String recipientEmails,
            boolean isActive,
            String actor) {
        this(UUID.randomUUID(), tenantId, actor);
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId must not be null");
        this.cadence = Objects.requireNonNull(cadence, "cadence must not be null");
        this.dayOfPeriod = dayOfPeriod;
        this.sendAtLocalTime = Objects.requireNonNull(sendAtLocalTime, "sendAtLocalTime must not be null");
        this.filters = filters;
        this.recipientEmails = Objects.requireNonNull(recipientEmails, "recipientEmails must not be null");
        this.isActive = isActive;
    }

    /** A new, empty schedule under the id the client chose; {@link #apply} fills it. */
    ReportSchedule(UUID id, UUID tenantId, String actor) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.createdBy = actor == null ? ACTOR_SYSTEM : actor;
        this.updatedBy = this.createdBy;
    }

    /** Replaces everything a client sets; run state is untouched. */
    void apply(
            UUID definitionId,
            UUID ownerUserAccountId,
            String cadence,
            Integer dayOfPeriod,
            LocalTime sendAtLocalTime,
            String filters,
            String recipientEmails,
            boolean isActive,
            String actor) {
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId must not be null");
        this.ownerUserAccountId = ownerUserAccountId;
        this.cadence = Objects.requireNonNull(cadence, "cadence must not be null");
        this.dayOfPeriod = dayOfPeriod;
        this.sendAtLocalTime = Objects.requireNonNull(sendAtLocalTime, "sendAtLocalTime must not be null");
        this.filters = filters;
        this.recipientEmails = Objects.requireNonNull(recipientEmails, "recipientEmails must not be null");
        this.isActive = isActive;
        this.updatedBy = actor == null ? ACTOR_SYSTEM : actor;
    }

    /** Records how a run ended. */
    public void recordRun(Instant at, String status, UUID documentId, String actor) {
        this.lastRunAt = at;
        this.lastRunStatus = status;
        if (documentId != null) {
            this.lastDocumentId = documentId;
        }
        this.updatedBy = actor == null ? ACTOR_SYSTEM : actor;
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

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    /** For a test that needs a known id; the service assigns ids through the constructor. */
    public void setId(UUID id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getDefinitionId() {
        return definitionId;
    }

    public UUID getOwnerUserAccountId() {
        return ownerUserAccountId;
    }

    /** For a test that needs a known owner; the service sets it through {@link #apply}. */
    public void setOwnerUserAccountId(UUID ownerUserAccountId) {
        this.ownerUserAccountId = ownerUserAccountId;
    }

    public String getCadence() {
        return cadence;
    }

    public Integer getDayOfPeriod() {
        return dayOfPeriod;
    }

    public LocalTime getSendAtLocalTime() {
        return sendAtLocalTime;
    }

    public String getFilters() {
        return filters;
    }

    public String getRecipientEmails() {
        return recipientEmails;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    /** For a test that needs a schedule that has already run; runs are recorded through {@link #recordRun}. */
    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }

    public UUID getLastDocumentId() {
        return lastDocumentId;
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
}
