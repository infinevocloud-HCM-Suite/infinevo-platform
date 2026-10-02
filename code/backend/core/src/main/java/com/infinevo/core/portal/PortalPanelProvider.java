package com.infinevo.core.portal;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;

/**
 * Interface implemented by modules to register a panel in the self-service portal (W-25, spec section 4;
 * {@code 12-core-contracts.md:110}).
 */
public interface PortalPanelProvider {

    /**
     * The panel id the shell routes on (e.g. {@code profile}, {@code leave}, {@code documents},
     * {@code payslips}, {@code timesheet}).
     */
    String code();

    /**
     * The module this panel belongs to. {@code null} means core (available to every tenant).
     */
    PlatformModule module();

    /**
     * Assembles the panel descriptor for the given employee.
     *
     * @param employeeId the current caller's employee id
     * @return the descriptor describing route code, display order, endpoint, and required action
     */
    PanelDescriptor panel(UUID employeeId);
}
