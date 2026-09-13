package com.itsdev.payroll.mapper.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ReimbursementClaimDTO;
import com.itsdev.payroll.dto.claimsanddeclarations.ReminderDTO;
import com.itsdev.payroll.entity.claimsanddeclarations.ReimbursementClaim;

import java.util.stream.Collectors;

public class ReimbursementClaimMapper {

    public static ReimbursementClaimDTO toDTO(ReimbursementClaim claim) {
        ReimbursementClaimDTO dto = new ReimbursementClaimDTO();
        dto.setSendMailOnReimbursementClaimRelease(claim.isSendMailOnReimbursementClaimRelease());
        dto.setSendMailOnReimbursementClaimLockAndRelease(claim.isSendMailOnReimbursementClaimLockAndRelease());
        dto.setSendMailOnReimbursementClaimLock(claim.isSendMailOnReimbursementClaimLock());
        dto.setLastDateForReimbursementClaim(claim.getLastDateForReimbursementClaim());
        dto.setPayscheduleConfigured(claim.isPayscheduleConfigured());
        dto.setReimbursementEnabled(claim.isReimbursementEnabled());
        dto.setSendMailOnReimbursementClaimDateChange(claim.isSendMailOnReimbursementClaimDateChange());
        dto.setAnyReminderBeforeLockdateEnabled(claim.isAnyReminderBeforeLockdateEnabled());

        dto.setReminders(
                claim.getReminders().stream().map(r -> {
                    ReminderDTO rd = new ReminderDTO();
                    rd.setReminderId(r.getReminderId());
                    rd.setEnabled(r.isEnabled());
                    rd.setNumberOfDays(r.getNumberOfDays());
                    return rd;
                }).collect(Collectors.toList())
        );
        return dto;
    }
}
