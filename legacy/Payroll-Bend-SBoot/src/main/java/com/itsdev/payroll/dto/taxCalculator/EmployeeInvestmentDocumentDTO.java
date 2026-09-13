package com.itsdev.payroll.dto.taxCalculator;

import org.springframework.web.multipart.MultipartFile;

public class EmployeeInvestmentDocumentDTO {

    private String declaredItemName;
    private String documentType;
    private MultipartFile file;
    
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
    
    
    

}

