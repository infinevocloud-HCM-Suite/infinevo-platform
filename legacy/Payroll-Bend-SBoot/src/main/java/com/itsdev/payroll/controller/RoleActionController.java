package com.itsdev.payroll.controller;

import com.itsdev.payroll.dto.action.AssignActionsRequest;
import com.itsdev.payroll.repository.OrganizationUserRoleMappingRepository;
import com.itsdev.payroll.service.action.OrganizationRoleActionService;
import com.itsdev.payroll.service.auth.AuthzService;
import com.itsdev.payroll.util.JWTUtil;
import com.itsdev.payroll.entity.organization.OrganizationRole;
import com.itsdev.payroll.repository.organization.OrganizationRoleRepository;
import com.itsdev.payroll.service.auth.OrgAccessValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/organizations/{orgId}/roles/{roleId}/actions")
public class RoleActionController {

    private final OrganizationRoleRepository roleRepository;
    private final OrganizationRoleActionService orgRoleActionService;
    private final OrganizationUserRoleMappingRepository orgUserRoleMappingRepository;
    private final AuthzService authzService;
    private final OrgAccessValidator orgAccessValidator;

    public RoleActionController(OrganizationRoleRepository roleRepository,
            OrganizationRoleActionService orgRoleActionService,
            OrganizationUserRoleMappingRepository orgUserRoleMappingRepository,
            AuthzService authzService,
            OrgAccessValidator orgAccessValidator) {
        this.roleRepository = roleRepository;
        this.orgRoleActionService = orgRoleActionService;
        this.orgUserRoleMappingRepository = orgUserRoleMappingRepository;
        this.authzService = authzService;
        this.orgAccessValidator = orgAccessValidator;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getRoleActions(
            @PathVariable("orgId") String orgId,
            @PathVariable("roleId") String roleId,
            @RequestHeader("organizationId") String organizationIdHeader) {
        // Validate header matches path and caller belongs to org
        if (!orgId.equals(organizationIdHeader)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", 400, "message", "organizationId header mismatch", "data", null));
        }
        // require caller be member (or superadmin)
        orgAccessValidator.validateCallerBelongsToOrgOrIsSuperAdmin(orgId);

        OrganizationRole role = roleRepository.findByRoleIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        List<String> codes = orgRoleActionService.getActionCodesForRole(role.getId());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", 200);
        resp.put("message", "Role actions fetched successfully");
        resp.put("data",
                Map.of("organizationId", orgId, "roleId", roleId, "roleName", role.getRoleName(), "actions", codes));
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> assignActions(
            @PathVariable("orgId") String orgId,
            @PathVariable("roleId") String roleId,
            @RequestHeader("organizationId") String organizationIdHeader,
            @RequestBody AssignActionsRequest request) {
        if (!orgId.equals(organizationIdHeader)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", 400, "message", "organizationId header mismatch", "data", null));
        }

        // validate caller
        orgAccessValidator.validateCallerBelongsToOrgOrIsSuperAdmin(orgId);

        OrganizationRole role = roleRepository.findByRoleIdAndIsDeletedFalse(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));

        // convert incoming actionIds (Long) to DB assign
        List<Long> actionIds = request.getActionIds() == null ? List.of() : request.getActionIds();

        // performedBy: userId from JWT
        String performedBy = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        List<String> assigned = orgRoleActionService.assignActionsToRole(role.getId(), actionIds, performedBy);

        // Invalidate cache for all users of this org who have this role
        var mappings = orgUserRoleMappingRepository.findByOrganizationIdAndRoleName(orgId, role.getRoleName());
        for (var m : mappings) {
            authzService.invalidateUserOrgCache(m.getUserId(), orgId);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("organizationId", orgId);
        data.put("roleId", roleId);
        data.put("assignedActionCount", assigned.size());
        data.put("assignedActions", assigned);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", 200);
        resp.put("message", "Actions assigned to role successfully");
        resp.put("data", data);
        return ResponseEntity.ok(resp);
    }
}
