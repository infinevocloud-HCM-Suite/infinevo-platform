package com.infinevo.core.navigation;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue of navigation item definitions (W-12.3, spec section 4).
 *
 * <p><strong>The catalogue is code, not a table.</strong> A menu item exists because an endpoint
 * exists; a row in a table would let the two drift. {@link NavigationCatalogueValidator} refuses
 * to start the application if any leaf's {@code targetEndpoint} has no {@code GET} mapping, and
 * {@code NavigationMatchesEnforcementIT} walks every leaf against the real controllers.
 *
 * <p><strong>An item is added in the ticket that ships its endpoint, never before.</strong>
 *
 * <p>A module's items are not listed here: the module supplies them through a
 * {@link NavigationContributor}, next to the controller they target ({@code payroll.runs} is
 * payroll's). {@link #withContributed} is the whole menu.
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

    /**
     * The core menu, in groups (D-34): People, Organisation, Approvals, Leave, Settings, then the platform's
     * Tenants. A group's {@code path} and {@code targetEndpoint} are its first child's; the feed rewrites the path
     * to the first child the caller can see. People, Organisation and Settings require no action of their own, so
     * the service shows each exactly when one of its children is visible. Leaf keys, label keys, paths, endpoints
     * and actions are the ones the flat menu had, so the frontend's routes and labels still match.
     */
    public static final List<ItemDefinition> DEFAULT_ITEMS = List.of(
            new ItemDefinition(
                    "core.people",
                    "nav.people",
                    "/employees",
                    "/api/v1/employees",
                    null,
                    null,
                    List.of(
                            new ItemDefinition(
                                    "core.employee",
                                    "nav.employees",
                                    "/employees",
                                    "/api/v1/employees",
                                    null,
                                    "core.employee.read"),
                            new ItemDefinition(
                                    "core.invitations.users",
                                    "nav.userInvitations",
                                    "/invitations/users",
                                    "/api/v1/user-invitations",
                                    null,
                                    "core.user.manage"),
                            new ItemDefinition(
                                    "core.invitations.employees",
                                    "nav.employeeInvitations",
                                    "/invitations/employees",
                                    "/api/v1/employee-invitations",
                                    null,
                                    "core.employee.create"))),
            new ItemDefinition(
                    "core.org",
                    "nav.organisation",
                    "/org/departments",
                    "/api/v1/departments",
                    null,
                    null,
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
                                    "core.org.read"),
                            new ItemDefinition(
                                    "core.holiday",
                                    "nav.holidays",
                                    "/holidays",
                                    "/api/v1/holiday-calendars",
                                    null,
                                    "core.holiday.read"),
                            new ItemDefinition(
                                    "core.roles", "nav.roles", "/roles", "/api/v1/roles", null, "core.role.read"))),
            new ItemDefinition(
                    "core.approvals",
                    "nav.approvals",
                    "/approvals",
                    "/api/v1/approvals/pending",
                    null,
                    "core.approval.decide",
                    List.of(
                            new ItemDefinition(
                                    "core.approvals.inbox",
                                    "nav.approvals.inbox",
                                    "/approvals",
                                    "/api/v1/approvals/pending",
                                    null,
                                    "core.approval.decide"),
                            new ItemDefinition(
                                    "core.approvals.delegations",
                                    "nav.approvals.delegations",
                                    "/approvals/delegations",
                                    "/api/v1/approval-delegations",
                                    null,
                                    "core.approval.delegate"),
                            new ItemDefinition(
                                    "core.approvals.definitions",
                                    "nav.approvals.definitions",
                                    "/approvals/definitions",
                                    "/api/v1/approval-definitions",
                                    null,
                                    "core.approval_definition.manage"))),
            new ItemDefinition(
                    "core.leave",
                    "nav.leave",
                    "/leave/types",
                    "/api/v1/leave-types",
                    null,
                    "core.leave.read",
                    List.of(
                            new ItemDefinition(
                                    "core.leave.types",
                                    "nav.leave.types",
                                    "/leave/types",
                                    "/api/v1/leave-types",
                                    null,
                                    "core.leave_type.manage"),
                            new ItemDefinition(
                                    "core.leave.allocations",
                                    "nav.leave.allocations",
                                    "/leave/allocations",
                                    "/api/v1/leave-types",
                                    null,
                                    "core.leave_balance.manage"),
                            new ItemDefinition(
                                    "core.leave.requests",
                                    "nav.leave.requests",
                                    "/leave/requests",
                                    "/api/v1/leave-requests",
                                    null,
                                    "core.leave.read"),
                            new ItemDefinition(
                                    "core.leave.import",
                                    "nav.leave.import",
                                    "/leave/import",
                                    "/api/v1/leave-imports",
                                    null,
                                    "core.leave_balance.manage"))),
            new ItemDefinition(
                    "core.settings",
                    "nav.settings",
                    "/setup",
                    "/api/v1/setup-checklist",
                    null,
                    null,
                    List.of(
                            new ItemDefinition(
                                    "core.setup",
                                    "nav.setup",
                                    "/setup",
                                    "/api/v1/setup-checklist",
                                    null,
                                    "core.tenant.read"),
                            // W-73.1: the company profile - name, logo, tagline - for whoever reads the tenant
                            new ItemDefinition(
                                    "core.settings.company",
                                    "nav.settings.company",
                                    "/settings/company",
                                    "/api/v1/tenants/current/profile",
                                    null,
                                    "core.tenant.read"),
                            new ItemDefinition(
                                    "core.audit", "nav.audit", "/audit", "/api/v1/audit", null, "core.audit.read"))),
            new ItemDefinition(
                    "core.tenants", "nav.tenants", "/admin/tenants", "/api/v1/tenants", null, "core.tenant.provision"));

    /** The core items followed by every module's contributed items, in contributor order. */
    public static List<ItemDefinition> withContributed(List<NavigationContributor> contributors) {
        List<ItemDefinition> all = new ArrayList<>(DEFAULT_ITEMS);
        for (NavigationContributor contributor : contributors) {
            all.addAll(contributor.items());
        }
        return List.copyOf(all);
    }

    private NavigationCatalogue() {}
}
