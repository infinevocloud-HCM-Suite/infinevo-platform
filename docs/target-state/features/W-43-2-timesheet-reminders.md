# Feature: Timesheet reminders and escalation

| Field | Value |
|---|---|
| **Feature ID** | `W-43.2` · ticket #55 (`W-43`) · `HRMS-10` · part 2 of 2 |
| **Promoted to** | `docs/target-state/features/W-43-2-timesheet-reminders.md` |
| **Owner** | devashis |
| **Apps touched** | `code/backend/hrms` |
| **Related gaps** | DEBT-018 (honoured), DEBT-021 (fixed — no own scheduler), DEBT-022 (fixed), DEBT-023 (not carried) |
| **Status** | **Blocked** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | `W-42.1` (the timesheet table) and `W-43.1` (audience values, `TIMESHEET_ESCALATION`) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee whose last week is missing or in draft gets one reminder, and later their reporting manager gets one list | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

| Today (frozen) | Evidence |
|---|---|
| Five reminder kinds — employee, approver, project manager, HR, escalation — each its own table | `serviceimpl/schedular/NotificationSchedularServiceImpl.java:80`, `:256`, `:370`, `:513`, `:619` |
| Each is a weekday and time, on or off, with a level 1–3 | `entity/notificationconfig/EmployeeReminder.java:22-40`; `enumuration/ReminderLevel.java` |
| All look at **last** week, Monday to Sunday | `NotificationSchedularServiceImpl.java:86-87` |
| Late means a draft, or no timesheet, for an assigned `STARTED` project — **one mail per project** | `NotificationSchedularServiceImpl.java:98-104` |
| Escalation goes to typed email addresses, as one list | `entity/notificationconfig/EscalationReminder.java:31-34`; `NotificationSchedularServiceImpl.java:677`, `:695-700` |
| A 60-second tick, server time, no lock | `scheduler/NotificationSchedular.java:54`; `NotificationSchedularServiceImpl.java:83` |

Paths above are under `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/`.

**The machinery exists.** `W-20.2` ships the rule table, the locked sweep in the tenant's zone and `TIMESHEET_REMINDER` with its template (`code/backend/worker/.../notification/ReminderEvaluator.java:75-76`, `:93`; `code/backend/core/.../notification/NotificationEvent.java:39`). `W-43.1` lets an audience supply `week_start` and `late_employees` and adds `TIMESHEET_ESCALATION`. What is missing is who is late. The only audience today, `SUBJECT`, is every active employee (`code/backend/core/.../notification/SubjectAudienceResolver.java:40`).

## 2. Scope

**In scope**

- Audience `TIMESHEET_LATE`: employees late for last week, each told which week
- Audience `TIMESHEET_LATE_MANAGER`: their primary reporting managers, each given the list of their late reports
- One query behind both, so they never disagree

**Out of scope**

- A default rule per tenant — the administrator adds rules through `POST /api/v1/reminder-rules`, as `W-34.3` decision 2; legacy reminders were off until configured
- Reminding approvers — the approval engine already escalates overdue steps (`code/backend/core/.../approval/EscalationSweep.java`, `W-15.2`)
- Project-manager and HR reminders, typed escalation addresses, reminder levels — not carried (§ 13)
- Rule screens — a later screens ticket

## 3. Flow

```
[admin] POST /api/v1/reminder-rules
   { event: TIMESHEET_REMINDER,   audience: TIMESHEET_LATE,         anchor: WEEKLY, day_of_week: 1, send_at_local_time: "10:00", offset_days: 0 }
   { event: TIMESHEET_ESCALATION, audience: TIMESHEET_LATE_MANAGER, anchor: WEEKLY, day_of_week: 3, send_at_local_time: "10:00", offset_days: 0 }

[worker sweep, W-20.2]  slotDate = tenant-local day
   week = Monday of (slotDate - 7 days)                       legacy NotificationSchedularServiceImpl.java:86
   --> TimesheetLateAudienceResolver.recipients
         TimesheetLateQuery.lateEmployees(tenant, week)
         one recipient per employee, { week_start: week }
   --> TimesheetLateManagerAudienceResolver.recipients
         same query, grouped by primary manager in force on week + 6
         one recipient per manager, { week_start: week, late_employees: "Asha Rao, Vikram Sen" }
   --> claim, then compose once per recipient                 W-20.2, unchanged
```

**Late for week W**, all in one tenant:

1. Employee is `ACTIVE` and not deleted (`core/.../employee/EmploymentStatus.java:25`).
2. Has a non-deleted assignment (`hrms.assignment`, `migration/.../hrms/V088__assignment.sql`) to a non-deleted `STARTED` project (`hrms/.../project/ProjectStatus.java`) whose `start_date` is null or on or before W's Sunday, and whose `end_date` is null or on or after W's Monday.
3. Has no timesheet for W in `SUBMITTED`, `APPROVED` or `REJECTED` (`W-42-1-timesheet-entry.md:182`, `:206`). None, `DRAFT` or `CANCELLED` is late. `REJECTED` is not: the employee did submit, and `W-42.3` already tells them.

One person is one recipient, however many projects they are on.

**Primary manager:** `core.reporting_line` row of kind `PRIMARY` with `effective_from` on or before W's Sunday and `effective_to` null or on or after it (`migration/.../core/V028__reporting_line.sql:4-17`; `core/.../org/ReportingLineKind.java`), and that manager `ACTIVE` and not deleted. A late employee with no such manager is left out of the escalation and counted in the log.

## 4. Backend changes

Under `code/backend/hrms/src/main/java/com/infinevo/hrms/timesheet/reminder/`.

| Layer | File | Change |
|---|---|---|
| Query | `TimesheetLateQuery` + `Impl` | new: `List<LateEmployee> lateEmployees(UUID tenantId, LocalDate weekStart)` — id, name, primary manager id or null. One JPQL, tenant-bound, every parameter bound, ordered by name |
| Record | `LateEmployee` | new |
| Resolver | `TimesheetLateAudienceResolver` | new `@Component`; `audience()` = `"TIMESHEET_LATE"`; `suppliedPlaceholders()` = `{week_start}`; `recipients(...)` overridden; `resolve(...)` returns the same ids for the current week. `@Transactional(readOnly = true)`, as `SubjectAudienceResolver` |
| Resolver | `TimesheetLateManagerAudienceResolver` | new `@Component`; `audience()` = `"TIMESHEET_LATE_MANAGER"`; `suppliedPlaceholders()` = `{week_start, late_employees}`; names joined with `", "` in name order |
| Helper | `ReminderWeek` | new: `weekStart(LocalDate slotDate)` = Monday of `slotDate.minusWeeks(1)` |

Follow `code/backend/payroll/src/main/java/com/infinevo/payroll/proof/ProofPendingAudienceResolver.java` for shape, constructors and transaction.

**API contract** — no new endpoint. `POST /api/v1/reminder-rules` now accepts the two audiences above (`ReminderRuleServiceImpl.java:153-161` checks a bean exists).

## 5. Frontend changes

None.

## 6. Database changes

None. Indexes that carry the query:
`uk_timesheet_tenant_employee_week` (`W-42.1`), the assignment index on `(tenant_id, employee_id, is_deleted)` (`V088__assignment.sql:23`), `idx_project_tenant_deleted_status` (`V086__project.sql:29`), `idx_reporting_line_lookup` (`V028__reporting_line.sql:19`).

- [x] `tenant_id` + RLS — no new table
- [x] Index on `tenant_id` plus lookup columns — existing, listed above
- [x] Money — none
- [x] Expand / contract — nothing to expand

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/.../timesheet/reminder/ReminderWeekTest.java` | Monday, Wednesday and Sunday slots all give last week's Monday; 1 January |
| Integration | `hrms/.../timesheet/reminder/TimesheetLateQueryIT.java` | of: no timesheet; `DRAFT`; `CANCELLED`; `SUBMITTED`; `APPROVED`; `REJECTED`; on a `COMPLETED` project only; on a project ending before the week; no assignment; `SUSPENDED` with no timesheet — exactly the first three are late; tenant B's rows never appear |
| Integration | `hrms/.../timesheet/reminder/TimesheetLateManagerIT.java` | two late reports of one manager give one recipient listing both, in name order; a manager whose line ended before the week is not used; a late employee with no manager is left out; a terminated manager is not a recipient |
| Integration | `hrms/.../timesheet/reminder/TimesheetReminderRuleIT.java` | both rules are `201`; `TIMESHEET_ESCALATION` with `TIMESHEET_LATE` is `400` (no `late_employees`) |
| Sweep | `worker/.../notification/TimesheetReminderSweepTest.java` | **acceptance**: real `ReminderEvaluator` with the real resolvers. An employee late on two projects gets **one** `TIMESHEET_REMINDER` email naming last week's Monday; a second sweep the same day sends nothing; their manager on Wednesday gets **one** `TIMESHEET_ESCALATION` listing them. Pattern: `ProofReminderSweepTest` |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
grep -rln "implements ReminderAudienceResolver" code/backend/hrms/src/main/java/
grep -rn "@Scheduled" code/backend/hrms/src/main/java/com/infinevo/hrms/timesheet/ || echo "no scheduler"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Resolvers | two files under `hrms/timesheet/reminder/` |
| Scheduler | `no scheduler` |
| Suite | green, no skips; `TimesheetReminderSweepTest` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Admin adds a `TIMESHEET_REMINDER` rule with `SUBJECT` and reminds everyone | medium | the rule is allowed on `main` today; § 3 names the two audiences; the screens ticket should offer only them for timesheet events |
| An employee on leave all week is reminded | medium | not handled in legacy either; leave lives in `hrms` and can be excluded later without a schema change |
| Week counted in server time | low | the week comes from the sweep's tenant-local `slotDate` (`W-43.1`) |
| The escalation and the reminder disagree | low | one `TimesheetLateQuery` for both |

## 10. Rollback

Nothing deployed, no schema change. Removing the two beans makes rules on these audiences resolve nothing and new ones be refused.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | creates no table |
| Flyway only | no schema change |
| `Money`/`BigDecimal` | no money |
| Index on `tenant_id` plus lookup columns | existing indexes, § 6 |
| Expand / contract | nothing changed |
| No module references another module | `hrms` → `core` only (`ReminderAudienceResolver`, `ReminderRecipient`, `Employee`, `ReportingLine`) |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-018 no indexes | **Honoured** — existing tenant-led indexes, § 6 |
| DEBT-021 scheduler on every replica | **Fixed** — no own scheduler; legacy HRMS had the same fault (`scheduler/NotificationSchedular.java:54`) |
| DEBT-022 unscoped finders | **Fixed** — every query is tenant-bound |
| DEBT-023 test endpoints fire schedulers | **Not carried** — no trigger endpoint |

## 13. Decisions — settled 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | Which week is chased? | **Last week**, Monday to Sunday, as legacy |
| 2 | Who gets the escalation? | **The primary reporting manager**, one list per manager. Not typed addresses, not project managers, not HR |
| 3 | One mail per project, as legacy? | **No.** One per person — the build-order rule is one email, not two |
| 4 | Seed a default rule? | **No**, as `W-34.3` |
| 5 | Approver reminders? | **Left to the approval engine's escalation** (`W-15.2`) |
| 6 | Is `REJECTED` late? | **No.** The employee submitted; `W-42.3` tells them of the rejection |
| 7 | Late employee with no manager? | **Left out of the escalation**, counted in the log |
| 8 | Reminder levels 1–3? | **Not carried.** A second or third reminder is another rule or `max_repeats` |

## 14. Open for the founder

None.
