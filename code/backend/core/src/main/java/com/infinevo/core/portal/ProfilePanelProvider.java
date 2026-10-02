package com.infinevo.core.portal;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Profile panel provider (W-25, spec section 4).
 */
@Component
public class ProfilePanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "profile";
    }

    @Override
    public PlatformModule module() {
        return null; // Core, available to every tenant
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor("profile", "My Profile", 1, "/api/v1/me/employee", "core.employee.read_own");
    }
}
