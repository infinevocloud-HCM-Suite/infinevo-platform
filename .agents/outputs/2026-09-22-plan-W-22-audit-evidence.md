# W-22 Audit Trail — Evidence from Legacy & New Platform

## Summary

| Finding | Status |
|---|---|
| Legacy has no centralized audit logging | Confirmed: no `@EntityListeners`, `@EnableJpaAuditing`, or Envers |
| Some entities carry manual `created_by`/`updated_at` columns | Inconsistent across both products |
| Payroll uses Hibernate `@CreationTimestamp`/`@UpdateTimestamp` | HRMS uses same annotations on some entities |
| New platform has `TenantContext` infrastructure | Confirmed; tenant binding per transaction |
| Flyway migration structure exists with RLS policies | Confirmed; full schema qualification required |
| Target state defines `core.audit_log` as new | `docs/target-state/02-data-model.md:128` |

---

## Part A1 — Legacy: Change Capture Mechanisms

### Annotations: No audit framework found

**No files found matching:**
- `@EntityListeners` decorator
- `AuditingEntityListener` 
- `@EnableJpaAuditing` config annotation
- Hibernate Envers (`@Audited`, `@AuditTable`)

**Search result:** `legacy/` — no matches `legacy/HRMS_Backend` · `legacy/Payroll-Bend-SBoot`

### Manual audit columns: Inconsistently present

**Payroll Backend** has `created_by`, `updated_by`, `created_at`, `updated_at` on select entities using Hibernate's `@CreationTimestamp` / `@UpdateTimestamp`:

- `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/Organization.java:67-79` — `createdBy` (String), `updatedBy` (String), `createdDate` (LocalDateTime with `@CreationTimestamp`), `updatedDate` (LocalDateTime with `@UpdateTimestamp`)
- `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leave/EmployeeLeaveAllocation.java:68-75` — `created_by` (String), `created_at` (LocalDateTime, set to `LocalDateTime.now()`), `updated_at` (LocalDateTime, set to `LocalDateTime.now()`)

**HRMS Backend** uses `@CreationTimestamp` / `@UpdateTimestamp` on subset of entities:

- `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Employee.java:54-58` — `@CreationTimestamp private LocalDateTime createdAt` and `@UpdateTimestamp private LocalDateTime updatedAt`
- 14 HRMS entity files found using these annotations (Timesheet.java, LeaveRequests.java, LeaveType.java, Attendance.java, etc.)

**Pattern:** Annotations are present but manually set timestamps. Not a centralized audit trail — no automatic recording of who made changes or what changed.

### History tables: Tax-specific only

Three history tables found, all for tax calculations, not general audit:

- `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/EmployeeITDeclaration/taxCalculator/TaxSlabMasterHistory.java`
- `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/statutorycomponents/PTHistory.java`
- `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/statutorycomponents/TaxSlabDetailHistory.java`

**Conclusion:** Neither frozen product has a general-purpose audit log. Many entities carry no audit columns at all. No row-versioning or change tracking exists.

---

## Part A2 — New Platform: Existing Infrastructure

### TenantContext (tenant binding)

**File:** `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantContext.java:1-118`

**Key methods:**
- `TenantContext.set(UUID tenantId)` — line 37: binds tenant for thread (rejects null)
- `TenantContext.require()` — line 50: returns current tenant or throws `IllegalStateException` if unbound
- `TenantContext.current()` — line 60: returns `Optional<UUID>` 
- `TenantContext.setForConnection(Connection conn)` — line 98: binds tenant to connection via `SELECT set_config('app.current_tenant_id', ?, true)`
- `TenantContext.clear()` — line 115: must be called in `finally` block

**How obtained:** "The current tenant is set once, at the edge, by the binding filter ({@code W-08}) or by the job runner" (line 9)

### Flyway migration structure

**Location:** `code/backend/migration/src/main/resources/db/migration/`

**Existing scripts:**
- `core/V001__tenant.sql`
- `core/V002__user_tenant.sql`
- `reference/V003__reference_lookups.sql`
- `reference/V004__reference_tax_masters.sql`
- `reference/V005__reference_tax_seed.sql`

**Convention (from `code/backend/migration/README.md:9-28`):**
- Naming: `V<NNN>__<slug>.sql` (three digits, double underscore, lowercase slug)
- Global sequence across all four directories: `reference/`, `core/`, `hrms/`, `payroll/`
- Within batch: number `reference` < `core` < modules (line 18-27)
- Example: `V001__country.sql` (reference), `V002__tenant.sql` (core), `V003__employee.sql` (hrms), `V004__payroll_period.sql` (payroll)

**Schema naming rule (line 33-47):**
> "Every statement names its schema. No exceptions, including indexes and constraints."
> ```sql
> CREATE TABLE core.employee (...)           -- yes
> CREATE INDEX ON core.employee (tenant_id)  -- yes
> CREATE TABLE employee (...)                -- NO
> ```

**tenant_id requirement (line 52-68):**
> "Every table in `core`, `hrms`, and `payroll` must carry a `tenant_id uuid NOT NULL` column as the **leading index column**. `reference` is the one exception — it holds national data shared across all tenants."

**Row-level security (line 76-125):**
> "Every table in `core`, `hrms`, and `payroll` enables RLS and carries exactly one isolation policy... with the exact USING clause... including the `CASE`. A policy using the short cast form is a defect even though CI accepts it."

The CASE handles two "unset" states (line 99-117):
- Never set on connection → `current_setting(..., true)` returns `NULL`
- Set earlier, transaction ended → reverts to `''` (empty string), and plain `::uuid` cast would raise error

Template from `core.tenant` in `V001__tenant.sql` (line 125).

### Spring Data / JPA config for auditing

**Search result:** No `@EnableJpaAuditing` found in `code/backend/` yet. This means W-22 will add the auditing framework from scratch.

---

## Part A3 — Target State: What `audit_log` will be

**From `docs/target-state/02-data-model.md:124-130`:**

| Table | Notes |
|---|---|
| `audit_log` | **New (`CORE-14`).** Neither product has one |

**Line 128:** `| audit_log | **New (`CORE-14`).** Neither product has one |`

**Capability reference (`docs/target-state/02-data-model.md:305`):**

| Capability | Tables | Count |
|---|---|
| `CORE-14` Audit trail | `audit_log` | 1 |

**Mapping appendix (`docs/target-state/02-data-model.md:256`):**

Under "New (no source)":
> `core.subscription` · `core.subscription_module` · `core.reporting_line` · `core.lop_policy` · `core.pay_input` · `core.approval_definition` · `core.approval_instance` · `core.approval_step` · `core.notification_template` · **`core.audit_log`** · `core.holiday_calendar` · `core.document`

**Audit retention policy:** Not mentioned in `docs/target-state/02-data-model.md`. No statement found about retention windows, deletion, or archival rules.

**Related from timestamp spec (line 12, 24):**
> "Timestamps: `created_at`, `created_by`, `updated_at`, `updated_by` on every table. Feeds `CORE-14`."

---

## Part A4 — Gaps and Uncertainties

| Gap | Impact |
|---|---|
| Audit retention policy not in spec | W-22 may need to propose one (e.g., 7 years for compliance, then delete) |
| No design of `audit_log` columns yet | W-22 must specify: what gets logged, when (all tables or selection?), how much history |
| JPA auditing provider not yet chosen | Spring Data JPA + AuditorAware? Or manual interceptor? |
| Who is "created_by" / "updated_by"? | Current user? Keycloak UUID? Need to define before coding |

