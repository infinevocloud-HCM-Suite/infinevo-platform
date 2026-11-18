package com.infinevo.core.portal;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Onboarding panel provider for employee portal (W-102).
 */
@Component
public class OnboardingPanelProvider implements PortalPanelProvider {

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
        return new PanelDescriptor("onboarding", "Onboarding", 4, "/api/v1/me/onboarding", "core.onboarding.read_own");
    }
}
