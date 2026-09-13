package com.itsdev.payroll.service.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.BrevoEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ITDeclarationEmailService {

    private final BrevoEmailService brevoEmailService;
    private final IncomeTaxDeclarationService incomeTaxDeclarationService;
    private final BasicDetailsRepository basicDetailsRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * Send IT Declaration reminder email to specific employee
     * Returns true if email sent successfully
     */
    public boolean sendReminderEmail(
            BasicDetails employee,
            Organization org,
            String lastDateForItDeclaration,
            long daysRemaining) {

        try {
            log.info("Sending IT Declaration reminder to employee {} ({} days before deadline)",
                    employee.getEmployeeId(), daysRemaining);

            // Prepare template parameters
            Map<String, Object> params = new HashMap<>();

            // Employee details
            params.put("employee_name", getEmployeeFullName(employee));
            params.put("employee_id", employee.getEmployeeId());

            // Organization details
            params.put("organization_name", org.getOrganizationName());

            // Deadline details
            LocalDate deadline = LocalDate.parse(lastDateForItDeclaration);
            params.put("deadline_date", formatDate(deadline));
            params.put("days_remaining", String.valueOf(daysRemaining));
            params.put("financial_year", String.valueOf(deadline.getYear()));

            // Additional context
            params.put("current_date", LocalDate.now().format(DateTimeFormatter.ISO_DATE));

            // Reply-to email
            String replyToEmail = null;
            String replyToName = null;
            if (org.getEmail() != null && !org.getEmail().trim().isEmpty()) {
                replyToEmail = org.getEmail().trim();
                replyToName = org.getOrganizationName();
            }

            // Validate employee email
            if (employee.getWorkMail() == null || employee.getWorkMail().trim().isEmpty()) {
                log.warn("Employee {} has no email address, skipping reminder email",
                        employee.getEmployeeId());
                return false;
            }

            // Send email using IT_DECLARATION_REMINDER template
            return brevoEmailService.sendEmail(
                    "IT_DECLARATION_REMINDER", // Template key
                    employee.getWorkMail(),
                    getEmployeeFullName(employee),
                    params,
                    replyToEmail,
                    replyToName);

        } catch (Exception e) {
            log.error("Error sending IT Declaration reminder to employee {}: {}",
                    employee.getEmployeeId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send IT Declaration lock email to all employees (Zoho style)
     * Triggered when admin locks IT Declaration
     */
    @Transactional
    public void sendLockEmails(String organizationId) {
        log.info("Sending IT Declaration lock emails for organization: {}", organizationId);

        try {
            // Get IT Declaration settings
            var settings = incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);

            // Check if emails are enabled
            if (!settings.isSendMailOnItDeclarationLock()) {
                log.info("IT Declaration lock emails disabled for organization: {}", organizationId);
                return;
            }

            // Get organization
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

            // Get all active employees in organization
            List<BasicDetails> employees = basicDetailsRepository
                    .findByOrganization_OrganizationId(organizationId);

            log.info("Found {} employees in organization {}", employees.size(), organizationId);

            // Send email to each employee
            int sentCount = 0;
            int failedCount = 0;

            for (BasicDetails employee : employees) {
                try {
                    boolean sent = sendLockEmailToEmployee(employee, org, settings);
                    if (sent) {
                        sentCount++;
                    } else {
                        failedCount++;
                        log.warn("Failed to send lock email to employee {}", employee.getEmployeeId());
                    }
                } catch (Exception e) {
                    failedCount++;
                    log.error("Error sending lock email to employee {}: {}",
                            employee.getEmployeeId(), e.getMessage());
                }
            }

            log.info("IT Declaration lock emails sent: {} successful, {} failed",
                    sentCount, failedCount);

        } catch (Exception e) {
            log.error("Error sending IT Declaration lock emails for organization {}: {}",
                    organizationId, e.getMessage(), e);
        }
    }

    /**
     * Send IT Declaration release email to all employees (Zoho style)
     * Triggered when admin releases IT Declaration
     */
    @Transactional
    public void sendReleaseEmails(String organizationId) {
        log.info("Sending IT Declaration release emails for organization: {}", organizationId);

        try {
            // Get IT Declaration settings
            var settings = incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);

            // Check if emails are enabled
            if (!settings.isSendMailOnItDeclarationRelease()) {
                log.info("IT Declaration release emails disabled for organization: {}", organizationId);
                return;
            }

            // Get organization
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

            // Get all active employees in organization
            List<BasicDetails> employees = basicDetailsRepository
                    .findByOrganization_OrganizationId(organizationId);

            log.info("Found {} employees in organization {}", employees.size(), organizationId);

            // Send email to each employee
            int sentCount = 0;
            int failedCount = 0;

            for (BasicDetails employee : employees) {
                try {
                    boolean sent = sendReleaseEmailToEmployee(employee, org, settings);
                    if (sent) {
                        sentCount++;
                    } else {
                        failedCount++;
                        log.warn("Failed to send release email to employee {}", employee.getEmployeeId());
                    }
                } catch (Exception e) {
                    failedCount++;
                    log.error("Error sending release email to employee {}: {}",
                            employee.getEmployeeId(), e.getMessage());
                }
            }

            log.info("IT Declaration release emails sent: {} successful, {} failed",
                    sentCount, failedCount);

        } catch (Exception e) {
            log.error("Error sending IT Declaration release emails for organization {}: {}",
                    organizationId, e.getMessage(), e);
        }
    }

    // ============ PRIVATE HELPER METHODS ============

    private boolean sendLockEmailToEmployee(
            BasicDetails employee,
            Organization org,
            com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO settings) {

        try {
            Map<String, Object> params = new HashMap<>();

            // Employee details
            params.put("employee_name", getEmployeeFullName(employee));
            params.put("employee_id", employee.getEmployeeId());

            // Organization details
            params.put("organization_name", org.getOrganizationName());

            // Deadline info (if available)
            String lastDateStr = settings.getLastDateForItDeclaration();

            if (lastDateStr != null && !lastDateStr.isBlank()) {
                LocalDate deadline = LocalDate.parse(lastDateStr);
                params.put("deadline_date", formatDate(deadline));
                params.put("financial_year", String.valueOf(deadline.getYear()));
            }

            // Additional context
            params.put("current_date", LocalDate.now().format(DateTimeFormatter.ISO_DATE));

            // Reply-to email
            String replyToEmail = null;
            String replyToName = null;
            if (org.getEmail() != null && !org.getEmail().trim().isEmpty()) {
                replyToEmail = org.getEmail().trim();
                replyToName = org.getOrganizationName();
            }

            // Validate employee email
            if (employee.getWorkMail() == null || employee.getWorkMail().trim().isEmpty()) {
                log.warn("Employee {} has no email address, skipping lock email",
                        employee.getEmployeeId());
                return false;
            }

            // Send email using IT_DECLARATION_LOCK template
            return brevoEmailService.sendEmail(
                    "IT_DECLARATION_LOCK", // Template key
                    employee.getWorkMail(),
                    getEmployeeFullName(employee),
                    params,
                    replyToEmail,
                    replyToName);

        } catch (Exception e) {
            log.error("Error preparing lock email for employee {}: {}",
                    employee.getEmployeeId(), e.getMessage());
            return false;
        }
    }

    private boolean sendReleaseEmailToEmployee(
            BasicDetails employee,
            Organization org,
            com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO settings) {

        try {
            Map<String, Object> params = new HashMap<>();

            // Employee details
            params.put("employee_name", getEmployeeFullName(employee));
            params.put("employee_id", employee.getEmployeeId());

            // Organization details
            params.put("organization_name", org.getOrganizationName());

            // Deadline info (if available)
            String lastDateStr = settings.getLastDateForItDeclaration();

            if (lastDateStr != null && !lastDateStr.isBlank()) {
                LocalDate deadline = LocalDate.parse(lastDateStr);
                params.put("deadline_date", formatDate(deadline));
                params.put("financial_year", String.valueOf(deadline.getYear()));

                long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.now(), deadline);
                params.put("days_remaining", String.valueOf(daysRemaining));
            }

            // Additional context
            params.put("current_date", LocalDate.now().format(DateTimeFormatter.ISO_DATE));

            // Reply-to email
            String replyToEmail = null;
            String replyToName = null;
            if (org.getEmail() != null && !org.getEmail().trim().isEmpty()) {
                replyToEmail = org.getEmail().trim();
                replyToName = org.getOrganizationName();
            }

            // Validate employee email
            if (employee.getWorkMail() == null || employee.getWorkMail().trim().isEmpty()) {
                log.warn("Employee {} has no email address, skipping release email",
                        employee.getEmployeeId());
                return false;
            }

            // Send email using IT_DECLARATION_RELEASE template
            return brevoEmailService.sendEmail(
                    "IT_DECLARATION_RELEASE", // Template key
                    employee.getWorkMail(),
                    getEmployeeFullName(employee),
                    params,
                    replyToEmail,
                    replyToName);

        } catch (Exception e) {
            log.error("Error preparing release email for employee {}: {}",
                    employee.getEmployeeId(), e.getMessage());
            return false;
        }
    }

    private String getEmployeeFullName(BasicDetails employee) {
        StringBuilder fullName = new StringBuilder();
        if (employee.getFirstName() != null) {
            fullName.append(employee.getFirstName());
        }
        if (employee.getMiddleName() != null && !employee.getMiddleName().isEmpty()) {
            fullName.append(" ").append(employee.getMiddleName());
        }
        if (employee.getLastName() != null && !employee.getLastName().isEmpty()) {
            fullName.append(" ").append(employee.getLastName());
        }
        return fullName.toString().trim();
    }

    private String formatDate(LocalDate date) {
        if (date == null)
            return "N/A";
        return date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
    }

    /**
     * Test method: Send IT Declaration lock email to single employee
     */
    public boolean testLockEmailForEmployee(String organizationId, String employeeId) {
        try {
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

            BasicDetails employee = basicDetailsRepository
                    .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                    .orElseThrow(() -> new RuntimeException(
                            "Employee not found: " + employeeId + " in org " + organizationId));

            IncomeTaxDeclarationDTO settings = incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);

            return sendLockEmailToEmployee(employee, org, settings);

        } catch (Exception e) {
            log.error("Error testing lock email for employee {}: {}", employeeId, e.getMessage());
            return false;
        }
    }

    /**
     * Test method: Send IT Declaration release email to single employee
     */
    public boolean testReleaseEmailForEmployee(String organizationId, String employeeId) {
        try {
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

            BasicDetails employee = basicDetailsRepository
                    .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                    .orElseThrow(() -> new RuntimeException(
                            "Employee not found: " + employeeId + " in org " + organizationId));

            IncomeTaxDeclarationDTO settings = incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);

            return sendReleaseEmailToEmployee(employee, org, settings);

        } catch (Exception e) {
            log.error("Error testing release email for employee {}: {}", employeeId, e.getMessage());
            return false;
        }
    }
}