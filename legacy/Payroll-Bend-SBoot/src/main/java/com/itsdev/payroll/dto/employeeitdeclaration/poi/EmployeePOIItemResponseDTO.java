package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import com.itsdev.payroll.enumeration.payruns.PayRunStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class EmployeePOIItemResponseDTO {
    private Long id;
    private String investmentType;
    private Long section6aItemId;
    private BigDecimal declaredAmount;
    private BigDecimal actualAmount;
    private BigDecimal approvedAmount;
    private PayRunStatus status;
    private String adminComment;
    private String itemIdExternal;
    private List<EmployeePOIDocumentResponseDTO> documents;

    // Additional fields for UI
    private String investmentTypeName;
    private String sectionName;
    private BigDecimal maxLimit;
    private Boolean hasDocuments;
    private Boolean canUploadDocument;
    private Boolean canEdit;
    private Boolean adminAdjusted;
    private String amountAdjustedBy;
    private LocalDateTime amountAdjustedDate;

    // Default constructor
    public EmployeePOIItemResponseDTO() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvestmentType() {
        return investmentType;
    }

    public void setInvestmentType(String investmentType) {
        this.investmentType = investmentType;
    }

    public Long getSection6aItemId() {
        return section6aItemId;
    }

    public void setSection6aItemId(Long section6aItemId) {
        this.section6aItemId = section6aItemId;
    }

    public BigDecimal getDeclaredAmount() {
        return declaredAmount;
    }

    public void setDeclaredAmount(BigDecimal declaredAmount) {
        this.declaredAmount = declaredAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public PayRunStatus getStatus() {
        return status;
    }

    public void setStatus(PayRunStatus status) {
        this.status = status;
    }

    public String getAdminComment() {
        return adminComment;
    }

    public void setAdminComment(String adminComment) {
        this.adminComment = adminComment;
    }

    public String getItemIdExternal() {
        return itemIdExternal;
    }

    public void setItemIdExternal(String itemIdExternal) {
        this.itemIdExternal = itemIdExternal;
    }

    public List<EmployeePOIDocumentResponseDTO> getDocuments() {
        return documents;
    }

    public void setDocuments(List<EmployeePOIDocumentResponseDTO> documents) {
        this.documents = documents;
    }

    public String getInvestmentTypeName() {
        return investmentTypeName;
    }

    public void setInvestmentTypeName(String investmentTypeName) {
        this.investmentTypeName = investmentTypeName;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public BigDecimal getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(BigDecimal maxLimit) {
        this.maxLimit = maxLimit;
    }

    public Boolean getHasDocuments() {
        return hasDocuments;
    }

    public void setHasDocuments(Boolean hasDocuments) {
        this.hasDocuments = hasDocuments;
    }

    public Boolean getCanUploadDocument() {
        return canUploadDocument;
    }

    public void setCanUploadDocument(Boolean canUploadDocument) {
        this.canUploadDocument = canUploadDocument;
    }

    public Boolean getCanEdit() {
        return canEdit;
    }

    public void setCanEdit(Boolean canEdit) {
        this.canEdit = canEdit;
    }

    public Boolean getAdminAdjusted() {
        return adminAdjusted;
    }

    public void setAdminAdjusted(Boolean adminAdjusted) {
        this.adminAdjusted = adminAdjusted;
    }

    public String getAmountAdjustedBy() {
        return amountAdjustedBy;
    }

    public void setAmountAdjustedBy(String amountAdjustedBy) {
        this.amountAdjustedBy = amountAdjustedBy;
    }

    public LocalDateTime getAmountAdjustedDate() {
        return amountAdjustedDate;
    }

    public void setAmountAdjustedDate(LocalDateTime amountAdjustedDate) {
        this.amountAdjustedDate = amountAdjustedDate;
    }
}