# Feature: Attendance regularization request

| Field | Value |
|---|---|
| **Feature ID** | `W-40.4` · from ticket `W-40` (#51–52) · `HRMS-03`, `HRMS-12` |
| **Promoted to** | `docs/target-state/features/W-40-4-attendance-regularization.md` on branch `dev-sayeed` — **`W-40-4` with hyphens** |
| **Owner** | sayeed, branch `dev-sayeed` |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-018 (honoured) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-30 |
| **Blocked by** | `W-40.3` (the sessions it corrects), `W-40.2` (the manager's `core.approval.decide` grant), `W-40.1` (the limits). `W-15` is on `main` (`a34c14f`). The tracker's wait on `W-16` is kept until the founder lifts it |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | 1 script, one table — `hrms.attendance_regularization`, plus one nullable column on `hrms.clock_session` | 1 |
| Externally testable behaviour | an employee asks for a day's clock times to be corrected, the manager approves, and the day's attendance changes | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

An employee who forgets to clock out, or could not clock in, has no way to ask for the day to
be corrected. **There is nothing to port.**

- No request entity, controller or screen exists in either backend. "Regulari" matches only the
  settings: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leaveAndAttedance/attendance/AttendancePreference.java:51-72`
  and `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/leaveAttendence/attendence.js:435-500`.
- `W-15` already says so and designed the approval shape: one step, the reporting manager
  (`W-15-1-approval-definition.md:40,72`). That flow is seeded for every tenant and has no
  consumer — `M/core/V089__approval_definition.sql:112-127`.
- A flow with no handler is claimed silently (`core/.../approval/OutcomeDispatcher.java:119-129`),
  so today an approved `REGULARIZATION` instance changes nothing.

## 2. Scope

**In scope**

- `hrms.attendance_regularization`: one row per request
- Submit a request; read my requests; HR reads all
- Start the `REGULARIZATION` approval flow on submit
- The outcome handler: on approval, replace the day's sessions with the requested times and
  re-derive the day; on rejection, mark the request rejected
- The three limits from `W-40.1`

**Out of scope**

- The approve and reject endpoints — they exist: `POST /api/v1/approvals/steps/{stepId}/decide`
  (`core/.../approval/ApprovalController.java:43-51`)
- Withdrawing a pending request. The engine cannot cancel an instance yet (deferred to
  `W-16.3`, `active-work.md` 2026-09-28 `W-15` entry)
- Correcting a day an administrator entered. The administrator's value stands
  (`W-40-2-core-clock-seams.md` §4)
- More than one corrected session per request. One request carries one in-time and one out-time
- Screens — Stream F

## 3. Flow

```
[employee] --> POST /api/v1/hrms/attendance/regularizations --> RegularizationService.submit()
                   --> hrms.attendance_regularization (PENDING)
                   --> ApprovalService.start(REGULARIZATION, SubjectRef("hrms.attendance_regularization", id), employeeId)
[manager]  --> POST /api/v1/approvals/steps/{stepId}/decide   (W-15, unchanged)
                   --> OutcomeDispatcher, after commit --> RegularizationOutcomeHandler
   approved: void the date's sessions --> insert one session (origin REGULARIZATION)
             --> ClockDayService.rederive --> AttendanceService.recordFromClock --> core.attendance
             --> request APPROVED
   rejected: request REJECTED, the manager's comment kept
```

## 4. Backend changes

All new, under `code/backend/hrms/src/main/java/com/infinevo/hrms/attendance/`.

| Layer | File | Change |
|---|---|---|
| Controller | `RegularizationController.java` | New. `@RequiresModule(PlatformModule.HRMS)` on the class |
| Service / ServiceImpl | `RegularizationService.java`, `RegularizationServiceImpl.java` | New. Submit, reads, the rules below |
| Handler | `RegularizationOutcomeHandler.java` | New. `implements ApprovalOutcomeHandler`, `flowType()` returns `REGULARIZATION`. Shape and tenant binding copied from `payroll/.../reimbursement/ReimbursementClaimOutcomeHandler.java:44-62,116-120` |
| Entity | `AttendanceRegularization.java` | New. `@Audited`, `@Table(schema = "hrms")` |
| Entity | `ClockSession.java` (`W-40.3`) | Add `regularizationId`; add `void(reason)` |
| Repository | `AttendanceRegularizationRepository.java` | New. Every finder takes `tenantId` |
| DTO | `RegularizationRequest.java`, `RegularizationResponse.java` | New records |
| Enumeration | `RegularizationStatus.java` | `PENDING`, `APPROVED`, `REJECTED` |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/hrms/attendance/regularizations` | `{date, inAt, outAt, reason}` — `inAt`, `outAt` are ISO instants with offset | `201`, the request, `status = PENDING`, `approvalInstanceId` | `hrms.attendance.mark` |
| `GET` | `/api/v1/hrms/attendance/regularizations/mine?from=&to=` | span ≤ 93 days | `200`, the caller's requests, newest first | `core.attendance.read_own` |
| `GET` | `/api/v1/hrms/attendance/regularizations?from=&to=&status=&employeeId=` | span ≤ 93 days; filters optional | `200`, the tenant's requests | `core.attendance.read` |

`hrms.attendance.mark` already reads "Clock in and out, and request a regularisation"
(`M/reference/V020__action.sql:89`). No new code.

**Rules at submit**

| Rule | Refusal |
|---|---|
| Caller's login is not linked to an employee | as `W-40.3` §4 |
| `date` after `TenantClock.today()` | `400 VALIDATION_FAILED` |
| `inAt` ≥ `outAt`, or more than 24 hours apart, or `outAt` in the future | `400 VALIDATION_FAILED` |
| `TenantClock.dateOf(inAt)` is not `date` | `400 VALIDATION_FAILED` |
| `reason` blank or over 500 characters | `400 VALIDATION_FAILED` |
| `regularizationWindowDays` is set and `date` is older than that many days before today | `400 VALIDATION_FAILED` |
| `maxRegularizationsPerMonth` is set and the employee already has that many `PENDING` or `APPROVED` requests whose `date` is in the same calendar month | `400 VALIDATION_FAILED` |
| `allowRegularizationWithoutSession` is false and the date has no session at all | `400 VALIDATION_FAILED` |
| A `PENDING` request already exists for this employee and date | `409 CONFLICT` |
| `core.attendance` for the date has `source = ADMIN` (`AttendanceQuery.days`, `core/.../attendance/AttendanceQuery.java:21`) | `409 CONFLICT` — "set by an administrator" |
| No active `REGULARIZATION` definition (`ApprovalService.java:98-100`) | the request is not saved; whole call rolls back |

The request row and the approval instance are one transaction, as
`payroll/.../reimbursement/ReimbursementClaimServiceImpl.java:125-140`.

**The handler**

| Event | Steps, one transaction |
|---|---|
| `onApproved` | 1. Load the request by `instance.getSubjectId()`. If it is not `PENDING`, return — the dispatcher may call twice. 2. Void every non-voided session of that employee and date, open ones included (`void_reason = REGULARIZED`). 3. Insert one session: `clock_in_at = inAt`, `clock_out_at = outAt`, `origin = REGULARIZATION`, `regularization_id` set. 4. `ClockDayService.rederive(employeeId, date)`. 5. Request `APPROVED`, `decided_at`, `decided_by` and `decision_comment` from the last `StepDecision` |
| `onRejected` | If not `PENDING`, return. Request `REJECTED`, same three fields. Sessions untouched |

If the day turned `ADMIN` between submit and approval, steps 2–3 still run and step 4 writes
nothing — the administrator's value stands and the sessions are still corrected for the record.
If the handler throws, the dispatcher's transaction rolls back and the sweep retries it
(`OutcomeDispatcher.java:86-93`).

**Decision — self-approval.** An employee with no reporting manager gets an unassigned step,
which a holder of `core.approval.manage` decides (`ApprovalService.java:181-188`). A manager's
own request goes to their manager. Nothing here blocks a person approving their own request
when the routing allows it — the standing founder decision (`active-work.md`, "self-approval is
allowed").

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `hrms/V119__attendance_regularization.sql` — reserved for `W-40.4`, 2026-09-30 | creates `hrms.attendance_regularization`; adds `hrms.clock_session.regularization_id` | yes | forward-only; a new table and a nullable column |

```sql
CREATE TABLE hrms.attendance_regularization (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id          UUID NOT NULL REFERENCES core.employee(id),
    attendance_date      DATE NOT NULL,
    requested_in_at      TIMESTAMPTZ NOT NULL,
    requested_out_at     TIMESTAMPTZ NOT NULL,
    reason               VARCHAR(500) NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    approval_instance_id UUID REFERENCES core.approval_instance(id),
    decided_at           TIMESTAMPTZ,
    decided_by           UUID,
    decision_comment     VARCHAR(500),
    created_at / created_by / updated_at / updated_by   -- as core/V030__attendance.sql:12-15
    CONSTRAINT attendance_regularization_status_check CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT attendance_regularization_order_check  CHECK (requested_out_at > requested_in_at)
);
CREATE UNIQUE INDEX uk_attendance_regularization_tenant_employee_date_pending
    ON hrms.attendance_regularization (tenant_id, employee_id, attendance_date) WHERE status = 'PENDING';
CREATE INDEX idx_attendance_regularization_tenant_employee_date ON hrms.attendance_regularization (tenant_id, employee_id, attendance_date DESC);
CREATE INDEX idx_attendance_regularization_tenant_status_date   ON hrms.attendance_regularization (tenant_id, status, attendance_date DESC);
CREATE INDEX idx_attendance_regularization_tenant_instance      ON hrms.attendance_regularization (tenant_id, approval_instance_id);
-- + ENABLE ROW LEVEL SECURITY and the tenant_isolation policy, CASE form, copied from core/V030__attendance.sql:23-32

ALTER TABLE hrms.clock_session ADD COLUMN regularization_id UUID REFERENCES hrms.attendance_regularization(id);
CREATE INDEX idx_clock_session_tenant_regularization ON hrms.clock_session (tenant_id, regularization_id);
```

`approval_instance_id` is nullable only because the row is inserted before the engine answers;
the service sets it in the same transaction. The key it points at is
`core.approval_instance.id` (`M/core/V090__approval_instance.sql:5`).

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | `hrms.attendance_regularization` has both, in the same script |
| Flyway only, `ddl-auto` nowhere | one script, one `CREATE TABLE` (`migration/README.md` §one table per script); every statement names `hrms.` |
| Money as `Money` / `BigDecimal` | no money column |
| Index on `tenant_id` plus lookup columns (`DEBT-018`) | five indexes, all lead with `tenant_id`; all three FKs covered; date indexes descending |
| Expand / contract | a new table and a nullable column; `W-40.3`'s code runs unchanged against it |
| No module references another | `hrms` calls `core` (`ApprovalService`, `ApprovalInstanceRepository`, `AttendanceQuery`, `TenantClock`, `EmployeeService`) and `shared` |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/.../attendance/RegularizationRulesTest.java` | every row of "Rules at submit" with a fixed `Clock`; the monthly count ignores `REJECTED` |
| Integration | `hrms/.../attendance/RegularizationFlowIT.java` | employee clocks in and never out; submits 09:00–18:00; the manager's step appears; on approve the open session is voided, one `REGULARIZATION` session exists, `core.attendance` is `PRESENT` with `source = CLOCK`, the request is `APPROVED`. A second dispatch of the same instance changes nothing |
| Integration | `hrms/.../attendance/RegularizationRejectIT.java` | on reject the request is `REJECTED` with the comment; sessions and `core.attendance` are unchanged |
| Integration | `hrms/.../attendance/RegularizationAdminDayIT.java` | submit for an `ADMIN` day is `409`; a day turned `ADMIN` after submit stays `ADMIN` after approval |
| Integration | `hrms/.../attendance/RegularizationRlsIT.java` | tenant A's requests invisible to tenant B |
| Integration | `hrms/.../attendance/RegularizationGuardIT.java` | `403 MODULE_NOT_ENTITLED` for a Payroll-only tenant; `employee` is `403` on the all-requests `GET` |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='hrms.attendance_regularization'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname FROM pg_indexes WHERE schemaname='hrms' AND tablename='attendance_regularization' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT is_nullable FROM information_schema.columns
    WHERE table_schema='hrms' AND table_name='clock_session' AND column_name='regularization_id';"
cd code/backend && mvn -q verify
node .claude/hooks/check-done.mjs W-40.4
```

| Check | Expected |
|---|---|
| RLS on `hrms.attendance_regularization` | `t` |
| Indexes | the primary key and the four named in §6 |
| `clock_session.regularization_id` | `YES` |
| Suite | green, no skips; the six test classes in §7 run |
| `check-done.mjs` | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The feature has no precedent and the shape is wrong for a real customer | medium | Kept to one in-time and one out-time per day; the table can take a second shape later without a destructive step |
| The handler runs twice and inserts two sessions | medium — the dispatcher retries | Step 1 of the handler; `RegularizationFlowIT` dispatches twice |
| The handler is called with no tenant bound (the sweep) | certain | Bind from the instance, as `ReimbursementClaimOutcomeHandler.java:58-62` |
| `ROLE` steps route to the first holder; escalation ignores delegations | known, `W-15` outstanding (`active-work.md` 2026-09-28) | Not this ticket's; the seeded flow is one reporting-manager step |
| A tenant edits the flow to zero steps | low | `ApprovalService.start` refuses when no definition is active; the submit rolls back |

## 10. Rollback

Revert the application commit. With the handler gone an approved instance is claimed and
changes nothing, which is today's behaviour. The table and column stay.

## 11. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 no tenant column | **Fixed for this table** |
| DEBT-018 no indexes | **Honoured** |
| DEBT-013 typo package | **Not carried** |

## 12. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `code/backend/migration` | `hrms/V119__attendance_regularization.sql` |
| 2 | `code/backend/hrms` | entity, repository, service, handler, controller, DTOs, the `ClockSession` change, tests |

## 13. Decisions — settled 2026-09-30

| # | Question | Answer |
|---|---|---|
| 1 | What does an approved request change | **The day's sessions**: the old ones are voided, one session with the requested times replaces them, and the day is derived again by the same rule as a clock-out |
| 2 | Can it overrule an administrator's entry | **No.** Refused at submit; ignored at approval |
| 3 | What does the monthly limit count | **`PENDING` and `APPROVED` requests by the month of the day being corrected.** Rejected ones do not count |
| 4 | New permission codes | **None.** Submit is `hrms.attendance.mark`; approval is `core.approval.decide` |
| 5 | Withdraw a pending request | **Not yet** — waits on the engine's cancel (`W-16.3`) |
