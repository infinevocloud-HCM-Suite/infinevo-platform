package com.itsdev.payroll.serviceimpl.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ReimbursementClaimDTO;
import com.itsdev.payroll.entity.claimsanddeclarations.ReimbursementClaim;
import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.claimsanddeclarations.ReimbursementClaimMapper;
import com.itsdev.payroll.repository.claimsanddeclarations.ReimbursementClaimRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.claimsanddeclarations.ReimbursementClaimService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Arrays;

@Service
public class ReimbursementClaimServiceImpl implements ReimbursementClaimService {

    private final ReimbursementClaimRepository claimRepository;
    private final OrganizationRepository organizationRepository;

    public ReimbursementClaimServiceImpl(ReimbursementClaimRepository claimRepository,
                                         OrganizationRepository organizationRepository) {
        this.claimRepository = claimRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    @Transactional
    public ReimbursementClaimDTO getReimbursementClaim(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        ReimbursementClaim claim = claimRepository.findByOrganization(org)
                .orElseGet(() -> createDefaultReimbursementClaim(org));

        return ReimbursementClaimMapper.toDTO(claim);
    }

    private ReimbursementClaim createDefaultReimbursementClaim(Organization org) {
        ReimbursementClaim claim = new ReimbursementClaim();
        claim.setOrganization(org);
        claim.setSendMailOnReimbursementClaimRelease(true);
        claim.setSendMailOnReimbursementClaimLockAndRelease(true);
        claim.setSendMailOnReimbursementClaimLock(true);
        claim.setLastDateForReimbursementClaim(31);
        claim.setPayscheduleConfigured(false);
        claim.setReimbursementEnabled(false);
        claim.setSendMailOnReimbursementClaimDateChange(true);
        claim.setAnyReminderBeforeLockdateEnabled(true);

        Reminder r1 = new Reminder();
        r1.setReminderId(generate12DigitId());
        r1.setEnabled(true);
        r1.setNumberOfDays(5);
        r1.setReimbursementClaim(claim);

        Reminder r2 = new Reminder();
        r2.setReminderId(generate12DigitId());
        r2.setEnabled(true);
        r2.setNumberOfDays(1);
        r2.setReimbursementClaim(claim);

        claim.getReminders().addAll(Arrays.asList(r1, r2));

        return claimRepository.save(claim);
    }

    @Override
    @Transactional
    public ReimbursementClaimDTO updateReimbursementClaim(String organizationId, ReimbursementClaimDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        ReimbursementClaim claim = claimRepository.findByOrganization(org)
                .orElseThrow(() -> new RuntimeException("Reimbursement claim not found"));

        // Only update allowed fields
        claim.setSendMailOnReimbursementClaimRelease(dto.isSendMailOnReimbursementClaimRelease());
        claim.setSendMailOnReimbursementClaimLockAndRelease(dto.isSendMailOnReimbursementClaimLockAndRelease());
        claim.setSendMailOnReimbursementClaimLock(dto.isSendMailOnReimbursementClaimLock());
        claim.setLastDateForReimbursementClaim(dto.getLastDateForReimbursementClaim());

        return ReimbursementClaimMapper.toDTO(claimRepository.save(claim));
    }

    private String generate12DigitId() {
        SecureRandom random = new SecureRandom();
        long number = 100000000000L + (long)(random.nextDouble() * 899999999999L);
        return String.valueOf(number);
    }
}