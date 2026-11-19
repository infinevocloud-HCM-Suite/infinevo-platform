package com.infinevo.hrms.portal;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Self-service portal panel provider for performance reviews (W-106.1).
 */
@Component
public class ReviewsPanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "reviews";
    }

    @Override
    public PlatformModule module() {
        return PlatformModule.HRMS;
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor(
                "reviews", "Reviews", 10, "/api/v1/performance/reviews/my", "hrms.performance.read_own");
    }
}
