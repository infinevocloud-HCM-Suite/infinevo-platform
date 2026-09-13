package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "home_loan_rule_master")
public class HomeLoanRuleMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ========================
       Section Details
       ======================== */

    @Column(nullable = false)
    private String sectionCode;          // 24B, 80EE, 80EEA

    @Column(nullable = false)
    private String sectionName;

    /* ========================
       Rule Applicability
       ======================== */

    @Column(nullable = false)
    private String component;            // PRINCIPAL / INTEREST

    @Column
    private String propertyType;          // SELF_OCCUPIED / LET_OUT / BOTH

    /* ========================
       Limits
       ======================== */

    @Column(precision = 15, scale = 2)
    private BigDecimal maxLimit;          // NULL = no upper limit

    private String maxLimitFormatted;

    /* ========================
       Conditional Fields (80EE / 80EEA)
       ======================== */

    private LocalDate loanSanctionFrom;

    private LocalDate loanSanctionTo;

    private Boolean isFirstTimeBuyer = false;

    /* ========================
       Status & Remarks
       ======================== */

    private Boolean isActive = true;

    private String remarks;


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

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(String propertyType) {
        this.propertyType = propertyType;
    }

    public BigDecimal getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(BigDecimal maxLimit) {
        this.maxLimit = maxLimit;
    }

    public String getMaxLimitFormatted() {
        return maxLimitFormatted;
    }

    public void setMaxLimitFormatted(String maxLimitFormatted) {
        this.maxLimitFormatted = maxLimitFormatted;
    }

    public LocalDate getLoanSanctionFrom() {
        return loanSanctionFrom;
    }

    public void setLoanSanctionFrom(LocalDate loanSanctionFrom) {
        this.loanSanctionFrom = loanSanctionFrom;
    }

    public LocalDate getLoanSanctionTo() {
        return loanSanctionTo;
    }

    public void setLoanSanctionTo(LocalDate loanSanctionTo) {
        this.loanSanctionTo = loanSanctionTo;
    }

    public Boolean getFirstTimeBuyer() {
        return isFirstTimeBuyer;
    }

    public void setFirstTimeBuyer(Boolean firstTimeBuyer) {
        isFirstTimeBuyer = firstTimeBuyer;
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

