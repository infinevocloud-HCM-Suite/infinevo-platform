package com.itsdev.payroll.dto.employee.SalaryRevision;

import com.itsdev.payroll.dto.employee.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class EmployeeCtcRevisionDTO {
    private Long revisionId;
    private String employeeId;          // required
    private Double ctc;                 // required
    private Double monthlySalary;       // optional

    private LocalDate effectiveDate;    // required for revision

    private String revisionReason;      // optional

    private Double previousCtc;
    private Double previousMonthlySalary;


    private List<EmployeeEarningDTO> earnings;
    private List<EmployeeBenefitDTO> benefits;
    private List<EmployeeReimbursementDTO> reimbursements;


    List<EmployeeVariableEarningDTO> variableEarnings;

    List<EmployeeFBPComponentDTO> fbpComponents;

    private List<EpfComponentDTO> epfComponents;
    private List<EsiComponentDTO> esiComponents;

    private String paymentMonth;  // "yyyy-MM"

    private Long ctcStructureId;

    private BigDecimal changeInPercent;


    // getters & setters


    public BigDecimal getChangeInPercent() {
        return changeInPercent;
    }

    public void setChangeInPercent(BigDecimal changeInPercent) {
        this.changeInPercent = changeInPercent;
    }

    public Long getCtcStructureId() {
        return ctcStructureId;
    }

    public void setCtcStructureId(Long ctcStructureId) {
        this.ctcStructureId = ctcStructureId;
    }

    public Long getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(Long revisionId) {
        this.revisionId = revisionId;
    }

    public String getPaymentMonth() {
        return paymentMonth;
    }

    public void setPaymentMonth(String paymentMonth) {
        this.paymentMonth = paymentMonth;
    }

    public Double getPreviousCtc() {
        return previousCtc;
    }

    public void setPreviousCtc(Double previousCtc) {
        this.previousCtc = previousCtc;
    }

    public Double getPreviousMonthlySalary() {
        return previousMonthlySalary;
    }

    public void setPreviousMonthlySalary(Double previousMonthlySalary) {
        this.previousMonthlySalary = previousMonthlySalary;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public Double getCtc() {
        return ctc;
    }

    public void setCtc(Double ctc) {
        this.ctc = ctc;
    }

    public Double getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(Double monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getRevisionReason() {
        return revisionReason;
    }

    public void setRevisionReason(String revisionReason) {
        this.revisionReason = revisionReason;
    }

    public List<EmployeeEarningDTO> getEarnings() {
        return earnings;
    }

    public void setEarnings(List<EmployeeEarningDTO> earnings) {
        this.earnings = earnings;
    }

    public List<EmployeeBenefitDTO> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<EmployeeBenefitDTO> benefits) {
        this.benefits = benefits;
    }

    public List<EmployeeReimbursementDTO> getReimbursements() {
        return reimbursements;
    }

    public void setReimbursements(List<EmployeeReimbursementDTO> reimbursements) {
        this.reimbursements = reimbursements;
    }

    public List<EpfComponentDTO> getEpfComponents() {
        return epfComponents;
    }

    public void setEpfComponents(List<EpfComponentDTO> epfComponents) {
        this.epfComponents = epfComponents;
    }

    public List<EsiComponentDTO> getEsiComponents() {
        return esiComponents;
    }

    public void setEsiComponents(List<EsiComponentDTO> esiComponents) {
        this.esiComponents = esiComponents;
    }

    public List<EmployeeVariableEarningDTO> getVariableEarnings() {
        return variableEarnings;
    }

    public void setVariableEarnings(List<EmployeeVariableEarningDTO> variableEarnings) {
        this.variableEarnings = variableEarnings;
    }

    public List<EmployeeFBPComponentDTO> getFbpComponents() {
        return fbpComponents;
    }

    public void setFbpComponents(List<EmployeeFBPComponentDTO> fbpComponents) {
        this.fbpComponents = fbpComponents;
    }
}
