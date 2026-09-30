package com.infinevo.payroll.statutory.lines;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Employee state insurance statutory line entity on a salary version (W-31.3).
 */
@Entity
@Table(
        name = "ctc_esi_component",
        schema = "payroll",
        indexes = {
            @Index(
                    name = "uk_ctc_esi_component_tenant_structure_code",
                    columnList = "tenant_id, ctc_structure_id, component_code",
                    unique = true),
            @Index(name = "idx_ctc_esi_component_tenant_structure", columnList = "tenant_id, ctc_structure_id")
        })
public class CtcEsiComponent {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "ctc_structure_id", nullable = false, updatable = false)
    private UUID ctcStructureId;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_code", nullable = false, length = 32)
    private StatutoryComponentCode componentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "share", nullable = false, length = 16)
    private ContributionShare share;

    @Column(name = "wage_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal wageBase;

    @Column(name = "rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal rate;

    @Column(name = "monthly_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyAmount;

    @Column(name = "annual_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal annualAmount;

    @Column(name = "is_included_in_ctc", nullable = false)
    private boolean includedInCtc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected CtcEsiComponent() {}

    public CtcEsiComponent(
            UUID tenantId,
            UUID ctcStructureId,
            StatutoryComponentCode componentCode,
            ContributionShare share,
            BigDecimal wageBase,
            BigDecimal rate,
            BigDecimal monthlyAmount,
            BigDecimal annualAmount,
            boolean includedInCtc,
            String actor) {
        this.tenantId = tenantId;
        this.ctcStructureId = ctcStructureId;
        this.componentCode = componentCode;
        this.share = share;
        this.wageBase = wageBase;
        this.rate = rate;
        this.monthlyAmount = monthlyAmount;
        this.annualAmount = annualAmount;
        this.includedInCtc = includedInCtc;
        this.createdBy = (actor != null && !actor.isBlank()) ? actor : ACTOR_SYSTEM;
        this.updatedBy = this.createdBy;
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

    public UUID getCtcStructureId() {
        return ctcStructureId;
    }

    public StatutoryComponentCode getComponentCode() {
        return componentCode;
    }

    public ContributionShare getShare() {
        return share;
    }

    public BigDecimal getWageBase() {
        return wageBase;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    public BigDecimal getAnnualAmount() {
        return annualAmount;
    }

    public boolean isIncludedInCtc() {
        return includedInCtc;
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
