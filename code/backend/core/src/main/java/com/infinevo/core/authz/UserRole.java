package com.infinevo.core.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One role granted to one user in one tenant (W-11.1) — {@code core.user_role},
 * {@code migration/src/main/resources/db/migration/core/V023__user_role.sql}.
 *
 * <p>The role side is protected at the database by the composite key {@code (tenant_id, role_id)}.
 * The user side is not: {@code user_account_id} references {@code core.user_account(id)} alone,
 * because {@code V009} declares no {@code (tenant_id, id)} key to point at. So
 * {@code RoleServiceImpl.replaceUserRoles} checks the account is visible in the bound tenant before
 * granting anything — that check is the boundary on this side.
 */
@Entity
@Table(
        name = "user_role",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_user_role_tenant_user_role",
                    columnList = "tenant_id, user_account_id, role_id",
                    unique = true),
            @Index(name = "idx_user_role_tenant_role", columnList = "tenant_id, role_id")
        })
public class UserRole extends AuthzRow {

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private UUID userAccountId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    protected UserRole() {}

    UserRole(UUID tenantId, UUID userAccountId, UUID roleId, String actor) {
        super(tenantId, actor);
        this.userAccountId = userAccountId;
        this.roleId = roleId;
    }

    public UUID getUserAccountId() {
        return userAccountId;
    }

    public UUID getRoleId() {
        return roleId;
    }
}
