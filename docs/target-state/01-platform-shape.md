# 01 — Platform Shape

> Target state. What the product *is*. Names capabilities, never tables — tables are `02`.
> Status: decided unless a row says otherwise. Reasons live in `07-decisions.md`.

---

## 1. The three-layer model

One platform, three layers. A customer buys modules; **Core is never optional**.

```
                ┌──────────────┐   ┌──────────────┐
                │ HRMS module  │   │Payroll module│   ← bought, per subscription
                └──────┬───────┘   └──────┬───────┘
                       │  may depend on   │
                       └────────┬─────────┘
                          ┌─────▼─────┐
                          │   Core    │              ← everyone gets this
                          └───────────┘
```

**Rules, enforced by the build and the schema:**

| # | Rule |
|---|---|
| 1 | Core is installed for every tenant, regardless of what was bought |
| 2 | HRMS may depend on Core. Payroll may depend on Core |
| 3 | **HRMS and Payroll may never reference each other**, in code or in the schema |
| 4 | Core may never reference a module |
| 5 | Anything both modules need is Core, by definition |
| 6 | Cross-module data passes through `CORE-10 Pay Input Ledger`, never directly |

**Why rule 3 has an escape hatch.** Overtime is earned in HRMS and paid in Payroll. Rather
than let the modules see each other, HRMS *writes* to the pay input ledger in Core and
Payroll *reads* from it. A Payroll-only tenant finds that ledger empty, which is correct.

---

## 2. The placement rule

When it is unclear whether something is Core or a module, apply in order:

1. **Do both products need it?** → Core.
2. **Would a single-module customer be broken without it?** → Core.
3. **Is it the thing the customer is actually buying?** → Module. *Note (`D-35`): the thing
   being bought may be the **experience** around data rather than the data itself.*
4. **Otherwise** → the module that owns the data, with a Core contract if the other needs it.

Worked example: attendance. Every customer needs to record absence, so **capture is Core**.
But clocking in and out, correcting a missed punch and having a manager approve it is what a
customer buys. → **capture Core, experience HRMS.** See `D-35`.

---

## 3. Core capabilities

Twenty-one. Every tenant has all of them.

| ID | Capability | What it does | Origin |
|---|---|---|---|
| `CORE-01` | Tenant registry | The customer record everything hangs off. Company details, status, setup state. | Payroll |
| `CORE-02` | Identity & single sign-on | One login for the whole platform. Token issue, session, password reset. | Payroll (Keycloak). HRMS token system retired |
| `CORE-03` | Authorization & roles | Who may do what, scoped per tenant. Role-to-action mapping. | Payroll (already tenant-aware) |
| `CORE-04` | Employee master | The single employee record: personal, contact, identification, employment, bank. | **Merge of both** |
| `CORE-05` | Org structure | Departments, designations, work locations as master records. | Payroll. HRMS free text converted |
| `CORE-06` | Reporting hierarchy | Who reports to whom. Required to route leave approvals. | **New build** |
| `CORE-07` | Leave engine | Types, policy, allocation, request, approval, consumption, loss of pay. | **Both halves** — request/approval from HRMS, allocation/consumption from Payroll |
| `CORE-08` | Holiday calendar | Per-location holiday lists feeding leave and pay. | Merge |
| `CORE-09` | Loss-of-pay & working-day policy | Per-tenant rules deciding how loss of pay is derived, and what a day of pay is worth (`D-07`, `D-27`). Attendance is its only optional extra input (`D-34`). | **New build.** Seeded from the pay schedule and attendance-preference flags that exist but are ignored today |
| `CORE-10` | Pay input ledger | The only channel by which a module hands pay-affecting values to Payroll. | **New build** |
| `CORE-11` | Approval workflow | One engine covering leave, reimbursement, investment proof, overtime and attendance regularization (`D-33`). | **New build**, replacing five hard-coded paths |
| `CORE-12` | Notifications & email | Templates, delivery, reminder scheduling. Also covers payroll events — payslip published, declaration window, proof rejected (`D-32`). | HRMS. Payroll has none today |
| `CORE-13` | Document store | Employee documents, payslips, proofs. Azure Blob. | Merge, moved off Cloudinary |
| `CORE-14` | Audit trail | Who changed what, when, per tenant. | **New build. Neither side has it** |
| `CORE-15` | Reference data | Generic lookups: countries, states, banks, currencies. | Merge |
| `CORE-16` | Reporting & export | One export path, replacing per-screen code. | **New build** |
| `CORE-17` | Setup checklist | Onboarding progress, assembled from the tenant's modules. | Payroll, made module-aware |
| `CORE-18` | Invitations | Inviting company users and employees. | Payroll |
| `CORE-19` | Employee self-service portal | One portal for the employee, showing whichever modules the tenant holds (`D-31`). | Payroll's employee portal, generalised |
| `CORE-20` | Attendance capture (basic) | Present, absent and half-day records. **Administrator data entry for a Payroll-only tenant** (`D-35`). Optional input to `CORE-09`. | Moved from HRMS |
| `CORE-21` | Overtime capture (basic) | Approved overtime hours and amount, recorded and written to `CORE-10`. **Administrator data entry for a Payroll-only tenant** (`D-35`). | Moved from HRMS |

**Six are new builds** (`06`, `09`, `10`, `11`, `14`, `16`). Green field, no migration.
**Three are merges** (`04`, `07`, `08`). These carry the project's highest risk.

---

## 4. HRMS module

Ten (two moved to Core). Only for tenants who bought HRMS.

| ID | Capability | What it does |
|---|---|---|
| `HRMS-01` | Attendance capture | Daily attendance records |
| `HRMS-02` | Clock sessions | Clock in and out, first-in / last-out derivation |
| `HRMS-03` | Regularization | Correcting missed or wrong punches, with approval |
| `HRMS-04` | Attendance preferences | Hour thresholds, full/half day rules, pay-treatment flags. **Moved from Payroll, where it is orphaned** |
| `HRMS-05` | Overtime | **Manual form, approved, then paid. Independent of attendance (`D-28`).** Approved overtime is written to `CORE-10` for the pay run to collect |
| `HRMS-06` | Projects | Project records and client context |
| `HRMS-07` | Tasks | Work items under a project |
| `HRMS-08` | Assignments | Employee-to-project allocation |
| `HRMS-09` | Timesheets | Weekly time capture against projects and tasks |
| `HRMS-10` | Timesheet reminders | Reminder and escalation rules when timesheets are late |
| `HRMS-11` | HRMS dashboards | Manager and employee views |
| `HRMS-12` | **Employee request workflows** | Leave requests, attendance regularization requests and overtime requests, each employee-submitted and manager-approved. **This is the HRMS differentiator (`D-35`).** Without it the same data is entered by an administrator |

> ⚠️ **`HRMS-09` blocker.** Two complete timesheet systems are live today — separate
> controllers, services, repositories and DTOs. One must be retired, with a data decision,
> **before** this module is ported. See `.claude/outputs/2026-09-11-hrms-duplicate-entities.md`.

**What HRMS sells.** Time, attendance, overtime, project tracking and timesheets. Attendance
and overtime are deliberately module-only so that a customer who needs them must buy HRMS
(`D-04`, `D-05`).

---

## 5. Payroll module

Seventeen. Only for tenants who bought Payroll.

| ID | Capability | What it does |
|---|---|---|
| `PAY-01` | Salary structure & CTC | Per-employee salary breakup and its revisions |
| `PAY-02` | Salary components | Earning and deduction definitions |
| `PAY-03` | Flexible benefit plan | Employee-declared benefit allocation |
| `PAY-04` | Pay schedule | Cycle, cut-off dates, pay day rules, and the working-day basis the pay run must actually read (`D-27`) |
| `PAY-05` | Pay run | The monthly run. Reads `CORE-10` for module-supplied inputs |
| `PAY-06` | Off-cycle pay runs | Runs outside the normal cycle |
| `PAY-07` | One-time payouts | Bonuses and ad-hoc payments |
| `PAY-08` | Statutory components | Provident fund, state insurance, professional tax, and per-tenant overrides |
| `PAY-09` | Income tax declaration | Annual employee declaration |
| `PAY-10` | Tax calculator | Old and new regime, with revisions |
| `PAY-11` | Proof of investment | Document-backed verification of declarations |
| `PAY-12` | Reimbursement claims | Claim, approve, feed into pay |
| `PAY-13` | Ad-hoc salary deductions | One-off deductions applied to a run |
| `PAY-14` | Tax deducted at source | Per-employee deduction records |
| `PAY-15` | Payslips & annual statements | Employee-facing outputs |
| `PAY-16` | Payroll dashboard | Run status and summaries |
| `PAY-17` | Prior payroll import | Mid-year onboarding of existing payroll history |

**Tax is the largest area in the platform**, spanning `PAY-09` to `PAY-11` and `PAY-14`. It
also carries the most compliance risk, so it should be touched **last**.

---

## 6. Cross-cutting platform capabilities

Fourteen. Not sold, but required for the platform to be enterprise-ready.

| ID | Capability | What it does |
|---|---|---|
| `PLAT-01` | Subscription & entitlement | What was bought, and enforcement of it |
| `PLAT-02` | Admin console | Internal: tenants, subscriptions, module toggles, support impersonation |
| `PLAT-03` | API gateway | Single entry, entitlement checks, rate limiting. **Deferred** (`D-11`) |
| `PLAT-04` | Async job queue | Pay runs, reports and scheduled jobs on a worker |
| `PLAT-05` | Caching | Permissions, master data, tenant config. Replaces the in-memory cache |
| `PLAT-06` | Index & query performance | Indexes and query fixes. Highest-return work in the programme |
| `PLAT-07` | Observability | Logs, traces, metrics, dashboards, health checks |
| `PLAT-08` | Alerting | Errors, latency, queue depth, failed pay runs, certificates |
| `PLAT-09` | Security hardening | Secrets, isolation tests, encryption, endpoint audit, scanning |
| `PLAT-10` | Backup & disaster recovery | Tested restores against agreed targets |
| `PLAT-11` | Build & deploy pipeline | Merge to running environment, automatically |
| `PLAT-12` | Infrastructure as code | Azure defined in the repository |
| `PLAT-13` | Marketing website | Public site. Separate repo, **no platform integration** (`D-13`) |
| `PLAT-14` | Load testing | Repeatable test at target scale |

---

## 7. Subscription and entitlement

| Aspect | Decision |
|---|---|
| Granularity | Module level: HRMS, Payroll. Core is implicit and always on |
| Combinations | HRMS only · Payroll only · Both |
| Upgrade | Add a module at any time. No re-onboarding, because the employee already exists in Core |
| Downgrade | Module switched off; data retained, not deleted |
| Enforcement | Two places, always together: the API refuses, **and** the navigation hides |
| Grant | Customer chooses modules during organisation creation (`D-12`) |
| Payment | **Deferred.** A status field on the subscription is the seam |

**Enforcement rule.** A hidden menu item with a live endpoint behind it is a security bug,
not a cosmetic one. Entitlement is checked server-side on every request; the UI only
reflects it.

---

## 8. Organisation creation flow

Self-serve, as Payroll works today. Requires an authenticated account; nothing more.

```
register ─► create organisation ─► choose modules ─► [ payment slots here ] ─► setup checklist ─► invite users
                                        │                                            │
                                   subscription                              assembled from the
                                   created (status)                          chosen modules only
```

**The payment seam (`D-12`).** The subscription row is created the moment modules are
chosen, carrying a status. Today it goes straight to active. When payment is built, it moves
the status from pending to active and setup waits. **One field, no restructuring.**

**The checklist becomes dynamic.** Today it is a fixed nine-step Payroll list. In the target
state it is assembled from the tenant's modules, so a HRMS-only customer never sees
provident fund or pay schedule.

| Step | Shown when |
|---|---|
| Work locations, employees, departments, designations | Always (Core) |
| Attendance preferences, projects | HRMS bought |
| Pay schedule, salary components, prior payroll, org tax, EPF, ESI, PT | Payroll bought |

---

## 9. What each purchase option gets

| | Core | HRMS | Payroll |
|---|---|---|---|
| **HRMS only** | ✅ | ✅ | ❌ |
| **Payroll only** | ✅ | ❌ | ✅ |
| **Both** | ✅ | ✅ | ✅ |

**Capability by purchase option:**

| Capability | HRMS only | Payroll only | Both |
|---|---|---|---|
| Employee master, org structure | ✅ | ✅ | ✅ |
| Leave: apply, approve, balances | ✅ | ✅ | ✅ |
| Holiday calendar | ✅ | ✅ | ✅ |
| Loss of pay from leave | ✅ (informational) | ✅ (paid) | ✅ (paid) |
| Attendance records | ✅ | ✅ (admin entry) | ✅ |
| Overtime records | ✅ | ✅ (admin entry, paid) | ✅ |
| **Employee requests leave / regularization / overtime** | ✅ | ❌ **admin entry instead** | ✅ |
| Clock in and out, sessions | ✅ | ❌ | ✅ |
| Reimbursement claims, employee-submitted and approved | ❌ | ✅ | ✅ |
| Investment proof submission and verification | ❌ | ✅ | ✅ |
| Projects, tasks, timesheets | ✅ | ❌ | ✅ |
| Pay runs, payslips, tax, statutory | ❌ | ✅ | ✅ |
| Reimbursements, deductions | ❌ | ✅ | ✅ |

**What the tiers really are (`D-35`).** Every customer can *record* leave, attendance and
overtime. What HRMS sells is the **employee experience around them**: the employee applies,
the manager approves, and there is a trail. A Payroll-only customer gets the same numbers,
keyed by an administrator.

**Two exceptions, deliberately** (`D-37`). Reimbursement claims and investment proofs are
employee-submitted and approved even for a Payroll-only tenant, because both are inherently
employee-originated and meaningless without Payroll.

**One consequence.** A Payroll-only tenant's loss of pay rests on an administrator keying
absences correctly, with no approval trail behind it. The audit trail (`CORE-14`) therefore
matters more in that tier, not less.

**Loss of pay when both are bought** is configurable per tenant (`D-07`, `CORE-09`). The pay
run must record which policy produced each figure, so any disputed payslip can be explained.

---

## 10. The capability catalogue

`PLAT-13` markets functionality. `PLAT-01` grants it. The application enforces it. If those
lists are maintained separately they will disagree, and you will sell something the product
cannot switch on.

**Rule: one catalogue, four consumers.**

| Consumer | Uses it for |
|---|---|
| Marketing website | Rendering module and comparison pages |
| Subscription | The set of grantable modules |
| Application | Runtime entitlement checks |
| This document | Section 3–6 above |

The platform publishes the catalogue. The website copies it (not a live feed — `D-13`). When
a capability moves between Core and a module, one change updates all four.

---

## Related

- Tables: `02-data-model.md` · Code: `03-code-structure.md` · Decisions: `07-decisions.md`
- Current → target rationale: `06-current-to-target.md`
