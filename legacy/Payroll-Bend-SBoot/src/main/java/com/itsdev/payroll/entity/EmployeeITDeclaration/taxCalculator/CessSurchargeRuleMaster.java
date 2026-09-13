package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cess_surcharge_rule_master")
public class CessSurchargeRuleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ========================
       Rule Type
       ======================== */

    @Column(nullable = false)
    private String ruleType;        // CESS / SURCHARGE

    private String taxRegime;       // OLD / NEW / BOTH

    /* ========================
       Applicability
       ======================== */

    private BigDecimal incomeFrom;  // Nullable for cess
    private BigDecimal incomeTo;

    /* ========================
       Rate
       ======================== */

    @Column(nullable = false)
    private BigDecimal rate;        // Percentage (e.g. 4 = 4%)

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

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public BigDecimal getIncomeFrom() {
        return incomeFrom;
    }

    public void setIncomeFrom(BigDecimal incomeFrom) {
        this.incomeFrom = incomeFrom;
    }

    public BigDecimal getIncomeTo() {
        return incomeTo;
    }

    public void setIncomeTo(BigDecimal incomeTo) {
        this.incomeTo = incomeTo;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
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

