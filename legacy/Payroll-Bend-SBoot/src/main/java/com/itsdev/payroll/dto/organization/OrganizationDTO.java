package com.itsdev.payroll.dto.organization;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public class OrganizationDTO {
	
	    private String organizationId;
	    
	    @NotBlank(message = "Organization name is required")
	    private String organizationName;

	    private String businessLocation;
	    private String industry;
	    private String addressLine1;
	    private String addressLine2;
	    private String state;
	    private String city;
	    private Boolean hasRunPayroll;
	    private String timezone;
		private String pinCode;
	    private List<WorkLocationDTO> workLocations;

	// pointer to the filing work location (id)
	private String filingWorkLocationId;

	    private String createdBy;
	    private String updatedBy;
	    private LocalDateTime createdDate;
	    private LocalDateTime updatedDate;


	    private Boolean isOrgActive = false;
	    private Boolean isDeleted = false;

		private String email;

	    private String fileName;
	    private String fileUrl;
	    private String filePublicId;

		public String getOrganizationId() {
			return organizationId;
		}

		public void setOrganizationId(String organizationId) {
			this.organizationId = organizationId;
		}

		public String getOrganizationName() {
			return organizationName;
		}

		public void setOrganizationName(String organizationName) {
			this.organizationName = organizationName;
		}

		public String getBusinessLocation() {
			return businessLocation;
		}

		public void setBusinessLocation(String businessLocation) {
			this.businessLocation = businessLocation;
		}

		public String getIndustry() {
			return industry;
		}

		public void setIndustry(String industry) {
			this.industry = industry;
		}

		public String getAddressLine1() {
			return addressLine1;
		}

		public void setAddressLine1(String addressLine1) {
			this.addressLine1 = addressLine1;
		}

		public String getAddressLine2() {
			return addressLine2;
		}

		public void setAddressLine2(String addressLine2) {
			this.addressLine2 = addressLine2;
		}

		public String getState() {
			return state;
		}

		public void setState(String state) {
			this.state = state;
		}

		public String getCity() {
			return city;
		}

		public void setCity(String city) {
			this.city = city;
		}

		public Boolean getHasRunPayroll() {
			return hasRunPayroll;
		}

		public void setHasRunPayroll(Boolean hasRunPayroll) {
			this.hasRunPayroll = hasRunPayroll;
		}

		public String getTimezone() {
			return timezone;
		}

		public void setTimezone(String timezone) {
			this.timezone = timezone;
		}

	    public String getPinCode() { return pinCode; }

	    public void setPinCode(String pinCode) { this.pinCode = pinCode; }

		public String getcreatedBy() {
			return createdBy;
		}

		public void setcreatedBy(String createdBy) {
			this.createdBy = createdBy;
		}

		public String getupdatedBy() {
			return updatedBy;
		}

		public void setupdatedBy(String updatedBy) {
			this.updatedBy = updatedBy;
		}

		public LocalDateTime getcreatedDate() {
			return createdDate;
		}

		public void setcreatedDate(LocalDateTime createdDate) {
			this.createdDate = createdDate;
		}

		public LocalDateTime getupdatedDate() {
			return updatedDate;
		}

		public void setupdatedDate(LocalDateTime updatedDate) {
			this.updatedDate = updatedDate;
		}

	    public Boolean getIsOrgActive() {return isOrgActive;}

	    public void setIsOrgActive(Boolean isOrgActive) {this.isOrgActive = isOrgActive;}

	    public Boolean getIsDeleted() {return isDeleted;}

	    public void setIsDeleted(Boolean isDeleted) {this.isDeleted = isDeleted;}

	    public String getEmail() {
		return email;
	}
	    public void setEmail(String email) {
		this.email = email;
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

	    public String getFilePublicId() {
		return filePublicId;
	}

	    public void setFilePublicId(String filePublicId) {
		this.filePublicId = filePublicId;
	}

	    public List<WorkLocationDTO> getWorkLocations() { return workLocations; }

	    public void setWorkLocations(List<WorkLocationDTO> workLocations) { this.workLocations = workLocations; }

	public String getFilingWorkLocationId() {
		return filingWorkLocationId;
	}

	public void setFilingWorkLocationId(String filingWorkLocationId) {
		this.filingWorkLocationId = filingWorkLocationId;
	}
}
