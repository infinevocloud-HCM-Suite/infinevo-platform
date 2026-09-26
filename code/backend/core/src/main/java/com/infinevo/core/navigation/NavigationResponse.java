package com.infinevo.core.navigation;

import java.util.List;
import java.util.Set;

/**
 * Response payload for {@code GET /api/v1/navigation} (W-12.3).
 *
 * @param items ordered navigation items visible to the current caller
 * @param actions caller's action codes in the bound tenant, for client-side button gating
 */
public record NavigationResponse(List<NavigationItemResponse> items, Set<String> actions) {}
