package com.itsdev.payroll.entity.claimsanddeclarations;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class ProofOfInvestment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @OneToOne
    @JoinColumn(name = "organizationId", unique = true)
    private Organization organization;

    @OneToMany(mappedBy = "proofOfInvestment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reminder> reminders = new ArrayList<>();

    // Add helper method to add reminder
    public void addReminder(Reminder reminder) {
        reminders.add(reminder);
        reminder.setProofOfInvestment(this);
    }

    // Add helper method to remove reminder
    public void removeReminder(Reminder reminder) {
        reminders.remove(reminder);
        reminder.setProofOfInvestment(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public boolean isPoiLocked() {
        return isPoiLocked;
    }

    public void setPoiLocked(boolean poiLocked) {
        isPoiLocked = poiLocked;
    }

    public boolean isCommentsMandatoryForPoiApproval() {
        return isCommentsMandatoryForPoiApproval;
    }

    public void setCommentsMandatoryForPoiApproval(boolean commentsMandatoryForPoiApproval) {
        isCommentsMandatoryForPoiApproval = commentsMandatoryForPoiApproval;
    }

    public boolean isAnyReminderBeforeLockdateEnabled() {
        return isAnyReminderBeforeLockdateEnabled;
    }

    public void setAnyReminderBeforeLockdateEnabled(boolean anyReminderBeforeLockdateEnabled) {
        isAnyReminderBeforeLockdateEnabled = anyReminderBeforeLockdateEnabled;
    }

    public boolean isSendMailOnEmployeeLevelPoiLockAndRelease() {
        return sendMailOnEmployeeLevelPoiLockAndRelease;
    }

    public void setSendMailOnEmployeeLevelPoiLockAndRelease(boolean sendMailOnEmployeeLevelPoiLockAndRelease) {
        this.sendMailOnEmployeeLevelPoiLockAndRelease = sendMailOnEmployeeLevelPoiLockAndRelease;
    }

    public boolean isAttachmentEnabledForPoi() {
        return isAttachmentEnabledForPoi;
    }

    public void setAttachmentEnabledForPoi(boolean attachmentEnabledForPoi) {
        isAttachmentEnabledForPoi = attachmentEnabledForPoi;
    }

    public boolean isCanTdsExceedAnnualLimit() {
        return canTdsExceedAnnualLimit;
    }

    public void setCanTdsExceedAnnualLimit(boolean canTdsExceedAnnualLimit) {
        this.canTdsExceedAnnualLimit = canTdsExceedAnnualLimit;
    }

    public String getLastDateForPoi() {
        return lastDateForPoi;
    }

    public void setLastDateForPoi(String lastDateForPoi) {
        this.lastDateForPoi = lastDateForPoi;
    }

    public boolean isCanItOverrideInProofMode() {
        return canItOverrideInProofMode;
    }

    public void setCanItOverrideInProofMode(boolean canItOverrideInProofMode) {
        this.canItOverrideInProofMode = canItOverrideInProofMode;
    }

    public String getMonthFormatted() {
        return monthFormatted;
    }

    public void setMonthFormatted(String monthFormatted) {
        this.monthFormatted = monthFormatted;
    }

    public boolean isPayscheduleConfigured() {
        return isPayscheduleConfigured;
    }

    public void setPayscheduleConfigured(boolean payscheduleConfigured) {
        isPayscheduleConfigured = payscheduleConfigured;
    }

    public String getMonthToConsiderPoi() {
        return monthToConsiderPoi;
    }

    public void setMonthToConsiderPoi(String monthToConsiderPoi) {
        this.monthToConsiderPoi = monthToConsiderPoi;
    }

    public boolean isPanMandatoryForAnnualRentOverOneLakh() {
        return isPanMandatoryForAnnualRentOverOneLakh;
    }

    public void setPanMandatoryForAnnualRentOverOneLakh(boolean panMandatoryForAnnualRentOverOneLakh) {
        isPanMandatoryForAnnualRentOverOneLakh = panMandatoryForAnnualRentOverOneLakh;
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
