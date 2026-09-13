package com.phegondev.usersmanagementsystem.dto.IntegrateWithPayroll;

import java.util.List;

public class EmployeeLeaveRequestDTO {

    private List<String> employeeEmails;
    private String payPeriod;

    public String getPayPeriod() {
        return payPeriod;
    }

    public void setPayPeriod(String payPeriod) {
        this.payPeriod = payPeriod;
    }

    public List<String> getEmployeeEmails() {
        return employeeEmails;
    }

    public void setEmployeeEmails(List<String> employeeEmails) {
        this.employeeEmails = employeeEmails;
    }


}
