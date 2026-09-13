package com.itsdev.payroll.dto.employeeTDS;

import com.itsdev.payroll.enumeration.TdsSourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class EmployeeTdsResponseDTO {

    private Long id;

    private String employeeId;
    private String organizationId;
    private Integer fiscalYear;

    private TdsSourceType tdsSourceType;
    private String tdsSourceTypeDisplayName;

    private Long poiId;
    private String taxRegime;

    private BigDecimal annualGrossSalary;
    private BigDecimal annualTaxableIncome;
    private BigDecimal finalAnnualTax;

    private String effectiveFromMonth;

    private Boolean isActive;

    private LocalDateTime createdAt;

    // ================= GETTERS & SETTERS =================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public TdsSourceType getTdsSourceType() {
        return tdsSourceType;
    }

    public void setTdsSourceType(TdsSourceType tdsSourceType) {
        this.tdsSourceType = tdsSourceType;
    }

    public String getTdsSourceTypeDisplayName() {
        return tdsSourceTypeDisplayName;
    }

    public void setTdsSourceTypeDisplayName(String tdsSourceTypeDisplayName) {
        this.tdsSourceTypeDisplayName = tdsSourceTypeDisplayName;
    }

    public Long getPoiId() {
        return poiId;
    }

    public void setPoiId(Long poiId) {
        this.poiId = poiId;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public BigDecimal getAnnualGrossSalary() {
        return annualGrossSalary;
    }

    public void setAnnualGrossSalary(BigDecimal annualGrossSalary) {
        this.annualGrossSalary = annualGrossSalary;
    }

    public BigDecimal getAnnualTaxableIncome() {
        return annualTaxableIncome;
    }

    public void setAnnualTaxableIncome(BigDecimal annualTaxableIncome) {
        this.annualTaxableIncome = annualTaxableIncome;
    }

    public BigDecimal getFinalAnnualTax() {
        return finalAnnualTax;
    }

    public void setFinalAnnualTax(BigDecimal finalAnnualTax) {
        this.finalAnnualTax = finalAnnualTax;
    }

    public String getEffectiveFromMonth() {
        return effectiveFromMonth;
    }

    public void setEffectiveFromMonth(String effectiveFromMonth) {
        this.effectiveFromMonth = effectiveFromMonth;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
