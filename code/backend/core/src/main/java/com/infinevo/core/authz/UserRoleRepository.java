package com.infinevo.core.authz;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes {@code core.user_role} (W-11.1). Every method names the tenant — see
 * {@link RoleRepository}.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    /** The user's current grants. Served by {@code idx_user_role_tenant_user_role}. */
    List<UserRole> findByTenantIdAndUserAccountId(UUID tenantId, UUID userAccountId);

    /** How many users hold this role — the check that refuses a delete. Served by {@code idx_user_role_tenant_role}. */
    long countByTenantIdAndRoleId(UUID tenantId, UUID roleId);
}
