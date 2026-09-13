package com.itsdev.payroll.dto.taxCalculator;

import java.util.List;

import com.itsdev.payroll.entity.taxCalculator.EmployeeInvestmentProof.DocumentStatus;

public class EmployeeInvestmentProofRequest {

	private String employeeId;
    private Integer financialYear;
    private DocumentStatus status;
    private List<EmployeeInvestmentDocumentDTO> documents;
    
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
	public List<EmployeeInvestmentDocumentDTO> getDocuments() {
		return documents;
	}
	public void setDocuments(List<EmployeeInvestmentDocumentDTO> documents) {
		this.documents = documents;
	}
	public DocumentStatus getStatus() {
		return status;
	}
	public void setStatus(DocumentStatus status) {
		this.status = status;
	}
    
    

}
