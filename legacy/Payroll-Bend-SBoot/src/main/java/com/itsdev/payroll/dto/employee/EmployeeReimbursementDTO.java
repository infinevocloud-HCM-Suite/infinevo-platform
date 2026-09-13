package com.itsdev.payroll.dto.employee;

// Reimbursements DTO
public class EmployeeReimbursementDTO {
    private String id;
    private String name;  // mapped from reimbursementCode
    private Boolean enabled;
    private Double amount;
    private String carryForwardOption; // monthly/yearly etc.


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getCarryForwardOption() {
        return carryForwardOption;
    }

    public void setCarryForwardOption(String carryForwardOption) {
        this.carryForwardOption = carryForwardOption;
    }
}
