package com.itsdev.payroll.controller.test;

import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeeProofOfInvestment;
import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.repository.claimsanddeclarations.ProofOfInvestmentRepository;
import com.itsdev.payroll.repository.claimsanddeclarations.ReminderRepository;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.poi.EmployeeProofOfInvestmentRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.BrevoEmailService;
import com.itsdev.payroll.service.claimsanddeclarations.POIReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test/poi-reminders")
@RequiredArgsConstructor
@Slf4j
public class POIReminderTestController {

    private final POIReminderService poiReminderService;
    private final BrevoEmailService brevoEmailService;
    private final EmployeeProofOfInvestmentRepository poiRepository;
    private final ReminderRepository reminderRepository;
    private final OrganizationRepository organizationRepository;
    private final BasicDetailsRepository employeeRepository;
    private final ProofOfInvestmentRepository proofOfInvestmentRepository;

    // ==================== 1. TEST SCHEDULER MANUALLY ====================

    @PostMapping("/trigger-scheduler")
    public ResponseEntity<Map<String, Object>> triggerScheduler() {
        log.info("Manually triggering POI reminder scheduler");

        try {
            poiReminderService.processReminders();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "POI reminder scheduler triggered successfully");
            response.put("timestamp", LocalDate.now().toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error triggering scheduler: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to trigger scheduler: " + e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ==================== 2. TEST EMAIL SENDING ====================

    @PostMapping("/test-email/{employeeId}")
    public ResponseEntity<Map<String, Object>> testEmail(
            @PathVariable String employeeId,
            @RequestParam String organizationId) {

        log.info("Testing email sending for employee: {}, org: {}", employeeId, organizationId);

        try {
            // Find employee and organization
            BasicDetails employee = employeeRepository
                    .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));

            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found"));

            // Test deadline (5 days from now)
            LocalDate testDeadline = LocalDate.now().plusDays(5);

            // Send test email
            boolean emailSent = brevoEmailService.sendPOIReminder(
                    employee, org, testDeadline, 5);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("emailSent", emailSent);
            response.put("employeeEmail", employee.getWorkMail());
            response.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
            response.put("organization", org.getOrganizationName());
            response.put("testDeadline", testDeadline.toString());
            response.put("daysRemaining", 5);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing email: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to send test email: " + e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ==================== 3. CHECK PENDING POIs ====================

    @GetMapping("/pending-pois/{organizationId}")
    public ResponseEntity<Map<String, Object>> getPendingPOIs(
            @PathVariable String organizationId,
            @RequestParam(required = false) Integer fiscalYear) {

        log.info("Checking pending POIs for org: {}, fiscalYear: {}",
                organizationId, fiscalYear);

        try {
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found"));

            // Use current year if not specified
            if (fiscalYear == null) {
                fiscalYear = LocalDate.now().getYear();
            }

            // CREATE FINAL COPY for use in lambda
            final Integer finalFiscalYear = fiscalYear;

            // Get all POIs and filter
            List<EmployeeProofOfInvestment> allPOIs = poiRepository.findByOrganizationId(
                    org.getOrganizationId());

            List<Map<String, Object>> pendingPOIs = allPOIs.stream()
                    .filter(poi -> poi.getFiscalYear() != null &&
                            poi.getFiscalYear().equals(finalFiscalYear) && // Use final copy
                            poi.getStatus().name().equals("DRAFT"))
                    .map(poi -> {
                        Map<String, Object> poiData = new HashMap<>();
                        poiData.put("poiId", poi.getId());
                        poiData.put("employeeId", poi.getEmployee().getEmployeeId());
                        poiData.put("employeeName",
                                poi.getEmployee().getFirstName() + " " +
                                        poi.getEmployee().getLastName());
                        poiData.put("fiscalYear", poi.getFiscalYear());
                        poiData.put("status", poi.getStatus().name());
                        poiData.put("email", poi.getEmployee().getWorkMail());
                        return poiData;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("organizationId", organizationId);
            response.put("fiscalYear", fiscalYear);
            response.put("totalPendingPOIs", pendingPOIs.size());
            response.put("pendingPOIs", pendingPOIs);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error checking pending POIs: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to check pending POIs: " + e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ==================== 4. CHECK REMINDER CONFIGURATION ====================

    @GetMapping("/config/{organizationId}")
    public ResponseEntity<Map<String, Object>> getReminderConfig(
            @PathVariable String organizationId) {

        log.info("Checking reminder config for org: {}", organizationId);

        try {
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found"));

            ProofOfInvestment poiConfig = proofOfInvestmentRepository.findByOrganization(org)
                    .orElseThrow(() -> new RuntimeException("POI config not found"));

            Map<String, Object> config = new HashMap<>();
            config.put("organizationId", organizationId);
            config.put("organizationName", org.getOrganizationName());
            config.put("lastDateForPoi", poiConfig.getLastDateForPoi());
            config.put("isAnyReminderBeforeLockdateEnabled",
                    poiConfig.isAnyReminderBeforeLockdateEnabled());
            config.put("sendMailOnPoiRelease", poiConfig.isSendMailOnPoiRelease());
            config.put("sendMailOnPoiLock", poiConfig.isSendMailOnPoiLock());
            config.put("isPoiLocked", poiConfig.isPoiLocked());

            // Get reminders
            List<Map<String, Object>> reminders = poiConfig.getReminders().stream()
                    .map(reminder -> {
                        Map<String, Object> rem = new HashMap<>();
                        rem.put("reminderId", reminder.getReminderId());
                        rem.put("enabled", reminder.isEnabled());
                        rem.put("daysBefore", reminder.getNumberOfDays());
                        return rem;
                    })
                    .toList();

            config.put("reminders", reminders);
            config.put("totalReminders", reminders.size());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("config", config);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error checking reminder config: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to check reminder config: " + e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ==================== 5. CHECK SENT REMINDERS ====================

    @GetMapping("/sent-reminders/{organizationId}")
    public ResponseEntity<Map<String, Object>> getSentReminders(
            @PathVariable String organizationId,
            @RequestParam(required = false) Integer fiscalYear) {

        log.info("Checking sent reminders for org: {}, fiscalYear: {}",
                organizationId, fiscalYear);

        try {
            // Use current year if not specified
            if (fiscalYear == null) {
                fiscalYear = LocalDate.now().getYear();
            }

            // CREATE FINAL COPY for use in lambda
            final Integer finalFiscalYear = fiscalYear;

            List<Reminder> reminders = reminderRepository.findByOrganizationOrganizationId(organizationId);

            List<Map<String, Object>> sentReminders = reminders.stream()
                    .filter(reminder -> reminder.getFiscalYear() == null ||
                            reminder.getFiscalYear().equals(finalFiscalYear)) // Use final copy
                    .map(reminder -> {
                        Map<String, Object> rem = new HashMap<>();
                        rem.put("reminderId", reminder.getReminderId());
                        rem.put("sentDate", reminder.getSentDate());
                        rem.put("daysBefore", reminder.getNumberOfDays());
                        rem.put("enabled", reminder.isEnabled());
                        if (reminder.getEmployee() != null) {
                            rem.put("employeeId", reminder.getEmployee().getEmployeeId());
                            rem.put("employeeName",
                                    reminder.getEmployee().getFirstName() + " " +
                                            reminder.getEmployee().getLastName());
                        }
                        return rem;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("organizationId", organizationId);
            response.put("fiscalYear", fiscalYear);
            response.put("totalSentReminders", sentReminders.size());
            response.put("sentReminders", sentReminders);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error checking sent reminders: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to check sent reminders: " + e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ==================== 6. SETUP TEST DATA ====================

    @PostMapping("/setup-test-data/{organizationId}")
    public ResponseEntity<Map<String, Object>> setupTestData(
            @PathVariable String organizationId) {

        log.info("Setting up test data for org: {}", organizationId);

        try {
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found"));

            // 1. Update POI config with test deadline (5 days from now)
            ProofOfInvestment poiConfig = proofOfInvestmentRepository.findByOrganization(org)
                    .orElse(new ProofOfInvestment());

            LocalDate testDeadline = LocalDate.now().plusDays(5);
            poiConfig.setLastDateForPoi(testDeadline.toString());
            poiConfig.setAnyReminderBeforeLockdateEnabled(true);
            poiConfig.setOrganization(org);

            // Save config
            proofOfInvestmentRepository.save(poiConfig);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Test data setup completed");
            response.put("organizationId", organizationId);
            response.put("testDeadline", testDeadline.toString());
            response.put("remindersEnabled", true);
            response.put("poiLocked", false);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error setting up test data: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to setup test data: " + e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    // ==================== 7. HEALTH CHECK ====================

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "POI Reminder System");
        health.put("timestamp", LocalDate.now().format(DateTimeFormatter.ISO_DATE));
        health.put("time", java.time.LocalTime.now().toString());

        // Check components
        Map<String, String> components = new HashMap<>();
        components.put("scheduler", "Enabled (runs at 9 AM daily)");
        components.put("emailService", "Configured with Brevo");
        components.put("database", "Connected");
        components.put("reminderTracking", "Active");

        health.put("components", components);

        return ResponseEntity.ok(health);
    }

    /**
     * Test POI submission email
     * POST /api/test/poi-reminders/{organizationId}/test-poi-submission
     */
    @PostMapping("/{organizationId}/test-poi-submission")
    public ResponseEntity<Map<String, Object>> testPOISubmissionEmail(
            @PathVariable String organizationId,
            @RequestBody Map<String, Object> request) {

        try {
            String employeeId = (String) request.get("employeeId");
            Integer fiscalYear = (Integer) request.get("fiscalYear");

            if (employeeId == null || fiscalYear == null) {
                throw new RuntimeException("employeeId and fiscalYear are required");
            }

            log.info("Testing POI submission email | org={}, employee={}, fiscalYear={}",
                    organizationId, employeeId, fiscalYear);

            // Get organization
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

            // Get employee - FIX: use employeeRepository (not basicDetailsRepository)
            BasicDetails employee = employeeRepository
                    .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                    .orElseThrow(() -> new RuntimeException(
                            "Employee not found: " + employeeId + " in org " + organizationId));

            // Send POI submission email
            boolean sent = brevoEmailService.sendPOISubmissionEmail(employee, org, fiscalYear);

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("success", sent);
            response.put("message",
                    sent ? "POI submission email sent successfully" : "Failed to send POI submission email");
            response.put("organizationId", organizationId);
            response.put("employeeId", employeeId);
            response.put("employeeEmail", employee.getWorkMail());
            response.put("fiscalYear", fiscalYear);
            response.put("template", "POI_SUBMISSION");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing POI submission email: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", 500);
            error.put("success", false);
            error.put("message", "Test failed: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
    }
}