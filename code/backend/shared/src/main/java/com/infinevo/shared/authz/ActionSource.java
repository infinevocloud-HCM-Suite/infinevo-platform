package com.infinevo.shared.authz;

import java.util.Set;
import java.util.UUID;

/**
 * Where {@link PermissionService} loads a user's actions from on a cache miss (W-11.2, spec section
 * 13 decision 3).
 *
 * <p>A port, not an implementation. The role and grant tables are {@code W-11.1}'s and live in
 * {@code core}, and {@code shared} may not depend on {@code core}; so {@code shared} declares what it
 * needs and {@code core}'s {@code PermissionReadServiceImpl} provides it. Exactly one bean is
 * expected. With none — a module that does not include {@code core}, or a misconfigured context —
 * every check is refused rather than skipped.
 */
public interface ActionSource {

    /**
     * Every action code the user holds in the tenant, through every role granted to them.
     *
     * <p>Empty, never {@code null}, for a user who holds nothing. The result is cached for every
     * replica under the tenant's permission version, so it must be the stored truth and nothing
     * derived from the request.
     *
     * @param tenantId the tenant the request is bound to
     * @param userAccountId {@code core.user_account.id} — the per-tenant profile row, not the Keycloak
     *     subject
     */
    Set<String> actionsOf(UUID tenantId, UUID userAccountId);
}
