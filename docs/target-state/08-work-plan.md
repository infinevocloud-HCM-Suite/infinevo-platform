# 08 — Work Plan

> The full list of work to reach the target state, broken to feature level.
> **Work items only** — no estimates, no dates, no team. Those live in
> `MANAGEMENT_SUMMARY_AND_PLAN.md`.
>
> Ordered by dependency. **Migration is last.** **There is no code or data sync** (`D-17`) —
> code is ported deliberately as each item is built.

---

## How to read this

| Column | Meaning |
|---|---|
| **Work item** | The unit you assign to someone |
| **Features** | What is actually built inside it. The estimating unit |
| **Delivers** | Capability identifiers from `01` |
| **Needs** | Must exist first |
| **Kind** | `New` · `Port` · `Merge` · `Infra` |

**The four kinds carry very different risk.** `Port` is mechanical. `New` has no migration but
no reference either. `Merge` is where the project can go wrong. `Infra` is well-trodden.

---

## Stream A — Foundation · 4 items · 17 features

| ID | Work item | Features | Delivers | Needs | Kind |
|---|---|---|---|---|---|
| `W-01` | Repository & module skeleton | Repo creation and branch rules · Maven multi-module with enforced dependency graph · Package conventions · Shared module (tenant context, error envelope, money types) · Website repo created | — | — | New |
| `W-02` | Local development stack | Compose stack (9 containers) · Schema bootstrap on start · **Seed data: two tenants with different module sets** · Mail catcher | — | `W-01` | New |
| `W-03` | Build & test pipeline | Compile gate · Lint gate · Test gate · Image build | `PLAT-11` | `W-01` | Infra |
| `W-04` | Test foundation | Unit test setup · Integration tests against a real database · Test data builders · Coverage reporting | — | `W-01` | New |

> **Code hygiene disappears as a phase.** The management summary assumed cleaning the existing
> repositories. With `D-17` there is no sync, so code is cleaned **as it is ported**. The 350
> lint errors and the duplicate entities never arrive.

---

## Stream B — Data foundation · 5 items · 19 features

| ID | Work item | Features | Delivers | Needs | Kind |
|---|---|---|---|---|---|
| `W-05` | Postgres & schemas | Server provisioning · Four schemas · Three database roles | — | `W-01` | New |
| `W-06` | Flyway | Migration runner · Script conventions · Per-schema ordering · Pipeline validation | — | `W-05` | New |
| `W-07` | Tenant model | `tenant_id` standard · Row-level security policies · Role grants · **Build check failing on an unscoped table** | `CORE-01` | `W-06` | New |
| `W-08` | Tenant binding filter | Token claim extraction · Membership verification · Database session binding · Failure handling | `CORE-01` | `W-07` | New |
| `W-09` | Reference schema & seed | Generic lookups · Tax master tables · Seed scripts · Annual update process | `CORE-15`, `PAY-10` | `W-06` | Port |

> `W-08` is small and high-leverage. An endpoint that forgets the tenant returns **nothing**
> rather than everything.

---

## Stream C — Core platform · 17 items · 75 features

| ID | Work item | Features | Delivers | Needs | Kind |
|---|---|---|---|---|---|
| `W-10` | Identity | Realm configuration · Login flow · Token validation · User profile sync · Password reset delegated to Keycloak | `CORE-02` | `W-08` | Port |
| `W-11` | Authorization | Role management · Action catalogue · Role-to-action mapping · Permission check API · Permission caching | `CORE-03` | `W-10` | Port |
| `W-12` | Tenant, subscription & entitlement | Tenant management · Organisation creation flow · Module selection · **Subscription status — the payment seam** · API entitlement enforcement · Navigation entitlement feed | `CORE-01`, `PLAT-01` | `W-10` | New |
| `W-13` | Employee master | Employee record · Personal details · Contact · Identification · Employment history · Bank details · Search and listing | `CORE-04` | `W-08` | **Merge** |
| `W-14` | Org structure & hierarchy | Department · Designation · Work location · **Reporting line (new)** · Org chart read model | `CORE-05`, `CORE-06` | `W-13` | Port + New |
| `W-15` | Approval engine | Approval definition · Instance lifecycle · Step routing via the reporting line · Delegation and escalation · Approval history | `CORE-11` | `W-14` | New |
| `W-16` | Leave engine | Leave types · Leave policy · Allocation · Request and cancel · Approval · Balance calculation · Consumption · Loss-of-pay derivation · Bulk import | `CORE-07` | `W-15` | **Merge** |
| `W-17` | Holiday calendar | Calendar per work location · Holiday management · Bulk import | `CORE-08` | `W-14` | Merge |
| `W-18` | Loss-of-pay & working-day policy | Policy model · Working-day basis · Derivation rules · **Policy stamped on every pay figure** | `CORE-09` | `W-16`, `W-17` | New |
| `W-19` | Pay input ledger | Write API for modules · Read API for the pay run · Period locking | `CORE-10` | `W-13` | New |
| `W-20` | Notifications | Template management · Email delivery · In-app notification · Reminder rules · Scheduler | `CORE-12` | `W-13` | Port + New |
| `W-21` | Document store | Upload · Download by signed link · Blob lifecycle and retention · Access control | `CORE-13` | `W-13` | Port |
| `W-22` | Audit trail | Change capture · Audit query · Retention policy | `CORE-14` | `W-07` | New |
| `W-23` | Reporting & export | Report definitions · Spreadsheet and CSV export · Scheduled reports | `CORE-16` | `W-13` | New |
| `W-24` | Setup checklist & invitations | **Module-aware checklist** · Progress tracking · User invitation · Employee invitation | `CORE-17`, `CORE-18` | `W-12` | Port |
| `W-25` | Employee self-service portal | My profile · My leave · My documents · My payslips (Payroll only) · My timesheet (HRMS only) | `CORE-19` | `W-16` | Port |
| `W-39` | Attendance & overtime capture (basic) | Attendance record — present, absent, half day · Overtime record · **Administrator entry for a Payroll-only tenant** · Writes overtime to the pay input ledger | `CORE-20`, `CORE-21` | `W-13`, `W-19` | Port + Move |

---

## Stream D — Payroll module · 13 items · 55 features

| ID | Work item | Features | Delivers | Needs | Kind |
|---|---|---|---|---|---|
| `W-26` | Salary catalogue & structure | Earning, deduction, benefit and reimbursement definitions · CTC structure · Component assignment · **Effective-dated revisions** · ⚠️ **Fix the floating-point money fields and the three competing amount fields while porting** | `PAY-01`, `PAY-02` | `W-13` | Port |
| `W-27` | Flexible benefit plan | Plan definition · Employee declaration · FBP components | `PAY-03` | `W-26` | Port |
| `W-28` | Pay schedule | Schedule configuration · **Working-day basis the calculation actually reads** · Cut-off and pay date | `PAY-04` | `W-18` | Port + New |
| `W-29` | Pay run | Run creation · Employee inclusion · Earnings and deductions computation · Loss-of-pay application · Pay input collection · **Asynchronous execution on the worker** · Run status and progress · Run locking | `PAY-05` | `W-28`, `W-19`, `W-52` | **Merge** |
| `W-30` | Off-cycle & one-time | Off-cycle run · One-time payout · Bonus | `PAY-06`, `PAY-07` | `W-29` | Port |
| `W-31` | Statutory components | Provident fund · State insurance · Professional tax · Slab configuration · **Per-tenant override** | `PAY-08` | `W-26` | Port |
| `W-32` | Income tax declaration | Declaration window · Section entries (10 detail areas) · Submission and revision | `PAY-09` | `W-26` | Port |
| `W-33` | Tax calculator | Old regime · New regime · Revisions · Section deductions · Tax summary | `PAY-10` | `W-32`, `W-09` | Port |
| `W-34` | Proof of investment | Submission · Document upload · Verification workflow · Reviewer comments · Rejection and resubmission | `PAY-11` | `W-32`, `W-15`, `W-21` | Port |
| `W-35` | Reimbursements & deductions | Claim submission · Approval · Payroll feed · Ad-hoc salary deduction | `PAY-12`, `PAY-13` | `W-15`, `W-29` | Port |
| `W-36` | TDS, payslips & statements | Tax deducted records · Payslip generation · **Signed link, signature never logged** · Annual statement | `PAY-14`, `PAY-15` | `W-29`, `W-33` | Port |
| `W-37` | Payroll dashboard | Run status · Summary widgets | `PAY-16` | `W-29` | Port |
| `W-38` | Prior payroll import | Import template · Validation · Load. ⚠️ **No table identified — confirm during scoping** | `PAY-17` | `W-29` | Port |

> **Tax is the largest and most compliance-exposed area** (`W-32`–`W-34`). Sequence it late.

---

## Stream E — HRMS module · 5 items · 19 features

| ID | Work item | Features | Delivers | Needs | Kind |
|---|---|---|---|---|---|
| `W-39` | Attendance | Attendance record · Clock in and out · Multiple sessions a day · Regularization request and approval · **Preferences moved over from Payroll** | `HRMS-01`–`04` | `W-15` | Port + Move |
| `W-40` | Overtime | Overtime form · Approval · **Write to pay input ledger.** Independent of attendance | `HRMS-05` | `W-15`, `W-19` | Port |
| `W-41` | Projects, tasks, assignments | Project management · Task management · Employee assignment | `HRMS-06`–`08` | `W-13` | Port |
| `W-42` | Timesheets | Weekly timesheet · Project entry · Day entry · Task entry · Submit · Approve | `HRMS-09` | `W-41`, `W-15` | Port |
| `W-43` | Timesheet reminders | Reminder rules · Escalation · Notification trigger | `HRMS-10` | `W-42`, `W-20` | Port |
| `W-44` | HRMS dashboards | Manager view · Employee view | `HRMS-11` | `W-42` | Port |

---

## Stream F — Frontend · 4 items · 21 features

| ID | Work item | Features | Delivers | Needs | Kind |
|---|---|---|---|---|---|
| `W-45` | Shell | Layout · **Navigation driven by entitlement** · **A real API service layer** (Payroll has none today) · Keycloak adapter · Runtime configuration · Design tokens | `PLAT-01` | `W-12` | New |
| `W-46` | Core screens | Employee · Leave · Holiday · Org setup · Approvals · Employee portal | `CORE-*` | `W-45` | Merge |
| `W-47` | Payroll screens | Salary structure · Pay run · Tax and declarations · Claims · Dashboard | `PAY-*` | `W-45` | Port |
| `W-48` | HRMS screens | Attendance · Timesheet · Projects · Dashboards. **Rewritten from MUI to Ant Design** | `HRMS-*` | `W-45` | Port |

---

## Stream G — Infrastructure · 7 items · 30 features

Can run in parallel with C, D and E from early on.

| ID | Work item | Features | Delivers | Needs | Kind |
|---|---|---|---|---|---|
| `W-49` | Containerisation — **merged 2026-09-17 (#116)** | Backend Dockerfile · Frontend Dockerfile · Keycloak image · **`app` and `worker` roles from one image, selected by `INFINEVO_ROLE`** (`D-48`) | — | `W-01` | Infra |
| `W-50` | Azure infrastructure as code | Resource groups · Container Apps · Postgres · Redis · Service Bus · Blob · Registry | `PLAT-12` | `W-49` — **merged, so this is unblocked** | Infra |
| `W-51` | Networking & identity | Front Door · Web application firewall · Private endpoints · Managed identity · Key Vault · **India region** | `PLAT-09` | `W-50` | Infra |
| `W-52` | Queue & worker | Queue setup · Job dispatch · Job status and progress · **Scheduler locking so jobs stop firing twice** | `PLAT-04` | `W-50` | New |
| `W-53` | Caching | Cache abstraction · Permission cache · Master data cache · Invalidation | `PLAT-05` | `W-50` | New |
| `W-54` | Deployment pipeline | Image promotion, never rebuilt · Migration step · Revision switch · Rollback by traffic shift | `PLAT-11` | `W-50`, `W-03` | Infra |
| `W-55` | Index & query standard | Index conventions, tenant-leading · Related-data fetching in one query · Connection pooling | `PLAT-06` | `W-07` | New |

> `W-55` is calibrated to your scale. At a thousand employees the employee tables are trivial.
> The tables that matter are those growing with time — attendance, pay run lines, tax detail.

---

## Stream H — Security, operations & go-to-market · 11 items · 37 features

| ID | Work item | Features | Delivers | Needs | Kind |
|---|---|---|---|---|---|
| `W-56` | Secrets | Key Vault setup · Managed identity wiring · Rotation process | `PLAT-09` | `W-51` | Infra |
| `W-57` | Deny-by-default authentication | Security configuration · Explicit exception list · **Build-time check failing on a new unlisted public endpoint** | `PLAT-09` | `W-10` | New |
| `W-58` | Tenant isolation tests | Cross-tenant read tests · Row-level security verification · Pipeline integration | `PLAT-09` | `W-08` | New |
| `W-59` | Scanning | Dependency scan · Code scan · Container scan | `PLAT-09` | `W-03` | Infra |
| `W-60` | Observability | Structured logging · Tracing · Metrics · Health endpoints · Dashboards | `PLAT-07` | `W-50` | Infra |
| `W-61` | Alerting | Alert rules · Routing to a person · On-call process | `PLAT-08` | `W-60` | Infra |
| `W-62` | Backup & disaster recovery | Backup configuration · **Tested restore** · Recovery runbook | `PLAT-10` | `W-50` | Infra |
| `W-63` | Load test | Scenarios · Baseline · Regression run | `PLAT-14` | `W-54` | Infra |
| `W-64` | Penetration test | External engagement · Remediation | `PLAT-09` | `W-57`, `W-58` | Infra |
| `W-65` | Admin console | Tenant list · Subscription management · Module toggle · Support impersonation · Audit view. **The real onboarding tool, since there is no payment step** | `PLAT-02` | `W-12` | New |
| `W-66` | Marketing website | Module pages · Feature comparison · Pricing · Lead capture · Help centre · Blog. Separate repo, **no platform integration** | `PLAT-13` | — | New |

---

## Stream I — Migration

**Last. Nothing here touches production data until everything above works.**

| ID | Work item | Features | Needs | Kind |
|---|---|---|---|---|
| `W-67` | Migration rules | Employee deduplication rules · Leave authority decision · Timesheet data decision · Duplicate entity resolution · Loss-of-pay reconciliation approach. **Written and agreed before a single row moves** | Streams C–E complete | New |
| `W-68` | Migration engine | Extract from two MySQL databases · Transform and merge · Load into Postgres · Restartability | `W-67` | New |
| `W-69` | Document migration | Reference rewrite · Blob copy · Verification | `W-21`, `W-68` | New |
| `W-70` | Reconciliation | Row counts · Financial totals · Per-employee comparison · **Loss-of-pay mismatch log for finance** | `W-68` | New |
| `W-71` | Rehearsals | Rehearsal automation · Result capture · Repeat until boring | `W-70` | Infra |
| `W-72` | Cutover | Freeze window · Switch · Smoke test · **Rollback runbook** | `W-71` | Infra |

### Migration questions, answered at `W-67`

Deliberately closed as target-state questions because they only affect what data is carried.

| Question |
|---|
| Which of the two live timesheet systems holds data worth keeping |
| Which duplicate leave and balance entities hold live rows |
| Which side seeds Core's leave tables — note the pay run currently reads HRMS |
| Whether both products genuinely hold live employee data |
| How the two existing loss-of-pay calculations are reconciled |

---

## Summary

| Stream | Items | Features | Can start |
|---|---|---|---|
| A — Foundation | 4 | 17 | **Immediately** |
| B — Data foundation | 5 | 19 | After A |
| C — Core platform | 17 | 75 | After B |
| D — Payroll module | 13 | 55 | After Core employee and leave |
| E — HRMS module | 5 | 19 | After Core approvals |
| F — Frontend | 4 | 21 | After entitlement |
| G — Infrastructure | 7 | 30 | **Immediately, in parallel** |
| H — Security & go-to-market | 11 | 37 | Mostly parallel |
| I — Migration | 6 | 22 | **Last** |
| | **72** | **295** | |

**Three things could start on day one** with no dependency: the repository skeleton (`W-01`),
the infrastructure definitions (`W-49`, `W-50`), and the marketing website (`W-66`). Of
those, `W-01` and `W-49` are merged; `W-50` is unblocked and `W-66` is still open.

**Two items carry most of the risk**, both merges: the employee master (`W-13`) and the leave
engine (`W-16`). Everything downstream waits on them.

**Work by kind:**

| Kind | Items | What it means |
|---|---|---|
| Port | 30 | Existing code moved and cleaned. Mechanical |
| New | 24 | Green field. No migration, no reference |
| Infra | 14 | Well-trodden |
| Merge | 4 | **Where the project can go wrong** |

---

## Related

- Capabilities: `01` · Tables: `02` · Decisions: `07`
- Phases, durations, team: `MANAGEMENT_SUMMARY_AND_PLAN.md`
