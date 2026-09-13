package com.itsdev.payroll.controller;

import com.itsdev.payroll.entity.OrganizationUserRoleMapping;
import com.itsdev.payroll.repository.OrganizationUserRoleMappingRepository;
import com.itsdev.payroll.util.JWTUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/organization-user-role-mapping")
public class OrganizationUserRoleMappingController {

    @Autowired
    private OrganizationUserRoleMappingRepository organizationUserRoleMappingRepository;

    @GetMapping("/my-role")
    public ResponseEntity<Map<String, Object>> getMyRoleMapping(
            @RequestHeader("organizationId") String organizationId) {

        // Get userId from JWT
        String userId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        // Fetch role mapping
        Optional<OrganizationUserRoleMapping> roleMappingOpt =
                organizationUserRoleMappingRepository.findByUserIdAndOrganizationId(userId, organizationId);

        Map<String, Object> response = new LinkedHashMap<>();

        if (roleMappingOpt.isEmpty()) {
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", "No role mapping found for user in this organization");
            return ResponseEntity.ok(response);
        }

        OrganizationUserRoleMapping roleMapping = roleMappingOpt.get();

        // Prepare data object
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roleId", roleMapping.getRoleId());
        data.put("roleName", roleMapping.getRoleName());
        data.put("isEmployeePortalEnable", roleMapping.getEmployeePortalEnable());

        // Prepare response
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Role mapping fetched successfully");
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    // Get all organization IDs where employee portal is enabled
    @GetMapping("/my-organizations")
    public ResponseEntity<Map<String, Object>> getMyOrganizations() {
        String userId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        List<OrganizationUserRoleMapping> orgMappings =
                organizationUserRoleMappingRepository.findByUserIdAndIsEmployeePortalEnableTrue(userId);

        Map<String, Object> response = new LinkedHashMap<>();

        if (orgMappings.isEmpty()) {
            response.put("status", HttpStatus.NOT_FOUND.value());
            response.put("message", "No organizations found with employee portal enabled for this user");
            response.put("data", Collections.emptyList());
            return ResponseEntity.ok(response);
        }

        // Extract organization IDs
        List<String> organizationIds = orgMappings.stream()
                .map(OrganizationUserRoleMapping::getOrganizationId)
                .toList();

        response.put("status", HttpStatus.OK.value());
        response.put("message", "Organizations fetched successfully");
        response.put("data", organizationIds);

        return ResponseEntity.ok(response);
    }
    
}

