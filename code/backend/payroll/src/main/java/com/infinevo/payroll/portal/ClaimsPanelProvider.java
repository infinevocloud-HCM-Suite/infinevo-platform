package com.infinevo.payroll.portal;

import com.infinevo.core.portal.PanelDescriptor;
import com.infinevo.core.portal.PortalPanelProvider;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Self-service portal panel provider for reimbursement claims and ad-hoc deductions (W-47.4 §4).
 *
 * <p>The endpoint is {@code ReimbursementClaimController}'s list of the caller's own claims (W-35.1); the
 * panel's deductions tab reads {@code /api/v1/me/employee-deductions} on its own (§13 decision 4).
 */
@Component
public class ClaimsPanelProvider implements PortalPanelProvider {

    @Override
    public String code() {
        return "claims";
    }

    @Override
    public PlatformModule module() {
        return PlatformModule.PAYROLL;
    }

    @Override
    public PanelDescriptor panel(UUID employeeId) {
        return new PanelDescriptor(
                "claims",
                "Claims and deductions",
                6,
                "/api/v1/me/reimbursement-claims",
                "payroll.reimbursement_claim.read_own");
    }
}
