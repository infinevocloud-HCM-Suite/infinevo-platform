package com.itsdev.payroll.dto.claimsanddeclarations;

import java.util.List;

public class ReimbursementClaimDTO {
    private boolean sendMailOnReimbursementClaimRelease;
    private boolean sendMailOnReimbursementClaimLockAndRelease;
    private boolean sendMailOnReimbursementClaimLock;
    private int lastDateForReimbursementClaim;
    private boolean isPayscheduleConfigured;
    private boolean isReimbursementEnabled;
    private boolean sendMailOnReimbursementClaimDateChange;
    private boolean isAnyReminderBeforeLockdateEnabled;
    private List<ReminderDTO> reminders;

    public boolean isSendMailOnReimbursementClaimRelease() {
        return sendMailOnReimbursementClaimRelease;
    }

    public void setSendMailOnReimbursementClaimRelease(boolean sendMailOnReimbursementClaimRelease) {
        this.sendMailOnReimbursementClaimRelease = sendMailOnReimbursementClaimRelease;
    }

    public boolean isSendMailOnReimbursementClaimLockAndRelease() {
        return sendMailOnReimbursementClaimLockAndRelease;
    }

    public void setSendMailOnReimbursementClaimLockAndRelease(boolean sendMailOnReimbursementClaimLockAndRelease) {
        this.sendMailOnReimbursementClaimLockAndRelease = sendMailOnReimbursementClaimLockAndRelease;
    }

    public boolean isSendMailOnReimbursementClaimLock() {
        return sendMailOnReimbursementClaimLock;
    }

    public void setSendMailOnReimbursementClaimLock(boolean sendMailOnReimbursementClaimLock) {
        this.sendMailOnReimbursementClaimLock = sendMailOnReimbursementClaimLock;
    }

    public int getLastDateForReimbursementClaim() {
        return lastDateForReimbursementClaim;
    }

    public void setLastDateForReimbursementClaim(int lastDateForReimbursementClaim) {
        this.lastDateForReimbursementClaim = lastDateForReimbursementClaim;
    }

    public boolean isPayscheduleConfigured() {
        return isPayscheduleConfigured;
    }

    public void setPayscheduleConfigured(boolean payscheduleConfigured) {
        isPayscheduleConfigured = payscheduleConfigured;
    }

    public boolean isReimbursementEnabled() {
        return isReimbursementEnabled;
    }

    public void setReimbursementEnabled(boolean reimbursementEnabled) {
        isReimbursementEnabled = reimbursementEnabled;
    }

    public boolean isSendMailOnReimbursementClaimDateChange() {
        return sendMailOnReimbursementClaimDateChange;
    }

    public void setSendMailOnReimbursementClaimDateChange(boolean sendMailOnReimbursementClaimDateChange) {
        this.sendMailOnReimbursementClaimDateChange = sendMailOnReimbursementClaimDateChange;
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