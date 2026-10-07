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
 */
public record NavigationResponse(
        List<NavigationItemResponse> items, Set<String> actions, Set<PlatformModule> modules, String tenantName) {}
