# Full-system certification — one company, four testers

Company: **Infinevo Test Co** (Azure dev tenant, seeded 2026-10-09, script `d:\tmp\infinevo-test-seed`).
Shared password and logins per `.claude/outputs/2026-10-09-dev-validation-logins.md`.

## 1. What the seed already gives us

| Seed step | Covers | Certification use |
|---|---|---|
| 1 Org masters | 5 departments, 8 designations, 3 locations | T1 verifies, does not re-create |
| 2–3 Employees | 53 employees with personal, PAN/Aadhaar, bank | T2 adds ~5 by hand (new hire flow); the 53 are payroll population |
| 4 Reporting lines | Managers set | T2 changes one, checks approvals re-route |
| 5 Leave | CL/EL/SL policies, 2026 allocations | T2 tests apply/approve on top |
| 6 Salaries + statutory | CTC on all, EPF/ESI/tax deductor | T3 verifies settings, adds one revision |
| 7 Attendance + leave on behalf | Sept data | T2 regularisation and LOP |
| 8 Projects/tasks/OT | 2026-07 projects, Sept OT | T2/T4 timesheets |
| 9 Invitations | 5 role logins | T1 invites the rest |
| 10 Pay run | 2026-10 created and computed | T3 must **delete or re-run** — it was computed before any test input |

**Gaps the seed does not create (must be done by hand, before parallel work):** setup checklist state, holiday calendar, FBP plan, pay schedule, PT settings, tax-declaration window, LOP policy, approval definitions, reminders. These are the T1/T3 prerequisites below.

## 2. Prerequisites — serial, before parallel work

| # | Step | Owner | Done when |
|---|---|---|---|
| P0 | Reset: delete seeded Oct pay run; confirm `resetPasswordAllowed` on realm; freeze test data sheet (names, amounts, period = Oct 2026) | Founder | All four can log in |
| P1 | Company and setup checklist complete, country template | T1 | Checklist shows done |
| P2 | Holiday calendar (default), approval definitions, LOP policy | T1 | Each saved |
| P3 | Invite remaining role logins; each tester accepts their invite | T1, all | All four signed in |
| P4 | Hire 5 new employees by hand, assign managers, invite, they sign in | T2 | T4 has a login per persona |
| P5 | Pay schedule, FBP plan, PT, tax-declaration window open | T3 | Employee sees declaration |

## 3. High-level plan per tester

### T1 — Platform admin + Tenant admin

| # | Flow | Spec |
|---|---|---|
| T1-01 | Platform dashboard, tenant list, create/suspend tenant | W-65, W-73-2 |
| T1-02 | Setup checklist end to end, country template | W-24-1, W-73-9 |
| T1-03 | Org masters CRUD, duplicates refused | W-14-1 |
| T1-04 | Roles catalogue, role matrix, permission denial per role | W-11 |
| T1-05 | Users & access: invite, resend, revoke, accept link sets password, forgot password | W-24-2, W-73-4, D-88 |
| T1-06 | Bulk invite | W-73-7 |
| T1-07 | Support impersonation with reason, audit entry written | W-65-2, W-22-1 |
| T1-08 | Subscription/entitlement: module off hides menu and blocks API | W-12 |
| T1-09 | Tenant isolation: second company sees nothing of Test Co | W-58 |
| T1-10 | Notifications, scheduled reports, exports | W-20, W-23 |
| T1-11 | Audit trail covers every T2/T3/T4 action sampled | W-22 |

### T2 — HR + Manager

| # | Flow | Spec |
|---|---|---|
| T2-01 | Add employee with access; all tabs (personal, ID, bank, documents) | W-13, W-73-3, W-73-5 |
| T2-02 | Duplicate email/PAN/Aadhaar refused; name casing | BUG-D4 |
| T2-03 | Reporting line change; manager sees direct reports | W-14-2 |
| T2-04 | Leave types/policy edit, allocation, balance | W-16-1, W-16-2 |
| T2-05 | Leave request: apply → manager → HR; reject; cancel; on-behalf | W-16-3 |
| T2-06 | LOP consumption; leave import | W-16-4a/b |
| T2-07 | Holiday screens, holiday view for employee | W-17, W-46-3b |
| T2-08 | Attendance capture, regularisation, preferences | W-39, W-40 |
| T2-09 | Overtime request → approve; HR OT list | W-40-5/6, W-68 |
| T2-10 | Projects, tasks, assignments | W-41 |
| T2-11 | Timesheet submit → per-project approve → recall | W-42 |
| T2-12 | Approvals inbox, delegation, escalation | W-15 |
| T2-13 | Terminate with reason, reactivate | BUG-D4 |
| T2-14 | HRMS dashboard figures reconcile | W-44 |

### T3 — Payroll officer + Finance

| # | Flow | Spec |
|---|---|---|
| T3-01 | Salary component catalogue; CTC structure and a revision | W-26 |
| T3-02 | FBP plan; employee declaration visible | W-27 |
| T3-03 | Pay schedule; EPF/ESI/PT settings | W-28, W-31 |
| T3-04 | Pay inputs ledger, run tag, scheduled earnings | W-19, W-30-1, W-73-6 |
| T3-05 | Prior payroll import and tax counted | W-38 |
| T3-06 | Proof verification queue (if D-25 spec lands; else exception) | W-34-2 |
| T3-07 | Pay run Oct 2026: create, LOP + inputs, compute, lock, pay; async worker | W-29 |
| T3-08 | Off-cycle run | W-30-2 |
| T3-09 | Claims approve/reject, ad-hoc deductions in run | W-35 |
| T3-10 | Finance: approvals, mark paid, payroll dashboard | W-37, D-14 |
| T3-11 | TDS, Form 16, Part A upload, signed payslip link | W-36 |
| T3-12 | Reconciliation: payslip totals = inputs + leave/LOP + claims + tax | all above |

### T4 — Employee personas (new hire, manager-reportee, ESI-eligible, high earner)

| # | Flow | Spec |
|---|---|---|
| T4-01 | Accept invite, welcome screen, `/me` layout, profile no raw ids | W-73-8, W-25 |
| T4-02 | Documents upload/delete | W-21 |
| T4-03 | Apply, cancel leave; balance; holiday view | W-46-5 |
| T4-04 | Clock in/out, regularisation request | W-40 |
| T4-05 | Timesheet entry, submit, see approval | W-42-1 |
| T4-06 | Overtime request | W-40-6 |
| T4-07 | FBP declaration | W-27-2 |
| T4-08 | Tax declaration: both regimes, housing, deductions, other income | W-32, W-33 |
| T4-09 | Proof upload (exception if D-25 open) | W-34-1 |
| T4-10 | Reimbursement claim | W-35-1 |
| T4-11 | My payslips, signed link, TDS, Form 16 | W-36 |
| T4-12 | Negative: cannot see others' data or admin menus | W-11-2 |

## 4. Join points

| Join | Waits for |
|---|---|
| J1 Pay run compute (T3-07) | T2-05/06/08/09 approved for Oct; T4-07/08/10 submitted; T3-01..05 done |
| J2 Reconciliation and Form 16 (T3-11/12, T1-10/11) | J1 paid and locked |

## 5. Test tracker

One row per flow id above. Status: `Blocked` / `Ready` / `Pass` / `Fail` / `Exception`.

| Id | Tester | Prereq | Status | Defect | Date | Note |
|---|---|---|---|---|---|---|
| P0 | Founder | – | Ready | | | |
| P1 | T1 | P0 | Blocked | | | |
| P2 | T1 | P1 | Blocked | | | |
| P3 | T1 | P2 | Blocked | | | |
| P4 | T2 | P3 | Blocked | | | |
| P5 | T3 | P3 | Blocked | | | |
| T1-01..11 | T1 | P3 (T1-11 after J2) | Blocked | | | |
| T2-01..14 | T2 | P4 | Blocked | | | |
| T3-01..06 | T3 | P5 | Blocked | | | |
| T3-07..10 | T3 | J1 | Blocked | | | |
| T3-11..12 | T3 | J2 | Blocked | | | |
| T4-01..06 | T4 | P4 | Blocked | | | |
| T4-07..10 | T4 | P5 | Blocked | | | |
| T4-11 | T4 | J2 | Blocked | | | |

Known exceptions carried into sign-off unless fixed first: D-25 (proof screens), D-43–D-58 (leave/attendance/timesheet lists), D-78–D-87 (screen audit phases 3–4).

Expand each `T?-nn` into its own row once testing starts; one defect per row in the Defect column, numbered `D-nn` by the founder.
