package com.infinevo.core.navigation;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import java.util.Set;

/**
 * Response payload for {@code GET /api/v1/navigation} (W-12.3).
 *
 * @param items ordered navigation items visible to the current caller
 * @param actions caller's action codes in the bound tenant, for client-side button gating
 * @param modules the modules the bound tenant holds - a fact about the tenant, not filtered by the
 *     caller's permissions, so a screen shared by both modules can tell which half applies
 * @param tenantName the bound tenant's name, for the shell to show; null when it cannot be read
 * @param homePath where the shell lands this caller after login (D-35): a path the feed names, or {@code /me}
 * @param tenantLogoUrl a signed link to the tenant's logo (W-73.1), valid for a day; null when the tenant has
 *     no logo, and the header then shows the company's initials
 * @param tagline the tenant's tagline (W-73.1); null when none is set, and the header then shows none
 */
public record NavigationResponse(
        List<NavigationItemResponse> items,
        Set<String> actions,
        Set<PlatformModule> modules,
        String tenantName,
        String homePath,
        String tenantLogoUrl,
        String tagline) {

    /** The W-12.3 shape, with no branding — for callers and tests that predate W-73.1. */
    public NavigationResponse(
            List<NavigationItemResponse> items,
            Set<String> actions,
            Set<PlatformModule> modules,
            String tenantName,
            String homePath) {
        this(items, actions, modules, tenantName, homePath, null, null);
    }
}
