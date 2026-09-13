package com.itsdev.payroll.dto.claimsanddeclarations;

import java.util.List;

public class IncomeTaxDeclarationDTO {
    private List<ReminderDTO> reminders;
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
    private String defaultTaxRegime; // "OLD" or "NEW"



    public List<ReminderDTO> getReminders() { return reminders; }
    public void setReminders(List<ReminderDTO> reminders) { this.reminders = reminders; }

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

    public String getDefaultTaxRegime() {
        return defaultTaxRegime;
    }

    public void setDefaultTaxRegime(String defaultTaxRegime) {
        this.defaultTaxRegime = defaultTaxRegime;
    }

}
