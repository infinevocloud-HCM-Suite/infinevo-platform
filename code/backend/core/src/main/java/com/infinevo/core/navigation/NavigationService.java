package com.infinevo.core.navigation;

import com.infinevo.core.setup.SetupChecklistService;
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
 * <p>Filters items by module entitlement (W-12.2) and by action (W-11.2).
 * Returns the filtered menu alongside the caller's full action-code set, and the caller's home page (D-35).
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

    @Autowired
    public NavigationService(
            EntitlementService entitlementService,
            PermissionService permissionService,
            ObjectProvider<NavigationContributor> contributors,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<SetupChecklistService> setupChecklist,
            ObjectProvider<PlatformTenant> platformTenant) {
        this(
                entitlementService,
                permissionService,
                NavigationCatalogue.withContributed(contributors.orderedStream().toList()),
                jdbcTemplate,
                setupChecklist.getIfAvailable(),
                platformTenant.getIfAvailable(PlatformTenant::new));
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
        this.jdbcTemplate = jdbcTemplate;
        this.setupChecklist = setupChecklist;
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
        return new NavigationResponse(items, actions, modules, tenantName(), homePath(items, actions));
    }

    /** The portal's own page: mounted for every signed-in user whatever the feed says, so always a safe home. */
    static final String PORTAL_HOME = "/me";

    static final String PLATFORM_HOME = "/admin/tenants";
    static final String SETUP_HOME = "/setup";
    static final String PAYROLL_HOME = "/payroll/dashboard";
    static final String HRMS_HOME = "/hrms/dashboard";
    static final String EMPLOYEES_HOME = "/employees";

    /**
     * Where the shell lands this caller after login (D-35, W-73 section 3). Decided from the bound tenant and the
     * caller's actions, never from a role name, and only ever a path the visible feed names (or the portal):
     *
     * <ol>
     *   <li>the platform tenant: the tenants screen;
     *   <li>a tenant admin ({@code core.tenant.manage}) whose setup is unfinished: the setup checklist;
     *   <li>a payroll reader ({@code payroll.run.read}, which gates the payroll dashboard): the payroll dashboard;
     *   <li>someone who reads other people ({@code core.employee.read} or {@code core.employee.read_team}) and
     *       sees the HRMS dashboard: the HRMS dashboard;
     *   <li>an employee reader ({@code core.employee.read}): the employee list;
     *   <li>everyone else: the portal.
     * </ol>
     *
     * <p>The platform tenant falls back to the first visible path when its tenants screen is hidden.
     */
    private String homePath(List<NavigationItemResponse> items, Set<String> actions) {
        Set<String> visible = new LinkedHashSet<>();
        collectPaths(items, visible);
        Optional<UUID> tenantId = TenantContext.current();

        if (tenantId.isPresent() && platformTenant.isPlatformTenant(tenantId.get())) {
            if (visible.contains(PLATFORM_HOME)) {
                return PLATFORM_HOME;
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
        boolean readsOthers = actions.contains("core.employee.read") || actions.contains("core.employee.read_team");
        if (readsOthers && visible.contains(HRMS_HOME)) {
            return HRMS_HOME;
        }
        if (actions.contains("core.employee.read") && visible.contains(EMPLOYEES_HOME)) {
            return EMPLOYEES_HOME;
        }
        if (readsOthers) {
            // A manager with no dashboard in the feed (the seeded manager holds no hrms.project.read_own)
            // still has a working screen - the approvals inbox - which beats the portal.
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

    private static void collectPaths(List<NavigationItemResponse> items, Set<String> into) {
        for (NavigationItemResponse item : items) {
            if (item.children() != null && !item.children().isEmpty()) {
                collectPaths(item.children(), into);
            } else if (item.path() != null) {
                into.add(item.path());
            }
        }
    }

    private static Optional<String> firstLeafPath(List<NavigationItemResponse> items) {
        for (NavigationItemResponse item : items) {
            if (item.children() != null && !item.children().isEmpty()) {
                Optional<String> child = firstLeafPath(item.children());
                if (child.isPresent()) {
                    return child;
                }
            } else if (item.path() != null) {
                return Optional.of(item.path());
            }
        }
        return Optional.empty();
    }

    /**
     * The bound tenant's name. Row-level security lets a tenant read its own row, so while staff
     * act inside a customer tenant this is the customer's name.
     */
    private String tenantName() {
        Optional<UUID> tenantId = TenantContext.current();
        if (jdbcTemplate == null || tenantId.isEmpty()) {
            return null;
        }
        List<String> names = jdbcTemplate.queryForList(
                "SELECT name FROM core.tenant WHERE tenant_id = ?", String.class, tenantId.get());
        return names.isEmpty() ? null : names.get(0);
    }

    /**
     * Screens that make sense only inside a customer tenant (D-33). The platform tenant keeps the actions
     * behind them ({@code core.tenant.read}, {@code core.user.manage}) for the tenant and user endpoints,
     * so the action filter alone would show them; the platform's menu is Tenants and Audit.
     */
    static final Set<String> CUSTOMER_ONLY_KEYS = Set.of("core.setup", "core.invitations.users");

    private Optional<NavigationItemResponse> filterItem(
            NavigationCatalogue.ItemDefinition itemDef, Set<String> actions, boolean platformTenantBound) {
        if (platformTenantBound && CUSTOMER_ONLY_KEYS.contains(itemDef.key())) {
            return Optional.empty();
        }

        // Module check: core items (null module) pass; otherwise tenant must hold the module
        if (itemDef.requiredModule() != null && !entitlementService.holds(itemDef.requiredModule())) {
            return Optional.empty();
        }

        // Action check: if an action is required, the user must hold it
        if (itemDef.requiredAction() != null && !actions.contains(itemDef.requiredAction())) {
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
