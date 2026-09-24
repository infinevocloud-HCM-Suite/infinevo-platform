package com.infinevo.core.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One action one role holds (W-11.1) — {@code core.role_action},
 * {@code migration/src/main/resources/db/migration/core/V022__role_action.sql}.
 *
 * <p>Plain ids rather than associations. The foreign keys are composite — {@code (tenant_id, role_id)}
 * to {@code core.role} and {@code action_code} to {@code reference.action} — so a link to another
 * tenant's role is impossible at the database whatever this class says; mapping them as
 * {@code @ManyToOne} would add lazy-loading behaviour and nothing the service needs.
 *
 * <p>The merge of both frozen products' mappings: HRMS {@code user_action_mapping} and Payroll
 * {@code organization_role_action} ({@code 02-data-model.md:59}).
 */
@Entity
@Table(
        name = "role_action",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_role_action_tenant_role_action",
                    columnList = "tenant_id, role_id, action_code",
                    unique = true)
        })
public class RoleAction extends AuthzRow {

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "action_code", nullable = false, length = 64, updatable = false)
    private String actionCode;

    protected RoleAction() {}

    RoleAction(UUID tenantId, UUID roleId, String actionCode, String actor) {
        super(tenantId, actor);
        this.roleId = roleId;
        this.actionCode = actionCode;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public String getActionCode() {
        return actionCode;
    }
}
