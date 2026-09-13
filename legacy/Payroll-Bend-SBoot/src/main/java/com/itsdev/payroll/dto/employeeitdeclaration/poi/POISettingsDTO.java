package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import com.itsdev.payroll.dto.claimsanddeclarations.ReminderConfig;
import java.util.List;

public class POISettingsDTO {
    private boolean canChangeTaxRegimePoi;
    private boolean isAttachmentMandatoryPoiForPortal;
    private boolean isPoiLocked;
    private boolean isAttachmentEnabledForPoi;
    private String lastDateForPoi; // YYYY-MM-DD format
    private boolean isCommentsMandatoryForPoiApproval;
    private boolean canItOverrideInProofMode;
    private boolean isPanMandatoryForAnnualRentOverOneLakh;

    // Email settings from Zoho
    private boolean sendMailOnPoiRelease;
    private boolean sendMailOnPoiLock;
    private boolean sendMailOnEmployeeLevelPoiLockAndRelease;

    // Reminder settings from Zoho
    private boolean isAnyReminderBeforeLockdateEnabled;
    private List<ReminderConfig> reminders;

    // Getters and Setters
    public boolean isCanChangeTaxRegimePoi() {
        return canChangeTaxRegimePoi;
    }

    public void setCanChangeTaxRegimePoi(boolean canChangeTaxRegimePoi) {
        this.canChangeTaxRegimePoi = canChangeTaxRegimePoi;
    }

    public boolean isAttachmentMandatoryPoiForPortal() {
        return isAttachmentMandatoryPoiForPortal;
    }

    public void setAttachmentMandatoryPoiForPortal(boolean attachmentMandatoryPoiForPortal) {
        isAttachmentMandatoryPoiForPortal = attachmentMandatoryPoiForPortal;
    }

    public boolean isPoiLocked() {
        return isPoiLocked;
    }

    public void setPoiLocked(boolean poiLocked) {
        isPoiLocked = poiLocked;
    }

    public boolean isAttachmentEnabledForPoi() {
        return isAttachmentEnabledForPoi;
    }

    public void setAttachmentEnabledForPoi(boolean attachmentEnabledForPoi) {
        isAttachmentEnabledForPoi = attachmentEnabledForPoi;
    }

    public String getLastDateForPoi() {
        return lastDateForPoi;
    }

    public void setLastDateForPoi(String lastDateForPoi) {
        this.lastDateForPoi = lastDateForPoi;
    }

    public boolean isCommentsMandatoryForPoiApproval() {
        return isCommentsMandatoryForPoiApproval;
    }

    public void setCommentsMandatoryForPoiApproval(boolean commentsMandatoryForPoiApproval) {
        isCommentsMandatoryForPoiApproval = commentsMandatoryForPoiApproval;
    }

    public boolean isCanItOverrideInProofMode() {
        return canItOverrideInProofMode;
    }

    public void setCanItOverrideInProofMode(boolean canItOverrideInProofMode) {
        this.canItOverrideInProofMode = canItOverrideInProofMode;
    }

    public boolean isPanMandatoryForAnnualRentOverOneLakh() {
        return isPanMandatoryForAnnualRentOverOneLakh;
    }

    public void setPanMandatoryForAnnualRentOverOneLakh(boolean panMandatoryForAnnualRentOverOneLakh) {
        isPanMandatoryForAnnualRentOverOneLakh = panMandatoryForAnnualRentOverOneLakh;
    }

    public boolean isSendMailOnPoiRelease() {
        return sendMailOnPoiRelease;
    }

    public void setSendMailOnPoiRelease(boolean sendMailOnPoiRelease) {
        this.sendMailOnPoiRelease = sendMailOnPoiRelease;
    }

    public boolean isSendMailOnPoiLock() {
        return sendMailOnPoiLock;
    }

    public void setSendMailOnPoiLock(boolean sendMailOnPoiLock) {
        this.sendMailOnPoiLock = sendMailOnPoiLock;
    }

    public boolean isSendMailOnEmployeeLevelPoiLockAndRelease() {
        return sendMailOnEmployeeLevelPoiLockAndRelease;
    }

    public void setSendMailOnEmployeeLevelPoiLockAndRelease(boolean sendMailOnEmployeeLevelPoiLockAndRelease) {
        this.sendMailOnEmployeeLevelPoiLockAndRelease = sendMailOnEmployeeLevelPoiLockAndRelease;
    }

    public boolean isAnyReminderBeforeLockdateEnabled() {
        return isAnyReminderBeforeLockdateEnabled;
    }

    public void setAnyReminderBeforeLockdateEnabled(boolean anyReminderBeforeLockdateEnabled) {
        isAnyReminderBeforeLockdateEnabled = anyReminderBeforeLockdateEnabled;
    }

    public List<ReminderConfig> getReminders() {
        return reminders;
    }

    public void setReminders(List<ReminderConfig> reminders) {
        this.reminders = reminders;
    }
}