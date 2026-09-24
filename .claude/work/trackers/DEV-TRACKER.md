# Dev Tracker

> The product itself — foundation, data, core platform, payroll, HRMS, frontend.
> Streams A to F, plus the product items in G and H.
> **GitHub is authoritative.** Status legend: [README.md](README.md).
> Last refreshed: **2026-09-22**; `W-11` rows updated **2026-09-24**. Other rows not yet refreshed.

## Summary

| Stream | Tickets | Code on main | Feature done | Where it stands |
|---|---|---|---|---|
| A — Foundation | 2 | 2 | 2 | Done |
| B — Data foundation | 5 | 5 | 5 | **Done.** Tenancy chain complete |
| C — Core platform | 19 | 0 | 0 | Two tickets claimable, the rest blocked behind them |
| D — Payroll | 21 | 0 | 0 | All blocked on `W-13` and `W-26` |
| E — HRMS | 6 | 0 | 0 | All blocked on `W-15` |
| F — Frontend | 12 | 0 | 0 | All blocked on `W-45`, which is blocked on `W-12` |
| G/H — product items | 3 | 0 | 0 | `W-66` claimed |

**Nothing in the product is built yet.** The foundation is finished, the first business
table has not been written.

---

## 1. Stream A — Foundation

| # | Ticket | What it is | Spec | Code | Feature |
|---|---|---|---|---|---|
| #1 | `W-01` Repository & module skeleton | Seven Maven modules with the `hrms` ↔ `payroll` boundary enforced by the build, package conventions, shared module holding tenant context, error envelope and money types | approved | on main | **done** |
| #5 | `W-04` Test foundation | Unit test setup, integration tests against a real database, test data builders, coverage reporting | approved | on main | **done** |

`W-02` local stack and `W-03` build pipeline are also stream A — they live in
[INFRA-TRACKER.md](INFRA-TRACKER.md) because they are containers and CI.

---

## 2. Stream B — Data foundation

| # | Ticket | What it is | Spec | Code | Feature |
|---|---|---|---|---|---|
| #6 | `W-05` Postgres & schemas | Four schemas owned by `migration_user`, four roles, none superuser and none `BYPASSRLS`. One set of SQL scripts that local Docker, Testcontainers and Azure all run | approved | on main | **done** |
| #7 | `W-06` Flyway | Migration runner, script conventions, per-schema ordering, pipeline validation | approved | on main | **done** — runs four locations |
| #8 | `W-07` Tenant model | `tenant_id` standard, row-level security policies, role grants, and a build check that fails on an unscoped table | approved | on main | **done** |
| #9 | `W-08` Tenant binding filter | Token claim extraction, membership verification, database session binding, failure handling | approved | on main | **done** |
| #10 | `W-09` Reference schema & seed | Generic lookups, 15 tax master tables, seed scripts, annual update process | approved | on main | **done** — backend suite now 100 passing, none skipped |

> **What is in is the mechanism, not the coverage.** A request carries its tenant from
> the JWT through `TenantContextFilter` into the database session, so row-level security
> has something to match on. **No business table is under it yet.** For reference, the
> frozen system is org-scoped on 63 of 97 payroll entities and on **none** of HRMS's 39
> (`BUG-002`).

### Open defect

| # | What | Size |
|---|---|---|
| #146 | `W-09` seeded only `age_category = 'GENERAL'`. A senior citizen gets ₹2.5L exemption instead of ₹3L and a super-senior instead of ₹5L — both over-deducted. `W-33` cannot fix it without a new migration | S |

---

## 3. Stream C — Core platform

Nothing started. `W-10`, `W-13` and `W-22` are claimable now; everything else waits.

| # | Ticket | What it is | Ready? |
|---|---|---|---|
| #11 | `W-10` Identity | Realm configuration, login flow, token validation, user profile sync, password reset delegated to Keycloak | **ready** |
| #14 | `W-13` Employee master | The employee record — personal, contact, identification, employment history, bank details, search and listing. **A merge of two systems, and one of the two riskiest tickets in the plan** | **ready** |
| #26 | `W-22` Audit trail | Change capture, audit query, retention policy | **ready** |
| #12 | `W-11.1` Role & action catalogue | 63-action catalogue in `reference.action`, tenant-scoped roles, seven system roles seeded per tenant, role and grant API | **on main `172eaaa`** · spec approved · code done |
| #12 | `W-11.2` Permission check & cache | `@RequiresAction` on every endpoint, 403 when not held, shared Redis cache that every replica reloads on a role change | **on main `23d1126`** · spec approved · code done |
| #13 | `W-12` Tenant, subscription & entitlement | Tenant management, organisation creation, module selection, subscription status — the payment seam — and entitlement enforcement on both API and navigation | `W-10` |
| #15 | `W-14` Org structure & hierarchy | Department, designation, work location, a new reporting line, org chart read model | `W-13` |
| #16 | `W-15` Approval engine | Approval definitions, instance lifecycle, step routing along the reporting line, delegation and escalation, history | `W-14` |
| #17–20 | `W-16.1`–`.4` Leave engine | Types and policy · allocation and balance · request, approval and documents · consumption, loss-of-pay derivation and bulk import. **The other riskiest ticket — a merge** | `W-15` |
| #21 | `W-17` Holiday calendar | Calendar per work location, holiday management, bulk import | `W-14` |
| #22 | `W-18` Loss-of-pay & working-day policy | Policy model, working-day basis, derivation rules, the policy stamped on every pay figure | `W-16`, `W-17` |
| #23 | `W-19` Pay input ledger | Write API for modules, read API for the pay run, period locking | `W-13` |
| #24 | `W-20` Notifications | Templates, email delivery, in-app notification, reminder rules, scheduler | `W-13` |
| #25 | `W-21` Document store | Upload, download by signed link, blob lifecycle and retention, access control | `W-13` |
| #27 | `W-23` Reporting & export | Report definitions, spreadsheet and CSV export, scheduled reports | `W-13` |
| #28 | `W-24` Setup checklist & invitations | A module-aware checklist, progress tracking, user and employee invitation | `W-12` |
| #29 | `W-25` Employee self-service portal | My profile, leave, documents, payslips (Payroll only), timesheet (HRMS only) | `W-16` |

---

## 4. Stream D — Payroll

Nothing started. Everything is blocked, most of it behind `W-26`.

| # | Ticket | What it is |
|---|---|---|
| #30 | `W-26` Salary catalogue & structure | Earning, deduction, benefit and reimbursement definitions, CTC structure, component assignment, effective-dated revisions. **Fix the floating-point money fields and the three competing amount fields while porting** |
| #31 | `W-27` Flexible benefit plan | Plan definition, employee declaration, FBP components |
| #32 | `W-28` Pay schedule | Schedule configuration, a working-day basis the calculation actually reads, cut-off and pay date |
| #33–36 | `W-29.1`–`.4` Pay run | Creation, inclusion and locking · earnings and deductions · loss-of-pay and pay input collection · async execution on the worker with status and progress. **A merge.** Needs `W-52` |
| #37 | `W-30` Off-cycle & one-time | Off-cycle run, one-time payout, bonus |
| #38 | `W-31` Statutory components | Provident fund, state insurance, professional tax, slab configuration, per-tenant override |
| #39–42 | `W-32.1`–`.4` Income tax declaration | Window, submission and revision · house rent, home loan, let-out property · section 6A, pre-tax deductions, previous employment · other income and tax summary |
| #43–45 | `W-33.1`–`.3` Tax calculator | New regime · old regime with section deductions · revisions and recalculation |
| #46 | `W-34` Proof of investment | Submission, document upload, verification workflow, reviewer comments, rejection and resubmission |
| #47 | `W-35` Reimbursements & deductions | Claim submission, approval, payroll feed, ad-hoc salary deduction |
| #48 | `W-36` TDS, payslips & statements | Tax deducted records, payslip generation, signed link with the signature never logged, annual statement |
| #49 | `W-37` Payroll dashboard | Run status, summary widgets |
| #50 | `W-38` Prior payroll import | Import template, validation, load. **No source table identified — confirm during scoping** |

> **Tax is the largest and most compliance-exposed area.** `W-33.2` (#44) was next in the
> queue once `W-09` shipped its 15 reference tables, and carries three conditions from
> `W-09`'s review: Chapter VI-A has no `financial_year`, `home_loan_rule_master` has no
> regime column, and loss carry-forward defaults FALSE.

---

## 5. Stream E — HRMS

Nothing started. All blocked on `W-15` approval engine.

| # | Ticket | What it is |
|---|---|---|
| #51 | `W-39` Attendance | Attendance record, clock in and out, multiple sessions a day, regularization request and approval, preferences moved over from Payroll |
| #52 | `W-40` Overtime | Overtime form, approval, write to the pay input ledger. Independent of attendance |
| #53 | `W-41` Projects, tasks, assignments | Project and task management, employee assignment |
| #54 | `W-42` Timesheets | Weekly timesheet, project, day and task entry, submit, approve |
| #55 | `W-43` Timesheet reminders | Reminder rules, escalation, notification trigger |
| #56 | `W-44` HRMS dashboards | Manager view, employee view |

---

## 6. Stream F — Frontend

Nothing started. Everything waits on `W-45`, which waits on `W-12` entitlement.

| # | Ticket | What it is |
|---|---|---|
| #57 | `W-45` Shell | Layout, navigation driven by entitlement, **a real API service layer** (Payroll has none today), Keycloak adapter, runtime configuration, design tokens |
| #58–62 | `W-46.1`–`.5` Core screens | Employee · leave · holiday and org setup · approvals · employee portal |
| #63–67 | `W-47.1`–`.5` Payroll screens | Salary structure · pay run · tax and declarations · claims · dashboard |
| #68 | `W-48` HRMS screens | Attendance, timesheet, projects, dashboards. **Rewritten from MUI to Ant Design** |

---

## 7. Product items in streams G and H

| # | Ticket | What it is | Status |
|---|---|---|---|
| #75 | `W-55` Index & query standard | Tenant-leading index conventions, related-data fetching in one query, connection pooling. Calibrated to scale — the tables that matter are the ones growing with time: attendance, pay run lines, tax detail | **ready** |
| #85 | `W-65` Admin console | Tenant list, subscription management, module toggle, support impersonation, audit view. **The real onboarding tool, since there is no payment step** | blocked on `W-12` |
| #86 | `W-66` Marketing website | Module pages, feature comparison, pricing, lead capture, help centre, blog. Separate repo, no platform integration | claimed — Gau318 |

`W-52` queue & worker and `W-53` caching are backend work but sit in
[INFRA-TRACKER.md](INFRA-TRACKER.md), where the Azure resources they use are tracked.

---

## 8. What to watch

| Risk | Why |
|---|---|
| `W-13` employee master and `W-16` leave engine | Both are **merges** of two systems, and everything downstream waits on them. This is where the project can go wrong |
| Floating-point money in the payroll port | `W-26` must fix it while porting, not after. `Money` or `BigDecimal`, never `double` — CI enforces it |
| HRMS has no org scoping at all | 0 of 39 entities (`BUG-002`). Every HRMS table gains `tenant_id` on the way across |
| Post-freeze production fixes | The frozen snapshots date from 2026-09-13 and **do not update** (`D-17`). Payroll was under active development to the day of the freeze. Keep a list of fixes made since, or they are lost at cutover |

---

## Related

- Every ticket and its features: [../target-state/08-work-plan.md](../../../docs/target-state/08-work-plan.md)
- What to build first: [../target-state/09-build-order.md](../../../docs/target-state/09-build-order.md)
- Tables: [../target-state/02-data-model.md](../../../docs/target-state/02-data-model.md)
- Rules new code must follow: [../CONVENTIONS.md](../../../docs/CONVENTIONS.md)
- Other trackers: [INFRA-TRACKER.md](INFRA-TRACKER.md) · [HARNESS-TRACKER.md](HARNESS-TRACKER.md) · [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md)
