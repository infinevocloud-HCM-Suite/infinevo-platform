package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import com.itsdev.payroll.enumeration.payruns.PayRunStatus;

import java.time.LocalDateTime;
import java.util.List;

public class EmployeeProofOfInvestmentResponseDTO {
    private Long id;
    private Long declarationId;
    private String organizationId;
    private String employeeId;
    private Integer fiscalYear;
    private String taxRegimeAtSubmission;
    private PayRunStatus status;
    private String employeeName;
    private String employeeNumber;
    private String lastEditedBy;
    private LocalDateTime lastEditedDate;
    private String submittedBy;
    private LocalDateTime submittedDate;
    private String approvedBy;
    private LocalDateTime approvedDate;
    private Boolean consideredForIt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private List<EmployeePOIItemResponseDTO> poiItems;

    // Additional fields for UI
    private String employeeCode;
    private String organizationName;
    private Boolean canEdit;
    private Boolean canSubmit;
    private Boolean canWithdraw;

    // Default constructor
    public EmployeeProofOfInvestmentResponseDTO() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeclarationId() {
        return declarationId;
    }

    public void setDeclarationId(Long declarationId) {
        this.declarationId = declarationId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
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

    public String getTaxRegimeAtSubmission() {
        return taxRegimeAtSubmission;
    }

    public void setTaxRegimeAtSubmission(String taxRegimeAtSubmission) {
        this.taxRegimeAtSubmission = taxRegimeAtSubmission;
    }

    public PayRunStatus getStatus() {
        return status;
    }

    public void setStatus(PayRunStatus status) {
        this.status = status;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getLastEditedBy() {
        return lastEditedBy;
    }

    public void setLastEditedBy(String lastEditedBy) {
        this.lastEditedBy = lastEditedBy;
    }

    public LocalDateTime getLastEditedDate() {
        return lastEditedDate;
    }

    public void setLastEditedDate(LocalDateTime lastEditedDate) {
        this.lastEditedDate = lastEditedDate;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public LocalDateTime getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(LocalDateTime submittedDate) {
        this.submittedDate = submittedDate;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(LocalDateTime approvedDate) {
        this.approvedDate = approvedDate;
    }

    public Boolean getConsideredForIt() {
        return consideredForIt;
    }

    public void setConsideredForIt(Boolean consideredForIt) {
        this.consideredForIt = consideredForIt;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public List<EmployeePOIItemResponseDTO> getPoiItems() {
        return poiItems;
    }

    public void setPoiItems(List<EmployeePOIItemResponseDTO> poiItems) {
        this.poiItems = poiItems;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public Boolean getCanEdit() {
        return canEdit;
    }

    public void setCanEdit(Boolean canEdit) {
        this.canEdit = canEdit;
    }

    public Boolean getCanSubmit() {
        return canSubmit;
    }

    public void setCanSubmit(Boolean canSubmit) {
        this.canSubmit = canSubmit;
    }

    public Boolean getCanWithdraw() {
        return canWithdraw;
    }

    public void setCanWithdraw(Boolean canWithdraw) {
        this.canWithdraw = canWithdraw;
    }
}