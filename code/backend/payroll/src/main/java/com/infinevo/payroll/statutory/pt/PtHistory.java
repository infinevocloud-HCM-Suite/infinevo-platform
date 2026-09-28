package com.infinevo.payroll.statutory.pt;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Historical record of a professional tax override change (W-31.2, V067).
 */
@Entity
@Table(name = "pt_history", schema = "payroll")
@Audited
public class PtHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "state_code", length = 10, nullable = false, updatable = false)
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", length = 16, nullable = false, updatable = false)
    private PtHistoryOperation operation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_slabs", updatable = false)
    private List<PtSlabDto> beforeSlabs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_slabs", updatable = false)
    private List<PtSlabDto> afterSlabs;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    protected PtHistory() {}

    public PtHistory(
            UUID tenantId,
            String stateCode,
            PtHistoryOperation operation,
            List<PtSlabDto> beforeSlabs,
            List<PtSlabDto> afterSlabs,
            UUID changedBy) {
        this.tenantId = tenantId;
        this.stateCode = stateCode;
        this.operation = operation;
        this.beforeSlabs = beforeSlabs;
        this.afterSlabs = afterSlabs;
        this.changedAt = Instant.now();
        this.changedBy = changedBy != null ? changedBy : new UUID(0L, 0L);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getStateCode() {
        return stateCode;
    }

    public PtHistoryOperation getOperation() {
        return operation;
    }

    public List<PtSlabDto> getBeforeSlabs() {
        return beforeSlabs;
    }

    public List<PtSlabDto> getAfterSlabs() {
        return afterSlabs;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public PtHistoryResponse toResponse() {
        return new PtHistoryResponse(id, stateCode, operation, beforeSlabs, afterSlabs, changedAt, changedBy);
    }
}
