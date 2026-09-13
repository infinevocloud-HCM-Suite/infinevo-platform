package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import java.time.LocalDateTime;
import java.util.List;

import com.itsdev.payroll.enumeration.payruns.PayRunStatus;

public class EmployeeProofOfInvestmentDTO {

	private Long id;
	private Long declarationId;
	private Long organizationId;
	private Long employeeId;
	private Integer fiscalYear;

	private String taxRegimeAtSubmission;
	private PayRunStatus status;

	private String employeeName;
	private String employeeNumber;
	private String lastEditedBy;
	private LocalDateTime lastEditedDate;
	private String submittedBy;
	private LocalDateTime submittedDate;
	private String taxRegime; // "with_exemptions" or "without_exemptions"
	private String taxRegimeFormatted; // "Old Regime" or "New Regime"

	private String approvedBy;
	private LocalDateTime approvedDate;

	private Boolean consideredForIt;

	private List<EmployeePOIItemDTO> poiItems;

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

	public Long getOrganizationId() {
		return organizationId;
	}

	public void setOrganizationId(Long organizationId) {
		this.organizationId = organizationId;
	}

	public Long getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(Long employeeId) {
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

	public String getEmployeeName() {
		return employeeName;
	}

	public void setEmployeeName(String employeeName) {
		this.employeeName = employeeName;
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

	public List<EmployeePOIItemDTO> getPoiItems() {
		return poiItems;
	}

	public void setPoiItems(List<EmployeePOIItemDTO> poiItems) {
		this.poiItems = poiItems;
	}

	public String getTaxRegime() {
		return taxRegime;
	}

	public void setTaxRegime(String taxRegime) {
		this.taxRegime = taxRegime;
	}

	public String getTaxRegimeFormatted() {
		return taxRegimeFormatted;
	}

	public void setTaxRegimeFormatted(String taxRegimeFormatted) {
		this.taxRegimeFormatted = taxRegimeFormatted;
	}

}
