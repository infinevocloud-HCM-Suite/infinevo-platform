# Active Work

> Live working file — gitignored. The tracked skeleton is `active-work.template.md`.
> Last refreshed: 2026-09-11 from actual repo state.
> **Read this before starting any task** (root `CLAUDE.md` rule 2).

## Repo state at last refresh

| App | Branch | HEAD | Last commit | Sync |
|---|---|---|---|---|
| `HRMS_Backend` | `main` | `d984c64` | 2026-06-16 · Prashant Kumar · "Logic updated" | ahead 0 / behind 0 |
| `HRMS_Frontend` | `main` | `c72116c` | 2025-12-17 · Sayeed · "negative leave balance application" | ahead 0 / behind 0 |
| `Payroll-Bend-SBoot` | **`taxation`** | `39b37d6` | 2026-09-09 · sarthak-infinevo · "Bug fix of lop and lwp" | ahead 0 / behind 0 |
| `Payroll-Fend-react` | **`employee`** | `053ca62` | 2026-09-10 · sarthak-infinevo · "stop continuous API" | ahead 0 / behind 0 |

All four working trees clean. Root repo (`main`) holds docs + harness only; the four app
folders are gitignored pending the subtree migration.

---

## Current direction

**Unify the four applications into one multi-tenant platform, then move to Azure.**

**Target state is now designed and documented: `docs/target-state/`** (8 files, 1,395 lines,
2026-09-11). Read `docs/target-state/README.md` first. Decisions `D-01`..`D-16` and open
questions `OQ-01`..`OQ-10`, six still open live in `docs/target-state/07-decisions.md`.

Shape, in one line: one repo, one backend with three enforced modules (Core / HRMS /
Payroll), one frontend, **one Postgres database with four schemas**, one login (Keycloak),
on **Azure Container Apps**.

Three threads, in dependency order:

1. **New consolidated repo.** The four origin repos are production and remain read-only.
   A new repo becomes the target codebase. **No subtree, no sync mechanism (`D-17`)** —
   code is ported deliberately, once, as each module is built. ⚠️ Production fixes made
   after 2026-09-11 do **not** arrive automatically; keep a list of them or they are lost
   at cutover. Payroll is actively developed (commits 09-09, 09-10), so divergence starts
   now. *Not started.*
2. **Multi-tenancy.** Payroll is org-scoped on 63 of 97 entities; HRMS has **none at all**
   (0 of 39, GAP BUG-002). Target: `tenant_id` on every table outside the `reference`
   schema, enforced by Postgres row-level security (`D-09`).
3. **Azure.** Container Apps + Azure Database for PostgreSQL + Front Door/WAF + Key Vault
   (`D-10`). Still blocked on DEBT-020 (in-memory permission cache) and DEBT-021 (unlocked
   schedulers) — both prevent running more than one replica today. `D-02` (worker role)
   is the fix for the second.

---

## In flight

| Item | Where | State |
|---|---|---|
| **Development harness** | root `.claude/`, `agents/`, per-app `CLAUDE.md` | Sections A–C complete. Toolchain gate cleared 2026-09-11: Microsoft OpenJDK 21.0.12.1 + Apache Maven 3.9.11 installed (`C:/Tools/apache-maven-3.9.11`, JAVA_HOME/MAVEN_HOME set at user level). Task 9 baseline done (`agents/outputs/2026-09-11-build-baseline.md`): both backends compile, Payroll FE 0 lint errors, HRMS FE 350 lint errors (code). Sections E–H complete 2026-09-11: hooks (`guard-edit`, `verify-app`, `session-log`) written, dry-run tested and wired in `.claude/settings.json`; agents explorer/implementer/verifier; skills analyze/plan-feature/infra-task/sync-docs/sync-upstream. Live proof: `agents/outputs/2026-09-11-harness-proof.md`. **Harness build DONE** — root repo still has no commit |
| **Docs consolidation** | `docs/` | **Complete 2026-09-11.** 10 files → 5 top-level + `features/` + `_archive/`. `DB_SCHEMA.md` corrected (44 table names) and completed (52 tables added, now 131/131). `FEATURE_MAP.md` gained the missing Payroll Frontend section. Audit: `agents/outputs/2026-09-11-docs-audit.md` |
| **Target-state design** | `docs/target-state/` | **Complete 2026-09-11.** 8 files, 1,395 lines. 60 capabilities (`CORE`/`HRMS`/`PAY`/`PLAT`), 16 decisions, 10 open questions. Next step: generate the feature list from the capability IDs |
| **Leave Allocation + Salary Deduction** | `Payroll-Bend-SBoot@taxation`, `Payroll-Fend-react@employee` | Merged 2026-08-28 → 2026-09-09. Now the source of truth for LOP, replacing the HRMS `fetchLeaves` path |

---

## Frozen

| Item | Evidence | Note |
|---|---|---|
| **HRMS_Backend** | Last commit 2026-06-16; `HRMS_BACKEND_DEV` last touched 2025-06-30 | No active development for ~3 months |
| **HRMS_Frontend** | Last commit 2025-12-17; `HRMS_FRONTEND_DEV` last touched 2025-06-30 | No active development for ~9 months |
| **`main` on both Payroll repos** | `Payroll-Bend-SBoot/main` 2025-12-18; `Payroll-Fend-react/main` 2025-08-11 | 9 and 13 months behind the live branches. Do not build or deploy from `main` |
| ~~**HRMS↔Payroll LOP integration**~~ | `IntegrateWithPayroll.java:29,64` | ⚠️ **NOT frozen — corrected 2026-09-11.** The controller is live, reachable at `POST /public/get-employee-leaves`, wired to the live `LeaveRequests` entity, and **unauthenticated**. See `OQ-03` and the security finding |
| **Stale branches** | `tabsSwitch`, `employee0.1`, `workLocations` | Last touched 2025 |

---

## Open questions

**Canonical list is `docs/target-state/07-decisions.md` §2 (`OQ-01`..`OQ-10`, six still open).**
The three that block work right now:

| # | Question | Blocks |
|---|---|---|
| `OQ-10` | **Is `POST /public/get-employee-leaves` on HRMS reachable from the public internet?** It requires no authentication. Decides whether the security finding is critical or minor | Nothing in the target state — it is a **live issue**. Check first. See `agents/outputs/2026-09-11-security-finding-public-endpoint.md` |
| `OQ-01` | **Which timesheet system survives?** Two complete implementations are live, with separate controllers, services, repositories and DTOs | The entire HRMS module port. See `agents/outputs/2026-09-11-hrms-duplicate-entities.md` |
| `OQ-03` | **Is the HRMS→Payroll leave integration still authoritative?** Listed under Frozen above, but the controller is live and reachable | `CORE-07` leave merge, and the security finding |

Also open, not yet blocking: `OQ-02` duplicate leave entities, `OQ-04` frontend component
library.

Answered 2026-09-11: `OQ-05` → `D-17` no sync · `OQ-06` → `D-18` India ·
`OQ-07` → `D-19` 10 tenants × 100 employees · `OQ-09` → `D-20` HRMS will be sold ·
`OQ-08` → `D-21` one Keycloak realm, following Payroll.
