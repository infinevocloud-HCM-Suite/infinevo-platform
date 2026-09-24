# Dev Tracker

> The product itself — foundation, data, core platform, payroll, HRMS, frontend.
> Streams A to F, plus the product items in G and H.
> **GitHub is authoritative. Done means on `origin/main`, nothing else.** Legend: [README.md](README.md).
> Last refreshed: **2026-09-24**, against `origin/main` at `1d1123a`.

## Summary

| Stream | Rows | Done | In flight | Ready | Blocked | Where it stands |
|---|---|---|---|---|---|---|
| A — Foundation | 2 | 2 | 0 | 0 | 0 | Done |
| B — Data foundation | 6 | 5 | 0 | 1 | 0 | Done, one seed defect open |
| C — Core platform | 24 | 7 | 0 | 10 | 7 | **Layer 0 done, layer 1 open.** Employee, org masters, roles, login, audit on `main` |
| D — Payroll | 21 | 0 | 0 | 1 | 20 | Not started. `W-26` can start |
| E — HRMS | 6 | 0 | 0 | 1 | 5 | Not started. `W-41` can start |
| F — Frontend | 12 | 0 | 0 | 0 | 12 | Not started. Only the login shell from `W-10` exists |
| G/H — product items | 3 | 1 | 1 | 0 | 1 | `W-55` merged, `W-66` spec in review |
| **Total** | **74** | **15** | **1** | **13** | **45** | |

**The first business tables are on `main`** — employee and its five detail sections,
department, designation, work location, role — all under row-level security.

---

## 1. Stream A — Foundation

| # | Ticket | Spec | Status | Owner | Built by |
|---|---|---|---|---|---|
| #1 | `W-01` Repository & module skeleton | approved | **Done** | — | InvoiceLLM |
| #5 | `W-04` Test foundation | approved | **Done** | SayInfi | SayInfi |

`W-02` local stack and `W-03` build pipeline are in [INFRA-TRACKER.md](INFRA-TRACKER.md).

---

## 2. Stream B — Data foundation

| # | Ticket | Spec | Status | Owner | Built by |
|---|---|---|---|---|---|
| #6 | `W-05` Postgres & schemas | approved | **Done** | SayInfi | SayInfi |
| #7 | `W-06` Flyway | approved | **Done** | — | InvoiceLLM |
| #8 | `W-07` Tenant model | approved | **Done** | — | Gautam Jha |
| #9 | `W-08` Tenant binding filter | approved | **Done** | SayInfi | sanjib |
| #10 | `W-09` Reference schema & seed | approved | **Done** | — | sanjib |
| #146 | `W-09` defect — senior-citizen tax slabs not seeded; seniors over-deducted | — | Ready | — | — |

---

## 3. Stream C — Core platform

All Core specs are written and live in `docs/target-state/features/`.

| # | Ticket | Spec | Status | Owner | Built by |
|---|---|---|---|---|---|
| #11 | `W-10` Identity — one Keycloak login | approved | **Done** | — | sanjib |
| #12 | `W-11.1` Role and action catalogue | approved | **Done** | — | sanjib |
| #12 | `W-11.2` Permission check and cache | approved | **Done** | — | sanjib |
| #13 | `W-12.1`–`.3` Subscription, entitlement, navigation feed | approved | Ready | SayInfi | — |
| #14 | `W-13.1` Employee record | approved | **Done** | — | sanjib |
| #14 | `W-13.2` Employee detail, audit trail turned on | approved | **Done** | — | sanjib |
| #14 | `W-13.3` Employee search and listing | approved | Ready | — | — |
| #15 | `W-14.1` Department, designation, work location | approved | **Done** | SayInfi | sanjib |
| #15 | `W-14.2` Reporting line | approved | Ready | — | — |
| #16 | `W-15.1`–`.3` Approval engine | approved | Ready | SayInfi | — |
| #17 | `W-16.1` Leave types and policy | approved | Blocked — `W-15` | — | — |
| #18 | `W-16.2` Leave allocation and balance | approved | Blocked — `W-16.1` | — | — |
| #19 | `W-16.3` Leave request, approval, documents | approved | Blocked — `W-16.2` | — | — |
| #20 | `W-16.4` Consumption, loss-of-pay, bulk import | approved | Blocked — `W-16.3` | — | — |
| #21 | `W-17` Holiday calendar | approved | Ready | — | — |
| #22 | `W-18.1`–`.2` Loss-of-pay policy and stamp | approved | Blocked — `W-16`, `W-17` | — | — |
| #23 | `W-19` Pay input ledger | approved | Ready | — | — |
| #24 | `W-20.1`–`.2` Notifications, delivery and scheduler | approved | Ready | — | — |
| #25 | `W-21` Document store | approved | Ready | — | — |
| #26 | `W-22.1` Audit trail | approved | **Done** | — | sanjib |
| **none** | `W-22.2` Audit retention | approved | Ready — **no GitHub ticket** | — | — |
| #27 | `W-23.1`–`.2` Export, scheduled reports | approved | Ready | — | — |
| #28 | `W-24.1`–`.2` Setup checklist, invitations | approved | Blocked — `W-12` | — | — |
| #29 | `W-25` Employee self-service portal | approved | Blocked — `W-16` | — | — |

> **GitHub tickets are no longer used** (2026-09-24). The `#nn` column is kept only to find
> history. Three were closed with parts still open: #14 (`W-13.3`), #15 (`W-14.2`), #26 (`W-22.2`).

> **`W-10-identity` is still being pushed** (Sayeed, 2026-09-24, 32 commits ahead of `main`).
> `W-10` is already done. That branch cannot merge — see `salvage/W-10-old` in
> `active-work.md`.

---

## 4. Stream D — Payroll

No spec written yet for any payroll ticket.

| # | Ticket | Status | Owner |
|---|---|---|---|
| #30 | `W-26` Salary catalogue & structure — **fix floating-point money while porting** | Ready | — |
| #31 | `W-27` Flexible benefit plan | Blocked — `W-26` | — |
| #32 | `W-28` Pay schedule | Blocked — `W-18` | — |
| #33 | `W-29.1` Pay run — creation, inclusion, locking | Blocked — `W-28`, `W-19` | — |
| #34 | `W-29.2` Pay run — earnings and deductions | Blocked — `W-29.1` | — |
| #35 | `W-29.3` Pay run — loss-of-pay and pay inputs | Blocked — `W-29.2` | — |
| #36 | `W-29.4` Pay run — async on the worker, progress | Blocked — `W-29.3` | — |
| #37 | `W-30` Off-cycle & one-time | Blocked — `W-29` | — |
| #38 | `W-31` Statutory components | Blocked — `W-26` | — |
| #39 | `W-32.1` Tax declaration — window, submission, revision | Blocked — `W-26` | — |
| #40 | `W-32.2` Tax declaration — house rent, home loan, let-out | Blocked — `W-32.1` | — |
| #41 | `W-32.3` Tax declaration — 6A, pre-tax, previous employment | Blocked — `W-32.1` | — |
| #42 | `W-32.4` Tax declaration — other income, tax summary | Blocked — `W-32.1` | — |
| #43 | `W-33.1` Tax calculator — new regime | Blocked — `W-32` | — |
| #44 | `W-33.2` Tax calculator — old regime | Blocked — `W-33.1`; needs #146 | — |
| #45 | `W-33.3` Tax calculator — revisions | Blocked — `W-33.2` | — |
| #46 | `W-34` Proof of investment | Blocked — `W-32`, `W-15`, `W-21` | — |
| #47 | `W-35` Reimbursements & deductions | Blocked — `W-15`, `W-29` | — |
| #48 | `W-36` TDS, payslips & statements | Blocked — `W-29`, `W-33` | — |
| #49 | `W-37` Payroll dashboard | Blocked — `W-29` | — |
| #50 | `W-38` Prior payroll import — **no source table identified yet** | Blocked — `W-29` | — |

---

## 5. Stream E — HRMS

| # | Ticket | Status | Owner |
|---|---|---|---|
| #51 | `W-39` Attendance | Blocked — `W-15`, `W-19` | — |
| #52 | `W-40` Overtime | Blocked — `W-15`, `W-19` | — |
| #53 | `W-41` Projects, tasks, assignments | Ready | — |
| #54 | `W-42` Timesheets | Blocked — `W-41`, `W-15` | — |
| #55 | `W-43` Timesheet reminders | Blocked — `W-42`, `W-20` | — |
| #56 | `W-44` HRMS dashboards | Blocked — `W-42` | — |

---

## 6. Stream F — Frontend

On `main` today: the shell skeleton with Keycloak login from `W-10` — 10 files under
`code/frontend/src/`. No business screen exists.

| # | Ticket | Status | Owner |
|---|---|---|---|
| #57 | `W-45` Shell — navigation by entitlement, API layer, design tokens | Blocked — `W-12` | — |
| #58 | `W-46.1` Employee screens | Blocked — `W-45` | — |
| #59 | `W-46.2` Leave screens | Blocked — `W-45` | — |
| #60 | `W-46.3` Holiday and org setup screens | Blocked — `W-45` | — |
| #61 | `W-46.4` Approvals screens | Blocked — `W-45` | — |
| #62 | `W-46.5` Employee portal screens | Blocked — `W-45` | — |
| #63 | `W-47.1` Salary structure screens | Blocked — `W-45` | — |
| #64 | `W-47.2` Pay run screens | Blocked — `W-45` | — |
| #65 | `W-47.3` Tax and declaration screens | Blocked — `W-45` | — |
| #66 | `W-47.4` Claims screens | Blocked — `W-45` | — |
| #67 | `W-47.5` Payroll dashboard screens | Blocked — `W-45` | — |
| #68 | `W-48` HRMS screens — rewritten from MUI to Ant Design | Blocked — `W-45` | — |

---

## 7. Product items in streams G and H

| # | Ticket | Spec | Status | Owner | Built by |
|---|---|---|---|---|---|
| #75 | `W-55` Index & query standard | approved | **Done** — merged 2026-09-24 | BirenGit | BirenGit |
| #85 | `W-65` Admin console | — | Blocked — `W-12` | — | — |
| #86 | `W-66` Marketing website | rev 3 in review | **In flight** — `W-66-marketing-website`, spec only | Gau318 | — |

---

## 8. What to watch

| Risk | Why |
|---|---|
| `W-16` leave engine | A **merge** of two systems, and leave, payroll loss-of-pay and three screen tickets wait on it |
| Floating-point money in the payroll port | `W-26` must fix it while porting. `Money` or `BigDecimal`, never `double` — CI enforces it |
| HRMS has no org scoping at all | 0 of 39 entities (`BUG-002`). Every HRMS table gains `tenant_id` on the way across |
| Post-freeze production fixes | The frozen snapshots date from 2026-09-13 and **do not update** (`D-17`). Keep a list of fixes made since, or they are lost at cutover |
| Owners not recorded | 13 merged tickets have no GitHub assignee. Claim before building |

---

## Related

- Every ticket and its features: [../target-state/08-work-plan.md](../target-state/08-work-plan.md)
- What to build first: [../target-state/09-build-order.md](../target-state/09-build-order.md)
- Tables: [../target-state/02-data-model.md](../target-state/02-data-model.md)
- Rules new code must follow: [../CONVENTIONS.md](../CONVENTIONS.md)
- Other trackers: [INFRA-TRACKER.md](INFRA-TRACKER.md) · [HARNESS-TRACKER.md](HARNESS-TRACKER.md) · [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md)
