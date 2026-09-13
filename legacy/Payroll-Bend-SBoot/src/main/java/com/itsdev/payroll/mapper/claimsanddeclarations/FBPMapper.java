package com.itsdev.payroll.mapper.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.FBPDTO;
import com.itsdev.payroll.dto.claimsanddeclarations.ReminderDTO;
import com.itsdev.payroll.entity.claimsanddeclarations.FBP;

import java.util.stream.Collectors;

public class FBPMapper {

    public static FBPDTO toDTO(FBP fbp) {
        FBPDTO dto = new FBPDTO();
        dto.setLastDateForFbpDeclaration(fbp.getLastDateForFbpDeclaration());
        dto.setFbpDeclarationLocked(fbp.isFbpDeclarationLocked());
        dto.setSendMailOnFbpDeclarationRelease(fbp.isSendMailOnFbpDeclarationRelease());
        dto.setSendMailOnFbpDeclarationLock(fbp.isSendMailOnFbpDeclarationLock());
        dto.setPayscheduleConfigured(fbp.isPayscheduleConfigured());
        dto.setFbpAssociatedWithEmployee(fbp.isFbpAssociatedWithEmployee());
        dto.setFbpEnabled(fbp.isFbpEnabled());
        dto.setAnyReminderBeforeLockdateEnabled(fbp.isAnyReminderBeforeLockdateEnabled());

        dto.setReminders(
                fbp.getReminders().stream()
                        .map(r -> {
                            ReminderDTO rd = new ReminderDTO();
                            rd.setReminderId(r.getReminderId());
                            rd.setEnabled(r.isEnabled());
                            rd.setNumberOfDays(r.getNumberOfDays());
                            return rd;
                        })
                        .collect(Collectors.toList())
        );
        return dto;
    }
}