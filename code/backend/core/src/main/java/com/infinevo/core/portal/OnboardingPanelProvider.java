package com.infinevo.core.portal;

import com.infinevo.core.onboarding.EmployeeOnboardingTaskRepository;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "Onboarding" tab on /me (W-102), shown only to an employee who has at least one onboarding task: employees who
 * existed before W-102 get no empty tab. Display order 9, after every other panel (payslips holds 4).
 */
@Component
public class OnboardingPanelProvider implements PortalPanelProvider {

    static final int DISPLAY_ORDER = 9;

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

    /** The one panel that reads the database, so it needs the transaction the tenant binding lives in. */
    @Override
    @Transactional(readOnly = true)
    public PanelDescriptor panel(UUID employeeId) {
        if (employeeId == null) {
            return null;
        }
        UUID tenantId = TenantContext.current().orElse(null);
        if (tenantId == null || taskRepository.countByTenantIdAndEmployeeId(tenantId, employeeId) == 0) {
            return null;
        }
        return new PanelDescriptor(
                "onboarding", "Onboarding", DISPLAY_ORDER, "/api/v1/me/onboarding", "core.onboarding.read_own");
    }
}
