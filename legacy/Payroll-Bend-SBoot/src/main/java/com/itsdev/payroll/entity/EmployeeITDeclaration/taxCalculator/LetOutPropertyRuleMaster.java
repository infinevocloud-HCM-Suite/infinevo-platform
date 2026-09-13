package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;

import jakarta.persistence.*;

@Entity
@Table(name = "letOutPropertyRuleMaster")
public class LetOutPropertyRuleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Example: "2025-26"
    private String financialYear;

    // OLD regime only
    private String taxRegime;

    // Standard deduction percentage on Net Annual Value (30%)
    private Integer standardDeductionPercentage;

    // Max loss allowed to be set off against salary (200000)
    private Integer maxLossSetOffAgainstSalary;

    // Whether home loan interest is allowed
    private Boolean isHomeLoanInterestAllowed;

    // Whether loss carry forward is allowed (future use)
    private Boolean isLossCarryForwardAllowed;

    // Rule active or not
    private Boolean isActive = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public Integer getStandardDeductionPercentage() {
        return standardDeductionPercentage;
    }

    public void setStandardDeductionPercentage(Integer standardDeductionPercentage) {
        this.standardDeductionPercentage = standardDeductionPercentage;
    }

    public Integer getMaxLossSetOffAgainstSalary() {
        return maxLossSetOffAgainstSalary;
    }

    public void setMaxLossSetOffAgainstSalary(Integer maxLossSetOffAgainstSalary) {
        this.maxLossSetOffAgainstSalary = maxLossSetOffAgainstSalary;
    }

    public Boolean getHomeLoanInterestAllowed() {
        return isHomeLoanInterestAllowed;
    }

    public void setHomeLoanInterestAllowed(Boolean homeLoanInterestAllowed) {
        isHomeLoanInterestAllowed = homeLoanInterestAllowed;
    }

    public Boolean getLossCarryForwardAllowed() {
        return isLossCarryForwardAllowed;
    }

    public void setLossCarryForwardAllowed(Boolean lossCarryForwardAllowed) {
        isLossCarryForwardAllowed = lossCarryForwardAllowed;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }
}

