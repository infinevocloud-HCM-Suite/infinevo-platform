# 09 — Build Order

> How the 72 work items in `08` are actually sequenced, what can run **in parallel**, and
> enough detail per item to hand it to a developer.
>
> **No dates, no durations, no team names.** Waves are dependency order, not a calendar.
> `MANAGEMENT_SUMMARY_AND_PLAN.md` holds the schedule.

---

## 1. Five tracks, running at different speeds

The mistake to avoid is treating this as one queue. It is five, and four of them overlap.

| Track | Items | Starts | Blocks |
|---|---|---|---|
| **P — Platform backbone** | 44 | After foundations | Everything product-facing. **This is the critical path** |
| **I — Infrastructure** | 7 | **Day one** | Only the pay run (needs the queue) and deployment |
| **S — Security & operations** | 10 | From identity onward | Go-live, not development |
| **U — Frontend** | 4 | After entitlement exists | Nothing else |
| **W — Website** | 1 | **Day one** | Nothing. Fully independent |
| **M — Migration** | 6 | **Last** | Cutover only |

```
Wave      1      2      3      4      5      6      7      8      9
P       ███    ███    ███    ███    ███    ███    ███    ███
I       ███    ███    ███    ▒▒▒    ▒▒▒           ▒▒▒
S                     ███    ███    ▒▒▒    ▒▒▒    ███    ███
U                            ███    ███    ███    ███
W       ███    ▒▒▒    ▒▒▒
M                                                        ███   ███
        ███ primary work    ▒▒▒ background / trickle
```

**Two items have no dependency at all and can start on day one:** `W-01` repository skeleton
and `W-66` marketing website. `W-49` containerisation opened the moment `W-01` merged, so with
three people it was the third assignment rather than the first. Both are now merged.

---

## 2. The waves

### Wave 1 — Foundations
*Nothing else is safe until these exist.*

| Track | Items | Parallel? |
|---|---|---|
| P | `W-01` repository skeleton | — |
| P | `W-02` local dev stack · `W-03` build pipeline · `W-04` test foundation | **All three in parallel** after `W-01` |
| P | `W-05` Postgres and four schemas | Parallel with `W-02`–`W-04` |
| I | `W-49` containerisation | **Parallel from day one** — merged 2026-09-17 |
| W | `W-66` marketing website | **Fully independent, any time** |

### Wave 2 — Data platform
*The safety mechanism the whole product rests on.*

| Track | Items | Parallel? |
|---|---|---|
| P | `W-06` Flyway → `W-07` tenant model → `W-08` tenant binding filter | **Strictly sequential.** Each needs the previous |
| P | `W-09` reference schema and seed | Parallel with `W-07`–`W-08` |
| I | `W-50` Azure infrastructure as code | Parallel |
| I | `W-51` networking and identity · `W-52` queue and worker · `W-53` caching | **All three in parallel** after `W-50` |
| I | `W-54` deployment pipeline | Parallel, after `W-50` and `W-03` |
| S | `W-59` dependency and code scanning | Parallel, after `W-03` |

> `W-06` → `W-07` → `W-08` is the one genuinely rigid chain in the project. Everything
> product-facing sits behind it.

### Wave 3 — Identity and tenancy
*The first wave where a person can log in.*

| Track | Items | Parallel? |
|---|---|---|
| P | `W-10` identity → `W-11` authorization · `W-12` subscription and entitlement | `W-11` and `W-12` in parallel after `W-10` |
| P | `W-13` employee master | **Parallel with `W-10`.** Needs only `W-08` |
| P | `W-22` audit trail · `W-55` index and query standard | Parallel, need only `W-07` |
| S | `W-56` secrets to Key Vault · `W-57` deny-by-default auth · `W-58` tenant isolation tests | Parallel |
| I | `W-60` observability · `W-62` backup and recovery | Parallel |

> **`W-58` should land the same week as `W-08`.** The isolation tests are what prove the
> tenant binding works. Writing them later means trusting it in the meantime.

### Wave 4 — Core data and services
*Everything that hangs off the employee record.*

| Track | Items | Parallel? |
|---|---|---|
| P | `W-14` org structure and reporting hierarchy | After `W-13` |
| P | `W-19` pay input ledger · `W-20` notifications · `W-21` document store · `W-23` reporting and export | **All four in parallel.** Each needs only `W-13` |
| P | `W-39` attendance & overtime capture | Parallel, after `W-19` |
| P | `W-24` setup checklist and invitations · `W-65` admin console | Parallel, after `W-12` |
| U | `W-45` frontend shell | **Frontend starts here**, after `W-12` |
| I | `W-61` alerting | After `W-60` |

### Wave 5 — Core engines
*The heart of Core, and the first of the two risky merges.*

| Track | Items | Parallel? |
|---|---|---|
| P | `W-15` approval engine | After `W-14`. **Blocks a lot** |
| P | `W-16` leave engine ⚠️ **merge** · `W-17` holiday calendar | `W-17` parallel; `W-16` after `W-15` |
| P | `W-41` projects, tasks, assignments | **Parallel.** Needs only `W-13` |
| P | `W-26` salary catalogue and structure | **Parallel.** Needs only `W-13` |
| U | `W-46` core screens | Parallel, after `W-45` |
| S | `W-64` penetration test | After `W-57` and `W-58` |

### Wave 6 — Modules open up
*HRMS and Payroll can now be built by different people at the same time.*

| Track | Items | Parallel? |
|---|---|---|
| P | `W-18` loss-of-pay and working-day policy | After `W-16` and `W-17` |
| P | `W-25` employee portal | After `W-16` |
| P — HRMS | `W-40` clock attendance & request workflows · `W-42` timesheets | **Parallel with each other and with Payroll** |
| P — Payroll | `W-27` FBP · `W-31` statutory · `W-32` tax declaration | **Parallel with each other and with HRMS** |
| U | `W-47` payroll screens · `W-48` HRMS screens | Parallel |

> **This is where a second and third developer pay off most.** HRMS and Payroll no longer
> touch each other, and the build enforces it.

### Wave 7 — Pay run
*The second risky merge, and the point where Payroll becomes real.*

| Track | Items | Parallel? |
|---|---|---|
| P | `W-28` pay schedule → `W-29` pay run ⚠️ **merge** | Sequential. `W-29` also needs `W-19` and `W-52` |
| P | `W-33` tax calculator · `W-34` proof of investment | Parallel with the pay schedule |
| P | `W-43` timesheet reminders · `W-44` HRMS dashboards | Parallel, HRMS side finishing |

### Wave 8 — Completion and hardening
*Everything that needs a working pay run.*

| Track | Items | Parallel? |
|---|---|---|
| P | `W-30` off-cycle · `W-35` reimbursements and deductions · `W-36` TDS and payslips · `W-37` dashboard · `W-38` prior payroll | **All parallel** after `W-29` |
| S | `W-63` load test | After `W-54` |

### Wave 9 — Migration
*Last. Nothing before this touches production data.*

| Order | Items |
|---|---|
| 1 | `W-67` migration rules — **agreed in writing before a single row moves** |
| 2 | `W-68` migration engine · `W-69` document migration (parallel) |
| 3 | `W-70` reconciliation |
| 4 | `W-71` rehearsals, repeated until boring |
| 5 | `W-72` cutover and rollback |

---

## 3. Per-item development detail

Format: **Build** (what to make) · **Done when** (how you know) · **Watch** (the trap).

### Track P — Platform backbone

**`W-01` Repository skeleton**
Build: repo, Maven modules `core`/`hrms`/`payroll`/`app`/`worker`/`shared`, dependency graph declared, package conventions, frontend folders.
Done when: a build fails if `hrms` declares a dependency on `payroll`.
Watch: get the graph right now. Retrofitting a module boundary after code lands is the expensive version.

**`W-02` Local dev stack**
Build: compose file with 9 containers, schema bootstrap, seed with **two tenants holding different module sets**, mail catcher.
Done when: one command gives a working platform on a clean machine.
Watch: two tenants is not optional. One tenant means entitlement bugs surface only after a customer buys.

**`W-03` Build pipeline** · Build: compile, lint, test gates; image build. Done when: a failing test blocks a merge. Watch: turn the gates on from the first commit, not once there is code to fix.

**`W-04` Test foundation** · Build: unit setup, integration tests against a real Postgres, data builders, coverage. Done when: a test can create a tenant and an employee in three lines. Watch: integration tests must run against real Postgres, not an in-memory database, or row-level security is never exercised.

**`W-05` Postgres and schemas** — **merged 2026-09-16 (#110).** Built: canonical `infra/postgres/` scripts, four schemas owned by `migration_user`, four roles. Done: `app_user` is refused DDL, proven by `DatabasePrivilegesIT`. Watch: the roles are the whole point; do not let the app connect as owner "just for now".

**`W-06` Flyway** — **merged 2026-09-18 (`365a319`).** Built: the `migration` module runner, its `application.yml`, the script conventions in `code/backend/migration/README.md`, four fixture trees and `FlywayMigrationIT`. Done: `ddl-auto` is absent from every configuration file, and CI ran the integration tests green — 10 of 10, none skipped. Watch: script order across four schemas — `reference` first, then `core`, then modules. Note how it landed: inside PR #119, a documentation pull request that declared "no code changed", so it reached `main` without its own `/review` and `/verify`.

**`W-07` Tenant model** · Build: `tenant_id` standard, row-level security policies, grants, **a build check that fails on a table without a tenant column outside `reference`**. Done when: the check fails on a deliberately bad migration. Watch: the check is the deliverable. A convention nobody enforces decays within a month.

**`W-08` Tenant binding filter** · Build: extract tenant from token, verify membership, set on the database session, handle failure. Done when: a query with no tenant set returns zero rows, not all rows. Watch: this replaces passing an organisation id into 270 methods. Resist any endpoint that takes a tenant as a parameter.

**`W-09` Reference schema and seed** · Build: lookups, 11 tax master tables, seed scripts, the annual-update process. Done when: a tax slab change is one migration script. Watch: no tenant column here, deliberately. Document why, or someone will "fix" it.

**`W-10` Identity** · Build: realm config, login, token validation, profile sync, password reset delegated to Keycloak. Done when: one login reaches both modules. Watch: HRMS's token system is deleted, not adapted. Two auth paths is the problem being solved.

**`W-11` Authorization** · Build: roles, action catalogue, mapping, permission check, caching in Redis. Done when: a role change takes effect across both running instances. Watch: the cache must be shared. The current in-process one is why the system cannot run two replicas.

**`W-12` Subscription and entitlement** · Build: tenant management, organisation creation, module selection, **subscription status as the payment seam**, API enforcement, navigation feed. Done when: a Payroll-only tenant gets a 403 on an HRMS endpoint *and* no HRMS menu. Watch: both halves, always. A hidden menu over a live endpoint is a security bug.

**`W-13` Employee master** ⚠️ **merge** · Build: employee, personal, contact, identification, employment, bank, search. Done when: one employee record serves both modules. Watch: the two products model this differently and both may hold live data. The merge rules belong to `W-67`; here, build the target shape and keep it clean.

**`W-14` Org structure and hierarchy** · Build: department, designation, work location, **reporting line (new)**, org chart read model. Done when: a manager can be resolved for any employee. Watch: HRMS holds department and designation as free text. Converting it has no clean rule — treat it as its own problem, not a footnote.

**`W-15` Approval engine** · Build: definition, instance lifecycle, routing via reporting line, delegation and escalation, history. Done when: leave, reimbursement, proof, overtime and regularization all use it. Watch: five flows must fit. Design against all five before building, or it becomes leave approval with adapters.

**`W-16` Leave engine** ⚠️ **merge** · Build: types, policy, allocation, request, approval, balance, consumption, loss-of-pay derivation, import. Done when: a Payroll-only tenant can run leave end to end. Watch: request and approval come from HRMS, allocation and consumption from Payroll. Neither side is discarded, and the join is where the risk lives.

**`W-17` Holiday calendar** · Build: calendar per location, management, import. Done when: leave and pay both read one calendar. Watch: per work location, not per tenant. Payroll models locations properly; use that.

**`W-18` Loss-of-pay and working-day policy** · Build: policy model, working-day basis, derivation rules, **policy stamped on every pay figure**. Done when: two tenants on different settings produce correctly different figures from identical data. Watch: the stamp is not optional. Without it a disputed payslip cannot be explained later.

**`W-19` Pay input ledger** · Build: write API for modules, read API for the pay run, period locking. Done when: HRMS overtime reaches a payslip without Payroll knowing HRMS exists. Watch: keep it dumb. It is a ledger, not a calculation engine.

**`W-20` Notifications** · Build: templates, email delivery, in-app, reminder rules, scheduler. Done when: a payroll event sends an email — something Payroll has never done. Watch: composed in `app`, sent by `worker`. The queue joins them.

**`W-21` Document store** · Build: upload, signed-link download, lifecycle, access control. Done when: a payslip downloads from Blob Storage. Watch: signed links, never a public container. Copy the existing payslip token pattern, and never log the signature.

**`W-22` Audit trail** · Build: change capture, query, retention. Done when: every write to a salary or leave record is attributable. Watch: build it early. Retrofitting audit means backfilling nothing and losing the first months.

**`W-23` Reporting and export** · Build: definitions, spreadsheet and CSV export, scheduled reports. Done when: three screens use one export path. Watch: exports run on `worker`, reading the replica when one exists.

**`W-24` Setup checklist and invitations** · Build: module-aware checklist, progress, user and employee invitations. Done when: a HRMS-only tenant never sees a provident fund step. Watch: assembled from modules, not a fixed nine-step list.

**`W-25` Employee self-service portal** · Build: my profile, leave, documents, payslips (Payroll only), timesheet (HRMS only). Done when: the portal renders correctly for all three purchase combinations. Watch: every panel is entitlement-gated, server-side.

**`W-26` Salary catalogue and structure** · Build: component definitions, CTC structure, assignment, effective-dated revisions. **Fix the floating-point money fields and the three competing amount fields.** Done when: a revision creates a version rather than overwriting. Watch: the money-type fix is a correctness change riding a port. Do not defer it — it is far cheaper now than after data exists.

**`W-27` Flexible benefit plan** · Build: plan, employee declaration, components. Done when: a declaration flows into the structure. Watch: sits inside the salary structure, not beside it.

**`W-28` Pay schedule** · Build: schedule config, **working-day basis the calculation actually reads**, cut-off and pay date. Done when: changing the basis changes the payslip. Watch: today these fields exist and are ignored. Wiring them is the work.

**`W-29` Pay run** ⚠️ **merge** · Build: creation, inclusion, computation, loss-of-pay application, pay input collection, **async on the worker**, status and progress, locking. Done when: a 100-employee run completes on the worker with progress visible, and matches a hand calculation to the rupee. Watch: the run must be restartable and locked. Two runs for one period is the failure that costs real money.

**`W-30` Off-cycle and one-time** · Build: off-cycle run, one-time payout, bonus. Done when: a bonus reaches a payslip outside the cycle. Watch: this is a Payroll-only customer's substitute for overtime.

**`W-31` Statutory components** · Build: provident fund, state insurance, professional tax, slab config, per-tenant override. Done when: an override applies without touching shared reference data. Watch: the override pattern generalises the one that already exists. Follow it.

**`W-32` Tax declaration** · Build: window, 10 section detail areas, submission and revision. Done when: an employee completes a full declaration. Watch: the largest single area in the platform. Break it into the ten sections and build them separately.

**`W-33` Tax calculator** · Build: old regime, new regime, revisions, section deductions, summary. Done when: both regimes match a worked example for the current year. Watch: highest compliance exposure in the project. Test against real published figures, not invented ones.

**`W-34` Proof of investment** · Build: submission, upload, verification, comments, rejection and resubmission. Done when: a rejected proof returns to the employee with a reason. Watch: uses the approval engine, not its own workflow.

**`W-35` Reimbursements and deductions** · Build: claim, approval, payroll feed, ad-hoc deduction. Done when: an approved claim appears on the next payslip. Watch: approved but unpaid claims must not be lost between periods.

**`W-36` TDS, payslips and statements** · Build: tax records, payslip generation, **signed link with signature never logged**, annual statement. Done when: an employee opens a payslip from an email with no session. Watch: payslips are rendered from the pay run row, not stored. Immutability comes from locking the run.

**`W-37` Payroll dashboard** · Build: run status, summary widgets. Done when: it reads only, and holds no tables. Watch: today's dashboard is hard-coded. Do not port that.

**`W-38` Prior payroll import** · Build: template, validation, load. Done when: a mid-year tenant shows correct year-to-date figures. Watch: **no table identified in the design.** Confirm where this writes before starting.

**`W-39` Attendance** · Build: record, clock in and out, sessions, regularization with approval, **preferences moved over from Payroll**. Done when: Payroll's orphaned settings screen is gone and works here. Watch: attendance feeds only the loss-of-pay policy. It produces nothing else.

**`W-40` Clock-based attendance & request workflows (HRMS)** · Build: clock in and out, sessions, regularization with approval, attendance preferences, and the **leave / regularization / overtime request workflows**. Done when: an employee applies, a manager approves, and a trail exists — and none of it is reachable for a Payroll-only tenant. Watch: this is the HRMS differentiator. The same data exists either way; what is sold is the experience.

**`W-41` Projects, tasks, assignments** · Build: project, task, assignment. Done when: an employee can be assigned to a project. Watch: needs only the employee master. Start it early to parallelise.

**`W-42` Timesheets** · Build: weekly timesheet, project/day/task entries, submit, approve. Done when: one timesheet system exists, on the richer model. Watch: two complete systems run today. Build one; which data migrates is `W-67`.

**`W-43` Timesheet reminders** · Build: rules, escalation, notification trigger. Done when: a late timesheet produces one email, not two. Watch: scheduled on the worker, with locking.

**`W-44` HRMS dashboards** · Build: manager and employee views. Done when: read-only, no tables. Watch: same as `W-37`.

### Track U — Frontend

**`W-45` Shell** · Build: layout, **entitlement-driven navigation**, **a real API service layer**, Keycloak adapter, runtime config, design tokens. Done when: one build serves every environment and the menu reflects the tenant's modules. Watch: Payroll's frontend has no service layer — screens call HTTP directly and read tenant from local storage. Both habits stop here.

**`W-46` Core screens** · Build: employee, leave, holiday, org setup, approvals, portal. Done when: all three purchase combinations render correctly. Watch: a merge of two designs. Agree the pattern once, then repeat it.

**`W-47` Payroll screens** · Build: salary, pay run, tax, claims, dashboard. Done when: parity with today. Watch: the largest surface. Ant Design markup ports mostly intact.

**`W-48` HRMS screens** · Build: attendance, timesheet, projects, dashboards. Done when: parity with today. Watch: **rewritten from MUI to Ant Design.** Budget for a rewrite, not a port.

### Track I — Infrastructure *(parallel from day one)*

**`W-49` Containerisation** — **merged 2026-09-17 (#116).** Built: three production Dockerfiles, all non-root; `app` and `worker` run from one image, selected by `INFINEVO_ROLE` rather than a Spring profile (`D-48`); the frontend takes its configuration at container start, so one image serves every environment. Done: the same image runs both roles, proven on CI. Watch: no secrets in images, ever — and the check must read layer **contents**, not just build instructions. It did not, and a development realm carrying three live credentials shipped inside the Keycloak image and scanned clean. CI now runs all three parts of that scan.

**`W-50` Azure infrastructure as code** · Build: resource groups, Container Apps, Postgres, Redis, Service Bus, Blob, registry. Done when: an environment is created from the repository, not a portal. Watch: build dev first and rebuild it twice. An environment you cannot recreate is not infrastructure as code.

**`W-51` Networking and identity** · Build: Front Door, firewall, private endpoints, managed identity, Key Vault, **India region**. Done when: the database has no public endpoint. Watch: region is a compliance decision and hard to change afterwards.

**`W-52` Queue and worker** · Build: queue, dispatch, status and progress, **scheduler locking**. Done when: two worker instances run a scheduled job once. Watch: this is why the platform cannot run two replicas today. It is a correctness fix, not a scaling one.

**`W-53` Caching** · Build: abstraction, permission cache, master data cache, invalidation. Done when: a permission change is visible on both instances immediately. Watch: invalidation is the hard half. Design it before the cache.

**`W-54` Deployment pipeline** · Build: promotion without rebuild, migration step, revision switch, rollback by traffic shift. Done when: a rollback takes seconds. Watch: **schema does not roll back.** Every migration must leave the previous release able to run.

**`W-55` Index and query standard** · Build: tenant-leading index conventions, related-data fetching in one query, connection pooling. Done when: no list screen issues a query per row. Watch: calibrated to your scale — the tables that matter are those growing with time, not with headcount.

### Track S — Security and operations

**`W-56` Secrets** · Build: Key Vault, managed identity wiring, rotation process. Done when: no credential exists in any file in the repository. Watch: **rotate the current Keycloak administrative password before this, not as part of it.** That is a live exposure.

**`W-57` Deny-by-default authentication** · Build: security config, explicit exception list, **build-time check on new public endpoints**. Done when: adding an unlisted public endpoint fails the build. Watch: exactly two exceptions — signed payslip links and health probes.

**`W-58` Tenant isolation tests** · Build: cross-tenant read tests, row-level security verification, pipeline integration. Done when: a deliberately unscoped query fails the test. Watch: land this with `W-08`, not after.

**`W-59` Scanning** · Build: dependency, code and container scanning. Done when: it runs on every merge. Watch: fix the initial backlog once, or the noise gets ignored permanently.

**`W-60` Observability** · Build: structured logging, tracing, metrics, health endpoints, dashboards. Done when: one request can be followed across app, worker and database. Watch: tag every log with tenant and correlation id from the start.

**`W-61` Alerting** · Build: rules, routing to a person, on-call process. Done when: a failed pay run wakes somebody. Watch: alert on business events, not only technical ones.

**`W-62` Backup and disaster recovery** · Build: backup config, **tested restore**, runbook. Done when: a restore has actually been performed. Watch: an untested backup is not a backup.

**`W-63` Load test** · Build: scenarios, baseline, regression run. Done when: a 100-employee pay run plus 100 concurrent users has a recorded baseline. Watch: modest at your scale. Do not over-invest.

**`W-64` Penetration test** · Build: external engagement, remediation. Done when: findings are closed or accepted in writing. Watch: after isolation tests, so the obvious issues are already gone.

**`W-65` Admin console** · Build: tenant list, subscription management, module toggle, impersonation, audit view. Done when: a tenant can be provisioned without touching the database. Watch: **the real onboarding tool**, since there is no payment step. Not a nicety.

### Track W — Website *(independent)*

**`W-66` Marketing website** · Build: module pages, comparison, pricing, lead capture, help centre, blog. Done when: marketing can change copy without a developer. Watch: **no platform integration.** The capability list is a copied file, not a live feed.

### Track M — Migration *(last)*

**`W-67` Migration rules** · Build: employee deduplication, leave authority, timesheet data decision, duplicate entity resolution, loss-of-pay reconciliation. Done when: agreed **in writing** before a row moves. Watch: this is where the questions deferred from the design return.

**`W-68` Migration engine** · Build: extract, transform, merge, load, restartability. Done when: it runs twice and produces the same result. Watch: restartable, always. A migration that cannot resume is a migration you only get one attempt at.

**`W-69` Document migration** · Build: reference rewrite, blob copy, verification. Done when: every document opens from Blob Storage. Watch: verify by opening files, not by counting rows.

**`W-70` Reconciliation** · Build: row counts, financial totals, per-employee comparison, **loss-of-pay mismatch log for finance**. Done when: finance signs the mismatch log. Watch: the migration is done when the numbers match, not when the script finishes.

**`W-71` Rehearsals** · Build: automation, result capture, repetition. Done when: three consecutive rehearsals are clean. Watch: rehearse on acceptance, which has the same shape as production.

**`W-72` Cutover** · Build: freeze window, switch, smoke test, **rollback runbook**. Done when: cutover completes and the rollback window closes. Watch: schedule outside payroll week. Old system stays available, read-only.

---

## 4. Parallelism, at a glance

**Maximum useful parallel width, by wave:**

| Wave | Parallel streams | Best use of a second and third person |
|---|---|---|
| 1 | 3 | Repo · containers · website |
| 2 | 4 | Data chain · Azure · pipeline · scanning |
| 3 | 5 | Identity · employee master · audit · security · observability |
| 4 | 6 | Org structure · four Core services · frontend shell |
| 5 | 5 | Approvals → leave · holidays · projects · salary · Core screens |
| 6 | **7 — the widest point** | HRMS ×3 · Payroll ×3 · frontend |
| 7 | 4 | Pay run chain · tax ×2 · HRMS finishing |
| 8 | 5 | Five Payroll items, all independent |
| 9 | 1 | Migration is sequential by nature |

**What is never parallel.** The Flyway → tenant model → tenant binding chain in Wave 2. The
pay schedule → pay run chain in Wave 7. And the whole of migration.

**What is always parallel.** Infrastructure, from day one. Security, from identity onward.
The website, from the first day to the last.

---

## Related

- Work items and features: `08` · Capabilities: `01` · Tables: `02` · Decisions: `07`
- Phases, durations, team: `MANAGEMENT_SUMMARY_AND_PLAN.md`
