package com.itsdev.payroll.dto.organization;

import jakarta.validation.constraints.NotBlank;

public class WorkLocationDTO {

    private String workLocationId;
    
    @NotBlank(message = "Work location name is required")
    private String workLocationName;
    
    private String streetAddress1;
    private String streetAddress2;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private Boolean isFilingAddress;


    public String getWorkLocationId() {
        return workLocationId;
    }
    public void setWorkLocationId(String workLocationId) {
        this.workLocationId = workLocationId;
    }

    public String getWorkLocationName() {
        return workLocationName;
    }
    public void setWorkLocationName(String workLocationName) {
        this.workLocationName = workLocationName;
    }

    public String getStreetAddress1() {
        return streetAddress1;
    }
    public void setStreetAddress1(String streetAddress1) {
        this.streetAddress1 = streetAddress1;
    }

    public String getStreetAddress2() {
        return streetAddress2;
    }
    public void setStreetAddress2(String streetAddress2) {
        this.streetAddress2 = streetAddress2;
    }

    public String getCity() {
        return city;
    }
    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }
    public void setState(String state) {
        this.state = state;
    }

    public String getZipCode() {
        return zipCode;
    }
    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }

    public Boolean getIsFilingAddress() {
        return isFilingAddress;
    }
    public void setIsFilingAddress(Boolean isFilingAddress) {
        this.isFilingAddress = isFilingAddress;
    }
}