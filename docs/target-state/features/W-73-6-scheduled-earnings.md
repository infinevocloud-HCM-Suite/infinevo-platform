# Feature: Scheduled earnings — a one-time or instalment earning planned for a future month

| Field | Value |
|---|---|
| **Feature ID** | `W-73.6` · from `W-73` · replaces the founder's `SCHEDULED_EARNINGS_DESIGN_PLAN` draft of 2026-10-07 |
| **Promoted to** | `docs/target-state/features/W-73-6-scheduled-earnings.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/payroll`, `code/backend/worker` (one scheduled job), `code/frontend/src/payroll` |
| **Related gaps** | `D-37` (named earning types, Variable checkbox), `D-38` (hidden fields) |
| **Status** | Draft |
| **Written by** | claude for the founder, 2026-10-07 |
| **Blocked by** | `D-37` on `main` — the drawer this adds a checkbox to |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` (+ one worker job that calls it) | 1 |
| Flyway migration | `V161` — one table | 1 |
| Externally testable behaviour | a bonus scheduled for November appears as a taxable earning line on November's pay run and nowhere else | 1 |
| Frontend area | `payroll/salary` (drawer checkbox + employee Salary tab panel) | 1 |

## 1. Problem

| Fact | Evidence |
|---|---|
| An earning is paid every month by the structure or by frequency; a one-off for a future month has to be typed as a pay input that month, by hand | `StructureLineContributor.java:97-105`, `PayInputLineContributor.java:47` |
| `ONE_TIME_PAYOUT` pay inputs already become taxable earning lines | `PayInputLineContributor.java:22,47` |
| `is_variable`, `earning_frequency` exist; a scheduled flag exists but drives nothing | `Earning.java:24-27,44,75` |
| The founder's draft proposed `V125` (taken), `core.tenant(id)` (the column is `tenant_id`) and a new `@Order(250)` contributor (unnecessary — see above) | draft §4, §6 |

## 2. Scope

**In scope**

- `payroll.scheduled_earning`: employee, earning component, amount (`numeric(19,4)`), first period, instalments (1–12), status `SCHEDULED | PAUSED | CANCELLED | PAID`, reason, created by
- **Materialise**: when a pay run is created for a period (`PayRunService` create), and nightly on the worker for the current period, every `SCHEDULED` row whose next period equals the run's period writes **one `ONE_TIME_PAYOUT` pay input** (amount ÷ instalments, last instalment takes the rounding) tagged with `scheduled_earning_id`; the last instalment sets `PAID`
- Termination: the employee-terminated hook cancels remaining instalments
- Component drawer: under the Variable checkbox (`D-37`), a **Scheduled** checkbox → sets `is_scheduled_earning`; only such components appear in the schedule form
- Employee Salary tab: **Scheduled earnings** panel — add (component, amount, first month, instalments, reason), pause, resume, cancel, history with the pay input each instalment became
- Actions: `payroll.structure.update` to write, `payroll.structure.read` to read (existing codes)

**Out of scope**

- Deductions on a schedule (loan EMIs are `emiType` on the component, `D-38`)
- Editing an instalment after it is materialised (edit the pay input instead)
- Approval flow

## 3. Flow

```
officer --> employee Salary tab --> Scheduled earnings --> Add {component, amount, firstPeriod, instalments, reason}
        --> POST /api/v1/employees/{id}/scheduled-earnings --> payroll.scheduled_earning SCHEDULED

pay run created for 2026-11  (or worker nightly, current period)
  --> ScheduledEarningService.materialise(tenant, period)
  --> for each SCHEDULED row with nextPeriod == period: INSERT core.pay_input (kind ONE_TIME_PAYOUT, amount, period, source scheduled_earning_id)
  --> row.paidInstalments++ ; last one -> PAID
PayInputLineContributor (@Order 300, unchanged) --> earning line "Bonus" taxable
employee terminated --> remaining SCHEDULED rows -> CANCELLED (reason "terminated")
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `payroll/.../scheduled/ScheduledEarning.java` (new) | fields above; `BigDecimal amount` |
| Repository | `ScheduledEarningRepository` | `findDue(tenantId, period)`, `findByEmployee` |
| Service | `ScheduledEarningService` + `Impl` | create / pause / resume / cancel / `materialise(period)` — idempotent: a pay input with the same `scheduled_earning_id` and period is never written twice |
| Service hook | `payroll/.../payrun/PayRunServiceImpl` create | call `materialise(period)` before the first compute |
| Worker | `worker/.../scheduler/ScheduledEarningJob` | nightly, `@SchedulerLock`, current period per tenant — catches runs created before a schedule was added |
| Core hook | `core` publishes `EmployeeTerminatedEvent` if it does not yet (`EmployeeService` terminate path); `payroll` listens | module rule: payroll depends on core only |
| Pay input | `core/.../payinput/PayInput.java` | `+ source_ref uuid null` — written by payroll through the existing pay-input service, not a direct insert |
| Controller | `ScheduledEarningController` | below |
| Drawer flag | `Earning.java:75` `isScheduledEarning` | already persisted; exposed in the component DTO if not yet |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/employees/{id}/scheduled-earnings` | — | `[{id, componentCode, amount, firstPeriod, instalments, paidInstalments, status, reason, payInputIds[]}]` | `payroll.structure.read` |
| POST | same | `{componentId, amount, firstPeriod, instalments, reason}` | `201` | `payroll.structure.update` |
| POST | `/api/v1/scheduled-earnings/{id}/pause` · `/resume` · `/cancel` | `{reason?}` | `200` | `payroll.structure.update` |

Rules: `firstPeriod` ≥ current period; cannot cancel a `PAID` row; pause only `SCHEDULED`.

## 5. Frontend changes

| File | Change |
|---|---|
| `payroll/salary/ComponentDrawer.jsx` | Scheduled checkbox under Variable; tooltip "Can be planned for a future month on the employee's Salary tab" |
| `payroll/salary/ScheduledEarningsPanel.jsx` (new) | table + drawer + actions |
| `payroll/salary/scheduledEarningService.js` (new) | the four calls |
| `payroll/salary/componentFields.js` | `scheduledEarning` boolean in schema and defaults |

**Routes added** — none.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V161__scheduled_earning.sql` | `payroll.scheduled_earning(id, tenant_id, employee_id, earning_id, amount numeric(19,4), first_period date, instalments smallint, paid_instalments smallint, status, reason, created_by, created_at, updated_at)` + RLS + index `(tenant_id, status, first_period)`; `core.pay_input + source_ref uuid null` | yes | table drop; column nullable |

- [x] `tenant_id` and RLS
- [x] index on `tenant_id` plus lookup columns
- [x] money `numeric(19,4)`, `BigDecimal`

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `ScheduledEarningSplitTest` | 10,000 ÷ 3 → 3,333.33 · 3,333.33 · 3,333.34 |
| Integration | `ScheduledEarningMaterialiseIT` | run created → one pay input; second call → none; last instalment → `PAID`; termination → `CANCELLED` |
| Integration | `PayRunScheduledLineIT` | the line appears on the run, taxable, through the unchanged contributor |
| Unit | `ScheduledEarningsPanel.test.jsx` | only scheduled components offered; past month refused |

## 8. Verification

| Check | Expected |
|---|---|
| Schedule 30,000 bonus for next month, 1 instalment | Next month's run shows "Bonus 30,000"; this month's does not |
| 3 instalments | Three months, three lines, rounding on the last |
| Terminate the employee after one instalment | Row `CANCELLED`; no further lines |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Run created before the schedule exists | medium | nightly job plus a "Refresh scheduled earnings" button on the run page (calls `materialise`) |
| Double line if both paths run | low | idempotent on `(scheduled_earning_id, period)` |

## 10. Rollback

Cancel all rows; pay inputs already written stay as ordinary one-time payouts. Drop the table.
