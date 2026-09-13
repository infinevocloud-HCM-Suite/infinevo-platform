package com.itsdev.payroll.controller.test;

import com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.claimsanddeclarations.IncomeTaxDeclarationService;
import com.itsdev.payroll.service.claimsanddeclarations.ITDeclarationEmailService;
import com.itsdev.payroll.service.claimsanddeclarations.ITDeclarationReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test/it-declaration-emails")
@RequiredArgsConstructor
@Slf4j
public class IncomeTaxDeclarationTestController {

    private final ITDeclarationEmailService itDeclarationEmailService;
    private final ITDeclarationReminderService itDeclarationReminderService;
    private final IncomeTaxDeclarationService incomeTaxDeclarationService;
    private final OrganizationRepository organizationRepository;
    private final BasicDetailsRepository basicDetailsRepository;

    /**
     * Test 1: Send IT Declaration reminder email to specific employee
     * POST /api/test/it-declaration-emails/{organizationId}/test-reminder
     */
    @PostMapping("/{organizationId}/test-reminder")
    public ResponseEntity<Map<String, Object>> testReminderEmail(
            @PathVariable String organizationId,
            @RequestBody Map<String, Object> request) {

        try {
            String employeeId = (String) request.get("employeeId");
            Long daysBefore = request.get("daysBefore") != null ? Long.parseLong(request.get("daysBefore").toString())
                    : 5L;

            if (employeeId == null) {
                throw new RuntimeException("employeeId is required in request body");
            }

            log.info("Testing IT Declaration reminder email | org={}, employee={}, daysBefore={}",
                    organizationId, employeeId, daysBefore);

            // Get organization
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

            // Get employee
            BasicDetails employee = basicDetailsRepository
                    .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                    .orElseThrow(() -> new RuntimeException(
                            "Employee not found: " + employeeId + " in org " + organizationId));

            // Get IT Declaration settings
            IncomeTaxDeclarationDTO settings = incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);

            // Check if last date is set
            String lastDateStr = settings.getLastDateForItDeclaration();
            if (lastDateStr == null || lastDateStr.trim().isEmpty()) {
                // Use default date for testing (30 days from now)
                LocalDate defaultDeadline = LocalDate.now().plusDays(30);
                lastDateStr = defaultDeadline.toString();
                log.info("No deadline set, using default: {}", lastDateStr);
            }

            // Send reminder email
            boolean sent = itDeclarationEmailService.sendReminderEmail(
                    employee, org, lastDateStr, daysBefore);

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("success", sent);
            response.put("message", sent ? "IT Declaration reminder email sent successfully"
                    : "Failed to send IT Declaration reminder email");
            response.put("organizationId", organizationId);
            response.put("employeeId", employeeId);
            response.put("employeeEmail", employee.getWorkMail());
            response.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
            response.put("daysBefore", daysBefore);
            response.put("deadline", lastDateStr);
            response.put("template", "IT_DECLARATION_REMINDER");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing IT Declaration reminder email: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", 500);
            error.put("success", false);
            error.put("message", "Test failed: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Test 2: Send IT Declaration lock email to specific employee
     * POST /api/test/it-declaration-emails/{organizationId}/test-lock
     */
    @PostMapping("/{organizationId}/test-lock")
    public ResponseEntity<Map<String, Object>> testLockEmail(
            @PathVariable String organizationId,
            @RequestBody Map<String, Object> request) {

        try {
            String employeeId = (String) request.get("employeeId");

            if (employeeId == null) {
                throw new RuntimeException("employeeId is required in request body");
            }

            log.info("Testing IT Declaration lock email | org={}, employee={}",
                    organizationId, employeeId);

            // Get organization
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

            // Get employee
            BasicDetails employee = basicDetailsRepository
                    .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                    .orElseThrow(() -> new RuntimeException(
                            "Employee not found: " + employeeId + " in org " + organizationId));

            // Get IT Declaration settings
            IncomeTaxDeclarationDTO settings = incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);

            // For testing, ensure email sending is enabled
            IncomeTaxDeclarationDTO testSettings = new IncomeTaxDeclarationDTO();
            testSettings.setSendMailOnItDeclarationLock(true);
            testSettings.setLastDateForItDeclaration(settings.getLastDateForItDeclaration());

            // Send lock email (using private method via reflection or create public method)
            // For now, trigger the bulk email method which will send to this employee
            // We'll need to modify the service or create a test method

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("success", true);
            response.put("message", "Lock email test endpoint ready - Need to implement single employee lock email");
            response.put("organizationId", organizationId);
            response.put("employeeId", employeeId);
            response.put("employeeEmail", employee.getWorkMail());
            response.put("template", "IT_DECLARATION_LOCK");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing IT Declaration lock email: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", 500);
            error.put("success", false);
            error.put("message", "Test failed: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Test 3: Send IT Declaration release email to specific employee
     * POST /api/test/it-declaration-emails/{organizationId}/test-release
     */
    @PostMapping("/{organizationId}/test-release")
    public ResponseEntity<Map<String, Object>> testReleaseEmail(
            @PathVariable String organizationId,
            @RequestBody Map<String, Object> request) {

        try {
            String employeeId = (String) request.get("employeeId");

            if (employeeId == null) {
                throw new RuntimeException("employeeId is required in request body");
            }

            log.info("Testing IT Declaration release email | org={}, employee={}",
                    organizationId, employeeId);

            // Get organization
            Organization org = organizationRepository.findByOrganizationId(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

            // Get employee
            BasicDetails employee = basicDetailsRepository
                    .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                    .orElseThrow(() -> new RuntimeException(
                            "Employee not found: " + employeeId + " in org " + organizationId));

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("success", true);
            response.put("message",
                    "Release email test endpoint ready - Need to implement single employee release email");
            response.put("organizationId", organizationId);
            response.put("employeeId", employeeId);
            response.put("employeeEmail", employee.getWorkMail());
            response.put("template", "IT_DECLARATION_RELEASE");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing IT Declaration release email: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", 500);
            error.put("success", false);
            error.put("message", "Test failed: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Test 4: Bulk test - Send lock emails to all employees
     * POST /api/test/it-declaration-emails/{organizationId}/test-bulk-lock
     */
    @PostMapping("/{organizationId}/test-bulk-lock")
    public ResponseEntity<Map<String, Object>> testBulkLockEmails(
            @PathVariable String organizationId) {

        try {
            log.info("Testing bulk IT Declaration lock emails for organization: {}", organizationId);

            // Trigger lock emails for all employees
            itDeclarationEmailService.sendLockEmails(organizationId);

            // Get employee count for response
            List<BasicDetails> employees = basicDetailsRepository
                    .findByOrganization_OrganizationId(organizationId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("success", true);
            response.put("message", "Bulk lock emails triggered for " + employees.size() + " employees");
            response.put("organizationId", organizationId);
            response.put("employeeCount", employees.size());
            response.put("template", "IT_DECLARATION_LOCK");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing bulk IT Declaration lock emails: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", 500);
            error.put("success", false);
            error.put("message", "Test failed: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Test 5: Bulk test - Send release emails to all employees
     * POST /api/test/it-declaration-emails/{organizationId}/test-bulk-release
     */
    @PostMapping("/{organizationId}/test-bulk-release")
    public ResponseEntity<Map<String, Object>> testBulkReleaseEmails(
            @PathVariable String organizationId) {

        try {
            log.info("Testing bulk IT Declaration release emails for organization: {}", organizationId);

            // Trigger release emails for all employees
            itDeclarationEmailService.sendReleaseEmails(organizationId);

            // Get employee count for response
            List<BasicDetails> employees = basicDetailsRepository
                    .findByOrganization_OrganizationId(organizationId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("success", true);
            response.put("message", "Bulk release emails triggered for " + employees.size() + " employees");
            response.put("organizationId", organizationId);
            response.put("employeeCount", employees.size());
            response.put("template", "IT_DECLARATION_RELEASE");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing bulk IT Declaration release emails: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", 500);
            error.put("success", false);
            error.put("message", "Test failed: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Test 6: Trigger scheduled reminders (simulate daily job)
     * POST
     * /api/test/it-declaration-emails/{organizationId}/test-scheduled-reminders
     */
    @PostMapping("/{organizationId}/test-scheduled-reminders")
    public ResponseEntity<Map<String, Object>> testScheduledReminders(
            @PathVariable String organizationId) {

        try {
            log.info("Testing scheduled IT Declaration reminders for organization: {}", organizationId);

            // Manually trigger the reminder service
            itDeclarationReminderService.processReminders();

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("success", true);
            response.put("message", "Scheduled reminders processed");
            response.put("organizationId", organizationId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing scheduled IT Declaration reminders: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", 500);
            error.put("success", false);
            error.put("message", "Test failed: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Test 7: Get test employee list for organization
     * GET /api/test/it-declaration-emails/{organizationId}/test-employees
     */
    @GetMapping("/{organizationId}/test-employees")
    public ResponseEntity<Map<String, Object>> getTestEmployees(
            @PathVariable String organizationId) {

        try {
            log.info("Getting test employees for organization: {}", organizationId);

            List<BasicDetails> employees = basicDetailsRepository
                    .findByOrganization_OrganizationId(organizationId);

            // Create simplified employee list
            List<Map<String, Object>> employeeList = employees.stream()
                    .map(emp -> {
                        Map<String, Object> empMap = new HashMap<>();
                        empMap.put("employeeId", emp.getEmployeeId());
                        empMap.put("firstName", emp.getFirstName());
                        empMap.put("lastName", emp.getLastName());
                        empMap.put("email", emp.getWorkMail());
                        empMap.put("hasEmail", emp.getWorkMail() != null && !emp.getWorkMail().trim().isEmpty());
                        return empMap;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("success", true);
            response.put("message", "Found " + employees.size() + " employees");
            response.put("organizationId", organizationId);
            response.put("employeeCount", employees.size());
            response.put("employees", employeeList);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting test employees: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", 500);
            error.put("success", false);
            error.put("message", "Failed to get employees: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Test 8: Get IT Declaration settings for testing
     * GET /api/test/it-declaration-emails/{organizationId}/test-settings
     */
    @GetMapping("/{organizationId}/test-settings")
    public ResponseEntity<Map<String, Object>> getTestSettings(
            @PathVariable String organizationId) {

        try {
            log.info("Getting IT Declaration test settings for organization: {}", organizationId);

            IncomeTaxDeclarationDTO settings = incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", 200);
            response.put("success", true);
            response.put("message", "IT Declaration settings retrieved");
            response.put("organizationId", organizationId);
            response.put("settings", settings);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting IT Declaration test settings: {}", e.getMessage(), e);

            Map<String, Object> error = new HashMap<>();
            error.put("status", 500);
            error.put("success", false);
            error.put("message", "Failed to get settings: " + e.getMessage());

            return ResponseEntity.status(500).body(error);
        }
    }
}