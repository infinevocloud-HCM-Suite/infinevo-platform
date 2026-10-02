# Feature: Overtime request workflow

| Field | Value |
|---|---|
| **Feature ID** | `W-40.6` · from ticket `W-40` (#51–52) · `HRMS-12` · **closes #51–52** |
| **Promoted to** | `docs/target-state/features/W-40-6-overtime-request-workflow.md` on branch `dev-karma` — **`W-40-6` with hyphens** |
| **Owner** | karma, branch `dev-karma` (from sayeed 2026-10-02) |
| **Apps touched** | `code/backend/hrms` |
| **Related gaps** | DEBT-007 (fixed), DEBT-008 (fixed for these endpoints) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-30 |
| **Blocked by** | `W-40.5` (`submit`, `approve`, `reject`) and `W-40.2` (the `hrms.overtime.request` code and the manager's `core.approval.decide` grant). `W-15` is on `main` (`a34c14f`). The tracker's wait on `W-16` is kept until the founder lifts it |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee asks for overtime, the approvers approve, and the hours reach the pay input ledger | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

An HRMS customer's employees cannot ask for overtime. The administrator can record it
(`W-39.2`, on `main`), but the request-and-approve experience is what HRMS sells
(`D-35`, `01-platform-shape.md:106,233`).

The frozen HRMS has the experience, in a shape that is not carried:

- **Approval by role name.** The controller lower-cases the caller's role names and picks
  "manager", "reporting manager" or "hr" —
  `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/OvertimeRequestController.java:104-118`.
  Two status columns record it (`.../entity/OvertimeRequest.java:43-49`).
- **Approved overtime becomes comp-off, not pay** — `.../service/OvertimeRequestService.java:83-91,97-130`.
  Dropped by `D-28` (`W-39-2-overtime-capture.md:260`).
- **Anyone signed in can read, change or delete any request** — `GET /overtime/all`,
  `PUT /overtime/{id}`, `DELETE /overtime/{id}` carry no check
  (`OvertimeRequestController.java:74-81,146-162`).

What moves across: the employee submits, identified by the login, not by a field in the body
(`OvertimeRequestController.java:35-41`); manager and HR both approve, in either order
(`W-15-1-approval-definition.md:37`). That flow is seeded — `M/core/V089__approval_definition.sql:59-75`.

## 2. Scope

**In scope**

- Submit an overtime request; read my requests
- Start the `OVERTIME` approval flow on submit
- The outcome handler: approved → `OvertimeService.approve`; rejected → `OvertimeService.reject`

**Out of scope**

- The table and its states — `W-40.5`, `core`
- The approve and reject endpoints — `POST /api/v1/approvals/steps/{stepId}/decide`, `W-15`
- HR's list of every request — `GET /api/v1/overtime`, `W-39.2`, already returns them with `status`
- Withdrawing a pending request — waits on the engine's cancel, as `W-40-4-attendance-regularization.md` §2
- `category`, `project`, start and end clock times (`OvertimeRequest.java:28-38`) — not ported;
  see §13
- Screens — Stream F

## 3. Flow

```
[employee] --> POST /api/v1/hrms/overtime-requests --> OvertimeRequestWorkflowService.submit()
                   --> OvertimeService.submit(entry)                     --> core.overtime_request (PENDING, REQUEST)
                   --> ApprovalService.start(OVERTIME, SubjectRef("core.overtime_request", id), employeeId)
[manager], [hr] --> POST /api/v1/approvals/steps/{stepId}/decide   (W-15, unchanged; any order)
                   --> OutcomeDispatcher, after commit --> OvertimeRequestOutcomeHandler
   approved: OvertimeService.approve(subjectId) --> core.pay_input (one OVERTIME row)
   rejected: OvertimeService.reject(subjectId)
[employee] --> GET /api/v1/hrms/overtime-requests/mine --> OvertimeService.list(from, to, myEmployeeId)
```

## 4. Backend changes

All new, under `code/backend/hrms/src/main/java/com/infinevo/hrms/overtime/`.

| Layer | File | Change |
|---|---|---|
| Controller | `OvertimeRequestController.java` | New. `@RequiresModule(PlatformModule.HRMS)` on the class |
| Service / ServiceImpl | `OvertimeRequestWorkflowService.java`, `OvertimeRequestWorkflowServiceImpl.java` | New. Submit and the "mine" read |
| Handler | `OvertimeRequestOutcomeHandler.java` | New. `implements ApprovalOutcomeHandler`, `flowType()` returns `OVERTIME`. Shape and tenant binding from `payroll/.../reimbursement/ReimbursementClaimOutcomeHandler.java:44-62,116-120` |
| DTO | `OvertimeRequestSubmission.java` | New record: `overtimeDate`, `hours`, `remarks` |

No entity and no repository: the row is `core`'s and is reached only through `OvertimeService`.
The response is `core`'s `OvertimeResponse` (`core/.../overtime/OvertimeResponse.java:11-23`).

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/hrms/overtime-requests` | `{overtimeDate, hours, remarks?}` | `201`, the row, `status = PENDING`, `source = REQUEST` | `hrms.overtime.request` |
| `GET` | `/api/v1/hrms/overtime-requests/mine?from=&to=` | span ≤ 93 days | `200`, the caller's rows of every status, newest first | `hrms.overtime.request` |

**Rules**

| Rule | Result |
|---|---|
| The employee is always the caller — `EmployeeService.currentEmployee()`. The body has no employee field | — |
| Caller's login is not linked to an employee | as `W-40.3` §4 |
| Date in the future, hours ≤ 0 or > 24, remarks too long | `400 VALIDATION_FAILED`, from `OvertimeService.submit` (`W-40-5-overtime-request-states.md` §4) |
| No active `OVERTIME` definition (`core/.../approval/ApprovalService.java:98-100`) | the row is not saved; whole call rolls back |
| `submit` and `ApprovalService.start` | one transaction, as `payroll/.../reimbursement/ReimbursementClaimServiceImpl.java:125-140` |

**The handler**

| Event | Does |
|---|---|
| `onApproved` | Bind the tenant from the instance if none is bound; `OvertimeService.approve(instance.getSubjectId())` |
| `onRejected` | Bind likewise; `OvertimeService.reject(instance.getSubjectId())` |

Both `core` methods already return quietly when called a second time
(`W-40-5-overtime-request-states.md` §4), so the handler holds no state of its own. If
`approve` throws, the dispatcher's transaction rolls back and the sweep retries
(`core/.../approval/OutcomeDispatcher.java:86-93`).

The approver's comment stays in the approval step, where the history endpoint reads it
(`ApprovalService.java:366-386`); it is not copied onto the overtime row.

## 5. Frontend changes

None.

## 6. Database changes

None. The permission code is seeded by `W-40.2` (`reference/V122__hrms_request_actions.sql`);
the states by `W-40.5` (`core/V120__overtime_request_states.sql`).

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | creates no table |
| Flyway only, `ddl-auto` nowhere | no script |
| Money as `Money` / `BigDecimal` | `hours` is `BigDecimal`; no amount is accepted |
| Index on `tenant_id` plus lookup columns | no new lookup; "mine" uses `idx_overtime_request_tenant_employee_date` (`M/core/V041__overtime_request.sql:35`) |
| Expand / contract | nothing changes |
| No module references another | `hrms` calls `core` (`OvertimeService`, `ApprovalService`, `ApprovalInstanceRepository`, `EmployeeService`) and `shared`. It holds no JPA mapping of `core.overtime_request` |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/.../overtime/OvertimeRequestOutcomeHandlerTest.java` | approved calls `approve` with the subject id; rejected calls `reject`; the tenant is bound from the instance when none is bound and cleared after |
| Integration | `hrms/.../overtime/OvertimeRequestFlowIT.java` | employee submits 3 hours → row `PENDING`, zero `core.pay_input` rows, two pending steps (manager, HR). One approves → still zero. The other approves → row `APPROVED`, exactly one `OVERTIME` ledger row with `quantity = 3.00`. A second dispatch of the instance leaves one |
| Integration | `hrms/.../overtime/OvertimeRequestRejectIT.java` | one approver rejects → row `REJECTED`, zero ledger rows, the other step closed |
| Integration | `hrms/.../overtime/OvertimeRequestGuardIT.java` | `403` without `hrms.overtime.request`; `403 MODULE_NOT_ENTITLED` for a Payroll-only tenant; a body carrying another employee's id is ignored — the row is the caller's; "mine" never returns a colleague's row |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
cd code/backend && mvn -q verify
node .claude/hooks/check-done.mjs W-40.6
```

| Check | Expected |
|---|---|
| Suite | green, no skips; the four test classes in §7 run |
| `check-done.mjs` | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The HR step goes to the first holder of the `hr` role, not to all of them | known, `W-15` outstanding (`active-work.md` 2026-09-28) | Not this ticket's. A tenant with several HR users reassigns through `core.approval.manage` until the engine changes |
| An hours-only row reaches the pay run with no rate | certain until `W-29` prices it (`W-39-2-overtime-capture.md:243`) | Unchanged from `W-39.2`; nothing here has to change when it is decided |
| The handler is called with no tenant bound (the sweep) | certain | Bind from the instance, as `ReimbursementClaimOutcomeHandler.java:58-62` |
| Someone adds a JPA entity for `core.overtime_request` inside `hrms` "to query it" | medium | §4: `OvertimeService` only. `maven-enforcer` does not catch it; review does |

## 10. Rollback

Revert the application commit. With the handler gone an approved `OVERTIME` instance is
claimed and changes nothing; rows stay `PENDING` and an administrator can cancel them
(`W-40-5-overtime-request-states.md` §4).

## 11. Gap inventory

| ID | Decision |
|---|---|
| DEBT-007 no `/api/v1` | **Fixed** |
| DEBT-008 hand-built responses | **Fixed for these endpoints** — `core`'s `OvertimeResponse` and `ApiErrorResponse` |
| Legacy role-name approval (`OvertimeRequestController.java:104-118`) | **Replaced** by the approval engine |
| Legacy comp-off (`OvertimeRequestService.java:97-130`) | **Dropped by decision** — `D-28` |

## 12. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `code/backend/hrms` | controller, service, handler, DTO, tests |

## 13. Decisions — settled 2026-09-30

| # | Question | Answer |
|---|---|---|
| 1 | Start and end times, or hours | **Date and hours**, the shape `core.overtime_request` already has. The legacy hours were computed from start and end and never stored (`OvertimeRequest.java:80-88`) |
| 2 | `category` and `project` | **Not ported.** `remarks` (255) carries free text. A project link waits for `W-41` and a reason to have it |
| 3 | Who approves | **Reporting manager and HR, any order** — the seeded flow; a tenant changes it through `core.approval_definition.manage` |
| 4 | New table in `hrms` | **None** |
