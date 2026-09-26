package com.infinevo.core.navigation;

import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    @Autowired
    public NavigationService(EntitlementService entitlementService, PermissionService permissionService) {
        this(entitlementService, permissionService, NavigationCatalogue.DEFAULT_ITEMS);
    }

    public NavigationService(
            EntitlementService entitlementService,
            PermissionService permissionService,
            List<NavigationCatalogue.ItemDefinition> catalogueItems) {
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.catalogueItems = Objects.requireNonNull(catalogueItems, "catalogueItems must not be null");
    }

    /**
     * Derives the navigation response containing visible items and held action codes.
     */
    public NavigationResponse navigation() {
        Set<String> actions = permissionService.currentActions();
        List<NavigationItemResponse> visibleItems = new ArrayList<>();

        for (NavigationCatalogue.ItemDefinition itemDef : catalogueItems) {
            filterItem(itemDef, actions).ifPresent(visibleItems::add);
        }

        return new NavigationResponse(List.copyOf(visibleItems), actions);
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
