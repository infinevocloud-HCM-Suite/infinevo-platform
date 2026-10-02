package com.infinevo.hrms.portal;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Self-service portal panel provider for timesheet (W-25, spec section 4).
 *
 * <p>Placeholder descriptor until HRMS timesheet ticket builds the full feature (spec decision 2).
 */
@Component
public class TimesheetPanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "timesheet";
    }

    @Override
    public PlatformModule module() {
        return PlatformModule.HRMS;
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor("timesheet", "My Timesheet", 5, "/api/v1/me/timesheet", "hrms.timesheet.read_own");
    }
}
