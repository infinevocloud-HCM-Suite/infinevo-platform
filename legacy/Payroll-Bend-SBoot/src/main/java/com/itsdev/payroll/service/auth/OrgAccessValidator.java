package com.itsdev.payroll.service.auth;

import com.itsdev.payroll.repository.OrganizationUserRoleMappingRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Small helper to validate that the caller belongs to the org (or is a
 * super-admin).
 * Used by admin endpoints to prevent organizationId header spoofing.
 */
@Component
public class OrgAccessValidator {

    private final OrganizationUserRoleMappingRepository orgUserRoleRepo;

    public OrgAccessValidator(OrganizationUserRoleMappingRepository orgUserRoleRepo) {
        this.orgUserRoleRepo = orgUserRoleRepo;
    }

    /**
     * Throws RuntimeException if the caller is not a member of the organization and
     * not super-admin.
     * You can change to AccessDeniedException or a custom exception for nicer HTTP
     * mapping.
     */
    public void validateCallerBelongsToOrgOrIsSuperAdmin(String organizationId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated");
        }

        // Try to get userId from JWT (reuse your JWTUtil)
        String callerUserId = com.itsdev.payroll.util.JWTUtil.getUserIdAndEmailFromToken().get("userId");

        // Quick super-admin check: keycloak realm role ROLE_SUPER_ADMIN (adjust if
        // different)
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (isSuperAdmin)
            return;

        // Check mapping exists for this user and org
        boolean exists = orgUserRoleRepo.existsByUserIdAndOrganizationId(callerUserId, organizationId);
        if (!exists) {
            throw new RuntimeException("Access denied: caller does not belong to organization " + organizationId);
        }
    }
}
