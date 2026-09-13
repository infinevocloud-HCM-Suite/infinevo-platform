package com.itsdev.payroll.dto.employee;



import java.time.LocalDate;
import java.util.List;

public class EmployeePersonalDetailDTO {

    private String id;
    private String personalMail;
    private LocalDate dateOfBirth;
    private String fatherName;
    private String pan;
    private String differentlyAbledType;
    private Boolean isEligibleForFullIncomeTaxExemption;
    private ResidentialAddressDTO presentResidentialAddress;
    private List<CustomFieldDTO> customFields;
    private String organizationId;

    private String employeeId;   // 🔹 link back to employee


    // Getters & Setters


    public Boolean getEligibleForFullIncomeTaxExemption() {
        return isEligibleForFullIncomeTaxExemption;
    }

    public void setEligibleForFullIncomeTaxExemption(Boolean eligibleForFullIncomeTaxExemption) {
        isEligibleForFullIncomeTaxExemption = eligibleForFullIncomeTaxExemption;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getPersonalMail() {
        return personalMail;
    }
    public void setPersonalMail(String personalMail) {
        this.personalMail = personalMail;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }
    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getFatherName() {
        return fatherName;
    }
    public void setFatherName(String fatherName) {
        this.fatherName = fatherName;
    }

    public String getPan() {
        return pan;
    }
    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getDifferentlyAbledType() {
        return differentlyAbledType;
    }
    public void setDifferentlyAbledType(String differentlyAbledType) {
        this.differentlyAbledType = differentlyAbledType;
    }

    public Boolean getIsEligibleForFullIncomeTaxExemption() {
        return isEligibleForFullIncomeTaxExemption;
    }
    public void setIsEligibleForFullIncomeTaxExemption(Boolean isEligibleForFullIncomeTaxExemption) {
        this.isEligibleForFullIncomeTaxExemption = isEligibleForFullIncomeTaxExemption;
    }

    public ResidentialAddressDTO getPresentResidentialAddress() {
        return presentResidentialAddress;
    }
    public void setPresentResidentialAddress(ResidentialAddressDTO presentResidentialAddress) {
        this.presentResidentialAddress = presentResidentialAddress;
    }

    public List<CustomFieldDTO> getCustomFields() {
        return customFields;
    }
    public void setCustomFields(List<CustomFieldDTO> customFields) {
        this.customFields = customFields;
    }

    public String getOrganizationId() {
        return organizationId;
    }
    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}

