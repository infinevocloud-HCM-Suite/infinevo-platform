package com.itsdev.payroll.entity.claimsanddeclarations;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class FBP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String lastDateForFbpDeclaration;

    private boolean isFbpDeclarationLocked;
    private boolean sendMailOnFbpDeclarationRelease;
    private boolean sendMailOnFbpDeclarationLock;
    private boolean isPayscheduleConfigured;
    private boolean isFbpAssociatedWithEmployee;
    private boolean isFbpEnabled;
    private boolean isAnyReminderBeforeLockdateEnabled;

    @OneToOne
    @JoinColumn(name = "organizationId", unique = true)
    private Organization organization;

    @OneToMany(mappedBy = "fbp", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reminder> reminders = new ArrayList<>();


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
