package com.itsdev.payroll.serviceimpl.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO;
import com.itsdev.payroll.dto.claimsanddeclarations.ReminderDTO;
import com.itsdev.payroll.entity.claimsanddeclarations.IncomeTaxDeclaration;
import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.claimsanddeclarations.IncomeTaxDeclarationMapper;
import com.itsdev.payroll.repository.claimsanddeclarations.IncomeTaxDeclarationRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.BrevoEmailService;
import com.itsdev.payroll.service.claimsanddeclarations.ITDeclarationEmailService;
import com.itsdev.payroll.service.claimsanddeclarations.IncomeTaxDeclarationService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IncomeTaxDeclarationServiceImpl implements IncomeTaxDeclarationService {

    private final IncomeTaxDeclarationRepository declarationRepository;
    private final OrganizationRepository organizationRepository;
    private final BrevoEmailService brevoEmailService;
    private final ITDeclarationEmailService itDeclarationEmailService;

    public IncomeTaxDeclarationServiceImpl(IncomeTaxDeclarationRepository declarationRepository,
            OrganizationRepository organizationRepository, BrevoEmailService brevoEmailService,
            @Lazy ITDeclarationEmailService itDeclarationEmailService) {
        this.declarationRepository = declarationRepository;
        this.organizationRepository = organizationRepository;
        this.brevoEmailService = brevoEmailService;
        this.itDeclarationEmailService = itDeclarationEmailService;
    }

    // =========================
    // GET Income Tax Declaration
    // =========================
    @Override
    @Transactional
    public IncomeTaxDeclarationDTO getIncomeTaxDeclaration(String organizationId) {

        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        IncomeTaxDeclaration config = declarationRepository.findByOrganization(org)
                .orElseGet(() -> createDefaultIncomeTaxDeclaration(org));

        return IncomeTaxDeclarationMapper.toDTO(config);
    }

    // =========================
    // CREATE default IT Declaration
    // =========================
    private IncomeTaxDeclaration createDefaultIncomeTaxDeclaration(Organization org) {

        IncomeTaxDeclaration cfg = new IncomeTaxDeclaration();
        cfg.setOrganization(org);

        // Default configuration (Zoho-aligned)
        cfg.setCanTdsExceedAnnualLimit(false);
        cfg.setItDeclarationLocked(true);
        cfg.setCanChangeTaxRegimeIt(true);
        cfg.setLastDateForItDeclaration("");
        cfg.setSendMailOnEmployeeLevelItLockAndRelease(true);
        cfg.setCurrentPayrun("");
        cfg.setSendMailOnItDeclarationLock(true);
        cfg.setPayscheduleConfigured(false);
        cfg.setSendMailOnItDeclarationRelease(true);
        cfg.setAnyReminderBeforeLockdateEnabled(true);
        cfg.setPanMandatoryForAnnualRentOverOneLakh(false);

        // Default reminders: 5 days & 1 day
        Reminder r1 = new Reminder();
        r1.setReminderId(generate12DigitId());
        r1.setNumberOfDays(5);
        r1.setEnabled(true);
        r1.setIncomeTaxDeclaration(cfg);

        Reminder r2 = new Reminder();
        r2.setReminderId(generate12DigitId());
        r2.setNumberOfDays(1);
        r2.setEnabled(true);
        r2.setIncomeTaxDeclaration(cfg);

        cfg.getReminders().addAll(Arrays.asList(r1, r2));

        return declarationRepository.save(cfg);
    }

    // =========================
    // PUT Update IT Declaration
    // =========================
    @Override
    @Transactional
    public IncomeTaxDeclarationDTO updateIncomeTaxDeclaration(
            String organizationId,
            IncomeTaxDeclarationDTO dto) {

        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        IncomeTaxDeclaration cfg = declarationRepository.findByOrganization(org)
                .orElseThrow(() -> new RuntimeException("IncomeTaxDeclaration not found"));

        // 1️⃣ Capture old state
        boolean wasLocked = cfg.isItDeclarationLocked();

        // 2️⃣ Update fields
        cfg.setCanTdsExceedAnnualLimit(dto.isCanTdsExceedAnnualLimit());

        cfg.setCanChangeTaxRegimeIt(dto.isCanChangeTaxRegimeIt());

        //  Determine release state transitions
        boolean willBeReleased = wasLocked && !dto.isItDeclarationLocked();
        boolean willRemainReleased = !wasLocked && !dto.isItDeclarationLocked();

        //  Validate lock date whenever declaration is or will be released
        if (willBeReleased || willRemainReleased) {

            String lastDateStr = dto.getLastDateForItDeclaration();

            //  Required check
            if (lastDateStr == null || lastDateStr.isBlank()) {
                throw new IllegalArgumentException(
                        "Last date for IT declaration is required when declaration is released");
            }

            final LocalDate enteredDate;
            try {
                enteredDate = LocalDate.parse(lastDateStr.trim());
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(
                        "Invalid date format for lastDateForItDeclaration. Expected format: yyyy-MM-dd");
            }

            LocalDate today = LocalDate.now();

            // Must be strictly future
            if (!enteredDate.isAfter(today)) {
                throw new IllegalArgumentException(
                        "Last date for IT declaration must be a future date");
            }
        }

        
        cfg.setItDeclarationLocked(dto.isItDeclarationLocked());
        cfg.setLastDateForItDeclaration(dto.getLastDateForItDeclaration());

        cfg.setSendMailOnEmployeeLevelItLockAndRelease(dto.isSendMailOnEmployeeLevelItLockAndRelease());
        cfg.setSendMailOnItDeclarationLock(dto.isSendMailOnItDeclarationLock());
        cfg.setSendMailOnItDeclarationRelease(dto.isSendMailOnItDeclarationRelease());
        cfg.setAnyReminderBeforeLockdateEnabled(dto.isAnyReminderBeforeLockdateEnabled());
        cfg.setPanMandatoryForAnnualRentOverOneLakh(dto.isPanMandatoryForAnnualRentOverOneLakh());

        // Default Tax Regime validation
        if (dto.getDefaultTaxRegime() != null) {

            if (!"OLD".equalsIgnoreCase(dto.getDefaultTaxRegime())
                    && !"NEW".equalsIgnoreCase(dto.getDefaultTaxRegime())) {
                throw new RuntimeException(
                        "Invalid default tax regime. Allowed values: OLD or NEW"
                );
            }

            cfg.setDefaultTaxRegime(dto.getDefaultTaxRegime().toUpperCase());
        }


        // 3️⃣ Update reminders
        if (dto.getReminders() != null) {
            Map<String, Reminder> existingReminderMap = cfg.getReminders().stream()
                    .collect(Collectors.toMap(Reminder::getReminderId, r -> r));

            cfg.getReminders().clear();

            for (ReminderDTO reminderDto : dto.getReminders()) {
                Reminder reminder;

                if (reminderDto.getReminderId() != null
                        && existingReminderMap.containsKey(reminderDto.getReminderId())) {
                    reminder = existingReminderMap.get(reminderDto.getReminderId());
                } else {
                    reminder = new Reminder();
                    reminder.setReminderId(generate12DigitId());
                }

                reminder.setNumberOfDays(reminderDto.getNumberOfDays());
                reminder.setEnabled(reminderDto.isEnabled());
                reminder.setIncomeTaxDeclaration(cfg);

                cfg.getReminders().add(reminder);
            }
        }

        // 4️⃣ Save ONCE
        IncomeTaxDeclaration saved = declarationRepository.save(cfg);

        // 5️⃣ Detect transition
        boolean isLockedNow = saved.isItDeclarationLocked();
        boolean lockAction = !wasLocked && isLockedNow;
        boolean releaseAction = wasLocked && !isLockedNow;

        // 6️⃣ 🔥 Trigger emails (THIS WAS MISSING)
        if (saved.isSendMailOnEmployeeLevelItLockAndRelease()) {

            if (lockAction && saved.isSendMailOnItDeclarationLock()) {
                log.info("Triggering IT Declaration LOCK emails for org {}", organizationId);
                itDeclarationEmailService.sendLockEmails(organizationId);
            }

            if (releaseAction && saved.isSendMailOnItDeclarationRelease()) {
                log.info("Triggering IT Declaration RELEASE emails for org {}", organizationId);
                itDeclarationEmailService.sendReleaseEmails(organizationId);
            }
        }

        // 7️⃣ Return
        return IncomeTaxDeclarationMapper.toDTO(saved);
    }

    // =========================
    // Utility: 12-digit Reminder ID
    // =========================
    private String generate12DigitId() {
        SecureRandom random = new SecureRandom();
        long min = 100_000_000_000L;
        long max = 999_999_999_999L;
        long number = min + (long) (random.nextDouble() * (max - min + 1));
        return String.valueOf(number);
    }
}