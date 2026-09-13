package com.itsdev.payroll.service.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ReminderConfig;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POISettingsDTO;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeeProofOfInvestment;
import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.repository.claimsanddeclarations.ReminderRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.poi.EmployeeProofOfInvestmentRepository;
import com.itsdev.payroll.service.BrevoEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class POIReminderService {

    private final POISettingsService poiSettingsService;
    private final EmployeeProofOfInvestmentRepository poiRepository;
    private final ReminderRepository reminderRepository;
    private final BrevoEmailService brevoEmailService;

    @Transactional
    public void processReminders() {
        log.info("Starting POI reminder processing at {}", LocalDateTime.now());

        LocalDate today = LocalDate.now();

        // Get all organizations with POI enabled
        List<Organization> organizations = poiSettingsService.getOrganizationsWithPOIEnabled();
        log.info("Found {} organizations with POI enabled", organizations.size());

        for (Organization org : organizations) {
            try {
                processRemindersForOrganization(org, today);
            } catch (Exception e) {
                log.error("Error processing reminders for organization {}: {}",
                        org.getOrganizationId(), e.getMessage(), e);
                // Continue with next organization even if one fails
            }
        }

        log.info("Completed POI reminder processing at {}", LocalDateTime.now());
    }

    private void processRemindersForOrganization(Organization org, LocalDate today) {
        String orgId = org.getOrganizationId();

        // Get POI settings for this organization
        POISettingsDTO settings = poiSettingsService.getSettingsDTO(orgId);

        // Check if reminders are enabled
        if (!settings.isAnyReminderBeforeLockdateEnabled()) {
            log.debug("Reminders disabled for organization: {}", orgId);
            return;
        }

        // Parse last date for POI
        String lastDateStr = settings.getLastDateForPoi();
        if (lastDateStr == null || lastDateStr.trim().isEmpty()) {
            log.warn("No last date set for POI in organization: {}", orgId);
            return;
        }

        LocalDate deadline;
        try {
            deadline = LocalDate.parse(lastDateStr);
        } catch (Exception e) {
            log.error("Invalid date format for lastDateForPoi in org {}: {}", orgId, lastDateStr);
            return;
        }

        // Check if deadline has passed
        if (today.isAfter(deadline)) {
            log.debug("Deadline has passed for organization: {}, deadline: {}", orgId, deadline);
            return;
        }

        // Get fiscal year from deadline (assuming deadline is in the fiscal year)
        Integer fiscalYear = deadline.getYear();

        // Process each reminder configuration
        if (settings.getReminders() != null) {
            for (ReminderConfig reminderConfig : settings.getReminders()) {
                if (!reminderConfig.isEnabled()) {
                    continue;
                }

                long daysBefore = reminderConfig.getNumberOfDays();
                LocalDate reminderDate = deadline.minusDays(daysBefore);

                // Check if today is exactly the reminder date
                if (!today.equals(reminderDate)) {
                    continue;
                }

                log.info("Triggering POI reminder | orgId={} | daysBefore={} | deadline={}",
                        orgId, daysBefore, deadline);

                sendRemindersForOrganization(org, deadline, fiscalYear, daysBefore);
            }
        }
    }

    private void sendRemindersForOrganization(
            Organization org,
            LocalDate deadline,
            Integer fiscalYear,
            long daysBefore) {

        String orgId = org.getOrganizationId();

        // FIXED: Use the repository method that works
        // Find all POIs for this organization and fiscal year, then filter
        List<EmployeeProofOfInvestment> allPOIs = poiRepository.findByOrganizationId(orgId);

        // Filter for DRAFT status and correct fiscal year
        List<EmployeeProofOfInvestment> pendingPOIs = allPOIs.stream()
                .filter(poi -> poi.getFiscalYear() != null &&
                        poi.getFiscalYear().equals(fiscalYear) &&
                        poi.getStatus() == PayRunStatus.DRAFT)
                .collect(Collectors.toList());

        log.info("Found {} pending POIs for organization {} (fiscal year {})",
                pendingPOIs.size(), orgId, fiscalYear);

        for (EmployeeProofOfInvestment poi : pendingPOIs) {
            try {
                sendReminderToEmployee(poi, org, deadline, fiscalYear, daysBefore);
            } catch (Exception e) {
                log.error("Failed to send reminder to employee {} in org {}: {}",
                        poi.getEmployee().getEmployeeId(), orgId, e.getMessage(), e);
                // Continue with next employee even if one fails
            }
        }
    }

    private void sendReminderToEmployee(
            EmployeeProofOfInvestment poi,
            Organization org,
            LocalDate deadline,
            Integer fiscalYear,
            long daysBefore) {

        String employeeId = poi.getEmployee().getEmployeeId();
        String orgId = org.getOrganizationId();

        // Check if reminder already sent
        boolean alreadySent = reminderRepository.existsByEmployeeAndOrganizationAndFiscalYearAndDaysBefore(
                employeeId, orgId, fiscalYear, (int) daysBefore);

        if (alreadySent) {
            log.debug("Reminder already sent for employee {} (org {}, {} days before)",
                    employeeId, orgId, daysBefore);
            return;
        }

        // Send email via Brevo
        log.info("Sending POI reminder to employee {} in org {} ({} days before deadline)",
                employeeId, orgId, daysBefore);

        boolean emailSent = brevoEmailService.sendPOIReminder(
                poi.getEmployee(),
                org,
                deadline,
                daysBefore);

        if (emailSent) {
            // Save reminder record
            Reminder reminder = Reminder.createForPOI(
                    poi.getEmployee(),
                    org,
                    fiscalYear,
                    daysBefore);

            reminder.setProofOfInvestment(poi.getOrganization().getProofOfInvestment());
            reminderRepository.save(reminder);

            log.info("Reminder sent and recorded for employee {} (org {}, {} days before)",
                    employeeId, orgId, daysBefore);
        } else {
            log.error("Failed to send email to employee {} in org {}", employeeId, orgId);
        }
    }

    // Helper method to manually trigger reminder for testing
    @Transactional
    public void triggerManualReminder(String organizationId, String employeeId, Integer fiscalYear) {
        Organization org = poiSettingsService.getSettings(organizationId).getOrganization();
        EmployeeProofOfInvestment poi = poiRepository.findByEmployeeIdAndFiscalYear(employeeId, fiscalYear)
                .stream()
                .filter(p -> p.getOrganization().getOrganizationId().equals(organizationId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "POI not found for employee " + employeeId + " in org " + organizationId));

        LocalDate deadline = poiSettingsService.getLastDateAsLocalDate(organizationId);
        if (deadline == null) {
            throw new RuntimeException("No deadline set for organization: " + organizationId);
        }

        // Send reminder for 1 day before (for testing)
        sendReminderToEmployee(poi, org, deadline, fiscalYear, 1);
    }
}