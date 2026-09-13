package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import java.math.BigDecimal;

public class ConsiderForITResponse {

    private Long poiId;
    private String employeeId;
    private Integer fiscalYear;

    private String status;
    private Boolean consideredForIt;

    private BigDecimal finalAnnualTax;
    private String taxRegime;



    // 🔥 ADD THIS
    private Object taxCalculation;

    // ================= GETTERS / SETTERS =================

    public Object getTaxCalculation() {
        return taxCalculation;
    }

    public void setTaxCalculation(Object taxCalculation) {
        this.taxCalculation = taxCalculation;
    }



    // private String consideredBy;
    private java.time.LocalDateTime consideredDate;

    public Long getPoiId() {
        return poiId;
    }

    public void setPoiId(Long poiId) {
        this.poiId = poiId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public Integer getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(Integer fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getConsideredForIt() {
        return consideredForIt;
    }

    public void setConsideredForIt(Boolean consideredForIt) {
        this.consideredForIt = consideredForIt;
    }

    // public String getConsideredBy() {
    //     return consideredBy;
    // }

    // public void setConsideredBy(String consideredBy) {
    //     this.consideredBy = consideredBy;
    // }

    public java.time.LocalDateTime getConsideredDate() {
        return consideredDate;
    }

    public void setConsideredDate(java.time.LocalDateTime consideredDate) {
        this.consideredDate = consideredDate;
    }

    public BigDecimal getFinalAnnualTax() {
        return finalAnnualTax;
    }

    public void setFinalAnnualTax(BigDecimal finalAnnualTax) {
        this.finalAnnualTax = finalAnnualTax;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }
}
