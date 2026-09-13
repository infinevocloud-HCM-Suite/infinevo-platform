package com.itsdev.payroll.dto.employee;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EmployeeBenefitDTO {

    private String id;  // maps to benefitId from master
    private String benefitCode; // maps to employee_benefit.benefitCode
    private String name; // maps from benefit.benefitName
    private Boolean enabled;
    private Double amount;
    private Double amountInPercentage;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String organizationId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBenefitCode() {
        return benefitCode;
    }

    public void setBenefitCode(String benefitCode) {
        this.benefitCode = benefitCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getAmountInPercentage() {
        return amountInPercentage;
    }

    public void setAmountInPercentage(Double amountInPercentage) {
        this.amountInPercentage = amountInPercentage;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}
