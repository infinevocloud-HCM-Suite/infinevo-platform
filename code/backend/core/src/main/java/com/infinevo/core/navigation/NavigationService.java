package com.infinevo.core.navigation;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.setup.SetupChecklistService;
import com.infinevo.core.tenant.TenantBranding;
import com.infinevo.core.tenant.TenantProfileService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives the navigation feed for the current caller and bound tenant (W-12.3, spec section 4).
 *
 * <p>Filters items by module entitlement (W-12.2) and by action (W-11.2) - the item's action or any of its
 * alternatives (D-76). The self-service portal item also needs a linked employee record (D-75).
 * Returns the filtered menu alongside the caller's full action-code set, the caller's home page (D-35) and the
 * bound tenant's branding - name, logo link, tagline (W-73.1).
 */
@Service
public class NavigationService {

    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);

    private final EntitlementService entitlementService;
    private final PermissionService permissionService;
    private final List<NavigationCatalogue.ItemDefinition> catalogueItems;
    /** Reads the bound tenant's name; null in unit tests, which then get a null name. */
    private final JdbcTemplate jdbcTemplate;
    /** Answers whether setup is finished, for a tenant admin's home page; null means "do not send to setup". */
    private final SetupChecklistService setupChecklist;

    private final PlatformTenant platformTenant;

    /**
     * Reads the bound tenant's name, tagline and logo link in one go (W-73.1); null in a context without it
     * (a unit test), which then falls back to the name alone through {@link #jdbcTemplate}.
     */
    private final TenantProfileService tenantProfiles;

    /**
     * Answers whether the caller has a linked employee record, which the self-service portal item needs on top
     * of its action (D-75): the action is seeded on every role that may be on the payroll, the record says who
     * actually is. Null only in unit tests that construct the service without one; the item then shows on the
     * action alone.
     */
    private final EmployeeService employees;

    @Autowired
    public NavigationService(
            EntitlementService entitlementService,
            PermissionService permissionService,
            ObjectProvider<NavigationContributor> contributors,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<SetupChecklistService> setupChecklist,
            ObjectProvider<PlatformTenant> platformTenant,
            ObjectProvider<TenantProfileService> tenantProfiles,
            ObjectProvider<EmployeeService> employees) {
        this(
                entitlementService,
                permissionService,
                NavigationCatalogue.withContributed(contributors.orderedStream().toList()),
                jdbcTemplate,
                setupChecklist.getIfAvailable(),
                platformTenant.getIfAvailable(PlatformTenant::new),
                tenantProfiles.getIfAvailable(),
                employees.getIfAvailable());
    }

    public NavigationService(EntitlementService entitlementService, PermissionService permissionService) {
        this(entitlementService, permissionService, NavigationCatalogue.DEFAULT_ITEMS);
    }

    public NavigationService(
            EntitlementService entitlementService,
            PermissionService permissionService,
            List<NavigationCatalogue.ItemDefinition> catalogueItems) {
        this(entitlementService, permissionService, catalogueItems, null);
    }

    public NavigationService(
            EntitlementService entitlementService,
            PermissionService permissionService,
            List<NavigationCatalogue.ItemDefinition> catalogueItems,
            JdbcTemplate jdbcTemplate) {
        this(entitlementService, permissionService, catalogueItems, jdbcTemplate, null, new PlatformTenant());
    }

    public NavigationService(
            EntitlementService entitlementService,
            PermissionService permissionService,
            List<NavigationCatalogue.ItemDefinition> catalogueItems,
            JdbcTemplate jdbcTemplate,
            SetupChecklistService setupChecklist,
            PlatformTenant platformTenant) {
        this(entitlementService, permissionService, catalogueItems, jdbcTemplate, setupChecklist, platformTenant, null);
    }

    public NavigationService(
            EntitlementService entitlementService,
            PermissionService permissionService,
            List<NavigationCatalogue.ItemDefinition> catalogueItems,
            JdbcTemplate jdbcTemplate,
            SetupChecklistService setupChecklist,
            PlatformTenant platformTenant,
            TenantProfileService tenantProfiles) {
        this(
                entitlementService,
                permissionService,
                catalogueItems,
                jdbcTemplate,
                setupChecklist,
                platformTenant,
                tenantProfiles,
                null);
    }

    public NavigationService(
            EntitlementService entitlementService,
            PermissionService permissionService,
            List<NavigationCatalogue.ItemDefinition> catalogueItems,
            JdbcTemplate jdbcTemplate,
            SetupChecklistService setupChecklist,
            PlatformTenant platformTenant,
            TenantProfileService tenantProfiles,
            EmployeeService employees) {
        this.jdbcTemplate = jdbcTemplate;
        this.setupChecklist = setupChecklist;
        this.tenantProfiles = tenantProfiles;
        this.employees = employees;
        this.platformTenant = Objects.requireNonNull(platformTenant, "platformTenant must not be null");
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.catalogueItems = Objects.requireNonNull(catalogueItems, "catalogueItems must not be null");
    }

    /**
     * Derives the navigation response containing visible items and held action codes.
     */
    @Transactional(readOnly = true)
    public NavigationResponse navigation() {
        Set<String> actions = permissionService.currentActions();
        List<NavigationItemResponse> visibleItems = new ArrayList<>();

        boolean platform =
                TenantContext.current().map(platformTenant::isPlatformTenant).orElse(false);
        for (NavigationCatalogue.ItemDefinition itemDef : catalogueItems) {
            filterItem(itemDef, actions, platform).ifPresent(visibleItems::add);
        }

        Set<PlatformModule> modules = EnumSet.noneOf(PlatformModule.class);
        for (PlatformModule module : PlatformModule.values()) {
            if (entitlementService.holds(module)) {
                modules.add(module);
            }
        }

        List<NavigationItemResponse> items = List.copyOf(visibleItems);
        TenantBranding branding = tenantBranding();
        return new NavigationResponse(
                items,
                actions,
                modules,
                branding.name(),
                homePath(items, actions),
                branding.logoUrl(),
                branding.tagline());
    }

    /** The portal's own page: mounted for every signed-in user whatever the feed says, so always a safe home. */
    static final String PORTAL_HOME = "/me";

    static final String PLATFORM_HOME = "/admin";
    /** The platform home before W-73.2's dashboard, still the fallback when the dashboard item is withdrawn. */
    static final String PLATFORM_TENANTS = "/admin/tenants";

    static final String SETUP_HOME = "/setup";
    static final String PAYROLL_HOME = "/payroll/dashboard";
    static final String HRMS_HOME = "/hrms/dashboard";
    static final String EMPLOYEES_HOME = "/employees";
    static final String APPROVALS_HOME = "/approvals";

    /**
     * Where the shell lands this caller after login (D-35, W-73 section 3). Decided from the bound tenant and the
     * caller's actions, never from a role name, and only ever a path the visible feed names (or the portal):
     *
     * <ol>
     *   <li>the platform tenant: the platform dashboard (W-73.2), else the tenants screen;
     *   <li>a tenant admin ({@code core.tenant.manage}) whose setup is unfinished: the setup checklist;
     *   <li>a payroll reader ({@code payroll.run.read}, which gates the payroll dashboard): the payroll dashboard;
     *   <li>an employee reader ({@code core.employee.read}, HR) who sees the HRMS dashboard: the HRMS dashboard (D-76);
     *   <li>a team reader ({@code core.employee.read_team}, a manager) who sees the approvals inbox: the inbox (D-76);
     *   <li>an employee reader: the employee list;
     *   <li>anyone else who reads other people: the first screen they can see other than the portal;
     *   <li>everyone else: the portal.
     * </ol>
     *
     * <p>The platform tenant falls back to the first visible path when both its screens are hidden.
     */
    private String homePath(List<NavigationItemResponse> items, Set<String> actions) {
        Set<String> visible = new LinkedHashSet<>();
        collectPaths(items, visible);
        Optional<UUID> tenantId = TenantContext.current();

        if (tenantId.isPresent() && platformTenant.isPlatformTenant(tenantId.get())) {
            if (visible.contains(PLATFORM_HOME)) {
                return PLATFORM_HOME;
            }
            if (visible.contains(PLATFORM_TENANTS)) {
                return PLATFORM_TENANTS;
            }
            return firstLeafPath(items).orElse(PORTAL_HOME);
        }
        if (actions.contains("core.tenant.manage")
                && visible.contains(SETUP_HOME)
                && tenantId.isPresent()
                && setupUnfinished(tenantId.get())) {
            return SETUP_HOME;
        }
        if (actions.contains("payroll.run.read") && visible.contains(PAYROLL_HOME)) {
            return PAYROLL_HOME;
        }
        boolean readsAll = actions.contains("core.employee.read");
        boolean readsTeam = actions.contains("core.employee.read_team");
        if (readsAll && visible.contains(HRMS_HOME)) {
            return HRMS_HOME;
        }
        if (readsTeam && visible.contains(APPROVALS_HOME)) {
            return APPROVALS_HOME;
        }
        if (readsAll && visible.contains(EMPLOYEES_HOME)) {
            return EMPLOYEES_HOME;
        }
        if (readsAll || readsTeam) {
            // Someone who reads other people but sees none of the screens above still has a working screen,
            // which beats the portal - the portal item comes first in the menu (D-75), so it is skipped here.
            return firstLeafPath(items).orElse(PORTAL_HOME);
        }
        return PORTAL_HOME;
    }

    /**
     * True when the checklist has an open step. No checklist service (a unit test) is read as "finished",
     * and so is a checklist that cannot be read: a broken checker must not take the whole menu down.
     */
    private boolean setupUnfinished(UUID tenantId) {
        if (setupChecklist == null) {
            return false;
        }
        try {
            return !setupChecklist.isComplete(tenantId);
        } catch (RuntimeException e) {
            log.warn(
                    "Setup checklist could not be read for the home path: {}",
                    e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * True when the caller has a live employee record in the bound tenant (D-75). No employee service (a unit
     * test) is read as "has one", so the action alone decides. A lookup that throws before it reaches the
     * database - no tenant bound - is read as "has none" and hides the item alone. The read joins this
     * feed's transaction, so a failure inside the database still fails the feed; this guard does not cover it.
     */
    private boolean hasEmployeeRecord() {
        if (employees == null) {
            return true;
        }
        try {
            return employees.currentEmployee().isPresent();
        } catch (RuntimeException e) {
            log.warn(
                    "Employee record could not be read for the portal item: {}",
                    e.getClass().getSimpleName());
            return false;
        }
    }

    private static void collectPaths(List<NavigationItemResponse> items, Set<String> into) {
        for (NavigationItemResponse item : items) {
            if (item.children() != null && !item.children().isEmpty()) {
                collectPaths(item.children(), into);
            } else if (item.path() != null) {
                into.add(item.path());
            }
        }
    }

    /** The first visible screen's path, never the portal's: the portal is every fallback's own answer. */
    private static Optional<String> firstLeafPath(List<NavigationItemResponse> items) {
        for (NavigationItemResponse item : items) {
            if (item.children() != null && !item.children().isEmpty()) {
                Optional<String> child = firstLeafPath(item.children());
                if (child.isPresent()) {
                    return child;
                }
            } else if (item.path() != null && !PORTAL_HOME.equals(item.path())) {
                return Optional.of(item.path());
            }
        }
        return Optional.empty();
    }

    private static final TenantBranding NO_BRANDING = new TenantBranding(null, null, null, null);

    /**
     * The bound tenant's name, tagline and logo link (W-73.1). Row-level security lets a tenant read its own
     * row, so while staff act inside a customer tenant this is the customer's branding. Without a profile
     * service - a unit test - the name alone, from the same row; every field null when nothing can be read.
     */
    private TenantBranding tenantBranding() {
        Optional<UUID> tenantId = TenantContext.current();
        if (tenantId.isEmpty()) {
            return NO_BRANDING;
        }
        if (tenantProfiles != null) {
            return tenantProfiles.branding().orElse(NO_BRANDING);
        }
        if (jdbcTemplate == null) {
            return NO_BRANDING;
        }
        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM core.tenant WHERE tenant_id = ?", String.class, tenantId.get());
        return names.isEmpty() ? NO_BRANDING : new TenantBranding(names.get(0), null, null, null);
    }

    /**
     * Screens that make sense only inside a customer tenant (D-33). The platform tenant keeps the actions
     * behind them ({@code core.tenant.read}, {@code core.user.manage}) for the tenant and user endpoints,
     * so the action filter alone would show them; the platform's menu is Tenants and Audit. The self-service portal
     * (D-75) is here too: platform staff have no employee record, so it would only ever be empty.
     */
    static final Set<String> CUSTOMER_ONLY_KEYS =
            Set.of("core.me", "core.setup", "core.users", "core.settings.company");

    /**
     * The self-service portal (D-75): shown to anyone with a linked employee record, whatever their role. Its action
     * ({@code core.employee.read_own}) is on every seeded role that may be on the payroll (V171), so the record is
     * what tells a payroll officer who is staff from one who is not.
     */
    static final String PORTAL_KEY = "core.me";

    private Optional<NavigationItemResponse> filterItem(
            NavigationCatalogue.ItemDefinition itemDef, Set<String> actions, boolean platformTenantBound) {
        if (platformTenantBound && CUSTOMER_ONLY_KEYS.contains(itemDef.key())) {
            return Optional.empty();
        }

        // Record check: the portal shows only to a caller with an employee record of their own (D-75)
        if (PORTAL_KEY.equals(itemDef.key()) && !hasEmployeeRecord()) {
            return Optional.empty();
        }

        // Module check: core items (null module) pass; otherwise tenant must hold the module
        if (itemDef.requiredModule() != null && !entitlementService.holds(itemDef.requiredModule())) {
            return Optional.empty();
        }

        // Action check: the user must hold the required action or one of its alternatives (D-76)
        if (!itemDef.admits(actions)) {
            return Optional.empty();
        }

        // Parent check: if the item has children defined, only keep visible children
        if (itemDef.hasChildren()) {
            List<NavigationItemResponse> visibleChildren = new ArrayList<>();
            for (NavigationCatalogue.ItemDefinition childDef : itemDef.children()) {
                filterItem(childDef, actions, platformTenantBound).ifPresent(visibleChildren::add);
            }
            // A parent with no visible children is itself hidden (W-12.3 §7)
            if (visibleChildren.isEmpty()) {
                return Optional.empty();
            }
            // A group's path is its first visible child's (D-34): the catalogue's is the first child overall,
            // which this caller may not be able to open.
            return Optional.of(new NavigationItemResponse(
                    itemDef.key(), itemDef.labelKey(), visibleChildren.get(0).path(), List.copyOf(visibleChildren)));
        }

        return Optional.of(new NavigationItemResponse(itemDef.key(), itemDef.labelKey(), itemDef.path()));
    }
}
