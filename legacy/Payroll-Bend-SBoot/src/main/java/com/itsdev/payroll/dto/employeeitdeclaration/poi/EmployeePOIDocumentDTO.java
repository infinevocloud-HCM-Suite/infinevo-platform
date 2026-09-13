package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import java.time.LocalDateTime;


public class EmployeePOIDocumentDTO {

    private Long id;
    private String documentName;
    private String documentUrl;
    private String uploadedBy;
    private LocalDateTime uploadedTime;
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getDocumentName() {
		return documentName;
	}
	public void setDocumentName(String documentName) {
		this.documentName = documentName;
	}
	public String getDocumentUrl() {
		return documentUrl;
	}
	public void setDocumentUrl(String documentUrl) {
		this.documentUrl = documentUrl;
	}
	public String getUploadedBy() {
		return uploadedBy;
	}
	public void setUploadedBy(String uploadedBy) {
		this.uploadedBy = uploadedBy;
	}
	public LocalDateTime getUploadedTime() {
		return uploadedTime;
	}
	public void setUploadedTime(LocalDateTime uploadedTime) {
		this.uploadedTime = uploadedTime;
	}
    
    
}
