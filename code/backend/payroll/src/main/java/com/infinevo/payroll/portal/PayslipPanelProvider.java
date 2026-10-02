package com.infinevo.payroll.portal;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Self-service portal panel provider for payslips (W-25, spec section 4).
 *
 * <p>The endpoint is {@code PayslipController}'s list of the caller's own payslips (W-36.2), which
 * replaced the W-25 placeholder.
 */
@Component
public class PayslipPanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "payslips";
    }

    @Override
    public PlatformModule module() {
        return PlatformModule.PAYROLL;
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor("payslips", "My Payslips", 4, "/api/v1/me/payslips", "payroll.payslip.read_own");
    }
}
