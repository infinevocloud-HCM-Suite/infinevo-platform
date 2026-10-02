package com.infinevo.core.portal;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Leave panel provider (W-25, spec section 4).
 */
@Component
public class LeavePanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "leave";
    }

    @Override
    public PlatformModule module() {
        return null; // Core, available to every tenant
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor("leave", "My Leave", 2, "/api/v1/me/leave-requests", "core.leave.read_own");
    }
}
