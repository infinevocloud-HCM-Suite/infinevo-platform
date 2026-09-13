package com.itsdev.payroll.controller.salarycomponents;

import com.itsdev.payroll.controller.payruns.EmployeePayRunController;
import com.itsdev.payroll.dto.salarycomponents.DeductionDTO;
import com.itsdev.payroll.service.salarycomponents.DeductionService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deductions")
public class DeductionController {

    private final DeductionService deductionService;
    private static final Logger log = LoggerFactory.getLogger(DeductionController.class);
    public DeductionController(DeductionService deductionService) {
        this.deductionService = deductionService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody DeductionDTO dto) {

        DeductionDTO saved = deductionService.create(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Deduction created successfully");
        response.put("data", saved);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{deductionId}")
    public ResponseEntity<Map<String, Object>> update(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String deductionId,
            @Valid @RequestBody DeductionDTO dto) {

        DeductionDTO updated = deductionService.update(organizationId, deductionId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Deduction updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{deductionId}")
    public ResponseEntity<Map<String, Object>> get(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String deductionId) {

        DeductionDTO dto = deductionService.get(organizationId, deductionId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Deduction fetched successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestHeader("organizationId") String organizationId) {

        List<DeductionDTO> list = deductionService.getAll(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Deductions fetched successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{deductionId}")
    public ResponseEntity<Map<String, Object>> delete(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String deductionId) {

        deductionService.delete(organizationId, deductionId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.NO_CONTENT.value());
        response.put("message", "Deduction deleted successfully");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/inactive/{deductionId}")
    public ResponseEntity<Map<String, Object>> inactivateDeduction(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String deductionId) {

        String method = "inactivateDeduction";
        log.info("[{}] 📥 Incoming request to inactivate deduction | orgId={}, deductionId={}", method, organizationId, deductionId);

        deductionService.inactivateDeduction(organizationId, deductionId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Deduction inactivated successfully");

        log.info("[{}] ✅ Deduction inactivated successfully | orgId={}, deductionId={}", method, organizationId, deductionId);
        return ResponseEntity.ok(response);
    }


    @PutMapping("/active/{deductionId}")
    public ResponseEntity<Map<String, Object>> reactivateDeduction(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String deductionId) {

        deductionService.reactivateDeduction(organizationId, deductionId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Deduction activated successfully");

        return ResponseEntity.ok(response);
    }


}