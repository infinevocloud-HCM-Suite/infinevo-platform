package com.infinevo.core.portal;

import com.infinevo.core.onboarding.EmployeeOnboardingTaskRepository;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Onboarding panel provider for employee portal (W-102).
 */
@Component
public class OnboardingPanelProvider implements PortalPanelProvider {

    private final EmployeeOnboardingTaskRepository taskRepository;

    public OnboardingPanelProvider(EmployeeOnboardingTaskRepository taskRepository) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
    }

    @Override
    public String code() {
        return "onboarding";
    }

    @Override
    public PlatformModule module() {
        return null; // Core, available to every tenant
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        if (employeeId == null) {
            return null;
        }
        UUID tenantId = TenantContext.current().orElse(null);
        if (tenantId == null) {
            return null;
        }
        long count = taskRepository.countByTenantIdAndEmployeeId(tenantId, employeeId);
        if (count == 0) {
            return null;
        }
        return new PanelDescriptor("onboarding", "Onboarding", 9, "/api/v1/me/onboarding", "core.onboarding.read_own");
    }
}
