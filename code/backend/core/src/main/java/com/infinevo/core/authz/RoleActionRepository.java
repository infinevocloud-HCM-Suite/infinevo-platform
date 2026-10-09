package com.infinevo.core.authz;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes {@code core.role_action} (W-11.1). Every method names the tenant — see
 * {@link RoleRepository}.
 */
public interface RoleActionRepository extends JpaRepository<RoleAction, UUID> {

    /** What one role holds. Served by {@code idx_role_action_tenant_role_action}'s leading prefix. */
    List<RoleAction> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    /** What several roles hold, in one query — so listing roles is two queries, not one per role. */
    List<RoleAction> findByTenantIdAndRoleIdIn(UUID tenantId, Collection<UUID> roleIds);

    /**
     * Every action code a user holds in a tenant, through every role granted to them — the one query
     * behind {@link PermissionReadService#actionsOf}.
     *
     * <p>Both sides are pinned to the tenant, not only the grant: a {@code role_action} row can only
     * point at a role in its own tenant ({@code V022}'s composite key), but stating it here as well
     * keeps the join on the {@code (tenant_id, role_id, ...)} index and makes the query correct on its
     * own terms rather than by the schema's courtesy.
     *
     * <p>A disabled account holds nothing (W-73.4): its roles stay on record, so enabling it again restores
     * them, but no action is granted while {@code core.user_account.status} is not {@code ACTIVE}.
     */
    @Query(
            """
            select distinct ra.actionCode
              from UserRole ur, RoleAction ra, UserAccount ua
             where ur.tenantId = :tenantId
               and ur.userAccountId = :userAccountId
               and ra.tenantId = ur.tenantId
               and ra.roleId = ur.roleId
               and ua.tenantId = ur.tenantId
               and ua.id = ur.userAccountId
               and ua.status = 'ACTIVE'
            """)
    Set<String> findActionCodesOfUser(@Param("tenantId") UUID tenantId, @Param("userAccountId") UUID userAccountId);
}
