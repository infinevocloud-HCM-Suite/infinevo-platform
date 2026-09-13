package com.itsdev.payroll.dto.taxCalculator;

import org.springframework.web.multipart.MultipartFile;

public class POISubmissionRequest {
    private String declaredItemName; // "pan", "llc", "Adhar"
    private String documentType; // "poi", "pol"
    private MultipartFile file;
    private Integer financialYear;

    // === CONSTRUCTORS ===
    public POISubmissionRequest() {
    }

    public POISubmissionRequest(String declaredItemName, String documentType, Integer financialYear) {
        this.declaredItemName = declaredItemName;
        this.documentType = documentType;
        this.financialYear = financialYear;
    }

    // === GETTERS & SETTERS ===
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

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public Integer getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(Integer financialYear) {
        this.financialYear = financialYear;
    }
}