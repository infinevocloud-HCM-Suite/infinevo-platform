package com.itsdev.payroll.controller.organization;

import com.itsdev.payroll.dto.organization.OrganizationRoleDTO;
import com.itsdev.payroll.service.organization.OrganizationRoleService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
public class OrganizationRoleController {

    private final OrganizationRoleService roleService;

    public OrganizationRoleController(OrganizationRoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRole(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody OrganizationRoleDTO dto) {

        OrganizationRoleDTO created = roleService.createRole(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Role created successfully");
        response.put("data", created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<Map<String, Object>> updateRole(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String roleId,
            @Valid @RequestBody OrganizationRoleDTO dto) {

        OrganizationRoleDTO updated = roleService.updateRole(organizationId, roleId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Role updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<Map<String, Object>> getRole(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String roleId) {

        OrganizationRoleDTO role = roleService.getRole(organizationId, roleId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Role fetched successfully");
        response.put("data", role);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllRoles(
            @RequestHeader("organizationId") String organizationId) {

        List<OrganizationRoleDTO> roles = roleService.getAllRoles(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Roles fetched successfully");
        response.put("data", roles);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<Map<String, Object>> deleteRole(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String roleId) {

        roleService.deleteRole(organizationId, roleId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Role deleted successfully");

        return ResponseEntity.ok(response);
    }
}