# Feature: Approval engine — project-manager step knows its project

| Field | Value |
|---|---|
| **Feature ID** | `W-42.2` · ticket #54 · `CORE-*` approval engine, for `HRMS-09` |
| **Promoted to** | `docs/target-state/features/W-42-2-approval-per-project-step.md` |
| **Owner** | devashis |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | none: the defect is in new code (`W-15`), not legacy |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | nothing — `W-15` is on `main` (`a34c14f`). Can be built alongside `W-42.1` |
| **Followed by** | `W-42.3` timesheet submit and approve (needs `W-42.1` and this) |
| **Size** | S |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `core/V145` — one script, no table | 1 |
| Externally testable behaviour | a project-manager step goes to the manager of the project named on that step | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

The founder decided on 2026-10-02 that timesheet approval stays per project, as legacy
does. Each project's manager approves or rejects only that project's hours. In legacy, the
approve endpoint checks `isManagerOfProject` against the project in the path
(`legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/timesheet/TimesheetsController.java:389-404`).

`W-15`'s engine has a `PROJECT_MANAGER` approver kind, but it cannot route to a project's
manager today:

- **The resolver is never told the project.** A step's approver is resolved with the step
  definition's `assignee`, which is `null` for `PROJECT_MANAGER`. The step's own `itemRef`
  is not passed.
  `code/backend/core/src/main/java/com/infinevo/core/approval/ApprovalService.java:144-145`
  (step created), `:289-293` (pre-existing next step activated).
  `CoreApproverResolver.java:63,106-112` hands that `null` to the custom resolver.
- **The seeded timesheet definition is not per item.** `per_item` is `false`, so the step
  carries no `itemRef` at all.
  `code/backend/migration/src/main/resources/db/migration/core/V089__approval_definition.sql:146-160`
- **Nothing stops a tenant saving a project-manager step that can never resolve.**
  Validation checks only that a resolver bean exists.
  `ApprovalDefinitionService.java:147-153`

**The other half of "per project" needs no core change.** One rejection ends the whole
instance (`ApprovalService.java:236-251`, `RejectionEndsInstanceIT`). So `W-42.3` starts
**one instance per project entry**, and rejecting one project leaves the others pending.
There is no unique constraint on an instance's subject (`core/V090__approval_instance.sql:22-23`),
so a resubmitted project can start a fresh instance.

## 2. Scope

**In scope**

- For a `PROJECT_MANAGER` step only: the step's `itemRef` becomes the resolver's `contextRef`, at both resolve sites
- The `TIMESHEET` default definition becomes `per_item: true`, both for new tenants and for existing tenants still on the seeded default
- Validation: a `PROJECT_MANAGER` step must be `per_item: true`
- The `ApproverResolver` Javadoc states what `contextRef` holds for each kind

**Out of scope**

- The `hrms` resolver that maps a project id to `manager_employee_id`; submit; the outcome handler — `W-42.3`
- `core.approval.decide` for managers — `W-40.2` adds it (`W-40-2-core-clock-seams.md:57,116`)
- Any change to rejection, ordering, escalation or delegation
- Other per-item kinds. `PROOF_OF_INVESTMENT` uses `ROLE` per item with `assignee: "hr"` (`V089:102-103`) and must keep `assignee` as its `contextRef`

## 3. Flow

```
W-42.3: approvalService.start(TIMESHEET, SubjectRef("hrms.timesheet_project_entry", entryId),
                              employeeId, List.of(projectId.toString()))
  → definition TIMESHEET: [{PROJECT_MANAGER, per_item: true}]
  → createStepsForIndex: one step, itemRef = projectId
  → createSingleStep: resolve(tenant, employee, PROJECT_MANAGER, contextRef = itemRef)   ← changed
  → CoreApproverResolver → the PROJECT_MANAGER bean (W-42.3) → project.manager_employee_id
```

## 4. Backend changes

All in `code/backend/core/src/main/java/com/infinevo/core/approval/`.

| Layer | File | Change |
|---|---|---|
| Service | `ApprovalService.java:144-145` | `contextRef = stepDef.kind() == PROJECT_MANAGER && itemRef != null ? itemRef : stepDef.assignee()` |
| Service | `ApprovalService.java:289-293` | same rule, with `nextStep.getItemRef()` |
| Service | `ApprovalDefinitionService.java:147-153` | also refuse a `PROJECT_MANAGER` step with `per_item: false`: `400`, message `"Step at index i: PROJECT_MANAGER must be per_item"` |
| Contract | `ApproverResolver.java` | Javadoc only: for `PROJECT_MANAGER`, `contextRef` is the step's `itemRef` (W-42.3: the project id as a UUID string); for `NAMED_EMPLOYEE` and `ROLE`, the step definition's `assignee` |

Put the rule in one private method, used by both sites. Do not change `ApproverResolver`'s
signature: `CoreApproverResolver` and every custom resolver keep compiling.

**API contract:** no new endpoint. `PUT` and `POST` on approval definitions gain the one
`400` above.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V145__timesheet_approval_per_project.sql` | none; replaces a function and updates rows in `core.approval_definition` | n/a, rows keep their `tenant_id` | additive data change; the old value is a single literal |

`V145` is reserved for `W-42.2` (2026-10-02), above `W-42.1`'s `V141`–`V144`. Every
statement names the `core` schema (`migration/README.md:33-50`).

1. `CREATE OR REPLACE FUNCTION core.seed_approval_definitions(p_tenant_id UUID)`: a full
   copy of `V089:34-163`, header included (`SECURITY DEFINER`,
   `SET search_path = pg_catalog, pg_temp`). The one change is `"per_item": true` in the
   `TIMESHEET` block (`:146-160`). Keep the `REVOKE EXECUTE … FROM PUBLIC` after it
   (`V089:165`); leave the trigger function `core.tenant_seed_approval_definitions` (`V089:166-179`) alone. `V089` is the only script that defines this function today, so `V145`
   becomes the one current copy. A later ticket edits `V145`'s list, not `V089`'s.
2. For existing tenants, change only rows still on the untouched seed:

   ```sql
   UPDATE core.approval_definition
      SET steps = jsonb_set(steps, '{0,per_item}', 'true'::jsonb),
          updated_at = CURRENT_TIMESTAMP,
          updated_by = 'V145'
    WHERE flow_type = 'TIMESHEET'
      AND created_by = 'system'
      AND steps = '[{"kind": "PROJECT_MANAGER", "assignee": null, "escalate_after_days": 3, "per_item": false}]'::jsonb;
   ```

   A tenant who edited their timesheet definition keeps it. If it is `per_item: false`,
   the new validation refuses its next save, with a clear message. No timesheet has ever
   been submitted, because the tables arrive in `W-42.1`, so no instance depends on the
   old shape.

- [x] `tenant_id` + RLS: no new table
- [x] Index on `tenant_id` plus lookup columns: none needed. The update runs once, over at most one row per tenant
- [x] No money
- [x] Expand / contract: no schema change. The previous release reads `per_item: true` correctly, because the field already exists (`ApprovalStepDefinition`)

## 7. Tests

All in `code/backend/core/src/test/java/com/infinevo/core/approval/`.

| Type | File | Covers |
|---|---|---|
| Unit | `ApproverResolverTest.java` (existing) | extended: a `PROJECT_MANAGER` per-item step calls the custom resolver with the `itemRef`; a `ROLE` per-item step still gets `assignee` (`"hr"`), not the `itemRef` |
| Integration | `ProjectManagerStepIT.java` (new) | stub `PROJECT_MANAGER` bean maps project A to manager M1 and project B to M2. Start two `TIMESHEET` instances with `itemRefs` A and B; their steps are assigned to M1 and M2. M2 rejects B; A's instance is still `PENDING`, and M1 can approve it. A third instance on the same subject after the rejection is accepted |
| Integration | `ApprovalDefinitionGuardIT.java` (existing) | extended: a `PROJECT_MANAGER` step with `per_item: false` is `400` |
| Integration | `TimesheetDefinitionMigrationIT.java` (new) | after migrate: a new tenant's `TIMESHEET` row has `per_item: true`; a tenant whose row was edited before `V145` keeps the edit |
| Unit | `SevenFlowFitTest.java:196-216` (existing) | flow 7 built with `per_item: true`, asserted |

Reuse `ApprovalTestApp` and `StubApprovalOutcomeHandler` from the same package.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT steps->0->>'per_item' AS per_item, count(*) FROM core.approval_definition
   WHERE flow_type='TIMESHEET' GROUP BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT steps->0->>'per_item' FROM core.approval_definition
   WHERE flow_type='PROOF_OF_INVESTMENT' LIMIT 1;"
cd code/backend && mvn -q verify
node .claude/scripts/check-done.mjs
```

| Check | Expected |
|---|---|
| `TIMESHEET` rows | one line, `per_item = true`, count = number of tenants |
| `PROOF_OF_INVESTMENT` | `true`, unchanged |
| Suite | green, no skips; `RejectionEndsInstanceIT` unchanged and passing |
| `check-done.mjs` | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `itemRef` is passed for every kind and breaks proof of investment, whose `ROLE` step needs `"hr"` | medium; it is the one-line version of the fix | §4 limits the rule to `PROJECT_MANAGER`; `ApproverResolverTest` covers `ROLE` |
| `V145`'s function copy drops another flow's seed | low | copy `V089:34-163` whole; `TimesheetDefinitionMigrationIT` checks a new tenant gets all seven flows |
| Someone "fixes" rejection to be per item instead | low | out of scope in §2; `W-42.3` uses one instance per project |

## 10. Rollback

Nothing is deployed. To undo the data change, run the reverse `jsonb_set` on rows where
`updated_by = 'V145'`. The code change is backward-compatible: a `PROJECT_MANAGER` step
with no `itemRef` behaves as today.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table |
| Flyway only, `ddl-auto` nowhere | `core/V145` |
| `Money`/`BigDecimal` for money | no money |
| Index on `tenant_id` plus lookup columns | no new lookup; existing `idx_approval_definition_lookup` |
| Expand / contract | data only, no schema change |
| No module references another module | `core` only; the project lookup stays in `hrms` behind `ApproverResolver` |

## 12. Gap inventory

No BUG or DEBT entry overlaps. The defect is in `W-15`'s new code, and the spec fixes it
here rather than logging it.

## 13. Decisions — 1 and 3 by the founder 2026-10-02; 2 is the spec's design choice

| # | Question | Answer |
|---|---|---|
| 1 | Who approves a timesheet? | **Each project's manager, for that project only**, as legacy |
| 2 | One instance per timesheet with per-item steps, or one instance per project entry? | **One per project entry.** A rejection then ends only that project's instance, and the engine's rejection rule stays as it is |
| 3 | After a rejection | **The employee edits the same timesheet and resubmits.** `W-42.3` starts a fresh instance for the rejected project only |

## 14. As built (devashish, 2026-10-02)

Where the build differs from, or settles a point left open in, the sections above:

- The rule is one private method, `ApprovalService.resolverContextRef`, used at both resolve sites.
- The new validation sits after the resolver-bean check, so a tenant with no `PROJECT_MANAGER` resolver still sees "requires an ApproverResolver bean registered" first. `ApprovalDefinitionGuardIT` therefore registers a stub resolver to reach the per_item message.
- `ApproverResolverTest` could not exercise the rule: `CoreApproverResolver` never sees an `itemRef`. The three cases (`PROJECT_MANAGER` gets the item; per-item `ROLE` still gets `"hr"`; no item gives `null`) start a flow through `ApprovalService` with a mocked resolver instead.
- `ApprovalTestSchema.apply()` now runs `V145` after `V089` (it is safe to run again), so every approval IT seeds new tenants as a fresh database would. `TimesheetDefinitionMigrationIT` also runs it over rows put back to the old seed, and over one edited by the tenant and one created by them.
- `ProjectManagerStepIT` registers its stub resolver and a `TIMESHEET` outcome handler as a `@TestConfiguration`, not as scanned beans, so the other approval ITs keep their `PROJECT_MANAGER`-less context.
