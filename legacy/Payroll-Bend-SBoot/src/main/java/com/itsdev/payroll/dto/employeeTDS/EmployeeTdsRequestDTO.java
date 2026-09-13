package com.itsdev.payroll.dto.employeeTDS;

import com.itsdev.payroll.enumeration.TdsSourceType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class EmployeeTdsRequestDTO {

    @NotNull
    private String employeeId;

    @NotNull
    private String organizationId;

    @NotNull
    private Integer fiscalYear;

    @NotNull
    private TdsSourceType tdsSourceType;

    private Long poiId;

    @NotNull
    private String taxRegime; // OLD / NEW

    @NotNull
    private BigDecimal annualGrossSalary;

    @NotNull
    private BigDecimal annualTaxableIncome;

    @NotNull
    private BigDecimal finalAnnualTax;

    @NotNull
    private String effectiveFromMonth; // "march"


    // ================= GETTERS & SETTERS =================

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
}