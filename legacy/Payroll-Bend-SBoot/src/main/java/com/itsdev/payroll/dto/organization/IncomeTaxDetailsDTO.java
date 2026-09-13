package com.itsdev.payroll.dto.organization;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public class IncomeTaxDetailsDTO {


    @NotBlank(message = "TAN number is required")
    @Size(min = 10, max = 10, message = "TAN must be exactly 10 characters")
    @Pattern(regexp = "^[A-Za-z0-9]{10}$", message = "TAN must be alphanumeric (10 characters)")
    private String tanNumber;

    @NotBlank(message = "PAN number is required")
    @Size(min = 10, max = 10, message = "PAN must be exactly 10 characters")
    @Pattern(regexp = "^[A-Za-z0-9]{10}$", message = "PAN must be alphanumeric (10 characters)")
    private String panNumber;
    
    @Pattern(
    	    regexp = "^[A-Z]{3}/[A-Z]{2}/\\d{3}/\\d{2}$",
    	    message = "TDS Circle must follow the format: AAA/AA/000/00"
    	)
    private String tdsCircle;
    
    private String authorizedPersonName;
    private String authorizedPersonParent;
    private String authorizedPersonDesignation;
    private String depositSchedule;
    private String employeeId;


    public String getTanNumber() { return tanNumber; }
    public void setTanNumber(String tanNumber) { this.tanNumber = tanNumber; }

    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }

    public String getTdsCircle() { return tdsCircle; }
    public void setTdsCircle(String tdsCircle) { this.tdsCircle = tdsCircle; }

    public String getAuthorizedPersonName() { return authorizedPersonName; }
    public void setAuthorizedPersonName(String authorizedPersonName) { this.authorizedPersonName = authorizedPersonName; }

    public String getAuthorizedPersonParent() { return authorizedPersonParent; }
    public void setAuthorizedPersonParent(String authorizedPersonParent) { this.authorizedPersonParent = authorizedPersonParent; }

    public String getAuthorizedPersonDesignation() { return authorizedPersonDesignation; }
    public void setAuthorizedPersonDesignation(String authorizedPersonDesignation) { this.authorizedPersonDesignation = authorizedPersonDesignation; }

    public String getDepositSchedule() { return depositSchedule; }
    public void setDepositSchedule(String depositSchedule) { this.depositSchedule = depositSchedule; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
}
