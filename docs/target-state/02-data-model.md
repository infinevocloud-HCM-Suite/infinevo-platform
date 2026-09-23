# 02 — Data Model

> Target state. Where data lives. Names tables, never behaviour — behaviour is `01`.
> Today: 2 MySQL databases, 131 tables, employee and leave duplicated.
> Target: 1 Postgres database, 4 schemas, ~130 tables, nothing duplicated.

---

## 1. Design principles

| # | Principle | Detail |
|---|---|---|
| 1 | **One database** | A single Postgres database. Keycloak keeps its own, separate. |
| 2 | **Four application schemas** | `core`, `hrms`, `payroll`, `reference`. The module boundary is visible in the schema name. A fifth schema, `migration`, holds Flyway's own bookkeeping and never application data (`D-45`) — see §5. |
| 3 | **Tenant column everywhere** | Every table in `core`, `hrms` and `payroll` carries `tenant_id`. No exceptions. |
| 4 | **`reference` is the one exception** | Shared national data. **No tenant column, by design.** Isolating the exception into its own schema is what makes rule 3 auditable: `SELECT` any table outside `reference` without `tenant_id` and it is a bug. |
| 5 | **Row-level security** | Postgres policies on `tenant_id`. Isolation is enforced by the database, not by application care. |
| 6 | **Dependency direction** | `hrms` → `core`. `payroll` → `core`. Any → `reference`. **Never** `hrms` ↔ `payroll`. **Never** `core` → module. |
| 7 | **Naming** | `lower_snake_case` throughout. Ends today's mix of `camelCase` (`workLocations`, `paySchedule`, `FBP`) and snake_case. |
| 8 | **Keys** | One surrogate primary key per row. The current dual-identifier pattern (DB id + random 10-digit business id) is formalised into a single `public_id` column where an external identifier is genuinely needed, and dropped elsewhere. |
| 9 | **Migrations** | Flyway. One numbered script set covering all four schemas, applied together. `ddl-auto` is disabled permanently. |
| 10 | **Money** | `NUMERIC(19,4)` for monetary values, `NUMERIC(10,2)` for leave days. Never float. See `docs/CONVENTIONS.md`. |
| 11 | **Soft delete** | Where it exists today it is kept, but standardised on one column name and always part of the index. |
| 12 | **Timestamps** | `created_at`, `created_by`, `updated_at`, `updated_by` on every table. Feeds `CORE-14`. |

### The `reference` schema exception, justified

Tax slabs, house-rent rules and surcharge rates are **national law**, identical for every
customer. Today each tenant carries its own copy, so an annual tax change means updating
every tenant. Making them shared reduces that to one update.

Genuine per-tenant deviation already exists in the code (a professional tax override table
and its history). That pattern generalises: **shared base in `reference`, per-tenant
overrides in `payroll`**. Exceptions stay visible as exceptions instead of hiding among
ninety identical copies. (`D-08`)

---

## 2. `core` schema — 46 tables

Present for every tenant.

### Tenant & subscription (4)
| Table | Notes |
|---|---|
| `tenant` | From Payroll `organization` |
| `tenant_setup_step` | From `orgSetupSteps`. Now module-aware (`CORE-17`) |
| `subscription` | **New.** Carries the status field that is the payment seam (`D-12`) |
| `subscription_module` | **New.** One row per granted module |

### Identity & access (8)
| Table | Notes |
|---|---|
| `user_account` | Merge of HRMS `ourusers` and Payroll `companyUser`. Keycloak is the authority; this is the local profile |
| `user_tenant` | From `organizationUserMapping`. A user may belong to more than one tenant |
| `user_role` | From `organizationUserRoleMapping` |
| `role` | Merge of HRMS `role` and Payroll `organizationRole` |
| `action` | Merge of both `action` tables |
| `role_action` | Merge of `organization_role_action` and `user_action_mapping` |
| `user_invitation` | From `userInvitations` |
| `employee_invitation` | From `employeeInvitation` |

> HRMS `password_reset_token` is **retired** — Keycloak owns password reset.

### Employee master (6)
| Table | Merges |
|---|---|
| `employee` | HRMS `employee` + Payroll `employee`. **The highest-risk merge in the project** |
| `employee_personal` | HRMS `personal` + Payroll `employee_personal_detail` (incl. the embedded residential address) |
| `employee_contact` | HRMS `contact` |
| `employee_identification` | HRMS `identification` |
| `employee_employment` | HRMS `work`, plus `report`'s free-text `note`. **`report`'s manager and approver columns do not come here** — they become `core.reporting_line` (`CORE-06`) |
| `employee_bank` | Payroll `employee_bank_detail` |

### Org structure (4)
| Table | Notes |
|---|---|
| `department` | Payroll. HRMS free-text values converted to rows |
| `designation` | Payroll. Same conversion |
| `work_location` | Payroll `workLocations` |
| `reporting_line` | **New (`CORE-06`).** Neither product has this. Required to route leave approvals |

### Leave (8)
| Table | Origin |
|---|---|
| `leave_type` | Merge of HRMS `leave_types` and Payroll `leave_type` |
| `leave_policy` | Payroll `preferences` + accrual rules |
| `leave_allocation` | Payroll `employee_leave_allocation` + HRMS `leave_balances` / `employee_leave_balances` |
| `leave_request` | HRMS `leave_requests` (the live one — see §7) |
| `leave_request_document` | HRMS `leave_documents` |
| `leave_consumption` | Payroll `employee_leave_balance_consumption` |
| `leave_monthly_lop` | HRMS `employee_monthly_lop` |
| `leave_import_log` | Payroll `employee_leave_import` |

> The engine takes **both halves** (`D-03`): request/approval/documents from HRMS,
> allocation/consumption/loss-of-pay from Payroll.

### Holiday (2)
`holiday_calendar` (new grouping, per work location) · `holiday` (merge of both `holidays` tables)

### Attendance & overtime capture (2) — moved from `hrms` (`D-35`)
| Table | Notes |
|---|---|
| `attendance` | Present, absent, half day. Administrator-entered for a Payroll-only tenant. Clock sessions stay in `hrms` |
| `overtime_request` | Approved overtime. Administrator-entered for a Payroll-only tenant. Writes to `pay_input` |

### Pay handoff (2)
| Table | Notes |
|---|---|
| `lop_policy` | **New (`CORE-09`).** Per-tenant rule for deriving loss of pay. Seeded from Payroll's attendance-preference flags (`D-07`) |
| `pay_input` | **New (`CORE-10`).** The only channel from a module into a pay run. HRMS writes overtime and attendance adjustments; Payroll reads. Empty for a Payroll-only tenant |

### Approval workflow (3)
`approval_definition` · `approval_instance` · `approval_step` — **all new (`CORE-11`)**,
replacing three hard-coded approval paths (leave, reimbursement, investment proof).

### Notification (3)
| Table | Origin |
|---|---|
| `notification` | HRMS `notifications`. Payroll has none today |
| `notification_template` | **New** |
| `reminder_rule` | HRMS `role_reminder_config` + the five reminder tables + Payroll `reminder` |

### Platform (4)
| Table | Notes |
|---|---|
| `document` | Unified blob pointer. Replaces scattered Cloudinary references |
| `audit_log` | **New (`CORE-14`).** Neither product has one |
| `master_config` | Payroll `masterConfig` |
| `report_definition` | **New (`CORE-16`).** HRMS `report` is the employee reporting hierarchy, not a report definition — neither product lets anyone define a report |

---

## 3. `hrms` schema — 11 tables

Present only when HRMS is bought.

> `attendance` and `overtime_request` **moved to `core`** (`D-35`). What remains here is the
> clock-based experience and the project work — the part a customer actually buys.

| Group | Tables |
|---|---|
| Clock-based attendance | `clock_session`, `attendance_regularization`, `attendance_preference` |
| Project work | `project`, `task`, `assignment` |
| Timesheet | `timesheet`, `timesheet_project_entry`, `timesheet_day_entry`, `timesheet_task_entry`, `timesheet_notification` |

> ⚠️ `attendance_preference` **moves in from Payroll**, where it is orphaned: it configures
> hour thresholds, overtime minimums and pay-treatment flags for an attendance system
> Payroll does not have.

> ⚠️ **Timesheets are unresolved.** Two complete systems are live today (`timesheet` and
> `timesheets`, with separate controllers, services, repositories and DTOs). The table list
> above assumes **one** survives. Which one, and what happens to the other's data, is
> settled at `W-67`, the migration rules ticket, before a single row moves. It is a
> **data** question — whose history is authoritative — not a design one, so it does not
> block building the HRMS module.

---

## 4. `payroll` schema — 58 tables

Present only when Payroll is bought.

| Group | Tables |
|---|---|
| Component definitions (4) | `earning`, `deduction`, `benefit`, `reimbursement` |
| Employee salary (10) | `ctc_structure`, `ctc_epf_component`, `ctc_esi_component`, `employee_earning`, `employee_benefit`, `employee_reimbursement`, `employee_variable_earning`, `employee_deduction`, `fbp`, `employee_fbp_component` |
| Pay run (8) | `pay_schedule`, `payrun`, `employee_payrun`, `off_cycle_payrun`, `off_cycle_payrun_employee`, `off_cycle_payrun_employee_earning`, `off_cycle_payrun_employee_deduction`, `one_time_payout` |
| Statutory (7) | `epf`, `esi`, `professional_tax`, `slab_rate_configuration`, `slab_detail`, `org_pt_override`, `pt_history` |
| Tax declaration (12) | `income_tax_declaration`, `employee_investment_declaration`, and ten `employee_inv_*` detail tables (home loan, house rent, let-out property ×2, other income, pre-tax deduction, previous employment, section 6A, tax summary) |
| Tax computation (7) | `new_tax_calculation`, `old_tax_calculation`, `old_tax_calculation_revision`, `old_tax_section_deduction`, `old_tax_revision_section_deduction`, `employee_tds`, `income_tax_detail` |
| Investment proof (8) | `proof_of_investment`, `employee_proof_of_investment`, `employee_poi_item`, `employee_poi_item_comment`, `employee_poi_document`, `employee_poi_property_detail`, `employee_investment_proof`, `employee_investment_proof_file` |
| Claims (2) | `reimbursement_claim`, `employee_reimbursement_request` |

**All carry `tenant_id`.** The per-tenant statutory overrides (`org_pt_override`,
`pt_history`) stay here, referencing the shared base in `reference`.

---

## 5. `reference` schema — 15 tables

**No tenant column.** Shared national data, readable by every tenant, writable only by a
migration or an administrator.

| Group | Tables |
|---|---|
| Tax rules (11) | `tax_slab_master`, `tax_slab_master_history`, `tax_slab_detail_history`, `hra_rule_master`, `home_loan_rule_master`, `let_out_property_rule_master`, `other_income_rule_master`, `standard_deduction_rule_master`, `section6a_item_master`, `section87a_rebate_rule_master`, `cess_surcharge_rule_master` |
| Generic lookups (4) | `country`, `state`, `bank`, `currency` |

**Annual tax update becomes:** one migration script, plus a review of which tenants hold
overrides in `payroll`.

---

### The `migration` schema — bookkeeping, not data

**Not an application schema.** It holds one table, created and owned by Flyway's own
runner, and exists so that rule 3 can stay absolute: `flyway_schema_history` records
which migration scripts have run, which is not tenant data and cannot carry `tenant_id`.
Keeping it out of `core`, `hrms` and `payroll` means the `W-07` build check needs no
exemption list (`D-45`).

| Table | Owner | Readable by | Notes |
|---|---|---|---|
| `flyway_schema_history` | `migration_user` | `migration_user` only | Created by Flyway on first run. **No** grant to `app_user` or `readonly_user` — neither role holds `USAGE` on the schema |

The schema itself is created by provisioning (`infra/postgres/02-schemas.sql`), not by a
migration: Flyway cannot create the schema that holds its own history table before it has
run.

---

## 6. Counts

| | Today | Target |
|---|---|---|
| Databases | 2 | 1 (+1 for Keycloak) |
| Schemas | — | 4 application (+1 `migration`, no application data) |
| Tables | 131 | ~130 |
| `core` | — | 46 |
| `hrms` | — | 11 |
| `payroll` | — | 58 |
| `reference` | — | 15 |
| `migration` | — | 1 (`flyway_schema_history`, not counted above) |
| Duplicated concepts | employee, leave, holiday, roles, documents, timesheets | none |
| Tables with a tenant column | 63 of 131 | 115 of 130 (all but `reference`) |
| `core` / `hrms` / `payroll` / `reference` | — | 46 / 11 / 58 / 15 |

**The count barely moves, which is the point.** Nothing is thrown away. Roughly twenty
duplicate tables collapse into ten; about twelve new tables appear for capabilities neither
product has today.

---

## 7. Mapping appendix — what happens to each concept

> Narrative and rationale live in `06-current-to-target.md`. This table is the reference.

### Merged (two sources → one table)
| Concept | HRMS source | Payroll source | Target |
|---|---|---|---|
| Employee | `employee` | `employee` | `core.employee` |
| Personal details | `personal` | `employee_personal_detail` | `core.employee_personal` |
| Leave type | `leave_types` | `leave_type` | `core.leave_type` |
| Leave balance | `leave_balances`, `employee_leave_balances` | `employee_leave_allocation` | `core.leave_allocation` |
| Holiday | `holidays` | `holidays` | `core.holiday` |
| Role | `role` | `organizationRole` | `core.role` |
| Action | `action` | `Action` | `core.action` |
| Role↔action | `user_action_mapping` | `organization_role_action` | `core.role_action` |
| User | `ourusers` | `companyUser` | `core.user_account` |
| Reminder rules | `role_reminder_config` + 5 reminder tables | `reminder` | `core.reminder_rule` |

### New (no source)
`core.subscription` · `core.subscription_module` · `core.reporting_line` · `core.lop_policy` ·
`core.pay_input` · `core.approval_definition` · `core.approval_instance` · `core.approval_step` ·
`core.notification_template` · `core.audit_log` · `core.holiday_calendar` · `core.document` ·
`core.report_definition`

### Retired
| Table | Reason |
|---|---|
| `password_reset_token` (HRMS) | Keycloak owns password reset |
| `leave_request` (HRMS, older) | Superseded by `leave_requests`. **But both are still reachable today** — see warning below |
| `employee_leave_balances` **or** `leave_balances` | One of the pair. **Not yet proven which** |
| `timesheet` **or** `timesheets` | One of the pair. **Both fully live today** |
| `EmployeeTaxCalculationResult`, `EmployeeTaxRecalculation` | Already disabled in code (`@Entity` commented out) |
| `ResidentialAddress` | Not a table — `@Embeddable`, columns inline |

> ⚠️ **Do not delete any row in the "Retired" block on the strength of this document.**
> Investigation (`.claude/outputs/2026-09-11-hrms-duplicate-entities.md`) found that the
> older leave request entity is still reachable via update and delete routes, and that
> *both* timesheet systems are live. "No endpoints" does not prove an entity is dead — a
> service can still write to it. Each retirement needs its own confirmation.

### Moved between modules
| Table | From | To | Why |
|---|---|---|---|
| `attendance_preferences` | Payroll | `hrms` | Clock-based thresholds. Pay-treatment flags go to `core.lop_policy` instead |
| `attendance`, `overtime_request` | `hrms` | `core` | Every tenant records these; only the request workflow is bought (`D-35`) |
| Tax rule masters (11) | Payroll, per tenant | `reference`, shared | National law, identical for all (`D-08`) |

---

## 7a. Tables per functionality

Capability identifiers come from `01`. This section names tables only; what each capability
*does* stays in `01`.

### `core` schema

| Capability | Tables | Count |
|---|---|---|
| `CORE-01` Tenant registry | `tenant` | 1 |
| `CORE-02` Identity & SSO | `user_account`, `user_tenant` | 2 |
| `CORE-03` Authorization & roles | `role`, `action`, `role_action`, `user_role` | 4 |
| `CORE-04` Employee master | `employee`, `employee_personal`, `employee_contact`, `employee_identification`, `employee_employment`, `employee_bank` | 6 |
| `CORE-05` Org structure | `department`, `designation`, `work_location` | 3 |
| `CORE-06` Reporting hierarchy | `reporting_line` | 1 |
| `CORE-07` Leave engine | `leave_type`, `leave_policy`, `leave_allocation`, `leave_request`, `leave_request_document`, `leave_consumption`, `leave_monthly_lop`, `leave_import_log` | 8 |
| `CORE-08` Holiday calendar | `holiday_calendar`, `holiday` | 2 |
| `CORE-09` LOP & working-day policy | `lop_policy` | 1 |
| `CORE-10` Pay input ledger | `pay_input` | 1 |
| `CORE-11` Approval workflow | `approval_definition`, `approval_instance`, `approval_step` | 3 |
| `CORE-12` Notifications | `notification`, `notification_template`, `reminder_rule` | 3 |
| `CORE-13` Document store | `document` | 1 |
| `CORE-14` Audit trail | `audit_log` | 1 |
| `CORE-15` Reference data | `master_config` (+ 4 lookups in `reference`) | 1 |
| `CORE-16` Reporting & export | `report_definition` | 1 |
| `CORE-17` Setup checklist | `tenant_setup_step` | 1 |
| `CORE-18` Invitations | `user_invitation`, `employee_invitation` | 2 |
| `CORE-19` Employee self-service portal | **none** — a view over Core and whichever modules the tenant holds | 0 |
| `CORE-20` Attendance capture (basic) | `attendance` | 1 |
| `CORE-21` Overtime capture (basic) | `overtime_request` | 1 |
| `PLAT-01` Subscription & entitlement | `subscription`, `subscription_module` | 2 |
| | **Total** | **46** |

### `hrms` schema

| Capability | Tables | Count |
|---|---|---|
| ~~`HRMS-01`~~ Attendance capture | **moved to `core`** (`D-35`) | 0 |
| `HRMS-02` Clock sessions | `clock_session` | 1 |
| `HRMS-03` Regularization | `attendance_regularization` | 1 |
| `HRMS-04` Attendance preferences | `attendance_preference` | 1 |
| ~~`HRMS-05`~~ Overtime | **moved to `core`** (`D-35`) | 0 |
| `HRMS-12` Employee request workflows | **none** — runs on `core.approval_*` | 0 |
| `HRMS-06` Projects | `project` | 1 |
| `HRMS-07` Tasks | `task` | 1 |
| `HRMS-08` Assignments | `assignment` | 1 |
| `HRMS-09` Timesheets | `timesheet`, `timesheet_project_entry`, `timesheet_day_entry`, `timesheet_task_entry` | 4 |
| `HRMS-10` Timesheet reminders | `timesheet_notification` | 1 |
| `HRMS-11` HRMS dashboards | **none** — reads the above | 0 |
| | **Total** | **11** |

### `payroll` schema

| Capability | Tables | Count |
|---|---|---|
| `PAY-01` Salary structure & CTC | `ctc_structure`, `ctc_epf_component`, `ctc_esi_component`, `employee_earning`, `employee_benefit`, `employee_reimbursement`, `employee_variable_earning` | 7 |
| `PAY-02` Salary components catalogue | `earning`, `deduction`, `benefit`, `reimbursement` | 4 |
| `PAY-03` Flexible benefit plan | `fbp`, `employee_fbp_component` | 2 |
| `PAY-04` Pay schedule | `pay_schedule` | 1 |
| `PAY-05` Pay run | `payrun`, `employee_payrun` | 2 |
| `PAY-06` Off-cycle pay runs | `off_cycle_payrun`, `off_cycle_payrun_employee`, `off_cycle_payrun_employee_earning`, `off_cycle_payrun_employee_deduction` | 4 |
| `PAY-07` One-time payouts | `one_time_payout` | 1 |
| `PAY-08` Statutory components | `epf`, `esi`, `professional_tax`, `slab_rate_configuration`, `slab_detail`, `org_pt_override`, `pt_history` | 7 |
| `PAY-09` Income tax declaration | `income_tax_declaration`, `employee_investment_declaration`, + 10 `employee_inv_*` detail tables | 12 |
| `PAY-10` Tax calculator | `new_tax_calculation`, `old_tax_calculation`, `old_tax_calculation_revision`, `old_tax_section_deduction`, `old_tax_revision_section_deduction`, `income_tax_detail` | 6 |
| `PAY-11` Proof of investment | `proof_of_investment`, `employee_proof_of_investment`, `employee_poi_item`, `employee_poi_item_comment`, `employee_poi_document`, `employee_poi_property_detail`, `employee_investment_proof`, `employee_investment_proof_file` | 8 |
| `PAY-12` Reimbursement claims | `reimbursement_claim`, `employee_reimbursement_request` | 2 |
| `PAY-13` Ad-hoc salary deductions | `employee_deduction` | 1 |
| `PAY-14` Tax deducted at source | `employee_tds` | 1 |
| `PAY-15` Payslips & annual statements | **none** — derived from `employee_payrun` and rendered on demand | 0 |
| `PAY-16` Payroll dashboard | **none** — reads the above | 0 |
| `PAY-17` Prior payroll import | ⚠️ **no table identified.** The setup checklist has a prior-payroll step, but no dedicated table was found. Either it writes into `employee_payrun`, or a table is missing from this design. **Confirm when scoping `PAY-17`** | 0? |
| | **Total** | **58** |

### `reference` schema (no tenant column)

| Capability | Tables | Count |
|---|---|---|
| `CORE-15` Reference data | `country`, `state`, `bank`, `currency` | 4 |
| `PAY-10` Tax calculator | `tax_slab_master`, `tax_slab_master_history`, `tax_slab_detail_history`, `hra_rule_master`, `home_loan_rule_master`, `let_out_property_rule_master`, `other_income_rule_master`, `standard_deduction_rule_master`, `section6a_item_master`, `section87a_rebate_rule_master`, `cess_surcharge_rule_master` | 11 |
| | **Total** | **15** |

### Capabilities that own no tables

| Capability | Why |
|---|---|
| `CORE-19` Employee self-service portal | A view over existing tables, filtered to the signed-in employee |
| `HRMS-12` Employee request workflows | Runs entirely on the Core approval tables. The workflow is gated by entitlement, not by its own storage |
| `HRMS-11`, `PAY-16` Dashboards | Aggregate reads |
| `PAY-15` Payslips | Rendered from the pay run row; the signed link is a computed token, not stored |
| Most `PLAT-*` | Infrastructure, not data. The exception is `PLAT-01`, which owns the subscription tables |

---

## 8. Indexing

Today there are effectively **no indexes** across 136 entity classes. This is the single
highest-return change in the programme (`PLAT-06`).

Minimum standard for the target schema:

| Rule |
|---|
| `tenant_id` is the **leading column** of every index on a tenant-scoped table |
| Every foreign key has an index |
| Every column used in a `WHERE`, `ORDER BY` or `JOIN` on a list screen has one |
| Composite index on `(tenant_id, employee_id, period)` for anything queried per pay period |
| Soft-delete column included in the index, not filtered after the fact |

---

## 9. Row-level security

| Database role | Used by | Rights | RLS |
|---|---|---|---|
| `app_user` | `app` and `worker` containers | Read/write on `core`, `hrms`, `payroll`; read on `reference` | **Enforced.** Sets tenant per transaction |
| `migration_user` | The migration step in the pipeline only | DDL on all schemas | Bypassed |
| `readonly_user` | Reporting and exports, replica | Read only | Enforced |

**The application never connects as an owner.** That single constraint is what makes
row-level security a real boundary rather than a suggestion — a bug in the application
cannot escape it.

### The policy, and the shape every table copies

`core.tenant` is the root table and the worked reference, shipped by `W-07`
(`code/backend/migration/src/main/resources/db/migration/core/V001__tenant.sql`). Every
table in `core`, `hrms` and `payroll` carries the same two statements in the migration
script that creates it:

```sql
ALTER TABLE <schema>.<table> ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON <schema>.<table>
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
```

The session variable is `app.current_tenant_id` and the policy is always named
`tenant_isolation`. The application sets the variable per transaction, through
`TenantContext.setForConnection` in the `shared` module.

**Three things about that policy are load-bearing, and each was got wrong once:**

| | |
|---|---|
| The `CASE` is not decoration | After a transaction ends the variable reverts to the **empty string**, not to unset, and `''::uuid` raises. The short form breaks on the second use of a pooled connection (`D-56`) |
| There is no `WITH CHECK`, deliberately | With no `FOR` and no `TO` clause the policy is `FOR ALL TO PUBLIC`, and PostgreSQL reuses `USING` as the check — so a cross-tenant `INSERT` is rejected too. Narrow it to `FOR SELECT` and reads stay correct while writes leak |
| Unbound means empty, not everything | A connection that never sets the variable sees zero rows. That is the intended fail-safe, and `W-08` must still always bind |

Three CI gates in `.github/workflows/ci.yml` refuse a migration that creates a table in
`core`, `hrms` or `payroll` without `tenant_id`, without a schema qualifier, or without
both `ENABLE ROW LEVEL SECURITY` and `CREATE POLICY tenant_isolation`. They match per
**file**, so a script creating two tables can still ship the second unprotected — one
table per migration script is the rule that keeps them sound (#137).

---

## Related

- Capabilities: `01-platform-shape.md` · Migration narrative: `06-current-to-target.md`
- Decisions: `07-decisions.md` · Today's schema: `legacy/docs/DB_SCHEMA.md`
