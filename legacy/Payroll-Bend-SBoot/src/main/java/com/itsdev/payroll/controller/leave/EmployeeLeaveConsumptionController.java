package com.itsdev.payroll.controller.leave;

import com.itsdev.payroll.dto.leave.EmployeeLeaveConsumptionRequestDTO;
import com.itsdev.payroll.dto.leave.EmployeeLeaveConsumptionResponseDTO;
import com.itsdev.payroll.service.leave.EmployeeLeaveConsumptionService;
import com.itsdev.payroll.util.JWTUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leave-consumption")
@CrossOrigin(origins = "*")
public class EmployeeLeaveConsumptionController {

    private final EmployeeLeaveConsumptionService leaveConsumptionService;

    public EmployeeLeaveConsumptionController(EmployeeLeaveConsumptionService leaveConsumptionService) {
        this.leaveConsumptionService = leaveConsumptionService;
    }

    @PutMapping("/{employeeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")
    public ResponseEntity<Map<String, Object>> updateEmployeeConsumption(
            @RequestHeader(value = "organizationId", required = true) String organizationId,
            @PathVariable("employeeId") String employeeId,
            @RequestParam(value = "year", required = true) String year,
            @Valid @RequestBody EmployeeLeaveConsumptionRequestDTO request) {

        String updatedBy = JWTUtil.getCurrentUserName();
        EmployeeLeaveConsumptionResponseDTO result = leaveConsumptionService.updateEmployeeConsumption(
                organizationId, employeeId, year, request.getLeaveMonth(), request.getLeaveTypes(), updatedBy);

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("message", "Leave consumption updated successfully");
        response.put("data", result);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")
    public ResponseEntity<Map<String, Object>> getConsumptions(
            @RequestHeader(value = "organizationId", required = true) String organizationId,
            @RequestParam(value = "year", required = false) String year) {

        List<EmployeeLeaveConsumptionResponseDTO> result = leaveConsumptionService.getConsumptions(organizationId, year);

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("message", "Leave consumptions fetched successfully");
        response.put("data", result);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin') or #employeeId == authentication.name")
    public ResponseEntity<Map<String, Object>> getEmployeeConsumption(
            @RequestHeader(value = "organizationId", required = true) String organizationId,
            @PathVariable("employeeId") String employeeId,
            @RequestParam(value = "year", required = false) String year) {

        EmployeeLeaveConsumptionResponseDTO result = leaveConsumptionService.getEmployeeConsumption(organizationId, employeeId, year);

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("message", "Employee leave consumption fetched successfully");
        response.put("data", result);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{employeeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")
    public ResponseEntity<Map<String, Object>> deleteEmployeeConsumption(
            @RequestHeader(value = "organizationId", required = true) String organizationId,
            @PathVariable("employeeId") String employeeId,
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "month", required = false) String month) {

        if (month != null && !month.isBlank()) {
            leaveConsumptionService.deleteEmployeeMonthConsumption(organizationId, employeeId, year, month);
        } else {
            leaveConsumptionService.deleteEmployeeConsumption(organizationId, employeeId, year);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("message", "Employee leave consumption deleted successfully");

        return ResponseEntity.ok(response);
    }

    
    @DeleteMapping("/{employeeId}/entry/{entryId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")
    public ResponseEntity<Map<String, Object>> deleteEmployeeEntry(
            @RequestHeader(value = "organizationId", required = true) String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("entryId") String entryId,
            @RequestParam(value = "year", required = false) String year) {

        leaveConsumptionService.deleteEmployeeEntry(organizationId, employeeId, year, entryId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("message", "Leave entry deleted successfully");

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{employeeId}/type/{leaveType}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")
    public ResponseEntity<Map<String, Object>> deleteSingleLeaveTypeConsumption(
            @RequestHeader(value = "organizationId", required = true) String organizationId,
            @PathVariable("employeeId") String employeeId,
            @PathVariable("leaveType") String leaveType,
            @RequestParam(value = "year", required = false) String year) {

        leaveConsumptionService.deleteSingleLeaveTypeConsumption(organizationId, employeeId, leaveType, year);

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("message", "Leave consumption for " + leaveType + " deleted successfully");

        return ResponseEntity.ok(response);
    }
}