package com.infinevo.shared.authz;

import java.util.Set;
import java.util.UUID;

/**
 * Where the role codes come from (W-73.1) — the port beside {@link ActionSource}, implemented in
 * {@code core} by the same service that reads actions, so {@code GET /api/v1/me} names the roles the
 * navigation feed's actions are granted through.
 *
 * <p>Not cached: the header asks once per page load, and a role change should show on the next load.
 */
public interface RoleSource {

    /**
     * The code of every role the user holds in the tenant — {@code hr}, {@code payroll-officer} ...
     *
     * <p>Empty, never {@code null}, for a user who holds none.
     *
     * @param tenantId the tenant the request is bound to
     * @param userAccountId {@code core.user_account.id} — the per-tenant profile row, not the Keycloak
     *     subject
     */
    Set<String> roleCodesOf(UUID tenantId, UUID userAccountId);
}
