package com.infinevo.core.portal;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Collects, filters and sorts the {@link PortalPanelProvider} beans for the current caller (W-25, spec section 4).
 *
 * <p>Three gates control access:
 * <ol>
 *   <li>{@code is_portal_enabled} on the employee: if false or missing, yields an empty list.</li>
 *   <li>Module entitlement: panel module must be null (core) or held by the tenant.</li>
 *   <li>Action check: caller must hold the panel's required {@code *_own} action code.</li>
 * </ol>
 * Strictest wins: all three must allow the panel, otherwise it is excluded.
 */
@Service
public class PortalPanelService {

    private static final Logger log = LoggerFactory.getLogger(PortalPanelService.class);

    private final EmployeeService employeeService;
    private final PermissionService permissionService;
    private final EntitlementService entitlementService;
    private final List<PortalPanelProvider> providers;

    public PortalPanelService(
            EmployeeService employeeService,
            PermissionService permissionService,
            EntitlementService entitlementService,
            List<PortalPanelProvider> providers) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService must not be null");
        this.providers = providers != null ? List.copyOf(providers) : List.of();
    }

    public List<PanelDescriptor> getPanels() {
        Optional<EmployeeResponse> current = employeeService.currentEmployee();
        if (current.isEmpty()) {
            log.debug("Portal panels empty: no employee record linked to current user");
            return List.of();
        }

        EmployeeResponse employee = current.get();
        if (!employee.portalEnabled()) {
            log.debug("Portal panels empty: portal is disabled for employee {}", employee.id());
            return List.of();
        }

        List<PanelDescriptor> result = new ArrayList<>();
        for (PortalPanelProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            // Gate 2: Module entitlement
            if (provider.module() != null && !entitlementService.holds(provider.module())) {
                log.debug("Panel {} excluded: tenant not entitled to module {}", provider.code(), provider.module());
                continue;
            }

            PanelDescriptor descriptor = provider.panel(employee.id());
            if (descriptor == null) {
                continue;
            }

            // Gate 3: Permission action check
            if (descriptor.requiredAction() != null && !permissionService.holds(descriptor.requiredAction())) {
                log.debug("Panel {} excluded: caller lacks action {}", descriptor.code(), descriptor.requiredAction());
                continue;
            }

            result.add(descriptor);
        }

        result.sort(Comparator.comparingInt(PanelDescriptor::displayOrder));
        return List.copyOf(result);
    }
}
