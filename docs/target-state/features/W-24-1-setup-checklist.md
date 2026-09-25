# Feature: Module-aware setup checklist

| Field | Value |
|---|---|
| **Feature ID** | `W-24.1` · from ticket #28 · `CORE-17` |
| **Promoted to** | `docs/target-state/features/W-24-1-setup-checklist.md` on branch `W-24-1-setup-checklist` — **`W-24-1` with hyphens**, never `W-24.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-12.1` — the checklist is assembled from the tenant's modules |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 rows 19–22 (§2 `/setup-checklist` row, §3 `SetupStepChecker` seam) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one script, one table — `core.tenant_setup_step` | 1 |
| Externally testable behaviour | an HRMS-only tenant's checklist contains no payroll step, and completing a step is detected rather than declared | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

The checklist is nine boolean columns on one row, and seven of the nine are payroll-only.

`OrgSetupSteps.java:15-23` holds exactly nine flags:

| Step | Applies to |
|---|---|
| Work location | both |
| Employee | both |
| Pay schedule | payroll |
| Prior payroll | payroll |
| Organisation tax | payroll |
| Salary components | payroll |
| EPF | payroll |
| ESI | payroll |
| Professional tax | payroll |

Adding a step is a migration and a code change. Removing one for a customer who does not need
it is impossible. An HRMS-only tenant on this model would be shown seven steps it can never
complete, and its onboarding would never reach 100%.

`09-build-order.md:211` names it precisely: *"assembled from modules, not a fixed nine-step
list ... a HRMS-only tenant never sees a provident fund step."*

There is a second, quieter problem. The flags are **declared**, not detected: something sets
`employee = true`, and nothing checks that an employee exists. A flag can be true with no
data behind it.

## 2. Scope

**In scope**

- `core.tenant_setup_step` — one row per applicable step per tenant, created from the module set
- A step catalogue in code, each step declaring the module it belongs to
- **Completion detected by a check, not by a flag someone sets**
- Progress as a fraction of applicable steps
- Re-assembly when a module is added, so an upgrade adds the steps it needs

**Out of scope**

- Invitations — `W-24.2`
- The screens — a frontend ticket consumes this
- The steps' own features. "Pay schedule" is `W-28`; this ticket only knows whether one exists
- Blocking anything until setup completes. See decision 2

## 3. Flow

```
[W-12.1 tenant created with modules]
  --> [SetupChecklistService.assemble(tenant)] --> rows for applicable steps only

[anyone] --> GET /api/v1/setup-checklist
  --> each step's completion checker runs --> complete / incomplete + progress

[W-12.1 module added] --> assemble again --> new steps appear, existing keep their state
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../setup/SetupChecklistController.java` | new |
| Service | `core/.../setup/SetupChecklistService.java` | new |
| Catalogue | `core/.../setup/SetupStepCatalogue.java` | new — the steps, each with its module and checker |
| Contract | `core/.../setup/SetupStepChecker.java` | new — the interface a step's owner implements |
| Entity | `core/.../setup/TenantSetupStep.java` | new, `@Table(schema="core")` |
| Repository | `core/.../setup/TenantSetupStepRepository.java` | new |

**The catalogue is code, not rows** — the same reasoning as `W-12.3`'s navigation catalogue. A
step exists because a feature exists, and a table would let the two disagree.

**`SetupStepChecker` is how detection stays honest.** The step's owning ticket implements it:
`W-14.1` answers whether a work location exists, `W-13.1` whether an employee does, `W-28`
whether a pay schedule does. `core` does not reach into `payroll` to look — the module
registers its checker.

**`SetupStepChecker` bean contract** (`12-core-contracts.md` §3, "Setup step"). Each module
registers one Spring bean per step; `core` collects them by type, never by name:

| Method | Returns | Meaning |
|---|---|---|
| `code()` | `String` | the `step_code` stored on the row, e.g. `WORK_LOCATION`, `PAY_SCHEDULE` |
| `module()` | `PlatformModule` or `null` | the module the step belongs to; `null` means core, applies to every tenant. Matches the `module` column |
| `isComplete(UUID tenantId)` | `boolean` | an existence query under the caller's tenant context; must be side-effect free |

`SetupStepCatalogue` holds only the ordering and display metadata; a step with no registered
checker is a build error, asserted by `SetupChecklistServiceTest`. `module()` is filtered
against the tenant's entitlement (`W-12.2`), which is how an HRMS-only tenant never sees a
payroll checker run.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/setup-checklist` | — | steps with completion and progress | `@RequiresAction("core.tenant.read")` |
| POST | `/api/v1/setup-checklist/{stepCode}/skip` | reason | `200` | `@RequiresAction("core.tenant.manage")` |

Every `core` endpoint must carry `@RequiresAction` or `EndpointGuardCoverageTest` fails
(`12-core-contracts.md` §2). Both codes already exist in the catalogue —
`reference/V020__action.sql:45-46`.

There is no endpoint to mark a step complete. Completion is observed; only skipping is a
choice, and it is recorded with a reason.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__tenant_setup_step.sql` | `core.tenant_setup_step` | yes | additive only |

Version number assigned when the branch is cut — `migration/README.md:17-31`.

Columns: `id uuid` · `tenant_id uuid NOT NULL` · `step_code varchar(64) NOT NULL` ·
`module varchar(16) NULL` — null means core, applies to every tenant ·
`display_order int NOT NULL` · `is_skipped boolean NOT NULL DEFAULT false` ·
`skip_reason varchar(500) NULL` · `completed_at timestamptz NULL` ·
`first_seen_at timestamptz NOT NULL` · four audit columns.

- [x] `tenant_id` present, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, step_code)` unique, `(tenant_id, display_order)`
- [x] Money columns — none
- [x] Expand / contract — new table only

**`completed_at` is a cache of the checker's answer, not the source.** It is refreshed when
the checklist is read, so a step that becomes incomplete — the only work location is deleted —
reverts. Nine booleans that can only move one way is how the frozen model lies.

**One row per step, not nine columns.** Adding a step becomes a code change with no migration,
which is the whole point.

RLS and the `tenant_isolation` policy in the exact `CASE` form, same script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../setup/SetupChecklistServiceTest.java` | an HRMS-only tenant gets no payroll step; a both-modules tenant gets all; progress counts only applicable steps |
| Unit | `core/.../setup/SetupStepDetectionTest.java` | a step completes when its checker says so, and reverts when the underlying data goes |
| Integration | `core/.../setup/ModuleUpgradeIT.java` | adding Payroll to an HRMS-only tenant adds the payroll steps and leaves completed ones alone |
| Integration | `core/.../setup/SetupChecklistRlsIT.java` | tenant A cannot read tenant B's checklist |
| Unit | `core/.../setup/SetupStepCheckerRegistryTest.java` | a checker with `module()` = `PAYROLL` is not invoked for an HRMS-only tenant; a catalogue step with no registered checker fails fast |
| Integration | `core/.../setup/SetupChecklistGuardIT.java` | `GET` without `core.tenant.read` is `403`; `skip` with `core.tenant.read` only is `403`; with `core.tenant.manage` is `200` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`SetupChecklistServiceTest`'s first case is the build order's acceptance criterion — no
provident fund step for an HRMS-only tenant — asserted directly.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
bash infra/docker/seed/seed.sh
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.tenant_setup_step'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT t.name, count(*) FILTER (WHERE s.module='PAYROLL') AS payroll_steps
     FROM core.tenant t JOIN core.tenant_setup_step s ON s.tenant_id = t.tenant_id
    GROUP BY 1 ORDER BY 1;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS | `t` |
| Acme (Payroll only) | payroll steps present |
| A hypothetical HRMS-only tenant | `payroll_steps = 0` |
| Suite | green, no skips |

The seeded tenants are Payroll-only and both-modules, so the HRMS-only case is proved by the
unit test rather than the seed — worth knowing when reading the output.

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Completion becomes a flag someone sets, as today | **medium — it is much easier to write** | No complete endpoint; `SetupStepDetectionTest` asserts reversion |
| `core` reaches into `payroll` to check a payroll step | medium | The module registers a `SetupStepChecker`; `maven-enforcer` would reject the alternative |
| A step is added and every existing tenant shows incomplete overnight | medium | `first_seen_at` per row, and progress can report new steps separately — see decision 1 |
| Running every checker on each read is slow | low | Checkers are existence queries on indexed columns; the cached `completed_at` short-circuits the common case |
| Skipping is used to hide an unfinished setup | low | Skips require a reason and are visible in the response, not silent |

## 10. Rollback

Nothing is deployed. The script is additive and forward-only —
`migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.tenant_setup_step` has both, same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | `core` only; module steps register a checker through a `core` interface |

## 12. Gap inventory

| ID | Decision |
|---|---|
| Nine fixed boolean flags (`OrgSetupSteps.java:15-23`) | **Fixed.** Rows assembled from the module set |
| Seven of nine steps are payroll-only, shown to everyone | **Fixed.** Steps carry their module |
| Completion declared, never verified | **Fixed.** Detected by a checker |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **What happens to existing tenants when a step is added later?** **Recommend** the step appears as incomplete but is excluded from the progress fraction until the tenant next touches setup — otherwise a release drops every customer from 100% to 90% overnight with no explanation.
2. **Does incomplete setup block anything?** **Recommend** no — the checklist guides, it does not gate. A tenant that has not set up professional tax should still be able to add employees, and blocking is how onboarding stalls with a support ticket.
