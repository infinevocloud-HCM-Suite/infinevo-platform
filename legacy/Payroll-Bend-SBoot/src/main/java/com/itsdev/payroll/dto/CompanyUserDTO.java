package com.itsdev.payroll.dto;

import jakarta.validation.constraints.*;

public class CompanyUserDTO {

    private String userId;
 

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String userEmail;
    
    private String phoneNumber;
    private String country;
    private String states;
    private String password;
    private Boolean toc;
    private String firstName;
    private String lastName;
    private String roleId;
    private String roleName;
    private String organizationId;
    private Boolean isEmployeePortalEnable;

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCompanyName() {
        return companyName;
    }
    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getUserEmail() {
        return userEmail;
    }
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }

    public String getStates() {
        return states;
    }
    public void setStates(String states) {
        this.states = states;
    }

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getToc() {
        return toc;
    }
    public void setToc(Boolean toc) {
        this.toc = toc;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public Boolean getIsEmployeePortalEnable() {
        return isEmployeePortalEnable;
    }

    public void setIsEmployeePortalEnable(Boolean employeePortalEnable) {
        this.isEmployeePortalEnable = employeePortalEnable;
    }



    @Override
    public String toString() {
        return "CompanyUserDTO{" +
                "userId='" + userId + '\'' +
                ", companyName='" + companyName + '\'' +
                ", userEmail='" + userEmail + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", country='" + country + '\'' +
                ", states='" + states + '\'' +
                ", password='" + password + '\'' +
                ", toc=" + toc +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", roleId='" + roleId + '\'' +
                ", roleName='" + roleName + '\'' +
                ", organizationId='" + organizationId + '\'' +
                ", isEmployeePortalEnable=" + isEmployeePortalEnable +
                '}';
    }
}