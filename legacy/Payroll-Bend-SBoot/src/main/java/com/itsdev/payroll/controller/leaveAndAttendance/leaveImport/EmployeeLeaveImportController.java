package com.itsdev.payroll.controller.leaveAndAttendance.leaveImport;

import com.itsdev.payroll.dto.leaveAndAttendance.leaveImport.EmployeeLeaveImportDTO;
import com.itsdev.payroll.service.leaveAndAttendance.leaveImport.EmployeeLeaveImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employee-leave-imports")
public class EmployeeLeaveImportController {

    private final EmployeeLeaveImportService leaveImportService;

    public EmployeeLeaveImportController(EmployeeLeaveImportService leaveImportService) {
        this.leaveImportService = leaveImportService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createLeaveImport(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody EmployeeLeaveImportDTO dto) {

        EmployeeLeaveImportDTO created = leaveImportService.createLeaveImport(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Employee leave import created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateLeaveImport(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long id,
            @RequestBody EmployeeLeaveImportDTO dto) {

        EmployeeLeaveImportDTO updated = leaveImportService.updateLeaveImport(organizationId, id, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Employee leave import updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLeaveImport(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long id) {

        EmployeeLeaveImportDTO dto = leaveImportService.getLeaveImport(organizationId, id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Employee leave import retrieved successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllLeaveImports(
            @RequestHeader("organizationId") String organizationId) {

        List<EmployeeLeaveImportDTO> list = leaveImportService.getAllLeaveImports(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Employee leave imports retrieved successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteLeaveImport(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable Long id) {

        leaveImportService.deleteLeaveImport(organizationId, id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Employee leave import deleted successfully");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/imports")
    public ResponseEntity<Map<String, Object>> bulkUploadLeaveImports(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody List<EmployeeLeaveImportDTO> leaveImports) {

        leaveImportService.saveAll(organizationId, leaveImports);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Employee leave imports imported successfully");
        response.put("data", leaveImports);

        return ResponseEntity.ok(response);
    }
}

