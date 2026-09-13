package com.itsdev.payroll.dto.taxCalculator;

import java.time.LocalDateTime;
import java.util.List;

import com.itsdev.payroll.entity.taxCalculator.EmployeeInvestmentProof.DocumentStatus;

public class EmployeeInvestmentProofResponse {

    private Long proofId;
    private String employeeId;
    private Integer financialYear;
    private DocumentStatus status;

    private List<InvestmentDocumentResponse> documents;

    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    
    public Long getProofId() {
		return proofId;
	}

	public void setProofId(Long proofId) {
		this.proofId = proofId;
	}

	public String getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(String employeeId) {
		this.employeeId = employeeId;
	}

	public Integer getFinancialYear() {
		return financialYear;
	}

	public void setFinancialYear(Integer financialYear) {
		this.financialYear = financialYear;
	}

	public DocumentStatus getStatus() {
		return status;
	}

	public void setStatus(DocumentStatus status) {
		this.status = status;
	}

	public List<InvestmentDocumentResponse> getDocuments() {
		return documents;
	}

	public void setDocuments(List<InvestmentDocumentResponse> documents) {
		this.documents = documents;
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

	public static class InvestmentDocumentResponse {
        private String declaredItemName;
        private String documentType;
        private String fileName;
        private String fileUrl;
        private Long fileSize;
        private String contentType;
        
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
        
        
        
    }
}

