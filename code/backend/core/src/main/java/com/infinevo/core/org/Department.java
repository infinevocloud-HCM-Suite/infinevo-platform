package com.infinevo.core.org;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One department in one tenant (W-14.1) — {@code core.department},
 * {@code migration/src/main/resources/db/migration/core/V011__department.sql}.
 *
 * <p>The schema is named on the table: {@code code/backend/app/src/main/resources/application.yml}
 * sets no {@code default_schema}, deliberately, so every entity declares its own.
 *
 * <p>This is the shape HRMS never had. It holds the department as a plain string on the employment
 * record ({@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Work.java},
 * field {@code department}) — no lookup table, no foreign key, no validation, so "Finance",
 * "finance" and "Fin." are three departments to every query and one to every human. Payroll models
 * it properly and this follows Payroll.
 *
 * <p><strong>Flat, with no {@code parent_id}</strong> — founder decision 2, spec section 13. Neither
 * frozen product nests departments. A nullable parent column is cheap to add later and expensive to
 * un-model once screens assume a tree.
 *
 * <p>Converting the HRMS free-text values is <strong>not</strong> done here. That is W-67, and it
 * needs production data this repository does not hold — spec section 2 (Out of scope) and section 9,
 * which names "someone attempts the conversion here" as the likeliest way this ticket goes wrong.
 */
@Entity
@Table(
        name = "department",
        schema = "core",
        indexes = {
            @Index(name = "idx_department_tenant_code", columnList = "tenant_id, code", unique = true),
            @Index(name = "idx_department_tenant_is_active", columnList = "tenant_id, is_active")
        })
public class Department extends OrgMaster {

    protected Department() {}

    Department(UUID tenantId, String actor) {
        super(tenantId, actor);
    }
}
