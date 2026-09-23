# Feature: Department, designation and work location

| Field | Value |
|---|---|
| **Feature ID** | `W-14.1` · from ticket #15 · `CORE-05` |
| **Promoted to** | `docs/target-state/features/W-14-1-org-masters.md` on branch `W-14-1-org-masters` — **`W-14-1` with hyphens**, never `W-14.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-13.1` — an employee is assigned to these |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 3 scripts, one table each — aggregate exception | 1 — exception granted 2026-09-22 |
| Externally testable behaviour | an employee is assigned a department, designation and work location that exist in the tenant | 1 |
| Frontend area | none | 1 |

Within cap. The three are one aggregate: the master records an employee's employment points at.

---

## 1. Problem

One product models these properly and the other stores them as free text.

- Payroll has `Department`, `Designation` and `WorkLocation` entities, each with a public id, a foreign key to the organisation and a status flag. `WorkLocation.java:1-130` also carries a full address and an `isFilingAddress` flag, which statutory filing needs
- HRMS stores both as **plain strings on the employment record** — `department` and `jobTitle` on `Work.java`. No lookup table, no enumeration, no foreign key, no validation

`09-build-order.md:187` is blunt about what that costs: *"HRMS holds department and
designation as free text. Converting it has no clean rule — treat it as its own problem, not a
footnote."*

The evidence pass could not determine how many distinct values a conversion would face — the
code has no seed, no dropdown and no enumeration to count. That number is only knowable from
production data, and it belongs to `W-67`.

This ticket therefore builds the target shape and does not attempt the conversion. Free-text
values become somebody's migration problem with a real dataset in front of them, which is the
only way that job can honestly be done.

`W-17` also waits on this: a holiday calendar hangs off a work location, and there is no work
location until this ticket.

## 2. Scope

**In scope**

- `core.department`, `core.designation`, `core.work_location`
- CRUD for each, tenant-scoped
- Work location carrying the address and the filing-address flag Payroll already models
- Adding the three nullable foreign-key columns to `core.employee`, expand-style

**Out of scope**

- **Converting HRMS free text** — `W-67`, with production data
- The reporting hierarchy — `W-14.2`
- The org chart — `W-14.2`
- Statutory registrations tied to a filing address — `W-31`

## 3. Flow

```
[tenant admin] --> [DepartmentController / DesignationController / WorkLocationController]
   --> TenantContext bound by W-08 --> [core.department, designation, work_location under RLS]

[W-13 employee] --> assigned department_id, designation_id, work_location_id
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../org/DepartmentController.java`, `DesignationController.java`, `WorkLocationController.java` | new |
| Service | three, one per master | new |
| Entity | `core/.../org/Department.java`, `Designation.java`, `WorkLocation.java` | new, each `@Table(schema="core")` |
| Repository | three | new |
| Entity | `core/.../employee/Employee.java` | change — three nullable associations |
| DTO | `core/.../org/*Request.java`, `*Response.java` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/departments` | name, code | `201` | Bearer, tenant bound |
| GET | `/api/v1/departments` | `?activeOnly=` | list | Bearer, tenant bound |
| PUT | `/api/v1/departments/{id}` | name, code, active | `200` | Bearer, tenant bound |
| DELETE | `/api/v1/departments/{id}` | — | `204`, refused if assigned | Bearer, tenant bound |

and the same four for `/designations` and `/work-locations`.

Deletion is refused while an employee points at the record. Payroll's status flag is the
alternative and it is kept: deactivating hides a value from new assignments without breaking
the employees who hold it.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__department.sql` | `core.department` | yes | additive |
| `core/V0NN__designation.sql` | `core.designation` | yes | additive |
| `core/V0NN__work_location.sql` | `core.work_location` | yes | additive |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

`department` and `designation`: `id uuid` · `tenant_id uuid NOT NULL` ·
`code varchar(32) NOT NULL` · `name varchar(128) NOT NULL` ·
`is_active boolean NOT NULL DEFAULT true` · four audit columns.

`work_location`: the same, plus `address_line1` · `address_line2` · `city` · `state` ·
`state_code varchar(8)` · `zip_code varchar(16)` · `country_code char(2)` ·
`is_filing_address boolean NOT NULL DEFAULT false`.

- [x] `tenant_id` on all three, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, code)` unique on each, `(tenant_id, is_active)` on each
- [x] Money columns — none
- [x] Expand / contract — three new tables, plus three **nullable** columns added to `core.employee` with no destructive step

**The three columns on `core.employee` are nullable and added here, not in `W-13.1`.** That is
the expand half: `W-13.1` shipped without them by design, and an employee created before this
ticket keeps working afterwards.

`state_code` is separate from `state` because payroll filings need the code and free text
cannot supply one — the same reasoning that settled the address shape in `W-13.2`.

RLS and the `tenant_isolation` policy in the exact `CASE` form in each script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../org/OrgMasterServiceTest.java` | duplicate code refused within a tenant; deleting an assigned record refused; deactivating an assigned record allowed |
| Integration | `core/.../org/OrgMasterRlsIT.java` | tenant A cannot read or assign tenant B's masters as `app_user` |
| Integration | `core/.../org/EmployeeAssignmentIT.java` | an employee cannot be assigned a department belonging to another tenant |
| Integration | `core/.../org/FilingAddressIT.java` | at most one work location per tenant is the filing address |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`EmployeeAssignmentIT` matters because a foreign key alone does not stop a cross-tenant
reference — `migration_user` owns the tables and bypasses RLS, so the check must run as
`app_user`. Same reasoning as `W-13.2`'s cascade test.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in department designation work_location; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, is_nullable FROM information_schema.columns
    WHERE table_schema='core' AND table_name='employee'
      AND column_name IN ('department_id','designation_id','work_location_id') ORDER BY 1;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on all three | `t` three times |
| New employee columns | three rows, all `is_nullable = YES` |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Someone attempts the free-text conversion here | **medium — it looks like it belongs** | Named in **Out of scope**; the build order calls it its own problem, and the data needed to do it is not in the repository |
| The employee columns are made `NOT NULL` and existing rows break | medium | Nullable, asserted in verification |
| More than one filing address per tenant | medium | Partial unique index plus `FilingAddressIT` |
| Deletion breaks employees pointing at a record | medium | Refused while assigned; deactivation is the supported path |
| Designation is confused with pay grade | low | Pay grade lives on `core.employee_employment` from `W-13.2`; designation is an org master |

## 10. Rollback

Nothing is deployed. All scripts are additive and forward-only —
`migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | all three, each in its own script |
| Flyway only, `ddl-auto` nowhere | three scripts; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | two indexes per table, `tenant_id` leading |
| Expand / contract | three new tables plus three nullable columns; no destructive step |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| HRMS department and designation as free text (`Work.java`) | **Fixed for new data.** Converting existing values is `W-67` |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Does a designation carry a level or grade?** Payroll's does not; HRMS keeps `payGrade` on the employment record. **Recommend** leaving grade on employment for now — adding a level to designation is easy later, and removing it is not.
2. **Are departments hierarchical?** Neither product nests them. **Recommend** flat, and revisiting only if a customer asks — a nullable parent column is cheap to add and expensive to un-model once screens assume a tree.
