package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;

import jakarta.persistence.*;

@Entity
@Table(name = "otherIncomeRuleMaster")
public class OtherIncomeRuleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Example: 80TTA, 80G, 80CCD1B, 80E
    private String sectionCode;

    // Display name
    private String sectionName;

    // DEDUCTION or INCOME
    private String ruleType;

    // OLD / NEW / BOTH
    private String taxRegime;

    // Financial year e.g. 2025-26
    private String financialYear;

    // Maximum allowed amount
    private Integer maxLimit;

    // Percentage of deduction allowed (100, 50, etc.)
    private Integer deductionPercentage;

    // Whether proof is mandatory
    private Boolean isProofRequired;

    // Whether this rule is conditional (disability, senior citizen, etc.)
    private Boolean isConditional;

    // Whether allowed in current regime
    private Boolean isAllowed;

    // Active flag
    private Boolean isActive = true;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public void setSectionCode(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
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

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public Integer getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(Integer maxLimit) {
        this.maxLimit = maxLimit;
    }

    public Integer getDeductionPercentage() {
        return deductionPercentage;
    }

    public void setDeductionPercentage(Integer deductionPercentage) {
        this.deductionPercentage = deductionPercentage;
    }

    public Boolean getProofRequired() {
        return isProofRequired;
    }

    public void setProofRequired(Boolean proofRequired) {
        isProofRequired = proofRequired;
    }

    public Boolean getConditional() {
        return isConditional;
    }

    public void setConditional(Boolean conditional) {
        isConditional = conditional;
    }

    public Boolean getAllowed() {
        return isAllowed;
    }

    public void setAllowed(Boolean allowed) {
        isAllowed = allowed;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }
}

