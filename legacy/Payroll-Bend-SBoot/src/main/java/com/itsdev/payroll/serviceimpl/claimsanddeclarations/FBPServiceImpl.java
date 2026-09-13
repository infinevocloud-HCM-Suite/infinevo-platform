package com.itsdev.payroll.serviceimpl.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.FBPDTO;
import com.itsdev.payroll.entity.claimsanddeclarations.FBP;
import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.claimsanddeclarations.FBPMapper;
import com.itsdev.payroll.repository.claimsanddeclarations.FBPRepository;
import com.itsdev.payroll.repository.claimsanddeclarations.ReminderRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.claimsanddeclarations.FBPService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;

@Service
public class FBPServiceImpl implements FBPService {

    private final FBPRepository fbpRepository;
    private final OrganizationRepository organizationRepository;
    private final ReminderRepository reminderRepository;

    public FBPServiceImpl(FBPRepository fbpRepository,
                          OrganizationRepository organizationRepository,
                          ReminderRepository reminderRepository) {
        this.fbpRepository = fbpRepository;
        this.organizationRepository = organizationRepository;
        this.reminderRepository = reminderRepository;
    }

    @Override
    @Transactional
    public FBPDTO getFBP(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        FBP fbp = fbpRepository.findByOrganization(org)
                .orElseGet(() -> createDefaultFBP(org));

        return FBPMapper.toDTO(fbp);
    }

    private FBP createDefaultFBP(Organization org) {
        FBP fbp = new FBP();
        fbp.setOrganization(org);
        fbp.setFbpDeclarationLocked(true);
        fbp.setSendMailOnFbpDeclarationRelease(true);
        fbp.setSendMailOnFbpDeclarationLock(true);
        fbp.setPayscheduleConfigured(true);
        fbp.setFbpAssociatedWithEmployee(true);
        fbp.setFbpEnabled(true);
        fbp.setAnyReminderBeforeLockdateEnabled(true);

        // Create default reminders
        Reminder r1 = new Reminder();
        r1.setReminderId(generate12DigitId());
        r1.setEnabled(true);
        r1.setNumberOfDays(5);
        r1.setFbp(fbp);

        Reminder r2 = new Reminder();
        r2.setReminderId(generate12DigitId());
        r2.setEnabled(true);
        r2.setNumberOfDays(1);
        r2.setFbp(fbp);

        fbp.getReminders().addAll(Arrays.asList(r1, r2));

        return fbpRepository.save(fbp);
    }

    @Override
    @Transactional
    public FBPDTO updateFBP(String organizationId, FBPDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        FBP fbp = fbpRepository.findByOrganization(org)
                .orElseThrow(() -> new RuntimeException("FBP not found"));

        // Only update allowed fields
        fbp.setLastDateForFbpDeclaration(dto.getLastDateForFbpDeclaration());
        fbp.setFbpDeclarationLocked(dto.isFbpDeclarationLocked());
        fbp.setSendMailOnFbpDeclarationRelease(dto.isSendMailOnFbpDeclarationRelease());
        fbp.setSendMailOnFbpDeclarationLock(dto.isSendMailOnFbpDeclarationLock());
        fbp.setFbpEnabled(dto.isFbpEnabled());

        return FBPMapper.toDTO(fbpRepository.save(fbp));
    }

    private String generate12DigitId() {
        SecureRandom random = new SecureRandom();
        long number = 100000000000L + (long)(random.nextDouble() * 899999999999L);
        return String.valueOf(number);
    }
}
