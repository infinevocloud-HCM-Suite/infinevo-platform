package com.infinevo.payroll.statutory.pt;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-level professional tax override slab row (W-31.2, V066).
 */
@Entity
@Table(name = "org_pt_override_slab", schema = "payroll")
@Audited
public class OrgPtOverrideSlab {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "override_id", nullable = false)
    private UUID overrideId;

    @Column(name = "from_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", precision = 19, scale = 4)
    private BigDecimal toAmount;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "is_female_exempt", nullable = false)
    private boolean isFemaleExempt;

    @Column(name = "deduction_months", length = 32)
    private String deductionMonths;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100, nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100, nullable = false)
    private String updatedBy;

    protected OrgPtOverrideSlab() {}

    public OrgPtOverrideSlab(
            UUID tenantId,
            UUID overrideId,
            BigDecimal fromAmount,
            BigDecimal toAmount,
            BigDecimal amount,
            boolean isFemaleExempt,
            String deductionMonths,
            short sortOrder,
            String actor) {
        this.tenantId = tenantId;
        this.overrideId = overrideId;
        this.fromAmount = fromAmount;
        this.toAmount = toAmount;
        this.amount = amount;
        this.isFemaleExempt = isFemaleExempt;
        this.deductionMonths = deductionMonths;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
        this.createdBy = actor != null ? actor : "system";
        this.updatedAt = Instant.now();
        this.updatedBy = this.createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOverrideId() {
        return overrideId;
    }

    public BigDecimal getFromAmount() {
        return fromAmount;
    }

    public BigDecimal getToAmount() {
        return toAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isFemaleExempt() {
        return isFemaleExempt;
    }

    public String getDeductionMonths() {
        return deductionMonths;
    }

    public short getSortOrder() {
        return sortOrder;
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

    public PtSlabDto toDto() {
        return new PtSlabDto(fromAmount, toAmount, amount, isFemaleExempt, PtSlabDto.parseMonths(deductionMonths));
    }
}
