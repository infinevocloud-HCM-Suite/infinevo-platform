package com.itsdev.payroll.entity.claimsanddeclarations;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class IncomeTaxDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean canTdsExceedAnnualLimit;
    private boolean isItDeclarationLocked;
    private boolean canChangeTaxRegimeIt;
    private String lastDateForItDeclaration;
    private boolean sendMailOnEmployeeLevelItLockAndRelease;
    private String currentPayrun;
    private boolean sendMailOnItDeclarationLock;
    private boolean isPayscheduleConfigured;
    private boolean sendMailOnItDeclarationRelease;
    private boolean isAnyReminderBeforeLockdateEnabled;
    private boolean isPanMandatoryForAnnualRentOverOneLakh;

    @OneToOne
    @JoinColumn(name = "organizationId", unique = true)
    private Organization organization;

    @OneToMany(mappedBy = "incomeTaxDeclaration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reminder> reminders = new ArrayList<>();

    @Column(name = "default_tax_regime")
    private String defaultTaxRegime; // "OLD" or "NEW"


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public boolean isCanTdsExceedAnnualLimit() { return canTdsExceedAnnualLimit; }
    public void setCanTdsExceedAnnualLimit(boolean canTdsExceedAnnualLimit) { this.canTdsExceedAnnualLimit = canTdsExceedAnnualLimit; }

    public boolean isItDeclarationLocked() { return isItDeclarationLocked; }
    public void setItDeclarationLocked(boolean itDeclarationLocked) { isItDeclarationLocked = itDeclarationLocked; }

    public boolean isCanChangeTaxRegimeIt() { return canChangeTaxRegimeIt; }
    public void setCanChangeTaxRegimeIt(boolean canChangeTaxRegimeIt) { this.canChangeTaxRegimeIt = canChangeTaxRegimeIt; }

    public String getLastDateForItDeclaration() { return lastDateForItDeclaration; }
    public void setLastDateForItDeclaration(String lastDateForItDeclaration) { this.lastDateForItDeclaration = lastDateForItDeclaration; }

    public boolean isSendMailOnEmployeeLevelItLockAndRelease() { return sendMailOnEmployeeLevelItLockAndRelease; }
    public void setSendMailOnEmployeeLevelItLockAndRelease(boolean sendMailOnEmployeeLevelItLockAndRelease) { this.sendMailOnEmployeeLevelItLockAndRelease = sendMailOnEmployeeLevelItLockAndRelease; }

    public String getCurrentPayrun() { return currentPayrun; }
    public void setCurrentPayrun(String currentPayrun) { this.currentPayrun = currentPayrun; }

    public boolean isSendMailOnItDeclarationLock() { return sendMailOnItDeclarationLock; }
    public void setSendMailOnItDeclarationLock(boolean sendMailOnItDeclarationLock) { this.sendMailOnItDeclarationLock = sendMailOnItDeclarationLock; }

    public boolean isPayscheduleConfigured() { return isPayscheduleConfigured; }
    public void setPayscheduleConfigured(boolean payscheduleConfigured) { isPayscheduleConfigured = payscheduleConfigured; }

    public boolean isSendMailOnItDeclarationRelease() { return sendMailOnItDeclarationRelease; }
    public void setSendMailOnItDeclarationRelease(boolean sendMailOnItDeclarationRelease) { this.sendMailOnItDeclarationRelease = sendMailOnItDeclarationRelease; }

    public boolean isAnyReminderBeforeLockdateEnabled() { return isAnyReminderBeforeLockdateEnabled; }
    public void setAnyReminderBeforeLockdateEnabled(boolean anyReminderBeforeLockdateEnabled) { isAnyReminderBeforeLockdateEnabled = anyReminderBeforeLockdateEnabled; }

    public boolean isPanMandatoryForAnnualRentOverOneLakh() { return isPanMandatoryForAnnualRentOverOneLakh; }
    public void setPanMandatoryForAnnualRentOverOneLakh(boolean panMandatoryForAnnualRentOverOneLakh) { isPanMandatoryForAnnualRentOverOneLakh = panMandatoryForAnnualRentOverOneLakh; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public List<Reminder> getReminders() { return reminders; }
    public void setReminders(List<Reminder> reminders) { this.reminders = reminders; }

    public String getDefaultTaxRegime() {
        return defaultTaxRegime;
    }

    public void setDefaultTaxRegime(String defaultTaxRegime) {
        this.defaultTaxRegime = defaultTaxRegime;
    }
}
