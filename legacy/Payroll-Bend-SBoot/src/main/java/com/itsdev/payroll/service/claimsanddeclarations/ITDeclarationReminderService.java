package com.itsdev.payroll.service.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO;
import com.itsdev.payroll.dto.claimsanddeclarations.ReminderConfig;
import com.itsdev.payroll.entity.EmployeeITDeclaration.EmployeeInvestmentDeclaration;
import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.repository.claimsanddeclarations.ReminderRepository;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.EmployeeInvestmentDeclarationRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.entity.claimsanddeclarations.IncomeTaxDeclaration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ITDeclarationReminderService {

    private final IncomeTaxDeclarationService incomeTaxDeclarationService;
    private final EmployeeInvestmentDeclarationRepository itDeclarationRepository;
    private final ReminderRepository reminderRepository;
    private final ITDeclarationEmailService itDeclarationEmailService;
    private final OrganizationRepository organizationRepository;
    private final BasicDetailsRepository basicDetailsRepository;

    private List<ReminderConfig> getDefaultReminders() {
        List<ReminderConfig> reminders = new ArrayList<>();
        reminders.add(new ReminderConfig(false, 5, "IT_REMINDER_5_DAY"));
        reminders.add(new ReminderConfig(false, 1, "IT_REMINDER_1_DAY"));
        return reminders;
    }

    /**
     * Process IT Declaration reminders daily (Zoho style)
     * Called by scheduled job
     */
    @Transactional
    public void processReminders() {
        log.info("Starting IT Declaration reminder processing at {}", LocalDateTime.now());

        LocalDate today = LocalDate.now();

        // Get all organizations
        List<Organization> organizations = organizationRepository.findAll();
        log.info("Processing reminders for {} organizations", organizations.size());

        for (Organization org : organizations) {
            try {
                processRemindersForOrganization(org, today);
            } catch (Exception e) {
                log.error("Error processing reminders for organization {}: {}",
                        org.getOrganizationId(), e.getMessage(), e);
                // Continue with next organization
            }
        }

        log.info("Completed IT Declaration reminder processing at {}", LocalDateTime.now());
    }

    private void processRemindersForOrganization(Organization org, LocalDate today) {
        String orgId = org.getOrganizationId();

        try {
            // Get IT Declaration settings for this organization
            IncomeTaxDeclarationDTO settings = incomeTaxDeclarationService.getIncomeTaxDeclaration(orgId);

            // Check if reminders are enabled (Zoho:
            // is_any_reminder_before_lockdate_enabled)
            if (!settings.isAnyReminderBeforeLockdateEnabled()) {
                log.debug("IT Declaration reminders disabled for organization: {}", orgId);
                return;
            }

            // Check if IT Declaration is locked (Zoho: is_it_declaration_locked)
            if (settings.isItDeclarationLocked()) {
                log.debug("IT Declaration is locked for organization: {}", orgId);
                return;
            }

            // Get last date for IT Declaration (Zoho: last_date_for_it_declaration)
            String lastDateStr = settings.getLastDateForItDeclaration();
            if (lastDateStr == null || lastDateStr.trim().isEmpty()) {
                log.warn("No last date set for IT Declaration in organization: {}", orgId);
                return;
            }

            LocalDate deadline;
            try {
                deadline = LocalDate.parse(lastDateStr);
            } catch (Exception e) {
                log.error("Invalid date format for lastDateForItDeclaration in org {}: {}", orgId, lastDateStr);
                return;
            }

            // Check if deadline has passed
            if (today.isAfter(deadline)) {
                log.debug("Deadline has passed for organization: {}, deadline: {}", orgId, deadline);
                return;
            }

            // Get fiscal year from deadline
            Integer fiscalYear = deadline.getYear();

            // Process each reminder configuration (Zoho: reminders array)
            List<ReminderConfig> reminders = getDefaultReminders(); // Create this method
            if (reminders != null) {
                for (ReminderConfig reminderConfig : reminders) {
                    if (!reminderConfig.isEnabled()) {
                        continue;
                    }

                    long daysBefore = reminderConfig.getNumberOfDays();
                    LocalDate reminderDate = deadline.minusDays(daysBefore);

                    // Check if today is exactly the reminder date
                    if (!today.equals(reminderDate)) {
                        continue;
                    }

                    log.info("Triggering IT Declaration reminder | orgId={} | daysBefore={} | deadline={}",
                            orgId, daysBefore, deadline);

                    sendRemindersForOrganization(org, deadline, fiscalYear, daysBefore, settings);
                }
            }
        } catch (Exception e) {
            log.error("Error getting IT Declaration settings for organization {}: {}", orgId, e.getMessage());
            // Skip this organization if settings can't be retrieved
        }
    }

    private void sendRemindersForOrganization(
            Organization org,
            LocalDate deadline,
            Integer fiscalYear,
            long daysBefore,
            IncomeTaxDeclarationDTO settings) {

        String orgId = org.getOrganizationId();

        // Get organization's IT Declaration entity
        IncomeTaxDeclaration itDeclaration = org.getIncomeTaxDeclaration();
        if (itDeclaration == null) {
            log.warn("No IT Declaration settings found for organization: {}", orgId);
            return;
        }

        // Find all employees who haven't submitted IT Declaration or have
        // DRAFT/NOT_CREATED status
        List<EmployeeInvestmentDeclaration> pendingDeclarations = itDeclarationRepository
                .findByOrganization_OrganizationIdAndFiscalYear(org, fiscalYear).stream()
                .filter(decl -> "DRAFT".equals(decl.getStatus()) ||
                        "NOT_CREATED".equals(decl.getStatus()) ||
                        decl.getStatus() == null)
                .collect(Collectors.toList());

        log.info("Found {} pending IT Declarations for organization {} (fiscal year {})",
                pendingDeclarations.size(), orgId, fiscalYear);

        for (EmployeeInvestmentDeclaration declaration : pendingDeclarations) {
            try {
                sendReminderToEmployee(declaration.getEmployee(), org, deadline, fiscalYear,
                        daysBefore, settings, itDeclaration);
            } catch (Exception e) {
                log.error("Failed to send reminder to employee {} in org {}: {}",
                        declaration.getEmployee().getEmployeeId(), orgId, e.getMessage(), e);
                // Continue with next employee
            }
        }

        // Also send to employees who haven't created any declaration
        sendRemindersToEmployeesWithoutDeclaration(org, deadline, fiscalYear, daysBefore, settings, itDeclaration);
    }

    private void sendRemindersToEmployeesWithoutDeclaration(
            Organization org,
            LocalDate deadline,
            Integer fiscalYear,
            long daysBefore,
            IncomeTaxDeclarationDTO settings,
            IncomeTaxDeclaration itDeclaration) {

        String orgId = org.getOrganizationId();

        // Get all active employees in organization
        List<BasicDetails> allEmployees = basicDetailsRepository
                .findByOrganization_OrganizationId(orgId);

        // Get employees who have declarations
        List<String> employeesWithDeclarations = itDeclarationRepository
                .findByOrganization_OrganizationIdAndFiscalYear(org, fiscalYear).stream()
                .map(decl -> decl.getEmployee().getEmployeeId())
                .collect(Collectors.toList());

        // Filter employees without declarations
        List<BasicDetails> employeesWithoutDeclarations = allEmployees.stream()
                .filter(emp -> !employeesWithDeclarations.contains(emp.getEmployeeId()))
                .collect(Collectors.toList());

        log.info("Found {} employees without IT Declarations for organization {} (fiscal year {})",
                employeesWithoutDeclarations.size(), orgId, fiscalYear);

        for (BasicDetails employee : employeesWithoutDeclarations) {
            try {
                sendReminderToEmployee(employee, org, deadline, fiscalYear, daysBefore, settings, itDeclaration);
            } catch (Exception e) {
                log.error("Failed to send reminder to employee {} in org {}: {}",
                        employee.getEmployeeId(), orgId, e.getMessage(), e);
                // Continue with next employee
            }
        }
    }

    private void sendReminderToEmployee(
            BasicDetails employee,
            Organization org,
            LocalDate deadline,
            Integer fiscalYear,
            long daysBefore,
            IncomeTaxDeclarationDTO settings,
            IncomeTaxDeclaration itDeclaration) {

        String employeeId = employee.getEmployeeId();
        String orgId = org.getOrganizationId();

        // Check if reminder already sent (using your repository method)
        boolean alreadySent = reminderRepository.existsByEmployeeAndOrganizationAndFiscalYearAndDaysBefore(
                employeeId, orgId, fiscalYear, (int) daysBefore);

        if (alreadySent) {
            log.debug("IT Declaration reminder already sent for employee {} (org {}, {} days before)",
                    employeeId, orgId, daysBefore);
            return;
        }

        // Send email
        log.info("Sending IT Declaration reminder to employee {} in org {} ({} days before deadline)",
                employeeId, orgId, daysBefore);

        boolean emailSent = itDeclarationEmailService.sendReminderEmail(
                employee, org, settings.getLastDateForItDeclaration(), daysBefore);

        if (emailSent) {
            // Create and save reminder record (Zoho style)
            Reminder reminder = createReminderForITDeclaration(
                    employee, org, fiscalYear, daysBefore, itDeclaration);

            reminderRepository.save(reminder);

            log.info("IT Declaration reminder sent and recorded for employee {} (org {}, {} days before)",
                    employeeId, orgId, daysBefore);
        } else {
            log.error("Failed to send email to employee {} in org {}", employeeId, orgId);
        }
    }

    private Reminder createReminderForITDeclaration(
            BasicDetails employee,
            Organization org,
            Integer fiscalYear,
            long daysBefore,
            IncomeTaxDeclaration itDeclaration) {

        Reminder reminder = new Reminder();
        reminder.setEmployee(employee);
        reminder.setOrganization(org);
        reminder.setFiscalYear(fiscalYear);
        reminder.setNumberOfDays((int) daysBefore);
        reminder.setEnabled(true);
        reminder.setSentDate(LocalDateTime.now());
        reminder.setIncomeTaxDeclaration(itDeclaration);

        // Generate reminder ID similar to Zoho format
        String reminderId = "IT-" + org.getOrganizationId() + "-" +
                employee.getEmployeeId() + "-" + fiscalYear + "-" + daysBefore + "-" +
                System.currentTimeMillis();
        reminder.setReminderId(reminderId);

        reminder.setCreatedTime(LocalDateTime.now());
        reminder.setUpdatedTime(LocalDateTime.now());

        return reminder;
    }

    // Helper method for manual testing (Zoho style)
    @Transactional
    public void triggerManualReminder(String organizationId, String employeeId, Integer fiscalYear) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElseThrow(() -> new RuntimeException(
                        "Employee not found: " + employeeId + " in org " + organizationId));

        IncomeTaxDeclarationDTO settings = incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);

        if (settings.getLastDateForItDeclaration() == null) {
            throw new RuntimeException("No deadline set for organization: " + organizationId);
        }

        LocalDate deadline = LocalDate.parse(settings.getLastDateForItDeclaration());
        long daysBefore = 1; // Default to 1 day before for manual trigger

        sendReminderToEmployee(
                employee,
                org,
                deadline,
                fiscalYear,
                daysBefore,
                settings,
                org.getIncomeTaxDeclaration());

        log.info("Manual IT Declaration reminder triggered for employee {} in org {}",
                employeeId, organizationId);
    }
}