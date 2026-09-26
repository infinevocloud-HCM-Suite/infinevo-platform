package com.infinevo.core.setup;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Checks whether at least one active employee has been created for the tenant (W-24.1, W-13.1).
 */
@Component
public class EmployeeSetupStepChecker implements SetupStepChecker {

    public static final String CODE = "EMPLOYEE";

    private final EmployeeRepository employeeRepository;

    public EmployeeSetupStepChecker(EmployeeRepository employeeRepository) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public PlatformModule module() {
        return null; // Core capability; applies to all tenants
    }

    @Override
    public boolean isComplete(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }
        return employeeRepository.existsByTenantIdAndDeletedFalse(tenantId);
    }
}
