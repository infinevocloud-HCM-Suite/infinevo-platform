# 07 — Decision Log

> Append-only. A decision, its reason, its date. No design detail — that lives in `01`–`06`.
> **Any entry can be reopened** by adding a new dated entry that supersedes it. Never edit
> or delete a past decision; that is what makes this log worth reading.

---

## 1. Decisions taken

| ID | Date | Decision | Reason | Affects |
|---|---|---|---|---|
| `D-01` | 2026-09-11 | **Modular monolith.** One backend with Core, HRMS and Payroll as build modules — not separate services | All 58 Payroll tables reference Core. Separate services would mean a network call for employee and leave data on every operation. The boundary is enforced by the build instead | `03`, `04` |
| `D-02` | 2026-09-11 | **Worker role on the same image** for pay runs, reports and scheduled jobs | The pay run is a batch problem, not a throughput problem. Also fixes two scheduled jobs that fire twice when a second instance runs | `04` |
| `D-03` | 2026-09-11 | **Core leave engine built from both halves** — request/approval/documents from HRMS, allocation/consumption/loss-of-pay from Payroll | Investigation showed the two implement different halves of one engine, not two versions of the same one. Supersedes the management summary, which said build from HRMS and retire Payroll's | `01`, `02` |
| ~~`D-04`~~ | 2026-09-11 | ~~**Attendance is HRMS, not Core**~~ — **superseded by `D-35`, 2026-09-13.** Basic attendance capture is Core; only the clock-based experience and the request workflow are HRMS | Loss of pay comes from approved leave, so Payroll does not depend on attendance and a Payroll-only customer is not broken without it. It is therefore something a customer buys. Reverses my earlier recommendation, which rested on a dependency that does not exist | `01`, `02` |
| ~~`D-05`~~ | 2026-09-11 | ~~**Overtime is HRMS**~~ — **superseded by `D-35`, 2026-09-13.** A Payroll-only tenant records approved overtime directly; the request-and-approve workflow is HRMS | ⚠️ **Reason revised 2026-09-13.** Originally justified by overtime deriving from attendance hours; `D-28` removed that link, so the justification is now purely commercial — overtime is a reason to buy HRMS. **Known consequence: a Payroll-only customer cannot pay overtime at all, with no workaround.** Founder confirmed this deliberately | `01`, `02` |
| `D-06` | 2026-09-11 | **Timesheets stay in HRMS** | Project billing, not pay input. Sits naturally with attendance | `01`, `02` |
| `D-07` | 2026-09-11 | **Loss of pay is configurable per tenant** when both modules are held | Payroll's existing attendance preferences already carry flags for whether leaves, holidays and weekends count for pay, so configurability was the original intent. Requires that the pay run record which policy produced each figure | `01`, `02` |
| `D-08` | 2026-09-11 | **Tax reference data: shared base, per-tenant override.** Held in a `reference` schema with no tenant column | National law is identical for everyone, so an annual change should be one update. Per-tenant deviation already exists in code (professional tax override and history), so overrides generalise that pattern | `02` |
| `D-09` | 2026-09-11 | **Postgres, with Flyway.** `ddl-auto` disabled permanently | Row-level security is the isolation mechanism, which matters most because HRMS has no tenancy at all. `ddl-auto` silently alters production schema with no record | `02`, `03` |
| `D-10` | 2026-09-11 | **Azure Container Apps, not Kubernetes** | Five containers and a part-time team do not justify operating a cluster. Migrating later is not a rewrite | `05` |
| `D-11` | 2026-09-11 | **Defer the API gateway** | Front Door plus in-application authentication covers the early period. Gateway routing rules would need rewriting while module boundaries are still moving | `01`, `04` |
| `D-12` | 2026-09-11 | **Self-serve organisation creation, modules chosen before setup**, with a subscription status field as the payment seam | Matches how Payroll already works. The status field lets payment slot in between module choice and setup with one field change, no restructuring | `01` |
| `D-13` | 2026-09-11 | **Marketing website is a separate repository with no platform integration** | Content changes weekly, product code does not. Zero integration keeps it off the critical path and gives it no security surface | `03` |
| `D-14` | 2026-09-11 | **Trial sign-up and payment integration deferred to last** | Founder decision. Organisation creation remains self-serve; only the commercial gate is deferred | `01` |
| `D-15` | 2026-09-11 | **One repository for the platform** | Four repositories cannot see each other, so shared logic was copied. One repository plus enforced module dependencies makes sharing possible without a tangle | `03` |
| `D-16` | 2026-09-11 | **Architecture recommendations recorded as decided**, reopenable by a new entry | A log in which everything is provisional tells you nothing. Gives the feature list a baseline to build from | this file |
| `D-17` | 2026-09-11 | **No upstream sync mechanism.** The new codebase is built here, once. The four origin repos are reference and production, not a source to pull from | Founder decision. Removes the subtree migration and the `sync-upstream` skill entirely. **Consequence: any fix made to production after today must be re-applied by hand, or it regresses at cutover** | `03` §7 |
| `D-18` | 2026-09-11 | **Azure India region** | Indian payroll data under the Digital Personal Data Protection Act. Primary and backups both in-jurisdiction | `05` |
| `D-19` | 2026-09-11 | **Target scale: 10 tenants, max 100 employees per tenant** (~1,000 employees total) | Founder figure. Recalibrates several platform capabilities downward — see §4 | `02`, `04`, `05` |
| `D-20` | 2026-09-11 | **HRMS is pre-launch and will be sold.** Not dormant | Founder confirmation. Full tenancy retrofit on all 39 HRMS entities is required; rebuilding HRMS inside the Payroll codebase is off the table | `01`, `02` |
| `D-21` | 2026-09-11 | **One Keycloak realm** (`HRMS`), with tenant membership validated server-side per request — following Payroll's existing model. **Target-state improvement: resolve the tenant once in a filter and set it on the database session for row-level security, instead of passing it to 270 controller methods** | Payroll already runs one realm at `authentication.infinevocloud.com/realms/HRMS` for both apps, and `OrganizationRoleInterceptor` already verifies the caller's `OrganizationUserRoleMapping` for the target org before any `/api/**` or `/admin/**` request. The design is sound; the weakness is that the tenant is re-passed by hand everywhere rather than bound once | `01`, `02`, `03` |
| `D-22` | 2026-09-11 | **Deny-by-default authentication. No `permitAll()` on a path prefix.** Every endpoint authenticated unless it appears on an explicit, reviewed exception list. Exceptions must be signed, time-limited, rate-limited, and must never log the signature | Both products today grant blanket access to a prefix — HRMS `permitAll()` on `/public/**`, Payroll on `/api/public/**`. A prefix grant means any controller later mapped under it silently becomes public, which is exactly how the unauthenticated leave endpoint happened. The signed payslip link is the pattern to copy: it is a specific endpoint with an HMAC signature, not an open prefix | `01`, `03`, `05` |
| `D-23` | 2026-09-11 | **The HRMS→Payroll leave HTTP integration does not exist in the target state.** Core owns leave; Payroll reads it in-process | Removes the endpoint, its authentication question and its network hop entirely. `OQ-03` remains open only because it decides which data is authoritative *for the migration*, not whether the integration survives | `01`, `02` |
| `D-24` | 2026-09-13 | **One timesheet in the target state, built on the richer model** — weekly header with per-project, per-day and per-task entries, and a draft-to-submitted status. The older flat timesheet is not carried forward | Closes `OQ-01` as a *design* decision. Which of today's two live systems holds data worth keeping is a **migration** question, deferred to the migration phase, not a target-state one | `01`, `02` |
| `D-25` | 2026-09-13 | **One leave request table and one balance model in Core.** Today's duplicate leave and balance entities are not carried forward | Closes `OQ-02` as a design decision. Which of the duplicates holds live data is a migration question | `02` |
| `D-26` | 2026-09-13 | **Core is the single authority for leave.** No HTTP integration, no second source | Closes `OQ-03` for design. Which side's data seeds Core at migration time remains a migration question | `01`, `02` |
| `D-27` | 2026-09-13 | **Pay-per-day is configurable per tenant**, using the working-day settings the pay schedule already stores (which days are working days, how many, include weekends, include holidays). The calculation must actually read them | Those fields exist today and the pay run ignores them, dividing by raw calendar days instead. Making them work honours the original design and matches `D-07`. Every combination must be tested, and the payslip must record which basis produced the figure | `01`, `02` |
| `D-28` | 2026-09-13 | **Overtime is a manual form, approved, then paid. Independent of attendance** | Founder decision. No derivation from clock hours. Approved overtime is written to the pay input ledger (`CORE-10`) and picked up by the pay run | `01`, `02` |
| `D-29` | 2026-09-13 | **Ant Design for the unified frontend.** MUI is retired with the HRMS screens | Payroll holds 158 routes and the most intricate forms and tables — pay runs, tax declarations, investment proofs. Keeping Ant Design ports the larger half intact and rewrites only the smaller HRMS surface. Closes `OQ-04` | `03` |
| `D-30` | 2026-09-13 | **Vite as the build tool**, despite Ant Design arriving from the Create React App side | Create React App is deprecated and unmaintained. The build tool and the component library are independent choices | `03` |
| `D-31` | 2026-09-13 | **Core owns employee self-service.** One employee portal, showing whichever modules the tenant holds | Follows from Core owning the employee master and leave. Payroll's `employeePortalEnable` flag and its portal endpoints move to Core | `01` |
| `D-32` | 2026-09-13 | **Payroll events get notifications through Core** — payslip published, declaration window open, proof rejected, pay run complete | Payroll has no notification capability at all today. Core supplies it, so this is new function for Payroll customers rather than a port | `01` |
| `D-33` | 2026-09-13 | **One approval engine covers leave, reimbursement, investment proof, overtime and attendance regularization** | Five hard-coded approval paths today, none reusable. `CORE-11` replaces all of them | `01`, `02` |
| `D-34` | 2026-09-13 | **Attendance feeds only the loss-of-pay policy**, and only where a tenant configures it to. It produces nothing else | `D-28` detached overtime from attendance, leaving loss of pay as its sole downstream consumer. Attendance is otherwise a record | `01` |
| `D-35` | 2026-09-13 | **Basic capture of leave, attendance and overtime is Core. The employee request-and-approve experience is HRMS.** A Payroll-only tenant records these as administrator data entry | Moves the differentiator from *what data exists* to *who enters it and how*, which is how the market actually tiers these products. A payroll customer recording twelve absences a month should not have to buy a workflow. **Supersedes `D-04` and `D-05`** | `01`, `02`, `08`–`10` |
| `D-36` | 2026-09-13 | **The approval engine stays in Core as infrastructure.** Which workflows it runs depends on the modules held | Reimbursement claims and investment proofs are Payroll features that need approvals, so the engine cannot be HRMS-only. HRMS gates three specific workflows — leave requests, attendance regularization, overtime requests — not the engine itself | `01`, `02` |
| `D-37` | 2026-09-13 | **Reimbursement and investment-proof approvals are available to Payroll-only tenants**, employee-submitted and approved | Both are inherently employee-originated and meaningless without Payroll. Their approval must not depend on buying a second product | `01` |
| `D-38` | 2026-09-13 | **Java 21** for the whole backend | `HRMS_Backend` is already on 21, Payroll on 17. 21 is LTS with support to 2031, and nothing in the frozen Payroll code blocks the move. One version across seven modules, chosen before any code exists | `03`, `W-01` |
| `D-39` | 2026-09-13 | **Spring Boot 3.3.x**, latest patch | The frozen apps are on 3.2.4 and 3.2.5. Starting one minor ahead avoids a framework upgrade in month two, while staying close enough that ported code needs no rework | `03`, `W-01` |
| `D-40` | 2026-09-13 | **Maven coordinates `com.infinevo` / `infinevo-platform`**, packages `com.infinevo.<module>.<feature>` | Neither frozen groupId carries forward. `com.phegondev` in `HRMS_Backend` is a template artefact and must not propagate into the new platform | `03`, `W-01` |
| ~~`D-41`~~ | 2026-09-13 | ~~**Node 20 LTS**~~ — **superseded same day by `D-42`** | Node 20 reached end of life in April 2026. It was proposed and approved in error; an unsupported runtime receives no security patches | `03`, `W-01` |
| `D-42` | 2026-09-13 | **Node 24 LTS** for the frontend and the harness | The current active LTS, maintained to 2028. **Supersedes `D-41`**, which named a runtime that was already end-of-life on the day it was approved | `03`, `W-01` |
| `D-43` | 2026-09-13 | **Branch protection is convention, not enforcement, until the second developer joins** | GitHub refuses branch protection on private repositories on the Free plan. The options were to make the repository public, pay for Team, or rely on convention. A multi-tenant platform holding other companies' payroll data cannot be public, and paying per seat before there are seats is premature. Revisit when the team grows | `W-01` |
| `D-44` | 2026-09-13 | **RabbitMQ stands in for Azure Service Bus in the local stack.** Production remains Service Bus (`D-10`) — this is a laptop substitute only | No faithful local emulator exists with acceptable licence terms. `W-52` builds a queue abstraction, so local and production differ by one adapter, which is the point of having the abstraction. The alternative, a Postgres-backed queue, tests nothing about real queue behaviour | `04` §6, `W-02` |

---

## 2. Open questions

**None. All target-state design questions are closed as of 2026-09-13.**

Questions about what exists today are not listed here — if the answer only affects which data
is carried across, it belongs to the migration phase (§5). Operational matters on the running
system are in §6.

| ID | Question | Options | Blocks | Owner |
|---|---|---|---|---|

| ID | Question | Options | Blocks | Owner |
|---|---|---|---|---|

**Answered 2026-09-11 and moved to §1:** `OQ-05` → `D-17` (no sync) · `OQ-06` → `D-18`
(India) · `OQ-07` → `D-19` (10 × 100) · `OQ-09` → `D-20` (HRMS will be sold).

---

## 3. Superseded

| Original | Superseded by | Note |
|---|---|---|
| Management summary: "Leave engine built from HRMS's existing leave code; Payroll's newer leave module is retired" | `D-03` | Evidence showed the two are complementary halves, not competing versions |
| Earlier recommendation: attendance belongs in Core because Payroll needs it for loss of pay | `D-04` | The stated dependency does not exist. Loss of pay comes from approved leave |
| Earlier recommendation: Payroll as its own service because pay runs are heavy | `D-01`, `D-02` | Heaviness is a batch concern, better solved by a worker |
| Earlier recommendation: tax reference data fully shared, no overrides | `D-08` | Professional tax overrides already exist in the code |
| `D-04` attendance is HRMS-only · `D-05` overtime is HRMS-only | `D-35` | Basic capture moves to Core. The differentiator becomes the request-and-approve experience, not the data |
| `D-41` Node 20 LTS | `D-42` | Node 20 was already end-of-life when it was approved. Caught during the `W-01` build |
| Earlier proposal: three schemas | `D-08` | A fourth, `reference`, isolates the tenant-column exception and makes it auditable |
| `.claude/work/active-work.md`: shared-schema MySQL on Azure Kubernetes | `D-09`, `D-10` | Predates this design work |
| Plan: subtree migration + daily/weekly one-way sync from the four origin repos | `D-17` | No sync mechanism. The `sync-upstream` skill is cancelled |
| Management summary: "weekly sync of prod fixes until a cutoff date" | `D-17` | Same. Post-cutoff fixes are tracked and re-applied by hand |

---

## Related

- `01`–`06` for the design these decisions produced
- `MANAGEMENT_SUMMARY_AND_PLAN.md` for phases and estimates
- `.claude/work/active-work.md` for live project state

---

## 4. Scale recalibration (`D-19`)

At 10 tenants and 1,000 employees total, several capabilities designed for "enterprise load"
are over-built. Recorded so they are scoped to the real number, not the aspiration.

| Capability | Original intent | At this scale |
|---|---|---|
| `PLAT-04` async queue | Throughput for heavy pay runs | **Keep**, but justified by *correctness* not scale: job survives restart, and scheduled jobs stop double-firing |
| `PLAT-05` caching | Reduce database load | **Keep**, but small. Needed because two instances cannot share an in-process permission cache |
| `PLAT-06` indexes | Prevent collapse under load | **Keep.** Still the highest-return work, but the symptom is slow pages, not outage |
| `PLAT-14` load testing | Prove capacity at scale | **Downgrade.** A 100-employee pay run and ~100 concurrent users is a modest target |
| Read replica | Isolate reporting load | **Drop for now.** Add when reporting load justifies it |
| Autoscaling | Handle traffic peaks | **Minimal.** Two instances for availability, not for load |
| Database tier | Sized for load | **Smallest viable tier** with high availability in production only |
| Horizontal scaling | Many instances | **Two.** The goal is surviving an instance dying, not throughput |

**What does *not* get downgraded:** multi-tenancy correctness, row-level security, the audit
trail, secrets handling, and the test foundation. Those are about correctness and
sellability, and they do not scale with headcount.


---

## 5. Deferred to the migration phase

Not target-state questions. Recorded so they are not lost, and answered when migration is
actually scoped.

| Item | Question at that time |
|---|---|
| Timesheet data | Which of the two live systems holds data worth carrying into the single target model (`D-24`) |
| Leave and balance duplicates | Which of the duplicate entities holds live rows, and whether both must be merged (`D-25`) |
| Leave authority | Which side seeds Core's leave tables — HRMS leave requests, or Payroll consumption (`D-26`). Note the pay run currently reads HRMS |
| Employee master | The deduplication rules, and whether both sides genuinely hold live employee data |
| Documents | Rewriting every Cloudinary reference to Blob Storage |
| Loss-of-pay history | Reconciling the two existing calculations before cutover |

## 6. Closed without action

| Item | Why closed |
|---|---|
| Is the unauthenticated HRMS endpoint internet-reachable | Operational matter on the running system. The target state has no such endpoint (`D-22`, `D-23`) |
| Do the 27 path-variable endpoints leak across tenants | Same. The target state binds the tenant to the database session, so the class of bug cannot exist (`D-21`) |
| Is HRMS deployed and holding real data | Founder ruling 2026-09-13: current state does not drive target-state design. Relevant only to migration, where it appears in §5 |
