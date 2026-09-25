# Target State — Infinevo Cloud

> The destination. What the unified platform is, how its data is shaped, how the code is
> organised, and where it runs.
>
> **Status:** drafted 2026-09-11. Decisions in `07` are recorded as taken (`D-16`) and any
> can be reopened with a new dated entry.
> **Not in scope here:** phases, estimates, team allocation — see
> `MANAGEMENT_SUMMARY_AND_PLAN.md`.

---

## The destination in five lines

One platform. A customer buys HRMS, Payroll, or both, and upgrades with a switch rather than
a re-onboarding. **Core** — employee master, leave, holidays, identity, approvals, audit —
is always on, whatever they bought. One repository, one backend with three enforced modules,
one frontend, one Postgres database with four schemas, one login. Running on Azure Container
Apps with an automated pipeline.

---

## Index

| File | Answers | Read it if you are |
|---|---|---|
| `01-platform-shape.md` | What is Core, what is HRMS, what is Payroll, and what each customer gets | Deciding scope, selling, or writing any feature |
| `02-data-model.md` | Which tables exist, in which schema, and what merges into what | Building or migrating data |
| `03-code-structure.md` | Repository layout, module graph, how the boundary is enforced | Writing code |
| `04-runtime-containers.md` | What runs, in how many containers, and how it scales | Building or operating it |
| `05-azure-architecture.md` | Which Azure resources, networking, database roles, deployment | Building the infrastructure |
| `06-current-to-target.md` | Why each thing changes, what does not change, and the risks | New to the project, or questioning a change |
| `07-decisions.md` | What was decided, why, and what is still open | Asking "why did we do it this way" |
| `08-work-plan.md` | The full list of work to get there, in dependency order | Scoping, assigning or sequencing work |
| `09-build-order.md` | Waves, parallel tracks, and per-item build detail | About to start building something |
| `10-scoping.md` | Sizes, skills, blockers, and what to assign first | Assigning work to people |
| `11-ways-of-working.md` | What you assign, how a developer works a ticket, what done means | Starting development |
| `12-core-contracts.md` | What Core provides to Payroll, HRMS, frontend and admin: tables, APIs, Java seams, permission codes; the fixes the Core specs need | Writing any spec outside Core |
| `features/` | The per-ticket specs. `TEMPLATE.md`, then one `W-nn-*.md` per item as it starts | Writing or approving a spec |

**Start with `01`.** It is the shortest path to understanding the product.

---

## How to use this to build the feature list

Every capability in `01` carries a stable identifier. The feature list is generated from
them, and each deep-dive analysis cites the feature it belongs to.

| Prefix | Applies to | Count | Lives in |
|---|---|---|---|
| `CORE-nn` | Core capabilities | 21 | `01` §3 |
| `HRMS-nn` | HRMS module capabilities | 10 | `01` §4 |
| `PAY-nn` | Payroll module capabilities | 17 | `01` §5 |
| `PLAT-nn` | Cross-cutting platform capabilities | 14 | `01` §6 |
| `D-nn` | Decisions | 44 | `07` §1 |
| `OQ-nn` | Open questions | **0 — design closed 2026-09-13** | `07` §2 |

**Sixty-two capabilities in total**, delivered by **72 work items broken into 295 features**
(`08`), sequenced into **9 waves across 5 parallel tracks** (`09`). The chain runs:
spec → work item → capability → decision.

### Where a feature gets its own document

**Not here.** `08` and `09` are the complete inventory and the build order. A feature earns a
document of its own **only when it is about to be built**, written into `docs/target-state/features/`
from `docs/target-state/features/TEMPLATE.md`, and named after its work item — `W-16-leave-engine.md`.

| Document | Written | Holds |
|---|---|---|
| `08` + `09` | **Now, once** | Every work item, its features, order, parallelism, build detail |
| `docs/target-state/features/W-nn-*.md` | **Just before that item starts** | The full spec: flows, API, schema changes, tests, rollback |

Writing 72 specs up front would produce 60 stale documents. The build detail in `09` §3 is
deliberately the level that survives — what to build, how you know it is done, and the trap
to avoid.

---

## Boundary rules between these files

So the set does not rot into duplication.

| File | Must never contain |
|---|---|
| `01` | Table names. It names capabilities, not storage |
| `02` | Capability descriptions. It names tables, not behaviour |
| `03` | Azure resources |
| `04` | Azure resources. It stops at the image and the local stack |
| `05` | Dockerfile detail |
| `06` | Timelines, estimates, team allocation |
| `07` | Design detail. A decision, its reason, its date, nothing more |
| `08` | Estimates, dates, team allocation. Work items and their dependencies only |
| `09` | Dates and durations. Waves are dependency order, not a calendar |
| `10` | Dates. Sizes are relative effort, not a schedule |

---

## Before you act on this

Three things are recorded here that are **live problems, not target-state matters**. They
are in `06` §5 with evidence.

1. An HRMS endpoint returns employee leave data with **no authentication**, and Keycloak
   administrative credentials are committed to the repository with a trivial password.
   **Operational actions on the running system, not target-state questions** — the target
   state removes both (`D-22`, `D-23`). Do not wait for this programme to act on them.
2. **Two complete timesheet systems** are running side by side. Which one's data
   survives is settled at `W-67` before any row moves — a migration question, not a
   blocker on building the HRMS module.
3. Entities that look duplicate are **not all dead**. Nothing in the "Retired" list in `02`
   §7 should be deleted without its own confirmation.

---

## Related

| Document | Holds |
|---|---|
| `MANAGEMENT_SUMMARY_AND_PLAN.md` | Phases, durations, team, exit gates |
| `legacy/docs/DB_SCHEMA.md` | Today's 131 tables, verified against the live branches |
| `legacy/docs/FEATURE_MAP.md` | Which files implement which feature today |
| `legacy/docs/GAP_INVENTORY.md` | Known defects and debt |
| `docs/CONVENTIONS.md` | Coding rules, money handling, naming hazards |
| `docs/trackers/` | Owner and status of every ticket — the source of truth |
| `.claude/work/active-work.md` | Live project state, branches, in-flight work |
| `.claude/outputs/2026-09-11-*` | The investigations this design rests on |
