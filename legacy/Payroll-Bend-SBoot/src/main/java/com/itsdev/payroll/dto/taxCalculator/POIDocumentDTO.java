package com.itsdev.payroll.dto.taxCalculator;

import com.itsdev.payroll.entity.taxCalculator.ProofOfInvestmentDocument;
import java.time.LocalDateTime;

public class POIDocumentDTO {
    private Long id;
    private String declaredItemName;
    private String documentType;
    private String fileName;
    private String fileUrl;
    private ProofOfInvestmentDocument.DocumentStatus status;
    private String remarks;
    private LocalDateTime submittedDate;
    private LocalDateTime reviewedDate;
    private String reviewedBy;
    private Integer financialYear;

    // === CONSTRUCTORS ===
    public POIDocumentDTO() {
    }

    public POIDocumentDTO(Long id, String declaredItemName, String documentType, String fileName,
            String fileUrl, ProofOfInvestmentDocument.DocumentStatus status,
            String remarks, LocalDateTime submittedDate, Integer financialYear) {
        this.id = id;
        this.declaredItemName = declaredItemName;
        this.documentType = documentType;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.status = status;
        this.remarks = remarks;
        this.submittedDate = submittedDate;
        this.financialYear = financialYear;
    }

    // === GETTERS & SETTERS ===
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeclaredItemName() {
        return declaredItemName;
    }

    public void setDeclaredItemName(String declaredItemName) {
        this.declaredItemName = declaredItemName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public ProofOfInvestmentDocument.DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(ProofOfInvestmentDocument.DocumentStatus status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public LocalDateTime getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(LocalDateTime submittedDate) {
        this.submittedDate = submittedDate;
    }

    public LocalDateTime getReviewedDate() {
        return reviewedDate;
    }

    public void setReviewedDate(LocalDateTime reviewedDate) {
        this.reviewedDate = reviewedDate;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Integer getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(Integer financialYear) {
        this.financialYear = financialYear;
    }
}