package com.infinevo.core.authz;

import java.util.List;
import java.util.UUID;

/**
 * {@code PUT /api/v1/users/{id}/roles} (W-11.1, spec section 4) — the user's complete role set in the
 * bound tenant. Required; an empty list revokes every role.
 */
public record UserRolesRequest(List<UUID> roleIds) {}
