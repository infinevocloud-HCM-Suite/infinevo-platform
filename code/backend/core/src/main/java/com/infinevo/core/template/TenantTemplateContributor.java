package com.infinevo.core.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One section of a country template, written into a tenant by the module that owns its tables (W-73.9).
 *
 * <p>{@code core} reads {@code reference.country_template} and calls every contributor bean; a module
 * implements this interface next to its own repositories, the way {@code NavigationContributor} adds
 * menu items, so no module calls another.
 *
 * <p>Every row a contributor writes carries {@link #ACTOR} as its {@code created_by} and
 * {@code updated_by}. A save through the module's own service replaces {@code updated_by} with the
 * saving user, which is how the setup checklist tells "pre-filled, not yet reviewed" from "done".
 */
public interface TenantTemplateContributor {

    /** The {@code created_by} and {@code updated_by} of every row a template writes. */
    String ACTOR = "template";

    /** The {@code reference.country_template.section} this contributor writes, e.g. {@code holidays}. */
    String section();

    /** The module the section belongs to; {@code null} for core. A tenant without it is skipped. */
    default PlatformModule module() {
        return null;
    }

    /**
     * Writes the section's rows for the tenant, only where the tenant has none of its own. Runs with
     * the tenant bound, in the caller's transaction. Must be safe to call again.
     *
     * @param tenantId the tenant to write for
     * @param payload the section's {@code payload}
     * @param today the tenant's today, for anything dated
     * @return true if anything was written; false if the tenant already had its own rows
     */
    boolean apply(UUID tenantId, JsonNode payload, LocalDate today);
}
