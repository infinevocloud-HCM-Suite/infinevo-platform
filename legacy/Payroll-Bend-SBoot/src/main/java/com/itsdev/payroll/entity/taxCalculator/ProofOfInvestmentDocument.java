package com.itsdev.payroll.entity.taxCalculator;

import com.itsdev.payroll.entity.employee.BasicDetails;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "proof_of_investment_document")
public class ProofOfInvestmentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // === DOCUMENT IDENTIFICATION ===
    @Column(name = "declared_item_name", nullable = false)
    private String declaredItemName; // "pan", "llc", "Adhar", "lic" etc.

    @Column(name = "document_type", nullable = false)
    private String documentType; // "poi", "pol" etc.

    // === FILE INFORMATION ===
    @Column(name = "file_name", nullable = false)
    private String fileName; // "Screenshot 2025-11-11 145232.png"

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl; // Cloudinary URL

    @Column(name = "public_id", nullable = false)
    private String publicId; // Cloudinary public_id

    @Column(name = "file_size")
    private Long fileSize; // File size in bytes

    @Column(name = "content_type")
    private String contentType; // image/png, application/pdf

    // === STATUS & WORKFLOW ===
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(length = 1000)
    private String remarks; // HR comments

    @Column(name = "submitted_date", nullable = false)
    private LocalDateTime submittedDate;

    @Column(name = "reviewed_date")
    private LocalDateTime reviewedDate;

    @Column(name = "reviewed_by")
    private String reviewedBy; // HR user who reviewed

    // === RELATIONSHIPS ===
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private BasicDetails employee;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "financial_year", nullable = false)
    private Integer financialYear; // 2025, 2026 etc.

    // === AUDIT FIELDS ===
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "modified_date", nullable = false)
    private LocalDateTime modifiedDate = LocalDateTime.now();

    // === ENUM ===
    public enum DocumentStatus {
        PENDING, APPROVED, REJECTED, DRAFT
    }

    // === CONSTRUCTORS ===
    public ProofOfInvestmentDocument() {
    }

    public ProofOfInvestmentDocument(BasicDetails employee, String organizationId, Integer financialYear) {
        this.employee = employee;
        this.organizationId = organizationId;
        this.financialYear = financialYear;
        this.createdDate = LocalDateTime.now();
        this.modifiedDate = LocalDateTime.now();
        this.submittedDate = LocalDateTime.now();
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

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
        this.modifiedDate = LocalDateTime.now();
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

    public BasicDetails getEmployee() {
        return employee;
    }

    public void setEmployee(BasicDetails employee) {
        this.employee = employee;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public Integer getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(Integer financialYear) {
        this.financialYear = financialYear;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }
}