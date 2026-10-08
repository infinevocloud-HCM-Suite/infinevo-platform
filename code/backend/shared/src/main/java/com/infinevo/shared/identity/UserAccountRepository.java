package com.infinevo.shared.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Whether the user dismissed the welcome page (W-73.8); null when there is no such row.
     *
     * <p>{@code welcome_seen_at} ({@code V165}) is read and written here by native SQL and is deliberately
     * not a field of {@link UserAccount}: the sync filter loads that entity on every authenticated request, so
     * mapping the column would make every integration-test schema that applies {@code V009} alone fail.
     * Only {@code GET} and {@code PUT /api/v1/me} need it.
     */
    @Query(
            value =
                    "SELECT welcome_seen_at IS NOT NULL FROM core.user_account WHERE tenant_id = :tenantId AND id = :id",
            nativeQuery = true)
    Boolean findWelcomeSeen(@Param("tenantId") UUID tenantId, @Param("id") UUID userAccountId);

    /**
     * Stamps {@code welcome_seen_at} the first time only; a second call changes nothing (W-73.8).
     *
     * @return rows written: 1 the first time, 0 afterwards or when the row is not in the tenant
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    UPDATE core.user_account
                       SET welcome_seen_at = now(), updated_at = now(), updated_by = :actor
                     WHERE tenant_id = :tenantId AND id = :id AND welcome_seen_at IS NULL
                    """,
            nativeQuery = true)
    int markWelcomeSeen(
            @Param("tenantId") UUID tenantId, @Param("id") UUID userAccountId, @Param("actor") String actor);
}
