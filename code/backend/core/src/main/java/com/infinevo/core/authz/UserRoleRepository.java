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

    /** The users holding this role in this tenant. */
    List<UserRole> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    /** How many users hold this role — the check that refuses a delete. Served by {@code idx_user_role_tenant_role}. */
    long countByTenantIdAndRoleId(UUID tenantId, UUID roleId);

    /**
     * The codes of every role a user holds in a tenant — what {@code GET /api/v1/me} shows as chips (W-73.1).
     * Both sides pinned to the tenant, as {@code RoleActionRepository.findActionCodesOfUser} does.
     */
    @Query(
            """
            select distinct r.code
              from UserRole ur, Role r
             where ur.tenantId = :tenantId
               and ur.userAccountId = :userAccountId
               and r.tenantId = ur.tenantId
               and r.id = ur.roleId
            """)
    Set<String> findRoleCodesOfUser(@Param("tenantId") UUID tenantId, @Param("userAccountId") UUID userAccountId);
}
