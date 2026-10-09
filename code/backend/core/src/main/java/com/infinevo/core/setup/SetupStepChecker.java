package com.infinevo.core.setup;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.UUID;

/**
 * Setup step completion checker (W-24.1, 12-core-contracts.md §3).
 *
 * <p>Each module registers one Spring bean per step; core collects them by type, never by name.
 */
public interface SetupStepChecker {

    /**
     * The {@code step_code} stored on the row, e.g. {@code WORK_LOCATION}, {@code EMPLOYEE}, {@code PAY_SCHEDULE}.
     */
    String code();

    /**
     * The module the step belongs to; {@code null} means core, applies to every tenant.
     * Matches the {@code module} column.
     */
    PlatformModule module();

    /**
     * An existence query under the caller's tenant context; must be side-effect free.
     *
     * @param tenantId the tenant to check
     * @return true if the step condition is currently satisfied, false otherwise
     */
    boolean isComplete(UUID tenantId);

    /**
     * Whether the step's data was written by a country template and nobody has saved it since (W-73.9), so the
     * checklist shows it as "Pre-filled - review" rather than done. Asked only of a complete step; side-effect
     * free, like {@link #isComplete}.
     *
     * @param tenantId the tenant to check
     * @return true while every row the step looks at still carries the template as its last writer
     */
    default boolean isPrefilled(UUID tenantId) {
        return false;
    }
}
