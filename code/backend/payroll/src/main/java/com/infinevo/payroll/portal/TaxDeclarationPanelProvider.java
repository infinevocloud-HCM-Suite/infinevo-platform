package com.infinevo.payroll.portal;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Self-service portal panel provider for tax declaration (W-47.1b §5a, W-47.3 §14 decision 1).
 */
@Component
public class TaxDeclarationPanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "taxDeclaration";
    }

    @Override
    public PlatformModule module() {
        return PlatformModule.PAYROLL;
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor(
                "taxDeclaration",
                "Tax declaration",
                5,
                "/api/v1/me/tax-declaration",
                "payroll.tax_declaration.read_own");
    }
}
