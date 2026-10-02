package com.infinevo.core.portal;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Document panel provider (W-25, spec section 4).
 */
@Component
public class DocumentPanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "documents";
    }

    @Override
    public PlatformModule module() {
        return null; // Core, available to every tenant
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor("documents", "My Documents", 3, "/api/v1/me/documents", "core.document.read_own");
    }
}
