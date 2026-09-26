package com.infinevo.core.navigation;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;

/**
 * Catalogue of navigation item definitions (W-12.3, spec section 4).
 *
 * <p><strong>The catalogue is code, not a table.</strong> A menu item exists because an endpoint
 * exists; a row in a table would let the two drift. {@link NavigationCatalogueValidator} refuses
 * to start the application if any leaf's {@code targetEndpoint} has no {@code GET} mapping, and
 * {@code NavigationMatchesEnforcementIT} walks every leaf against the real controllers.
 *
 * <p><strong>An item is added here in the ticket that ships its endpoint, never before.</strong>
 * Items that are not here yet, and the ticket that adds each one:
 *
 * <ul>
 *   <li>{@code core.employee} → {@code GET /api/v1/employees} — W-13.3 (employee search and listing)
 *   <li>{@code hrms.timesheets} → {@code GET /api/v1/timesheets} — the HRMS timesheet ticket
 *   <li>{@code payroll.runs} → {@code GET /api/v1/payroll/runs} — W-29 (pay run)
 * </ul>
 *
 * <p>A module item names its {@link PlatformModule}; a core item passes {@code null}. The service
 * filters by module first, then by action, and hides a parent whose children are all hidden.
 */
public final class NavigationCatalogue {

    public record ItemDefinition(
            String key,
            String labelKey,
            String path,
            String targetEndpoint,
            PlatformModule requiredModule,
            String requiredAction,
            List<ItemDefinition> children) {

        public ItemDefinition(
                String key,
                String labelKey,
                String path,
                String targetEndpoint,
                PlatformModule requiredModule,
                String requiredAction) {
            this(key, labelKey, path, targetEndpoint, requiredModule, requiredAction, List.of());
        }

        public boolean hasChildren() {
            return children != null && !children.isEmpty();
        }
    }

    public static final List<ItemDefinition> DEFAULT_ITEMS = List.of(
            new ItemDefinition(
                    "core.org",
                    "nav.organisation",
                    "/org/departments",
                    "/api/v1/departments",
                    null,
                    "core.org.read",
                    List.of(
                            new ItemDefinition(
                                    "core.org.departments",
                                    "nav.departments",
                                    "/org/departments",
                                    "/api/v1/departments",
                                    null,
                                    "core.org.read"),
                            new ItemDefinition(
                                    "core.org.designations",
                                    "nav.designations",
                                    "/org/designations",
                                    "/api/v1/designations",
                                    null,
                                    "core.org.read"),
                            new ItemDefinition(
                                    "core.org.locations",
                                    "nav.locations",
                                    "/org/work-locations",
                                    "/api/v1/work-locations",
                                    null,
                                    "core.org.read"))),
            new ItemDefinition("core.roles", "nav.roles", "/roles", "/api/v1/roles", null, "core.role.read"),
            new ItemDefinition("core.audit", "nav.audit", "/audit", "/api/v1/audit", null, "core.audit.read"));

    private NavigationCatalogue() {}
}
