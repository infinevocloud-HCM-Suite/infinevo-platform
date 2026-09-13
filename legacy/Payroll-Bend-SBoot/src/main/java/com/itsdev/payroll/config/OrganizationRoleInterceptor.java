package com.itsdev.payroll.config;

import com.itsdev.payroll.entity.OrganizationUserRoleMapping;
import com.itsdev.payroll.repository.OrganizationUserRoleMappingRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Optional;

/**
 * OrganizationRoleInterceptor
 *
 * Server-side enforcement of the Admin Portal / Employee Portal boundary
 * (Stage 2). It complements the frontend route guards, which can be bypassed by
 * editing localStorage. Here we authorize each API request against the caller's
 * actual {@link OrganizationUserRoleMapping} row for the target organization.
 *
 * <h3>Role signals (no data migration)</h3>
 * <ul>
 *   <li><b>Admin/HR user</b>: mapping has a non-blank {@code roleId}/{@code roleName}.</li>
 *   <li><b>Employee user</b>: mapping has {@code employeePortalEnable == true}
 *       (roleId/roleName are typically null for employee-only accounts).</li>
 * </ul>
 *
 * <h3>Enforcement model</h3>
 * The check only runs when the target organization can be resolved (from the
 * {@code organizationId} header, falling back to an {@code organizationId} request
 * parameter). Requests with no organization scope (e.g. list-my-organizations,
 * create-organization) are left to Spring Security's authentication requirement.
 *
 * <ul>
 *   <li>No mapping for (user, org) -&gt; 403 (not a member of this organization).</li>
 *   <li><b>Employee-accessible</b> paths (used by the employee portal, and also by
 *       admins) -&gt; allowed for any valid member (admin or employee-portal-enabled).</li>
 *   <li><b>All other</b> {@code /api/**} paths are treated as <b>admin-only</b> -&gt;
 *       allowed only when the mapping carries an admin role; employee-only accounts
 *       get 403.</li>
 * </ul>
 *
 * <p><b>Dual-access users</b> (admin role AND employee portal) are authorized here
 * by their real role, so they pass admin checks regardless of which portal they
 * logged in through. Per-portal scoping for dual users remains a frontend concern.</p>
 *
 * <p><b>Maintenance note:</b> {@link #EMPLOYEE_ACCESSIBLE_PREFIXES} is a deny-by-default
 * allowlist. Any NEW endpoint the employee portal starts calling MUST be added here,
 * otherwise employee-only users will receive 403 on it.</p>
 */
@Component
public class OrganizationRoleInterceptor implements HandlerInterceptor {

    private final OrganizationUserRoleMappingRepository roleMappingRepository;

    public OrganizationRoleInterceptor(OrganizationUserRoleMappingRepository roleMappingRepository) {
        this.roleMappingRepository = roleMappingRepository;
    }

    /**
     * Path prefixes (relative to the context root) that the Employee Portal legitimately
     * calls. These are accessible to any valid organization member (employee or admin).
     * Everything else under /api/** is treated as admin-only.
     */
    private static final List<String> EMPLOYEE_ACCESSIBLE_PREFIXES = List.of(
            "/api/employees-portal",                 // employee self profile
            "/api/payrun-employees",                 // payslips list / view / download
            "/api/employee-it-declarations",         // IT declaration (self)
            "/api/proof-of-investments",             // proof of investment (self) - plural
            "/api/proof-of-investment",              // proof-of-investment document download - singular
            "/api/employee-investment-proof",        // POI upload/view (self)
            "/api/section6a-items",                  // shared reference data for declarations
            "/api/organization-user-role-mapping",   // my-role / my-organizations (login + guards)
            "/api/employee/reimbursements",          // employee reimbursement requests (submit + view own)
            "/api/employee-deductions/my-deductions" // employee self deductions (view own)
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // Never interfere with CORS preflight requests.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Resolve the organization scope. If we cannot, we cannot make an
        // organization-scoped role decision, so defer to Spring Security (which
        // already requires authentication for /api/**).
        String organizationId = resolveOrganizationId(request);
        if (isBlank(organizationId)) {
            return true;
        }

        // Resolve the caller from the validated JWT. If it is missing/invalid,
        // Spring Security would already have rejected the request; be defensive.
        String userId = resolveUserId();
        if (isBlank(userId)) {
            return true;
        }

        Optional<OrganizationUserRoleMapping> mappingOpt =
                roleMappingRepository.findByUserIdAndOrganizationId(userId, organizationId);

        if (mappingOpt.isEmpty()) {
            return deny(response, "You are not a member of this organization.");
        }

        OrganizationUserRoleMapping mapping = mappingOpt.get();
        boolean isAdmin = !isBlank(mapping.getRoleId()) || !isBlank(mapping.getRoleName());
        boolean isEmployeePortalEnabled = Boolean.TRUE.equals(mapping.getEmployeePortalEnable());

        String path = request.getRequestURI();

        if (isEmployeeAccessible(path)) {
            // Employee/shared endpoint: allowed for any valid member.
            if (isAdmin || isEmployeePortalEnabled) {
                return true;
            }
            return deny(response, "You do not have access to this resource in this organization.");
        }

        // Admin-only endpoint (deny-by-default).
        if (isAdmin) {
            return true;
        }
        return deny(response, "This action requires an administrator role in this organization.");
    }

    private boolean isEmployeeAccessible(String path) {
        if (path == null) {
            return false;
        }
        for (String prefix : EMPLOYEE_ACCESSIBLE_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private String resolveOrganizationId(HttpServletRequest request) {
        String header = request.getHeader("organizationId");
        if (!isBlank(header)) {
            return header.trim();
        }
        String param = request.getParameter("organizationId");
        return isBlank(param) ? null : param.trim();
    }

    private String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return null;
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaimAsString("sub");
    }

    private boolean deny(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"" + escapeJson(message) + "\"}");
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
