package com.infinevo.core.setup;

import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Checks whether at least one work location has been created for the tenant (W-24.1, W-14.1).
 */
@Component
public class WorkLocationSetupStepChecker implements SetupStepChecker {

    public static final String CODE = "WORK_LOCATION";

    private final WorkLocationRepository workLocationRepository;

    public WorkLocationSetupStepChecker(WorkLocationRepository workLocationRepository) {
        this.workLocationRepository =
                Objects.requireNonNull(workLocationRepository, "workLocationRepository must not be null");
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
        return workLocationRepository.existsByTenantId(tenantId);
    }
}
