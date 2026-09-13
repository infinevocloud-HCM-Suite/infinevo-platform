package com.itsdev.payroll.dto.employee;

import jakarta.validation.constraints.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BasicDetailsDTO {

    private String id;

    private String employeeId;

    @NotBlank(message = "Employee number cannot be blank")
    private String employeeNumber;

    @NotBlank(message = "First name is required")
    private String firstName;

    private String middleName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    private String gender;

    @NotBlank(message = "Date of joining is required")
    private String dateOfJoining; // Can be LocalDate if needed

    private String departmentId;

    private String designationId;

    private String workLocationId;

    private String departmentName;
    private String designationName;
    private String workLocationName;

    private String employeeStatus;

    // ✅ Boolean fields - only one getter/setter each
    private Boolean isPortalEnabled;
    private Boolean eligibleForPf;
    private Boolean eligibleForPt;
    private Boolean eligibleForLwf;
    private Boolean eligibleForEsi;

    private String esiNumber;

    private Boolean director;
    private Boolean eligibleForEps;
    private Boolean canContributeToEpsOnHigherWages;

    private String hrUser;

    @Pattern(regexp = "\\d{10}", message = "Mobile number must be 10 digits")
    private String mobile;

    @Email(message = "Invalid email format")
    private String workMail;

    // Format: AA/AAA/0000000/XXX/0000000
    @Pattern(regexp = "^[A-Z]{2}/[A-Z]{3}/\\d{7}/[A-Z]{3}/\\d{7}$", message = "PF Account Number must follow the format: AA/AAA/0000000/XXX/0000000")
    private String pfAccountNumber;

    @Pattern(regexp = "^\\d{12}$", message = "UAN must be a 12-digit numeric value")
    private String uan;

    private List<String> tags;

    @NotBlank(message = "Organization ID is required")
    private String organizationId;

    private Double amountInPercentage;

    private String employeeUniqueId;

    private Boolean isDeleted;

    private Map<String, Boolean> completionStatus = new HashMap<>();

    private String fatherName;

    public String getFatherName() {
        return fatherName;
    }

    public void setFatherName(String fatherName) {
        this.fatherName = fatherName;
    }

    public void markStepComplete(String step, boolean isComplete) {
        completionStatus.put(step, isComplete);
    }

    public Map<String, Boolean> getCompletionStatus() {
        return completionStatus;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getEmployeeUniqueId() {
        return employeeUniqueId;
    }

    public void setEmployeeUniqueId(String employeeUniqueId) {
        this.employeeUniqueId = employeeUniqueId;
    }

    // ✅ Getters & Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(String dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDesignationId() {
        return designationId;
    }

    public void setDesignationId(String designationId) {
        this.designationId = designationId;
    }

    public String getWorkLocationId() {
        return workLocationId;
    }

    public void setWorkLocationId(String workLocationId) {
        this.workLocationId = workLocationId;
    }

    public String getEmployeeStatus() {
        return employeeStatus;
    }

    public void setEmployeeStatus(String employeeStatus) {
        this.employeeStatus = employeeStatus;
    }

    public Boolean getIsPortalEnabled() {
        return isPortalEnabled;
    }

    public void setIsPortalEnabled(Boolean isPortalEnabled) {
        this.isPortalEnabled = isPortalEnabled;
    }

    public Boolean getEligibleForPf() {
        return eligibleForPf;
    }

    public void setEligibleForPf(Boolean eligibleForPf) {
        this.eligibleForPf = eligibleForPf;
    }

    public Boolean getEligibleForPt() {
        return eligibleForPt;
    }

    public void setEligibleForPt(Boolean eligibleForPt) {
        this.eligibleForPt = eligibleForPt;
    }

    public Boolean getEligibleForLwf() {
        return eligibleForLwf;
    }

    public void setEligibleForLwf(Boolean eligibleForLwf) {
        this.eligibleForLwf = eligibleForLwf;
    }

    public Boolean getEligibleForEsi() {
        return eligibleForEsi;
    }

    public void setEligibleForEsi(Boolean eligibleForEsi) {
        this.eligibleForEsi = eligibleForEsi;
    }

    public Boolean getDirector() {
        return director;
    }

    public void setDirector(Boolean director) {
        this.director = director;
    }

    public Boolean getEligibleForEps() {
        return eligibleForEps;
    }

    public void setEligibleForEps(Boolean eligibleForEps) {
        this.eligibleForEps = eligibleForEps;
    }

    public Boolean getCanContributeToEpsOnHigherWages() {
        return canContributeToEpsOnHigherWages;
    }

    public void setCanContributeToEpsOnHigherWages(Boolean canContributeToEpsOnHigherWages) {
        this.canContributeToEpsOnHigherWages = canContributeToEpsOnHigherWages;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getWorkMail() {
        return workMail;
    }

    public void setWorkMail(String workMail) {
        this.workMail = workMail;
    }

    public String getPfAccountNumber() {
        return pfAccountNumber;
    }

    public void setPfAccountNumber(String pfAccountNumber) {
        this.pfAccountNumber = pfAccountNumber;
    }

    public String getUan() {
        return uan;
    }

    public void setUan(String uan) {
        this.uan = uan;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public Double getAmountInPercentage() {
        return amountInPercentage;
    }

    public void setAmountInPercentage(Double amountInPercentage) {
        this.amountInPercentage = amountInPercentage;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getDesignationName() {
        return designationName;
    }

    public void setDesignationName(String designationName) {
        this.designationName = designationName;
    }

    public String getWorkLocationName() {
        return workLocationName;
    }

    public void setWorkLocationName(String workLocationName) {
        this.workLocationName = workLocationName;
    }

    public Boolean getPortalEnabled() {
        return isPortalEnabled;
    }

    public void setPortalEnabled(Boolean portalEnabled) {
        isPortalEnabled = portalEnabled;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getHrUser() {
        return hrUser;
    }

    public void setHrUser(String hrUser) {
        this.hrUser = hrUser;
    }

    public String getEsiNumber() {
        return esiNumber;
    }

    public void setEsiNumber(String esiNumber) {
        this.esiNumber = esiNumber;
    }

    @Override
    public String toString() {
        return "BasicDetailsDTO{" +
                "id=" + id +
                ", employeeId='" + employeeId + '\'' +
                ", employeeNumber='" + employeeNumber + '\'' +
                ", firstName='" + firstName + '\'' +
                ", middleName='" + middleName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", gender='" + gender + '\'' +
                ", dateOfJoining='" + dateOfJoining + '\'' +
                ", departmentId='" + departmentId + '\'' +
                ", designationId='" + designationId + '\'' +
                ", workLocationId='" + workLocationId + '\'' +
                ", employeeStatus='" + employeeStatus + '\'' +
                // ", portalEnabled=" + portalEnabled +
                ", isPortalEnabled=" + isPortalEnabled +
                ", eligibleForPf=" + eligibleForPf +
                ", eligibleForPt=" + eligibleForPt +
                ", eligibleForLwf=" + eligibleForLwf +
                ", eligibleForEsi=" + eligibleForEsi +
                ", director=" + director +
                ", eligibleForEps=" + eligibleForEps +
                ", canContributeToEpsOnHigherWages=" + canContributeToEpsOnHigherWages +
                ", mobile='" + mobile + '\'' +
                ", workMail='" + workMail + '\'' +
                ", pfAccountNumber='" + pfAccountNumber + '\'' +
                ", uan='" + uan + '\'' +
                ", tags=" + tags +
                ", organizationId='" + organizationId + '\'' +
                ", amountInPercentage=" + amountInPercentage +
                ", employeeUniqueId='" + employeeUniqueId + '\'' +
                '}';

    }

}