package com.itsdev.payroll.entity.claimsanddeclarations;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class ReimbursementClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean sendMailOnReimbursementClaimRelease;
    private boolean sendMailOnReimbursementClaimLockAndRelease;
    private boolean sendMailOnReimbursementClaimLock;
    private int lastDateForReimbursementClaim;
    private boolean isPayscheduleConfigured;
    private boolean isReimbursementEnabled;
    private boolean sendMailOnReimbursementClaimDateChange;
    private boolean isAnyReminderBeforeLockdateEnabled;

    @OneToOne
    @JoinColumn(name = "organizationId", unique = true)
    private Organization organization;

    @OneToMany(mappedBy = "reimbursementClaim", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reminder> reminders = new ArrayList<>();


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public List<Reminder> getReminders() {
        return reminders;
    }

    public void setReminders(List<Reminder> reminders) {
        this.reminders = reminders;
    }
}