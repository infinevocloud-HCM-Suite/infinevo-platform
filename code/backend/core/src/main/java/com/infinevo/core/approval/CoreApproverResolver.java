package com.infinevo.core.approval;

import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.authz.UserRole;
import com.infinevo.core.authz.UserRoleRepository;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.org.ReportingLineService;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Core approver resolver (W-15.2, spec section 4).
 *
 * <p>Resolves approvers from the reporting line, role assignments, or direct employee references.
 * Unresolvable approvers (such as an employee with no manager) yield {@link Optional#empty()} rather than stalling.
 * {@link ApproverKind#PROJECT_MANAGER} is looked up from custom {@link ApproverResolver} beans and is unassignable if none is found.
 */
@Component
public class CoreApproverResolver {

    private final ReportingLineService reportingLineService;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final EmployeeRepository employeeRepository;
    private final List<ApproverResolver> customResolvers;

    @Autowired
    public CoreApproverResolver(
            ReportingLineService reportingLineService,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            EmployeeRepository employeeRepository,
            @Autowired(required = false) List<ApproverResolver> customResolvers) {
        this.reportingLineService =
                Objects.requireNonNull(reportingLineService, "reportingLineService must not be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.customResolvers = customResolvers != null ? customResolvers : Collections.emptyList();
    }

    public Optional<UUID> resolve(UUID tenantId, UUID employeeId, ApproverKind kind, String contextRef) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");

        return switch (kind) {
            case REPORTING_MANAGER -> resolveManagerAtDepth(employeeId, 0);
            case INDIRECT_MANAGER -> resolveManagerAtDepth(employeeId, 1);
            case APPROVER_LEVEL_1 -> resolveManagerAtDepth(employeeId, 0);
            case APPROVER_LEVEL_2 -> resolveManagerAtDepth(employeeId, 1);
            case APPROVER_LEVEL_3 -> resolveManagerAtDepth(employeeId, 2);
            case NAMED_EMPLOYEE -> resolveNamedEmployee(contextRef);
            case ROLE -> resolveRole(tenantId, contextRef);
            case PROJECT_MANAGER -> resolveProjectManager(tenantId, employeeId, contextRef);
        };
    }

    private Optional<UUID> resolveManagerAtDepth(UUID employeeId, int depth) {
        List<Employee> chain = reportingLineService.chainAbove(employeeId, LocalDate.now());
        if (chain != null && chain.size() > depth) {
            return Optional.of(chain.get(depth).getId());
        }
        return Optional.empty();
    }

    private Optional<UUID> resolveNamedEmployee(String contextRef) {
        if (contextRef == null || contextRef.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(contextRef.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<UUID> resolveRole(UUID tenantId, String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return Optional.empty();
        }
        Optional<Role> role = roleRepository.findByTenantIdAndCode(tenantId, roleCode.trim());
        if (role.isEmpty()) {
            return Optional.empty();
        }
        List<UserRole> userRoles =
                userRoleRepository.findByTenantIdAndRoleId(tenantId, role.get().getId());
        for (UserRole userRole : userRoles) {
            Optional<Employee> emp = employeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse(
                    tenantId, userRole.getUserAccountId());
            if (emp.isPresent()) {
                return Optional.of(emp.get().getId());
            }
        }
        return Optional.empty();
    }

    private Optional<UUID> resolveProjectManager(UUID tenantId, UUID employeeId, String contextRef) {
        for (ApproverResolver resolver : customResolvers) {
            if (resolver.kind() == ApproverKind.PROJECT_MANAGER) {
                return resolver.resolve(tenantId, employeeId, contextRef);
            }
        }
        return Optional.empty();
    }
}
