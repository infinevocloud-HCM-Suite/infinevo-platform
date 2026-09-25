# Dev Tracker

> The product itself — foundation, data, core platform, payroll, HRMS, frontend.
> Streams A to F, plus the product items in G and H.
> **GitHub is authoritative.** Status legend: [README.md](README.md).
> Last refreshed: **2026-09-25**, against `main` — every row checked against a merge commit. Three lanes assigned 2026-09-25: **sayeed** (defects, then employee), **krushna** (tenant and onboarding), **devashis** (documents, notifications, reporting).

## Summary

| Stream | Tickets | Code on main | Feature done | Where it stands |
|---|---|---|---|---|
| A — Foundation | 2 | 2 | 2 | Done |
| B — Data foundation | 5 | 5 | 5 | **Done.** Tenancy chain complete; suite green again (`W-04.1`) |
| C — Core platform | 26 | 9 | 9 | **Building.** `W-04.1` and `W-11.3` merged `3350cf2`; every corrected spec is now unblocked on codes |
| D — Payroll | 21 | 0 | 0 | All blocked on `W-13.3` onward and `W-26` |
| E — HRMS | 5 | 0 | 0 | All blocked on `W-15` |
| F — Frontend | 12 | 0 | 0 | All blocked on `W-45`, which is blocked on `W-12` |
| G/H — product items | 3 | 1 | 1 | `W-55` merged `2ccd723`. `W-66` unassigned |

**The core is being built.** The foundation is finished and eight Stream C tickets are on
`main`: identity, the audit trail, the employee record and its five detail sections, the
three org masters, the role and permission pair, and the catalogue correction. Payroll, HRMS and the frontend have
not started.

## Assignments

One branch per developer, `dev-<name>`. Tickets run in the order listed; a developer
claims the next one when the previous is on `main`. Migration numbers are reserved per
lane so two lanes never collide on a version.

| Developer | Branch | Lane | Order | Migrations |
|---|---|---|---|---|
| sayeed | `dev-sayeed` | Defects, then employee | `W-52.1` → `W-53.1` → `W-13.4` → `W-09.1` → D-9 manual checks → `W-13.3` → `W-14.2` → `W-39.1` → `W-19` | `V026`–`V032` |
| krushna | `dev-krushna` | Tenant and onboarding | `W-12.1` → `W-12.2` → `W-12.3` → `W-24.1` → `W-17` | `V033`–`V036` |
| devashis | `dev-devashis` | Documents, notifications, reporting | `W-21` → `W-20.1` → `W-23.1` | `V037`–`V040` |

**2026-09-25.** sayeed holds every open defect (D-2, D-3, D-6, D-7, D-8, D-9) and follows
them with the employee chain, since `W-13.4` and `W-13.3` both touch `core.employee`. No
`dev-<name>` branch exists yet — the stray `dev-claude` and `W-10-identity` branches were
deleted the same day. Each developer creates `dev-<name>` from `main` when they start their
first ticket. `W-11.3` is on `main`, so nothing waits on permission codes.

The specs still cite migration numbers already used on `main`; **use the lane's reserved block,
not the number in the spec.** `V026` `W-13.4` · `V027` `W-09.1` · `V028`–`V029` `W-14.2` ·
`V030` `W-39.1` · `V031`–`V032` `W-19` · `V033`–`V034` `W-12.1` · `V035` `W-24.1` · `V036` `W-17` ·
`V037` `W-21` · `V038`–`V039` `W-20.1` · `V040` `W-23.1`.

**Assign later, cross-lane:** `W-24.2` (needs `W-12.1` and `W-20.1`), `W-20.2` (needs `W-20.1`
and `W-12.1`), `W-22.2` and `W-23.2` (need `W-20.2` and `W-52.1`), `W-15`, `W-16`, `W-18`, `W-25`
(need `W-14.2`).

## Defects on `main`

One row per known defect in merged code. A row leaves this table only when its fix is on
`main`. Status: `open` · `spec ready` · `assigned` · `fixed`.

| # | Defect | Found | Fixed by | Status |
|---|---|---|---|---|
| D-1 | `mvn verify` fails: `shared`'s `DatabasePrivilegesIT` hits `53300 too_many_connections` (16 test contexts × pool of 10 > 100 slots) | 2026-09-25, running the suite | `W-04.1` | **fixed** `3350cf2` |
| D-2 | Worker never reads the queue; no consumer loop, producer bean only in `worker`, no retry-then-fail, `RUNNING` jobs re-run | 2026-09-24, `12-core-contracts.md` §5 | `W-52.1` | assigned — sayeed |
| D-3 | `GET /jobs/{id}` has no `@RequiresAction` and honours a legacy `organizationId` header | 2026-09-24 | `W-52.1` | assigned — sayeed |
| D-4 | Every tenant's seeded `platform-admin` role holds `core.tenant.provision` (platform staff only) | 2026-09-24 | `W-11.3` | **fixed** `3350cf2` |
| D-5 | Core actions catalogued as `hrms.*` (leave, holiday, attendance) — a module filter would strip them from a Payroll-only tenant | 2026-09-24 | `W-11.3` | **fixed** `3350cf2` |
| D-6 | No link from `core.employee` to `core.user_account`; `*_own` actions and the portal cannot resolve the caller | 2026-09-24 | `W-13.4` | assigned — sayeed |
| D-7 | Dead duplicates: `core.cache.PermissionCacheService`, `PermissionInvalidationService`, `core.queue.*` | 2026-09-24 | `W-53.1` | assigned — sayeed |
| D-8 | Tax slab seed has only `GENERAL`; senior and super-senior over-deducted (#146) | 2026-09-22 | `W-09.1` | assigned — sayeed |
| D-9 | `W-10` spec §8 login flow never run by hand; `W-14.1` §8 never independently re-run | at merge | sayeed runs the two §8 checks | assigned — sayeed |

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

See **D-8** in [Defects on `main`](#defects-on-main), fixed by `W-09.1`.

---

## 3. Stream C — Core platform

Seven merged. All unbuilt specs were corrected on 2026-09-25 (§3a). **Build `W-11.3` first**:
it renames and adds the permission codes every other spec now cites. `W-13.3`, `W-39.1`,
`W-13.4`, `W-53.1` and `W-09.1` do not depend on it and can start at once.

### 3a. Spec corrections (2026-09-25)

The first read of all 36 Core specs together (`docs/target-state/12-core-contracts.md` §5)
found 23 corrections. **All applied 2026-09-25** — each spec carries a `Corrected` header row
naming the rows it took. Kept here as the record of what changed.

| Spec | Needs correction for |
|---|---|
| `W-12.1`, `W-12.2`, `W-12.3` | tenant columns, port placement, cache bump, action codes in the feed |
| `W-14.2` | `asOf` on the chain; permission codes |
| `W-15.1`, `W-15.2`, `W-15.3` | step JSON, two missing flows, approved amount, reassign endpoint, circular block |
| `W-16.1`, `W-16.2`, `W-16.3`, `W-16.4a`, `W-16.4b` | exceed-balance modes, frequencies, statuses, on-behalf entry, file names |
| `W-17` | three scripts, two unique indexes |
| `W-18.1`, `W-18.2` | weekday set from pay schedule, class name, no-policy rule |
| `W-19` | period lock design, idempotency key, append-only grants |
| `W-20.1`, `W-20.2` | event list, reminder-rule columns, `/reminder-rules` API, `notification` queue |
| `W-21` | document kinds, limits, link life |
| `W-22.2`, `W-23.1`, `W-23.2` | tenant sweep visibility, `ReportSource`, async export as a job |
| `W-24.1`, `W-24.2`, `W-25` | role join table, invitation lifecycle, `PortalPanelProvider` |

Every spec above names its `@RequiresAction` codes. **`W-11.3` is on main (`3350cf2`)**, so that
blocker is cleared for all of them.

### 3b. Correction tickets

| Ticket | What it is | Fixes | Ready? |
|---|---|---|---|
| `W-11.3` Catalogue correction | Rename the 14 Core actions misfiled as `hrms.*`, add 24 missing codes, take `core.tenant.provision` out of the seeded `platform-admin` role. Migration `V025` | `12-core-contracts.md` §4 | **on main `3350cf2`** · done — built by claude |
| `W-13.4` Employee login link | `user_account_id` on `core.employee`; employees may edit their own personal and contact sections. Migration `V026` | §5 row 14 | **assigned — sayeed** |
| `W-52.1` Worker fix | The queue consumer loop, producer bean in `app`, retry-then-fail, running-job idempotency, `@RequiresAction` on `/jobs/{id}` | §5 row 21 | **assigned — sayeed** |
| `W-53.1` Cache cleanup | Delete the unused `core.cache` permission classes and the `core.queue` package | §5 row 22 | **assigned — sayeed** |
| `W-04.1` Test connection budget | `mvn verify` **fails on main** (2026-09-25, reproducible serially): `shared`'s `DatabasePrivilegesIT` dies with `53300 too_many_connections`. 16 `@SpringBootTest` classes each hold a Hikari pool of 10 against a 100-slot Testcontainers Postgres. Fix: one shared test context config with a small pool, or raise the container's `max_connections` | suite green | **on main `3350cf2`** · done — built by claude |
| `W-09.1` Age category seed | Seed `SENIOR` and `SUPER_SENIOR` slab rows for three financial years (defect #146). Migration `V027` | Stream B defect | **assigned — sayeed** |

`W-52.1` and `W-53.1` are backend fixes to tickets tracked in [INFRA-TRACKER.md](INFRA-TRACKER.md).

| # | Ticket | What it is | Ready? |
|---|---|---|---|
| #11 | `W-10` Identity | Realm configuration, login flow, token validation, user profile sync, password reset delegated to Keycloak | **on main `ac1e531`** · code done — **spec §8 login never run by hand** |
| #14 | `W-13.1` Employee record | The neutral root, `core.employee` (`V010`), isolated by row-level security rather than a remembered `WHERE` | **on main `7d0bab6`** · done |
| #14 | `W-13.2` Employee detail | Five one-to-one sections (`V015`–`V019`) — personal, contact, identification, employment, bank. **Turned the audit trail on**, and fixed the `@Embedded` redaction gap | **on main `5228385`** · done |
| #14 | `W-13.3` Employee search & listing | Search and listing over the employee record | **assigned — sayeed**, after `W-13.4`; must filter `is_deleted`; `EmployeeResponse.from` is an N+1 here |
| #26 | `W-22.1` Audit trail | Change capture, `core.audit_log` (`V008`), `GET /api/v1/audit` | **on main `6fb4012`** · done — capturing since `W-13.2` |
| #— | `W-22.2` Audit retention | Retention sweep and purge | spec approved; layer 3 in practice — also needs `W-20.1`, `W-20.2` |
| #12 | `W-11.1` Role & action catalogue | 63-action catalogue in `reference.action`, tenant-scoped roles, seven system roles seeded per tenant, role and grant API | **on main `172eaaa`** · spec approved · code done |
| #12 | `W-11.2` Permission check & cache | `@RequiresAction` on every endpoint, 403 when not held, shared Redis cache that every replica reloads on a role change | **on main `23d1126`** · spec approved · code done |
| #13 | `W-12` Tenant, subscription & entitlement | Tenant management, organisation creation, module selection, subscription status — the payment seam — and entitlement enforcement on both API and navigation | **assigned — krushna** (`W-12.1` → `.2` → `.3`) |
| #15 | `W-14.1` Org masters | Department, designation and work location (`V011`–`V013`), plus the three nullable columns on `core.employee` (`V014`). Free-text conversion deliberately left to `W-67` | **on main `235aab2`** · code done — §8 verification not independently re-run |
| #15 | `W-14.2` Reporting line | The new reporting line and the org chart read model | **assigned — sayeed**, after `W-13.3` |
| #16 | `W-15` Approval engine | Approval definitions, instance lifecycle, step routing along the reporting line, delegation and escalation, history | `W-14.2` — unassigned |
| #17–20 | `W-16.1`–`.4` Leave engine | Types and policy · allocation and balance · request, approval and documents · consumption, loss-of-pay derivation and bulk import. **The other riskiest ticket — a merge** | `W-15` — unassigned |
| #21 | `W-17` Holiday calendar | Calendar per work location, holiday management, bulk import | **assigned — krushna**, after `W-24.1` |
| #22 | `W-18` Loss-of-pay & working-day policy | Policy model, working-day basis, derivation rules, the policy stamped on every pay figure | `W-16`, `W-17` — unassigned |
| #23 | `W-19` Pay input ledger | Write API for modules, read API for the pay run, period locking | **assigned — sayeed**, after `W-39.1` |
| #24 | `W-20` Notifications | Templates, email delivery, in-app notification, reminder rules, scheduler | **assigned — devashis** (`W-20.1`); `W-20.2` assigned later, needs `W-12.1` |
| #25 | `W-21` Document store | Upload, download by signed link, blob lifecycle and retention, access control | **assigned — devashis** |
| #27 | `W-23` Reporting & export | Report definitions, spreadsheet and CSV export, scheduled reports | **assigned — devashis** (`W-23.1`); `W-23.2` needs `W-20.2` |
| #28 | `W-24` Setup checklist & invitations | A module-aware checklist, progress tracking, user and employee invitation | **assigned — krushna** (`W-24.1`), after `W-12.1`; `W-24.2` needs `W-20.1` |
| #29 | `W-25` Employee self-service portal | My profile, leave, documents, payslips (Payroll only), timesheet (HRMS only) | `W-16` — unassigned |
| #— | `W-39.1` Attendance capture (basic) | `core.attendance` — present, absent, half day per employee per date, entered by an administrator, so a Payroll-only tenant can record it (`D-35`) | **assigned — sayeed**, after `W-14.2` |
| #— | `W-39.2` Overtime capture (basic) | `core.overtime_request` — approved overtime entered by an administrator, written to the pay input ledger | `W-19` — no spec yet |

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
| #50 | `W-38` Prior payroll import | Import template, validation, load. **Source settled 2026-09-25: a fixed spreadsheet template, one row per employee per month**, loaded through the `W-16.4b` import pattern |

> **Tax is the largest and most compliance-exposed area.** `W-33.2` (#44) was next in the
> queue once `W-09` shipped its 15 reference tables, and carries three conditions from
> `W-09`'s review: Chapter VI-A has no `financial_year`, `home_loan_rule_master` has no
> regime column, and loss carry-forward defaults FALSE.

---

## 5. Stream E — HRMS

Nothing started. All blocked on `W-15` approval engine.

> **Renumbered 2026-09-24**, following `10-scoping.md:93,117`. `W-39` is Core's basic
> attendance and overtime capture (`D-35`), now in Stream C. HRMS attendance and the
> overtime request are one ticket, `W-40`.

| # | Ticket | What it is |
|---|---|---|
| #51–52 | `W-40` Clock attendance & request workflows | Clock in and out, multiple sessions a day, attendance preferences moved over from Payroll, and the employee-submitted, manager-approved regularization and overtime requests. Blocked on `W-39`, `W-15`, `W-16` — unassigned |
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
| #75 | `W-55` Index & query standard | Tenant-leading index conventions, related-data fetching in one query, connection pooling. Calibrated to scale — the tables that matter are the ones growing with time: attendance, pay run lines, tax detail | **on main `2ccd723`** · done |
| #85 | `W-65` Admin console | Tenant list, subscription management, module toggle, support impersonation, audit view. **The real onboarding tool, since there is no payment step** | blocked on `W-12` |
| #86 | `W-66` Marketing website | Module pages, feature comparison, pricing, lead capture, help centre, blog. Separate repo, no platform integration | unassigned — separate repo |

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

- Every ticket and its features: [../target-state/08-work-plan.md](../target-state/08-work-plan.md)
- What to build first: [../target-state/09-build-order.md](../target-state/09-build-order.md)
- Tables: [../target-state/02-data-model.md](../target-state/02-data-model.md)
- Rules new code must follow: [../CONVENTIONS.md](../CONVENTIONS.md)
- Other trackers: [INFRA-TRACKER.md](INFRA-TRACKER.md) · [HARNESS-TRACKER.md](HARNESS-TRACKER.md) · [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md)
