package com.infinevo.shared.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes {@code core.user_account} (W-10).
 *
 * <p>Every method is keyed on {@code tenantId} first, even though row-level security would hide
 * the other tenants' rows anyway. Two reasons: the unique index is {@code (tenant_id,
 * keycloak_user_id)} ({@code V009__user_account.sql:25}), so the query matches the index; and a
 * repository whose signatures name the tenant cannot be reused by accident from a code path where
 * none is bound.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** The one profile row for this user in this tenant, if it has been synced yet. */
    Optional<UserAccount> findByTenantIdAndKeycloakUserId(UUID tenantId, UUID keycloakUserId);
}
