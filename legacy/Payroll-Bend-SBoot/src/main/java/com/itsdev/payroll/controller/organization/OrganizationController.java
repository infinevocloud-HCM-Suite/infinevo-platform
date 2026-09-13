package com.itsdev.payroll.controller.organization;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.itsdev.payroll.dto.organization.OrganizationDTO;
import com.itsdev.payroll.service.organization.OrganizationService;
import com.itsdev.payroll.serviceimpl.CloudinaryServiceImpl;

import jakarta.validation.Valid;

import java.util.*;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    @Autowired
    private OrganizationService organizationService;
    
    
    private static final Logger log = LoggerFactory.getLogger(CloudinaryServiceImpl.class);

    @PostMapping("/new")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody OrganizationDTO dto) {
        OrganizationDTO created = organizationService.createOrganization(dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Organization created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }
    
    @PutMapping(value = "/{organizationId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String organizationId,
            @RequestPart("organization") OrganizationDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file) throws Exception {

        OrganizationDTO updated = organizationService.updateOrganization(organizationId, dto, file);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Organization updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{organizationId}/worklocation/{workLocationId}/filing-address")
    public ResponseEntity<Map<String, Object>> setFilingAddress(
            @PathVariable String organizationId,
            @PathVariable String workLocationId) {

        String methodName = "setFilingAddress";
        log.info("[{}]  Setting filing address for organizationId: {}, workLocationId: {}",
                 methodName, organizationId, workLocationId);

        organizationService.setFilingAddress(organizationId, workLocationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Filing address set successfully");

        log.info("[{}] API completed successfully for organizationId: {}, workLocationId: {}", 
                 methodName, organizationId, workLocationId);

        return ResponseEntity.ok(response);
    }



    @GetMapping("/{organizationId}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String organizationId) {
        OrganizationDTO org = organizationService.getOrganizationByOrganizationId(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Organization retrieved successfully");
        response.put("data", org);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        List<OrganizationDTO> orgs = organizationService.getAllOrganizations(userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Organizations retrieved successfully");
        response.put("data", orgs);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{organizationId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String organizationId) {
        organizationService.deleteOrganization(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Organization deleted successfully");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/active-organizations")
    public ResponseEntity<Map<String, Object>> getOrganizationsForCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub"); // adjust claim as per token
        List<Map<String, Object>> orgs = organizationService.getActiveOrganizationsForUser(userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Active organizations retrieved successfully");
        response.put("data", orgs);

        return ResponseEntity.ok(response);
    }
    
 
    @DeleteMapping("/{organizationId}/logo")
    public ResponseEntity<Map<String, Object>> deleteOrganizationFile(@PathVariable String organizationId) {
        String methodName = "deleteOrganizationFile";
        log.info("[{}] Request received to delete file for organizationId: {}", methodName, organizationId);

        String message = organizationService.deleteOrganizationFile(organizationId);
        log.info("[{}] Service response: {}", methodName, message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", null);

        if ("File deleted successfully".equals(message)) {
            response.put("status", HttpStatus.OK.value());
            response.put("message", message);
            return ResponseEntity.ok(response);
        } else if ("Organization not found".equals(message)) {
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", message);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } else if ("No file exists for this organization".equals(message)) {
            response.put("status", HttpStatus.BAD_REQUEST.value());
            response.put("message", message);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } else {
            response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", message);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PutMapping("/setup/{organizationId}")
    public ResponseEntity<Map<String, Object>> updateOrganizationWithHeadOffice(
            @PathVariable String organizationId,
            @RequestBody OrganizationDTO dto) {
        OrganizationDTO updated = organizationService.updateOrganizationWithHeadOffice(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Organization updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }


}

