package com.itsdev.payroll.dto.IntegrateWithHrms;

import java.util.List;

public class LeaveRequestDTO {

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
