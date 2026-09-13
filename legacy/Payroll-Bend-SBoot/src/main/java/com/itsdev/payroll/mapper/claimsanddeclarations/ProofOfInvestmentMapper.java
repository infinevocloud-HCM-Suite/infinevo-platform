package com.itsdev.payroll.mapper.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ProofOfInvestmentDTO;
import com.itsdev.payroll.dto.claimsanddeclarations.ReminderDTO;
import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;

import java.util.stream.Collectors;

public class ProofOfInvestmentMapper {

    public static ProofOfInvestmentDTO toDTO(ProofOfInvestment entity) {
        ProofOfInvestmentDTO dto = new ProofOfInvestmentDTO();

        dto.setReminders(
                entity.getReminders().stream().map((Reminder r) -> {
                    ReminderDTO rd = new ReminderDTO();
                    rd.setReminderId(r.getReminderId());
                    rd.setEnabled(r.isEnabled());
                    rd.setNumberOfDays(r.getNumberOfDays());
                    return rd;
                }).collect(Collectors.toList())
        );

        dto.setCanChangeTaxRegimePoi(entity.isCanChangeTaxRegimePoi());
        dto.setAttachmentMandatoryPoiForPortal(entity.isAttachmentMandatoryPoiForPortal());
        dto.setSendMailOnPoiRelease(entity.isSendMailOnPoiRelease());
        dto.setSendMailOnPoiLock(entity.isSendMailOnPoiLock());
        dto.setPoiLocked(entity.isPoiLocked());
        dto.setCommentsMandatoryForPoiApproval(entity.isCommentsMandatoryForPoiApproval());
        dto.setAnyReminderBeforeLockdateEnabled(entity.isAnyReminderBeforeLockdateEnabled());
        dto.setSendMailOnEmployeeLevelPoiLockAndRelease(entity.isSendMailOnEmployeeLevelPoiLockAndRelease());
        dto.setAttachmentEnabledForPoi(entity.isAttachmentEnabledForPoi());
        dto.setCanTdsExceedAnnualLimit(entity.isCanTdsExceedAnnualLimit());
        dto.setLastDateForPoi(entity.getLastDateForPoi());
        dto.setCanItOverrideInProofMode(entity.isCanItOverrideInProofMode());
        dto.setMonthFormatted(entity.getMonthFormatted());
        dto.setPayscheduleConfigured(entity.isPayscheduleConfigured());
        dto.setMonthToConsiderPoi(entity.getMonthToConsiderPoi());
        dto.setPanMandatoryForAnnualRentOverOneLakh(entity.isPanMandatoryForAnnualRentOverOneLakh());

        return dto;
    }
}
