package com.itsdev.payroll.dto.employee.SalaryRevision;

import java.math.BigDecimal;
import java.time.LocalDate;

public class EmployeeCtcRevisionExportRowDTO {

    private String employeeNumber;
    private String employeeName;

    private String salaryTemplateName; // if not available → keep null
    private String status;             // APPROVED
    private Boolean isCtcChangedByPercentage;
    private Double revisionPercentage;

    private Double previousCtc;
    private Double revisedCtc;

    private LocalDate effectiveFrom;
    private String payoutMonth;

    // Earnings
    private String basicName;
    private Double basicAmount;

    private String hraName;
    private Double hraAmount;

    private String conveyanceName;
    private Double conveyanceAmount;

    private String fixedAllowanceName;
    private Double fixedAllowanceAmount;

    // PF
    private Boolean isEligibleForPf;
    private Boolean isEligibleForEps;
    private Boolean canContributeEpsOnHigherWages;
    private Boolean isEmployerRestrictedBasicEnabled;
    private Boolean isEmployeeRestrictedBasicEnabled;
    private Double employerRestrictedBasicAmount;
    private Double employeeRestrictedBasicAmount;

    private BigDecimal  epfEmployerContribution;
    private BigDecimal  epfEmployeeContribution;

    // ESI
    private Boolean isEligibleForEsi;
    private BigDecimal esiEmployerContribution;
    private BigDecimal  esiEmployeeContribution;

    private BigDecimal changeInPercent;

    private String revisionStatus;

    public String getRevisionStatus() {
        return revisionStatus;
    }

    public void setRevisionStatus(String revisionStatus) {
        this.revisionStatus = revisionStatus;
    }

    public BigDecimal getChangeInPercent() {
        return changeInPercent;
    }

    public void setChangeInPercent(BigDecimal changeInPercent) {
        this.changeInPercent = changeInPercent;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getSalaryTemplateName() {
        return salaryTemplateName;
    }

    public void setSalaryTemplateName(String salaryTemplateName) {
        this.salaryTemplateName = salaryTemplateName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getCtcChangedByPercentage() {
        return isCtcChangedByPercentage;
    }

    public void setCtcChangedByPercentage(Boolean ctcChangedByPercentage) {
        isCtcChangedByPercentage = ctcChangedByPercentage;
    }

    public Double getRevisionPercentage() {
        return revisionPercentage;
    }

    public void setRevisionPercentage(Double revisionPercentage) {
        this.revisionPercentage = revisionPercentage;
    }

    public Double getPreviousCtc() {
        return previousCtc;
    }

    public void setPreviousCtc(Double previousCtc) {
        this.previousCtc = previousCtc;
    }

    public Double getRevisedCtc() {
        return revisedCtc;
    }

    public void setRevisedCtc(Double revisedCtc) {
        this.revisedCtc = revisedCtc;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public String getPayoutMonth() {
        return payoutMonth;
    }

    public void setPayoutMonth(String payoutMonth) {
        this.payoutMonth = payoutMonth;
    }

    public String getBasicName() {
        return basicName;
    }

    public void setBasicName(String basicName) {
        this.basicName = basicName;
    }

    public Double getBasicAmount() {
        return basicAmount;
    }

    public void setBasicAmount(Double basicAmount) {
        this.basicAmount = basicAmount;
    }

    public String getHraName() {
        return hraName;
    }

    public void setHraName(String hraName) {
        this.hraName = hraName;
    }

    public Double getHraAmount() {
        return hraAmount;
    }

    public void setHraAmount(Double hraAmount) {
        this.hraAmount = hraAmount;
    }

    public String getConveyanceName() {
        return conveyanceName;
    }

    public void setConveyanceName(String conveyanceName) {
        this.conveyanceName = conveyanceName;
    }

    public Double getConveyanceAmount() {
        return conveyanceAmount;
    }

    public void setConveyanceAmount(Double conveyanceAmount) {
        this.conveyanceAmount = conveyanceAmount;
    }

    public String getFixedAllowanceName() {
        return fixedAllowanceName;
    }

    public void setFixedAllowanceName(String fixedAllowanceName) {
        this.fixedAllowanceName = fixedAllowanceName;
    }

    public Double getFixedAllowanceAmount() {
        return fixedAllowanceAmount;
    }

    public void setFixedAllowanceAmount(Double fixedAllowanceAmount) {
        this.fixedAllowanceAmount = fixedAllowanceAmount;
    }

    public Boolean getEligibleForPf() {
        return isEligibleForPf;
    }

    public void setEligibleForPf(Boolean eligibleForPf) {
        isEligibleForPf = eligibleForPf;
    }

    public Boolean getEligibleForEps() {
        return isEligibleForEps;
    }

    public void setEligibleForEps(Boolean eligibleForEps) {
        isEligibleForEps = eligibleForEps;
    }

    public Boolean getCanContributeEpsOnHigherWages() {
        return canContributeEpsOnHigherWages;
    }

    public void setCanContributeEpsOnHigherWages(Boolean canContributeEpsOnHigherWages) {
        this.canContributeEpsOnHigherWages = canContributeEpsOnHigherWages;
    }

    public Boolean getEmployerRestrictedBasicEnabled() {
        return isEmployerRestrictedBasicEnabled;
    }

    public void setEmployerRestrictedBasicEnabled(Boolean employerRestrictedBasicEnabled) {
        isEmployerRestrictedBasicEnabled = employerRestrictedBasicEnabled;
    }

    public Boolean getEmployeeRestrictedBasicEnabled() {
        return isEmployeeRestrictedBasicEnabled;
    }

    public void setEmployeeRestrictedBasicEnabled(Boolean employeeRestrictedBasicEnabled) {
        isEmployeeRestrictedBasicEnabled = employeeRestrictedBasicEnabled;
    }

    public Double getEmployerRestrictedBasicAmount() {
        return employerRestrictedBasicAmount;
    }

    public void setEmployerRestrictedBasicAmount(Double employerRestrictedBasicAmount) {
        this.employerRestrictedBasicAmount = employerRestrictedBasicAmount;
    }

    public Double getEmployeeRestrictedBasicAmount() {
        return employeeRestrictedBasicAmount;
    }

    public void setEmployeeRestrictedBasicAmount(Double employeeRestrictedBasicAmount) {
        this.employeeRestrictedBasicAmount = employeeRestrictedBasicAmount;
    }




    public Boolean getEligibleForEsi() {
        return isEligibleForEsi;
    }

    public BigDecimal getEpfEmployerContribution() {
        return epfEmployerContribution;
    }

    public void setEpfEmployerContribution(BigDecimal epfEmployerContribution) {
        this.epfEmployerContribution = epfEmployerContribution;
    }

    public BigDecimal getEpfEmployeeContribution() {
        return epfEmployeeContribution;
    }

    public void setEpfEmployeeContribution(BigDecimal epfEmployeeContribution) {
        this.epfEmployeeContribution = epfEmployeeContribution;
    }

    public void setEligibleForEsi(Boolean eligibleForEsi) {
        isEligibleForEsi = eligibleForEsi;
    }

    public BigDecimal getEsiEmployerContribution() {
        return esiEmployerContribution;
    }

    public void setEsiEmployerContribution(BigDecimal esiEmployerContribution) {
        this.esiEmployerContribution = esiEmployerContribution;
    }

    public BigDecimal getEsiEmployeeContribution() {
        return esiEmployeeContribution;
    }

    public void setEsiEmployeeContribution(BigDecimal esiEmployeeContribution) {
        this.esiEmployeeContribution = esiEmployeeContribution;
    }
}

