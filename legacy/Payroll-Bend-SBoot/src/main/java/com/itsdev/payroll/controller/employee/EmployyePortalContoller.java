package com.itsdev.payroll.controller.employee;

import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.dto.employee.EmployeeFullProfileDTO;
import com.itsdev.payroll.service.employee.EmployyePortalService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/employees-portal")
public class EmployyePortalContoller {
	
    private final EmployyePortalService employyePortalService;
    
	 private static final Logger log = LoggerFactory.getLogger(EmployyePortalContoller.class);

    public EmployyePortalContoller(EmployyePortalService employyePortalService) {
        this.employyePortalService = employyePortalService;
    }

    @GetMapping("/employee-profile")
    public ResponseEntity<Map<String, Object>> getEmployeeProfile(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam String employeeId) {

        String method = "getEmployeeProfile";
        log.info("[{}] 📥 Incoming request to fetch employee profile | orgId={}, employeeId={}", 
                 method, organizationId, employeeId);

        EmployeeFullProfileDTO dto = employyePortalService.getEmployeeProfile(organizationId, employeeId);

        log.info("[{}] ✅ Employee profile fetched successfully | orgId={}, employeeId={}", 
                 method, organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee retrieved successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{employeeId}/activate")
    public ResponseEntity<Map<String, Object>> activateEmployee(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        String method = "activateEmployee";
        log.info("[{}] 📥 Request | Activate employee | orgId={}, employeeId={}",
                method, organizationId, employeeId);

        BasicDetailsDTO employee = employyePortalService.activateEmployee(organizationId, employeeId);

        log.info("[{}] ✅ Success | Employee activated | orgId={}, employeeId={}",
                method, organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee activated successfully");
        response.put("data", Map.of("employeeId", employee.getId(),
                                    "status", employee.getEmployeeStatus()));

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{employeeId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateEmployee(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        String method = "deactivateEmployee";
        log.info("[{}] 📥 Request | Deactivate employee | orgId={}, employeeId={}",
                method, organizationId, employeeId);

        BasicDetailsDTO employee = employyePortalService.deactivateEmployee(organizationId, employeeId);

        log.info("[{}] ✅ Success | Employee deactivated | orgId={}, employeeId={}",
                method, organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee deactivated successfully");
        response.put("data", Map.of("employeeId", employee.getId(),
                                    "status", employee.getEmployeeStatus()));

        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{employeeId}/soft-delete")
    public ResponseEntity<Map<String, Object>> softDeleteEmployee(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        String method = "softDeleteEmployee";
        log.info("[{}] 📥 Request | Soft delete employee | orgId={}, employeeId={}",
                method, organizationId, employeeId);

        BasicDetailsDTO employee = employyePortalService.softDeleteEmployee(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Employee soft deleted successfully");
        response.put("data", Map.of("employeeId", employee.getId(),
                                    "isDeleted", employee.getIsDeleted()));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{employeeId}/enable-portal")
    public ResponseEntity<Map<String, Object>> enablePortal(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        String method = "enablePortal";
        log.info("[{}] 📥 Request | Enable portal | orgId={}, employeeId={}", method, organizationId, employeeId);

        employyePortalService.enablePortal(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Portal enabled successfully and invitation email sent");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{employeeId}/resend-invitation")
    public ResponseEntity<Map<String, Object>> resendInvitation(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        String method = "resendInvitation";
        log.info("[{}] 📥 Request | Resend invitation | orgId={}, employeeId={}", method, organizationId, employeeId);

        employyePortalService.resendInvitation(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Invitation email resent successfully");

        return ResponseEntity.ok(response);
    }

}
