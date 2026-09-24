package com.infinevo.core.authz;

import java.util.List;

/**
 * {@code PUT /api/v1/roles/{id}} (W-11.1, spec section 4) — the new name and the <em>complete</em> new
 * action set.
 *
 * <p>{@code actionCodes} is required rather than "null means unchanged": a PUT replaces, and a client
 * that forgot the field would otherwise be told it succeeded while nothing changed — or, worse, read
 * a missing list as empty and strip the role bare. An empty list is accepted and means "no actions".
 */
public record RoleUpdateRequest(String name, List<String> actionCodes) {}
