package com.infinevo.core.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One role in one tenant (W-11.1) — {@code core.role},
 * {@code migration/src/main/resources/db/migration/core/V021__role.sql}.
 *
 * <p><strong>Tenant-scoped, which is the point.</strong> HRMS roles are global — its role entity has
 * no organisation column
 * ({@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/useraccess/Role.java:21-32}) —
 * so a role defined for one employer is a role for all of them (spec section 1). This follows
 * Payroll's organisation-scoped shape instead.
 *
 * <p>{@link #isSystem()} marks the seven roles {@code core.seed_system_roles} ({@code V022}) creates
 * for every tenant. The application never creates one — the column is not insertable as true from
 * here — and {@code RoleServiceImpl} refuses to edit or delete them (spec section 7).
 */
@Entity
@Table(
        name = "role",
        schema = "core",
        indexes = {
            @Index(name = "idx_role_tenant_code", columnList = "tenant_id, code", unique = true),
            @Index(name = "idx_role_tenant_id", columnList = "tenant_id, id", unique = true)
        })
public class Role extends AuthzRow {

    /** Unique within the tenant, never globally, and fixed once created. */
    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    protected Role() {}

    /** A tenant's own role. Never a system role — those come only from the migration's seed. */
    Role(UUID tenantId, String code, String name, String actor) {
        super(tenantId, actor);
        this.code = code;
        this.name = name;
        this.system = false;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isSystem() {
        return system;
    }

    /** Renames the role. Package-private: only {@code RoleServiceImpl} calls it, after validation. */
    void rename(String name, String actor) {
        this.name = name;
        touch(actor);
    }
}
