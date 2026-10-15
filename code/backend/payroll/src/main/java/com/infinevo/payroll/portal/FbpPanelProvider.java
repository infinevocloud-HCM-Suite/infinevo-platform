package com.infinevo.payroll.portal;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Self-service portal panel provider for Flexible Benefit Plan declaration (W-27.2, BUG-D5-025).
 */
@Component
public class FbpPanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "fbp";
    }

    @Override
    public PlatformModule module() {
        return PlatformModule.PAYROLL;
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor("fbp", "Flexible Benefits", 8, "/api/v1/me/fbp-declaration", "payroll.fbp.read_own");
    }
}
