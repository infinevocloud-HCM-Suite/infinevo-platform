# Infinevo Cloud — Platform Unification
## Management Summary & Project Plan

**Date:** 11 Sep 2026 · **Owner:** Sanjib · **Status:** For approval

---

## 1. Why we are doing this

Today HRMS and Payroll are two separate products with two logins, two databases, and duplicated employee and leave data. A customer cannot buy one and later add the other. We are unifying them into one platform where a customer buys HRMS, Payroll, or both — and upgrades with a switch, not a re-onboarding.

A baseline check (11 Sep) confirms the code builds, but 350 lint errors, ~300KB of dead code, duplicated tables, and zero automated tests mean we cannot safely change it yet. Phase 0 fixes that first.

---

## 2. Current vs Future

| | Today | After |
|---|---|---|
| Products | 2 apps, 2 logins, sold separately | 1 platform, 1 login. **Core** (employee, leave, holidays, attendance basics) always on; HRMS and Payroll switched on per subscription |
| Employee & leave data | Kept twice, can disagree | One employee master and one leave engine in a **Core** layer that every customer gets, regardless of module. HRMS and Payroll both read from it |
| Code health | Backends compile. HRMS frontend: 350 lint errors (314 unused code). Payroll frontend: 833 warnings. ~300KB dead files, duplicate tables, build output committed to git | Zero lint errors and warnings, enforced on every change. Dead code and duplicates removed |
| Quality | No automated tests | Automated tests on every change + manual UAT sign-off per phase |
| Infrastructure | DigitalOcean servers, MySQL, manual deploys | Azure, Postgres, containers, automated deploys |
| Code migration | Prod code in 4 old repos, still receiving fixes | New repo built beside old. Weekly sync of prod fixes until a cutoff date; after that, fixes ported by hand. Old system stays live until one planned switchover, then read-only 30 days as rollback |
| Data migration | 131 tables across 2 DBs, employee/leave duplicated | Migrated once via a rehearsed script: employees deduplicated by email, HRMS leave data preferred, deleted tables either mapped or archived — never lost. Freeze window under 1 hour |

**In one line:** We build and test the new system beside the old one. Customers keep using the old one until a single planned switchover — no half-migrated state, and the old system stays available to roll back.

---

## 3. Phases

Durations assume AI coding agents (Claude Code) do most code generation, refactoring and test writing, with the harness enforcing rules. Agents compress coding time; they do not compress decisions, data verification, migration rehearsals or UAT — those remain human-paced and set the floor.

**Team model:** Lead (Sanjib) = architecture, decisions, review, all DB/migration work. Dev-1..N = part-time, each owns one app or one workstream via the harness. Minimum viable team: Lead + 2 part-time devs. Adding a third dev shortens Phases 3–4 only.

| Ph | Name | Key work | Sequential / Parallel | Who | Duration | Exit gate |
|---|---|---|---|---|---|---|
| 0 | **Foundation** | Lint to zero; dead code deleted; build output untracked; test harness; CI; Docker; secrets removed; prod-fix sync | **Sequential gate for everything after.** Internally parallel: one dev per app for lint/dead code; lead builds tests + CI | Lead + Dev-1 (HRMS FE/BE) + Dev-2 (Payroll FE/BE) | **2 wks** | 15 tests green; CI blocks lint errors; all 4 apps clean |
| 1 | **Data platform** | Postgres; Flyway; tenant model on HRMS; migration script weekly on dev data; deleted-table map | **Sequential** (lead-owned; every step depends on the previous). Dev-1 can start Phase 2 Keycloak realm setup in parallel | Lead (+ Dev-1 on P2 prep) | **3 wks** | Dev data migrates with matching counts |
| 2 | **One login, one subscription** | Keycloak for both; subscription table; API gateway; service-to-service auth | **Sequential after P1.** Parallel inside: Dev-1 = Keycloak + HRMS auth swap, Dev-2 = gateway + subscription, Lead = review + tenant claims | Lead + Dev-1 + Dev-2 | **3–4 wks** | Demo: HRMS-only, Payroll-only, upgrade to Both |
| 3 | **Core: one employee, one leave engine** | Extract **Core** module: employee master + leave/holiday/attendance engine (built from HRMS's leave code), always enabled for every tenant. HRMS and Payroll become consumers; Payroll's own leave module retired; pay run reads LOP from Core; God-services split | **Sequential after P2** (employee master → leave engine → LOP feed). Parallel inside: Lead = Core extraction + migration mapping, Dev-1 = HRMS re-pointed to Core, Dev-2 = Payroll re-pointed to Core | Lead + Dev-1 + Dev-2 | **5 wks** | Payroll-only tenant can manage leave; pay run matches old system to the rupee; finance signs off LOP mismatch log |
| 4 | **Scale & UI** | Job queue; caching; single frontend; load test | **Parallel with P3** from its second week. Dev-3 (or Dev-1 after P3 HRMS work) = frontend unification; Dev-2 = queue + Redis after P3 | Dev-2 + Dev-3 | **4 wks** (overlapping) | 500-employee pay run under target; UAT sign-off |
| 5 | **Azure & go-live** | Azure infra; IaC; monitoring; prod rehearsals ×2–3; cutover | Azure setup **parallel from P2 onward** (Dev-2 or Lead, background). Rehearsals + cutover **strictly sequential at the end**, lead-owned | Lead (+ Dev-2 infra) | **2 wks setup (background) + 2 wks rehearsal/cutover** | Cutover done; 30-day rollback window closed |

### Timeline (wall clock)

```
Week:   1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19
P0      ██ ██
P1            ██ ██ ██
P2                  ▒▒ ██ ██ ██ ██
P3                                 ██ ██ ██ ██ ██
P4                                    ██ ██ ██ ██
P5 infra                   ▒▒ ▒▒ ▒▒ ▒▒ ▒▒ ▒▒ ▒▒ ▒▒
P5 cutover                                          ██ ██
```
▒ = background / prep work · █ = primary work

**Total: ~4.5–5 months (19–20 weeks) with Lead + 2 part-time devs; ~4 months with Lead + 3.** Previous estimate without agents was 6–7 months. The saving is in Phases 0, 3 and 4 (code-heavy). Phases 1, 2 and 5 barely change — they are decision- and verification-bound, not typing-bound.

**What does not get faster with agents:** deciding domain ownership, verifying migrated financial data, finance sign-off, UAT, cutover freeze. Plan around those, not around code volume.

---

## 4. Decisions already taken

| Decision | Choice | Why |
|---|---|---|
| Base platform | Payroll's organization model extended to HRMS | Payroll already has multi-org and enterprise login |
| Leave ownership | **Core module**, not HRMS or Payroll | Leave is needed by both: HRMS for workflow, Payroll for LOP. It lives in a Core layer every customer gets. Engine built from HRMS's existing leave code; Payroll's newer leave module (Aug–Sep 2026) is retired |
| Tenant provisioning | Every customer gets Core + all tables; HRMS/Payroll toggled by subscription | Simplest upgrade path; Core is never off; empty module tables cost nothing |
| Database | Postgres with versioned migrations | Row-level tenant isolation; current setup has no schema control |
| Migration style | Build beside, rehearse, switch once | No half-migrated customers; rollback path kept |

---

## 5. Risks

| Risk | Mitigation |
|---|---|
| Two leave engines may give different LOP for the same month | Migration logs every mismatch; finance reviews before cutover |
| Old repos keep changing while we rebuild | Weekly sync until Phase 3 cutoff, then manual porting |
| Lint cleanup touches 67 files at once | 314 unused-code errors are mechanical; the 36 remaining reviewed by hand; compile check runs on every edit |
| Cutover needs a freeze window | Under 1 hour, scheduled outside payroll week |
| Recent Payroll leave work is discarded | Called out now, not discovered later; Payroll-specific leave features (import, LOP preferences) are ported into Core, not lost |
| Single lead, part-time team | Development harness and AI agents make part-timers productive; phases sized small |

---

## 6. Asks from management

1. Approve plan and phase order
2. Confirm a payroll-free week is acceptable for cutover (Phase 5)
3. Name a finance contact for LOP mismatch review (Phase 3) and pay run sign-off (Phase 5)
