package com.itsdev.payroll.mapper.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO;
import com.itsdev.payroll.dto.claimsanddeclarations.ReminderDTO;
import com.itsdev.payroll.entity.claimsanddeclarations.IncomeTaxDeclaration;
import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;

import java.util.stream.Collectors;

public class IncomeTaxDeclarationMapper {

    public static IncomeTaxDeclarationDTO toDTO(IncomeTaxDeclaration entity) {
        IncomeTaxDeclarationDTO dto = new IncomeTaxDeclarationDTO();
        dto.setReminders(
                entity.getReminders().stream().map((Reminder r) -> {
                    ReminderDTO rd = new ReminderDTO();
                    rd.setReminderId(r.getReminderId());
                    rd.setEnabled(r.isEnabled());
                    rd.setNumberOfDays(r.getNumberOfDays());
                    return rd;
                }).collect(Collectors.toList())
        );

        dto.setCanTdsExceedAnnualLimit(entity.isCanTdsExceedAnnualLimit());
        dto.setItDeclarationLocked(entity.isItDeclarationLocked());
        dto.setCanChangeTaxRegimeIt(entity.isCanChangeTaxRegimeIt());
        dto.setLastDateForItDeclaration(entity.getLastDateForItDeclaration());
        dto.setSendMailOnEmployeeLevelItLockAndRelease(entity.isSendMailOnEmployeeLevelItLockAndRelease());
        dto.setCurrentPayrun(entity.getCurrentPayrun());
        dto.setSendMailOnItDeclarationLock(entity.isSendMailOnItDeclarationLock());
        dto.setPayscheduleConfigured(entity.isPayscheduleConfigured());
        dto.setSendMailOnItDeclarationRelease(entity.isSendMailOnItDeclarationRelease());
        dto.setAnyReminderBeforeLockdateEnabled(entity.isAnyReminderBeforeLockdateEnabled());
        dto.setPanMandatoryForAnnualRentOverOneLakh(entity.isPanMandatoryForAnnualRentOverOneLakh());
        dto.setDefaultTaxRegime(entity.getDefaultTaxRegime());

        return dto;
    }
}
