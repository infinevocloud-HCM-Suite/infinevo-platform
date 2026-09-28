package com.infinevo.payroll.component;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deduction component entity in schema payroll (W-26.1).
 */
@Entity
@Table(
        name = "deduction",
        schema = "payroll",
        indexes = {
            @Index(name = "uk_deduction_tenant_code", columnList = "tenant_id, code", unique = true),
            @Index(name = "idx_deduction_tenant_deleted_active", columnList = "tenant_id, is_deleted, is_active")
        })
public class Deduction extends SalaryComponent {

    @Column(name = "deduction_type", nullable = false, length = 32)
    private String deductionType;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = false;

    @Column(name = "is_pre_tax", nullable = false)
    private boolean preTax = false;

    @Column(name = "emi_type", length = 32)
    private String emiType;

    @Column(name = "perquisite_interest_rate", precision = 7, scale = 4)
    private BigDecimal perquisiteInterestRate;

    @Column(name = "emi_interest_rate", precision = 7, scale = 4)
    private BigDecimal emiInterestRate;

    protected Deduction() {}

    public Deduction(UUID tenantId, String actor) {
        super(tenantId, actor);
    }

    public String getDeductionType() {
        return deductionType;
    }

    public void setDeductionType(String deductionType) {
        this.deductionType = deductionType;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public boolean isPreTax() {
        return preTax;
    }

    public void setPreTax(boolean preTax) {
        this.preTax = preTax;
    }

    public String getEmiType() {
        return emiType;
    }

    public void setEmiType(String emiType) {
        this.emiType = emiType;
    }

    public BigDecimal getPerquisiteInterestRate() {
        return perquisiteInterestRate;
    }

    public void setPerquisiteInterestRate(BigDecimal perquisiteInterestRate) {
        this.perquisiteInterestRate = perquisiteInterestRate;
    }

    public BigDecimal getEmiInterestRate() {
        return emiInterestRate;
    }

    public void setEmiInterestRate(BigDecimal emiInterestRate) {
        this.emiInterestRate = emiInterestRate;
    }
}
