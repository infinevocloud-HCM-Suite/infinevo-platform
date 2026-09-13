package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "section87a_rebate_rule_master")
public class Section87ARebateRuleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ========================
       Regime & Applicability
       ======================== */

    @Column(nullable = false)
    private String taxRegime;            // OLD / NEW

    @Column(nullable = false)
    private BigDecimal incomeThreshold;  // Max taxable income allowed

    @Column(nullable = false)
    private BigDecimal maxRebateAmount;  // Max rebate allowed

    /* ========================
       Rule Nature
       ======================== */

    @Column(nullable = false)
    private Boolean isFullRebate;         // true = rebate equals tax payable

    /* ========================
       Validity
       ======================== */

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private Boolean isActive = true;

    private String remarks;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public BigDecimal getIncomeThreshold() {
        return incomeThreshold;
    }

    public void setIncomeThreshold(BigDecimal incomeThreshold) {
        this.incomeThreshold = incomeThreshold;
    }

    public BigDecimal getMaxRebateAmount() {
        return maxRebateAmount;
    }

    public void setMaxRebateAmount(BigDecimal maxRebateAmount) {
        this.maxRebateAmount = maxRebateAmount;
    }

    public Boolean getFullRebate() {
        return isFullRebate;
    }

    public void setFullRebate(Boolean fullRebate) {
        isFullRebate = fullRebate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}

