package com.itsdev.payroll.controller.organization;

import com.itsdev.payroll.dto.organization.DepartmentDTO;
import com.itsdev.payroll.service.organization.DepartmentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDepartment(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody DepartmentDTO dto) {

        DepartmentDTO created = departmentService.createDepartment(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Department created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{departmentId}")
    public ResponseEntity<Map<String, Object>> updateDepartment(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String departmentId,
            @Valid @RequestBody DepartmentDTO dto) {

        DepartmentDTO updated = departmentService.updateDepartment(organizationId, departmentId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Department updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{departmentId}")
    public ResponseEntity<Map<String, Object>> getDepartment(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String departmentId) {

        DepartmentDTO dto = departmentService.getDepartment(organizationId, departmentId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Department retrieved successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllDepartments(
            @RequestHeader("organizationId") String organizationId) {

        List<DepartmentDTO> list = departmentService.getAllDepartments(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Departments retrieved successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{departmentId}")
    public ResponseEntity<Map<String, Object>> deleteDepartment(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String departmentId) {

        departmentService.deleteDepartment(organizationId, departmentId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Department deleted successfully");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/imports")
    public ResponseEntity<Map<String, Object>> bulkUploadDepartments(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody List<DepartmentDTO> departments) {

        departmentService.saveAll(organizationId, departments);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Departments imported successfully");
        response.put("data", departments);

        return ResponseEntity.ok(response);
    }
}
