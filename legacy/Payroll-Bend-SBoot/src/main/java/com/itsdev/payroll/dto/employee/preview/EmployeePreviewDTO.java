package com.itsdev.payroll.dto.employee.preview;


import java.util.List;

public class EmployeePreviewDTO {
    private String employeeId;
    private String employeeNumber;
    private String employeeName;
    private String employeeStatus;

    private Double ctc;              // annual CTC
    private Double grossAmount;      // calculated gross
    private Double monthlySalary;    // calculated monthly

    private List<PreviewEarningDTO> earnings;
    private List<PreviewBenefitDTO> benefits;
    private List<PreviewReimbursementDTO> reimbursements;
    private List<PreviewVariableEarningDTO> variableEarnings;
    private List<PreviewFbpDTO> fbpComponents;

    // totals (computed in service)
    private Double earningsTotal;
    private Double benefitsTotal;
    private Double reimbursementsTotal;
    private Double fbpTotal;

    // --- getters & setters ---


    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
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

    public String getEmployeeStatus() {
        return employeeStatus;
    }

    public void setEmployeeStatus(String employeeStatus) {
        this.employeeStatus = employeeStatus;
    }

    public Double getCtc() {
        return ctc;
    }

    public void setCtc(Double ctc) {
        this.ctc = ctc;
    }

    public Double getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(Double grossAmount) {
        this.grossAmount = grossAmount;
    }

    public Double getMonthlySalary() {
        return monthlySalary;
    }

    public void setMonthlySalary(Double monthlySalary) {
        this.monthlySalary = monthlySalary;
    }

    public List<PreviewEarningDTO> getEarnings() {
        return earnings;
    }

    public void setEarnings(List<PreviewEarningDTO> earnings) {
        this.earnings = earnings;
    }

    public List<PreviewBenefitDTO> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<PreviewBenefitDTO> benefits) {
        this.benefits = benefits;
    }

    public List<PreviewReimbursementDTO> getReimbursements() {
        return reimbursements;
    }

    public void setReimbursements(List<PreviewReimbursementDTO> reimbursements) {
        this.reimbursements = reimbursements;
    }

    public List<PreviewVariableEarningDTO> getVariableEarnings() {
        return variableEarnings;
    }

    public void setVariableEarnings(List<PreviewVariableEarningDTO> variableEarnings) {
        this.variableEarnings = variableEarnings;
    }

    public List<PreviewFbpDTO> getFbpComponents() {
        return fbpComponents;
    }

    public void setFbpComponents(List<PreviewFbpDTO> fbpComponents) {
        this.fbpComponents = fbpComponents;
    }

    public Double getEarningsTotal() {
        return earningsTotal;
    }

    public void setEarningsTotal(Double earningsTotal) {
        this.earningsTotal = earningsTotal;
    }

    public Double getBenefitsTotal() {
        return benefitsTotal;
    }

    public void setBenefitsTotal(Double benefitsTotal) {
        this.benefitsTotal = benefitsTotal;
    }

    public Double getReimbursementsTotal() {
        return reimbursementsTotal;
    }

    public void setReimbursementsTotal(Double reimbursementsTotal) {
        this.reimbursementsTotal = reimbursementsTotal;
    }

    public Double getFbpTotal() {
        return fbpTotal;
    }

    public void setFbpTotal(Double fbpTotal) {
        this.fbpTotal = fbpTotal;
    }
}

