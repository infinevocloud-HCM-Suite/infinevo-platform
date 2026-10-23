package com.infinevo.core.portal;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Work From Home panel provider for employee portal (W-74).
 */
@Component
public class WfhPanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "wfh";
    }

    @Override
    public PlatformModule module() {
        return null; // Core, available to every tenant
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor("wfh", "Work From Home", 3, "/api/v1/me/wfh-requests", "core.wfh.read_own");
    }
}
