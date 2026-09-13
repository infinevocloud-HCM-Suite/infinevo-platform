package com.itsdev.payroll.service;

import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.config.EmailTemplateConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.api.url}")
    private String apiUrl;

    @Value("${brevo.email.sender.name}")
    private String senderName;

    @Value("${brevo.email.sender.email}")
    private String senderEmail;

    @Value("${frontend.base-url}")
    private String frontendBaseUrl;

    // NEW: Template configuration injected
    private final EmailTemplateConfig templateConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    // ==================== GENERIC EMAIL METHOD ====================

    /**
     * Generic method to send any email template
     * 
     * @param templateKey    Key from EmailTemplateConfig (e.g., "POI_REMINDER")
     * @param recipientEmail To email address
     * @param recipientName  To name
     * @param templateParams Map of variables for template
     * @param replyToEmail   Optional reply-to email
     * @param replyToName    Optional reply-to name
     * @return true if email sent successfully
     */
    public boolean sendEmail(
            String templateKey,
            String recipientEmail,
            String recipientName,
            Map<String, Object> templateParams,
            String replyToEmail,
            String replyToName) {

        try {
            // Validate inputs
            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                log.error("Recipient email is required");
                return false;
            }

            // Get template ID dynamically from config
            Long templateId = templateConfig.getTemplateId(templateKey);

            log.info("Sending email: Template={}(ID:{}) To={}",
                    templateKey, templateId, recipientEmail);

            // Build payload
            Map<String, Object> payload = buildEmailPayload(
                    templateId, recipientEmail, recipientName,
                    templateParams, replyToEmail, replyToName);

            // Send with retry logic
            return sendWithRetry(payload, recipientEmail);

        } catch (Exception e) {
            log.error("Error sending email: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean sendHtmlEmail(
            String recipientEmail,
            String recipientName,
            String subject,
            String htmlContent) {
        try {
            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                log.error("Recipient email is required");
                return false;
            }

            log.info("Sending custom HTML email: Subject='{}' To={}", subject, recipientEmail);

            Map<String, Object> payload = buildHtmlEmailPayload(
                    recipientEmail, recipientName, subject, htmlContent);

            return sendWithRetry(payload, recipientEmail);
        } catch (Exception e) {
            log.error("Error sending HTML email: {}", e.getMessage(), e);
            return false;
        }
    }

    private Map<String, Object> buildHtmlEmailPayload(
            String recipientEmail,
            String recipientName,
            String subject,
            String htmlContent) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("subject", subject);
        payload.put("htmlContent", htmlContent);

        // Recipient
        Map<String, String> recipient = new HashMap<>();
        recipient.put("email", recipientEmail);
        recipient.put("name", recipientName != null ? recipientName : "User");
        payload.put("to", List.of(recipient));

        // Sender
        Map<String, String> sender = new HashMap<>();
        sender.put("name", senderName);
        sender.put("email", senderEmail);
        payload.put("sender", sender);

        return payload;
    }

    // ==================== POI REMINDER (Backward Compatible) ====================

    public boolean sendPOIReminder(
            BasicDetails employeeDetails,
            Organization org,
            LocalDate deadline,
            long daysRemaining) {

        try {
            // Validate inputs
            if (employeeDetails == null || employeeDetails.getWorkMail() == null) {
                log.error("Employee details or email is null");
                return false;
            }

            String employeeEmail = employeeDetails.getWorkMail();
            if (employeeEmail == null || employeeEmail.trim().isEmpty()) {
                log.error("Employee email is empty for employee: {}",
                        employeeDetails.getEmployeeId());
                return false;
            }

            log.info("Sending POI reminder email to {} ({})",
                    employeeEmail, employeeDetails.getEmployeeId());

            // ✅ CRITICAL: Use "params." prefix for ALL parameters
            Map<String, Object> params = new HashMap<>();

            // Employee details - with params. prefix
            params.put("params.employee_name", getEmployeeFullName(employeeDetails));
            params.put("params.employee_id", employeeDetails.getEmployeeId());
            params.put("params.employee_number", employeeDetails.getEmployeeNumber());

            // Organization details
            params.put("params.organization_name", org.getOrganizationName());

            // ✅ FROM YOUR TEMPLATE: {{ params.financial_year }}, {{ params.deadline_date
            // }}, {{ params.days_remaining }}
            params.put("params.financial_year", (deadline.getYear() - 1) + "-" + deadline.getYear()); // "2024-2025"
            params.put("params.deadline_date", formatDateWithSuffix(deadline)); // "31st March 2024"
            params.put("params.days_remaining", daysRemaining + " days"); // "5 days"

            // Additional context
            params.put("params.current_date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));
            params.put("params.deadline_passed", daysRemaining <= 0 ? "YES" : "NO");

            // Determine reply-to
            String replyToEmail = null;
            String replyToName = null;
            if (org.getEmail() != null && !org.getEmail().trim().isEmpty()) {
                replyToEmail = org.getEmail().trim();
                replyToName = org.getOrganizationName();
            }

            // Use the generic sendEmail method
            return sendEmail(
                    "POI_REMINDER",
                    employeeEmail,
                    getEmployeeFullName(employeeDetails),
                    params,
                    replyToEmail,
                    replyToName);

        } catch (Exception e) {
            log.error("Unexpected error sending POI reminder email: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Object> buildEmailPayload(
            Long templateId,
            String recipientEmail,
            String recipientName,
            Map<String, Object> templateParams,
            String replyToEmail,
            String replyToName) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("templateId", templateId);

        // Recipient
        Map<String, String> recipient = new HashMap<>();
        recipient.put("email", recipientEmail);
        recipient.put("name", recipientName != null ? recipientName : "User");
        payload.put("to", List.of(recipient));

        // Sender
        Map<String, String> sender = new HashMap<>();
        sender.put("name", senderName);
        sender.put("email", senderEmail);
        payload.put("sender", sender);

        // Template parameters
        if (templateParams != null && !templateParams.isEmpty()) {
            payload.put("params", templateParams);
        }

        // Reply-to (only if email is provided)
        if (replyToEmail != null && !replyToEmail.trim().isEmpty()) {
            Map<String, String> replyTo = new HashMap<>();
            replyTo.put("email", replyToEmail.trim());
            replyTo.put("name", replyToName != null ? replyToName : "HR Team");
            payload.put("replyTo", replyTo);
        }

        return payload;
    }

    private boolean sendWithRetry(Map<String, Object> payload, String recipientEmail) {
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("api-key", apiKey);
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                        apiUrl, request, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Email sent successfully to {} (attempt {})",
                            recipientEmail, attempt);
                    return true;
                } else {
                    log.warn("Failed to send email to {}: HTTP {} (attempt {})",
                            recipientEmail, response.getStatusCode(), attempt);
                }
            } catch (HttpClientErrorException e) {
                log.error("Client error sending email to {}: {} (attempt {})",
                        recipientEmail, e.getMessage(), attempt);
                if (attempt == maxRetries)
                    return false;
            } catch (HttpServerErrorException e) {
                log.error("Server error sending email to {}: {} (attempt {})",
                        recipientEmail, e.getMessage(), attempt);
                if (attempt == maxRetries)
                    return false;
            } catch (ResourceAccessException e) {
                log.error("Connection error sending email to {}: {} (attempt {})",
                        recipientEmail, e.getMessage(), attempt);
                if (attempt == maxRetries)
                    return false;
            }

            // Wait before retry (exponential backoff)
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(1000 * attempt); // 1s, 2s, 3s
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
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

    // ==================== EXAMPLE FUTURE METHOD ====================

    /**
     * EXAMPLE: How to add a new email type in future
     * Uncomment and modify when needed
     * 
     * public boolean sendForgotPasswordEmail(String email, String resetLink) {
     * Map<String, Object> params = new HashMap<>();
     * params.put("RESET_LINK", resetLink);
     * 
     * return sendEmail(
     * "FORGOT_PASSWORD", // Add this key to EmailTemplateConfig first
     * email,
     * "User",
     * params,
     * null, // No reply-to needed
     * null
     * );
     * }
     */

    /**
     * Send POI submission confirmation email
     */
    public boolean sendPOISubmissionEmail(
            BasicDetails employeeDetails,
            Organization org,
            Integer fiscalYear) {

        try {
            // Validate inputs
            if (employeeDetails == null || employeeDetails.getWorkMail() == null) {
                log.error("Employee details or email is null");
                return false;
            }

            String employeeEmail = employeeDetails.getWorkMail();
            if (employeeEmail == null || employeeEmail.trim().isEmpty()) {
                log.error("Employee email is empty for employee: {}",
                        employeeDetails.getEmployeeId());
                return false;
            }

            log.info("Sending POI submission email to {} ({}) for FY {}",
                    employeeEmail, employeeDetails.getEmployeeId(), fiscalYear);

            // Prepare template parameters
            Map<String, Object> params = new HashMap<>();

            // Employee details
            params.put("employee_name", getEmployeeFullName(employeeDetails));
            params.put("employee_number", employeeDetails.getEmployeeNumber());

            // Organization details
            params.put("organization_name", org.getOrganizationName());

            // Fiscal year
            params.put("financial_year", String.valueOf(fiscalYear));

            // Submission details
            params.put("submission_date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));
            params.put("submission_time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));

            // Determine reply-to
            String replyToEmail = null;
            String replyToName = null;
            if (org.getEmail() != null && !org.getEmail().trim().isEmpty()) {
                replyToEmail = org.getEmail().trim();
                replyToName = org.getOrganizationName();
            }

            // Use the generic sendEmail method
            return sendEmail(
                    "POI_SUBMISSION",
                    employeeEmail,
                    getEmployeeFullName(employeeDetails),
                    params,
                    replyToEmail,
                    replyToName);

        } catch (Exception e) {
            log.error("Unexpected error sending POI submission email: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean sendSalarySlipEmail(
            BasicDetails employee,
            Organization org,
            Map<String, Object> params) {
        try {
            if (employee.getWorkMail() == null || employee.getWorkMail().trim().isEmpty()) {
                log.warn("Employee {} has no email, skipping salary slip mail",
                        employee.getEmployeeId());
                return false;
            }

            String replyToEmail = org.getEmail();
            String replyToName = org.getOrganizationName();

            return sendEmail(
                    "SALARY_SLIP",
                    employee.getWorkMail(),
                    employee.getFirstName() + " " + employee.getLastName(),
                    params,
                    replyToEmail,
                    replyToName);

        } catch (Exception e) {
            log.error("Failed to send salary slip email to {}",
                    employee.getEmployeeId(), e);
            return false;
        }
    }

    private String formatDateWithSuffix(LocalDate date) {
        if (date == null)
            return "N/A";

        int day = date.getDayOfMonth();
        String suffix = getDaySuffix(day);

        DateTimeFormatter monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy");
        String monthYear = date.format(monthYearFormatter);

        return day + suffix + " " + monthYear; // "31st March 2024"
    }

    private String getDaySuffix(int day) {
        if (day >= 11 && day <= 13)
            return "th";
        switch (day % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }

    /**
     * Send employee invitation email
     */
    public boolean sendEmployeeInvitationEmail(
            String employeeEmail,
            String employeeName,
            String organizationName,
            String invitedBy,
            String userRole,
            String expiryDate,
            String acceptanceToken,
            String organizationId) { // ✅ ADD organizationId

        Map<String, Object> params = new HashMap<>();
        // ✅ MUST use params. prefix for ALL parameters
        params.put("organization_name", organizationName);
        params.put("invited_by", invitedBy);
        params.put("user_email", employeeEmail);
        params.put("user_role", userRole);
        params.put("expiry_date", expiryDate);

        // ✅ NEW URL format: /accept-invite?user=email&type=EMPLOYEE&orgId=123
        String acceptanceUrl = frontendBaseUrl + "/accept-invite" +
                "?user=" + URLEncoder.encode(employeeEmail, StandardCharsets.UTF_8) +
                "&type=EMPLOYEE" +
                "&orgId=" + organizationId; // ✅ Use organizationId
        params.put("acceptance_url", acceptanceUrl);

        log.info("Acceptance URL for {}: {}", employeeEmail, acceptanceUrl);

        return sendEmail("EMPLOYEE_INVITATION", employeeEmail, employeeName, params, null, null);
    }

    /**
     * Send user invitation email
     */
    public boolean sendUserInvitationEmail(
            String userEmail,
            String userName,
            String organizationName,
            String invitedBy,
            String userRole,
            String expiryDate,
            String acceptanceToken,
            String organizationId) { // ✅ ADD organizationId parameter

        Map<String, Object> params = new HashMap<>();
        // ✅ MUST use params. prefix for ALL parameters
        params.put("organization_name", organizationName);
        params.put("invited_by", invitedBy);
        params.put("user_email", userEmail);
        params.put("user_role", userRole);
        params.put("expiry_date", expiryDate);

        // ✅ NEW URL format: /accept-invite?user=email&type=USER&orgId=123
        String acceptanceUrl = frontendBaseUrl + "/accept-invite" + // or your production URL
                "?user=" + URLEncoder.encode(userEmail, StandardCharsets.UTF_8) +
                "&type=USER" +
                "&orgId=" + organizationId; // ✅ Use organizationId
        params.put("acceptance_url", acceptanceUrl);

        log.info("Acceptance URL for {}: {}", userEmail, acceptanceUrl);

        return sendEmail("USER_INVITATION", userEmail, userName, params, null, null);
    }

    /**
     * Send employee credentials email
     */
    public boolean sendEmployeeCredentialsEmail(
            String employeeEmail,
            String employeeName,
            String generatedPassword) {

        Map<String, Object> params = new HashMap<>();
        params.put("username", employeeEmail);
        params.put("temp_password", generatedPassword);

        return sendEmail(
                "EMPLOYEE_CREDENTIALS",
                employeeEmail,
                employeeName,
                params,
                null,
                null);
    }

    /**
     * Send user credentials email
     */
    public boolean sendUserCredentialsEmail(
            String userEmail,
            String userName,
            String generatedPassword) {

        Map<String, Object> params = new HashMap<>();
        params.put("username", userEmail);
        params.put("temp_password", generatedPassword);

        return sendEmail(
                "USER_CREDENTIALS",
                userEmail,
                userName,
                params,
                null,
                null);
    }

    /**
     * Helper method to calculate expiry date (7 days from now)
     */
//    public String calculateExpiryDate() {
//        java.time.LocalDateTime expiryDate = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusMinutes(5);
//        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm a");
//        return expiryDate.format(formatter);
//    }

    public String calculateExpiryDate() {
        java.time.LocalDateTime expiryDate =
                java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                        .plusDays(7);

        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm a");

        return expiryDate.format(formatter);
    }

}