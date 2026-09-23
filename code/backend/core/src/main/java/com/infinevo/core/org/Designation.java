package com.infinevo.core.org;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One job designation in one tenant (W-14.1) — {@code core.designation},
 * {@code migration/src/main/resources/db/migration/core/V012__designation.sql}.
 *
 * <p>Column for column the same as {@link Department}, and that is deliberate rather than
 * accidental: they are two lists of the same shape, not one list with a type column. A single table
 * with a discriminator would share a unique index across both, so a tenant could not use the code
 * {@code FIN} for a department and a designation, and every query would carry a filter that exists
 * only to undo the merge.
 *
 * <p><strong>No level and no grade</strong> — founder decision 1, spec section 13. Payroll's
 * designation carries none, and HRMS keeps {@code payGrade} on the employment record, where W-13.2
 * puts it. A designation is an org master, not a compensation band. Adding a level later is easy;
 * removing one is not.
 *
 * <p>The HRMS free-text {@code jobTitle} values are W-67's problem, not this ticket's — spec section
 * 2 (Out of scope).
 */
@Entity
@Table(
        name = "designation",
        schema = "core",
        indexes = {
            @Index(name = "idx_designation_tenant_code", columnList = "tenant_id, code", unique = true),
            @Index(name = "idx_designation_tenant_is_active", columnList = "tenant_id, is_active")
        })
public class Designation extends OrgMaster {

    protected Designation() {}

    Designation(UUID tenantId, String actor) {
        super(tenantId, actor);
    }
}
