# Feature: Employee record

| Field | Value |
|---|---|
| **Feature ID** | `W-13.1` · from ticket #14 · `CORE-04` |
| **Promoted to** | `docs/target-state/features/W-13-1-employee-record.md` on branch `W-13-1-employee-record` — **`W-13-1` with hyphens**, never `W-13.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | new `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured), BUG-002 (fixed for this table) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.employee` | 1 |
| Externally testable behaviour | an employee is created, read and updated within one tenant and invisible to another | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

Two products hold an employee and they do not agree on what one is.

- HRMS spreads ~67 columns over six entities rooted at `legacy/HRMS_Backend/.../entity/Employee.java:14-154`
- Payroll holds ~56 columns over three, rooted at `legacy/Payroll-Bend-SBoot/.../entity/employee/BasicDetails.java`
- `02-data-model.md:68` calls this **"the highest-risk merge in the project"**

Neither shape can be adopted whole. HRMS carries no bank details and no statutory eligibility;
Payroll carries no emergency contact, no reporting line and only a PAN for identification.

Two defects come across if the shapes are copied rather than merged.

- `dateOfJoining` is a `VARCHAR` in Payroll — `BasicDetails.java:44`
- `amountInPercentage` is a `double` — `BasicDetails.java:123` — which `CONVENTIONS.md` §2 forbids

And the tenancy gap this closes: HRMS scopes none of its 39 entities to an organisation
(BUG-002), so `core.employee` is the first employee table in the programme that is isolated by
the database rather than by a `WHERE` clause a developer remembered to write.

## 2. Scope

**In scope**

- `core.employee` — the root record only
- Create, read, update, soft-delete, scoped by RLS to the calling tenant
- `employee_number` unique **within a tenant**, not globally

**Out of scope**

- The five detail tables — `W-13.2`
- Search and listing — `W-13.3`
- `department_id`, `designation_id`, `work_location_id` — those tables are `W-14`'s (#15), and `W-14` adds the columns in its own migration
- Statutory eligibility flags — see decision 1
- Merging live rows from either product — `W-67`, as #14 says

## 3. Flow

```
[API client] --> [EmployeeController] --> [EmployeeService]
   --> TenantContext already bound by W-08 --> [core.employee under RLS]
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../employee/EmployeeController.java` | new |
| Service | `core/.../employee/EmployeeService.java` | new |
| ServiceImpl | `core/.../employee/EmployeeServiceImpl.java` | new |
| Entity | `core/.../employee/Employee.java` | new, `@Table(schema="core")` |
| Repository | `core/.../employee/EmployeeRepository.java` | new |
| DTO | `core/.../employee/EmployeeRequest.java`, `EmployeeResponse.java` | new |
| Enumeration | `core/.../employee/EmploymentStatus.java` | new |

Every entity declares its own schema — `code/backend/app/src/main/resources/application.yml:22-23`
sets no `default_schema`, deliberately.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/employees` | `EmployeeRequest` | `201` + `EmployeeResponse` | Bearer, tenant bound |
| GET | `/api/v1/employees/{id}` | — | `EmployeeResponse` | Bearer, tenant bound |
| PUT | `/api/v1/employees/{id}` | `EmployeeRequest` | `EmployeeResponse` | Bearer, tenant bound |
| DELETE | `/api/v1/employees/{id}` | — | `204`, soft delete | Bearer, tenant bound |

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__employee.sql` | `core.employee` | yes | additive only |

**The version number is assigned when the branch is cut, not here.** `W-10` and `W-22` are
also in flight and the sequence is global across all four directories —
`migration/README.md:17-31`. A duplicate number is a loud Flyway failure.

Columns: `id uuid` · `tenant_id uuid NOT NULL` · `employee_number varchar(64) NOT NULL` ·
`first_name` · `middle_name` · `last_name` · `gender varchar(32)` ·
`date_of_joining date NOT NULL` · `termination_date date NULL` ·
`status varchar(32) NOT NULL` · `work_email varchar(255)` · `mobile varchar(32)` ·
`is_portal_enabled boolean NOT NULL DEFAULT true` ·
`is_deleted boolean NOT NULL DEFAULT false` · the four audit columns used by
`V002__user_tenant.sql:8-11`.

**`is_portal_enabled` is here by decision 2**, carried across from `BasicDetails.java:76`.
It defaults to `true`, so an employee gets the portal unless someone turns it off — the
frozen system defaults to off and requires an administrator to enable each person, which is
onboarding friction with no security benefit once roles exist.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, employee_number)` unique, `(tenant_id, work_email)`, `(tenant_id, status)`
- [x] Money columns — none. `amountInPercentage` is **not** carried here; it is a payroll figure and belongs to `PAY-01`
- [x] Expand / contract — new table only; `W-14` adds its three FK columns later, nullable

`date_of_joining` becomes a real `date`, correcting `BasicDetails.java:44`.
RLS and the `tenant_isolation` policy in the exact `CASE` form go in the same script —
`migration/README.md:76-123`. One table, one script — `migration/README.md:127-129`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../employee/EmployeeServiceImplTest.java` | validation, soft delete excludes from read, status transitions |
| Integration | `core/.../employee/EmployeeRlsIT.java` | tenant A cannot read, update or delete tenant B's employee as `app_user` |
| Integration | `core/.../employee/EmployeeNumberUniquenessIT.java` | the same `employee_number` is accepted in two tenants and refused twice in one |

Both integration tests extend `AbstractIntegrationTest` and carry `@EnabledIfDockerAvailable`
— `code/backend/shared/src/test/java/com/infinevo/shared/test/EnabledIfDockerAvailable.java` —
so they fail loudly rather than skipping the way #117 did.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.employee'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "\d core.employee"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on `core.employee` | `t` |
| `date_of_joining` type | `date`, not `character varying` |
| No `double precision` column | none present |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-14`'s three FK columns are added here "to save a migration" | medium | They are named in **Out of scope**; `W-14` adds them nullable in its own script |
| Statutory flags land in `core` and Payroll semantics leak into the neutral record | medium | **Settled** — they go to `payroll` under `PAY-01`; this migration creates none of them |
| `employee_number` made globally unique, copying `BasicDetails.java`'s `employeeUniqueId` | low | The uniqueness test asserts the same number in two tenants is legal |
| Soft delete makes RLS look like it works when it does not | low | `EmployeeRlsIT` asserts on a **hard** read as `app_user`, not through the service |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only — `migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.employee` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | no money column; the legacy `double` is deliberately not carried |
| Index on `tenant_id` plus lookup columns | three indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `core` only; `hrms` and `payroll` untouched |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 HRMS entities carry no tenant scope | **Fixed here, for this table.** The remaining 38 HRMS entities are `W-67`'s |
| DEBT-018 missing tenant indexes | **Honoured.** All three indexes lead with `tenant_id` |
| `double` for a percentage (`BasicDetails.java:123`) | **Discounted.** The column is not carried into `core` |

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | Where do the statutory eligibility flags live? | **A `payroll`-schema table under `PAY-01`** — settled 2026-09-22. PF, PT, LWF, ESI, EPS and the higher-wages flag sit on Payroll's `employee` row today (`BasicDetails.java:76-140`); they are payroll semantics and do not belong on a neutral record. `core.employee` carries none of them, so an HRMS-only tenant has no payroll columns at all |
| 2 | Is `is_portal_enabled` core or `CORE-19`? | **Kept, on `core.employee`** — settled 2026-09-22, against my recommendation of dropping it. `W-25` reads it alongside entitlement |

**Consequence of decision 1:** six columns leave this migration. If `PAY-01` has not been
specced when this is built, the flags simply do not exist yet — nothing in `core` needs them.
