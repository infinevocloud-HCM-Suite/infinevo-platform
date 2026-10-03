# Feature: Timesheet submit and approve

| Field | Value |
|---|---|
| **Feature ID** | `W-42.3` · ticket #54 · `HRMS-09` |
| **Promoted to** | `docs/target-state/features/W-42-3-timesheet-submit-approve.md` |
| **Owner** | devashis |
| **Apps touched** | `code/backend/hrms` |
| **Related gaps** | BUG-002 (no new table), DEBT-007 (fixed), DEBT-008 (fixed), DEBT-022 (fixed); legacy approve takes any status (`TimesheetsController.java:389-411`), not carried (§12) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | `W-42.1` (the tables) and `W-42.2` (the project-manager step). **Before merge:** `W-40.2`, which grants `core.approval.decide` to `manager` and `hr`. Without it only an administrator can decide (`W-40-2-core-clock-seams.md:41-45`). The tests grant the action themselves, so building does not wait |
| **Followed by** | `W-42.4` review lists (project manager, reporting manager, HR) |
| **Size** | M |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | none: `status` and `rejection_reason` come from `W-42.1`, the definition from `W-42.2`, the actions from `V020` | 1 |
| Externally testable behaviour | a submitted week reaches each project's approver, and their decision lands on that project and rolls up to the week | 1 |
| Frontend area | none (`W-48`) | 1 |

The founder split the review lists out to `W-42.4` on 2026-10-02.

---

## 1. Problem

Legacy submits a week, notifies each project's manager, and lets that manager approve or
reject their own project. The week's status is worked out from its projects. All of that is
being ported. These faults must not come across:

- **Any status is accepted on approve.** The endpoint takes a `status` query parameter and
  never checks the current one, so a manager can set a project back to `DRAFT`, or flip
  `APPROVED` to `REJECTED`.
  `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/timesheet/TimesheetsController.java:389-411`,
  `serviceimpl/timeshhet/TimesheetServiceImpl.java:451-478`
- **A manager can approve their own hours.** The only check is "manager of the project"
  (`TimesheetsController.java:401`).
- **Notifications are sent inside the transaction**, by hand-built email
  (`TimesheetServiceImpl.java:786-857`, `:858+`, `:666-700`).
- **Approval is not recorded anywhere but the status column.** There is no history of who
  decided or when.

Kept from legacy:
- Approval is per project.
- The roll-up rule (`TimesheetServiceImpl.java:492-515`).
- Editing a rejected timesheet changes only its rejected projects and sends them straight
  back for approval (`:627-648`).

## 2. Scope

**In scope**

- Submit a draft week; each project entry goes for approval on its own
- The `PROJECT_MANAGER` resolver for `hrms`
- The `TIMESHEET` outcome handler: write the decision onto the project entry, roll up the week
- Resubmit: replace the rejected projects of a `REJECTED` week and send only those back
- The approver reads the one project entry they are asked to decide
- Notifications: `APPROVAL_PENDING` to each approver, `APPROVAL_DECIDED` to the employee

**Out of scope**

- Lists: the project manager's, the reporting manager's and HR's — `W-42.4`
- Approve and reject endpoints: decisions go through `core`'s existing `POST /api/v1/approvals/steps/{stepId}/decide` (`ApprovalController.java:74-75`)
- Withdrawing a submitted week — founder decision 5: no
- Submit-all (`TimesheetsController.java:245`) — not ported; the screen submits one week at a time
- Granting `core.approval.decide` to anyone — `W-40.2`; founder decision 1
- Reminders — `W-43`

## 3. Flow

```
[employee] PUT /timesheets/{id}/submit
   → TimesheetSubmitService: DRAFT → SUBMITTED, every project entry SUBMITTED, submitted_at
   → per project entry: approvalService.start(TIMESHEET,
         SubjectRef("hrms.timesheet_project_entry", entryId), employeeId, List.of(projectId))
         → core → TimesheetProjectManagerResolver (hrms) → approver or none
   → after commit: APPROVAL_PENDING to each assigned approver
[approver] GET /api/v1/approvals/pending (core) → itemId = project entry id
[approver] GET /timesheets/project-entries/{entryId}         (hrms, this ticket)
[approver] POST /api/v1/approvals/steps/{stepId}/decide       (core)
   → OutcomeDispatcher → TimesheetOutcomeHandler (hrms)
   → entry APPROVED / REJECTED (+ reason) → week rolled up → APPROVAL_DECIDED to employee
[employee] PUT /timesheets/{id} on a REJECTED week → rejected entries replaced → SUBMITTED → new instance each
```

## 4. Backend changes

All under `code/backend/hrms/src/main/java/com/infinevo/hrms/timesheet/`.

| Layer | File | Change |
|---|---|---|
| Resolver | `TimesheetProjectManagerResolver.java` | new; `implements ApproverResolver`, `kind()` = `PROJECT_MANAGER` (`core/.../approval/ApproverResolver.java:10-13`) |
| Outcome | `TimesheetOutcomeHandler.java` | new; `implements ApprovalOutcomeHandler`, `flowType()` = `TIMESHEET`; copy the tenant binding and idempotence of `payroll/.../reimbursement/ReimbursementClaimOutcomeHandler.java:24-60` |
| Service | `TimesheetSubmitService` + `Impl` | new: submit, resubmit, roll-up |
| Service | `TimesheetServiceImpl` (`W-42.1`) | `PUT /{id}` also accepts `REJECTED` and delegates to resubmit |
| Controller | `TimesheetController.java` (`W-42.1`) | two endpoints added, below |
| DTO | `TimesheetProjectEntryResponse` | new: week, employee id, project id, tasks and days, status, rejection reason |

**The resolver.** `contextRef` is the project id (`W-42.2` §4).
1. Read the project with `ProjectRepository.findByIdAndTenantIdAndDeletedFalse` (`hrms/.../project/ProjectRepository.java:20`).
2. If there is no project or no `manager_employee_id`, return empty. The step is then unassigned, and anyone holding `core.approval.manage` decides it (`ApprovalService.java:184-191`). This is founder decision 2.
3. If the manager is the employee themself, return their reporting manager: `ReportingLineService.chainAbove(employeeId, today)` first entry, or empty (`core/.../org/ReportingLineService.java:101`). This is founder decision 3.
4. Otherwise return the manager.

**Submit** (`TimesheetsController.java:206-243`, `TimesheetServiceImpl.java:348-365`, `:370-381`):
1. The week must be `DRAFT` and every entry `DRAFT`; otherwise `409`.
2. Set the week and every entry to `SUBMITTED`, and set `submitted_at`.
3. Start one approval instance per project entry, as in §3. `approvalService.start` takes the subject, the employee and `List.of(projectId.toString())` as `itemRefs` (`ApprovalService.java:93`).
4. Everything happens in one transaction. If any `start` throws, for example because no `TIMESHEET` definition is in force (`ApprovalService.java:101-103`), the week stays `DRAFT` and the response is `409` with that message.
5. After commit, compose `APPROVAL_PENDING` for each step that has an assignee. Use `employee_name` = the approver, `requester_name` = the employee, `request_title` = `"Timesheet <project name>, week of <weekStartDate>"`. Wrap it in try/catch and log, as `payroll/.../proof/ProofOutcomeHandler.java:285-295` does. A failed mail must never undo a submit.

**Outcome handler.**
1. Find the entry by `instance.getSubjectId()`.
2. **Ignore a stale instance.** Do nothing if the entry is not `SUBMITTED`, or if this is not the newest instance for the subject (`ApprovalInstanceRepository.findByTenantIdAndSubjectTableAndSubjectId`, `ApprovalInstanceRepository.java:19`).
3. On approve: `APPROVED`, and `rejection_reason` cleared.
4. On reject: `REJECTED`, and `rejection_reason` = the rejecting step's `comment` (`StepDecision`, `core/.../approval/StepDecision.java`), cut to 1000 characters.
5. Roll up the week.
6. Compose `APPROVAL_DECIDED` to the employee: `request_title` as above, `decision` = `approved` or `rejected`. Same try/catch.

**Roll-up**, ported as is from `TimesheetServiceImpl.java:492-515`: any entry `REJECTED`
gives `REJECTED`; otherwise any `SUBMITTED` gives `SUBMITTED`; otherwise all `APPROVED`
gives `APPROVED`.

**Resubmit** (`TimesheetServiceImpl.java:537-548`, `:627-700`):
1. `PUT /{id}` on a `REJECTED` week: the body carries only the rejected projects.
2. A `projectId` in the body that is not a `REJECTED` entry of this week is `409`. Legacy silently ignored it.
3. A `REJECTED` entry missing from the body stays `REJECTED`.
4. Each project in the body has its tasks and days replaced, with `W-42.1`'s validation, including 24 hours a day across the whole week.
5. Those entries become `SUBMITTED`, `rejection_reason` is cleared, and a new instance starts for each. The old instance is left as history.
6. Roll up, then send `APPROVAL_PENDING` as for submit.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| PUT | `/api/v1/hrms/timesheets/{id}/submit` | — | `200`, the week as `SUBMITTED`; `409` unless `DRAFT` | `hrms.timesheet.submit`, owner only (`404` otherwise) |
| PUT | `/api/v1/hrms/timesheets/{id}` | `W-42.1`'s body, rejected projects only | `200`; now also allowed on `REJECTED`, see Resubmit | `hrms.timesheet.submit`, owner only |
| GET | `/api/v1/hrms/timesheets/project-entries/{entryId}` | — | `TimesheetProjectEntryResponse` | `hrms.timesheet.approve`, and the caller is the assignee of a step on any instance for this entry; else `404` |

Approve and reject are `core`'s `decide`. `W-15` rules hold:
- The caller must be the step's assignee (`ApprovalService.java:192-197`).
- A decided step cannot be decided again. That closes the legacy any-status fault.

All three endpoints carry `@RequiresModule(PlatformModule.HRMS)`.

**Who can decide: founder decision 1, "leave it".** A project manager whose only role is
`employee` is still assigned the step, but `core.approval.decide` refuses them
(`ApprovalController.java:75`), even after `W-40.2`. An administrator moves the step with
`POST /api/v1/approvals/{instanceId}/reassign` (`core.approval.manage`,
`ApprovalController.java:62-63`). This is accepted, not a defect. `W-48` should say so on
screen.

## 5. Frontend changes

None (`W-48`). The approver's inbox is `core`'s `GET /api/v1/approvals/pending`. Each row's
`itemId` is the project entry id (`ApprovalStepResponse.java:53`).

## 6. Database changes

**Creates no table and no migration.**
- The columns already exist from `W-42.1`: `hrms.timesheet.status`, `submitted_at`, and `hrms.timesheet_project_entry.status`, `rejection_reason`.
- The definition comes from `W-42.2` (`core/V145`).
- `hrms.timesheet.approve` is granted to `hr` and `manager` (`core/V135__hrms_project_seed_roles.sql:65,79`).

- [x] `tenant_id` + RLS: no new table
- [x] Index: the outcome lookup is by primary key; instance lookup uses `idx_approval_instance_lookup` (`core/V090__approval_instance.sql:22`)
- [x] No money
- [x] Expand / contract: no schema change

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `TimesheetRollupTest.java` | every row of the roll-up: all approved; one rejected among approved; one submitted, one approved; resubmitted after rejection |
| Unit | `TimesheetProjectManagerResolverTest.java` | manager returned; no manager gives empty; deleted project gives empty; manager is the employee gives the reporting manager; that, with no reporting line, gives empty |
| Integration | `TimesheetSubmitIT.java` | a two-project week gives two instances, each step on its project's manager; second submit is `409`; with no `TIMESHEET` definition the week stays `DRAFT`; another employee's id is `404` |
| Integration | `TimesheetDecisionIT.java` | M1 approves A, M2 rejects B with a comment: A `APPROVED`, B `REJECTED` with that reason, week `REJECTED`. Resubmit B: only B changes, B `SUBMITTED`, week `SUBMITTED`, a new instance for B, the old one kept. M2 approves: week `APPROVED`. A body naming A on resubmit is `409`. Deciding an already-decided step fails |
| Integration | `TimesheetStaleOutcomeIT.java` | an outcome from an older instance after a resubmit changes nothing |
| Integration | `TimesheetProjectEntryReadIT.java` | the assignee reads the entry; another manager gets `404`; an employee gets `403` |
| Integration | `TimesheetNotificationIT.java` | submit writes one `APPROVAL_PENDING` per assigned approver; a decision writes one `APPROVAL_DECIDED` to the employee; a failing compose still leaves the week `SUBMITTED` |

Each test that decides grants `core.approval.decide` to `manager` in its own setup until
`W-40.2` is on `main`. Then remove that setup and keep the tests green. Reuse `HrmsTestApp`.
`hrms` tests need `core`'s approval beans: copy what `payroll`'s
`ProofApprovalStarterTest` loads, and do not share it across modules.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
cd code/backend && mvn -q verify
node .claude/scripts/check-done.mjs
# after a TimesheetDecisionIT-shaped run against the local stack:
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT subject_table, status, count(*) FROM core.approval_instance
   WHERE flow_type='TIMESHEET' GROUP BY 1,2 ORDER BY 1,2;"
```

| Check | Expected |
|---|---|
| Suite | green, no skips |
| `check-done.mjs` | 5/5 |
| Approval rows | `subject_table = hrms.timesheet_project_entry` only; one row per submitted or resubmitted project |
| App boots | `ApprovalDefinitionService` finds a `PROJECT_MANAGER` resolver; saving a `TIMESHEET` definition no longer fails on "requires an ApproverResolver bean" (`ApprovalDefinitionService.java:147-153`) |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| One instance per timesheet instead of per project, so one rejection rejects everything | medium; it looks simpler | §3, `W-42.2` decision 2; `TimesheetDecisionIT` |
| A late outcome from an old instance overwrites a resubmitted project | medium | stale check in the handler; `TimesheetStaleOutcomeIT` |
| A failed email rolls back a submit | medium; `W-20.1` review item 3 | compose after commit, in try/catch |
| A project manager with only the `employee` role is stuck | certain, by founder decision 1 | administrator reassigns; `W-48` shows it |
| Merged before `W-40.2`, so only administrators can decide | medium | the header blocks the merge on `W-40.2` |

## 10. Rollback

Nothing is deployed and there is no migration. Removing the beans leaves submitted weeks
`SUBMITTED` with open instances, which an administrator can decide.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table |
| Flyway only, `ddl-auto` nowhere | no migration |
| `Money`/`BigDecimal` for money | no money |
| Index on `tenant_id` plus lookup columns | no new lookup; uses `W-42.1`'s and `core`'s |
| Expand / contract | no schema change |
| No module references another module | `hrms` uses `core` (`ApprovalService`, `ApprovalInstanceRepository`, `ReportingLineService`, `NotificationService`, `EmployeeService`) and `shared` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 | no new table; `W-42.1`'s RLS covers every row touched |
| DEBT-007 | **Fixed**: `/api/v1` |
| DEBT-008 | **Fixed**: one envelope |
| DEBT-022 | **Fixed**: every finder takes `tenantId` |
| Proposed: legacy approve takes any status | **Not carried**: decisions go through `core`, which refuses a second decision |

## 13. Decisions — founder, 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | A project manager with only the `employee` role cannot decide | **Leave it.** Only `manager` and `hr` (after `W-40.2`) and administrators decide; an administrator reassigns the rest |
| 2 | Project with no manager | **Administrator decides.** The step is unassigned |
| 3 | Employee manages their own project | **Their reporting manager approves** |
| 4 | Review lists | **Split to `W-42.4`** |
| 5 | Withdraw after submit | **No** |
| 6 | Fix after rejection | **Edit the same week; only the rejected projects go back** (from `W-42` decision 7) |
| 7 | Submit-all | **Not ported** (spec's choice) |

## 14. As built (devashish, 2026-10-02)

Where the build differs from, or settles a point left open in, the sections above:

- The save rules moved out of `TimesheetServiceImpl` into `TimesheetValidator`, so that the draft save and the resubmit meet the same rules. `TimesheetRules.validate` takes the hours already held on the lines a resubmit does not replace, so 24 hours a day still counts the whole week.
- Resubmit is `TimesheetSubmitService.resubmit`. `TimesheetServiceImpl.replace` hands a `REJECTED` week to it; the body must carry the week's own `week_start_date`. A project that is not a rejected line of the week is `409`, checked after the shape of the body and before the database rules.
- `APPROVAL_PENDING` is composed after the submit commits, in a transaction of its own (`REQUIRES_NEW`), because a callback after commit has no live transaction to write in. Every name and title is read before the commit: the callback runs while the submit's connection is still held, and reading there starved the tests' two-connection pool. `APPROVAL_DECIDED` is composed inside the outcome handler's own transaction, as `ProofOutcomeHandler` does. Both are wrapped and logged.
- The rejection reason is the first rejecting decision's comment that is not blank, cut to 1000 characters; a rejection with no comment leaves the reason empty. The cut is a guard only: core already holds a step comment to 1000, so a longer one is refused there.
- `TimesheetRow` is public: a Hibernate proxy of a lazy parent (a line's timesheet) calls its inherited getters by reflection, and a package-private declaring class refuses that.
- The outcome handler binds the tenant when none is bound, as the spec says, but it cannot read the instance before that: core's dispatcher binds it first in every real path, so no test calls it unbound.
- The outcome handler binds the tenant when none is bound, as the spec says, but it cannot read the instance before that; core's dispatcher binds it first in every real path, so no test calls it unbound.
- The outcome is ignored when the line is not `SUBMITTED` or when its instance is not the newest for the line (newest by `created_at`, then `started_at`).
- The approver's read answers `404` unless the caller is the assignee of a step on any instance for the line, so an unassigned step, which an administrator decides, is not readable here.
- `HrmsTestApp` now loads core's real approval beans (`core.approval` scanned, its entities and repositories), the way `PayrollTestApp` does, with mocks for `EmployeeRepository`, `ReportingLineService` and `NotificationService`; `HrmsProjectTestSchema` applies `V089`–`V092` and `V145`. The decisions in the tests go through the real `ApprovalService.decide`, and one through the real endpoint with `core.approval.decide` granted to the test login. **That grant is the part to delete when `W-40.2` is on `main`.**
- `PUT /{id}/submit` of a week that has no `TIMESHEET` definition answers `409` with core's message ("No active approval definition found for flow TIMESHEET").
