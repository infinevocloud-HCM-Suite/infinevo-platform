package com.infinevo.payroll.gratuity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for {@code payroll.gratuity_settlement} (W-91).
 */
@Entity
@Table(name = "gratuity_settlement", schema = "payroll")
public class GratuitySettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(name = "exit_date", nullable = false)
    private LocalDate exitDate;

    @Column(name = "completed_years", nullable = false)
    private int completedYears;

    @Column(name = "is_exempt_from_5yr_rule", nullable = false)
    private boolean exemptFrom5YrRule;

    @Column(name = "last_drawn_basic", nullable = false, precision = 14, scale = 2)
    private BigDecimal lastDrawnBasic;

    @Column(name = "last_drawn_da", nullable = false, precision = 14, scale = 2)
    private BigDecimal lastDrawnDa;

    @Column(name = "calculated_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal calculatedAmount;

    @Column(name = "statutory_ceiling", nullable = false, precision = 14, scale = 2)
    private BigDecimal statutoryCeiling;

    @Column(name = "gross_gratuity_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossGratuityPayable;

    @Column(name = "tax_exempt_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxExemptAmount;

    @Column(name = "taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GratuitySettlementStatus status = GratuitySettlementStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public LocalDate getExitDate() {
        return exitDate;
    }

    public void setExitDate(LocalDate exitDate) {
        this.exitDate = exitDate;
    }

    public int getCompletedYears() {
        return completedYears;
    }

    public void setCompletedYears(int completedYears) {
        this.completedYears = completedYears;
    }

    public boolean isExemptFrom5YrRule() {
        return exemptFrom5YrRule;
    }

    public void setExemptFrom5YrRule(boolean exemptFrom5YrRule) {
        this.exemptFrom5YrRule = exemptFrom5YrRule;
    }

    public BigDecimal getLastDrawnBasic() {
        return lastDrawnBasic;
    }

    public void setLastDrawnBasic(BigDecimal lastDrawnBasic) {
        this.lastDrawnBasic = lastDrawnBasic;
    }

    public BigDecimal getLastDrawnDa() {
        return lastDrawnDa;
    }

    public void setLastDrawnDa(BigDecimal lastDrawnDa) {
        this.lastDrawnDa = lastDrawnDa;
    }

    public BigDecimal getCalculatedAmount() {
        return calculatedAmount;
    }

    public void setCalculatedAmount(BigDecimal calculatedAmount) {
        this.calculatedAmount = calculatedAmount;
    }

    public BigDecimal getStatutoryCeiling() {
        return statutoryCeiling;
    }

    public void setStatutoryCeiling(BigDecimal statutoryCeiling) {
        this.statutoryCeiling = statutoryCeiling;
    }

    public BigDecimal getGrossGratuityPayable() {
        return grossGratuityPayable;
    }

    public void setGrossGratuityPayable(BigDecimal grossGratuityPayable) {
        this.grossGratuityPayable = grossGratuityPayable;
    }

    public BigDecimal getTaxExemptAmount() {
        return taxExemptAmount;
    }

    public void setTaxExemptAmount(BigDecimal taxExemptAmount) {
        this.taxExemptAmount = taxExemptAmount;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public void setTaxableAmount(BigDecimal taxableAmount) {
        this.taxableAmount = taxableAmount;
    }

    public GratuitySettlementStatus getStatus() {
        return status;
    }

    public void setStatus(GratuitySettlementStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
