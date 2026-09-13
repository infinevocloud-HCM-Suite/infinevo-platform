package com.itsdev.payroll.dto.claimsanddeclarations;

import java.util.List;

public class ProofOfInvestmentDTO {
    private List<ReminderDTO> reminders;
    private boolean canChangeTaxRegimePoi;
    private boolean isAttachmentMandatoryPoiForPortal;
    private boolean sendMailOnPoiRelease;
    private boolean sendMailOnPoiLock;
    private boolean isPoiLocked;
    private boolean isCommentsMandatoryForPoiApproval;
    private boolean isAnyReminderBeforeLockdateEnabled;
    private boolean sendMailOnEmployeeLevelPoiLockAndRelease;
    private boolean isAttachmentEnabledForPoi;
    private boolean canTdsExceedAnnualLimit;
    private String lastDateForPoi;
    private boolean canItOverrideInProofMode;
    private String monthFormatted;
    private boolean isPayscheduleConfigured;
    private String monthToConsiderPoi;
    private boolean isPanMandatoryForAnnualRentOverOneLakh;



    public List<ReminderDTO> getReminders() { return reminders; }
    public void setReminders(List<ReminderDTO> reminders) { this.reminders = reminders; }

    public boolean isCanChangeTaxRegimePoi() { return canChangeTaxRegimePoi; }
    public void setCanChangeTaxRegimePoi(boolean canChangeTaxRegimePoi) { this.canChangeTaxRegimePoi = canChangeTaxRegimePoi; }

    public boolean isAttachmentMandatoryPoiForPortal() { return isAttachmentMandatoryPoiForPortal; }
    public void setAttachmentMandatoryPoiForPortal(boolean attachmentMandatoryPoiForPortal) { isAttachmentMandatoryPoiForPortal = attachmentMandatoryPoiForPortal; }

    public boolean isSendMailOnPoiRelease() { return sendMailOnPoiRelease; }
    public void setSendMailOnPoiRelease(boolean sendMailOnPoiRelease) { this.sendMailOnPoiRelease = sendMailOnPoiRelease; }

    public boolean isSendMailOnPoiLock() { return sendMailOnPoiLock; }
    public void setSendMailOnPoiLock(boolean sendMailOnPoiLock) { this.sendMailOnPoiLock = sendMailOnPoiLock; }

    public boolean isPoiLocked() { return isPoiLocked; }
    public void setPoiLocked(boolean poiLocked) { isPoiLocked = poiLocked; }

    public boolean isCommentsMandatoryForPoiApproval() { return isCommentsMandatoryForPoiApproval; }
    public void setCommentsMandatoryForPoiApproval(boolean commentsMandatoryForPoiApproval) { isCommentsMandatoryForPoiApproval = commentsMandatoryForPoiApproval; }

    public boolean isAnyReminderBeforeLockdateEnabled() { return isAnyReminderBeforeLockdateEnabled; }
    public void setAnyReminderBeforeLockdateEnabled(boolean anyReminderBeforeLockdateEnabled) { isAnyReminderBeforeLockdateEnabled = anyReminderBeforeLockdateEnabled; }

    public boolean isSendMailOnEmployeeLevelPoiLockAndRelease() { return sendMailOnEmployeeLevelPoiLockAndRelease; }
    public void setSendMailOnEmployeeLevelPoiLockAndRelease(boolean sendMailOnEmployeeLevelPoiLockAndRelease) { this.sendMailOnEmployeeLevelPoiLockAndRelease = sendMailOnEmployeeLevelPoiLockAndRelease; }

    public boolean isAttachmentEnabledForPoi() { return isAttachmentEnabledForPoi; }
    public void setAttachmentEnabledForPoi(boolean attachmentEnabledForPoi) { isAttachmentEnabledForPoi = attachmentEnabledForPoi; }

    public boolean isCanTdsExceedAnnualLimit() { return canTdsExceedAnnualLimit; }
    public void setCanTdsExceedAnnualLimit(boolean canTdsExceedAnnualLimit) { this.canTdsExceedAnnualLimit = canTdsExceedAnnualLimit; }

    public String getLastDateForPoi() { return lastDateForPoi; }
    public void setLastDateForPoi(String lastDateForPoi) { this.lastDateForPoi = lastDateForPoi; }

    public boolean isCanItOverrideInProofMode() { return canItOverrideInProofMode; }
    public void setCanItOverrideInProofMode(boolean canItOverrideInProofMode) { this.canItOverrideInProofMode = canItOverrideInProofMode; }

    public String getMonthFormatted() { return monthFormatted; }
    public void setMonthFormatted(String monthFormatted) { this.monthFormatted = monthFormatted; }

    public boolean isPayscheduleConfigured() { return isPayscheduleConfigured; }
    public void setPayscheduleConfigured(boolean payscheduleConfigured) { isPayscheduleConfigured = payscheduleConfigured; }

    public String getMonthToConsiderPoi() { return monthToConsiderPoi; }
    public void setMonthToConsiderPoi(String monthToConsiderPoi) { this.monthToConsiderPoi = monthToConsiderPoi; }

    public boolean isPanMandatoryForAnnualRentOverOneLakh() { return isPanMandatoryForAnnualRentOverOneLakh; }
    public void setPanMandatoryForAnnualRentOverOneLakh(boolean panMandatoryForAnnualRentOverOneLakh) { isPanMandatoryForAnnualRentOverOneLakh = panMandatoryForAnnualRentOverOneLakh; }
}
