package com.infinevo.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One captured change: insert, update or delete of an entity that carries {@link Audited}.
 *
 * <p>Maps {@code core.audit_log} — {@code migration/src/main/resources/db/migration/core/V008__audit_log.sql}.
 *
 * <p><strong>Insert-only.</strong> {@code V008__audit_log.sql:44} revokes {@code UPDATE} and
 * {@code DELETE} from {@code app_user}, so an audit row cannot be edited by the application that
 * wrote it. The mapping says the same thing in Java: {@link Immutable}, every column
 * {@code updatable = false}. An accidental dirty-check would otherwise reach the database and be
 * refused there with a privilege error that reads like an outage.
 *
 * <p>{@code actorUserId} is a bare {@code uuid} with no foreign key. {@code W-10} has not merged,
 * so {@code core.user_account} does not exist yet — spec section 9, the actor risk row.
 *
 * <p>Values in {@link #getOldValues()} and {@link #getNewValues()} are <strong>strings</strong>,
 * never numbers. A money value serialised as a JSON float would lose precision on the way back
 * out, which is exactly what {@code docs/CONVENTIONS.md} section 2 forbids — spec section 11.
 */
@Entity
@Table(name = "audit_log", schema = "core")
@Immutable
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_label", nullable = false, length = 100, updatable = false)
    private String actorLabel;

    @Column(name = "operation", nullable = false, length = 10, updatable = false)
    private String operation;

    @Column(name = "entity_schema", nullable = false, length = 32, updatable = false)
    private String entitySchema;

    @Column(name = "entity_table", nullable = false, length = 64, updatable = false)
    private String entityTable;

    @Column(name = "entity_id", nullable = false, length = 64, updatable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "changed_columns", updatable = false)
    private String[] changedColumns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", updatable = false)
    private Map<String, String> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", updatable = false)
    private Map<String, String> newValues;

    @Column(name = "trace_id", length = 36, updatable = false)
    private String traceId;

    protected AuditLog() {}

    AuditLog(
            UUID tenantId,
            Instant occurredAt,
            UUID actorUserId,
            String actorLabel,
            String operation,
            String entitySchema,
            String entityTable,
            String entityId,
            String[] changedColumns,
            Map<String, String> oldValues,
            Map<String, String> newValues,
            String traceId) {
        this.tenantId = tenantId;
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.actorLabel = actorLabel;
        this.operation = operation;
        this.entitySchema = entitySchema;
        this.entityTable = entityTable;
        this.entityId = entityId;
        this.changedColumns = changedColumns;
        this.oldValues = oldValues;
        this.newValues = newValues;
        this.traceId = traceId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public String getOperation() {
        return operation;
    }

    public String getEntitySchema() {
        return entitySchema;
    }

    public String getEntityTable() {
        return entityTable;
    }

    public String getEntityId() {
        return entityId;
    }

    public String[] getChangedColumns() {
        return changedColumns == null ? null : changedColumns.clone();
    }

    public Map<String, String> getOldValues() {
        return oldValues;
    }

    public Map<String, String> getNewValues() {
        return newValues;
    }

    public String getTraceId() {
        return traceId;
    }
}
