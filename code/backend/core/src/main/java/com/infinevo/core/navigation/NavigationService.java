package com.infinevo.core.navigation;

import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives the navigation feed for the current caller and bound tenant (W-12.3, spec section 4).
 *
 * <p>Filters items by module entitlement (W-12.2) and by action (W-11.2).
 * Returns the filtered menu alongside the caller's full action-code set.
 */
@Service
public class NavigationService {

    private final EntitlementService entitlementService;
    private final PermissionService permissionService;
    private final List<NavigationCatalogue.ItemDefinition> catalogueItems;
    /** Reads the bound tenant's name; null in unit tests, which then get a null name. */
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public NavigationService(
            EntitlementService entitlementService,
            PermissionService permissionService,
            ObjectProvider<NavigationContributor> contributors,
            JdbcTemplate jdbcTemplate) {
        this(
                entitlementService,
                permissionService,
                NavigationCatalogue.withContributed(contributors.orderedStream().toList()),
                jdbcTemplate);
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
        this.jdbcTemplate = jdbcTemplate;
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

        for (NavigationCatalogue.ItemDefinition itemDef : catalogueItems) {
            filterItem(itemDef, actions).ifPresent(visibleItems::add);
        }

        Set<PlatformModule> modules = EnumSet.noneOf(PlatformModule.class);
        for (PlatformModule module : PlatformModule.values()) {
            if (entitlementService.holds(module)) {
                modules.add(module);
            }
        }

        return new NavigationResponse(List.copyOf(visibleItems), actions, modules, tenantName());
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

    private Optional<NavigationItemResponse> filterItem(
            NavigationCatalogue.ItemDefinition itemDef, Set<String> actions) {
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
                filterItem(childDef, actions).ifPresent(visibleChildren::add);
            }
            // A parent with no visible children is itself hidden (W-12.3 §7)
            if (visibleChildren.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new NavigationItemResponse(
                    itemDef.key(), itemDef.labelKey(), itemDef.path(), List.copyOf(visibleChildren)));
        }

        return Optional.of(new NavigationItemResponse(itemDef.key(), itemDef.labelKey(), itemDef.path()));
    }
}
