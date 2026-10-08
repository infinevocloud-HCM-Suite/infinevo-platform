package com.infinevo.shared.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code core.user_account} (W-10).
 *
 * <p>Every method is keyed on {@code tenantId} first, even though row-level security would hide
 * the other tenants' rows anyway. Two reasons: the unique index is {@code (tenant_id,
 * keycloak_user_id)} ({@code V009__user_account.sql:25}), so the query matches the index; and a
 * repository whose signatures name the tenant cannot be reused by accident from a code path where
 * none is bound.
 */
@Transactional(readOnly = true)
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** The one profile row for this user in this tenant, if it has been synced yet. */
    Optional<UserAccount> findByTenantIdAndKeycloakUserId(UUID tenantId, UUID keycloakUserId);

    /**
     * The profile rows for these users in this tenant, in one statement — for a list that names who
     * did something by subject, such as the uploader of each document (W-73.5). A subject with no
     * synced row is simply absent.
     */
    List<UserAccount> findByTenantIdAndKeycloakUserIdIn(UUID tenantId, Collection<UUID> keycloakUserIds);
}
