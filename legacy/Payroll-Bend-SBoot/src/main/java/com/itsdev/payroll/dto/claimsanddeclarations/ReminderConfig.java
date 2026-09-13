package com.itsdev.payroll.dto.claimsanddeclarations;

import lombok.Data;

@Data
public class ReminderConfig {
    private boolean isEnabled;
    private int numberOfDays;
    private String reminderId;

    // Constructor matching Zoho response
    public ReminderConfig(boolean isEnabled, int numberOfDays, String reminderId) {
        this.isEnabled = isEnabled;
        this.numberOfDays = numberOfDays;
        this.reminderId = reminderId;
    }
}