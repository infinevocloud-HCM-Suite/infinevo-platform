package com.itsdev.payroll.controller.organization;

import com.itsdev.payroll.dto.organization.DesignationDTO;
import com.itsdev.payroll.service.organization.DesignationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/designations")
public class DesignationController {

    private final DesignationService designationService;

    public DesignationController(DesignationService designationService) {
        this.designationService = designationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDesignation(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody DesignationDTO dto) {

        DesignationDTO created = designationService.createDesignation(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Designation created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{designationId}")
    public ResponseEntity<Map<String, Object>> updateDesignation(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String designationId,
            @Valid @RequestBody DesignationDTO dto) {

        DesignationDTO updated = designationService.updateDesignation(organizationId, designationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Designation updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{designationId}")
    public ResponseEntity<Map<String, Object>> getDesignation(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String designationId) {

        DesignationDTO dto = designationService.getDesignation(organizationId, designationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Designation retrieved successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllDesignations(
            @RequestHeader("organizationId") String organizationId) {

        List<DesignationDTO> list = designationService.getAllDesignations(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Designations retrieved successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{designationId}")
    public ResponseEntity<Map<String, Object>> deleteDesignation(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String designationId) {

        designationService.deleteDesignation(organizationId, designationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Designation deleted successfully");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/imports")
    public ResponseEntity<Map<String, Object>> bulkUploadDesignations(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody List<DesignationDTO> designations) {

        designationService.saveAll(organizationId, designations);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Designations imported successfully");
        response.put("data", designations);

        return ResponseEntity.ok(response);
    }
}