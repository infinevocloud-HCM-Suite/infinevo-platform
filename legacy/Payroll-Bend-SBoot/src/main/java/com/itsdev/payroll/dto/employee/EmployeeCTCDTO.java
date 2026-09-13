package com.itsdev.payroll.dto.employee;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class EmployeeCTCDTO {

    @NotNull
    private String employeeId;  // <-- Add this field

    @NotNull
    private Double ctc; // Annual CTC, matches entity field type

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String organizationId;

    private List<EmployeeEarningDTO> earnings = new ArrayList<>();
    private List<EmployeeVariableEarningDTO> variableEarnings = new ArrayList<>();
    private List<EmployeeBenefitDTO> benefits = new ArrayList<>();
    private List<EmployeeFBPComponentDTO> fbpComponents = new ArrayList<>();
    private List<EmployeeReimbursementDTO> reimbursements = new ArrayList<>();

    private List<EpfComponentDTO> epfComponents;
    private List<EsiComponentDTO> esiComponents;

    private Double monthlySalary;

    private Double previousCtc;
    private Double previousMonthlySalary;

    // optional

    private LocalDate effectiveDate;    // required for revision

    private String revisionReason;      // optional

    private String revisionStatus;



    private Boolean isActive;



    private LocalDateTime createdAt = LocalDateTime.now();


    private LocalDateTime updatedAt;

    private String paymentMonth;  // "yyyy-MM"


    private Long ctcStructureId;

    private Long revisionId;


    private String employeeNumber;
    private String firstName;
    private String middleName;
    private String LastName;

    private BigDecimal changeInPercent;

    private Boolean appliedInPayrun;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal finalAnnualTax;


// getters & setters (system will generate for you)

    public BigDecimal getFinalAnnualTax() {
        return finalAnnualTax;
    }

    public void setFinalAnnualTax(BigDecimal finalAnnualTax) {
        this.finalAnnualTax = finalAnnualTax;
    }


    public Boolean getAppliedInPayrun() {
        return appliedInPayrun;
    }

    public void setAppliedInPayrun(Boolean appliedInPayrun) {
        this.appliedInPayrun = appliedInPayrun;
    }

    public BigDecimal getChangeInPercent() {
        return changeInPercent;
    }

    public void setChangeInPercent(BigDecimal changeInPercent) {
        this.changeInPercent = changeInPercent;
    }

    public String getLastName() {
        return LastName;
    }

    public void setLastName(String lastName) {
        LastName = lastName;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getRevisionStatus() {
        return revisionStatus;
    }

    public void setRevisionStatus(String revisionStatus) {
        this.revisionStatus = revisionStatus;
    }

    public Long getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(Long revisionId) {
        this.revisionId = revisionId;
    }

    public Long getCtcStructureId() {
        return ctcStructureId;
    }

    public void setCtcStructureId(Long ctcStructureId) {
        this.ctcStructureId = ctcStructureId;
    }

    public String getPaymentMonth() {
        return paymentMonth;
    }

    public void setPaymentMonth(String paymentMonth) {
        this.paymentMonth = paymentMonth;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
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

    // Getter
    public Double getMonthlySalary() {
        return monthlySalary;
    }

    // Setter
    public void setMonthlySalary(Double monthlySalary) {
        this.monthlySalary = monthlySalary;
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

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public List<EmployeeEarningDTO> getEarnings() {
        return earnings;
    }

    public void setEarnings(List<EmployeeEarningDTO> earnings) {
        this.earnings = earnings;
    }

    public List<EmployeeVariableEarningDTO> getVariableEarnings() {
        return variableEarnings;
    }

    public void setVariableEarnings(List<EmployeeVariableEarningDTO> variableEarnings) {
        this.variableEarnings = variableEarnings;
    }

    public List<EmployeeBenefitDTO> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<EmployeeBenefitDTO> benefits) {
        this.benefits = benefits;
    }

    public List<EmployeeFBPComponentDTO> getFbpComponents() {
        return fbpComponents;
    }

    public void setFbpComponents(List<EmployeeFBPComponentDTO> fbpComponents) {
        this.fbpComponents = fbpComponents;
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
}






