package com.infinevo.core.authz;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes {@code core.user_role} (W-11.1). Every method names the tenant — see
 * {@link RoleRepository}.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    /** The user's current grants. Served by {@code idx_user_role_tenant_user_role}. */
    List<UserRole> findByTenantIdAndUserAccountId(UUID tenantId, UUID userAccountId);

    /** Every grant in the tenant — the Users & access list reads them in one query (W-73.4). */
    List<UserRole> findByTenantId(UUID tenantId);

    /** The users holding this role in this tenant. */
    List<UserRole> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    /** How many users hold this role — the check that refuses a delete. Served by {@code idx_user_role_tenant_role}. */
    long countByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    /**
     * The codes of every role a user holds in a tenant — what {@code GET /api/v1/me} shows as chips (W-73.1).
     * Both sides pinned to the tenant, as {@code RoleActionRepository.findActionCodesOfUser} does. A disabled
     * account holds no role here either (W-73.4).
     */
    @Query(
            """
            select distinct r.code
              from UserRole ur, Role r, UserAccount ua
             where ur.tenantId = :tenantId
               and ur.userAccountId = :userAccountId
               and r.tenantId = ur.tenantId
               and r.id = ur.roleId
               and ua.tenantId = ur.tenantId
               and ua.id = ur.userAccountId
               and ua.status = 'ACTIVE'
            """)
    Set<String> findRoleCodesOfUser(@Param("tenantId") UUID tenantId, @Param("userAccountId") UUID userAccountId);

    /**
     * How many active accounts hold this role in this tenant — the "last tenant-admin" guard (W-73.4). A
     * disabled holder ({@code core.user_account.status} not {@code ACTIVE}) cannot sign in, so does not count.
     */
    @Query(
            """
            select count(distinct ur.userAccountId)
              from UserRole ur, UserAccount ua
             where ur.tenantId = :tenantId
               and ur.roleId = :roleId
               and ua.tenantId = ur.tenantId
               and ua.id = ur.userAccountId
               and ua.status = 'ACTIVE'
            """)
    long countActiveHolders(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

    /**
     * Serialises the "last active tenant-admin" guard within one tenant (W-73.4 merge review): a
     * transaction-level advisory lock, released at commit or rollback, taken before the count in both the
     * role-removal and the disable path — so two admins removing or disabling each other at once cannot both
     * pass the check and leave the tenant with none.
     */
    @Query(
            value =
                    "select 1 from (select pg_advisory_xact_lock(hashtextextended('core.tenant-admin-guard:' || cast(:tenantId as text), 0))) l",
            nativeQuery = true)
    Integer lockTenantAdminGuard(@Param("tenantId") UUID tenantId);
}
