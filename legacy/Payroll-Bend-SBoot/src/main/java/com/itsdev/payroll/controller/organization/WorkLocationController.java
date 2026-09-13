package com.itsdev.payroll.controller.organization;

import com.itsdev.payroll.dto.organization.WorkLocationDTO;
import com.itsdev.payroll.service.organization.WorkLocationService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/worklocations")
public class WorkLocationController {

    @Autowired
    private WorkLocationService workLocationService;

    private String getOrganizationIdOrThrow(String orgId) {
        if (orgId == null || orgId.trim().isEmpty()) {
            throw new RuntimeException("organizationId header is missing");
        }
        return orgId;
    }


    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody WorkLocationDTO dto) {

        String orgId = getOrganizationIdOrThrow(organizationId);
        WorkLocationDTO created = workLocationService.createWorkLocationForOrg(orgId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Work location created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{workLocationId}")
    public ResponseEntity<Map<String, Object>> update(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String workLocationId,
            @Valid @RequestBody WorkLocationDTO dto) {

        String orgId = getOrganizationIdOrThrow(organizationId);
        WorkLocationDTO updated = workLocationService.updateWorkLocationForOrg(orgId, workLocationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Work location updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{workLocationId}")
    public ResponseEntity<Map<String, Object>> getById(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String workLocationId) {

        String orgId = getOrganizationIdOrThrow(organizationId);
        WorkLocationDTO dto = workLocationService.getWorkLocationForOrg(orgId, workLocationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Work location retrieved successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllForOrg(
            @RequestHeader("organizationId") String organizationId) {

        String orgId = getOrganizationIdOrThrow(organizationId);
        List<WorkLocationDTO> list = workLocationService.getAllWorkLocationsForOrg(orgId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Work locations retrieved successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{workLocationId}")
    public ResponseEntity<Map<String, Object>> delete(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String workLocationId) {

        String orgId = getOrganizationIdOrThrow(organizationId);
        workLocationService.deleteWorkLocationForOrg(orgId, workLocationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Work location deleted successfully");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/imports")
    public ResponseEntity<Map<String, Object>> bulkUploadWorkLocations(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody List<WorkLocationDTO> workLocations) {

        workLocationService.saveAll(organizationId, workLocations);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Work locations imported successfully");
        response.put("data", workLocations);

        return ResponseEntity.ok(response);
    }

}