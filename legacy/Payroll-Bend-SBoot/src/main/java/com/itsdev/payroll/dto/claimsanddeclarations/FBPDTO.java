package com.itsdev.payroll.dto.claimsanddeclarations;

import java.util.List;

public class FBPDTO {
    private String lastDateForFbpDeclaration;
    private boolean isFbpDeclarationLocked;
    private boolean sendMailOnFbpDeclarationRelease;
    private boolean sendMailOnFbpDeclarationLock;
    private boolean isPayscheduleConfigured;
    private boolean isFbpAssociatedWithEmployee;
    private boolean isFbpEnabled;
    private boolean isAnyReminderBeforeLockdateEnabled;
    private List<ReminderDTO> reminders;

    public String getLastDateForFbpDeclaration() {
        return lastDateForFbpDeclaration;
    }

    public void setLastDateForFbpDeclaration(String lastDateForFbpDeclaration) {
        this.lastDateForFbpDeclaration = lastDateForFbpDeclaration;
    }

    public boolean isFbpDeclarationLocked() {
        return isFbpDeclarationLocked;
    }

    public void setFbpDeclarationLocked(boolean fbpDeclarationLocked) {
        isFbpDeclarationLocked = fbpDeclarationLocked;
    }

    public boolean isSendMailOnFbpDeclarationRelease() {
        return sendMailOnFbpDeclarationRelease;
    }

    public void setSendMailOnFbpDeclarationRelease(boolean sendMailOnFbpDeclarationRelease) {
        this.sendMailOnFbpDeclarationRelease = sendMailOnFbpDeclarationRelease;
    }

    public boolean isSendMailOnFbpDeclarationLock() {
        return sendMailOnFbpDeclarationLock;
    }

    public void setSendMailOnFbpDeclarationLock(boolean sendMailOnFbpDeclarationLock) {
        this.sendMailOnFbpDeclarationLock = sendMailOnFbpDeclarationLock;
    }

    public boolean isPayscheduleConfigured() {
        return isPayscheduleConfigured;
    }

    public void setPayscheduleConfigured(boolean payscheduleConfigured) {
        isPayscheduleConfigured = payscheduleConfigured;
    }

    public boolean isFbpAssociatedWithEmployee() {
        return isFbpAssociatedWithEmployee;
    }

    public void setFbpAssociatedWithEmployee(boolean fbpAssociatedWithEmployee) {
        isFbpAssociatedWithEmployee = fbpAssociatedWithEmployee;
    }

    public boolean isFbpEnabled() {
        return isFbpEnabled;
    }

    public void setFbpEnabled(boolean fbpEnabled) {
        isFbpEnabled = fbpEnabled;
    }

    public boolean isAnyReminderBeforeLockdateEnabled() {
        return isAnyReminderBeforeLockdateEnabled;
    }

    public void setAnyReminderBeforeLockdateEnabled(boolean anyReminderBeforeLockdateEnabled) {
        isAnyReminderBeforeLockdateEnabled = anyReminderBeforeLockdateEnabled;
    }

    public List<ReminderDTO> getReminders() {
        return reminders;
    }

    public void setReminders(List<ReminderDTO> reminders) {
        this.reminders = reminders;
    }
}
