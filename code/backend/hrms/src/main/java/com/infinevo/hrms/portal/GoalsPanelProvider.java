package com.infinevo.hrms.portal;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Self-service portal panel provider for goals and OKRs (W-106.2).
 */
@Component
public class GoalsPanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "goals";
    }

    @Override
    public PlatformModule module() {
        return PlatformModule.HRMS;
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor("goals", "Goals & OKRs", 11, "/api/v1/hrms/goals/me", "hrms.goal.read_own");
    }
}
