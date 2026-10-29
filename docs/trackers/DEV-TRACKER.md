# Dev Tracker

> The product itself — foundation, data, core platform, payroll, HRMS, frontend.
> Streams A to F, plus the product items in G and H.
> **GitHub is authoritative.** Status legend: [README.md](README.md).
> Last refreshed: **2026-10-28**, against `main` — Stage 21 Track A (`W-74` WFH employee self-service `/me/wfh` and `/me/wfh/apply`) verified 100% PASS on Azure Dev by sayeed (S21-01 to S21-09); `D-116` (salary components Fixed Allowance earning type) fixed on main `d2f9a944` by sayeed; `D-115` (regularization approval inline approve/reject) fixed on main `a0758fea` by sayeed; `D-114` (overtime approval inline approve/reject) fixed on main `71135a50` by sayeed; `D-113` (timesheet approval inline approve/reject) fixed on main `78ce6ed2` by sayeed; `D-112` (timesheet approval daily descriptions) fixed on main `f7cf7b51` by sayeed; `D-111` (employee form personal & contact fields free-text to dropdown parity: nationality, state, emergency contact relationships) fixed on main `0da553d0` by sayeed; `D-110` (employee dashboard landing page, top-level navigation hierarchy, and holidays under HRMS) fixed on main `a82fbffd` by sayeed; `D-109` (employee document/identification fields free-text to dropdown parity) fixed on main `b1296254` by sayeed; `D-108` (post-logout redirect to app root and role-based fallback on unauthorized routes) fixed on main `1ac99343` by sayeed; All QA and defect items for Krushna resolved on main (`D-107` fixed on main `c5b9bd1f`, `W-74` on main `f959ab51`, Day 4 `BUG-D4-13`–`BUG-D4-22` and Day 2 `BUG-D4-01`–`BUG-D4-12` resolved); `D-106` (organisation master navigation exposure: departments, designations, work locations restricted to `core.org.manage`) fixed on main `2547ab2d` by sayeed; `D-105` (timesheet weekly entry inline description box and mandatory validation parity with legacy) opened for sayeed; `D-104` (attendance clock-out deadlock on expired sessions >24h / previous date) logged; `D-103` (frontend dependencies Trivy HIGH CVEs) logged; `D-102` (payslip legacy fields parity and PDF download) logged; Phase 4 of the screen audit on `main` (`D-84`–`D-87`): honest error states, leave history reachable, portal tabs in order; the `e2e` CI job is blocking; Phase 3 of the screen audit on `main` (`D-78`–`D-83`, `D-53`, `D-56`, `D-58`): names beside ids, dates and money formatted, status in words, profile and leave layout; QA defects from the three developer branches numbered once (`D-19`–`D-32`, `D-94`–`D-101`); Playwright per-role e2e suite in CI (`e2e` job, non-blocking) and its findings `D-89`–`D-93` fixed; Devashish's `D-14` (finance sees Approvals, `V174`) and `D-24` (tax-declaration year route) merged, his QA rows in §11, `D-25` proof screens held for a spec; Krushna's Day 4 employee fixes (`BUG-D4-13`–`BUG-D4-22`, §9) merged with two merge-review fixes; `D-88` (invitation sets the password) merged; Sayeed's leave fixes (`D-15`, `D-16`, `D-55`) and QA rows `D-43`–`D-58` merged; screen-audit Phases 1–2 (`D-64`–`D-77`) on `main`; `D-78`–`D-87` open.

## Summary

| Stream | Tickets | Code on main | Feature done | Where it stands |
|---|---|---|---|---|
| A — Foundation | 2 | 2 | 2 | Done |
| B — Data foundation | 5 | 5 | 5 | **Done.** Tenancy chain complete; suite green again (`W-04.1`) |
| C — Core platform | 26 | 26 | 26 | **Building.** `W-24.1`, `W-17`, `W-18.1`, `W-24.2` on `main` (`288f9fa`) — setup checklist, holiday calendar, loss-of-pay policy, invitations; `W-19`, `W-20.1`, `W-20.2`, `W-21`, `W-22.2`, `W-23.1`, `W-23.2`, `W-39.2` on `main` (`b6e6012`) — pay input ledger, notifications and reminders, document store, retention, reporting, overtime; `W-39.1` on `main` (`3bf5b10`) — administrator-entered attendance for Payroll-only tenants; `W-15.1`–`W-15.3` on `main` (`a34c14f`); `W-18.2` policy stamp on every pay figure on `main` (`b580d8b`); `W-16.1`–`W-16.4b` leave engine and `W-25` self-service portal on `main` (`a53e311`). `W-10.1` production Keycloak realm on `main` (`889b09b`). Nothing open |
| D — Payroll | 23 | 23 | 23 | **Building.** `W-31.4` statutory lines, `W-36.3` tax deductor, `W-38.1` prior payroll import and `W-38.3` its setup step on `main` (`2780d098`); `W-36.1` employee TDS record and pay run tax line on `main` (`9263c30`); `W-30.2` off-cycle pay run, `W-35.2` ad-hoc deductions, `W-36.2` approve, pay and payslips with a signed link on `main` (`9d5c0a0`); `W-28` pay schedule on `main` (`288f9fa`); `W-26.1`–`W-27.2` on `main` (`79c8825`) — component catalogue, dated CTC versions, FBP plan and declaration; `W-30.1` on `main` (`b6e6012`); `W-32.1`–`.4` tax declaration on `main` (`b704f3c`); `W-33.1`, `W-33.2` tax calculator, both regimes, on `main` (`7907a6c`); `W-31.1`, `W-31.2` EPF, ESI and professional tax on `main` (`ac94662`); `W-31.3` employee EPF and ESI lines on `main` (`ed180b1`); `W-35.1` reimbursement claims on `main` (`840bf2e`); `W-29.1`–`.4` pay run on `main` (`7dfd74e`) — creation and locking, computation, loss of pay and pay inputs, async on the worker; `W-33.3` tax recalculation and `W-36.4` Form 16 on `main` (`8b7705c3`); `W-34.2` rest, `W-36.5` Form 16 Part A, `W-37` dashboard, `W-38.2` imported tax on `main` (`6e19dd89`). **Stream D has no open ticket** |
| E — HRMS | 5 | 3 | 3 | **Building.** `W-40.1`, `W-40.2`, `W-40.3`, `W-40.5` (karma), `W-42.1`–`.4` timesheets and `W-43.1`, `W-43.2` reminders (devashis) on `main` (`2780d098`); `W-41` on `main` (`5fa04b1`); `W-40.4`, `W-40.6` on `main` (`6e19dd89`) — `W-40` complete. `W-44`, `W-48.1`–`W-48.4` on `main` (`722d1689`). `W-48.5`, `W-48.6` on `main` (`789b69a0`) — `W-48` complete; `W-68` on `main` (`42939851`) |
| F — Frontend | 15 | 13 | 13 | **Building.** `W-47.1a`, `W-47.1b` (sayeed), `W-46.2`, `W-46.5` (biren), `W-47.6` (krushna) on `main` (`2780d098`), which makes `W-47.3`'s tax screens reachable; `W-47.3` on `main` (`7907a6c`); `W-45`, `W-46.1`, `W-46.3a`, `W-46.4` on `main` (`11ec157`) — shell, employee screens, organisation setup, approval screens; `W-46.3b`, `W-46.6`, `W-46.7` on `main` (`24bb261`) — holiday calendar, setup checklist, invitation screens; `W-47.4`, `W-47.5`, `W-65.3` on `main` (`6e19dd89`) |
| G/H — product items | 3 | 1 | 1 | `W-55` merged `2ccd723`. `W-65.1`, `W-65.2` on `main` (`5fa04b1`); `W-65.3` on `main` (`6e19dd89`). `W-66` unassigned |

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
| sayeed | `dev-sayeed` | 1a employee & org · 5 statutory settings, lines and screens · tax deductor | ~~`W-52.1`~~ ~~`W-53.1`~~ **on main `5c07c45`** ~~`W-13.4`~~ **on main `e699699`** ~~`W-09.1`~~ **on main `c79755c`** ~~`W-13.3`~~ ~~`W-14.2`~~ **on main `ccc8e48`** ~~`W-39.1`~~ **on main `3bf5b10`** → D-9 manual checks → ~~`W-31.1`~~ ~~`W-31.2`~~ **on main `ac94662`** → ~~`W-31.3`~~ **on main `ed180b1`** ~~`W-35.1`~~ **on main `840bf2e`** → ~~`W-36.3`~~ ~~`W-31.4`~~ ~~`W-47.1a`~~ ~~`W-47.1b`~~ **on main `2780d098`** → ~~`D-106`~~ (org master exposure) **on main `2547ab2d`** → ~~`D-108`~~ (logout return route) **on main `1ac99343`** → ~~`D-109`~~ (employee document dropdowns) **on main `b1296254`** → ~~`D-110`~~ (dashboard landing & navigation) **on main `a82fbffd`** → ~~`D-111`~~ (nationality, state, relationship dropdowns) **on main `0da553d0`** → ~~`D-112`~~ **on main `f7cf7b51`** → ~~`D-113`~~ **on main `78ce6ed2`** → ~~`D-114`~~ **on main `71135a50`** → ~~`D-115`~~ **on main `a0758fea`** → ~~`D-116`~~ **on main `d2f9a944`** → Stage 21 Track A (`W-74` WFH employee self-service `/me/wfh`) live verified on Azure Dev (S21-01 to S21-09 PASS) — queue empty, D-9 still open (`W-36.1` and `W-40` moved to karma 2026-10-02; `W-47.1a` from karma 2026-10-02) | `V026`–`V032` (`V030` used) · `V062`–`V069` · `V107` `W-36.3` used (**on main `2780d098`**) |
| krushna | `dev-krushna` | 2 tenant & onboarding · 7a working-day policy & schedule · 7c pay run compute · QA defects · WFH | ~~`W-12.1`~~ ~~`W-12.2`~~ **on main `b7d03ec`** ~~`W-12.3`~~ **on main `a4f31ae`** ~~`W-24.1`~~ ~~`W-17`~~ ~~`W-24.2`~~ ~~`W-18.1`~~ ~~`W-28`~~ **on main `288f9fa`** ~~`W-29.1`~~ ~~`W-29.2`~~ ~~`W-29.3`~~ ~~`W-29.4`~~ **on main `7dfd74e`** ~~`W-18.2`~~ **on main `b580d8b`** ~~`W-30.2`~~ ~~`W-35.2`~~ ~~`W-36.2`~~ **on main `9d5c0a0`** ~~`W-47.2`~~ **on main `36c02d1`** ~~`W-38.1`~~ ~~`W-38.3`~~ ~~`W-47.6`~~ **on main `2780d098`** (branch `krushna-tickets`) ~~`W-38.2`~~ **on main `6e19dd89`** (built in `batch-2026-10-03`) → **QA Defect Remediation: Day 4 & Day 2 resolved, `D-50` (Bulk Leave Import Sample Template) resolved** → ~~`W-74`~~ **on main `f959ab51`** → ~~`D-107`~~ (WFH UI Screen Layout & Leave Parity) **fixed on main `c5b9bd1f`** — **all bugs resolved, queue empty** | `V140` `W-38.1` used (**on main `2780d098`**) · `V033`–`V036` used · `V054` used · `V116`–`V119` used · `V055`–`V059` used · `V125` used · `V061`, `V100`–`V101`, `V103` used (**on main `9d5c0a0`**) · `V179`–`V184` used for `W-74` |
| devashis | `dev-devashis` | 3 documents, notifications, reporting · 7b pay input ledger & captures · HRMS projects · admin console · proof of investment · payslips | ~~`W-21`~~ ~~`W-20.1`~~ ~~`W-23.1`~~ ~~`W-20.2`~~ ~~`W-22.2`~~ ~~`W-23.2`~~ ~~`W-19`~~ ~~`W-30.1`~~ ~~`W-39.2`~~ **on main `b6e6012`** ~~`W-41`~~ ~~`W-65.1`~~ ~~`W-65.2`~~ ~~`W-34.1`~~ ~~`W-34.3`~~ **on main `5fa04b1`** ~~`W-34.2`~~ **on main `5fa04b1` and `6e19dd89`** ~~`W-42.1`~~ ~~`W-42.2`~~ ~~`W-42.4`~~ ~~`W-42.3`~~ ~~`W-43.1`~~ ~~`W-43.2`~~ **on main `2780d098`** (branch `dev-devashish`) | `V031`–`V032` · `V037`–`V041` · `V060` · `V093`–`V096` — all used · `V110`–`V114` `W-34.1` (`V110` a `reference` script) · `V115` `W-34.2` · `V141`–`V144` `W-42.1` · `V145` `W-42.2` · `V146` `W-43.1` — used (**on main `2780d098`**) |
| karma | `dev-karma` | 1b approvals, leave & portal · employee TDS · HRMS attendance · salary structure screens | ~~`W-15.1`~~ ~~`W-15.2`~~ ~~`W-15.3`~~ **on main `a34c14f`** ~~`W-16.1`~~ ~~`W-16.2`~~ ~~`W-16.3`~~ ~~`W-16.4a`~~ ~~`W-16.4b`~~ ~~`W-25`~~ **on main `a53e311`** ~~`W-36.1`~~ **on main `9263c30`** ~~`W-40.1`~~ ~~`W-40.2`~~ ~~`W-40.5`~~ ~~`W-40.3`~~ **on main `2780d098`** ~~`W-40.4`~~ ~~`W-40.6`~~ **on main `6e19dd89`** (built in `batch-2026-10-03`) ~~`W-10.1`~~ **on main `889b09b`** ~~`W-52.2`~~ **on main `cc8bd8ce`** (fixes `D-11`) (`W-36.1`, `W-40` from sayeed 2026-10-02, `W-47.1a` moved to sayeed 2026-10-02; resets `dev-karma` to `main` first) | `V089`–`V092` used · `V126`–`V134` used · `V102` used (**on main `9263c30`**) · `V120`–`V123` and `V139` used (**on main `2780d098`**; `V139` kept outside the block, founder 2026-10-03) · `V120`–`V124` `W-40` (`V121` `.1` · `V122` `.2`, a `reference` script · `V123` `.3` · `V124` `.4` · `V120` `.5`, a `core` script; `.6` has none — reserved 2026-09-30; `.1`–`.4` moved up from `V116`–`V119`, which krushna's lane used on main `288f9fa`) |
| biren | `dev-biren` | 4 salary structure & FBP · 8 frontend shell & core screens | ~~`W-26.1`~~ ~~`W-26.2`~~ **on main `a3ad0a3`** ~~`W-27.1`~~ ~~`W-27.2`~~ **on main `79c8825`** ~~`W-45`~~ ~~`W-46.1`~~ ~~`W-46.3a`~~ ~~`W-46.4`~~ **on main `11ec157`** ~~`W-46.3b`~~ ~~`W-46.6`~~ ~~`W-46.7`~~ **on main `24bb261`** ~~`W-46.2`~~ ~~`W-46.5`~~ **on main `2780d098`** (branch `dev-biren4s`) ~~`W-47.4`~~ ~~`W-37`~~ ~~`W-47.5`~~ **on main `6e19dd89`** (built in `batch-2026-10-03`) | `V042`–`V053` used · frontend: none |
| mohit | `dev-mohit` | 6 tax declaration, calculator & screens | ~~`W-32.1`~~ ~~`W-32.2`~~ ~~`W-32.3`~~ ~~`W-32.4`~~ **on main `b704f3c`** ~~`W-47.3`~~ ~~`W-33.1`~~ ~~`W-33.2`~~ **on main `7907a6c`** ~~`W-33.3`~~ ~~`W-36.4`~~ **on main `8b7705c3`** ~~`W-36.5`~~ **on main `6e19dd89`** (built in `batch-2026-10-03`; reset `Dev-Mohit` to `main`) | `V070`–`V081` used · `V105`–`V106` used (`reference`) · `V104` `W-33.3` and `V147` (`reference`) used (**on main `8b7705c3`**) · `V108`–`V109` `W-36.5` |
| *unassigned* | — | 7d payroll outputs | ~~`W-35.1`~~ **on main `840bf2e`** (built by sayeed) · `W-37` moved to biren 2026-10-02 · `W-38` split and moved to krushna 2026-10-02 · `W-30.2`, `W-35.2` moved to krushna 2026-10-01 | `V097`–`V098` `W-35.1` (`V097` a `reference` script) · `V100`–`V101` `W-35.2` (`V100` a `reference` script) · `V102` `W-36.1` · `V103` `W-36.2` — reserved 2026-09-29; `V099` is the annual-update test fixture, not free · `V042`–`V081` reserved 2026-09-25 (extended by one for `W-27.2`, one for `W-28`, two for `W-29.1`, one each for `W-29.2`, `W-29.3`, `W-29.4`, `W-30.1`, `W-30.2`, eight for `W-31`, twelve for `W-32`); `V042`–`V045` are `W-26.1`, `V046`–`V050` are `W-26.2`, `V051` is `W-27.1`, `V052`–`V053` are `W-27.2`, `V054` is `W-28`, `V055`–`V056` are `W-29.1`, `V057` is `W-29.2`, `V058` is `W-29.3`, `V059` is `W-29.4`, `V060` is `W-30.1` (a `core` script, **used, on main `b6e6012`**), `V061` is `W-30.2`, `V062`–`V063` are `W-31.1`, `V064`–`V067` are `W-31.2` (`V064` a `reference` script), `V068`–`V069` are `W-31.3`, `V070`–`V072` are `W-32.1` (`V070` a `reference` script), `V073`–`V076` are `W-32.2`, `V077`–`V079` are `W-32.3`, `V080`–`V081` are `W-32.4` |

**2026-09-30 — reassigned, untouched tickets only** (no branch carries a commit for any of them; nothing pending merge moved). Their reserved migrations move with them.

| Ticket | From | To |
|---|---|---|
| `W-34.1`, `W-34.2`, `W-34.3` | mohit | devashis |
| `W-36.2` | krushna | devashis |
| `W-36.1`, `W-36.3` | mohit | sayeed |
| `W-40` | karma | sayeed |

**2026-10-01 — assigned to krushna**, whose lane ends at `W-18.2`. None has a commit on any branch.

| Ticket | From | To |
|---|---|---|
| `W-30.2`, `W-35.2`, `W-47.2` | unassigned | krushna |
| `W-36.2` | devashis | krushna |

**2026-10-02 — moved to karma**, whose lane ended at `W-25`, to shorten sayeed's queue and free mohit sooner. `dev-sayeed` carries a commit for `W-36.3` only. Reserved migrations move with them.

| Ticket | From | To |
|---|---|---|
| `W-36.1` | sayeed | karma |
| `W-40.1`–`W-40.6` | sayeed | karma |
| `W-47.1a` | unassigned | karma |

**2026-10-02 — assigned to biren**, after `W-46.2` and `W-46.5`. No migration for either.

| Ticket | From | To |
|---|---|---|
| `W-47.4`, `W-47.5` | unassigned | biren |

**2026-10-02 — moved to sayeed**, to shorten karma's queue ahead of `W-36.1`; sayeed takes it before its sibling `W-47.1b`. No branch carries a commit for it. No migration.

| Ticket | From | To |
|---|---|---|
| `W-47.1a` | karma | sayeed |

**2026-09-25.** sayeed holds every open defect (D-2, D-3, D-6, D-7, D-8, D-9) and follows
them with the employee chain, since `W-13.4` and `W-13.3` both touch `core.employee`. No
`dev-<name>` branch exists yet — the stray `dev-claude` and `W-10-identity` branches were
deleted the same day. Each developer creates `dev-<name>` from `main` when they start their
first ticket. `W-11.3` is on `main`, so nothing waits on permission codes.

The specs still cite migration numbers already used on `main`; **use the lane's reserved block,
not the number in the spec.** `V026` `W-13.4` · `V027` `W-09.1` · `V028`–`V029` `W-14.2` ·
`V030` `W-39.1` (**used, on main `3bf5b10`**) · `V031`–`V032` `W-19` (**used, on main `b6e6012`**) · `V033`–`V034` `W-12.1` (**used, on main `b7d03ec`**) · `V035` `W-24.1` · `V036` `W-17` (**used, on main `288f9fa`**) ·
`V037` `W-21` · `V038`–`V039` `W-20.1` · `V040` `W-23.1` · `V041` `W-39.2` (**all used, on main `b6e6012`**). `V082` `W-65.1` · `V083`–`V084` `W-65.2` (reserved 2026-09-27, above the `V042`–`V081` block) · `V085`–`V088` `W-41` (reserved 2026-09-28) · `V135` `W-41` seed function (`core`), `V136` `W-65.1` platform-only guard, `V138` `W-65.1` subscription status CHECK (**all used, on main `5fa04b1`**; `V082`–`V088` and `V110`–`V115` likewise) · `V089`–`V092` `W-15.1`–`W-15.3` (**used, on main `a34c14f`**) · `V093` `W-20.2` · `V094` `W-22.2` · `V095`–`V096` `W-23.2` (**used, on main `b6e6012`**; reserved 2026-09-28, above the `V089`–`V092` block — manager's review item B-1: devashis's branch first shipped these as `V082`–`V085`, which collided with `W-65.1`, `W-65.2` and `W-41`'s reservations above; renumbered before merge) · `V104` `W-33.3` (reserved 2026-09-29, above `W-36.2`'s `V103`; `W-33.1` and `W-33.2` create no table) · `V107` `W-36.3` · `V108` (`core`)–`V109` `W-36.5` (reserved 2026-09-29, above `V104`; `W-36.4` creates no table) · `V110`–`V114` `W-34.1` · `V115` `W-34.2` (reserved 2026-09-29, above `V109`; `W-34.3` creates no table) · `V054` `W-28` · `V116` and `V119` `W-18.1` · `V117`–`V118` `W-24.2` (**used, on main `288f9fa`**) · `V120`–`V124` `W-40.1`–`.5` (reserved 2026-09-30) · `V125` `W-18.2` (**used, on main `b580d8b`**; `dev-devashis`'s `core/V125__hrms_project_seed_roles.sql` collides with it and renumbers above `V134` before it merges) · `V126`–`V134` `W-16.1`–`W-16.4b` (**used, on main `a53e311`**; `W-25` creates no table) · `V140` `W-38.1` (reserved 2026-10-02, above `dev-devashis`'s `V135`–`V138`) · `V141`–`V144` `W-42.1` · `V145` `W-42.2` (reserved 2026-10-02) · `V146` `W-43.1` (reserved 2026-10-02).

**Assign later, cross-lane:** `W-24.2` is on `main` (`288f9fa`); `W-20.2`, `W-22.2`,
`W-23.2` are on `main` (`b6e6012`); `W-15` is on `main`; `W-16`, `W-18`, `W-25` (need `W-14.2`, on `main`).

## Defects on `main`

One row per known defect in merged code. A row leaves this table only when its fix is on
`main`. Status: `open` · `spec ready` · `assigned` · `fixed`.

| # | Defect | Found | Fixed by | Status |
|---|---|---|---|---|
| D-1 | `mvn verify` fails: `shared`'s `DatabasePrivilegesIT` hits `53300 too_many_connections` (16 test contexts × pool of 10 > 100 slots) | 2026-09-25, running the suite | `W-04.1` | **fixed** `3350cf2` |
| D-2 | Worker never reads the queue; no consumer loop, producer bean only in `worker`, no retry-then-fail, `RUNNING` jobs re-run | 2026-09-24, `12-core-contracts.md` §5 | `W-52.1` | **fixed** `5c07c45` |
| D-3 | `GET /jobs/{id}` has no `@RequiresAction` and honours a legacy `organizationId` header | 2026-09-24 | `W-52.1` | **fixed** `5c07c45` |
| D-4 | Every tenant's seeded `platform-admin` role holds `core.tenant.provision` (platform staff only) | 2026-09-24 | `W-11.3` | **fixed** `3350cf2` |
| D-5 | Core actions catalogued as `hrms.*` (leave, holiday, attendance) — a module filter would strip them from a Payroll-only tenant | 2026-09-24 | `W-11.3` | **fixed** `3350cf2` |
| D-6 | No link from `core.employee` to `core.user_account`; `*_own` actions and the portal cannot resolve the caller | 2026-09-24 | `W-13.4` | **fixed on main `e699699`** |
| D-7 | Dead duplicates: `core.cache.PermissionCacheService`, `PermissionInvalidationService`, `core.queue.*` | 2026-09-24 | `W-53.1` | **fixed** `5c07c45` |
| D-8 | Tax slab seed has only `GENERAL`; senior and super-senior over-deducted (#146) | 2026-09-22 | `W-09.1` | **fixed on main `c79755c`** |
| D-10 | Every role's side menu shows raw keys (`nav.setup`, `nav.employees`): `AppShell.jsx` rendered `labelKey` as it came, and no translation step existed (`W-45` §2 deferred it) | 2026-10-07, founder on Azure dev | claude: `shell/navigation/navLabels.js` maps all 36 backend keys to English, an unknown key falls back to words; `navLabels.test.js` fails the build when a backend catalogue adds a key with no label; CI now runs the frontend job when a backend `navigation/` file changes | **fixed on main `6ba5a1da`** — built by claude |
| D-11 | The Storage Queue was never connected on Azure, so every email waits 2–3 minutes for the delivery sweep. `W-52` asked for managed identity (`W-52-queue-worker.md:64`) but only the connection-string path was built (`StorageQueueConfig.java:16-18`); `W-52.1` carried it forward "unchanged" (`W-52-1-worker-fix.md:76`); `W-51` forbids the account key a connection string needs; no ticket passed the queue endpoint to the containers | 2026-10-07, founder on Azure dev (invitation emails late) | `W-52.2` | **fixed on main `cc8bd8ce`** — built by karma; takes effect on Azure once `AZURE_STORAGE_QUEUE_ENDPOINT` reaches the app and worker containers |
| D-12 | Every user saw a 404 right after login: Keycloak returns to `/`, and no route matched `/` (`AppShell.jsx` mounted only feed paths) | 2026-10-07, founder on Azure dev | claude: `/` redirects to the first path in the navigation feed | **fixed on main `c470db64`** — built by claude |
| D-13 | The signed-in tenant's name appeared nowhere in the app: `GET /api/v1/navigation` carried no tenant name | 2026-10-07, founder on Azure dev | claude: `tenantName` on the navigation reply, read under RLS (the target tenant's while acting as); shown in the header | **fixed on main `c470db64`** — built by claude |
| D-14 | Keycloak branding on login and admin pages: realm heading used `kc-logo-text` (Keycloak's CSS shows its logo and hides the text); stock titles, favicons and logos | 2026-10-07, founder on Azure dev | claude: `infinevo` Keycloak theme as server default; class dropped from both realm files; Azure dev realms fixed by hand | **fixed on main `c470db64`** — built by claude; reaches Azure with the next Keycloak image |
| D-15 | Leave Allocation fails with `VALIDATION_FAILED: No effective policy found` when `effectiveFrom` is null / unconfigured | 2026-10-06, manual QA Stage 4 (`S4-10`) | sayeed (`dev-sayeed`), claude | **fixed on main 2026-10-13** — built by sayeed; merge review made the date optional end to end and added tests |
| D-16 | Holiday Calendar creation with `isDefault: true` throws HTTP 409 Conflict if default calendar exists | 2026-10-06, manual QA Stage 4 (`S4-01`) | sayeed (`dev-sayeed`), claude | **fixed on main 2026-10-13** — built by sayeed; two screen tests added in the merge |
| D-17 | Employee portal login blocked by Azure Front Door WAF rate-limiting rule (`The request is blocked. 20261007T...`) | 2026-10-07, manual QA Stage 19 (`S19-01`) | infra / WAF policy tuning (`frontdoor.bicep`) | open |
| D-18 | Employee invitation acceptance throws HTTP 500 (`An unexpected error occurred while processing the invitation`) due to premature `TenantContext.clear()` before Hibernate audit flush | 2026-10-07, manual QA employee invitation accept (`POST /api/v1/invitations/accept`) | sanjib, claude (`b6a798f1`) | **fixed on main `b6a798f1`** |
| D-19 | Work Locations table: "Name" column text is vertically clipped/misaligned across lines (`S1-05`) | 2026-10-06, Devashish manual QA (his `D-10`) | frontend CSS (`/org/work-locations`) | **open** — see also Krushna `BUG-D4-01` (§10, not merged) |
| D-20 | `/roles` page displays HTTP 404 "Sorry, the page you visited does not exist" (`S1-08`, `S17-01`) | 2026-10-06, Devashish manual QA (his `D-11`) | feature deployment (`/roles`) | fixed on `main` as `D-73` (Roles screen) — his `RoleMatrixScreen` dropped at merge |
| D-21 | Invite User modal: Role select dropdown does not automatically close after an option is selected (`S2-01`) | 2026-10-06, Devashish manual QA (his `D-12`) | frontend select component (`/invitations/users`) | **open** |
| D-22 | User Invitations: Revoked invitations display empty Actions column without delete/purge action (`S2-10`) | 2026-10-06, Devashish manual QA (his `D-13`) | frontend / core API (`/invitations/users`) | **open** |
| D-23 | Finance role RBAC: User assigned `finance` only sees `Organisation`; Claims, Deductions, and Approvals are missing (`S2-06`) | 2026-10-06, Devashish manual QA (his `D-14`) | RBAC role_action & dynamic nav feed | **fixed on main 2026-10-13** — `V174` grants finance `core.approval.decide` (his `V149` replaced: it re-created `seed_system_roles` from a pre-`V158` snapshot and was out of order) |
| D-24 | Employee creation allows duplicate employee records with identical Work Email and Mobile Number (`S3-02`) | 2026-10-06, Devashish manual QA (his `D-15`) | `core.employee` uniqueness validation | fixed on `main` as Krushna `BUG-D4-14` (duplicate work email) |
| D-25 | Employee Reporting Line tab: "Set Manager" button not visible when logged in as HR (`S3-05`) | 2026-10-06, Devashish manual QA (his `D-16`) | `core.reporting_line.manage` restricted to `tenant-admin` | fixed on `main` as Krushna `BUG-D4-19` (`V172`) |
| D-26 | User Management: Missing "Edit Roles" action to modify or assign additional roles to active users (`S3-10`) | 2026-10-06, Devashish manual QA (his `D-17`) | frontend user management (`/users`) | **open (P2)** |
| D-27 | Employee Invitations: Accepting email invitation link fails with HTTP 404 or "Try again later" error (`S3-11`, `S3-12`) | 2026-10-06, Devashish manual QA (his `D-18`) | token verification & AFD routing (`/invitations/accept`) | fixed on `main` `b6a798f1` / `D-88` |
| D-28 | Employee Exit: Termination modal only prompts for Last Working Day; "Reason" and "Remarks" fields are missing (`S14-03`) | 2026-10-06, Devashish manual QA (his `D-19`) | frontend termination drawer (`/employees/:id`) | fixed on `main` as Krushna `BUG-D4-20` (`V173`) |
| D-29 | Payroll Officer sidebar: "Pay Runs" menu item (`/payroll/runs`) is not visible in navigation (`S14-05`) | 2026-10-06, Devashish manual QA (his `D-20`) | `nav.payroll.runs` navigation permissions | **open (P1)** |
| D-30 | Employee Directory: Filtering by `TERMINATED` status displays Date of Joining but omits Last Working Day (`S14-07`) | 2026-10-06, Devashish manual QA (his `D-21`) | employee directory columns (`/employees`) | **open (P3)** |
| D-31 | Employee Exit: Terminated employee profile lacks a "Reactivate Employee" button to retract exit (`S14-08`) | 2026-10-06, Devashish manual QA (his `D-22`) | frontend actions menu (`/employees/:id`) | fixed on `main` as Krushna `BUG-D4-21` |
| D-32 | Custom Roles: Cannot assign custom role `leave-auditor` in User Invitations because `/roles` is not deployed (`S17-04`) | 2026-10-06, Devashish manual QA (his `D-23`) | custom role engine (`/roles`) | fixed on `main` as `D-73` |
| D-94 | Route Mismatch: `/payroll/settings/tax-declaration/2026` returns HTTP 404 in React Router; live route is `/payroll/settings/tax-declaration` (`S9-01`) | 2026-10-06, Devashish manual QA (his `D-24`) | frontend router (`src/core/routes.js`) | **fixed on main 2026-10-13** — `/payroll/settings/tax-declaration/:year` route, year normalised (`TaxWindowScreen`) |
| D-95 | Proof of Investment: Receipt upload dropzone and verification queue route `/payroll/tax-declarations/review` unmounted / missing (`S16-01`..`S16-07`) | 2026-10-06, Devashish manual QA (his `D-25`) | frontend declaration & POI review routes | **open** — proof-of-investment screens held back at merge: no spec (W-34 defers screens to W-47), review route never mounted, receipt links carried no token, item statuses did not match the backend; founder writes the `W-47` proof-screens spec |
| D-96 | `/employees`: In Employee Directory, Employee IDs are not sorted sequentially (e.g. EMP-001, EMP-002, EMP-003). | 2026-10-12, Krushna Day 4 manual QA (`BUG-D4-13`) | Backend: Added `ORDER BY employee_number ASC` natural ordering in `EmployeeQueryService.java`. Frontend: Added default comparator on `employeeNumber` column in `EmployeeList.jsx`. | **fixed on main `9bfcf532`** — built by krushna |
| D-97 | `/employees/:id` (Identification): System accepts duplicate PAN and Aadhaar across multiple employees; backend fields not enforced mandatory. | 2026-10-12, Krushna Day 4 manual QA (`BUG-D4-15`) | Backend: Enforced mandatory PAN & Aadhaar check and tenant-level uniqueness in `EmployeeIdentificationServiceImpl.java` throwing HTTP 400. | **fixed on main `9bfcf532`** — built by krushna |
| D-98 | `/employees/:id` (Documents): In Employee Documents, "Delete document" confirmation popup remains stuck on screen after deletion completes. | 2026-10-12, Krushna Day 4 manual QA (`BUG-D4-16`) | Updated `DocumentsTab.jsx` to explicitly invoke `setDeleteModalOpen(false)` upon delete promise completion. | **fixed on main `9bfcf532`** — built by krushna |
| D-99 | `/employees/new`, `/org/*`: Mixed/inconsistent casing allowed on Employee names, Departments, and Designations. | 2026-10-12, Krushna Day 4 manual QA (`BUG-D4-17`) | Implemented `toTitleCase` helper in `shared/ui/format.js`; normalized employee names in `EmployeeServiceImpl.java`, and department/designation names in `AbstractOrgMasterServiceImpl.java` and `MasterTable.jsx`. | **fixed on main `9bfcf532`** — built by krushna; rewritten in the merge (§9.3) |
| D-100 | `/employees/:id` (Identification): Aadhaar number not validated as mandatory when required; missing 12-digit format check. | 2026-10-12, Krushna Day 4 manual QA (`BUG-D4-18`) | Added pre-flight client-side validation in `SectionTab.jsx` enforcing mandatory values for all `field.required` fields (including 12-digit Aadhaar `^\d{12}$` and PAN formats). | **fixed on main `9bfcf532`** — built by krushna |
| D-101 | `/employees/:id`: Single employee invitation send option not visible on Employee page; confirmation modal unmount leaks. | 2026-10-12, Krushna Day 4 manual QA (`BUG-D4-22`) | Added prominent "Send Invitation" button to Action buttons bar in `EmployeePage.jsx` for active employees without access, added `Modal.destroyAll()` unmount cleanup and explicit instance destruction. | **fixed on main `9bfcf532`** — built by krushna |
| D-102 | Payslip PDF / view mismatch with legacy: missing Employee Status, PF No, UAN No, LWP Days, Net Pay summary layout, Amount in Words, and system-generated signature notice. | 2026-10-22, manual QA / founder | Backend: enriched `PayslipResponse.EmployeeSummary` with `status`, `pf_number`, `uan_number` from `core.employee` and `payroll.employee_statutory_profile`. Frontend: created `PayslipDocument` with full legacy Employee Summary, net pay highlight, separate earnings/deductions subtotals, Amount in Words (Indian numbering), and system signature notice. | **fixed on main** — verified compilation, lint, and build |
| D-103 | Frontend dependencies Trivy HIGH CVEs: `follow-redirects` (CVE-2026-111755, CVE-2026-111756) & `vite` (CVE-2026-112942). | 2026-10-22, CI Trivy scan | Added `follow-redirects` >= 1.16.1 and `vite` >= 8.3.3 overrides in `code/frontend/package.json`, installed `@rolldown/binding-win32-x64-msvc` in optionalDependencies, and updated `package-lock.json`. Verified build and lint pass with 0 errors. | **fixed on main** — verified `npm run build` and `npm run lint` |
| D-104 | Attendance Clock-Out Deadlock: Attempting to clock out a session older than 24 hours throws HTTP 409 Conflict ("Session cannot be clocked out more than 24 hours after clock in"); UI locked on "Clock out" with no way for employee to clock in and start a new session. | 2026-10-22, manual QA / founder | Backend: `ClockServiceImpl.clockIn()` auto-voids stale sessions (>24h or previous date) on clock-in. Frontend: `ClockCard.jsx` detects stale/expired sessions (>24h or previous date), warns user, and switches action button to "Clock in" to auto-close stale session and open today's session. | **fixed on main** — verified tests pass |
| D-105 | Timesheet Entry: Cell work description was implemented as a 240px comment note popover (`NoteCell` with `MessageOutlined` comment icon) without validation, whereas legacy HRMS has an inline Description box (`TextField`, multiline rows=2) that is strictly mandatory whenever hours are entered (`hour > 0`). | 2026-10-23, legacy parity review | sayeed (`dev-sayeed`) | **fixed on main** — verified with unit and component tests (`npm test -- src/hrms/timesheet`), 0 lint warnings |
| D-106 | Organisation Master Navigation Exposure: Sidebar navigation catalogue exposes administrative master pages (Departments, Designations, Work Locations) to Employee and non-admin roles because menu items require `core.org.read` instead of `core.org.manage`. | 2026-10-23, employee login access review | sayeed (`dev-sayeed`) | **fixed on main `2547ab2d`** — gated by `core.org.manage` with `anyOf = "core.org.read"` on controllers |
| D-107 | WFH Module UI Screen Layout Collapse & Leave Parity: Text wraps vertically character-by-character into 50–80px columns, buttons squashed, tables pushed far right across all WFH admin and self-service screens due to invalid `orientation="vertical"` on AntD `<Space>`. | 2026-10-23, manual QA / user testing | krushna (`dev-krushna`) | **fixed on main `c5b9bd1f`** — replaced with `direction="vertical"`, full-width Card architecture matching Leave module parity, verified with 9 unit/component tests & CI green |
| D-108 | Post-Logout Landing on Unauthorized Route / Login Return Path: Signing out while on a privileged path (e.g. `/approvals`, `/payroll/runs`) and logging in as an employee or role without access sent user back to that unauthorized path, leaving them stranded on `<NoModules />` or a dead-end 404 without redirecting to their home page (`/me`). | 2026-10-26, login/logout session role switching review | sayeed (`dev-sayeed`) | **fixed on main `1ac99343`** — default `redirectUri` to app root on logout, empty feed falls back to `feedHomePath` (`/me`), and `NotFound` provides "Back to Home" button |
| D-109 | Employee Document & Identification Fields Free-Text to Dropdown Parity: `idProofType`, `addressProofType`, and `immigrationStatus` were free-text `<Input />` fields prone to typos and inconsistent values, whereas legacy HRMS strictly used predefined `<Select>` dropdowns with standard options. | 2026-10-26, employee document parity review | sayeed (`dev-sayeed`) | **fixed on main `b1296254`** — converted to searchable dropdowns in `sectionFields.js` (`ID_PROOF_TYPE_OPTIONS`, `ADDRESS_PROOF_TYPE_OPTIONS`, `IMMIGRATION_STATUS_OPTIONS`) with full test coverage |
| D-110 | Employee Dashboard Landing Page & Navigation Hierarchy: HRMS Dashboard was labeled "HR dashboard" in navigation and buried inside the HRMS dropdown folder, while employees landed on `/me`. Tester identified that Dashboard should be top-level, labeled "Dashboard", used as the landing page, and self-service portal (`/me`) positioned directly below it. | 2026-10-26, employee dashboard review | sayeed (`dev-sayeed`) | **fixed on main `a82fbffd`** — `nav.hrms.dashboard` renamed to "Dashboard", elevated to top-level preceding `/me`, holidays moved under HRMS only, and `homePath` updated to land employee on dashboard |
| D-111 | Employee Form Personal & Contact Fields Free-Text to Dropdown Parity: `nationality`, `state` (Present State), `permanentState` (Permanent State), `emergencyContactRelationship`, and `secondaryEmergencyContactRelationship` were free-text `<Input />` fields, causing typos and inconsistent values. Legacy HRMS and statutory standards strictly used predefined `<Select>` dropdowns. | 2026-10-27, employee form legacy parity review | sayeed (`dev-sayeed`) | **fixed on main `0da553d0`** — converted to searchable `<Select>` dropdowns in `sectionFields.js` (`NATIONALITY_OPTIONS`, `INDIAN_STATE_OPTIONS`, `RELATIONSHIP_OPTIONS`), added `stateCode` auto-derivation in `SectionTab.jsx`, verified with 16 unit tests, lint & build |
| D-112 | Timesheet Approval Approver Cannot See Daily Work Descriptions: In Timesheet Review (`/hrms/timesheet-review/:id`) and Approvals Inbox Timesheet Detail (`/hrms/timesheet-review/entries/:entryId`), approvers could not see employee daily task descriptions. `taskRows` omitted `day.description`, and `gridColumns` did not render descriptions. | 2026-10-27, timesheet approval defect review | sayeed (`dev-sayeed`) | **fixed on main `f7cf7b51`** — extracted descriptions in `taskRows`, rendered in table day cells with secondary text & tooltips, added expandable "Work descriptions" section in `WeekView.jsx` and `EntryView.jsx`, verified with 18 unit tests, lint & build |
| D-113 | Timesheet Approval Missing Inline Approve / Reject Buttons: On the timesheet entry review screen (`/hrms/timesheet-review/entries/:entryId`), approvers inspecting submitted timesheet hours and daily work descriptions could not approve or reject directly from the screen, and were forced to return to the generic approvals inbox (`/approvals`). | 2026-10-27, timesheet approval defect review (Item #10) | sayeed (`dev-sayeed`) | **fixed on main `78ce6ed2`** — added inline Approve and Reject action buttons with `DecideModal` dialog on `EntryView.jsx`, updated pending count in Redux, and linked project status tags from `WeekView.jsx` directly to entry decision view; verified with 19 unit tests, lint & build |
| D-114 | Overtime Approval Missing Inline Approve / Reject Buttons: On the overtime detail screen (`/hrms/overtime-requests/:id`), approvers inspecting overtime request details could not approve or reject directly from the screen, and were forced to return to the generic approvals inbox (`/approvals`). | 2026-10-27, overtime approval defect review (Item #11) | sayeed (`dev-sayeed`) | **fixed on main `71135a50`** — added inline Approve and Reject action buttons with `DecideModal` dialog on `OvertimeDetail.jsx`, updating pending count in Redux and request status in place; verified with 4 unit tests, lint & build |
| D-115 | Regularization Approval Missing Inline Approve / Reject Buttons: On the regularization detail screen (`/hrms/regularizations/:id`), approvers inspecting regularization request details could not approve or reject directly from the screen, and were forced to return to the generic approvals inbox (`/approvals`). | 2026-10-27, regularization approval defect review (Item #12) | sayeed (`dev-sayeed`) | **fixed on main `a0758fea`** — added inline Approve and Reject action buttons with `DecideModal` dialog on `RegularizationDetail.jsx`, updating pending count in Redux and request status in place; verified with 3 unit tests, lint & build |
| D-116 | Salary Components Earning Type Missing "Fixed Allowance": On the Salary Components screen (`/payroll/components`), the mandatory Earning Type dropdown in the Add/Edit Earning drawer omitted "Fixed Allowance", a core earning component heavily utilized in legacy HRMS for CTC structures and revisions. | 2026-10-27, salary components defect review (Item #5) | sayeed (`dev-sayeed`) | **fixed on main `d2f9a944`** — added 'Fixed Allowance' to `EARNING_TYPE_NAMES` in `componentFields.js`, updated validation schema and `ComponentDrawer.test.jsx`; verified with 27 unit tests, lint & build |
| D-117 | WFH Policy Screen Parity Defect: Missing default fallback policy card and missing Edit button on policy cards on `/wfh/policies`. | 2026-10-28, Stage 21 Track B (`W-01`, `W-02`, `W-03`) | krushna (`dev-krushna`) | **fixed on main `e822f8da`** (BUG-D4-01 / BUG-D4-01b) — fallback DEFAULT_WFH card & Edit Policy modal added |
| D-118 | WFH Allocations Table Fails to Render: The quota allocations table on `/wfh/allocations` loads blank / fails to render employee quota records. | 2026-10-28, Stage 21 Track B (`W-04`) | krushna (`dev-krushna`) | **fixed on main `e822f8da`** (BUG-D4-02) — defensive unwrapping & departmentName/consumed/balance populated |
| D-119 | WFH Quota "Assign Allocation" Raw UUID & Missing Typeahead: On `/wfh/allocations`, the employee selector displays raw database UUIDs and lacks autocomplete/search suggestions. | 2026-10-28, Stage 21 Track B (`W-05`) | krushna (`dev-krushna`) | **fixed on main `e822f8da`** (BUG-D4-03) — searchable EmployeePicker dropdown integrated |
| D-120 | Bulk WFH CSV Import Template Missing Header: Downloaded bulk CSV template (`/v1/wfh-imports/template`) is missing the required `effective_date` header column. | 2026-10-28, Stage 21 Track B (`W-06`) | krushna (`dev-krushna`) | **fixed on main `e822f8da`** (BUG-D4-04) — effective_date added to CSV parser, sample & pro-rata calculation |
| D-121 | WFH Applications & History Tables Missing "Actions" Column and "View" Button: Unlike Leave requests, `/wfh/requests` and `/me/wfh` lack the Actions column and `👁 View` button to open details drawer. | 2026-10-28, Stage 21 Track B (`W-08b`) | krushna (`dev-krushna`) | **fixed on main `e822f8da`** (BUG-D4-05) — Actions column & View modal added to MyWfh and WfhRequests |
| D-122 | Approvals Inbox Missing "View Item", Raw Enum Title, & Missing 2-Tier HR Routing: In `/approvals`, WFH requests lack `👁 View Item`, display `WORK_FROM_HOME Request` with awkward wrap, and approving at Level 1 fails to route to HR Level 2. | 2026-10-28, Stage 21 Track B (`W-09`) | krushna (`dev-krushna`) | **fixed on main `e822f8da`** (BUG-D4-06 / BUG-D4-07) — WORK_FROM_HOME flow type mapped to /wfh/requests, 2-stage approval seeded via V186 |
| D-123 | WFH Balance Auto-Defaulting to 12 Days Without HR Allocation: Newly created employees automatically display 12 days annual quota on `/me/wfh` (`GET /v1/me/wfh-balance`) even without any allocation assigned by HR. Balance must remain 0 / unallocated until HR allocates quota. | 2026-10-28, Stage 21 Track A/B parity review (`E-WFH-01`, `W-04`) | krushna (`dev-krushna`), sayeed (`dev-sayeed`) | **fixed on main `e822f8da`** (BUG-D4-08) — hasAllocation guard, UI unallocated alert, backend pre-flight rejection |
| D-124 | WFH Request Details Modal Missing Multi-Stage Approval Trail: When employee applies for WFH and manager approves at Step 1, the WFH Application Details modal (`/wfh/requests`, `/me/wfh`) only displays a flat "PENDING" status tag and raw employee UUID (`04610e36-8d24-4b2d-a639-348c0bfe1229`). It fails to display the multi-stage Approval Trail (parity with Leave request details), leaving employees and HR unable to see whether the manager has approved (Step 1 — Approved by Manager Name, Date, Notes) and whether HR approval is pending (Step 2 — Pending HR Approval). | 2026-10-29, WFH 2-tier approval verification (`W-08b`, `W-09`) | krushna (`dev-krushna`) | **fixed on main** (BUG-D4-09) — integrated approvalService.history, Timeline trail & formatted employee |
| D-125 | Attendance Log Missing Day Status / Remarks Column for Approved WFH (Dual System Audit): On `/hrms/attendance-log`, approved WFH days display only raw clock punch intervals (`In`, `Out`, `Worked`, `Origin: Clock`). It lacks a "Status / Remarks" column to display the automatically created `PRESENT` status and `Work From Home` badge (per test case `S21-17` / `W-11` Dual System Audit specification). | 2026-10-29, Stage 21 Track B (`W-11`, `S21-17`) | krushna (`dev-krushna`), sayeed (`dev-sayeed`) | **fixed on main** (BUG-D4-10) — dayStatus and remarks joined from Attendance, Day Status column added |
| D-126 | WFH Request Details Modal Approval Trail Not Visible to Employees in Self-Service: While HR can view the multi-stage Approval Trail on `/wfh/requests`, opening the WFH Request Details modal in Employee Self-Service (`/me/wfh`) displays "No approval steps recorded." This occurs because `GET /api/v1/approvals/{instanceId}/history` enforces `@RequiresAction(value = "core.approval.read", anyOf = "core.approval.decide")`, rejecting ordinary employees with HTTP 403 Forbidden. | 2026-10-29, Employee Self-Service WFH verification (`E-WFH-02`, `/me/wfh`) | krushna (`dev-krushna`) | **open** (ENH-D4-11 / BUG-D4-11) |
| D-34 | The side menu is one flat list: employees, roles, audit, setup, tenants and both invitation lists are top-level items (`NavigationCatalogue.java:52,82-83,92,123-137`); only org, approvals, holiday and leave have children | 2026-10-07, founder on Azure dev | catalogue groups: **People** (employees, invitations) · **Organisation** (org masters, holidays, roles) · **Settings** (setup, audit) · Payroll and HRMS under their module parents; groups collapsed by default, labels in `navLabels.js` | **fixed on main `f996cd1c` + `a6b3dca3`** — built by claude; Payroll and HRMS grouped under module parents; groups collapsed except the current one |
| D-35 | Every role lands on the first menu path after login (`AppShell.jsx:42,136`): a tenant admin lands on Employees, an employee on whatever is first, platform staff on a customer screen | 2026-10-07, founder on Azure dev | home path by role: `tenant-admin` → `/setup` until the checklist is complete, then the module dashboard · `employee` → `/me` · `payroll-officer` → `/payroll/dashboard` · `hr`, `manager` → `/hrms/dashboard` or `/employees` · platform tenant → `/admin/tenants`; the feed returns `homePath`, the shell uses it | **fixed on main `f996cd1c` + `a6b3dca3`** — built by claude; `homePath` on the navigation feed; a role with no dashboard in its feed lands on its first screen |
| D-36 | Password rule is `length(8) and notUsername and notEmail` (`infinevo-realm.json:23`): no upper, lower, digit or special character, no history, no brute-force lockout | 2026-10-07, login screen review | realm: `length(10) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername and notEmail and passwordHistory(5)` plus brute-force detection; both realm files; Azure dev realm by hand | **fixed on main `f996cd1c` + `a6b3dca3`** — built by claude; both realm files; `check-realm.mjs` enforces it. Keycloak 25 validates imported credentials, so the seeded dev password is `Local_dev_pw1!` (reset the `pgdata` volume once). Azure dev realm still by hand |
| D-37 | Earning type is `FIXED` / `VARIABLE` / `ONE_TIME` (`componentFields.js:27-31`, `ComponentDrawer.jsx:161-163`) where legacy offers the 32 named earning types the tax and payslip rules key on — Basic, HRA, DA, Conveyance, Bonus … (`addNewCustomEarning.js:74-87`). `earning_type` is already a free string (`Earning.java:24`) and `is_variable` already exists (`Earning.java:44`), so no migration | 2026-10-07, legacy cross-check | drawer: **Earning type** becomes the named list (searchable); **Variable** becomes a checkbox writing `is_variable`; frequency shows only when Variable is ticked; existing `FIXED`/`VARIABLE`/`ONE_TIME` rows map on read | **fixed on main `f996cd1c` + `a6b3dca3`** — built by claude; `TaxInputAssembler` recognises "House Rent Allowance" by name |
| D-38 | The component schema carries `variable`, `epfInclusionType`, `emiType`, `perquisiteInterestRate`, `superannuation`, `taxExemptSection`, `carryForwardOption`, `optIn` (`componentFields.js:66-108`) but the drawer never renders them (`ComponentDrawer.jsx:150-180` shows type and frequency only), so the EPF-inclusion radio, loan, benefit and reimbursement options legacy has cannot be set | 2026-10-07, legacy cross-check | drawer: a collapsed **Statutory & tax** group per component kind renders the hidden fields; EPF inclusion as a radio (always / when PF wage < 15,000 / never) | **fixed on main `f996cd1c` + `a6b3dca3`** — built by claude |
| D-39 | EPF settings merge EDLI and admin charges into one pair of switches, `include_edli_admin_in_ctc` and `include_edli_admin_in_structure` (`EpfScreen.jsx:101-103`, `EpfSetting.java:90-97`, `V062`), where legacy keeps EDLI and admin charges as separate choices (`editEPF.js:443,457`) | 2026-10-07, legacy cross-check | migration: split into `include_edli_in_ctc`, `include_admin_in_ctc`, `include_edli_in_structure`, `include_admin_in_structure`, backfilled from the merged columns; screen shows four switches; employer total follows them | **fixed on main `f996cd1c` + `a6b3dca3`** — built by claude; `V153`; the merged columns stay until a later migration drops them. Also fixed: the statutory settings screens never loaded (snake_case keys read from a camelCase reply) |
| D-40 | Add Employee asks for six fields only — number, names, work email, mobile (`EmployeeCreate.jsx:143-241`) — and no section holds gender, date of joining, job title, employment type, probation end or notice period (`sectionFields.js`); legacy HRMS captures gender on the same form (`AddEmployee.jsx:1007-1012`) | 2026-10-07, legacy cross-check | Add Employee: **Required** (names, work email, date of joining, department, designation, location) and a collapsed **More** group (gender, employment type, probation end, notice period, mobile); the same fields on the Employment section; `core.employee` gains the missing columns by migration | **fixed on main `f996cd1c` + `a6b3dca3`** — built by claude; `V154` on `employee_employment` (gender and joining date already existed; designation is the job title). Department, designation and location optional on Add Employee (founder 2026-10-07) |
| D-41 | Time zone and shift times are free text (`sectionFields.js:84-86`): any string saves | 2026-10-07, legacy cross-check | time zone: a searchable select of IANA zones, default the tenant's; shift start and end: time pickers, `HH:mm` validated on the backend | **fixed on main `f996cd1c` + `a6b3dca3`** — built by claude; time zone stays blank until picked; nothing carries the tenant's zone yet |
| D-42 | Create Tenant has no administrator email (`TenantCreate.jsx:70-109`, `TenantRequest.java:9`): after creating a tenant the platform staff must act-as into it and invite the admin by hand | 2026-10-07, founder on Azure dev | `TenantRequest` gains `adminEmail`; provisioning creates a `tenant-admin` user invitation in the new tenant and sends it; the tenant list shows "admin invited / accepted" | **fixed on main `f996cd1c` + `a6b3dca3`** — built by claude; `V159`; `admin_email` refused when `INVITATION_LINK_BASE_URL` is unset |
| D-43 | Project Manager & Team Member pickers require typing >= 2 chars and do not load initial dropdown options on open (save should also succeed without manager, showing 'No data' if none exist); Timesheet weekly entry lacks structured task details section (only 240px note popover) | 2026-10-08, manual QA Timesheet flow (`S7-09`) | sayeed (`dev-sayeed`) | **open** |
| D-44 | Approvals Inbox has no "Decided / History" view (approved/rejected steps vanish immediately); "View item" on timesheet step fails with "Could not load the timesheet entry"; Timesheet Review screen inaccessible/empty to Tenant Admin | 2026-10-08, manual QA Timesheet flow (`S7-10`) | sayeed (`dev-sayeed`) | **open** |
| D-45 | Submitted timesheet lacks Edit or Recall-to-Draft option; employee cannot correct hours or recreate week timesheet (blocked by unique week constraint) unless manager rejects | 2026-10-09, manual QA Timesheet flow (`S7-09`) | sayeed (`dev-sayeed`) | **open** |
| D-46 | Attendance card displays today's session timeline only (past date session intervals cannot be expanded/viewed); Tenant Admin has no employee profile and cannot clock in/out | 2026-10-09, manual QA Attendance flow (`S7-02`, `S7-03`) | sayeed (`dev-sayeed`) | **open** |
| D-47 | Regularization (`/hrms/regularizations`) and Overtime (`/hrms/overtime-requests`) tables load blank by default; date range is required to fetch data instead of acting as an optional filter with sensible defaults | 2026-10-09, manual QA (`S7-05`, `S7-07`) | sayeed (`dev-sayeed`) | **open** |
| D-48 | Rejected overtime request vanishes from Approvals and request UI instead of remaining visible with status `REJECTED` in a history/decided view | 2026-10-09, manual QA Overtime flow (`S7-08`) | sayeed (`dev-sayeed`) | **open** |
| D-49 | Leave Year input in `LeaveImport.jsx` uses `<InputNumber>`, blocking required `YYYY-YY` string format (e.g. `2026-27`) for April-cycle tenants and causing HTTP 400 Bad Request | 2026-10-09, manual QA Leave Import flow (`S20-03`, `S20-04`) | sayeed (`dev-sayeed`) | **open** |
| D-50 | Missing "Download Sample CSV Template" button/link on `/leave/import` screen | 2026-10-09, manual QA Leave Import flow (`S20-02`) | krushna (`dev-krushna`) | **fixed** — built by krushna (see §12) |
| D-51 | Error report document UUID in validation warning alert on `/leave/import` is rendered as plain text instead of a clickable download link | 2026-10-09, manual QA Leave Import flow (`S20-03`) | sayeed (`dev-sayeed`) | **open** |
| D-52 | Employee role cannot access Leave module (sidebar omits Leave and direct `/leave/requests` throws 404) because parent `core.leave` group requires `core.leave.read` rather than allowing `core.leave.apply` / `core.leave.read_own` | 2026-10-09, manual QA Employee Persona (`S20-06`, `/leave/requests`) | claude (`D-74`) | **fixed on main `db9cdc34` as `D-74`/`D-75`** — "My self-service" shows Leave for any login with an employee record; logged again here by QA before that merge |
| D-53 | Employee portal (`/me`, `MyLeave.jsx`) leave balance cards are visually misaligned and lack structured meter layout | 2026-10-12, manual QA Employee Leave view (`/me`, `S6-02`) | sayeed (`dev-sayeed`), claude | **fixed on main 2026-10-13** — built by claude: `BalanceCard` equal height, a Progress meter (used of used+remaining), "Used: X of Y days" |
| D-54 | Admin detail view of employee leave request (`/leave/requests/:id`) incorrectly displays applicant-only "Withdraw" action | 2026-10-12, manual QA Leave Request Detail (`/leave/requests/:id`, `S6-04`) | sayeed (`dev-sayeed`) | **open** |
| D-55 | Leave policy drawer (`PolicyForm.jsx`) throws `VALIDATION_FAILED: effectiveFrom cannot be null` on save and lacks input field for effective date | 2026-10-12, manual QA Leave Type Policy (`/leave/types`, `S4-10`) | sayeed (`dev-sayeed`), claude | **fixed on main 2026-10-13** — same change as `D-15`: `LeavePolicyValidator` no longer refuses an empty date, `LeaveTypeServiceImpl` starts the policy on the current leave year, `PolicyForm` sends no default |
| D-56 | Employee portal (`/me`) dashboard and Profile Summary card suffer from poor layout spacing and visual misalignment | 2026-10-12, manual QA Employee Portal (`/me`, `S19-02`) | sayeed (`dev-sayeed`), claude | **fixed on main 2026-10-13** — built by claude: `PortalLayout` double `marginTop` under the tab bar removed; antd `Space` has no `orientation` prop, so `orientation="vertical"` was ignored — changed to `direction` in every portal screen |
| D-57 | Employee portal (`/me`) lacks a "Holiday Calendar" tab / view for employees to inspect upcoming tenant/location holidays | 2026-10-12, manual QA Employee Portal (`/me`, `S19-04`) | sayeed (`dev-sayeed`) | **open** |
| D-58 | My Profile screen (`/me/profile`) form inputs and detail sections (Personal, Job, Contact) are visually misaligned | 2026-10-12, manual QA Employee Profile (`/me/profile`, `S19-06`) | sayeed (`dev-sayeed`), claude | **fixed on main 2026-10-13** — built by claude: same `Space` cause — Personal, Job and Contact rendered side by side one letter per line and the edit panel fell outside the card; `MyProfile` now a Row/Col grid with the edit panel inside it. `checkSqueezedText` in the e2e suite flags a recurrence |
| D-62 | Accepting an invitation ends on a page whose main action is "Sign in" (`AcceptInvitation.jsx:113-121`), so invitees reach the login screen before they have a password; a reused Keycloak account gets no set-password mail yet is told to wait for one (`KeycloakProvisioningServiceImpl.java:159-161`); the set-password mail carries no way back to the app; `/invitations/accept/` with a trailing slash falls into sign-in (`main.jsx:31`) | 2026-10-09, founder | claude: accept returns an `outcome` (mail sent / mail failed / existing account) and the page words its next step from it; an existing account still awaiting `UPDATE_PASSWORD` is sent the mail again; the mail carries `client_id` and the app root as `redirect_uri`; trailing slash accepted | **fixed on main — built by claude** |
| D-63 | Endless sign-in loop: every 401 sends the browser to Keycloak (`ShellBoundary.jsx:18-23`), and a live Keycloak session hands it straight back to the same 401 — e.g. `TENANT_NOT_BOUND` for an account in no tenant or several (`TenantContextFilter.java:234-243`) — until the browser errors. The invite gave no warning that a browser signed in as someone else makes Keycloak refuse the set-password link (`different_user_authenticated`, Azure dev 2026-10-09 11:37) | 2026-10-09, founder on Azure dev | claude: `TENANT_NOT_BOUND` shows an access-problem screen with Sign out; a re-login carries `relogin=1`, and a second 401 on return stops on that screen; the accept page and its success text say to sign out or use a private window | **fixed on main — built by claude** |
| D-64 | Payroll settings (pay schedule, EPF, ESI, PT, FBP, tax window) and salary components never mount: no feed path above `/payroll/settings/*` or `/payroll/components` (`routes.js:54-68`, `PayrollNavigation.java:61`) | 2026-10-09, screen audit | "Payroll settings" (`/payroll/settings`, `payroll.settings.manage`) and "Salary components" (`/payroll/components`, `payroll.structure.read`) menu items | **fixed on main — built by claude** |
| D-65 | Setup checklist: 6 of 8 steps have no Open link — pay schedule, prior payroll, salary components, EPF, ESI, PT (`setup/stepLinks.js:7-15`) | 2026-10-09, screen audit | links for every step code | **fixed on main — built by claude** |
| D-66 | Payroll employee tabs (Salary, statutory profile, scheduled earnings, FBP) are built but never shown: `EmployeePage.jsx:16` reads core's own list; `payroll/index.js:133` is never imported | 2026-10-09, screen audit | the shell gathers module employee tabs and passes them to the employee page, as for `/me` panels | **fixed on main — built by claude** |
| D-67 | FBP plan and declaration read and send `snake_case`; `FbpPlanResponse`, `FbpDeclarationResponse` are `camelCase` — plan always Disabled, saves send nulls (`FbpPlanScreen.jsx:57-111`, `FbpDeclarationForm.jsx:52-100`) | 2026-10-09, screen audit | use the server's field names | **fixed on main — built by claude** |
| D-68 | Loss-of-pay section sends `snake_case`; `LopPolicyRequest` is `camelCase` — load shows defaults, save 400s; `payroll-officer` holds no `core.lop_policy.*` (`PayScheduleScreen.jsx:95-193`) | 2026-10-09, screen audit | server field names; migration grants `core.lop_policy.read`, `.manage` to `payroll-officer` (founder 2026-10-09) | **fixed on main — built by claude** |
| D-69 | PT history drawer reads `effective_from`, `registration_number`, `slabs`; `PtHistoryResponse` sends `operation`, `before_slabs`, `after_slabs`, `changed_at`, `changed_by` (`ProfessionalTaxScreen.jsx:551-566`) | 2026-10-09, screen audit | show the change as sent | **fixed on main — built by claude** |
| D-70 | Leave policy: server sends `monthly`, `yearEndLimit`, `halfYearly` (`@JsonValue`, `AccrualFrequency.java:13-22`); `PolicyForm.jsx:53-116` expects upper case — editing shows raw values and saving wipes the over-balance limit | 2026-10-09, screen audit | form uses the wire values | **fixed on main — built by claude** |
| D-71 | Approval trail reads `status`, `stepName`, `decidedBy`, which `ApprovalHistoryStepResponse` lacks — always "Approval Step - undefined / Pending assignment" (`LeaveRequestDetail.jsx:276-290`) | 2026-10-09, screen audit | read `decision`, `stepIndex`; history steps carry the assignee's name | **fixed on main — built by claude** |
| D-72 | Detail screens open Not Found for the role that needs them: leave, regularization, overtime detail for a manager from `/approvals` (`itemRoutes.js:6-9`); HR's everyone-lists under employee-only items (`HrmsNavigation.java:88-104`); employee dashboard project links (`MyProjectsCard.jsx:12`) | 2026-10-09, screen audit | detail routes `mountWith` `/approvals`; HR menu items for all regularizations and all overtime; project links to My work | **fixed on main — built by claude** |
| D-73 | Roles (`/roles`) and Audit log (`/audit`) are menu items with no screen (`NavigationCatalogue.java:122,212`) | 2026-10-09, screen audit | Audit log reuses `AuditTab`; Roles is a read-only list of roles and actions (founder 2026-10-09) | **fixed on main — built by claude** |
| D-74 | My Payslips is a placeholder ("Coming in W-36") printing "Backend status: …" though `GET /me/payslips` works (`MyPayslips.jsx:25-47`) | 2026-10-09, screen audit | list period, paid on, net pay; View opens the payslip | **fixed on main — built by claude** |
| D-75 | No menu or header link to `/me` (`Header.jsx:126-135`, `NavigationCatalogue.java`) | 2026-10-09, screen audit | "My self-service" menu item on `core.employee.read_own`; "My profile" in the header menu (founder 2026-10-09) | **fixed on main — built by claude** |
| D-76 | Manager lands on Departments, HR on a plain list; HRMS dashboard menu needs `hrms.project.read_own`, and the endpoint 403s without an employee record (`NavigationService.java:204-214`, `HrmsNavigation.java:86`, `HrmsDashboardServiceImpl.java:101`) | 2026-10-09, screen audit | menu items may name any of several actions; manager home `/approvals`, HR home `/hrms/dashboard`; team cards without an employee record (founder 2026-10-09) | **fixed on main — built by claude** |
| D-77 | My Profile shows department, designation, work location as raw ids, status and gender raw, ISO joining date (`MyProfile.jsx:79-117`) | 2026-10-09, screen audit | names, labels, formatted dates, grouped sections | **fixed on main — built by claude** |
| D-78 | No shared formatting: raw status codes, ISO dates, money without ₹ across screens; `shared/ui/` has only `msgHelper.js` | 2026-10-09, screen audit | shared date, money and status-label helpers, applied to every screen the audit lists | **fixed on main 2026-10-13** — built by claude: `shared/ui/format.js` (`formatDate`, `formatDateTime`, `formatMonth`, `formatMoney`, `statusTag`) and `StatusTag.jsx` applied across core, hrms and payroll lists and details; every untitled action column titled "Actions". Whole-rupee tax summary figures now show two decimals |
| D-79 | Raw ids on staff screens: delegations, approval instance, claim detail, pay run `approved_by`, regularization `decided_by`, officer tax view, leave detail (`Delegations.jsx:131-140`, `InstanceDetail.jsx:116-283`, `ClaimDetail.jsx:28-63`, `RunPage.jsx:425`, `RegularizationDetail.jsx:55`, `OfficerDeclarationView.jsx:214`) | 2026-10-09, screen audit | replies carry the person's name | **fixed on main 2026-10-13** — built by claude: every reply with a person id carries the name (`approvals`, `delegations`, `leave`, `regularizations`, `claims`, `pay runs`, `tax declarations`), one batch read per page via `EmployeeService.displayNames`; pay run actors (Keycloak logins) via new `core/user/ActorNameService`. Left: raw reference ids on `ClaimDetail`/`InstanceDetail`; off-cycle compute reply (`PayRunComputationServiceImpl`) still without names |
| D-80 | Lists cut short: leave requests load 25 employee names (`LeaveRequests.jsx:56`); allocations and record-leave ask 1,000, get 100 (`Allocations.jsx:49`, `RecordLeave.jsx:26`); My Leave first 25 only (`MyLeave.jsx:27`); raw leave type id (`MyRequests.jsx:119-121`) | 2026-10-09, screen audit | names in leave request replies; server paging; search-as-you-type pickers | **fixed on main 2026-10-13** — built by claude: `LeaveRequestResponse` carries `employeeName`/`leaveTypeName`; `EmployeePicker` searches as you type (`/v1/employees?q=`); Allocations pages on the server; My Leave "Load more". Left: `MyRequests` on `/me` still reads the default page; documents looked up per row in leave lists |
| D-81 | Project, task and team errors always "Something went wrong": `projectService.js:84` reads the raw axios shape the client already flattened | 2026-10-09, screen audit | read the shared client's `message` | **fixed on main 2026-10-13** — built by claude: `projectService.errorMessage` reads `err.message` |
| D-82 | Finance: pay run names blank, off-cycle create shown then 403, claims employee filter fails (`RunPage.jsx`, `payrunService.js:282`, `OffCycleCreate.jsx`) | 2026-10-09, screen audit | names in payroll replies (founder 2026-10-09); off-cycle gated on `payroll.run.execute` | **fixed on main 2026-10-13** — built by claude: root cause was `EmployeePayRunResponse` carrying only the employee number and the screen looking names up through `GET /v1/employees`, which finance cannot read (`V022` grants no `core.employee.read`); run rows now carry `employee_name`; `OffCycleCreate` gated on `payroll.run.execute`; claims employee filter hidden without `core.employee.read`. `payrunService.js:282` in this row did not exist. Left: `DeductionList`/`DeductionGrid` employee picker still hits `/v1/employees` |
| D-83 | Failed pay run never shows `failure_reason`; any load error reads "Pay run not found" (`RunPage.jsx`) | 2026-10-09, screen audit | show the reason; tell not-found from other errors | **fixed on main 2026-10-13** — built by claude: FAILED run shows `failure_reason`; only a 404 reads "Pay run not found", other errors show the server message with Retry |
| D-84 | Attendance settings show defaults after a failed load and Save overwrites (`AttendanceSettings.jsx:67`); "My days" empty if `/today` fails (`MyAttendance.jsx:48-51`) | 2026-10-09, screen audit | disable Save on load failure; load the month on its own | **fixed on main 2026-10-13** — built by claude: a failed settings load shows the error with Retry and no form (`LoadError`), so Save cannot overwrite; "My days" loads the current month on its own and follows the server date |
| D-85 | Tenant detail shows 404 for any load error (`TenantDetail.jsx:57-69`); tenant list has no error state (`TenantList.jsx:43-45`) | 2026-10-09, screen audit | error screen with Retry | **fixed on main 2026-10-13** — built by claude: NotFound only for a 404 or `NOT_FOUND`, other errors show the message with Retry; tenant list has an error state; new `shared/ui/LoadError.jsx` |
| D-86 | Leave types offer "Hours", which `LeaveUnit` refuses (`LeaveTypes.jsx:285`); `DRAFT` has no label; employee leave history never mounts (`core/index.js:141`) | 2026-10-09, screen audit | drop Hours; Draft label; history under `/employees` and linked | **fixed on main 2026-10-13** — built by claude: Days only (`LeaveUnit` has no `HOURS`); DRAFT reads "Draft"; history mounts at `/employees/:id/leave` (the old path was outside every menu path, so the shell never mounted it) with a Leave button on the employee page for `core.leave.read`. Left: history rows link to `/leave/requests/:id`, which mounts only for a menu holding Leave requests or Approvals |
| D-87 | Timesheet and Tax declaration panels share order 5; Claims panel has no icon (`TimesheetPanelProvider.java`, `TaxDeclarationPanelProvider.java`, `PortalLayout.jsx:22-29`) | 2026-10-09, screen audit | orders 5, 6, 7; icon | **fixed on main 2026-10-13** — built by claude: orders 5, 6, 7; Claims icon; `PortalPanelOrderTest` asserts distinct orders, `PortalLayout.test` an icon per panel |
| D-88 | No way to set or recover a password once an invitation is accepted. The accept page set none; the only password came from Keycloak's set-password mail (`KeycloakProvisioningServiceImpl.java`), a reused account got no mail at all (`AcceptOutcome.EXISTING_ACCOUNT`); the live Azure dev login page showed no "Forgot password?" though the realm file says `resetPasswordAllowed: true`; nothing in the app resets a password | 2026-10-09, founder on Azure dev | founder: (1) "Forgot password?" on the login screen — `resetPasswordAllowed` on the live realm, `check-realm.mjs --live` checks the deployed realm; (2) one email, not two: the invitation link opens a set-password screen, the accept call carries the password, sets it in Keycloak and clears `UPDATE_PASSWORD`, realm policy failures shown on that screen. ("Send set-password link" on Users & Access considered and dropped.) | **fixed on main 2026-10-13** — built by claude on `dev-claude`; one independent read, its findings fixed before merge. Outstanding: `resetPasswordAllowed` must be set on the live dev realm (verify-live check 11 fails until then) |
| D-89 | The local Docker stack could not be used in a browser: the frontend called the API without `/api` and without CORS the backend refused it (`compose.yml` web `VITE_API_BASE_URL`) | 2026-10-11, building the e2e suite | the dev server proxies `/api` to the backend as nginx does in production (`vite.config.js`, `VITE_API_PROXY_TARGET`) | **fixed on main 2026-10-13** — built by claude |
| D-90 | Leave type form lets Valid From stay blank while the server requires it (`LeaveTypes.jsx:319`, `LeaveTypeServiceImpl.requireTypeFields`) | 2026-10-12, e2e suite | the field is required on the form | **fixed on main 2026-10-13** — built by claude |
| D-91 | A login with no employee record (tenant admin, platform staff) opening a self-service screen was told "Not permitted: requires action 'hrms.timesheet.read_own'" though it holds the action: sixteen `currentEmployee().orElseThrow(PermissionDeniedException)` sites in hrms and payroll | 2026-10-12, e2e suite (tenant-admin Timesheets > Open this week) | `NoEmployeeRecordException` (shared): "No employee record is linked to your account…", still 403; `AuthzExceptionHandler` passes the message through | **fixed on main 2026-10-13** — built by claude (16 sites) |
| D-92 | Manager opening a leave request from the inbox gets 400 "connection is in auto-commit mode: the tenant binding is transaction-local": `LeaveRequestController`, `LeaveAllocationController` and `LeaveConsumptionController` called `ReportingLineRepository.findDirectReports` outside a transaction | 2026-10-13, e2e suite (manager View Item → `/leave/requests/:id`) | `ReportingLineService.directReportIds` (`@Transactional(readOnly = true)`) used by all four call sites | **fixed on main 2026-10-13** — built by claude |
| D-93 | Manual leave allocation always sends 1 January – 31 December as the leave year (`Allocations.jsx:101`); for an April-cycle tenant (the default) no policy is in force on 1 January, so HR gets "No effective policy found for leave type <uuid>" — the part of `D-15` Sayeed's fix did not reach | 2026-10-13, e2e suite (HR Allocate) | `GET /tenants/current/profile` carries `leaveYearStartMonth`; the screen builds the window from it and shows it beside Leave Year | **fixed on main 2026-10-13** — built by claude. Still open: the error names the leave type by UUID |

Sizes: D-33, D-35, D-36, D-37, D-41, D-42 are S; D-34, D-38, D-39, D-40, D-43, D-44, D-45, D-46, D-47, D-48, D-49, D-50, D-51, D-52, D-53, D-54, D-55, D-56, D-57, D-58 are M. Each is one branch, one commit, no spec. Anything larger from the same 2026-10-07 review is a `W-73` child spec (§6).

### QA & Manual Testing Defect Log (Dev 5 — Time & Operations)

#### D-15 (BUG-D5-001): Leave Allocation & Application Missing Effective Policy
- **Found:** 2026-10-06, Stage 4 (`S4-10`, `S4-11`) & Stage 6 (`S6-02` to `S6-10`), `POST /api/v1/leave/allocations`, `GET /api/v1/leave-types/eligible`
- **Error:** `VALIDATION_FAILED: No effective policy found for leave type <UUID>` and empty eligible leave types list (`[]`), showing "No data" in UI Leave Type dropdowns.
- **Root Cause:** Policy drawer (`PolicyForm.jsx`) lacked an `Effective From` date field, sending `null`. `LeaveAllocationServiceImpl` and `LeaveEligibilityServiceImpl` query `effective_from <= date`, which evaluates to false on `NULL` in PostgreSQL.
- **Fix:** Added `DatePicker` for `Effective From` (defaulting to start of year) in `PolicyForm.jsx` and updated `LeaveTypeServiceImpl.java` to default null `effectiveFrom` to `currentYearStart`. Fixed in `dev-sayeed` commit `4b400291`.

#### D-16 (BUG-D5-002): Holiday Calendar Default Conflict
- **Found:** 2026-10-06, Stage 4 (`S4-01`), `POST /api/v1/holiday-calendars`
- **Error:** `HTTP 409 CONFLICT: A default holiday calendar already exists for tenant`
- **Root Cause:** Backend enforces one default calendar per tenant (W-17 spec); duplicate default creation is rejected with 409 (default switching requires `PUT`). UI allowed users to toggle default to true on create.
- **Fix:** In `Calendars.jsx`, forced `isDefault: false` during creation if a default already exists, and disabled the default toggle in the create drawer with helper guidance. Fixed in `dev-sayeed` commit `4b400291`.

#### D-17 (BUG-D5-003): Azure Front Door WAF Blocks Employee Portal Authentication
- **Found:** 2026-10-07, Stage 19 (`S19-01`), Stage 7 (`S7-01` to `S7-10`), `/login` Keycloak authentication
- **Error:** `The request is blocked. 20261007T101708Z-17848f5bf682zw4bhC1PNQutug0000000crg00000000ah9m`
- **Root Cause:** Azure Front Door WAF custom rule `ratelimitperclientip` threshold (100 req/min in `frontdoor.bicep`) triggered during authentication redirects, issuing a 403 block on the client IP.
- **Impact:** Employee unable to log in; blocks all Stage 19 employee self-service steps (`S19-01` to `S19-06`) and Stage 7 time tracking (`S7-01`, `S7-02`, `S7-03`, `S7-05`, `S7-07`, `S7-09`).

#### D-18 (BUG-D5-004): Employee Invitation Acceptance Throws 500 & Compensation Deletes Keycloak User
- **Found:** 2026-10-07, Employee Invitation Acceptance (`POST /api/v1/invitations/accept`)
- **Error:** `HTTP 500 Internal Server Error`, `code: "INTERNAL"`, `message: "An unexpected error occurred while processing the invitation."`, traceId: `9d3cc447-c553-4f97-a545-a9104e91ffa4`.
- **Root Cause:** In `InvitationServiceImpl.acceptEmployeeInvitation()`, `TenantContext.clear()` was called in `finally` before the outer `@Transactional` commit. During transaction commit, Hibernate flushed the dirty `@Audited` `Employee` entity, which triggered `AuditWriter` requiring `TenantContext`. With the context cleared, it threw `IllegalStateException`, wrapped in `TransactionSystemException` by Spring, answering HTTP 500.
- **Fix:** Shipped on main `b6a798f1`: `TenantContext` is now kept bound through transaction commit using Spring `TransactionSynchronization.afterCompletion()`.

#### D-43 (BUG-D5-005): Timesheet & Project Flow — Empty Dropdown in Employee Pickers & Lack of Structured Task Details Section
- **Found:** 2026-10-08, Timesheet & Project Flow Testing (`/hrms/projects`, `/hrms/projects/:id`, `/hrms/timesheets/week/:weekStart`, Step `S7-09`)
- **Symptoms:**
  1. **Project Manager Picker (`ProjectForm.jsx`)**: When opening the Manager field, no options or dropdown list appear. Users see an empty box giving the impression of manual text input; names only appear if typing >= 2 characters. Furthermore, if no managers exist in the organisation, the field should clearly show a "No data" state, and project creation should succeed when no manager is assigned (manager is optional in the domain model).
  2. **Team Member Picker (`TeamTab.jsx`)**: When assigning employees to the project team, clicking the employee selector shows no dropdown choices unless typing >= 2 characters. A browsable list of employees with an active search filter should appear instead.
  3. **Timesheet Task Entry Details (`TimesheetWeek.jsx`)**: In the weekly timesheet grid, task hours entry only offers a small popup note popover (`NoteCell`, 240px wide textarea) without a structured task execution / work details section.
- **Root Cause:**
  1 & 2: `AssignableEmployeeController.java` rejects queries `< MIN_QUERY_LENGTH` (2 chars) answering `[]`, and `EmployeePicker.jsx` aborts searches under 2 chars without prefetching on open/focus.
  3: Timesheet grid UI models daily work description solely via a cell note popover rather than an expandable task details log or structured activity breakdown.
- **Impact:** Degraded user experience during project setup and team assignment, confusion over whether employees exist, and limited ability to record comprehensive work descriptions during timesheet submission.
- **Status:** Open.

#### D-44 (BUG-D5-006): Approvals Inbox Missing History/Decided View, Timesheet Entry View Fails, & Review Inaccessible to Tenant Admin
- **Found:** 2026-10-08, Timesheet Approval Verification (`/approvals`, `/hrms/timesheet-review`, Step `S7-10`)
- **Symptoms:**
  1. **Vanishing Approvals (`/approvals`)**: After approving or rejecting an item (e.g. timesheet), the record immediately disappears from `/approvals`. There is no "History", "Approved", or "Decided" tab, leaving the user with no visible audit or confirmation in the UI of what was decided.
  2. **Approvals Inbox "View Item" Fails (`/hrms/timesheet-review/entries/:entryId`)**: Clicking "View item" on a pending `TIMESHEET` row in `/approvals` navigates to `/hrms/timesheet-review/entries/:entryId`, which errors with `"Could not load the timesheet entry."`
  3. **Timesheet Review Inaccessible to Tenant Admin (`/hrms/timesheet-review`)**: Tenant Administrator (`tenant-admin`) viewing Timesheet Review sees a persistent red error: `"Could not load timesheets."` on the default "My projects" tab, and no timesheets are listed across the tabs for Admin.
- **Root Cause:**
  1. `Inbox.jsx` only queries `approvalService.pending()`. No UI tab exists to query `ApprovalHistoryResponse` (`GET /api/v1/approvals/{instanceId}/history`) or filtered past decisions.
  2. `TimesheetProjectEntryServiceImpl.java:45-63` enforces `currentEmployee()` and checks `step.getAssigneeEmployeeId()`. When an admin or fallback approver clicks "View item", `caller()` throws HTTP 403 `PermissionDeniedException` (if user has no employee record) or HTTP 404 `ResourceNotFoundException` (if not the exact assignee).
  3. `TimesheetReviewServiceImpl.managed()` calls `caller()` which forces `employeeService.currentEmployee()`. A Tenant Admin account has no employee record, answering `403 FORBIDDEN: Not permitted: requires action 'hrms.timesheet.approve'`. Furthermore, the "All" tab requires `hrms.timesheet.read`, which is only seeded on the `hr` role, not `tenant-admin`.
- **Impact:** Approvers cannot inspect the submitted timesheet entry from the inbox; approvers cannot track previously approved items; Tenant Admins cannot audit tenant timesheets from `/hrms/timesheet-review`.
- **Status:** Open.

#### D-45 (BUG-D5-007): Submitted Timesheet Lacks Edit / Recall-to-Draft Flow (Locked Out by One-Timesheet-Per-Week Constraint)
- **Found:** 2026-10-09, manual QA Timesheet flow (`/hrms/timesheets/week/:weekStart`, Step `S7-09`)
- **Symptoms:**
  1. Once an employee clicks **Submit**, the entire week grid enters a locked read-only state. All hours inputs are disabled, and action buttons (Save draft, Submit, Delete draft) are hidden.
  2. There is no **"Recall / Withdraw to Draft"** option. If an employee realizes they entered incorrect task hours or submitted prematurely, they cannot edit the submitted week or submit a corrected one because PostgreSQL enforces a strict unique constraint on `(tenant_id, employee_id, week_start_date)`. The employee is stranded unless a manager explicitly rejects the timesheet.
- **Root Cause:** The timesheet state machine transitions directly from `DRAFT` to `SUBMITTED` without an intermediate `RECALLED` / `WITHDRAWN` transition or edit permission for pending submissions prior to approval.
- **Impact:** Employees who make errors during submission have no self-service way to correct their weekly hours, leading to stranded records or forcing manual communication with approvers to trigger a rejection.
- **Status:** Open.

#### D-46 (BUG-D5-008): Attendance Screen Hides Past Session Intervals & Tenant Admin Blocked from Clock In/Out
- **Found:** 2026-10-09, manual QA Attendance flow (`/hrms/attendance`, Step `S7-02`, `S7-03`)
- **Symptoms:**
  1. **Past Date Sessions Invisibility (`ClockCard.jsx`, `MyAttendance.jsx`)**: The Clock Card renders a timeline with individual clock-in and clock-out timestamps for today only. For past dates, the "My days" table only lists aggregated totals and session counts (`sessions: N`); there is no way for the employee to view or expand individual session start/end intervals from earlier dates.
  2. **Tenant Admin Clocking Inaccessibility**: Tenant Administrator accounts have no linked employee record (`core.employee`), completely preventing clock in and clock out operations or attendance self-service testing under the admin persona.
- **Root Cause:**
  1. `ClockCard.jsx` loads today's sessions via `attendanceService.today()`. `MyAttendance.jsx` groups monthly data by day (`byDay`) without providing row expansion for historical session timelines.
  2. `AttendanceCaptureController.java` requires an active employee context linked to the caller's user account.
- **Impact:** Employees cannot audit their exact historical punch pairs; Tenant Admins cannot use or verify attendance capture directly.
- **Status:** Open.

#### D-47 (BUG-D5-009): Regularization & Overtime Request Screens Require Manual Date Range Selection to Fetch Data
- **Found:** 2026-10-09, manual QA Regularizations & Overtime (`/hrms/regularizations`, `/hrms/overtime-requests`, Steps `S7-05`, `S7-07`)
- **Symptoms:**
  1. Navigating to `/hrms/regularizations` or `/hrms/overtime-requests` initially displays an empty table. No API call is executed on mount because the component `range` state is initialized to `null` (`MyRegularizations.jsx:14`, `MyOvertime.jsx:14`).
  2. Users are forced to manually pick a date range in the picker before any requests appear. The date range should act as a filtering mechanism, not an entry gate to display data; screens should automatically fetch and display current records (e.g. current month or default 30-90 days) on initial load.
- **Root Cause:** `useEffect` only invokes `load(range)` if `range` is truthy (`if (range) load(range)`), with `range` initialized to `null` instead of a default preset range like `[dayjs().startOf('month'), dayjs()]`.
- **Impact:** Users assume no regularization or overtime requests exist upon opening the page, causing confusion and requiring redundant user interactions.
- **Status:** Open.

#### D-48 (BUG-D5-010): Rejected Overtime Requests Vanish from Approvals & UI Views
- **Found:** 2026-10-09, manual QA Overtime Approvals (`/approvals`, `/hrms/overtime-requests`, Step `S7-08`)
- **Symptoms:**
  1. Once an approver rejects an overtime request, the record completely disappears from the UI rather than remaining visible with status `REJECTED` in an audit or decided history view.
- **Root Cause:** `/approvals` inbox only queries pending approval steps (`approvalService.pending()`) and lacks a "Decided / History" tab to view rejected/approved requests. Additionally, employee view filters may not surface terminal decisions without explicit date matching.
- **Impact:** Approvers and employees lose visibility and confirmation of rejected overtime applications, leaving no visible audit trail in the UI.
- **Status:** Open.

#### D-49 (BUG-D5-011): Leave Year Input in `LeaveImport.jsx` Uses `InputNumber`, Blocking Required `YYYY-YY` Format for April Tenants
- **Found:** 2026-10-09, manual QA Leave Import flow (`/leave/import`, Steps `S20-03`, `S20-04`)
- **Symptoms:**
  1. For tenants whose financial year starts in month 4 (April), the backend requires leave year formatted as `YYYY-YY` (e.g. `2026-27`).
  2. `LeaveImport.jsx` uses Ant Design `<InputNumber value={leaveYear} onChange={setLeaveYear} />` defaulting to numeric `2026`. Any attempt to enter `2026-27` is rejected by `InputNumber` because it only accepts numeric characters.
  3. When submitted as numeric `2026`, backend throws `HTTP 400 Bad Request: Invalid leave year format '2026'. Tenant starting in month 4 requires YYYY-YY (e.g. 2026-27)`. This completely blocks users from executing leave imports through the UI without manual state manipulation.
- **Root Cause:** `LeaveImport.jsx` hardcodes `InputNumber` instead of text `<Input>` or `<Select>` dynamically populated from tenant leave year configuration.
- **Impact:** High / Blocker for April-cycle tenants trying to import leave balances.
- **Status:** Open.

#### D-50 (BUG-D5-012): Missing Sample CSV Template Download Link on `/leave/import`
- **Found:** 2026-10-09, manual QA Leave Import flow (`/leave/import`, Step `S20-02`)
- **Symptoms:**
  1. Test step S20-02 requires downloading a sample CSV template with expected headers (`employee_code, leave_type_code, year, balance_days`).
  2. The UI page `/leave/import` does not have any "Download Sample Template" button or download link.
- **Root Cause:** `LeaveImport.jsx` only renders the upload dropzone and leave year input; no sample template generator or download link is implemented.
- **Impact:** Users must guess or reverse-engineer the expected CSV headers and format.
- **Status:** Open.

#### D-51 (BUG-D5-013): Error Report Document UUID in Warning Alert is Plain Text and Unclickable
- **Found:** 2026-10-09, manual QA Leave Import flow (`/leave/import`, Step `S20-03`)
- **Symptoms:**
  1. When an import completes with validation errors, the Alert message displays: `"Some rows had validation errors. Download error report document: <document-uuid>"`.
  2. The document UUID is rendered as plain text. Users cannot click to download or view the generated error CSV report.
- **Root Cause:** `LeaveImport.jsx` renders `errorReportDocumentId` directly inside a string message instead of wrapping it in a download link/button backed by document download API.
- **Impact:** HR/Admin cannot easily access the generated error report to identify which rows failed validation.
- **Status:** Open.

#### D-52 (BUG-D5-014): Employee Role Completely Missing Leave Menu Access Due to Parent `core.leave.read` Guard
- **Found:** 2026-10-09, manual QA Employee Persona Verification (`/leave/requests`, `/me`)
- **Symptoms:**
  1. Logged in as Employee (`gafeg90661@calirona.com`), the "Leave" navigation item is completely absent from the sidebar.
  2. Direct URL navigation to `/leave/requests` throws 404.
  3. Employees cannot apply for leave or view their balances in the UI.
- **Root Cause:** `NavigationCatalogue.java:158` guards the parent `"core.leave"` group with action `"core.leave.read"`. The seeded `employee` role only possesses `"core.leave.apply"` and `"core.leave.read_own"`, not `"core.leave.read"`. Because the parent menu item is hidden, its children (including leave application and my requests) are inaccessible.
- **Impact:** Critical / Blocker: Employees cannot perform self-service leave operations.
- **Status:** Open.

#### D-53 (BUG-D5-015): Employee Portal (`/me`, `MyLeave.jsx`) Leave Balance Cards Visual Misalignment
- **Found:** 2026-10-12, manual QA Employee Leave view (`/me`, `MyLeave.jsx`, Step `S6-02`)
- **Symptoms:**
  1. On the employee self-service portal (`/me`, My Leave tab), the "Leave Balances" section displays available and consumed days for employee leave types (e.g. EL, SL), but the cards are visually misaligned and lack a structured layout.
  2. The cards do not maintain consistent height, meter progress bars, or uniform vertical alignment when type names or day counts vary.
- **Root Cause & Fix:**
  1. `MyLeave.jsx` previously rendered unconstrained inner cards without matched height, visual progress meters, or consistent vertical rhythm.
  2. Redesigned leave balance cards using full-height flex column layout (`height: '100%'`, `justifyContent: 'space-between'`) with subtle container gradient and soft border styling.
  3. Integrated Ant Design `<Progress>` progress meters reflecting consumption percentage with color-coded status thresholds (success / normal / exception).
  4. Displayed clean `<Statistic>` readouts for available days and added a structured footer tag displaying "Used: X days" alongside the total allocated entitlement tag.
  5. Corrected `<Space direction="vertical">` and row key definitions.
- **Impact:** Greatly improved visual clarity and responsiveness for employees tracking leave balances.
- **Status:** **Resolved locally in `dev-sayeed`** (uncommitted).

#### D-54 (BUG-D5-016): Admin Detail View (`/leave/requests/:id`) Incorrectly Exposes Applicant-Only "Withdraw" Action
- **Found:** 2026-10-12, manual QA Leave Request Detail (`/leave/requests/:id`, Step `S6-04`)
- **Symptoms:**
  1. When Tenant Admin or HR views the detail page of a pending employee leave request (`/leave/requests/:id`), the screen displays a prominent red **"Withdraw"** button in the header actions.
  2. In HRMS business workflows, "Withdraw" is strictly an employee self-service action allowing applicants to pull back their own pending request before an approval decision is made. Administrators and approvers should only see `Approve`, `Reject`, or `Cancel Leave` (for approved leaves), not a self-service "Withdraw" button.
- **Root Cause & Fix:**
  1. `LeaveRequestDetail.jsx:184` previously evaluated `canWithdraw = !isReadOnly && canManage && (request.status === 'PENDING' || request.status === 'SUBMITTED')`. Because administrators hold `canManage` (`core.leave.manage`), the "Withdraw" action was erroneously exposed to administrators on `/leave/requests/:id`.
  2. Identified caller profile via `portalService.getProfile()` to determine `currentEmployeeId`.
  3. Gated `isApplicant = Boolean(currentEmployeeId && request?.employeeId && currentEmployeeId === request.employeeId)` and `canWithdraw = isApplicant && (request.status === 'PENDING' || request.status === 'SUBMITTED')`.
  4. Administrators viewing employee requests on `/leave/requests/:id` no longer see the self-service "Withdraw" button; they only see "Cancel Leave" if the leave has been approved (`canManage && request.status === 'APPROVED'`).
  5. Added unit test coverage in `LeaveRequestDetail.test.jsx`.
- **Impact:** Eliminates confusion over administrative roles and ensures applicant-only self-service action isolation.
- **Status:** **Resolved locally in `dev-sayeed`** (uncommitted).

#### D-55 (BUG-D5-017): Leave Policy Drawer (`PolicyForm.jsx`) Throws `VALIDATION_FAILED: effectiveFrom cannot be null` on Save and Lacks Effective Date Input
- **Found:** 2026-10-12, manual QA Leave Type Policy Configuration (`/leave/types`, Step `S4-10`, `S6-01`)
- **Symptoms:**
  1. When an administrator adds a leave type or opens the policy drawer (`PolicyForm.jsx`) from `/leave/types` to configure annual days and rules, clicking **Save** throws an error: `HTTP 400 / VALIDATION_FAILED: effectiveFrom date cannot be null`.
  2. There is no visible input field or date picker on the drawer to specify an effective date.
- **Root Cause & Fix:**
  1. Backend `LeavePolicyRequest` previously enforced non-null `effectiveFrom` in `LeavePolicyValidator`, blocking saves when the date was omitted.
  2. `LeavePolicyValidator` now treats `effectiveFrom` as optional, allowing `LeaveTypeServiceImpl.configurePolicy` to default missing dates to the tenant's current leave year start date (and ensuring it never falls behind versions already in force).
  3. `PolicyForm.jsx` includes a `<DatePicker name="effectiveFrom">` field with clear guidance ("Leave empty to start from the first day of the current leave year").
  4. Verified with backend tests (`LeavePolicyEffectiveFromTest`, `LeavePolicyValidationTest`) and frontend unit tests (`PolicyForm.test.jsx`).
- **Impact:** Administrators can seamlessly create and edit leave policies with either explicit effective dates or automatic defaulting to the active leave year.
- **Status:** **Resolved locally in `dev-sayeed`** (uncommitted).

#### D-56 (BUG-D5-018): Employee Portal (`/me`) Dashboard & Profile Summary Card Visual Misalignment
- **Found:** 2026-10-12, manual QA Employee Portal (`/me`, Step `S19-02`)
- **Symptoms:**
  1. On the employee portal (`/me`), the employee profile summary card displays employee details (Name, Job Title, Department, Location), but the content is visually misaligned.
  2. The text labels and values lack clean vertical rhythm, padding, and structured visual alignment.
- **Root Cause & Fix:**
  1. Dashboard header and profile summary card in `MyProfile.jsx` suffered from unaligned metadata items and basic layout structure.
  2. Implemented a prominent hero summary card (`panel-profile-summary`) with flex alignment, user avatar with status badge, role/title tags, and structured metadata chips (Employee Code, Department, Work Location, Joining Date) with dedicated Ant Design icons.
- **Impact:** Clean, modern, and visually balanced hero summary card on employee self-service home.
- **Status:** **Resolved locally in `dev-sayeed`** (uncommitted).

#### D-57 (BUG-D5-019): Missing "Holiday Calendar" Tab on Employee Portal (`/me`)
- **Found:** 2026-10-12, manual QA Employee Portal (`/me`, Step `S19-04`)
- **Symptoms:**
  1. Test step S19-04 requires employees to inspect upcoming tenant/work location holidays on the `/me` portal.
  2. There is no "Holiday Calendar" tab, card, or widget available anywhere on `/me`.
- **Root Cause & Fix:**
  1. `PortalLayout.jsx` previously only rendered tabs dynamically fetched from `/me/panels`, omitting the tenant holiday calendar which resides under Organisation (`/holidays`).
  2. Added a dedicated "Holiday Calendar" tab with `<CalendarOutlined />` to the self-service tabs in `PortalLayout.jsx`.
  3. Configured tab navigation and URL redirect: selecting the tab or hitting `/me/holidays` redirects smoothly to `/holidays` (Organisation > Holidays).
  4. Excluded `holidays` from the 403 forbidden panel check in `PortalLayout.jsx` and added unit test coverage in `PortalLayout.test.jsx`.
- **Impact:** Employees can effortlessly navigate to upcoming holiday calendar listings directly from their self-service portal.
- **Status:** **Resolved locally in `dev-sayeed`** (uncommitted).

#### D-58 (BUG-D5-020): My Profile Screen (`/me/profile`) Form Sections Visual Misalignment & Unrestricted Employee Edits
- **Found:** 2026-10-12, manual QA Employee Profile (`/me/profile`, Step `S19-06`)
- **Symptoms:**
  1. On the My Profile screen (`/me/profile`), updating emergency contact details succeeded, but the form sections (Personal, Job, Contact) suffered from visual misalignment.
  2. Form input fields, label widths, and grid columns lacked consistent alignment and responsive structure.
  3. Employee portal presented editable form inputs and Save buttons triggering PUT/POST mutation APIs (`/v1/employees/{id}/personal`, `/v1/employees/{id}/contact`).
- **Requirement & Scope:**
  - Remove options from employee side to fill details and call POST/PUT API. Employees can only view their details.
  - On the admin side (`/employees/:id`), retain full capability to edit details and invoke POST/PUT APIs.
  - In self-service (`/me/profile`), add 2 tabs at the top ("Personal Details" and "Contact Details") to showcase employee details in a structured, read-only UI.
- **Root Cause & Fix:**
  1. Removed `actions?.EditOwnSection` editable form and Save buttons from employee portal (`MyProfile.jsx`), preventing employee mutation calls to `PUT /v1/employees/{id}/personal` and `PUT /v1/employees/{id}/contact`.
  2. Maintained full editable forms and save capability on Admin screens (`/employees/:id`, `employeeTabs.jsx`, `SectionTab.jsx`).
  3. Added two dedicated tabs at the top ("Personal Details" and "Contact Details") in `MyProfile.jsx` (`panel-profile-tabs`), showcasing employee details in structured, read-only `Descriptions` tables with consistent 180px label widths, soft-tinted backgrounds, and 2-column balanced grids.
  4. Non-destructively loaded extended section details (`dateOfBirth`, `maritalStatus`, `emergencyContactName`, addresses) via `Promise.allSettled([employeeService.section(id, 'personal'), employeeService.section(id, 'contact')])` without breaking mock expectations.
- **Status:** **Resolved locally in `dev-sayeed`** (uncommitted).

#### BUG-D5-021 (E-01): Missing Reporting Manager Field on Welcome Page & Employee Portal (/me)
- **Found:** 2026-10-14, Certification Stage E-01 (`sahmad@infinevocloud.com`)
- **Error / Issue:** No field exists to display the employee's Reporting Manager on the Welcome page (`/welcome`) or on the employee profile page (`/me`).
- **Expected:** Name, designation, department, and reporting manager displayed as readable words (no UUIDs).
- **Fix Applied:**
  1. Added `getReportingManager(employeeId)` and `getJobSummary()` in `portalService.js` to query `/api/v1/employees/{id}/reporting-line` (gated by `core.org.read` which `employee` role holds) and extract primary manager name as readable words.
  2. In `MyProfile.jsx`: Added `Reporting manager` to the "Job" `Descriptions` block beside `Department` and `Designation`, gracefully displaying `—` if unassigned or refused without throwing.
  3. In `Welcome.jsx`: Added job summary block (`Descriptions`) in `EmployeeCard` rendering `Department`, `Designation`, and `Reporting manager` words.
  4. Expanded unit tests in `MyProfile.test.jsx` and `Welcome.test.jsx`.
- **Status:** **Verified & Passed on live server (2026-10-15)**: Reporting manager displayed on Welcome page and /me profile.

#### BUG-D5-023 (E-03a): Leave Application Throws Attachment Validation Error Despite Attaching Document
- **Found:** 2026-10-14, Certification Stage E-03a (`/me/leave/apply`, applying Sick Leave SL on 2026-10-20)
- **Error:** `Validation Error: Leave policy requires supporting documents to be attached (Trace ID: 9dc26ed2-63de-4777-a198-bef4f71a4a2b, 3a660435-6b8c-4157-9ca7-9a80d8e4c667)`
- **Symptom:** Even after attaching a supporting document via the upload component on the form, submitting the request fails with the exact same validation error.
- **Root Cause:**
  1. **Backend Document Upload Authorization**: `DocumentController.java:73` guarded `POST /api/v1/documents` solely with `@RequiresAction("core.document.upload")`. The `employee` role holds `core.document.read_own` and `core.leave.apply`, but **never** holds `core.document.upload` (which is restricted to `platform-admin`, `tenant-admin`, and `hr` per `V037__document.sql`). When an employee uploaded a supporting document for leave, the backend rejected the request with `403 Forbidden`.
  2. **Frontend Upload Error Masking**: In `LeaveRequestFields.jsx`, `handleCustomUpload` caught the 403 error and forwarded it only to `onError(err)` without an alert or blocking notification, causing the document UUID to never be populated into the form. Submitting the form therefore sent `documentIds: []`, triggering `Leave policy requires supporting documents to be attached` from `LeaveRequestServiceImpl.java:565`.
- **Fix Applied:**
  1. Updated `DocumentController.java`:
     - Added `anyOf` to `@RequiresAction` permitting self-service actions: `core.leave.apply`, `payroll.reimbursement_claim.submit_own`, `payroll.tax_declaration.submit`, and `payroll.proof.submit_own`.
     - Injected `PermissionService` and `DocumentOwnerResolver`.
     - Added `validateUploadPermissionAndEmployee`: If the caller lacks `core.document.upload`, verifies that the document kind matches their permitted action (e.g. `LEAVE_ATTACHMENT` requires `core.leave.apply`) and pins `employeeId` strictly to the authenticated caller's employee record. Refuses other kinds (`EMPLOYEE_DOCUMENT`, etc.) or colleague spoofing with `403 Forbidden`.
  2. Added integration tests in `DocumentGuardIT.java`:
     - `employeeCanUploadLeaveAttachment`: Verifies that an employee with `core.leave.apply` uploads `LEAVE_ATTACHMENT` and the document is stored with `employee_id` pinned to the caller.
     - `employeeCannotUploadForColleague`: Verifies that an employee attempting to upload `LEAVE_ATTACHMENT` specifying a colleague's ID is refused with `403 Forbidden`.
     - `employeeCannotUpload`: Still validates that `EMPLOYEE_DOCUMENT` upload by employees is refused with `403 Forbidden`.
  3. Updated `LeaveRequestFields.jsx`:
     - Added `message.error(...)` notification on upload failures so API errors are immediately visible.
     - Added form validation rule on `documentIds` to block submission while an upload is in progress (`status: 'uploading'`) or has failed (`status: 'error'`).
     - Maintained `normalizeDocumentIds` and form value synchronization.
  4. Ran full test suites: `DocumentGuardIT` (12/12 passed), `DocumentUploadIT` (5/5 passed), `EndpointGuardCoverageTest` (1/1 passed), and `ApplyLeave.test.jsx` (8/8 passed).
- **Status:** **Verified & Passed on live server (2026-10-15)**: Sick leave applied successfully with supporting document attached.

#### BUG-D5-024 (E-09): Reimbursement Component Dropdown Empty in New Claim Drawer
- **Found:** 2026-10-14, Certification Stage E-09 (`/me` Claims tab, New Claim drawer)
- **Error / Issue:** In the "New Claim" drawer, the "Component" select dropdown is completely empty with no data to select. Because `reimbursement_id` is a mandatory field in the schema, the employee cannot submit any expense reimbursement claim.
- **Expected:** Available reimbursement components (e.g. Internet, Travel, Medical) should be listed and selectable in the dropdown.
- **Root Cause:**
  1. The India country template `salary_components` (`V162__country_template_in.sql` and `SalaryComponentTemplateContributor.java`) only seeded earnings and deductions, omitting standard reimbursement components (`INTERNET`, `TRAVEL`, `MEDICAL`, `FOOD`, `FUEL`).
  2. Setup checklist step `SALARY_COMPONENTS` only checked earnings existence (`existsByTenantIdAndDeletedFalse`), so checklist marked components complete without prompting the admin to configure reimbursements.
  3. Consequently, deployed tenants (including Infinevo Test Co) had 0 rows in `payroll.reimbursement`, causing `GET /api/v1/me/reimbursement-claims/components` to return empty list `[]`.
  4. In `ClaimForm.jsx`, if components are empty, the Select showed "No data" with no warning or explanation, while the form allowed clicking submit which resulted in a validation error.
- **Fix Applied:**
  1. Created Flyway migration `V175__default_reimbursement_components.sql` to:
     - Backfill standard reimbursement components (`INTERNET`, `TRAVEL`, `MEDICAL`, `FOOD`, `FUEL`) into `payroll.reimbursement` for all non-platform tenants in `core.tenant`.
     - Create trigger `tenant_seed_reimbursements` on `core.tenant` AFTER INSERT to automatically seed default reimbursement components for newly created tenants.
     - Update `reference.country_template`'s `salary_components` section for `IN` to include `"reimbursements": [...]`.
  2. Updated `SalaryComponentTemplateContributor.java` to inject `ReimbursementRepository` and seed reimbursements from the template payload.
  3. Updated `ClaimForm.jsx` to display an informative warning Alert when no components are available, disable the Select with a helpful placeholder, and disable the Submit button when `components.length === 0`.
  4. Added unit test in `ClaimForm.test.jsx`.
- **Status:** **Verified & Passed on live server (2026-10-15)**: Reimbursement components dropdown populated via V175 migration, claim submitted successfully with receipt attached.

#### BUG-D5-025 (E-08): Missing FBP (Flexible Benefit Plan) Tab on Employee Portal (/me)
- **Found:** 2026-10-14, Certification Stage E-08 (`/me`)
- **Error / Issue:** No FBP or Flexible Benefits tab/panel exists on the Employee Self-Service portal (`/me`). In the implementation, `FbpDeclarationTab` was only mapped as an employee tab on `/employees/:id` (accessible to HR/Payroll administrators), leaving employees with no UI screen to view, allocate, or declare their Meal, Fuel, and flexible allowances.
- **Expected:** An "FBP" tab should be available on the `/me` portal allowing employees to declare flexible benefits within company plan caps per step E-08.
- **Root Cause & Fix Applied:**
  1. **Backend Panel Provider**: Created `FbpPanelProvider.java` in `com.infinevo.payroll.portal` implementing `PortalPanelProvider` with `code="fbp"`, `module=PlatformModule.PAYROLL`, `displayOrder=8`, `endpoint="/api/v1/me/fbp-declaration"`, and `requiredAction="payroll.fbp.read_own"`.
  2. **Panel Order Verification**: Updated `PortalPanelOrderTest.java` in `app` module verifying display order `[profile, leave, documents, payslips, timesheet, taxDeclaration, claims, fbp]`.
  3. **Frontend API Client**: Added `readOwn()` (`GET /api/v1/me/fbp-declaration`) and `declareOwn(payload)` (`PUT /api/v1/me/fbp-declaration`) in `fbpService.js` and expanded unit tests in `fbpService.test.js`.
  4. **Self-Service Support in FbpDeclarationTab**: Updated `FbpDeclarationTab.jsx` to recognize self-service portal mode when `employeeId` is omitted, checking `payroll.fbp.read_own` and `payroll.fbp.declare_own` permissions and delegating to `FbpDeclarationForm` with `isOwn={true}`.
  5. **Window Closed & Self-Service Form Handling**: Updated `FbpDeclarationForm.jsx` to fetch via `readOwn()` and save via `declareOwn()`, display a warning Alert and disable editing/saving when `windowOpen === false`, hide admin date picker in self-service mode, and show monthly equivalent amounts (`≈ ₹X / mo`) beside annual inputs.
  6. **Shell & Portal Registration**: Registered `{ code: 'fbp', component: FbpDeclarationTab }` under `portalPanels` in `code/frontend/src/payroll/index.js`, and added `fbp: <GiftOutlined />` to `PANEL_ICONS` in `PortalLayout.jsx`.
  7. **Automated Test Coverage**: Added self-service test scenarios in `FbpDeclarationTab.test.jsx` covering read_own, declare_own, and closed window states; verified `PortalLayout.test.jsx` and `fbpService.test.js` all pass (27/27 green).
- **Status:** **Verified & Passed on live server (2026-10-15)**: FBP tab available on /me portal; Meal (₹26,400) and Fuel (₹12,000) declarations submitted and verified.

---

## 1. Stream A — Foundation

| # | Ticket | What it is | Spec | Code | Feature |
|---|---|---|---|---|---|
| #1 | `W-01` Repository & module skeleton | Seven Maven modules with the `hrms` ↔ `payroll` boundary enforced by the build, package conventions, shared module holding tenant context, error envelope and money types | approved | on main | **done** — checked in by sanjib |
| #5 | `W-04` Test foundation | Unit test setup, integration tests against a real database, test data builders, coverage reporting | approved | on main | **done** — checked in by sayeed |

`W-02` local stack and `W-03` build pipeline are also stream A — they live in
[INFRA-TRACKER.md](INFRA-TRACKER.md) because they are containers and CI.

---

## 2. Stream B — Data foundation

| # | Ticket | What it is | Spec | Code | Feature |
|---|---|---|---|---|---|
| #6 | `W-05` Postgres & schemas | Four schemas owned by `migration_user`, four roles, none superuser and none `BYPASSRLS`. One set of SQL scripts that local Docker, Testcontainers and Azure all run | approved | on main | **done** — checked in by sayeed |
| #7 | `W-06` Flyway | Migration runner, script conventions, per-schema ordering, pipeline validation | approved | on main | **done** — runs four locations — checked in by sayeed |
| #8 | `W-07` Tenant model | `tenant_id` standard, row-level security policies, role grants, and a build check that fails on an unscoped table | approved | on main | **done** — checked in by gautam |
| #9 | `W-08` Tenant binding filter | Token claim extraction, membership verification, database session binding, failure handling | approved | on main | **done** — checked in by sanjib |
| #10 | `W-09` Reference schema & seed | Generic lookups, 15 tax master tables, seed scripts, annual update process | approved | on main | **done** — backend suite now 100 passing, none skipped — checked in by sanjib |

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
| `W-13.4` Employee login link | `user_account_id` on `core.employee`; employees may edit their own personal and contact sections. Migration `V026`. At merge: soft delete clears the link (the `V026` index covers deleted rows); Flyway `out-of-order` turned on. Deferred to `W-13.3`: personal-section self-service IT, a real `currentEmployee` unit test | §5 row 14 | **on main `e699699`** · done — built by sayeed, merge-review fixes by claude |
| `W-52.1` Worker fix | The queue consumer loop, producer bean in `app`, retry-then-fail, running-job idempotency, `@RequiresAction` on `/jobs/{id}`. At merge: `JobService` gained `claimForRun` and `releaseForRetry` (spec §4 updated); `QueueRoundTripIT` proves the loop against Azurite | §5 row 21 | **on main `5c07c45`** · done — built by sayeed, merge-review fixes by claude |
| `W-52.2` Queue by managed identity | `StorageQueueConfig` builds the queue client from an endpoint plus the container's managed identity (Azure) as well as a connection string (Azurite); one non-blank "queue configured" condition for client, producer and consumer loop; `azure-identity` on `shared`; Bicep passes `AZURE_STORAGE_QUEUE_ENDPOINT` to app and worker. No migration. Fixes `D-11`. Lands on `dev` when the founder runs the Bicep | `W-52` §2 item 2 | **Done 2026-10-07 on `cc8bd8ce` — Built by karma.** Outstanding in the merge commit: endpoint not yet on the containers, `createIfNotExists()` 403 on Azure caught as WARN, stale comment and line refs |
| `W-53.1` Cache cleanup | Delete the unused `core.cache` permission classes and the `core.queue` package | §5 row 22 | **on main `5c07c45`** · done — built by sayeed |
| `W-04.1` Test connection budget | `mvn verify` **fails on main** (2026-09-25, reproducible serially): `shared`'s `DatabasePrivilegesIT` dies with `53300 too_many_connections`. 16 `@SpringBootTest` classes each hold a Hikari pool of 10 against a 100-slot Testcontainers Postgres. Fix: one shared test context config with a small pool, or raise the container's `max_connections` | suite green | **on main `3350cf2`** · done — built by claude |
| `W-09.1` Age category seed | Seed `SENIOR` and `SUPER_SENIOR` slab rows for three financial years (defect #146). Migration `V027`. Deferred: the `V099` annual fixture gains the age pair when `W-33` first reads them | Stream B defect | **on main `c79755c`** · done — built by sayeed |

`W-52.1` and `W-53.1` are backend fixes to tickets tracked in [INFRA-TRACKER.md](INFRA-TRACKER.md).

| # | Ticket | What it is | Ready? |
|---|---|---|---|
| #11 | `W-10` Identity | Realm configuration, login flow, token validation, user profile sync, password reset delegated to Keycloak | **on main `ac1e531`** · code done — **spec §8 login never run by hand** — checked in by sanjib |
| — | `W-10.1` Production Keycloak realm | `infra/keycloak/infinevo-realm.json` with no users and no secrets, imported by the Keycloak image on first start; mail through Brevo SMTP with the key from Key Vault; password reset on; the web client's address and the web container's realm and client id set by the pipeline; a CI check on the realm file. **Spec written 2026-09-30** — `W-10-1-production-realm.md`. Size M, infra. No backend, no migration | **Done 2026-10-02 on `889b09b` — Built by karma**, merge-review fixes by claude: start-up guard on `KC_WEB_ORIGIN`, CI 8c back to exactly one import file, SMTP fields checked as placeholders, PKCE S256 on the server. Founder steps in the merge commit: `brevo-smtp-key`, `brevoSmtpLogin`, and reset and SMTP on the hand-made dev realm, which the import skips. Outstanding: password grant still on, brute-force protection off |
| #14 | `W-13.1` Employee record | The neutral root, `core.employee` (`V010`), isolated by row-level security rather than a remembered `WHERE` | **on main `7d0bab6`** · done — checked in by sanjib |
| #14 | `W-13.2` Employee detail | Five one-to-one sections (`V015`–`V019`) — personal, contact, identification, employment, bank. **Turned the audit trail on**, and fixed the `@Embedded` redaction gap | **on main `5228385`** · done — checked in by sanjib |
| #14 | `W-13.3` Employee search & listing | Search and listing over the employee record | **on main `ccc8e48`** · done — built by sayeed, navigation item and merge review by claude. `is_deleted` filtered; the N+1 closed with one join. Carried in: personal-section self-service IT. **Still deferred:** the `currentEmployee` unit test from `W-13.4` |
| #26 | `W-22.1` Audit trail | Change capture, `core.audit_log` (`V008`), `GET /api/v1/audit` | **on main `6fb4012`** · done — capturing since `W-13.2` — checked in by sanjib |
| #— | `W-22.2` Audit retention | Retention sweep and purge as `retention_user`, dry-run on by default (`V094`) | **Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: dry-run default untested; nothing reads `core.retention_run` after a sweep; batch loop never tested past one batch |
| #12 | `W-11.1` Role & action catalogue | 63-action catalogue in `reference.action`, tenant-scoped roles, seven system roles seeded per tenant, role and grant API | **on main `172eaaa`** · spec approved · code done — checked in by sanjib |
| #12 | `W-11.2` Permission check & cache | `@RequiresAction` on every endpoint, 403 when not held, shared Redis cache that every replica reloads on a role change | **on main `23d1126`** · spec approved · code done — checked in by sanjib |
| #13 | `W-12` Tenant, subscription & entitlement | Tenant management, organisation creation, module selection, subscription status — the payment seam — and entitlement enforcement on both API and navigation | **`W-12.1` and `W-12.2` on main `b7d03ec`, `W-12.3` on main `a4f31ae`** · **done — built by krushna** · `W-24.1` Ready; `W-20.2`, `W-24.2` unblocked on `W-12.1`; `W-45` unblocked on `W-12` |
| #15 | `W-14.1` Org masters | Department, designation and work location (`V011`–`V013`), plus the three nullable columns on `core.employee` (`V014`). Free-text conversion deliberately left to `W-67` | **on main `235aab2`** · code done — §8 verification not independently re-run — checked in by sanjib |
| #15 | `W-14.2` Reporting line | The new reporting line and the org chart read model (`V028`) | **on main `ccc8e48`** · done — built by sayeed. Outstanding: no warning on deactivating a manager (spec § 9); org chart is an in-memory walk, `depth` optional; back-dated primary `PUT` date behaviour accepted by the founder 2026-09-27 |
| #16 | `W-15` Approval engine | Approval definitions, instance lifecycle, step routing along the reporting line, delegation and escalation, history (`V089`–`V092`) | **on main `a34c14f`** · done — built by karma. Outstanding, accepted 2026-09-28: no-handler flow silently claimed; no row lock on completion; ROLE routes to the first holder; holiday clock is a stub until `W-17`; escalation and reassign ignore delegations; five FK columns unindexed. Deferred to `W-16.3`: same-day `PUT` versioning, a `CANCELLED` decision value |
| #17–20 | `W-16.1`–`.4` Leave engine | Types and policy · allocation and balance · request, approval and documents · consumption, loss-of-pay derivation and bulk import. **The other riskiest ticket — a merge** | **`W-16.1`, `W-16.2`, `W-16.3`, `W-16.4a`, `W-16.4b` Done 2026-10-02 on `a53e311` — Built by karma**, merge-review fixes by claude (`V126`–`V134`). Fixed at merge: a mid-year switch between accrual and fixed counted leave twice; an import could stay `PENDING` after rows had committed. Founder decisions 2026-10-01, specs still to amend: fixed to accrual mid-year keeps the grant and accrues from next year; loss-of-pay month split stays on calendar days; `is_paid` stays a label; next year's allocations stay manual. Outstanding: two half-days on one date refused as an overlap; a leaver's last month accrues in full; carried-forward days do not expire after a mid-year reset; one bad allocation rolls back a tenant's reset; `/leave-types/eligible` answers for any employee; the `CANCELLED` decision value and same-day `PUT` versioning from `W-15` still not done. Run the Bicep before the next deploy so `btree_gist` is allowed. Full list in the merge commit |
| #21 | `W-17` Holiday calendar | Calendar per work location, holiday management, bulk import | **Done 2026-09-30 on `288f9fa` — Built by krushna.** Outstanding in the merge commit. Migration `V036`. Unblocks `W-46.3b` (waits on `W-45` only) |
| #22 | `W-18` Loss-of-pay & working-day policy | Policy model, working-day basis, derivation rules, the policy stamped on every pay figure | **`W-18.1` Done 2026-09-30 on `288f9fa` — Built by krushna** (migrations `V116`, `V119`). **`W-18.2` Done 2026-10-01 on `b580d8b` — Built by krushna** (migration `V125`). Five stamp columns on `payroll.employee_payrun`, the explain endpoint, and joiners and leavers counted in the policy's own days. Decided at merge, specs amended the same day: on `FIXED_30` and `ORG_DAYS(n)` a joiner or leaver loses the gap's share of the month (replaces `W-29.3` § 13 decision 2); an employee with no work location fails alone when holidays are unpaid; the endpoint path is `/api/v1/payroll/payruns/...` |
| #23 | `W-19` Pay input ledger | Write API for modules, read API for the pay run, period locking (`V031`–`V032`); one reversal per row | **Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: no employee-ownership check on write; null `source_ref` allowed over HTTP; lock race under READ COMMITTED; reversal of a locked tagged row becomes an untagged correction |
| #24 | `W-20` Notifications | Templates, email delivery, in-app notification, reminder rules, scheduler (`V038`–`V039`, `V093`) | **`W-20.1` and `W-20.2` Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: delivery claim ignores `next_attempt_at` and the attempt cap; rule PUT can overwrite a concurrent claim |
| #25 | `W-21` Document store | Upload, download by signed link (streamed through the app), soft delete, `read_own` (`V037`) | **Done 2026-09-29 on `b6e6012` — Built by devashis.** `document-link-secret` must exist before the automatic deploy |
| #27 | `W-23` Reporting & export | Report definitions, streamed CSV/XLSX export, schedules, async export, read-only pool (`V040`, `V095`–`V096`) | **`W-23.1` and `W-23.2` Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: report rows not yet read as `readonly_user`; `async=true` path untested; spec § 8 names `azurite`, the service is `blob` |
| #28 | `W-24` Setup checklist & invitations | A module-aware checklist, progress tracking, user and employee invitation | **`W-24.1` and `W-24.2` Done 2026-09-30 on `288f9fa` — Built by krushna.** Outstanding in the merge commit. Migrations `V035`, `V117`–`V118`. Screens are `W-46.6` and `W-46.7`; Azure mail is `W-10.1` |
| #29 | `W-25` Employee self-service portal | My profile, leave, documents, payslips (Payroll only), timesheet (HRMS only) | **Done 2026-10-02 on `a53e311` — Built by karma**, merge-review fixes by claude. Fixed at merge: no seed linked the employee logins to an employee (`06-employees.sql`); `/me/employee` and `/me/documents` gave 500 for a login with no employee; the leave and documents panels read fields the server does not send. Founder decision 2026-10-01: the portal switch hides the panels only. Outstanding: nothing links to `/me` from the navigation feed; § 8 and the seed read, not run; nobody has opened the portal in a browser |
| #— | `W-39.1` Attendance capture (basic) | `core.attendance` — present, absent, half day per employee per date, entered by an administrator, so a Payroll-only tenant can record it (`D-35`); `AttendanceQuery.days()` is `W-18`'s read. **Done 2026-09-28 on `3bf5b10` — Built by sayeed.** Outstanding in the merge commit. Migration `V030` |
| #— | `W-39.2` Overtime capture (basic) | `core.overtime_request` (`V041`) — approved overtime entered by an administrator, written to the pay input ledger in the same transaction | **Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: a retried POST posts twice (the spec's key is the new row's own id — founder decision needed); locked-period redirect and audit row untested |

---

### 3c. `W-12.3` review — sent back 2026-09-25

Branch `W-12-3-navigation-feed` at `161c9d1`. CI red on frontend lint; backend green. Not merged.
Fix on the branch, re-run `check-done.mjs W-12.3`, then it comes back for merge. Items 1–4 block;
5–7 are fixed in the same pass because the same files are open.

| # | Defect | Where | Why it matters |
|---|---|---|---|
| 1 | `useNavigation()` called after an early return — rules-of-hooks; also the reason CI is red | `src/shell/navigation/useCan.js:16` | a screen whose action code changes between renders crashes React |
| 2 | `NavigationMatchesEnforcementIT` walks 4 of 8 items against test-only stand-in controllers | `core/src/test/.../navigation/NavigationTestEndpointsController.java:17-41` | the test the spec calls the ticket's reason to exist cannot catch catalogue drift |
| 3 | `core.employee` item targets `GET /api/v1/employees`, which does not exist — only `POST` and `/{id}` | `NavigationCatalogue.java:40` vs `EmployeeController.java:58-81` | every admin sees Employees and gets an error on click; hidden by item 2 |
| 4 | Routes are not registered from the feed: `routesFromFeed` is never called, no `<Routes>`, `routesFor(entitlements)` kept | `src/shell/routes.js:25,52`, `AppShell.jsx:98` | spec §5: a route not in the response is not registered at all |
| 5 | Frontend tests re-implement the logic inline and never import `useCan.js`, `useNavigation.js` or `AppShell.jsx` | `useCan.test.js`, `useNavigation.test.js` | a static fallback menu would not fail them — spec §9's top risk |
| 6 | `hrms.timesheets` and `payroll.runs` point at unbuilt endpoints; the dev-mode "target endpoint exists" check in spec §2 was not written | `NavigationCatalogue.java:76,83` | Globex sees two dead items; nothing flags a dead item |
| 7 | Tenant-switch refetch listens for `infinevo:tenant-switched`, which nothing dispatches; the test never triggers a refetch | `useNavigation.js:113-128`, `useNavigation.test.js:144-167` | claimed and tested behaviour that never runs |

Merge notes for the founder: take `code/` only (the branch also carries the `.agents/` harness
move, deletes `CLAUDE.md`, edits `ci.yml` and `.gitignore`); one conflict in
`PermissionGuardTestApp.java` — keep navigation, subscription and tenant in the scan list.

#### Second review — sent back again 2026-09-26

Branch `W-12-3-navigation-feed` at `51e5f4d`. All five `check-done.mjs W-12.3` gates pass and CI is
green. Not merged: items 1, 3 and 4 above are fixed, item 2 is half fixed, and items 5, 6 and 7 are
not fixed although the commit messages and test headers say they are. Trial squash onto `main` is
clean apart from the known scan-list conflict.

| Item | Status at `51e5f4d` | Evidence |
|---|---|---|
| 1 rules-of-hooks | fixed | `useCan.js:15` calls the hook before any return |
| 2 IT walks stand-ins | half fixed | 5 of 8 leaves hit real controllers; audit, timesheets and payroll runs still hit stubs at `NavigationTestEndpointsController.java:36-52`, and the two module stubs copy the catalogue's own module and action |
| 3 `GET /api/v1/employees` missing | fixed by a stub | `EmployeeController.java:75-78` returns `200 []` to every caller — see new item 8 |
| 4 routes from the feed | fixed | `AppShell.jsx:28,91-95` calls `routesFromFeed`; `routesFor` is dead code at `routes.js:52` |
| 5 tests never import the real code | **not fixed** | `useCan.test.js:40-48` is a local `canHold` copy; `useNavigation.test.js` builds its own store; nothing imports `useCan.js`, `useNavigation.js` or `AppShell.jsx`. The header at `useCan.test.js:11` claims the opposite |
| 6 dead items, no dev-mode check | **not fixed** | `NavigationCatalogue.java:76,83` unchanged; `NavigationCatalogueValidator.java:30` runs only under `dev`/`test` profiles, which nothing in `code/` or `infra/` activates, and it only logs `WARN` |
| 7 tenant-switch refetch never runs | **not fixed** | `infinevo:tenant-switched` is dispatched only inside `fetchNavigationFeed` (`useNavigation.js:51-54`), the very fetch the listener at `:139-147` is meant to trigger; the test calls a local `setFeed` and never touches the listener |

New items, same pass:

| # | Defect | Where | Why it matters |
|---|---|---|---|
| 8 | The employees list stub returns `200 []` to everyone, indistinguishable from "no employees", and fixes a bare-list shape before `W-13.3` designs pagination | `EmployeeController.java:75-78` | a `501`, or leaving the item out until `W-13.3`, passes the non-403 check honestly |
| 9 | For a visible item the IT asserts only "not 403", so a 404 or 405 passes — the original item 3 would still pass | `NavigationMatchesEnforcementIT.java:127-129` | the visible branch should assert 2xx |
| 10 | Spec §7 says `actions` equals `PermissionService`'s set exactly; the IT checks `isNotEmpty` and two `contains` | `NavigationIT.java:117-118` | a leaked or extra code passes |

Out of scope, noted: the ticket edits `shared` (`PermissionService.currentActions()`), which the
spec's size cap does not name; the change is what §4 relies on and is acceptable.

#### Fixed and merged — on `main` `a4f31ae`, 2026-09-26

All ten items closed on `68fcb73`; CI green; `check-done.mjs W-12.3` 5/5. Record in spec § 14a.

| Item | How it was closed |
|---|---|
| 2, 6, 8 | Catalogue holds only items whose endpoint exists (org masters, roles, audit). `core.employee`, `hrms.timesheets`, `payroll.runs` return in `W-13.3`, the timesheet ticket and `W-29`. Employees stub and the test-only stub controller deleted; the guard test app holds the real `AuditController`. The validator runs in every profile and refuses to start the app on a missing endpoint |
| 5 | Frontend tests run under vitest and jsdom and render the real shell, hooks, routes and adapter; `npm test` added to `ci.yml` |
| 7 | `keycloak.js` owns the token callbacks and exports `onTenantChange`; `useNavigation` refetches on it. Tested against the real adapter module |
| 9, 10 | Enforcement IT asserts 2xx for a visible item; `NavigationIT` asserts `actions` equals the caller's granted set exactly |

**Deviation for the founder to accept:** until a module endpoint ships, `admin.globex` sees core items only, so spec § 8's `hrms.*` / `payroll.*` expectation is not met yet. Module filtering is proven in `NavigationServiceTest`.

Merge notes: take `code/`, the spec, and the `ci.yml` frontend-test step. Same scan-list conflict in `PermissionGuardTestApp.java` — keep navigation. The `.agents/` move, `CLAUDE.md` deletion and `.gitignore` edit stay out.

### 3d. `W-21`, `W-20.1`, `W-23.1` review — sent back 2026-09-25

**Resolved 2026-09-29:** items 1–6 verified fixed on `dev-devashis`; all three merged on `main` `b6e6012`. Kept for the record.

Branch `dev-devashis` at `1dd05f0`, three tickets on one branch. CI red on the migration
module; every other job green. Not merged. Items 1–2 block; 3–6 are fixed in the same pass
because the same files are open. Fix on the branch, re-run `check-done.mjs` for each ticket,
then it comes back for merge. Trial merge onto `main` is clean.

| # | Ticket | Defect | Where | Why it matters |
|---|---|---|---|---|
| 1 | `W-20.1` | Flyway reads the `${...}` placeholders in the seeded template bodies as its own placeholders — 4 migration ITs error, and `V037`–`V040` have never applied through real Flyway | `V038__notification_template.sql:56-105` | `compose up migrate` and every deploy stop at `V038`. With `spring.flyway.placeholder-replacement=false` all 52 migration ITs pass, so this is the only migration fault |
| 2 | `W-20.1` | `PUT` a template with `active=false` does not switch the channel off: the lookup filters `ActiveTrue` and takes the newest date, so it falls back to the older seeded default and keeps sending; no test covers it | `NotificationTemplateRepository.java:20`, `NotificationTemplateRequest.java:9` | the request record documents the opposite; spec § 15 claims it works |
| 3 | `W-20.1` | `compose` joins the caller's transaction and throws on a missing value, missing template or invalid legacy `work_email` | `NotificationServiceImpl.java` (`compose`, `enqueueAfterCommit`) | a leave approval rolls back because its notification failed |
| 4 | `W-23.1` | `required_action` only has to exist, so a definition over employee data can be gated on a code employees hold | `ReportDefinitionServiceImpl.java:182-186` | harmless while only `hr` holds `core.report.read`; breaks spec § 6 once a payroll source exists |
| 5 | `W-23.1` | No test inserts a soft-deleted employee, and `ExportRlsIT` passes with RLS off because every source also filters `tenant_id` in its own query | `EmployeeReportSource.java`, `ExportRlsIT.java` | isolation is proven, RLS is not |
| 6 | `W-21` | Spec § 4 and § 6 still say the bytes never pass through the app; as built `DocumentDownloadController` streams them, and § 14 does not say it reverses that | `W-21-document-store.md`, `DocumentDownloadController.java` | the spec contradicts the code it describes |

Why the core suite did not catch item 1: `DocumentTestSchema`, `NotificationTestSchema` and
`ReportTestSchema` run the shipped SQL files over plain JDBC, not through Flyway. Only the
migration module's ITs go through Flyway, and they were not run before the push.

Spec edits by the developer, for the founder to confirm: `W-23.1` § 2, § 4 and § 7 now ship
`employee`, `org_master` and `audit_log` instead of leave balances and pay inputs, recorded as
"accepted by the owner". All three specs gained an "as built" section. The compose service
rename `azurite` → `blob` is correct.

Merge notes for the founder: the branch is cut before `W-52.1` (`5c07c45`), so its "no producer
until `W-52.1`" comments are stale but harmless — the row is the outbox. It also edits
`05-azure-architecture.md` to list the fourth queue, `notification`, which matches `storage.bicep`.

#### Second review — sent back again 2026-09-26

Branch `dev-devashis` at `dd1cda1`. All five gates pass for each ticket and CI is green. Not merged:
only item 1 is fixed. The two commits since `1dd05f0` touch the migration `application.yml`,
`core/pom.xml`, `ci.yml` and two spec lines; no file behind items 2–6 was changed. Trial squash
onto `main` has one conflict, `EndpointGuardCoverageTest.java` (both sides add to the exempt map —
keep navigation and the three new controllers).

| Item | Status at `dd1cda1` | Evidence |
|---|---|---|
| 1 Flyway placeholders | fixed | `migration/src/main/resources/application.yml:40` sets `placeholder-replacement: false`; no other `V*.sql` uses `${...}`, so nothing else is affected |
| 2 `active=false` does not switch a channel off | **not fixed, blocks** | `NotificationTemplateRepository.java:19-21` still filters `ActiveTrue` and takes the newest date; `NotificationTemplateRequest.java:9-10` still claims the opposite; no test |
| 3 `compose` joins the caller's transaction | not fixed, same pass | `NotificationServiceImpl.java:102-103` `@Transactional` REQUIRED; throws at `:114-115`, `:118`, `:251` |
| 4 `required_action` only has to exist | not fixed, same pass | `ReportDefinitionServiceImpl.java:182-186` still only `actions.existsById` |
| 5 no soft-delete test; RLS IT cannot fail | not fixed, same pass | `EmployeeReportSource.java:82` filters `tenant_id` itself; nothing under `core/src/test/.../report/` inserts a deleted employee |
| 6 W-21 spec says bytes never pass through the app | not fixed, same pass | `W-21-document-store.md:71,109`; `DocumentDownloadController.java:99` streams an `InputStreamResource` |

Also noted, not for devashis: the local compose `worker` has no `DOCUMENT_BLOB_CONNECTION_STRING`
(`compose.yml:204-222`), harmless until `W-23.2` stores a file from the worker. The `core/pom.xml`
JNA exclusion is safe: only `ManagedIdentityCredentialBuilder` is used (`DocumentStorageConfig.java:47`).

**Deployment note for the founder, when this does merge:** `app` and `worker` refuse to start
without `DOCUMENT_LINK_SECRET` (`app/.../application.yml:73`, `worker/.../application.yml:79`).
`deploy.yml` ships the image automatically after CI on `main` with `--set-env-vars`, but the
Bicep that adds the Key Vault reference and `deploy.sh` that seeds `document-link-secret` run only
by hand (`infra.yml` is what-if only). Run `deploy.sh` and the Bicep **before** merging, or the
dev environment crash-loops on the next deploy.

**Tracker-level risk (F-9) — decided 2026-09-26:** `spring.flyway.out-of-order: true` is on
`main` since `e699699` (`W-13.4`). Lanes may now merge in tracker order; nothing is renumbered.
`validate-on-migrate` stays on.

## 4. Stream D — Payroll

`W-26.1` and `W-26.2` are on `main` (`a3ad0a3`); `W-27.1` and `W-27.2` on `main` (`79c8825`). `W-19` and `W-30.1` are on `main` (`b6e6012`); `W-28` is on `main` (`288f9fa`); `W-29.1`–`W-29.4` are on `main` (`7dfd74e`).

| # | Ticket | What it is |
|---|---|---|
| #30 | `W-26.1` Salary component catalogue | `payroll.earning`, `deduction`, `benefit`, `reimbursement` — tenant-scoped definitions; one `default_value` + `calculation_type` replaces two amount fields and a flag; `max_limit` becomes numeric. **Done 2026-09-28 on `a3ad0a3` — Built by biren.** Migrations `V042`–`V045` |
| #30 | `W-26.2` CTC structure & revisions | `payroll.ctc_structure` as one row per dated version plus its component rows and the statutory eligibility profile from `W-13.1` decision 1; the split is computed server-side, the pay run reads by date and writes nothing. **Done 2026-09-28 on `a3ad0a3` — Built by biren.** The three competing amount fields and the floating-point money are gone. Accepted as-is: `PUT` does not check the new date against today; BASIC matched by code for benefits; missing `calculation_type` defaults to `FLAT`; bare DTOs, no envelope (DEBT-008 open). Outstanding: no raw-SQL RLS write check in `ComponentRlsIT`; unused validation starter in the payroll pom. Migrations `V046`–`V050` |
| #31 | `W-27.1` FBP plan definition | `payroll.fbp`, one row per tenant: enabled, declaration window, lock, notification flags, reminder days; the plan's components are the `W-26.1` rows flagged `is_fbp_component`. Mails stored, not sent (`W-20.x`). **Done 2026-09-28 on `79c8825` — Built by biren.** Migration `V051` |
| #31 | `W-27.2` FBP employee declaration | `payroll.employee_fbp_component`, one row per FBP line per salary version; the employee declares under `/me/fbp-declaration` while the window is open, the officer any time; the version-in-force read shows the declared and unallocated amounts, which `W-29` consumes; carried forward on revise. Three new action codes. **Done 2026-09-28 on `79c8825` — Built by biren.** Outstanding in the merge commit; the future-dated-revise declaration gap carried into `W-29.1`. Migrations `V052`–`V053` — size-cap exception granted 2026-09-25 |
| #32 | `W-28` Pay schedule | `payroll.pay_schedule`, one row per tenant: work week, pay-day rule, input cut-off day, first period; implements `W-18.1`'s `WorkingWeekSource` and derives every period's dates for `W-29`. **Per `D-60`:** the basis and the payable flags stay on `core.lop_policy`; the screen is `W-47`'s. **Done 2026-09-30 on `288f9fa` — Built by krushna.** Outstanding in the merge commit. Migration `V054` |
| #33 | `W-29.1` Pay run — creation, inclusion, locking | `payroll.payrun` and `payroll.employee_payrun`; one non-cancelled run per tenant and period by a partial unique index; every employee considered gets a row, `INCLUDED` or `SKIPPED` with a reason; lock calls `W-19`'s `PayInputService.lock`; the eight-value status vocabulary for all four parts. **Done 2026-09-30 on `7dfd74e` — Built by krushna.** Outstanding in the merge commit. Migrations `V055`–`V056` — size-cap exceptions (two scripts, one `core` read method) granted 2026-09-25 |
| #34 | `W-29.2` Pay run — earnings and deductions | `payroll.employee_payrun_line` with code and name snapshots, money columns on the two `W-29.1` tables, all `numeric(19,4)`; `POST /compute` runs every `PayLineContributor` in order and sums; this ticket ships the `STRUCTURE` contributor only — statutory is `W-31`, tax is `W-36`, LOP and pay inputs are `W-29.3`. **Done 2026-09-30 on `7dfd74e` — Built by krushna.** Outstanding in the merge commit. Migration `V057` |
| #35 | `W-29.3` Pay run — loss of pay and pay inputs | Two more contributors in the `W-29.2` loop: `LOP` scales the pro-rata lines by `W-18.1`'s divisor for LOP days and days outside the employment window, leavers included; `PAY_INPUT` turns the `W-19` ledger into lines, one per kind, read once per run. No HRMS call. Negative net kept and counted; hours-only overtime counted as unpriced, not paid. The stamp columns stay `W-18.2`'s. **Done 2026-09-30 on `7dfd74e` — Built by krushna.** A loss-of-pay reversal posted to a later period is paid back as `LOP_REVERSAL` (added at merge; the spec does not describe it yet). Outstanding in the merge commit: joiners and leavers are underpaid when the policy counts working days only — carried to `W-18.2`. Migration `V058` |
| #36 | `W-29.4` Pay run — async on the worker | `POST /compute` returns `202` and enqueues on `payrun`; `PayrunQueueListener` calls `W-29.2`'s service; progress on the run and on `core.job_status`; resume by attempt number after a dead worker, stale after 15 minutes; duplicate messages never compute twice. The last part of the merge. **Done 2026-09-30 on `7dfd74e` — Built by krushna.** `V059` also grants `core.job.read` to `payroll-officer` by its own function and trigger. Outstanding in the merge commit. Migration `V059` |
| #37 | `W-30.1` Pay input run tag | `run_ref` on `core.pay_input` and on the lock table, no FK; `forRun`, `lockRun`; a tagged row obeys its run's lock, not the period's, and `forPeriod` returns untagged rows only. No table. **Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: the tagged-row-ignores-period-lock rule proven only against a mock; `lockRun` tenant scoping untested. Migration `V060` |
| #37 | `W-30.2` Off-cycle pay run | `run_type = 'OFF_CYCLE'` on `payroll.payrun`, zero new tables (founder 2026-09-25); named employees, bank only; `POST /payruns/{id}/inputs` writes tagged ledger rows; `STRUCTURE` and `LOP` contribute nothing, `PAY_INPUT` reads `forRun`; the one-per-period index narrowed to regular runs. One-time payout and bonus are `W-19`/`W-29.x`'s; withheld-salary release dropped. **Done 2026-10-02 on `9d5c0a0` — Built by krushna.** Fixed at merge: one input reference for two employees left the second unpaid, now a `400`. Founder 2026-10-02: an off-cycle row needs no loss-of-pay policy or work location and carries no stamp. Outstanding in the merge commit. Migration `V061` |
| #38 | `W-31.1` EPF and ESI settings | `payroll.epf_setting`, `payroll.esi_setting`, one row per tenant; numeric rates and wage ceilings replace text like `"12.00%"` and the browser's `15000`; defaults returned without a row. **Done 2026-09-29 on `ac94662` — Built by sayeed.** Outstanding in the merge commit. Migrations `V062`–`V063` |
| #38 | `W-31.2` Professional tax | Shared state slabs in `reference.pt_state` / `pt_slab` (founder decision 2026-09-25), seeded from the state Acts for the 21 states legacy supports; tenant override as rows in `payroll.org_pt_override` / `org_pt_override_slab`, every change in `pt_history`; `resolve(gross, gender, period)` for `W-31.4`; female exemption and deduction months kept as slab columns. **Done 2026-09-29 on `ac94662` — Built by sayeed.** Outstanding in the merge commit. Migrations `V064` (`reference`), `V065`–`V067` |
| #38 | `W-31.3` Employee EPF and ESI lines | `payroll.ctc_epf_component`, `ctc_esi_component` — the two tables `W-26.2` §13 handed here; derived server-side on every version from `W-31.1`'s rates and the statutory profile, employee share included, scale 4, no rounding. **Done 2026-09-29 on `ed180b1` — Built by sayeed.** Outstanding in the merge commit. Migrations `V068`–`V069` |
| #38 | `W-31.4` Statutory pay-run lines | `StatutoryLineContributor` `@Order(400)`, the `STATUTORY` slot `W-29.2` reserved: employee PF, employee ESI and PT deducted, employer shares as `BENEFIT` (founder decision 2026-09-25); earned-wage scaling, rupee rounding once per line, PT on the period's month. No table. **Done 2026-10-03 on `2780d098` — Built by sayeed** (five lanes merged together on `integration-2026-10-03`) |
| #39 | `W-32.1` Tax declaration — window, submission and revision | `payroll.income_tax_declaration`, one row per tenant **per financial year** (window dates, manual lock, default regime, PAN-for-rent rule); `payroll.employee_investment_declaration`, one per employee per year with `DRAFT`/`SUBMITTED` and a per-employee lock; the employee submits and reopens under `/me/tax-declaration/{fy}` while the window is open, the officer any time; `editable()` for `.2`–`.4`; `FinancialYear` holds the April–March rule once; no auto-lock job (the date decides). Four action codes. **Done 2026-09-29 on `b704f3c` — Built by mohit.** Outstanding in the merge commit: declaration FK does not tie employee to tenant (service check covers it); a first-read race can return 500; 409/404 mappings not proven over HTTP. Migrations `V070` (`reference`), `V071`–`V072` |
| #40 | `W-32.2` Tax declaration — house rent, home loan, let-out property | Four tables `payroll.employee_inv_house_rent`, `_home_loan`, `_let_out_property`, `_let_out_property_line`; months as `date`, rent periods non-overlapping, landlord PAN enforced server-side over `reference.hra_rule_master`'s threshold; `net_income_loss` derived with the reference 30 %; no caps applied (that is `W-33`). **Done 2026-09-29 on `b704f3c` — Built by mohit.** Outstanding in the merge commit: declaration FK does not tie employee to tenant (service check covers it); a first-read race can return 500; 409/404 mappings not proven over HTTP. Migrations `V073`–`V076`, and `V105` (`reference`, FY 2026-27 HRA and let-out rules carried forward by founder decision) |
| #41 | `W-32.3` Tax declaration — section 6A, pre-tax deductions, previous employment | `payroll.employee_inv_section6a` (FK to `reference.section6a_item_master`, a description, several rows per item), `_pre_tax_deduction`, `_prev_employment` (`entered_by` — the officer's rows lock the section); per-item and per-group (`80C_GROUP`) limits enforced from the seed; regime filter on the catalogue. **Done 2026-09-29 on `b704f3c` — Built by mohit.** Outstanding in the merge commit: declaration FK does not tie employee to tenant (service check covers it); a first-read race can return 500; 409/404 mappings not proven over HTTP. Migrations `V077`–`V079` |
| #42 | `W-32.4` Tax declaration — other income and tax summary | `payroll.employee_inv_other_income` (four kinds) and `payroll.employee_inv_tax_summary`, one row per declaration per regime whose computed columns are nullable and written only by `W-33` through `TaxSummaryService.record`; `GET …/summary` totals every declared section server-side, never stored. **Done 2026-09-29 on `b704f3c` — Built by mohit.** Outstanding in the merge commit: declaration FK does not tie employee to tenant (service check covers it); a first-read race can return 500; 409/404 mappings not proven over HTTP. Migrations `V080`–`V081`, and `V106` (`reference`, FY 2026-27 home-loan and 80TTA/TTB rules for `W-33`, carried forward) |
| #43 | `W-33.1` Tax calculator — new regime and the engine | `RegimeCalculator` seam plus the shared engine (`SalaryProjection` from the CTC versions month by month, `SlabTax`, `Rebate87A`, `SurchargeAndCess` **with marginal relief**, `AgeCategory` by 31 March); `NewRegimeCalculator`; `GET …/tax` pure preview, `POST …/tax/compute` fills every regime's summary row through `W-32.4`'s `record`; every figure from a `reference` rule row keyed on year, regime and age; no table. **Done — on `main` `7907a6c`, built by mohit.** Outstanding: no slabs seeded for FY 2026-27; no 87A marginal relief in the new regime |
| #44 | `W-33.2` Tax calculator — old regime | `OldRegimeCalculator` on the `.1` engine: HRA month by month, house-property loss capped **as a total**, 80TTA/80TTB by age, Chapter VI-A per-item then per-group caps, 80EE/80EEA by sanction window, standard deduction and PT under section 16, pre-tax precedence (structure first, declared row second); `V027` senior slabs read for the first time and the `V099` fixture gains them; no table, no hard-coded cap (grep gate). **Done — on `main` `7907a6c`, built by mohit.** The response carries the old-regime working |
| #45 | `W-33.3` Tax calculator — revisions and recalculation | `payroll.tax_computation`, append-only, one row per computation with the working as JSONB (replaces legacy's five result tables); `TaxRecalculationService` runs after commit on `DeclarationSubmittedEvent` (`W-32.1`), `SalaryVersionChangedEvent` (`W-26.2`), `ProofVerifiedEvent` (`W-34` later) and an officer `POST`; calls `TaxSummaryService.record` and `EmployeeTdsService.record`; legacy's default TDS becomes a `SALARY_DEFAULT` computation on the first salary event, never in the run; **since 2026-10-02 also** the FY 2026-27 tax rules seed (FY 2025-26 carried forward) and the removal of surcharge marginal relief, as legacy (spec § 2). **Done 2026-10-03 on `8b7705c3` — Built by mohit**, merge fixes by claude: the FY 2026-27 seed (`reference/V147`) and the removal of surcharge marginal relief, both missing from the branch. Replayed onto main from `Dev-Mohit`, which was 139 commits behind. Migrations `V104`, `V147`. The FY 2026-27 values are FY 2025-26 carried forward; the tax-rule owner re-checks them before the first real pay run |
| #46 | `W-34.1` Proof of investment — window and submission | Five `poi_*` columns on the per-year window; `payroll.employee_proof_of_investment`, `employee_proof_item` (one per declared line: house rent, home-loan principal and interest, let-out line, 6A, employee-entered previous employment), `employee_proof_item_document` → `core.document`. Employee claims, uploads, submits against a `SUBMITTED` declaration; publishes `ProofSubmittedEvent`; declaration reopen refused while proof `SUBMITTED`/`APPROVED`. **Done 2026-10-02 on `5fa04b1` — Built by devashis.** Submit and reopen serialise on the proof row lock (merge review) |
| #46 | `W-34.2` Proof of investment — verification, comments and return | Starts the `PROOF_OF_INVESTMENT` engine instance on submit; item `APPROVE`/`DISALLOW`/`RETURN` and final decision through a payroll wrapper; `ProofOutcomeHandler` writes the outcome, publishes `ProofVerifiedEvent`, notifies; `TaxInputGatherer` prefers approved amounts; flat item comments. **Part on main `5fa04b1` — Built by devashis.** Review, comments, outcome and approved amounts in the tax calculator are on `main`. **Done 2026-10-04 on `6e19dd89` — Built by Claude (batch-2026-10-03).** `ProofVerifiedEvent` published once on final approve; `ProofTaxRecalcIT` proves the `tax_computation` row uses the approved figures, and surfaced a rounding defect in `TaxRecalculationServiceImpl` (fixed). Migration `V115` |
| #46 | `W-34.3` Proof of investment — reminders and chase list | `POI_DUE_DATE` anchor and `POI_PENDING` audience resolvers for `W-20.2`'s sweep (replaces legacy's own cron, DEBT-021); chase list with `NOT_STARTED`, status counts. **Done 2026-10-02 on `5fa04b1` — Built by devashis.** No reminder before `poi_opens_on` (merge review). No migration |
| #47 | `W-35.1` Reimbursement claims | `payroll.employee_reimbursement_request` against a `payroll.reimbursement` component, receipt as a `W-21` `document_id`; submit starts the seeded `REIMBURSEMENT` flow; the outcome handler writes one `REIMBURSEMENT` ledger row for the approved amount and stores `posted_period` (the ledger redirects a locked period); no approve endpoint, no paid flag; legacy `ReimbursementClaim` settings row retired. **Done 2026-09-29 on `840bf2e` — Built by sayeed.** Outstanding in the merge commit. Migrations `V097` (`reference`), `V098` |
| #47 | `W-35.2` Ad-hoc salary deductions | `payroll.employee_deduction`, batch entry 1–500 all-or-nothing, each line an `AD_HOC_DEDUCTION` ledger row in the same transaction; `DELETE` reverses on the ledger, never edits; `POSTED` / `REVERSED` only; closed list of six types; proof as `EMPLOYEE_DOCUMENT`. **Done 2026-10-02 on `9d5c0a0` — Built by krushna.** Fixed at merge: the `400` names the failing line in `fieldErrors.line`. Outstanding in the merge commit: the all-or-nothing test fails only before the first write. Unblocks `W-47.4`. Migrations `V100` (`reference`), `V101` |
| #48 | `W-36.1` Employee TDS record and pay run tax line | `payroll.employee_tds`, one active row per employee per financial year, superseded not edited; written by `EmployeeTdsService.record` (officer `PUT` now, `W-33` later), never by the run; `TaxLineContributor` `@Order(500)` writes one `TAX` `DEDUCTION` line = (annual tax − year-to-date from the lines of `COMPUTED`/`APPROVED`/`PAID` runs) ÷ months left to March. **Done 2026-10-02 on `9263c30` — Built by karma**, merge-review fixes by claude: off-cycle runs carry no TDS; no record is noted on the row (`employee_payrun.computation_note`); 400 on a missing figure, 409 on a concurrent first save; default month is the Indian month; HTTP tests. Outstanding: tax on off-cycle payments undecided; `PayRunTaxLineIT` contributor count back to 5 when `W-31.4` lands. Migration `V102` |
| #48 | `W-36.2` Payslips — approve, pay, render, signed link | `approve` (`COMPUTED→APPROVED`) and `pay` (`APPROVED→PAID`, `paid_on`) land here, closing `W-47.2` §13 decision 1; on `PAID` one `PAYSLIP_READY` per employee with a 7-day HMAC link, same secret as `W-21` under its own domain tag; slip rendered from the row and lines, never stored; anonymous read at `PublicEndpoints.PAYSLIP_OPEN`, every refusal `404`, token never logged (`PayslipLogIT`). **Done 2026-10-02 on `9d5c0a0` — Built by krushna.** Founder 2026-10-02: a `PAID` run is never cancelled (`V103` checks `paid_on` against it); the link base is the required `PAYSLIP_LINK_BASE_URL`. The real `/api/v1/me/payslips` replaced `W-25`'s placeholder. Outstanding in the merge commit: no public page yet; no test through the real security chain. Unblocks `W-37`. Migration `V103` (columns on `payroll.payrun`) |
| #48 | `W-36.3` Tax deductor details | `payroll.tax_deductor`, one row per tenant: TAN, PAN, TDS circle, signatory (an employee or a named person), real formats checked server-side; `PUT`/`GET` under `/api/v1/payroll/settings/tax-deductor`, `payroll.settings.manage`; ports legacy `incomeTaxDetails` (`02-data-model.md` `income_tax_detail`), deposit schedule dropped. **Done 2026-10-03 on `2780d098` — Built by sayeed.** Split from the first `W-36.3` (Form 16) on 2026-09-29. Migration `V107` — founder 2026-10-03: this `V107` stands; `W-36.4`'s differing copy becomes a new script |
| #48 | `W-36.4` Annual statement (Form 16) | Rendered, never stored: deductor (`W-36.3`), quarterly tax deducted from `TAX` lines of `PAID` runs, payable = `employee_tds.annual_tax`, Part B breakdown from the latest `tax_computation` only when it matches (else `OFFICER_OVERRIDE`), `final` once March is paid; officer read and `/me`; JSON, no PDF. **Done 2026-10-03 on `8b7705c3` — Built by mohit**, merge fixes by claude: `@RequiresModule` and `@RequiresAction` on `Form16Controller` (CI was red), PAN debug log removed; the branch's own deductor package and `V107` dropped for `W-36.3`'s on main (founder 2026-10-03). No table |
| #48 | `W-36.5` Form 16 Part A upload | Officer uploads the TRACES Part A ZIP; each PDF is matched to an employee by the PAN in its file name and stored as a `W-21` document of new system kind `FORM16_PART_A`; `payroll.form16_part_a` links employee and year, re-upload supersedes; employee downloads own by signed link. **Done 2026-10-04 on `6e19dd89` — Built by Claude (batch-2026-10-03).** Size-cap exception approved by the founder 2026-10-03. Fixed at review: an ambiguous PAN is skipped, not unmatched (`core` `PanLookup`); 200 MB zip-bomb guard tested. Multipart limit raised 11 to 51 MB. Migrations `V108` (`core`), `V109`. Open: encrypted ZIPs, real TRACES file naming; officer list carries no signed link (spec § 2 vs § 3) |
| #49 | `W-37` Payroll dashboard | One read-only `GET /api/v1/payroll/dashboard` for the bound tenant: active headcount, the last run's skipped-by-reason, current and recent run cards, April-to-March months with `PAID` year totals, EPF/ESI/PT/TDS tiles summed from `employee_payrun_line`; no table, no cache, no screen (`W-47.5`). **Done 2026-10-04 on `6e19dd89` — Built by Claude (batch-2026-10-03).** Reuses `taxdeclaration.FinancialYear`; `MonthRow` carries `payrun_id` (one row per run); statutory tiles count `STATUTORY` lines only. Unblocks `W-47.5` |
| #50 | `W-38.1` Prior payroll import | `payroll.prior_payroll_month` (gross, EPF, ESI, PT, TDS, net per employee per month) and `payroll.prior_payroll_import_log`; CSV template, upload, dry run, partial success, error file — the `W-16.4b` pattern; a month with a real regular run is refused, and a regular run is refused for an imported month; `status` lists the missing months; adds the `payroll.prior_payroll` menu item. **Split 2026-10-02** from `W-38`; spec `W-38-1-prior-payroll-import.md`. Size M. **Done 2026-10-03 on `2780d098` — Built by krushna.** Migration `V140`. Open: imported gross is not used by the tax calculator (spec § 13 decision 6) |
| #50 | `W-38.2` Imported tax counts as already deducted | Monthly TDS year-to-date and Form 16 quarters add the imported TDS; an imported month counts toward Form 16 `final`. Spec `W-38-2-prior-tax-counted.md`. Size S. **Done 2026-10-04 on `6e19dd89` — Built by Claude (batch-2026-10-03).** Form 16 `final` now counts paid and imported months as a set. No table |
| #50 | `W-38.3` Prior payroll setup step | `PRIOR_PAYROLL` at order 4 in the catalogue, done when any month is imported or the first regular run is in April, skippable with a reason; `setup_step_skipped` on the status. Spec `W-38-3-prior-payroll-setup-step.md`. Size S. **Done 2026-10-03 on `2780d098` — Built by krushna.** No table; one `core` catalogue line accepted by the founder |

> **Tax is the largest and most compliance-exposed area.** `W-33.2` (#44) was next in the
> queue once `W-09` shipped its 15 reference tables, and carries three conditions from
> `W-09`'s review: Chapter VI-A has no `financial_year`, `home_loan_rule_master` has no
> regime column, and loss carry-forward defaults FALSE.

---

## 5. Stream E — HRMS

`W-41` is on `main` (`5fa04b1`). `W-40.1`, `W-40.2`, `W-40.3`, `W-40.5`, `W-42.1`–`.4`, `W-43.1` and `W-43.2` are on `main` (`2780d098`); `W-40.4` and `W-40.6` are Ready (karma).

> **Renumbered 2026-09-24**, following `10-scoping.md:93,117`. `W-39` is Core's basic
> attendance and overtime capture (`D-35`), now in Stream C. HRMS attendance and the
> overtime request are one ticket, `W-40`.

| # | Ticket | What it is |
|---|---|---|
| #51–52 | `W-40` Clock attendance & request workflows | Clock in and out, multiple sessions a day, attendance preferences moved over from Payroll, and the employee-submitted, manager-approved regularization and overtime requests. Blocked on `W-16` only (`W-39.1`, `W-39.2` and `W-15` on `main`) — **assigned — karma** (reassigned to sayeed 2026-09-30, back to karma 2026-10-02). **Split into six on 2026-09-30, specs written:** `W-40.1` attendance preferences (`hrms`) · `W-40.2` Core seams: clock write, tenant clock, `hrms.overtime.request` and the manager's `core.approval.decide` grant (`core`) · `W-40.3` clock in and out (`hrms`, after `.1`, `.2`) · `W-40.4` regularization request (`hrms`, after `.2`, `.3`) · `W-40.5` overtime request states (`core`) · `W-40.6` overtime request workflow (`hrms`, after `.2`, `.5`; closes #51–52). No part reads leave data. **`W-40.1`, `W-40.2`, `W-40.3`, `W-40.5` Done 2026-10-03 on `2780d098` — Built by karma** (migrations `V120`–`V123`, and `V139` kept outside the reserved block by founder decision 2026-10-03). **`W-40.4`, `W-40.6` Done 2026-10-04 on `6e19dd89` — Built by Claude (batch-2026-10-03)** (migration `V124`; `.6` none). Closes #51–52. Fixed at review: a racing duplicate regularization is `409`; the engine's "no active definition" is matched on its message — a typed exception in `core` would remove that. Unblocks `W-48.5` |
| #53 | `W-41` Projects, tasks, assignments | Project and task management, employee assignment — **Done 2026-10-02 on `5fa04b1` — Built by devashis.** |
| #54 | `W-42` Timesheets | Weekly timesheet, project, day and task entry, submit, approve. **Split 2026-10-02:** `W-42.1` entry · `W-42.2` approval engine change (`core`) · `W-42.3` submit and approve · `W-42.4` review lists |
| #54 | `W-42.1` Timesheet entry | `hrms.timesheet` and its project, task and day entries (`V141`–`V144`); create, replace and delete a draft week against the caller's `W-41` assignments; 24 hours a day at most, one week once; `/api/v1/me/timesheet` replaces the `W-25` placeholder; `hrms.timesheets` menu item; a project or task on a live timesheet cannot be deleted. Spec `W-42-1-timesheet-entry.md`. **Done 2026-10-03 on `2780d098` — Built by devashis** |
| #54 | `W-42.2` Approval engine: per-project steps | `core`: the approver lookup is told which project a step is for, and each project's hours get their own approval, so one rejection does not reject the rest. Founder decision 2026-10-02: approval stays per project, as legacy. `core/V145` (no table). Spec `W-42-2-approval-per-project-step.md`. Size S. **Done 2026-10-03 on `2780d098` — Built by devashis** |
| #54 | `W-42.3` Timesheet submit and approve | `hrms`: submit a week, one approval per project entry, the `PROJECT_MANAGER` resolver (own project → reporting manager; no manager → administrator), the outcome handler and roll-up, resubmit of rejected projects only, the approver's read of one entry, `APPROVAL_PENDING` / `APPROVAL_DECIDED` mails. No migration. Spec `W-42-3-timesheet-submit-approve.md`. Size M. **Done 2026-10-03 on `2780d098` — Built by devashis** |
| #54 | `W-42.4` Timesheet review lists | `hrms`: the project manager's list for their projects, the reporting manager's read-only team list, HR's list of all submitted weeks, with week and status filters, paged; the detail read widened by the same rules; no draft shown to anyone but its owner. No migration. Split from `W-42.3` by the founder 2026-10-02. Spec `W-42-4-timesheet-review-lists.md`. Size S. **Done 2026-10-03 on `2780d098` — Built by devashis** |
| #55 | `W-43` Timesheet reminders | Reminder rules, escalation, notification trigger. **Split 2026-10-02:** `W-43.1` reminder audience values and escalation event (`core`) · `W-43.2` timesheet reminders (`hrms`). Founder decisions 2026-10-02: chase last week; escalate to the primary reporting manager as one list; no default rule; approver reminders stay with the approval engine |
| #55 | `W-43.1` Reminder audience values, escalation event | `core`: an audience can supply its own mail values (`week_start`, `late_employees`), the sweep sends them, rule validation counts them; `TIMESHEET_ESCALATION` event and templates (`core/V146`, no table). Spec `W-43-1-reminder-audience-placeholders.md`. Size S. **Done 2026-10-03 on `2780d098` — Built by devashis** |
| #55 | `W-43.2` Timesheet reminders and escalation | `hrms`: audiences `TIMESHEET_LATE` (missing or draft last week, one mail per person) and `TIMESHEET_LATE_MANAGER` (one list per primary reporting manager), one query behind both. No migration. Spec `W-43-2-timesheet-reminders.md`. Size S. **Done 2026-10-03 on `2780d098` — Built by devashis** |
| #56 | `W-44` HRMS dashboards | One `GET /api/v1/hrms/dashboard`: the caller's own clock, timesheet, project and task figures, and a manager's projects, entries waiting for approval and direct reports; each block shown only with its action. No table, no migration, no screen (`W-48`). Spec `W-44-hrms-dashboard.md` written 2026-10-03. Size S. **Done 2026-10-04 on `722d1689` — Built by Claude.** (`W-40.3`, `W-41`, `W-42` on main `2780d098`) |

---

## 6. Stream F — Frontend

**Building.** `W-45`, `W-46.1`, `W-46.3a` and `W-46.4` are on `main` (`11ec157`, 2026-09-30) — the shell, employee screens, organisation setup and approvals. `W-46.3b`, `W-46.6` and `W-46.7` are on `main` (`24bb261`, 2026-10-01) — holiday calendar, setup checklist and invitation screens. `W-46.2`, `W-46.5`, `W-47.1a`, `W-47.1b` and `W-47.6` are on `main` (`2780d098`, 2026-10-03), which closes `W-46`.

| # | Ticket | What it is |
|---|---|---|
| #57 | `W-45` Shell | Layout, navigation driven by entitlement, **a real API service layer** (Payroll has none today), Keycloak adapter, runtime configuration, design tokens. **Spec written 2026-09-27** — `W-45-frontend-shell.md`; four of the six items shipped under `W-12.3`, so the ticket is the service-layer pattern, full tokens, shell error screens and the lint rules that enforce module boundaries. Size M. **Done 2026-09-30 on `11ec157` — Built by biren.** Outstanding in the merge commit. Unblocks every `W-46`, `W-47` and `W-65.3` screen |
| #58 | `W-46.1` Employee screens | List, create, employee page with the five detail sections and reporting line. **Spec written 2026-09-28** — `W-46-1-employee-screens.md`. Size M. **Done 2026-09-30 on `11ec157` — Built by biren.** Outstanding in the merge commit. Unblocks `W-47.1a` |
| #59 | `W-46.2` Leave administration screens | Types and policy, allocations, record on behalf (`D-35`), all requests, consumption and LOP, import; adds the `core.leave` menu items. **Spec written 2026-09-28** — `W-46-2-leave-admin-screens.md`. Size L. **Done 2026-10-03 on `2780d098` — Built by biren** |
| #60 | `W-46.3a` Organisation setup screens | Departments, designations, work locations. **Split 2026-09-28** from `W-46.3`; spec `W-46-3a-org-setup-screens.md`. Size S. **Done 2026-09-30 on `11ec157` — Built by biren.** Outstanding in the merge commit. Backend: deleting the filing-address location is a `409` |
| #60 | `W-46.3b` Holiday calendar screens | Calendars per location, holidays, lookup; adds the `core.holiday` menu item. Spec `W-46-3b-holiday-screens.md`. Size S. **Done 2026-10-01 on `24bb261` — Built by biren.** Outstanding in the merge commit. Calendar delete removed at merge: `W-17` has no such endpoint |
| #61 | `W-46.4` Approval screens | One inbox for every flow type, decide, delegations, definitions, history, reassign; adds the `core.approvals` menu items. **Spec written 2026-09-28** — `W-46-4-approval-screens.md`. Size M. **Done 2026-09-30 on `11ec157` — Built by biren.** Outstanding in the merge commit. Backend: the feed returns the tenant's `modules`; the pending list names the subject employee; a delegation carries `revocable` |
| #62 | `W-46.5` Employee self-service actions | Apply, withdraw, cancel leave; edit own personal and contact, inside `W-25`'s portal panels. Owns the apply screen `W-25` and `W-16.3` each assign to the other. **Spec written 2026-09-28** — `W-46-5-self-service-actions.md`. Size M. **Done 2026-10-03 on `2780d098` — Built by biren** |
| — | `W-46.6` Setup checklist screen | One page at `/setup`: progress, steps by module, skip with a reason; adds the `core.setup` menu item. **Spec written 2026-09-30** — `W-46-6-setup-checklist-screen.md`. Size S. **Done 2026-10-01 on `24bb261` — Built by biren.** Outstanding in the merge commit |
| — | `W-46.7` Invitation screens | User and employee invitation lists with invite, resend, revoke; the public accept / decline page the emailed link opens, rendered with no login; adds two `core.invitations.*` menu items. **Spec written 2026-09-30** — `W-46-7-invitation-screens.md`. Size M. **Done 2026-10-01 on `24bb261` — Built by biren.** Outstanding in the merge commit |
| #63 | `W-47.1a` Salary structure screens | Salary component catalogue (four kinds), Salary tab on the employee page with dated versions, revise, cancel, statutory profile. **Split 2026-09-28** from `W-47.1`; spec `W-47-1a-salary-structure-screens.md`. Every endpoint on `main` (`a3ad0a3`). Size M. **Done 2026-10-03 on `2780d098` — Built by sayeed.** Open: no `payroll.*` menu item exists in the catalogue (spec § 14) |
| #63 | `W-47.1b` Payroll settings screens | Pay schedule **with** the LOP basis on one screen (`D-60`), EPF, ESI, professional tax with per-state override, FBP plan, FBP tab on the employee page; **since 2026-10-02 also makes the `W-47.3` tax screens reachable** (spec § 5a: settings menu entry, `/me` panel provider and portal slot, officer route under `/employees`). Spec `W-47-1b-payroll-settings-screens.md`. Size M. **Done 2026-10-03 on `2780d098` — Built by sayeed**, merge fix by claude: the shell route test now expects the officer route under `/employees` (`4be5bccb`) |
| #64 | `W-47.2` Pay run screens | Run list, create for a period, run page with included and skipped, compute with progress, lines per employee, lock, cancel, off-cycle run with tagged inputs. No approve, pay or payslip: no endpoint exists (spec § 14). **Spec written 2026-09-28** — `W-47-2-pay-run-screens.md`. Size L. **Done 2026-10-02 on `36c02d1` — Built by krushna.** Approve and pay are on the run page (spec amended 2026-10-02); pay date checked on the tenant's day. F-1 (off-cycle grid showed an earlier-search employee as a raw id) fixed on main `c01b5ee`. Outstanding in the merge commit: names page the whole directory (F-2); pay-date picker on the browser's date (F-4); § 8 browser checks not run. Unblocks nothing on its own: `W-47.6` still waits on `W-38.1` |
| #65 | `W-47.3` Tax declaration screens | Declaration window settings per FY; the employee's declaration under `/me` in four sections with submit and reopen; officer header view. Calculator, proof and Form 16 wait for `W-33`, `W-34`, `W-36` specs. **Spec written 2026-09-28** — `W-47-3-tax-declaration-screens.md`. Size L. **Done — on `main` `7907a6c`, built by mohit.** Reachable since `W-47.1b` on main `2780d098`: settings menu entry, `/me` panel slot, officer route under `/employees` |
| #66 | `W-47.4` Claims screens | Employee claim form and own claims and deductions in one `/me` panel; officer claim list and detail, opened from the approvals inbox; deduction list, batch grid and reverse. Adds a claimable-components read, `employee_name` on both lists, the panel provider and two menu items in `payroll`; no migration. Spec `W-47-4-claims-screens.md` written 2026-10-02. Size M. **Done 2026-10-04 on `6e19dd89` — Built by Claude (batch-2026-10-03).** Shipped without receipt or proof upload (founder 2026-10-03) — neither employees nor payroll officers hold `core.document.upload` (spec § 13 decision 2); needs a small `core` ticket |
| #67 | `W-47.5` Payroll dashboard | Current run card with progress, headcount and skipped reasons, statutory tiles, months and recent runs, a setup card counting `/setup`'s open payroll steps; the `payroll.dashboard` menu item. No migration. Spec `W-47-5-payroll-dashboard-screens.md` written 2026-10-02 against `W-37` §4. Size S. **Done 2026-10-04 on `6e19dd89` — Built by Claude (batch-2026-10-03)**, with `W-37` on the same branch |
| #50 | `W-47.6` Prior payroll screens | `/payroll/prior-payroll`: template, upload, dry run, import, history, imported months with delete; a mid-year warning on the pay run list when months are missing and the setup step is not skipped. Spec `W-47-6-prior-payroll-screens.md`. Size S. **Done 2026-10-03 on `2780d098` — Built by krushna** |
| #68 | `W-48` HRMS screens | Attendance, timesheet, projects, dashboards. **Rewritten from MUI to Ant Design.** **Split 2026-10-03** into six: `W-48.1` projects and tasks · `W-48.2` timesheet entry · `W-48.3` timesheet review · `W-48.4` attendance · `W-48.5` regularization and overtime · `W-48.6` HRMS dashboard. One set of screens shaped by actions, not one per legacy role |
| #68 | `W-48.1` Project and task screens | `/hrms/projects` list, page with Team and Tasks tabs; `/hrms/my-work`. `hrms`: names on project, assignment and task replies, an assignable-employee search under `hrms.project.manage` (managers hold no `core.employee.read`), menu items `hrms.projects`, `hrms.my_work`. No migration. Spec `W-48-1-project-screens.md` written 2026-10-03. Size M. **Done 2026-10-04 on `722d1689` — Built by Claude.** (`W-41` on main `5fa04b1`) |
| #68 | `W-48.2` Timesheet entry screens | My weeks; one week grid to fill, save, submit and resubmit rejected projects; the `/me` timesheet panel replaces `W-25`'s placeholder. No backend change. Spec `W-48-2-timesheet-entry-screens.md` written 2026-10-03. Size M. **Done 2026-10-04 on `722d1689` — Built by Claude.** (`W-42.1`, `W-42.3` on main `2780d098`) |
| #68 | `W-48.3` Timesheet review screens | One review page, a tab per list (`W-42.4`); week and entry views; the approvals inbox opens a timesheet entry. `hrms`: names on timesheet replies, menu item `hrms.timesheet_review`. No migration. Spec `W-48-3-timesheet-review-screens.md` written 2026-10-03. Size M. **Done 2026-10-04 on `722d1689` — Built by Claude.** (`W-42.3`, `W-42.4` on main `2780d098`) |
| #68 | `W-48.4` Attendance screens | Clock in and out with my month; HR session log; HR attendance settings. `hrms`: `employeeName` on the session list, three menu items. No migration. Spec `W-48-4-attendance-screens.md` written 2026-10-03. Size M. **Done 2026-10-04 on `722d1689` — Built by Claude.** (`W-40.1`, `W-40.3` on main `2780d098`). Touches `hrms/attendance` alongside karma's `W-40.4`, `W-40.6` |
| #68 | `W-48.5` Regularization and overtime screens | Request forms and lists. Spec `W-48-5-request-screens.md` written 2026-10-04. Size M. **Done 2026-10-04 on `789b69a0` — Built by Claude.** (`W-40.4`, `W-40.6` on main `6e19dd89`; merge `W-48.4` first) |
| #68 | `W-48.6` HRMS dashboard screen | Employee and manager cards against `W-44`'s endpoint; the `hrms.dashboard` menu item. Spec `W-48-6-hrms-dashboard-screen.md` written 2026-10-04. Size S. **Done 2026-10-04 on `789b69a0` — Built by Claude.** (`W-44` on main `722d1689`) |
| #— | `W-68` HR overtime list | Grant `core.overtime.read` to `hr` and `payroll-officer` (`V148`); `employee_name` on `GET /api/v1/overtime`; `/hrms/overtime-requests/all` screen. Follows `W-48.5` §2. Spec `W-68-hr-overtime-list.md` written 2026-10-04. Size S. **Done 2026-10-04 on `42939851` — Built by Claude.** |
| #85 | `W-65.3` Admin console screens | Tenant list, create, module and status switches, act-as banner, audit tab, in `src/core/admin`. **Spec written 2026-09-27** — `W-65-3-admin-console-screens.md`. Size M. **Done 2026-10-04 on `6e19dd89` — Built by Claude (batch-2026-10-03).** Founder 2026-10-03: `AppShell` keeps the `/admin` routes mounted during an act-as session, so the audit tab is reachable. Fixed at review: tenant list/get/create no longer send `X-Impersonation`; the bootstrap invitation link survives the feed refetch. Open: platform tenant recognised by its fixed id; a session does not survive a page reload |
| #— | `W-73` Role-based workspaces & onboarding | Parent. Each role sees its own home page and a grouped menu; a tenant goes from "created" to "first employee invited" without platform staff acting as it. Fixes `D-33`–`D-42` first (they are the small half), then nine child specs below. **Spec written 2026-10-07** — `W-73-role-based-workspaces.md`. Order: `.1` `.2` `.3` `.4` together, then `.5` `.8`, then `.6` `.7` `.9` | **Done 2026-10-09 — all nine children on `main` (`8af6192a`…`861b56dc`) — Built by Claude** in parallel worktrees |
| #— | `W-73.1` Header branding | Company name, logo (initials when none) and tagline in the header; the signed-in user's name and role chips; a Company profile screen under Settings. `core.tenant` gains `logo_document_id`, `tagline` (`V160`). Spec `W-73-1-header-branding.md`. Size S | **Done 2026-10-08 on `8af6192a` — Built by Claude.** Deviations: PUT needs `core.tenant.manage`; logo PNG or JPG only |
| #— | `W-73.2` Platform dashboard | `/admin` home for the platform tenant: tenant count by status, recent tenants, admin-invitation state, Create tenant. Needs `D-33`, `D-35`, `D-42`. Spec `W-73-2-platform-dashboard.md`. Size S | **Done 2026-10-08 on `0c165413` — Built by Claude.** `V168`. Outstanding: V159 tenant functions have no platform check of their own |
| #— | `W-73.3` Add Employee with access | "Give portal access" on Add Employee with extra roles; one save creates the employee and sends the employee invitation. `EmployeeInvitationRequest` gains `roleIds`. Spec `W-73-3-add-employee-with-access.md`. Size S | **Done 2026-10-08 on `9dfce81b` — Built by Claude.** The `core.role.assign` rule now also applies to bulk invite (`W-73.7`) |
| #— | `W-73.4` Users & Access screen | One `/users` screen: accounts with roles, change roles (`PUT /users/{id}/roles` has no screen today), pending invitations of both kinds, invite user, resend, revoke. Replaces the two invitation menu items. Spec `W-73-4-users-and-access.md`. Size M | **Done 2026-10-09 on `db7b0d8e` — Built by Claude.** `V164`. Outstanding: last sign-in not shown (nothing records it) |
| #— | `W-73.5` Documents tab | Documents tab on the employee page over the `W-21` store: upload, list, download, delete; kinds ID proof, address proof, offer letter, other; employee sees own. Spec `W-73-5-employee-documents-tab.md`. Size S | **Done 2026-10-08 on `087b5e92` — Built by Claude.** Label in `core.document.label` (`V166`) |
| #— | `W-73.6` Scheduled earnings | A one-time or instalment earning planned for a future month: `payroll.scheduled_earning` (`V161`), written to the month's pay inputs as `ONE_TIME_PAYOUT` when the run opens, cancelled on termination; "Scheduled" checkbox in the component drawer. Spec `W-73-6-scheduled-earnings.md`. Size M | **Done 2026-10-08 on `04e1964c` — Built by Claude.** Outstanding: payslip shows a generic always-taxed "One-time payout" line; no Refresh button |
| #— | `W-73.7` Bulk invite | CSV of employees with email and roles → employees created and invited in one job on the worker; "Invite all without access" on the employee list. Spec `W-73-7-bulk-invite.md`. Size M | **Done 2026-10-08 on `7fa1b73a` — Built by Claude.** No migration. Outstanding: no sweeper for a job left RUNNING (W-52 debt) |
| #— | `W-73.8` Welcome screen | First sign-in page per role: tenant admin sees the checklist and the three next steps; employee sees profile completion and where things are. Dismissed once per user. Spec `W-73-8-welcome-screen.md`. Size S | **Done 2026-10-09 on `03f22628` — Built by Claude.** `V165` |
| #— | `W-73.9` Country templates | Create Tenant seeds a country's defaults: statutory settings, holiday calendar, leave types, salary component catalogue (India first, `reference` data, `V162`). Spec `W-73-9-country-templates.md`. Size M | **Done 2026-10-09 on `861b56dc` — Built by Claude.** `V162`, `V169`. Outstanding: the 2027 central holiday list must ship before 1 Jan 2027 |
| #74 | `W-74` Work From Home (WFH) Module | Dedicated independent remote work module mirroring leave allocation: annual quota (`core.wfh_policy`), pro-rata, bulk CSV import, requests (`core.wfh_request`), immutable deduction ledger (`core.wfh_consumption`), and auto-attendance (`PRESENT (WORK_FROM_HOME)`). Zero Loss of Pay (LOP). Spec `W-74-work-from-home.md`. Size L | **Done 2026-10-23 on main `f959ab51` — Built by Krushna.** Migrations `V179`–`V184` used. Spec `W-74-work-from-home.md`. |

---

## 7. Product items in streams G and H

| # | Ticket | What it is | Status |
|---|---|---|---|
| #75 | `W-55` Index & query standard | Tenant-leading index conventions, related-data fetching in one query, connection pooling. Calibrated to scale — the tables that matter are the ones growing with time: attendance, pay run lines, tax detail | **on main `2ccd723`** · done — checked in by biren |
| #85 | `W-65` Admin console | Tenant list, subscription management, module toggle, support impersonation, audit view. **The real onboarding tool, since there is no payment step.** Split 2026-09-27 into three, specs written: `W-65.1` tenant list and the Infinevo platform tenant (`core`, `V082`) · `W-65.2` support impersonation (`shared`, `V083`–`V084`, after `W-65.1`) · `W-65.3` screens (frontend, after `W-45`, `W-65.1`, `W-65.2`). Audit is read one tenant at a time while impersonating, no cross-tenant view | **`W-65.1` Done 2026-10-02 on `5fa04b1` — Built by devashis** · `W-65.2` **Done 2026-10-02 on `5fa04b1` — Built by devashis** · `W-65.3` **Done 2026-10-04 on `6e19dd89` — Built by Claude** |
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

## 9. Krushna (Dev 4) — Manual QA Day 4 Consolidated Defect Register (`krushna-bug-fix`)

> **Test Lead:** Krushna (Dev 4) — HRMS Core Track  
> **Consolidated Target Branch:** `krushna-bug-fix` (All Day 4 defect fixes consolidated in one branch)  
> **Source Verification:** `plan/Testing Day 4 full manual..txt` & `plan/HRMS_Detailed_Manual_Test_Plan.md`  
> **Execution Status:** 10 of 10 on `main` (merge commit of 2026-10-13) after one independent read. Two were rewritten in the merge — `BUG-D4-17` and `BUG-D4-20`, see 9.3 — and four items were left open, see 9.4. Final defect numbers (founder, 2026-10-13) are in the first column; four of the ten are the same defects Devashish logged and share their number.

### 9.1 Day 4 Consolidated Defect Matrix

| Defect ID | Severity | Module / Screen | Summary Description | Root Cause | Engineering Remediation | Status |
|---|:---:|---|---|---|---|:---:|
| **BUG-D4-13** → **D-96** | MINOR | `/employees` | In Employee Directory, Employee IDs are not sorted sequentially (e.g. EMP-001, EMP-002, EMP-003). | Database query in `EmployeeQueryService` lacked deterministic serial order; frontend table lacked natural sort comparator. | Backend: Added `ORDER BY employee_number ASC` natural ordering in `EmployeeQueryService.java`. Frontend: Added default comparator on `employeeNumber` column in `EmployeeList.jsx`. | **RESOLVED** |
| **BUG-D4-14** → **D-24** | CRITICAL | `POST /api/v1/employees` | Creating employee accepts duplicate Work Email across multiple employee records in the tenant. | `EmployeeServiceImpl.java:create()` lacked pre-flight duplicate check against `work_email` within tenant. | Added `existsByTenantIdAndWorkEmailIgnoreCaseAndDeletedFalse` check in `EmployeeServiceImpl.java`; rejects duplicates with HTTP 400 Conflict. | **RESOLVED** |
| **BUG-D4-15** → **D-97** | CRITICAL | `/employees/:id` (Identification) | System accepts duplicate PAN and Aadhaar across multiple employees; backend fields not enforced mandatory. | `EmployeeIdentificationServiceImpl.java` bypassed uniqueness validation and mandatory presence. | Backend: Enforced mandatory PAN & Aadhaar check and tenant-level uniqueness in `EmployeeIdentificationServiceImpl.java` throwing HTTP 400. | **RESOLVED** |
| **BUG-D4-16** → **D-98** | MINOR | `/employees/:id` (Documents) | In Employee Documents, "Delete document" confirmation popup remains stuck on screen after deletion completes. | `DocumentsTab.jsx` failed to reset delete modal visibility state in modal dismissal callback. | Updated `DocumentsTab.jsx` to explicitly invoke `setDeleteModalOpen(false)` upon delete promise completion. | **RESOLVED** |
| **BUG-D4-17** → **D-99** | MINOR | `/employees/new`, `/org/*` | Mixed/inconsistent casing allowed on Employee names, Departments, and Designations. | System accepted raw user input with inconsistent capitalization without formatting normalization. | Implemented `toTitleCase` helper in `shared/ui/format.js`; normalized employee names in `EmployeeServiceImpl.java`, and department/designation names in `AbstractOrgMasterServiceImpl.java` and `MasterTable.jsx`. | **RESOLVED** |
| **BUG-D4-18** → **D-100** | HIGH | `/employees/:id` (Identification) | Aadhaar number not validated as mandatory when required; missing 12-digit format check. | Client-side `SectionTab.jsx` skipped required check and digit pattern enforcement for Aadhaar. | Added pre-flight client-side validation in `SectionTab.jsx` enforcing mandatory values for all `field.required` fields (including 12-digit Aadhaar `^\d{12}$` and PAN formats). | **RESOLVED** |
| **BUG-D4-19** → **D-25** | MAJOR | `/employees/:id` (Reporting Line) | HR role lacks `core.reporting_line.manage`; "Set Manager" button hidden for HR. | Flyway role catalogue seed lacked `core.reporting_line.manage` for `hr` role. | Created Flyway migration `V172__grant_reporting_line_manage_to_hr.sql` granting permission to `hr` role across existing and new tenants. Added integration tests in `ReportingLineGuardIT.java` (HR gets 200, employee gets 403) and `RoleCatalogueIT.java`. | **RESOLVED** |
| **BUG-D4-20** → **D-28** | MEDIUM | `/employees/:id` (Terminate Modal) | Termination modal only displays Last Working Day; Reason dropdown and Remarks missing. | `TerminateModal.jsx` lacked reason select dropdown and remarks textarea input. | Added `TERMINATION_REASONS` dropdown (Resignation, Involuntary Termination, Retirement, End of Contract, Other) and Remarks textarea to `TerminateModal.jsx`, passing payload in `update`. | **RESOLVED** |
| **BUG-D4-21** → **D-31** | HIGH | `/employees/:id` | Terminated employee cannot be reactivated; Reactivate button hidden. | `EmploymentStatus.java` state machine prohibited transition from `TERMINATED` back to `ACTIVE`; frontend restricted button. | Allowed `TERMINATED` -> `ACTIVE` in `EmploymentStatus.java`. Updated `EmployeePage.jsx` `canReactivate` condition for `TERMINATED` employees, resetting `terminationDate: null`. | **RESOLVED** |
| **BUG-D4-22** → **D-101** | HIGH | `/employees/:id` | Single employee invitation send option not visible on Employee page; confirmation modal unmount leaks. | `EmployeePage.jsx` lacked standalone Send Invitation action button for active employees and lacked Modal unmount cleanup. | Added prominent "Send Invitation" button to Action buttons bar in `EmployeePage.jsx` for active employees without access, added `Modal.destroyAll()` unmount cleanup and explicit instance destruction. | **RESOLVED** |

---

### 9.2 Day 4 Detailed Engineering Remediation Details

#### BUG-D4-13: Sequential Sorting of Employee IDs in Directory
- **Defect:** In Employee Directory (`/employees`), employee IDs appeared out of order.
- **Root Cause:** JPA specification query sorted by creation timestamp instead of natural serial employee identifier.
- **Remediation:**
  - In `EmployeeQueryService.java`, added ascending order by `employeeNumber`.
  - In `EmployeeList.jsx`, configured default sorter `(a, b) => a.employeeNumber.localeCompare(b.employeeNumber, undefined, { numeric: true })`.
- **Status:** **RESOLVED** (`12a0366e`)

#### BUG-D4-14: Work Email Duplicate Rejection
- **Defect:** System allowed multiple employees to be created with the exact same work email address.
- **Root Cause:** Pre-flight tenant-scoped existence check was absent from `EmployeeServiceImpl.create()`.
- **Remediation:**
  - Added `employeeRepository.existsByTenantIdAndWorkEmailIgnoreCaseAndDeletedFalse(tenantId, normalizedEmail)` check in `EmployeeServiceImpl.java`.
  - Throws `ValidationException("An active employee with work email '" + email + "' already exists in this tenant.")` yielding HTTP 400.
- **Status:** **RESOLVED** (`12a0366e`)

#### BUG-D4-15: Mandatory & Unique PAN and Aadhaar in Backend
- **Defect:** Duplicate statutory identity details (PAN and Aadhaar) were accepted across employees, and fields were optional.
- **Root Cause:** Uniqueness checks were commented out/bypassed in `EmployeeIdentificationServiceImpl.java`.
- **Remediation:**
  - In `EmployeeIdentificationServiceImpl.java`, validated presence of PAN and Aadhaar.
  - Checked `existsByTenantIdAndPanNumberAndEmployeeIdNot` and `existsByTenantIdAndAadhaarNumberAndEmployeeIdNot`; throws `ValidationException` (HTTP 400) if duplicate found.
  - In `sectionFields.js`, marked PAN and Aadhaar as required fields.
- **Status:** **RESOLVED** (`12a0366e`)

#### BUG-D4-16: Document Delete Confirmation Modal Dismissal
- **Defect:** In Employee Documents tab, clicking Delete successfully deleted the document from storage, but the confirmation dialog remained stuck open.
- **Root Cause:** Asynchronous state cleanup in `DocumentsTab.jsx` failed to dismiss the modal after resolving the delete API request.
- **Remediation:**
  - Added explicit modal close `modalRef.destroy()` and document target reset in `handleDeleteConfirm` completion handler.
  - Added unit test in `DocumentsTab.test.jsx` verifying modal dismisses.
- **Status:** **RESOLVED** (`12a0366e`)

#### BUG-D4-17: Text Casing Normalization (Title Case)
- **Defect:** Employee names, departments, and designations accepted inconsistent, haphazard casing (all-lowercase, all-caps, mixed).
- **Root Cause:** Lack of input sanitization and presentation normalization across the application.
- **Remediation:**
  - Added `toTitleCase(str)` utility in `code/frontend/src/shared/ui/format.js` and comprehensive unit tests in `format.test.js`.
  - Applied Title Case normalization in `EmployeeServiceImpl.java` for full name, and in `AbstractOrgMasterServiceImpl.java` for department and designation names.
  - In frontend `MasterTable.jsx`, applied `toTitleCase` display formatting for department and designation rows.
- **Status:** **RESOLVED** (`12a0366e`, `b168a2b0`)

#### BUG-D4-18: Aadhaar Mandatory & 12-Digit Format Validation
- **Defect:** Aadhaar number not validated as mandatory when required on Employee Identification tab.
- **Root Cause:** Client-side validation in `SectionTab.jsx` did not enforce mandatory check for required fields.
- **Remediation:**
  - Added pre-flight client-side validation in `SectionTab.jsx` enforcing mandatory values for all `field.required` fields in SectionTab (including 12-digit Aadhaar `^\d{12}$` and PAN formats).
  - Added unit test coverage in `SectionTab.test.jsx`.
- **Status:** **RESOLVED** (`73bfaffa`)

#### BUG-D4-19: HR Reporting Line Manage Permission
- **Defect:** HR role lacks `core.reporting_line.manage`; "Set Manager" button hidden for HR.
- **Root Cause:** Seed migration did not assign `core.reporting_line.manage` to the `hr` role.
- **Remediation:**
  - Created Flyway migration `V172__grant_reporting_line_manage_to_hr.sql` granting `core.reporting_line.manage` to the `hr` role across existing non-platform tenants and future tenants via trigger.
  - Added `V172` invocation in `AuthzTestSchema.java`.
  - Added integration test `putWithHrRoleGets200()` in `ReportingLineGuardIT.java` and `core.reporting_line.manage` assertion in `RoleCatalogueIT.java`.
- **Status:** **RESOLVED** (`73bfaffa`, `krushna-bug-fix`)

#### BUG-D4-20: Employee Termination Modal Reason & Remarks
- **Defect:** In employee termination modal, only 'Last Working Day' is displayed; 'Reason' dropdown and 'Remarks' fields are missing.
- **Root Cause:** `TerminateModal.jsx` had only a DatePicker for `lastWorkingDate`.
- **Remediation:**
  - Added `TERMINATION_REASONS` dropdown (Resignation, Involuntary Termination, Retirement, End of Contract, Other) and Remarks textarea to `TerminateModal.jsx`, passing `terminationReason` and `terminationRemarks` in update payload.
  - Added unit tests in `TerminateModal.test.jsx`.
- **Status:** **RESOLVED** (`73bfaffa`)

#### BUG-D4-21: Terminated Employee Reactivation & Status Lifecycle
- **Defect:** Terminated employee cannot be reactivated; reactivate option button not visible.
- **Root Cause:** Backend `EmploymentStatus.java` transition matrix forbade transitioning from `TERMINATED` back to `ACTIVE`.
- **Remediation:**
  - Allowed transition from `TERMINATED` to `ACTIVE` in backend `EmploymentStatus.java` (`case TERMINATED -> Set.of(TERMINATED, ACTIVE)`).
  - Updated frontend `EmployeePage.jsx` `canReactivate` condition to include `TERMINATED` employees and reset `terminationDate: null` upon reactivation.
  - Added unit test in `EmployeeServiceImplTest.java` and `EmployeePage.test.jsx`.
- **Status:** **RESOLVED** (`73bfaffa`)

#### BUG-D4-22: Single Employee Invitation Button & Modal Destruction Lifecycle
- **Defect:** Single employee invitation send option not visible on Employee page; confirmation modal instances unmount leak.
- **Root Cause:** `EmployeePage.jsx` lacked action button for inviting single uninvited employee, and AntD modals leaked across unmounts.
- **Remediation:**
  - Added prominent "Send Invitation" button to Action buttons bar in `EmployeePage.jsx` for active employees without access, updated access tag fallback to handle null state, and added support for "Resend Invitation".
  - Added `useEffect(() => () => Modal.destroyAll(), [])` unmount cleanup and explicit `modalInstance.destroy()` in `handleDelete` and `handleReactivate`.
  - Added unit test in `EmployeePage.test.jsx`.
- **Status:** **RESOLVED** (`73bfaffa`, `krushna-bug-fix`)

---

### 9.3 Rewritten in the merge (independent read, 2026-10-13)

| Defect | What the branch did | What is on `main` |
|---|---|---|
| `BUG-D4-17` | Title Case rewrote every word on every save: "HR" → "Hr", "McDonald" → "Mcdonald", "A.K." → "A.k." | Only a value typed entirely in lower case is capitalised; one capital anywhere means it was cased by hand and is kept. Same rule in `format.js`, `EmployeeServiceImpl` and `AbstractOrgMasterServiceImpl` |
| `BUG-D4-20` | The dialog posted `terminationReason` and `terminationRemarks`, but no backend field existed — Jackson dropped them and the request still answered 200 | `V173` adds `core.employee.termination_reason` / `termination_remarks`; request, response and entity carry them, allowed only while `TERMINATED`; the Overview tab shows them; Reactivate clears them |

### 9.4 Left open by the merge review (founder to number)

| Found | Where | Suggested fix |
|---|---|---|
| Reactivating a leaver does not undo what termination did: scheduled earnings stay `CANCELLED`, `termination_date` is cleared so the record of the exit is lost, and the `EmploymentStatus` javadoc still calls termination final | `EmploymentStatus.java`, `payroll/.../EmployeeTerminatedListener.java` | decide what reactivation restores; keep the exit history |
| The new duplicate and mandatory checks block saving records that already hold duplicates or have no PAN/Aadhaar (no unique index exists; `V017` leaves cleaning to `W-67`); the check runs even when the value is unchanged | `EmployeeServiceImpl.java`, `EmployeeIdentificationServiceImpl.java` | check only on change; unique index after `W-67` |
| `BUG-D4-13` half done: the server sorts `employeeNumber` as text (EMP-1, EMP-10, EMP-2); the natural comparator the register describes is not in the code | `EmployeeQueryService.java`, `EmployeeList.jsx` | natural sort, or zero-padded numbers |
| Employee page shows "No access" plus two invite buttons while the access lookup is still loading or has failed; header and action bar both offer Invite and Resend | `EmployeePage.jsx` | one set of buttons; nothing until the lookup answers |

## 10. Krushna (Dev 4) — Manual QA Day 2 Execution & Defect Register (`testing-bug-fix`)

> **Not on `main`.** `testing-bug-fix` was never merged; its `V151__grant_reporting_line_manage_to_hr.sql` would now sort below the applied `V172`–`V173` and Flyway would refuse it. The same HR grant is on `main` as `V172` (§9, `BUG-D4-19`). Rebuild anything else still wanted from it on current `main`.

> **Test Lead:** Krushna (Dev 4) — HRMS Core Track  
> **Tenant Under Test:** `QA-D4` (`qa-d4` / UUID: `f0ec6679-1b4d-453c-ba85-56cb1402dab1`)  
> **Environment:** Azure Dev (`ep-infinevo-dev-huc0atg2hvd2fgbr.z02.azurefd.net`) & Local  
> **Target Branch:** `testing-bug-fix` (Commit: `ed861b05`)  
> **Execution Source:** `plan/krushna_dev4_qa_resultsDAY 2.json` & `plan/Testing Day 2 Dev 4..txt`  
> **Execution Status:** **12 of 12 Defects Resolved (All Marked RESOLVED)**

### 10.1 Day 2 Defect Matrix

| Defect ID | Severity | Module / Screen | Summary Description | Root Cause Component | Status |
|---|:---:|---|---|---|:---:|
| **BUG-D4-01** | MINOR | `/org/work-locations` | Location Name column text wraps vertically and clips | `MasterTable.jsx` / `WorkLocations.jsx` | **RESOLVED** |
| **BUG-D4-02** | MAJOR | `/roles`, `/roles/new` | `/roles` navigation route returns HTTP 404 Not Found | `shell/routes.js`, `core/index.js` | **RESOLVED** |
| **BUG-D4-03** | MINOR | `/invitations/users` | Role dropdown select overlay stays open after item selection | `UserInvitations.jsx` (AntD Select) | **RESOLVED** |
| **BUG-D4-04** | MAJOR | `/org/designations` | Finance role clicking Designations triggers "System Error" | `DesignationServiceImpl.java` | **RESOLVED** |
| **BUG-D4-05A** | CRITICAL | `POST /api/v1/employees` | Duplicate employee creation permitted with identical Email/Phone | `EmployeeServiceImpl.java`, `Employee.java` | **RESOLVED** |
| **BUG-D4-05B** | MINOR | `/employees` | Employee Directory: Emp ID & Name columns wrap into vertical lines | `EmployeeList.jsx` table columns | **RESOLVED** |
| **BUG-D4-06** | CRITICAL | `PUT /identification` | Duplicate statutory PAN & Aadhaar numbers accepted across employees | `EmployeeIdentificationServiceImpl.java` | **RESOLVED** |
| **BUG-D4-07** | MAJOR | `/employees/:id` | HR role missing `core.reporting_line.manage`; "Set Manager" button hidden | `V012__seed_system_roles.sql` / `V151` | **RESOLVED** |
| **BUG-D4-08** | CRITICAL | `/invitations/accept` | Employee portal invitation link fails with 404 / "Invitation declined" | `InvitationController.java`, `routes.js` | **RESOLVED** |
| **BUG-D4-09** | MAJOR | `/employees/:id` | Employee termination modal missing Reason dropdown and Remarks | `TerminationModal.jsx` / `EmploymentTab.jsx` | **RESOLVED** |
| **BUG-D4-10** | CRITICAL | `/payroll/runs/new` | Pay run modal: "Configure Pay Schedule Settings" link opens 404 | `shell/routes.js` route whitelist | **RESOLVED** |
| **BUG-D4-11** | CRITICAL | `/employees/:id` | Terminated employee cannot be reactivated; action button missing | `EmployeeDetail.jsx` Actions menu | **RESOLVED** |
| **BUG-D4-12** | MINOR | `/employees` | Terminated filter in Employee Directory does not show Last Working Day | `EmployeeList.jsx` dynamic columns | **RESOLVED** |

---

### 10.2 Day 2 Detailed Remediation Summary

- **BUG-D4-01 (Location Column Clipping):** Set `width: 220`, `minWidth: 180`, and `ellipsis: true` on Location Name in `MasterTable.jsx`.
- **BUG-D4-02 (`/roles` 404 Not Found):** Exported `Roles` and `RoleForm` in `core/index.js`; mounted routes `/roles`, `/roles/new`, `/roles/:id` in `shell/routes.js`.
- **BUG-D4-03 (Role Select Overlay Auto-Close):** Configured `<Select>` in `UserInvitations.jsx` with `open={roleOpen}` and explicit dismissal on selection.
- **BUG-D4-04 (Finance Designations System Error):** Ensured `DesignationServiceImpl.java` handles read queries safely under `core.org.read` for non-admin roles without throwing NPE.
- **BUG-D4-05A (Duplicate Employee Email):** Added unique tenant check in `EmployeeServiceImpl.java` returning HTTP 409 Conflict.
- **BUG-D4-05B (Employee List Table Wrapping):** Configured `width: 120` on `employeeNumber` and `minWidth: 200` with `whiteSpace: 'nowrap'` in `EmployeeList.jsx`.
- **BUG-D4-06 (Duplicate PAN & Aadhaar):** Added duplicate checks in `EmployeeIdentificationServiceImpl.java` rejecting duplicates with HTTP 409.
- **BUG-D4-07 (HR Set Manager Permission):** Added migration `V151__grant_reporting_line_manage_to_hr.sql` granting `core.reporting_line.manage` to role `hr`.
- **BUG-D4-08 (Employee Invitation Accept 404):** Made `GET /api/v1/invitations/accept` read-only and cached token in `sessionStorage` preventing premature decline.
- **BUG-D4-09 (Termination Reason & Remarks):** Added `<Select name="reason">` and `<Input.TextArea name="remarks">` to `TerminationModal.jsx`.
- **BUG-D4-10 (Pay Schedule Link 404):** Whitelisted `/payroll/settings` route prefix in `shell/routes.js`.
- **BUG-D4-11 (Reactivate Terminated Employee):** Added "Reactivate Employee" action item in `EmployeeDetail.jsx` triggering `ReactivateModal` calling `PUT /api/v1/employees/{id}/reactivate`.
- **BUG-D4-12 (Terminated Filter Last Working Day Column):** Conditionally rendered `Last Working Day` column in `EmployeeList.jsx` when filtering by `TERMINATED`.

---

## Related

- Every ticket and its features: [../target-state/08-work-plan.md](../target-state/08-work-plan.md)
- What to build first: [../target-state/09-build-order.md](../target-state/09-build-order.md)
- Tables: [../target-state/02-data-model.md](../target-state/02-data-model.md)
- Rules new code must follow: [../CONVENTIONS.md](../CONVENTIONS.md)
- Other trackers: [INFRA-TRACKER.md](INFRA-TRACKER.md) · [HARNESS-TRACKER.md](HARNESS-TRACKER.md) · [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md)

## 11. Devashish (Dev 3) — Day 1 manual QA defects (`dev-devashish`, 2026-10-07)

> Logged on `dev-devashish` against the Azure dev ingress on 2026-10-06/07; merged to `main` on 2026-10-13 with `D-14` and `D-24` fixed. Final numbers (founder, 2026-10-13) are in the first column and in the defect table above; the branch's own numbers are kept here for the QA plan's references.

| Defect | What | Found | Fix | Status |
|---|---|---|---|---|
| D-10 (QA) → **D-19** | Work Locations table: "Name" column text is vertically clipped/misaligned across lines (`S1-05`) | 2026-10-06, manual QA | frontend CSS (`/org/work-locations`) | **open** |
| D-11 → **D-20** | `/roles` page displays HTTP 404 "Sorry, the page you visited does not exist" (`S1-08`, `S17-01`) | 2026-10-06, manual QA | feature deployment (`/roles`) | fixed on `main` as `D-73` (Roles screen) — his `RoleMatrixScreen` dropped at merge |
| D-12 → **D-21** | Invite User modal: Role select dropdown does not automatically close after an option is selected (`S2-01`) | 2026-10-06, manual QA | frontend select component (`/invitations/users`) | **open** |
| D-13 → **D-22** | User Invitations: Revoked invitations display empty Actions column without delete/purge action (`S2-10`) | 2026-10-06, manual QA | frontend / core API (`/invitations/users`) | **open** |
| D-14 → **D-23** | Finance role RBAC: User assigned `finance` only sees `Organisation`; Claims, Deductions, and Approvals are missing (`S2-06`) | 2026-10-06, manual QA | RBAC role_action & dynamic nav feed | **fixed on main 2026-10-13** — `V174` grants finance `core.approval.decide` (his `V149` replaced: it re-created `seed_system_roles` from a pre-`V158` snapshot and was out of order) |
| D-15 → **D-24** | Employee creation allows duplicate employee records with identical Work Email and Mobile Number (`S3-02`) | 2026-10-06, manual QA | `core.employee` uniqueness validation | fixed on `main` as Krushna `BUG-D4-14` (duplicate work email) |
| D-16 → **D-25** | Employee Reporting Line tab: "Set Manager" button not visible when logged in as HR (`S3-05`) | 2026-10-06, manual QA | `core.reporting_line.manage` restricted to `tenant-admin` | fixed on `main` as Krushna `BUG-D4-19` (`V172`) |
| D-17 → **D-26** | User Management: Missing "Edit Roles" action to modify or assign additional roles to active users (`S3-10`) | 2026-10-06, manual QA | frontend user management (`/users`) | **open (P2)** |
| D-18 → **D-27** | Employee Invitations: Accepting email invitation link fails with HTTP 404 or "Try again later" error (`S3-11`, `S3-12`) | 2026-10-06, manual QA | token verification & AFD routing (`/invitations/accept`) | fixed on `main` `b6a798f1` / `D-88` |
| D-19 → **D-28** | Employee Exit: Termination modal only prompts for Last Working Day; "Reason" and "Remarks" fields are missing (`S14-03`) | 2026-10-06, manual QA | frontend termination drawer (`/employees/:id`) | fixed on `main` as Krushna `BUG-D4-20` (`V173`) |
| D-20 → **D-29** | Payroll Officer sidebar: "Pay Runs" menu item (`/payroll/runs`) is not visible in navigation (`S14-05`) | 2026-10-06, manual QA | `nav.payroll.runs` navigation permissions | **open (P1)** |
| D-21 → **D-30** | Employee Directory: Filtering by `TERMINATED` status displays Date of Joining but omits Last Working Day (`S14-07`) | 2026-10-06, manual QA | employee directory columns (`/employees`) | **open (P3)** |
| D-22 → **D-31** | Employee Exit: Terminated employee profile lacks a "Reactivate Employee" button to retract exit (`S14-08`) | 2026-10-06, manual QA | frontend actions menu (`/employees/:id`) | fixed on `main` as Krushna `BUG-D4-21` |
| D-23 → **D-32** | Custom Roles: Cannot assign custom role `leave-auditor` in User Invitations because `/roles` is not deployed (`S17-04`) | 2026-10-06, manual QA | custom role engine (`/roles`) | fixed on `main` as `D-73` |
| D-24 → **D-94** | Route Mismatch: `/payroll/settings/tax-declaration/2026` returns HTTP 404 in React Router; live route is `/payroll/settings/tax-declaration` (`S9-01`) | 2026-10-06, manual QA | frontend router (`src/core/routes.js`) | **fixed on main 2026-10-13** — `/payroll/settings/tax-declaration/:year` route, year normalised (`TaxWindowScreen`) |
| D-25 → **D-95** | Proof of Investment: Receipt upload dropzone and verification queue route `/payroll/tax-declarations/review` unmounted / missing (`S16-01`..`S16-07`) | 2026-10-06, manual QA | frontend declaration & POI review routes | **open** — proof-of-investment screens held back at merge: no spec (W-34 defers screens to W-47), review route never mounted, receipt links carried no token, item statuses did not match the backend; founder writes the `W-47` proof-screens spec |

---

## 12. Krushna (Dev 4) — Active Defect Remediation: Bulk Leave Import Sample Template

> **Test Lead / Owner:** Krushna (Dev 4) — HRMS Core Track  
> **Source:** QA Leave Import Flow (`S20-02`) & HR / Manager Certification  
> **Implementation Plan:** `bulk_leave_import_sample_download_plan.md`  
> **Status:** **RESOLVED**

### 12.1 Defect Details & Remediation Matrix

| Defect ID | Severity | Module / Screen | Summary Description | Root Cause Component | Proposed Remediation | Status |
|---|:---:|---|---|---|---|:---:|
| **D-50** | MEDIUM | `/leave/import` (`LeaveImport.jsx`) | Missing "Download Sample CSV Template" button/option on Bulk Leave Import page. HR/Manager needs a pre-formatted template with 1 sample record to understand the exact structure before importing. | Frontend lacked template download action; backend lacked dedicated sample template export endpoint. | Added `GET /api/v1/leave-imports/template` endpoint in `LeaveImportController`, `downloadTemplate()` in `leaveImportService.js`, and mounted "Download Sample Template" button in `LeaveImport.jsx` header. Preserves all existing functionality (dry run, live import, history table). | **RESOLVED** |

### 12.2 Engineering Remediation Details
- **Backend Service:** Implemented `getSampleTemplate(UUID tenantId)` in `LeaveImportServiceImpl.java` and `LeaveImportService.java`. Dynamically pulls active leave type codes for the tenant (or defaults to `SL`, `CL`), returning canonical CSV with `employee_number,leave_type_code,days` header and sample rows.
- **Backend API:** Mounted `GET /api/v1/leave-imports/template` in `LeaveImportController.java` with `@RequiresAction("core.leave_balance.manage")`, returning `text/csv` with `attachment; filename=leave_import_sample_template.csv`.
- **Frontend Service:** Added `downloadTemplate()` in `leaveImportService.js` calling `/v1/leave-imports/template`.
- **Frontend UI:** Added "Download Sample Template" button in `LeaveImport.jsx` header (`Card` extra prop) with download handler generating file attachment via Blob, maintaining 100% of existing upload, dry run, and import behavior.
- **Verification & Tests:**
  - `LeaveImportServiceTest.java`: Added `getSampleTemplateReturnsStandardCsv` and `getSampleTemplateUsesTenantActiveLeaveTypes`.
  - `LeaveImportGuardIT.java`: Verified `GET /api/v1/leave-imports/template` returns 403 Forbidden for employee without `core.leave_balance.manage`, and 200 OK with `text/csv` attachment for HR.
  - `LeaveImport.test.jsx`: Added unit test asserting "Download Sample Template" button renders and triggers file download.
  - `leaveImportService.test.js`: Added unit test verifying `downloadTemplate()` endpoint call.

---

## 13. Defect D-102 — Payslip Data & Layout Parity with Legacy

| Field | Value |
|---|---|
| **Defect ID** | `D-102` |
| **Found** | 2026-10-22, manual QA / founder |
| **Module / Screen** | `/me/payslips/:payrunId`, `/public/payslips` (PublicPayslip), backend `PayslipResponse` |
| **Severity** | MAJOR |
| **Description** | Payslip view and printed/downloaded PDF do not match legacy data structure and layout. Missing fields in Employee Summary: Employee Status, Date of Joining, PF Number, UAN Number, LWP Days, Net Pay highlight card, separate Earnings vs Deductions layout with Gross/Total subtotals, Claims line, Net Payable breakdown, Amount In Words, and system-generated signature notice. |
| **Root Cause** | 1. `PayslipResponse.EmployeeSummary` omitted `status`, `pf_number`, and `uan_number`, and `PayslipServiceImpl` did not query `EmployeeStatutoryProfileRepository` to enrich them. 2. `PublicPayslip.jsx` and `PayslipPage.jsx` did not render the legacy two-column Employee Summary, Net Pay highlight card, Amount in Words, and system disclaimer. |
| **Resolution** | 1. Enriched `PayslipResponse.EmployeeSummary` with `status`, `pf_number`, and `uan_number`. 2. Updated `PayslipServiceImpl` to query `EmployeeStatutoryProfileRepository` for PF and UAN numbers and extract employee status. 3. Created `PayslipDocument.jsx` and updated `PublicPayslip.jsx` and `PayslipPage.jsx` to render the legacy-matching layout including Net Pay highlight, Amount in Words (Indian numbering system), LWP Days, PF/UAN details, separate earnings/deductions subtotals, and system signature notice. |
| **Status** | **fixed on main** (verified with `mvn test-compile`, `npm run lint`, and `npm run build`) |

---

## 14. Defect D-103 — Frontend Dependency Vulnerabilities in Trivy Scan (CVE-2026-111755, CVE-2026-111756, CVE-2026-112942)

| Field | Value |
|---|---|
| **Defect ID** | `D-103` |
| **Found** | 2026-10-22, CI Trivy security scan (`trivy fs --severity HIGH,CRITICAL code/`) |
| **Module / Screen** | `code/frontend/package-lock.json` (`follow-redirects`, `vite`) |
| **Severity** | HIGH |
| **Description** | Trivy security scan failed with exit code 1 on `frontend/package-lock.json` detecting 3 HIGH vulnerabilities: `follow-redirects@1.16.0` (CVE-2026-111755, CVE-2026-111756 info disclosure via regex escaping / array headers) and `vite@8.3.0` (CVE-2026-112942 command injection via launch-editor file path validation). |
| **Root Cause** | Transitive `follow-redirects` pinned to 1.16.0 by axios, and devDependency `vite` pinned to 8.3.0 in lockfile. |
| **Resolution** | Added `"follow-redirects": "^1.16.1"` and `"vite": "^8.3.3"` to `overrides` in `code/frontend/package.json`. Updated `devDependencies.vite` to `^8.3.3`. Added `@rolldown/binding-win32-x64-msvc` to `optionalDependencies`. Ran `npm install` to update `package-lock.json`. Verified `npm run lint` and `npm run build` pass with 0 errors. |
| **Status** | **fixed on main** (verified `follow-redirects@1.16.1`, `vite@8.3.5`, `npm run build`, `npm run lint`) |

---

## 15. Defect D-104 — Attendance Clock-Out Deadlock on Expired Sessions (>24h or Previous Date)

| Field | Value |
|---|---|
| **Defect ID** | `D-104` |
| **Found** | 2026-10-22, manual QA / founder (`POST /api/v1/hrms/attendance/clock-out` returned 409 Conflict) |
| **Module / Screen** | `/hrms/dashboard`, `/me` (Attendance Card), `ClockServiceImpl.java`, `ClockCard.jsx` |
| **Severity** | MAJOR |
| **Description** | When an employee forgets to clock out and attempts to clock out after 24 hours (or on a subsequent day), the backend correctly refuses clock-out with HTTP 409 Conflict ("Session cannot be clocked out more than 24 hours after clock in") per W-40.3 rules. However, the frontend `ClockCard.jsx` showed only a "Clock out" button whenever `today.openSession` was non-null. The employee was trapped in a deadlock with no UI action to clock in or start a new session. |
| **Root Cause** | 1. Frontend `ClockCard.jsx` conditioned the primary action button solely on `open` session existence without checking if the session had expired (>24 hours or previous calendar date). When stale, `act()` exclusively called `attendanceService.clockOut()`.<br>2. Backend `ClockServiceImpl.clockIn()` checked `openSession.getAttendanceDate().equals(today)` before throwing 409, but did not explicitly guard against edge cases where duration > 24 hours. |
| **Resolution** | 1. Backend: In `ClockServiceImpl.clockIn()`, ensure any open session exceeding 24 hours (or from an earlier date) is auto-voided with `VoidReason.NOT_CLOCKED_OUT` and opens the new session.<br>2. Frontend: In `ClockCard.jsx`, detect stale sessions (`isStale = open.attendanceDate < today.date || duration > 24h`). When stale, display a warning Alert explaining that the previous session exceeded 24 hours and must be regularized, and render the primary action as "Clock in" calling `attendanceService.clockIn()` to auto-close the stale session and begin today's work. |
| **Status** | **fixed on main** (verified with backend unit tests and Vitest) |

---
## 16. Defect D-105 — Timesheet Weekly Entry Inline Description Box & Mandatory Validation Parity

| Field | Value |
|---|---|
| **Defect ID** | `D-105` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-23, legacy parity review (`/hrms/timesheets/week/:weekStart`) |
| **Module / Screen** | `code/frontend/src/hrms/timesheet/` (`TimesheetWeek.jsx`, `weekGrid.js`) |
| **Severity** | NORMAL |
| **Description** | In the weekly timesheet grid (`TimesheetWeek.jsx`), the daily work description was implemented as a popover note button with a comment icon (`NoteCell` using `MessageOutlined`), whereas legacy HRMS (`TimesheetForm.jsx:709-735`) provides an inline multiline Description box directly inside each day cell. Furthermore, legacy strictly enforces that whenever hours are entered (`hour > 0`), the description field is mandatory (`TimesheetForm.jsx:251-256`); in the target-state code, description remained completely optional with no validation. |
| **Root Cause** | 1. `TimesheetWeek.jsx` used a popover `NoteCell` with `MessageOutlined` instead of an inline `Input.TextArea` description box.<br>2. `weekGrid.js` `validate()` checked hours range and daily 24h limit, but lacked validation requiring non-empty `cell.description` when `hours > 0`. |
| **Resolution** | 1. In `TimesheetWeek.jsx`, removed `NoteCell` and rendered an inline `Input.TextArea` (`placeholder="Description"`, rows=2, max 500 chars) stacked under the hours `InputNumber` in each day cell, matching the legacy layout. Shows description text in read-only mode.<br>2. In `weekGrid.js`, updated `validate()` to enforce that when `hours > 0`, `cell.description` must be non-empty (trimmed); otherwise sets `cellErrors` with `status="error"` and blocks save/submit. When hours are 0 or blank, description is allowed to be empty.<br>3. Updated `weekGrid.test.js` and `TimesheetWeek.test.jsx` with full unit and component test coverage. Verified with `npm test -- src/hrms/timesheet` and `npm run lint`. |
| **Status** | **fixed on main** (verified with unit and component tests in `code/frontend`, 0 lint warnings) |

---

## 17. Feature W-74 — Work From Home (WFH) Module

| Field | Value |
|---|---|
| **Ticket ID** | `W-74` |
| **Completed** | 2026-10-23, built by krushna (`W-74-work-from-home`) |
| **Module / Screen** | `core.wfh`, Flyway `V179`–`V184`, Self-Service Portal (`/me` tab 3, `/me/wfh/apply`), Management (`/wfh/policies`, `/wfh/allocations`, `/wfh/requests`, `/wfh/import`) |
| **Category** | Feature (First-class subsystem, Option 2) |
| **Description** | Work From Home (WFH) implemented as an independent core subsystem adhering to target specification in `docs/target-state/features/W-74-work-from-home.md`. |
| **Implementation** | 1. **Database Migrations (`V179`–`V184`)**: Dedicated tables with PostgreSQL RLS & tenant isolation: `core.wfh_policy` (`V179`), `core.wfh_allocation` (`V180`), `core.wfh_request` (`V181`), `core.wfh_consumption` (`V182`), `core.wfh_import_log` (`V183`), and `V184` seeding permissions & `WORK_FROM_HOME` approval definition.<br>2. **Backend Domain & Services**: Full entities, repositories, and services in `com.infinevo.core.wfh`. Enforces annual quota (12 days default), monthly velocity limit (2 days/month), and mutual exclusion against overlapping leave or active WFH bookings.<br>3. **Zero-LOP Attendance Integration**: `WfhApprovalOutcomeHandler` automatically creates `Attendance` records marked `PRESENT` (or `HALF_DAY`), source `ADMIN`, with remark `"Work From Home"`. No LOP deductions are generated.<br>4. **Frontend Self-Service & Admin Screens**: Portal panel (`MyWfh.jsx`), apply modal (`ApplyWfh.jsx`), admin policies (`WfhPolicies.jsx`), allocations (`WfhAllocations.jsx`), requests with on-behalf apply (`WfhRequests.jsx`), and bulk CSV import (`WfhImport.jsx`). |
| **Status** | **complete on branch `W-74-work-from-home`** (verified with 23 backend unit tests, `mvnw spotless:check`, `npm run lint`, and `npm run build`) |

---

## 18. Defect D-106 — Organisation Master Navigation Exposure (Departments, Designations, Work Locations)

| Field | Value |
|---|---|
| **Defect ID** | `D-106` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-23, employee login access review (`/org/departments`) |
| **Module / Screen** | `code/backend/core/src/main/java/com/infinevo/core/navigation/` (`NavigationCatalogue.java`), `code/backend/core/src/main/java/com/infinevo/core/org/` (`DepartmentController.java`, `DesignationController.java`, `WorkLocationController.java`) |
| **Severity** | HIGH |
| **Description** | In the sidebar navigation catalogue, administrative master screens (`Departments`, `Designations`, `Work locations`) were visible to employees and other non-admin roles because the menu items were gated by `core.org.read` instead of `core.org.manage`. In legacy, the master catalogue is strictly accessible only to Admin and HR. |
| **Root Cause** | To allow an employee's personal profile (`/me/profile`) to display readable department/designation names instead of raw UUIDs, the frontend resolves lookup names via `GET /api/v1/departments`, `/designations`, and `/work-locations`, which required `core.org.read`. Because `employee` was granted `core.org.read`, `NavigationCatalogue.java` mistakenly used `core.org.read` as the visibility requirement for the master management screens, exposing them in the employee sidebar. |
| **Resolution** | 1. In `NavigationCatalogue.java`, updated `core.org.departments`, `core.org.designations`, and `core.org.locations` `requiredAction` from `"core.org.read"` to `"core.org.manage"`. Only Admin and HR (holding `core.org.manage`) receive these screens in their navigation feed.<br>2. In `DepartmentController.java`, `DesignationController.java`, and `WorkLocationController.java`, updated the `list` endpoint `@RequiresAction` to `value = "core.org.manage", anyOf = "core.org.read"`, protecting catalogue endpoints while allowing background self-service lookups (e.g. employee profile) to resolve names.<br>3. In `NavigationServiceTest.java`, added unit test `orgMastersHiddenFromReadCallerAndVisibleToManager` verifying that `core.org.read` callers cannot see the master screens and `core.org.manage` callers can. Passed all unit tests and Spotless check. |
| **Status** | **fixed on main** (verified with backend unit tests `NavigationServiceTest`, `NavigationCatalogueValidatorTest`, `mvn spotless:check`, `npm run lint`, and `npm run build`) |

---

## 19. Defect D-107 — WFH Module UI Screen Layout Collapse & Leave Module Parity

| Field | Value |
|---|---|
| **Defect ID** | `D-107` |
| **Owner** | krushna (`dev-krushna`) |
| **Found** | 2026-10-23, manual QA / user live testing (`/admin/wfh/*`, `/me/wfh`) |
| **Module / Screen** | `code/frontend/src/core/wfh/` (`WfhPolicies.jsx`, `WfhAllocations.jsx`, `WfhRequests.jsx`, `WfhImport.jsx`), `code/frontend/src/shell/portal/panels/` (`MyWfh.jsx`) |
| **Severity** | MAJOR |
| **Description** | Across all WFH screens in admin console and employee self-service portal, text elements wrap vertically character-by-character into narrow 50px–80px columns (e.g. `W-F-H P-o-l-i-c-i-e-s`, `W-o-r-k F-r-o-m H-o-m-e`, `D-r-y R-u-n`), buttons are squashed underneath, and table cards are pushed horizontally to the far right. WFH screens deviate from established Leave module layout standards (`LeaveTypes`, `Allocations`, `LeaveRequests`, `LeaveImport`, `MyLeave`). |
| **Root Cause** | Invalid Ant Design prop `<Space orientation="vertical">` used in place of `<Space direction="vertical">`. Ant Design ignored `orientation` and fell back to default `direction="horizontal"`, collapsing the main view into an inline-flex horizontal row and squeezing the header column to 50px–80px width. |
| **Resolution** | 1. Replace all invalid `orientation="vertical"` with `direction="vertical"` across all 5 WFH components.<br>2. Align all admin screens with the Leave module architecture: full-width `<Card style={{ margin: 24 }}>`, header `<Row justify="space-between" align="middle">`, toolbar filters, and full-width responsive data tables.<br>3. Refactor Employee Portal `MyWfh.jsx` to mirror `MyLeave.jsx` with 4-metric quota card and full-width history table.<br>4. Run full frontend unit test suite and verify 0 visual regressions without committing until confirmed. |
| **Status** | **fixed on main `c5b9bd1f`** (verified with Vitest `WfhAdminScreens.test.jsx`, `MyWfh.test.jsx`, `ApplyWfh.test.jsx`, ESLint, Vite build, and GitHub Actions CI run #40294767068 100% green) |

---

## 20. Defect D-108 — Post-Logout Landing on Unauthorized Route / Login Return Path

| Field | Value |
|---|---|
| **Defect ID** | `D-108` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-26, login/logout session role switching (`/approvals`, etc.) |
| **Module / Screen** | `code/frontend/src/shell/auth/` (`keycloak.js`), `code/frontend/src/shell/` (`AppShell.jsx`, `screens/NotFound.jsx`) |
| **Severity** | HIGH |
| **Description** | When logging out from an admin or privileged screen (e.g., `/approvals`, `/payroll/runs`, `/admin/*`) and logging in as an employee or a role without access to that screen, the user lands back on that unauthorized path instead of their role-specific home page (`/me` for Employee, `/hrms/dashboard` for HR). For an employee with an empty navigation feed, landing on `/approvals` rendered `<NoModules />` ("No modules available") rather than redirecting to `/me`, leaving the user stranded. |
| **Root Cause** | 1. In `keycloak.js`, `logout()` invoked `keycloak.logout()` without arguments. `keycloak-js` defaults `redirectUri` to `window.location.href`, meaning Keycloak redirected post-logout to the previous page (e.g. `/approvals`), and subsequently sent the newly logged-in user back to that exact URL.<br>2. In `AppShell.jsx`, the empty feed branch rendered `<NoModules />` whenever `location.pathname` was not `/` and did not start with `/me`, stranding an employee who landed on `/approvals` instead of redirecting them to their `feedHomePath` (`/me`).<br>3. `NotFound.jsx` lacked a "Back to Home" action button to return unentitled or unmatched users back to their active home path. |
| **Resolution** | 1. In `keycloak.js`, updated `logout(options = {})` to provide `redirectUri: options.redirectUri ?? (typeof window !== 'undefined' && window.location?.origin ? `${window.location.origin}/` : '/')`, ensuring that sign-out always returns the browser to the app root (`/`). When any user logs in again, they land at `/`, which `AppShell` immediately routes to their designated `homePath` via `RootRedirect`.<br>2. In `AppShell.jsx`, updated the empty feed branch so that if `feedHomePath && feedHomePath.startsWith('/me')`, any visit outside `/me` falls back to `<RootRedirect homePath={feedHomePath} me={me} />` instead of showing `<NoModules />`.<br>3. In `NotFound.jsx`, added a "Back to Home" button directing to `homePath` so that any user who directly opens a route not in their feed can cleanly navigate back to their dashboard.<br>4. Added comprehensive unit tests in `keycloak.test.js` and `AppShell.test.jsx`. Verified with 144 passing frontend shell tests, 0 lint warnings, and a green Vite build. |
| **Status** | **fixed on main `1ac99343`** (verified with Vitest `keycloak.test.js`, `AppShell.test.jsx`, `Header.test.jsx`, `npm run lint`, and `npm run build`) |

---

## 21. Defect D-109 — Employee Document & Identification Fields Free-Text to Dropdown Parity

| Field | Value |
|---|---|
| **Defect ID** | `D-109` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-26, employee document parity review (`/employees/:id` Identification Details) |
| **Module / Screen** | `code/frontend/src/core/employee/` (`sectionFields.js`), `code/frontend/src/core/employee/tabs/` (`SectionTab.jsx`, `SectionTab.test.jsx`) |
| **Severity** | NORMAL |
| **Description** | In the Employee Profile Identification Details section (`/employees/:id`), key document identification fields (`idProofType`, `addressProofType`, and `immigrationStatus`) were rendered as unstructured free-text input boxes (`type: 'text'`). Administrators and HR were forced to manually type strings (e.g. "Passport", "Aadhaar Card", "Citizen"), leading to typographical inconsistencies and differing from legacy HRMS where these fields were strictly predefined `<Select>` dropdown menus. |
| **Root Cause** | In `sectionFields.js:86-102`, `identification` section fields configured `idProofType`, `addressProofType`, and `immigrationStatus` with `type: 'text'` instead of `type: 'select'` and did not define standard option arrays. |
| **Resolution** | 1. In `sectionFields.js`, added exported option constants matching legacy HRMS and Indian statutory standards:<br>&nbsp;&nbsp;• `IMMIGRATION_STATUS_OPTIONS`: Citizen, Permanent Resident, Work Visa / Permit, Student Visa, Other.<br>&nbsp;&nbsp;• `ID_PROOF_TYPE_OPTIONS`: Aadhaar Card, PAN Card, Passport, Driving Licence, Voter ID, Other.<br>&nbsp;&nbsp;• `ADDRESS_PROOF_TYPE_OPTIONS`: Aadhaar Card, Passport, Driving Licence, Electricity / Utility Bill, Rent Agreement, Bank Statement / Passbook, Voter ID, Other.<br>2. Updated `identification` section definition in `sectionFields.js` to set `type: 'select'` and wire the options arrays for `immigrationStatus`, `idProofType`, and `addressProofType`.<br>3. In `SectionTab.jsx`, enhanced `Select` rendering with `showSearch` and `optionFilterProp="label"` for quick keyboard filtering.<br>4. In `SectionTab.test.jsx`, added unit test coverage verifying that Identification document fields render as searchable Select dropdowns with standard options. Verified with 14 passing unit tests, 0 lint warnings, and a green Vite build. |
| **Status** | **fixed on main `b1296254`** (verified with Vitest `SectionTab.test.jsx`, `npm run lint`, and `npm run build`) |

---

## 22. Defect D-110 — Employee Dashboard Landing Page & Top-Level Navigation Hierarchy

| Field | Value |
|---|---|
| **Defect ID** | `D-110` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-26, employee dashboard & landing page review (`/hrms/dashboard`, `/me`) |
| **Module / Screen** | `code/backend/core/` (`NavigationCatalogue.java`, `NavigationService.java`), `code/backend/hrms/` (`HrmsNavigation.java`), `code/frontend/src/shell/navigation/` (`navLabels.js`) |
| **Severity** | NORMAL |
| **Description** | 1. In the sidebar menu, the HRMS Dashboard was labeled `"HR dashboard"`, giving employees the incorrect impression that it is an administrative HR-only screen, even though the page itself is titled `"Dashboard"` and displays personal employee widgets (Today clock card, My Timesheets, My Projects, My Tasks).<br>2. The dashboard was buried inside the collapsible `HRMS` module folder (item 8 in list) rather than positioned prominently at the top level.<br>3. When logging in as an employee, the user landed on `/me` (My self-service) rather than the Dashboard, contrary to tester expectations.<br>4. Tester requested that Holidays (`core.holiday`) be placed under `HRMS` only (`"📄 Holidays - put this under hrms only"`) rather than under `Organisation`. Placing Holidays under `Organisation` had caused an unnecessary expandable `Organisation` menu folder containing only "Holidays" to appear for regular employees, while all other Organisation screens (Departments, Designations, Work locations, Roles) are admin-restricted. |
| **Root Cause** | 1. `navLabels.js:23` defined `'nav.hrms.dashboard': 'HR dashboard'` instead of `'Dashboard'`.<br>2. `HrmsNavigation.java` placed `DASHBOARD` inside `LEAVES` under the `hrms` group dropdown rather than contributing it as a top-level item ahead of the group.<br>3. `NavigationCatalogue.withContributed` appended all contributed items after `DEFAULT_ITEMS`, which prevented module dashboards from preceding `/me` (`core.me`).<br>4. `NavigationService.homePath()` only routed to `HRMS_HOME` if `readsAll` (HR) held, falling back to `/me` for regular employees.<br>5. `NavigationCatalogue.java` had `core.holiday` configured under `core.org` ("Organisation") instead of under `HRMS`. |
| **Resolution** | 1. Renamed `'nav.hrms.dashboard'` in `navLabels.js` from `'HR dashboard'` to `'Dashboard'`.<br>2. Updated `HrmsNavigation.java` to remove `DASHBOARD` from `LEAVES` and contribute `List.of(DASHBOARD, GROUP)`, extracting the dashboard out of the dropdown folder into a standalone top-level item.<br>3. Updated `NavigationCatalogue.withContributed` to elevate `hrms.dashboard` to index 0, placing **Dashboard** at the very top of the navigation menu, with **My self-service** (`/me`) positioned immediately below it.<br>4. Updated `NavigationService.homePath` so that when `visible.contains(HRMS_HOME)`, employees automatically land on `/hrms/dashboard`, while preserving the fallback to `/me` when HRMS is unentitled.<br>5. Relocated `Holidays` under `HRMS` only: removed `core.holiday` from `core.org` in `NavigationCatalogue.java` and added `HOLIDAYS` to `HrmsNavigation.LEAVES` (`/holidays` -> `/api/v1/holiday-calendars` guarded by `core.holiday.read`). Now the `Organisation` folder disappears completely for regular employees, and Holidays is accessible under HRMS.<br>6. Updated `HrmsNavigationTest.java`, `NavigationServiceTest.java`, and `SeededRoleHomePathTest.java` to verify the new menu order, holidays under HRMS, and employee dashboard landing page. Verified with clean maven builds, 0 spotless issues, 144 green frontend shell tests, 0 lint warnings, and a green Vite build. |
| **Status** | **fixed on main `a82fbffd`** (verified with JUnit `HrmsNavigationTest`, `NavigationServiceTest`, `SeededRoleHomePathTest`, Vitest `navLabels.test.js`, ESLint, and Vite build) |

---

## 23. Defect D-111 — Employee Form Personal & Contact Fields Free-Text to Dropdown Parity

| Field | Value |
|---|---|
| **Defect ID** | `D-111` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-27, employee form legacy parity review (`/employees/:id` Personal & Contact tabs) |
| **Module / Screen** | `code/frontend/src/core/employee/` (`sectionFields.js`), `code/frontend/src/core/employee/tabs/` (`SectionTab.jsx`, `SectionTab.test.jsx`) |
| **Severity** | NORMAL |
| **Description** | In the Employee Profile Personal Details (`/employees/:id` Personal) and Contact Details (`/employees/:id` Contact) sections, key fields (`nationality`, `state`, `permanentState`, `emergencyContactRelationship`, and `secondaryEmergencyContactRelationship`) were rendered as free-text input boxes (`type: 'text'`). This led to casing inconsistencies and typographical mistakes (e.g. "indian" vs "Indian", "MH" vs "Maharashtra"), differing from legacy HRMS and industry standards where these fields are predefined `<Select>` dropdowns. |
| **Root Cause** | In `sectionFields.js`, `nationality` in `personal` and `state`, `permanentState`, `emergencyContactRelationship`, `secondaryEmergencyContactRelationship` in `contact` were typed as `'text'` without dropdown option configurations. |
| **Resolution** | 1. In `sectionFields.js`, added exported option constants matching Indian HCM and statutory standards:<br>&nbsp;&nbsp;• `NATIONALITY_OPTIONS`: Indian, American, Australian, British, Canadian, Chinese, Emirati, French, German, Japanese, Malaysian, Singaporean, Other.<br>&nbsp;&nbsp;• `INDIAN_STATE_OPTIONS`: 28 Indian States, 8 Union Territories, and Other.<br>&nbsp;&nbsp;• `STATE_CODE_MAP`: 2-letter standard state code mapping (e.g. Maharashtra &rarr; MH, Karnataka &rarr; KA).<br>&nbsp;&nbsp;• `RELATIONSHIP_OPTIONS`: Spouse, Father, Mother, Son, Daughter, Brother, Sister, Guardian, Friend, Colleague, Other.<br>2. Updated `personal` and `contact` section configurations in `sectionFields.js` to set `type: 'select'` and bind the respective option arrays.<br>3. Updated `SectionTab.jsx`'s `handleChange` handler to automatically derive and populate `stateCode` and `permanentStateCode` when a state is selected.<br>4. Updated and expanded unit tests in `SectionTab.test.jsx` (16 passing tests, 110 employee suite tests passing). Verified 0 ESLint errors and clean Vite build. |
| **Status** | **fixed on main `0da553d0`** (verified with Vitest `SectionTab.test.jsx`, `npm run lint`, and `npm run build`) |

---

## 24. Defect D-112 — Timesheet Approval Approver Cannot See Daily Work Descriptions

| Field | Value |
|---|---|
| **Defect ID** | `D-112` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-27, timesheet approval defect review (Item #9: "The approver can't see the employee's timesheet descriptions") |
| **Module / Screen** | `code/frontend/src/hrms/timesheetreview/` (`reviewGrid.js`, `WeekView.jsx`, `EntryView.jsx`, `reviewGrid.test.js`, `WeekView.test.jsx`, `EntryView.test.jsx`) |
| **Severity** | NORMAL |
| **Description** | In the Timesheet Review screen (`/hrms/timesheet-review/:id`) and Approvals Inbox Timesheet Entry screen (`/hrms/timesheet-review/entries/:entryId`), approvers could only see numbers representing hours worked (e.g. `8.00`, `7.25`), while the detailed work descriptions entered by the employee for each day (`day.description`) were completely hidden. An approver making approve/reject decisions could not verify what work was actually completed. |
| **Root Cause** | Although the backend endpoints (`TimesheetResponse.DayEntryResponse`) reliably populate and return `description` on every day entry, the frontend review helper `taskRows` in `reviewGrid.js` discarded `d.description`, storing only hundredths of hours in `cells[d.date]`. Furthermore, `gridColumns` only rendered `formatHundredths(row.cells[date])` and had no rendering logic for descriptions. Neither `WeekView.jsx` nor `EntryView.jsx` displayed descriptions. |
| **Resolution** | 1. In `reviewGrid.js`, updated `taskRows` to extract and preserve `descriptions[d.date]` alongside `cells[d.date]`.<br>2. In `reviewGrid.js`, updated `gridColumns` day cell renderers to display the daily work description directly below the hours in `Typography.Text` (secondary 12px with `title` tooltip and text wrapping). When no description exists, it cleanly outputs the formatted hours string.<br>3. Added `collectDescriptions(rows, dates)` helper in `reviewGrid.js` to aggregate daily work descriptions.<br>4. In `WeekView.jsx` and `EntryView.jsx`, added an AntD `Collapse` component displaying a "Work descriptions (N)" summary card open by default when daily descriptions are present, listing dates, tasks, hours, and full formatted description text.<br>5. Added comprehensive test coverage: `reviewGrid.test.js` (5 unit tests), `WeekView.test.jsx` (2 unit tests), and updated `EntryView.test.jsx` (2 unit tests) — all 18 suite tests passing. Verified 0 ESLint warnings and clean Vite build. |
| **Status** | **fixed on main `f7cf7b51`** (verified with Vitest `src/hrms/timesheetreview`, `npm run lint`, and `npm run build`) |

---

## 25. Defect D-113 — Timesheet Approval Missing Inline Approve & Reject Buttons

| Field | Value |
|---|---|
| **Defect ID** | `D-113` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-27, timesheet approval defect review (Item #10: "No Approve or Reject on the timesheet screen; only on the Approvals inbox list") |
| **Module / Screen** | `code/frontend/src/hrms/timesheetreview/` (`EntryView.jsx`, `WeekView.jsx`, `EntryView.test.jsx`) |
| **Severity** | NORMAL |
| **Description** | When an approver navigated to an individual project timesheet entry screen (`/hrms/timesheet-review/entries/:entryId`) from the Approvals Inbox ("View item") or from Timesheet Review, the screen displayed the hours grid, employee details, and daily task descriptions, but did not offer any "Approve" or "Reject" buttons. The approver was forced to click "Back to approvals" and take action in the bulk inbox list without being able to approve directly while inspecting the detailed breakdown. |
| **Root Cause** | In `W-48.3 §5`, the entry review view was documented as read-only with the note *"Out of scope: the inbox decides; the entry page links back to it"*. The view did not resolve the pending `ApprovalStepResponse` for the timesheet entry, nor did it render action buttons or the approval decision modal (`DecideModal`). |
| **Resolution** | 1. In `EntryView.jsx`, imported `Button`, `Space`, `CheckOutlined`, `CloseOutlined`, `useLocation`, `useDispatch`, `useCan`, `DecideModal`, `approvalService`, `decrementPendingCount`, and `successMsg`.<br>2. Resolved pending approval step from route navigation state (`location.state?.step`) or queried `approvalService.pending(0, 100)` to match `s.itemId === entryId`.<br>3. When `canDecide`, pending step exists, and entry status is `SUBMITTED`, rendered primary **Approve** (green check icon) and danger **Reject** (close icon) buttons in the Card header `extra` alongside "Back to approvals".<br>4. Integrated `DecideModal` to capture approver comments and submit to `/api/v1/approvals/steps/{stepId}/decide`. On success, immediately updated entry status in place to `APPROVED` or `REJECTED`, dispatched `decrementPendingCount()`, and displayed a success notification.<br>5. In `WeekView.jsx`, made project status tags clickable links pointing to `/hrms/timesheet-review/entries/${p.id}`, enabling managers reviewing a complete week timesheet to seamlessly navigate to the decision view.<br>6. Added comprehensive unit test in `EntryView.test.jsx` verifying that Approve and Reject buttons render and execute decision successfully through `approvalService.decide`. Verified with 19 passing unit tests in `src/hrms/timesheetreview`, 0 ESLint warnings, and clean Vite build. |
| **Status** | **fixed on main `78ce6ed2`** (verified with Vitest `src/hrms/timesheetreview`, `npm run lint`, and `npm run build`) |

---

## 26. Defect D-114 — Overtime Approval Missing Inline Approve & Reject Buttons

| Field | Value |
|---|---|
| **Defect ID** | `D-114` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-27, overtime approval defect review (Item #11: "No Approve or Reject on the overtime screen; only on the Approvals inbox list") |
| **Module / Screen** | `code/frontend/src/hrms/requests/` (`OvertimeDetail.jsx`, `OvertimeDetail.test.jsx`) |
| **Severity** | NORMAL |
| **Description** | When an approver navigated to an overtime request detail screen (`/hrms/overtime-requests/:id`) from the Approvals Inbox ("View item") or directly, the screen displayed the date, hours, amount, source, and remarks, but lacked "Approve" and "Reject" action buttons. The approver had to navigate back to `/approvals` to take action. |
| **Root Cause** | In `W-48.5 §5`, `OvertimeDetail.jsx` was originally created as a read-only view ("One overtime request, read-only..."). It did not query or resolve the associated pending `ApprovalStepResponse` (`flowType: 'OVERTIME'`), nor did it wire the approval decision modal (`DecideModal`). |
| **Resolution** | 1. In `OvertimeDetail.jsx`, imported `Button`, `Space`, `CheckOutlined`, `CloseOutlined`, `useDispatch`, `useCan`, `DecideModal`, `approvalService`, `decrementPendingCount`, and `successMsg`.<br>2. Resolved pending approval step from route navigation state (`location.state?.step`) or queried `approvalService.pending(0, 100)` to locate the active step where `s.itemId === id`.<br>3. When `canDecide`, pending step exists, and request status is `PENDING` or `SUBMITTED`, rendered primary **Approve** (green check icon) and danger **Reject** (close icon) buttons in the Card header `extra` alongside "Back to list" / "Back to approvals".<br>4. Integrated `DecideModal` to capture approver comments and submit to `/api/v1/approvals/steps/{stepId}/decide`. On success, immediately updated overtime status in place to `APPROVED` or `REJECTED`, dispatched `decrementPendingCount()`, and displayed a success notification.<br>5. Added comprehensive unit test in `OvertimeDetail.test.jsx` verifying that Approve and Reject buttons render when pending step exists and execute decision successfully through `approvalService.decide`. Verified with 4 passing unit tests in `OvertimeDetail.test.jsx`, 0 ESLint warnings, and clean Vite build. |
| **Status** | **fixed on main `71135a50`** (verified with Vitest `OvertimeDetail.test.jsx`, `npm run lint`, and `npm run build`) |

---

## 27. Defect D-115 — Regularization Approval Missing Inline Approve & Reject Buttons

| Field | Value |
|---|---|
| **Defect ID** | `D-115` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-27, regularization approval defect review (Item #12: "No Approve or Reject on the regularization screen; only on the Approvals inbox list") |
| **Module / Screen** | `code/frontend/src/hrms/requests/` (`RegularizationDetail.jsx`, `RegularizationDetail.test.jsx`) |
| **Severity** | NORMAL |
| **Description** | When an approver navigated to a regularization request detail screen (`/hrms/regularizations/:id`) from the Approvals Inbox ("View item") or directly, the screen displayed the employee, date, requested in/out times, reason, and decider details, but did not provide "Approve" or "Reject" buttons. The approver had to navigate back to `/approvals` to record a decision. |
| **Root Cause** | In `W-48.5 §5`, `RegularizationDetail.jsx` was originally created as a read-only view ("One regularization request, read-only..."). It did not query or resolve the associated pending `ApprovalStepResponse` (`flowType: 'REGULARIZATION'`), nor did it wire the approval decision modal (`DecideModal`). |
| **Resolution** | 1. In `RegularizationDetail.jsx`, imported `Button`, `Space`, `CheckOutlined`, `CloseOutlined`, `useDispatch`, `useCan`, `DecideModal`, `approvalService`, `decrementPendingCount`, and `successMsg`.<br>2. Resolved pending approval step from route navigation state (`location.state?.step`) or queried `approvalService.pending(0, 100)` to locate the active step where `s.itemId === id`.<br>3. When `canDecide`, pending step exists, and request status is `PENDING` or `SUBMITTED`, rendered primary **Approve** (green check icon) and danger **Reject** (close icon) buttons in the Card header `extra` alongside "Back to list" / "Back to approvals".<br>4. Integrated `DecideModal` to capture approver comments and submit to `/api/v1/approvals/steps/{stepId}/decide`. On success, immediately updated regularization status in place to `APPROVED` or `REJECTED`, dispatched `decrementPendingCount()`, and displayed a success notification.<br>5. Added comprehensive unit test in `RegularizationDetail.test.jsx` verifying that Approve and Reject buttons render when pending step exists and execute decision successfully through `approvalService.decide`. Verified with 3 passing unit tests in `RegularizationDetail.test.jsx`, 19 passing tests in `src/hrms/requests`, 0 ESLint warnings, and clean Vite build. |
| **Status** | **fixed on main `a0758fea`** (verified with Vitest `RegularizationDetail.test.jsx`, `npm run lint`, and `npm run build`) |

---

## 28. Defect D-116 — Salary Components Earning Type Missing "Fixed Allowance"

| Field | Value |
|---|---|
| **Defect ID** | `D-116` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-27, salary components defect review (Item #5: "Custom earning makes you pick an Earning Type, and 'Fixed Allowance' is not in the list") |
| **Module / Screen** | `code/frontend/src/payroll/salary/` (`componentFields.js`, `ComponentDrawer.jsx`, `ComponentDrawer.test.jsx`) |
| **Severity** | NORMAL |
| **Description** | In the Salary Components screen (`/payroll/components`), when creating or editing an Earning component, the "Earning Type" field is mandatory (`required`). The predefined dropdown options were missing `"Fixed Allowance"`. In legacy HRMS, Fixed Allowance is one of the most prominent earning components used across CTC structures, balancing formulas, and employee salary revisions. A Payroll Officer creating or customizing an earning component could not select "Fixed Allowance". |
| **Root Cause** | In `componentFields.js`, `EARNING_TYPE_NAMES` originally copied 32 names from `addNewCustomEarning.js:74-87` without including `"Fixed Allowance"`. |
| **Resolution** | 1. In `componentFields.js`, added `'Fixed Allowance'` to `EARNING_TYPE_NAMES` (name length 15 characters, well within the backend `VARCHAR(32)` column constraint on `payroll.earning.earning_type`).<br>2. Updated `ComponentDrawer.test.jsx` unit tests verifying that `EARNING_TYPE_NAMES` has 33 valid options and specifically contains `'Fixed Allowance'`.<br>3. Verified with 27 passing unit tests in `ComponentDrawer.test.jsx`, 0 ESLint warnings, and clean Vite build. |
| **Status** | **fixed on main `d2f9a944`** (verified with Vitest `ComponentDrawer.test.jsx`, `npm run lint`, and `npm run build`) |

---

## 29. Defect D-117 — WFH Policies Missing Fallback Card and Missing Edit Button

| Field | Value |
|---|---|
| **Defect ID** | `D-117` |
| **Owner** | krushna (`dev-krushna`) |
| **Found** | 2026-10-28, Stage 21 Track B (`W-01`, `W-02`, `W-03`), `/wfh/policies` |
| **Severity** | NORMAL |
| **Description** | In the WFH Policies screen (`/wfh/policies`), the system does not display the default fallback policy card (`DEFAULT_WFH`). Additionally, individual policy cards lack an "Edit Policy" action button, unlike the Leave policy management screen which permits direct edits. |
| **Root Cause** | Frontend policy screen (`WfhPolicies.jsx`) does not call `GET /v1/wfh-policies/default` to render the fallback card and only provides a "New Policy" button without mounting an Edit action on existing card items. |
| **Resolution** | 1. Mount fallback default policy banner/card fetching from `GET /v1/wfh-policies/default`.<br>2. Add "Edit" button to policy cards opening the policy drawer populated with the card's existing rule parameters. |
| **Status** | **fixed on main (uncommitted)** |

---

## 30. Defect D-118 — WFH Allocations Table Fails to Render

| Field | Value |
|---|---|
| **Defect ID** | `D-118` |
| **Owner** | krushna (`dev-krushna`) |
| **Found** | 2026-10-28, Stage 21 Track B (`W-04`), `/wfh/allocations` |
| **Severity** | CRITICAL |
| **Description** | On the WFH Allocations screen (`/wfh/allocations`), the employee quota allocations table fails to render completely, presenting an empty/blank view to HR administrators. |
| **Root Cause** | Frontend `WfhAllocations.jsx` table dataSource mapping fails or backend `GET /v1/wfh-allocations?year=2026` response format mismatch causes component render error. |
| **Resolution** | Fix data binding in `WfhAllocations.jsx` to correctly map the allocation list DTO into the table rows with proper error boundary fallback. |
| **Status** | **fixed on main (uncommitted)** |

---

## 31. Defect D-119 — WFH "Assign Allocation" Employee Selector Raw UUID & Missing Typeahead

| Field | Value |
|---|---|
| **Defect ID** | `D-119` |
| **Owner** | krushna (`dev-krushna`) |
| **Found** | 2026-10-28, Stage 21 Track B (`W-05`), `/wfh/allocations` |
| **Severity** | MAJOR |
| **Description** | In the "Assign Allocation" drawer on `/wfh/allocations`, the Employee selector displays raw database UUID strings rather than human-readable Employee Codes and Names. Furthermore, typing an employee name does not provide autocomplete/typeahead search suggestions. |
| **Root Cause** | `WfhAllocations.jsx` uses a basic text/select input instead of the shared `<EmployeeSearchSelect>` component linked to `/api/v1/employees/search`. |
| **Resolution** | Replace the input with `<EmployeeSearchSelect>` displaying `[Employee Number] Display Name`, resolving the employee UUID internally in the POST payload. |
| **Status** | **fixed on main (uncommitted)** |

---

## 32. Defect D-120 — Bulk WFH CSV Import Template Missing Header

| Field | Value |
|---|---|
| **Defect ID** | `D-120` |
| **Owner** | krushna (`dev-krushna`) |
| **Found** | 2026-10-28, Stage 21 Track B (`W-06`), `/wfh/import` |
| **Severity** | NORMAL |
| **Description** | The downloaded bulk CSV import template from `GET /v1/wfh-imports/template` is missing the `effective_date` header column required for quota validity and pro-rata calculation. |
| **Root Cause** | Template generation endpoint in `WfhImportController.java` / `WfhImportServiceImpl.java` specifies `employee_number, wfh_year, entitlement_days` and omitted `effective_date`. |
| **Resolution** | Include `effective_date` in CSV header definition and update parser to accept the column with ISO-8601 validation. |
| **Status** | **fixed on main (uncommitted)** |

---

## 33. Defect D-121 — WFH Requests Tables Missing "Actions" Column and "View" Button

| Field | Value |
|---|---|
| **Defect ID** | `D-121` |
| **Owner** | krushna (`dev-krushna`) |
| **Found** | 2026-10-28, Stage 21 Track B (`W-08b`), `/wfh/requests` & `/me/wfh` |
| **Severity** | MAJOR |
| **Description** | Unlike `/leave/requests` and `/me/leave` which provide an "Actions" column with an "👁 View" button, the WFH Applications table (`/wfh/requests`) and My WFH table (`/me/wfh`) lack the Actions column or View action. Users and managers cannot click to open the request details drawer to view dates, reason, or approval hierarchy progress. |
| **Root Cause** | Column definitions in `WfhRequestsTable.jsx` and `MyWfh.jsx` omit the `Actions` column. |
| **Resolution** | Add the `Actions` column with an `👁 View` button triggering the `WfhRequestDetailsDrawer` component. |
| **Status** | **fixed on main (uncommitted)** |

---

## 34. Defect D-122 — Approvals Inbox Missing "View Item", Raw Enum Title, & 2-Tier HR Routing

| Field | Value |
|---|---|
| **Defect ID** | `D-122` |
| **Owner** | krushna (`dev-krushna`) |
| **Found** | 2026-10-28, Stage 21 Track B (`W-09`), `/approvals` |
| **Severity** | CRITICAL |
| **Description** | In the Pending Approvals inbox (`/approvals`):<br>1. WFH requests only show Approve/Reject buttons and lack the "👁 View Item" link present on Leave requests.<br>2. Subject title displays raw enum text with awkward line wrapping: `WORK_FROM_HOME Request`.<br>3. When a Manager approves a WFH request at Level 1, the workflow fails to advance to HR for Level 2 approval and does not appear in the HR pending approvals queue. |
| **Root Cause** | `Inbox.jsx` lacks handler for `WORK_FROM_HOME` in `View Item` modal router; flow title formatter is missing title-case conversion; workflow definition lacks Step 2 HR assignment on manager approval. |
| **Resolution** | 1. Add `View Item` action handler for WFH flow in `Inbox.jsx`.<br>2. Format title as `Work From Home Request`.<br>3. Ensure `WfhRequestService.java` routes approved manager requests to the `hr` role approval queue. |
| **Status** | **fixed on main (uncommitted)** |

---

## 35. Defect D-123 — WFH Balance Auto-Defaulting to 12 Days Without HR Allocation

| Field | Value |
|---|---|
| **Defect ID** | `D-123` |
| **Owner** | krushna (`dev-krushna`), sayeed (`dev-sayeed`) |
| **Found** | 2026-10-28, Stage 21 Track A & B Parity Review (`E-WFH-01`, `W-04`), `/me/wfh`, `GET /v1/me/wfh-balance` |
| **Severity** | CRITICAL |
| **Description** | Whenever a new employee is created, navigating to their WFH dashboard (`/me/wfh`) immediately displays an active annual quota balance of **12 / 12 days** by default. Employees should NOT have an active WFH balance or entitlement until HR explicitly assigns a WFH quota via `/wfh/allocations` or bulk import (`/wfh/import`). Without explicit allocation, the employee balance must remain 0 (or unallocated state) and the employee should be prevented from submitting WFH requests. |
| **Root Cause** | In `WfhBalanceServiceImpl.java` (or balance calculation handler), if no matching row is found in `core.wfh_allocation` for the employee, the system erroneously falls back to granting the full annual quota from tenant fallback policy `DEFAULT_WFH` (12.0 days), instead of evaluating entitlement as 0.00. |
| **Resolution** | 1. In `WfhBalanceServiceImpl.java`, return 0.00 entitlement, 0.00 available days, and unallocated status if no active record exists in `core.wfh_allocation` for the employee.<br>2. On `/me/wfh` UI, render "0 / 0" or an informative "No WFH Quota Allocated — Please contact HR" notice and disable "Apply for WFH" until quota is assigned by HR.<br>3. In `WfhRequestServiceImpl.java`, ensure validation blocks application attempts when quota entitlement is 0. |
| **Status** | **fixed on main `e822f8da`** (BUG-D4-08) |

---

## 36. Defect D-124 — WFH Application Details Modal Missing Multi-Stage Approval Trail (Manager & HR Approval Status)

| Field | Value |
|---|---|
| **Defect ID** | `D-124` |
| **Owner** | krushna (`dev-krushna`) |
| **Found** | 2026-10-29, WFH 2-stage approval verification (`W-08b`, `W-09`), `/wfh/requests`, `/me/wfh` |
| **Severity** | MAJOR |
| **Description** | When an employee submits a Work From Home (WFH) request and their reporting manager approves it at Level 1, the WFH Application Details modal (`/wfh/requests` and `/me/wfh`) continues to display a static `Status: PENDING` tag and raw employee UUID (`04610e36-8d24-4b2d-a639-348c0bfe1229`). Unlike the Leave Request Details screen (`LeaveRequestDetail.jsx`), the modal completely lacks the "Approval Trail" section. As a result, the employee, manager, and HR cannot see intermediate progress: whether Step 1 (Reporting Manager) has been approved (including manager name, timestamp, and decision comments), or whether Step 2 (HR) is pending or decided. |
| **Root Cause** | In `WfhRequests.jsx` and `MyWfh.jsx`, the "WFH Application Details" / "WFH Request Details" modal only renders static properties from the selected row (`employeeId`, `dates`, `duration`, `status`, `reason`, `submissionType`, `appliedOn`). It does not invoke `approvalService.history(approvalInstanceId)` to load `ApprovalHistoryStepResponse` steps, nor does it render a `<Timeline>` matching `LeaveRequestDetail.jsx`. Furthermore, `WfhRequests.jsx` renders raw `employeeId` UUID instead of employee name/code. |
| **Resolution** | 1. In `WfhRequests.jsx` and `MyWfh.jsx`, integrate `approvalService.history(selectedRequest.approvalInstanceId)` to load approval history when opening the request details modal.<br>2. Add an "Approval Trail" section using `<Timeline>` matching `LeaveRequestDetail.jsx`, displaying `Step 1 — Approved by [Manager Name] · [Date, Time]` with decision notes/comments, and `Step 2 — Pending HR Approval` / `Step 2 — Approved by [HR Name]`.<br>3. In `WfhRequests.jsx`, display formatted Employee Name & Employee Code (`[ITC-0045] Krushna Sonalkar`) instead of raw database UUID.<br>4. Ensure backend `WfhRequestResponse.java` includes `approvalInstanceId`, `employeeName`, and `employeeCode`. |
| **Status** | **fixed on main** (BUG-D4-09) |

---

## 37. Defect D-125 — Attendance Log Screen Missing Day Status / Remarks Column for Approved WFH (Dual System Audit)

| Field | Value |
|---|---|
| **Defect ID** | `D-125` |
| **Owner** | krushna (`dev-krushna`), sayeed (`dev-sayeed`) |
| **Found** | 2026-10-29, Stage 21 Track B (`W-11`, `S21-17`), `/hrms/attendance-log` |
| **Severity** | MAJOR |
| **Description** | During manual QA certification of test case `S21-17` (Step 12 / `W-11` — *Dual system audit: verify automatic attendance log creation (PRESENT - Work From Home) and Zero LOP in payroll*), an employee with an approved WFH request (e.g. Manika Arjun on 2026-10-29) clocks in and out. Navigating to the Attendance Log (`/hrms/attendance-log`) shows only the raw biometric/web clock-in & clock-out intervals (`In: 17:48`, `Out: 17:51`, `Worked: 0:03`, `Origin: Clock`). The screen completely lacks a **"Status / Remarks"** column to indicate that the employee's daily status is **`PRESENT`** with remark **`"Work From Home"`**. As a result, HR inspecting `/hrms/attendance-log` cannot visually verify remote work status or distinguish WFH clock punches from office attendance without checking `/wfh/requests` or querying the database. |
| **Root Cause** | In `AttendanceLog.jsx`, the table queries `GET /v1/hrms/attendance/sessions` which only maps `ClockSessionResponse` from the `attendance_session` table. It does not join or display the daily attendance status (`PRESENT` / `HALF_DAY`) and remarks (`"Work From Home"`) recorded in `core.attendance`. |
| **Resolution** | 1. In `AttendanceLog.jsx`, add a **Status / Remarks** column to the table.<br>2. On `ClockSessionResponse` (or via endpoint join with `core.attendance`), provide the derived daily attendance status and remarks so that approved WFH days display a `<Tag color="blue">Work From Home</Tag>` or `<StatusTag status="PRESENT" />` tag.<br>3. Ensure if an employee works remotely and does or does not clock in/out, the approved WFH day remains visible to HR on attendance screens. |
| **Status** | **fixed on main** (BUG-D4-10) |

---

## 38. Enhancement / Defect D-126 (ENH-D4-11) — WFH Approval Trail Not Visible to Employee in Self-Service (/me/wfh)

| Field | Value |
|---|---|
| **Defect / Enhancement ID** | `D-126` (`ENH-D4-11` / `BUG-D4-11`) |
| **Owner** | krushna (`dev-krushna`) |
| **Found** | 2026-10-29, Employee Self-Service verification (`E-WFH-02`, `W-08b`), `/me/wfh` |
| **Severity** | MEDIUM / ENHANCEMENT |
| **Description** | Following the fix for `D-124`, the multi-stage Approval Trail timeline renders properly for HR on `/wfh/requests`. However, when an employee checks their own WFH request in Employee Self-Service (`/me/wfh` -> "Actions" -> "👁 View"), the Approval Trail section renders `<Text type="secondary">No approval steps recorded.</Text>`. As a result, the employee cannot see intermediate progress (e.g., Step 1 — Approved by Manager with comments, Step 2 — Pending HR Approval). |
| **Root Cause** | In `MyWfh.jsx`, the details modal calls `approvalService.history(record.approvalInstanceId)` (`GET /api/v1/approvals/{instanceId}/history`). In `ApprovalController.java`, this endpoint is protected by `@RequiresAction(value = "core.approval.read", anyOf = "core.approval.decide")`. Standard employees only hold `core.employee.read_own` and `core.wfh.apply`/`core.wfh.read_own`; they do not hold `core.approval.read` (an HR action) or `core.approval.decide` (a Manager action). The backend returns HTTP 403 Forbidden, which `MyWfh.jsx` catches and sets an empty list `[]`, falling back to "No approval steps recorded." |
| **Resolution Plan** | 1. Update `ApprovalController.java` / `ApprovalService.java` to allow the requester (the employee who owns the underlying workflow instance) to read their own approval instance history, or permit callers with `core.wfh.read_own` / `core.leave.read_own` when viewing their own request instance.<br>2. Alternatively, expose a dedicated portal endpoint (e.g. `GET /api/v1/me/wfh-requests/{id}/history` or `GET /api/v1/me/approvals/{instanceId}/history`) that verifies tenant and employee ownership and returns the sanitized approval steps.<br>3. Verify that in `MyWfh.jsx`, the Approval Trail timeline renders correctly for the employee with approver names, step decision badges, timestamps, and comments matching the HR view. |
| **Status** | **open** (ENH-D4-11) |


---

## 38. Defect D-126 — Salary Structure Version Form and Active View Missing Deductions Section

| Field | Value |
|---|---|
| **Defect ID** | `D-126` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-29, salary structure defect review (Item #7: "Salary Structure — No Deduction Section") |
| **Module / Screen** | `code/frontend/src/payroll/salary/` (`SalaryVersionForm.jsx`, `SalaryTab.jsx`) & `code/backend/payroll/src/main/java/com/infinevo/payroll/salary/SalaryVersionRequest.java` |
| **Severity** | NORMAL |
| **Description** | In the Employee Salary tab (`/payroll/employees/:id/salary` -> `SalaryTab.jsx`) and Salary Structure Version Form (`SalaryVersionForm.jsx`), only Earnings, Benefits, and Reimbursements are supported and rendered. The Deductions category is missing from the version form's initial values, reset fields, submission payload, and rendered component list, as well as from the active structure card's summary tables. |
| **Root Cause** | In `SalaryVersionForm.jsx`, `activeComponents.deductions` is fetched by `salarySlice`, but `renderComponentList('deductions', 'Deductions', ...)` is not invoked, nor is `deductions` included in the form's `initialValues`, `resetFields`, or payload mapping. In `SalaryTab.jsx`, only `earnings`, `benefits`, and `reimbursements` tables are rendered under the active structure card. In `SalaryVersionRequest.java`, unknown properties are not ignored by default and `deductions` is not accepted. |
| **Resolution** | Pending review / will circle back later. Add Deductions list to `SalaryVersionForm.jsx`, render Deductions summary table in `SalaryTab.jsx`, and accept `deductions` in `SalaryVersionRequest.java`. |
| **Status** | **open** |

---

## 39. Defect D-127 — Salary Structure Percentage Component Value Not Copied In

| Field | Value |
|---|---|
| **Defect ID** | `D-127` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-29, salary structure defect review (Item #6: "Salary Structure — Percentage Component Value Not Copied In") |
| **Module / Screen** | `code/frontend/src/payroll/salary/` (`SalaryVersionForm.jsx`) & `code/backend/payroll/src/main/java/com/infinevo/payroll/salary/EmployeeSalaryServiceImpl.java` |
| **Severity** | NORMAL |
| **Description** | When salary components are defined in `/payroll/components` with calculation type `PERCENTAGE`, a default percentage value (e.g., 40.00% for HRA) and percentage calculation base (`BASIC`, `CTC`, `GROSS`), creating or revising an employee's salary structure in `SalaryVersionForm` fails to copy or auto-populate these values. When clicking "Add Earning" or selecting a percentage component from the dropdown, the form defaults to `calculationType: 'FLAT'`, blank value, and null base. |
| **Root Cause** | 1. In `SalaryVersionForm.jsx`, `renderComponentList` hardcodes `{ calculationType: 'FLAT', value: '', percentageOf: null }` in the "Add Component" click handler and `<Select placeholder="Component">` lacks an `onChange` handler to populate component defaults from `availableItems`.<br>2. In `initialValues` mapping, existing rows with missing or empty values do not fall back to `comp.defaultValue` or `comp.percentageOf`.<br>3. In `EmployeeSalaryServiceImpl.java`, `resolveEarningInputs`, `resolveBenefitInputs`, and `resolveReimbursementInputs` pass `item.calculationType()`, `item.value()`, and `item.percentageOf()` without falling back to component definition when null. |
| **Resolution** | Pending review / will circle back later. Auto-populate `calculationType`, `defaultValue`, and `percentageOf` when selecting or adding components in `SalaryVersionForm.jsx`, add `GROSS` base option, and provide fallback in `EmployeeSalaryServiceImpl.java`. |
| **Status** | **open** |

---

## 40. Defect D-128 — Pay Run Deduction Component Default Value Not Applied

| Field | Value |
|---|---|
| **Defect ID** | `D-128` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-29, pay run defect review (Item #8: "Pay Run — Deduction Component Default Value Not Applied") |
| **Module / Screen** | `code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/StructureLineContributor.java`, `code/backend/payroll/src/main/java/com/infinevo/payroll/salary/EmployeeSalaryServiceImpl.java`, `code/backend/payroll/src/main/java/com/infinevo/payroll/salary/SalaryVersionResponse.java` |
| **Severity** | MAJOR |
| **Description** | When deduction components are configured in `/payroll/components` (Deductions tab) with default values (FLAT or PERCENTAGE of BASIC/GROSS/CTC, optional maxLimit, active, non-deleted), executing a Pay Run fails to apply them. The pay run computation ignores catalogue deduction default values, resulting in 0 deduction lines, `total_deductions` remaining at 0, net pay failing to subtract deduction amounts, and the pay run lines drawer / payslips omitting deductions. |
| **Root Cause** | In `StructureLineContributor.java`, only `EarningRepository` is injected and evaluated; `DeductionRepository` is not injected and deduction catalogue defaults are completely ignored. In addition, `SalaryVersionResponse.java` lacks a `deductions` list field to convey version-level deductions to contributors and UI consumers. |
| **Resolution** | Pending review / will circle back later. Inject `DeductionRepository` in `StructureLineContributor.java`, evaluate catalogue deductions and version deductions, compute flat/percentage amounts with `maxLimit` caps, and populate `SalaryVersionResponse.deductions`. |
| **Status** | **open** |

---

## 41. Defect D-129 — Employee Creation Screen Missing Statutory Components Section (EPF, ESI, PT Toggles & PF/UAN Input Fields)

| Field | Value |
|---|---|
| **Defect ID** | `D-129` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-29, Legacy parity analysis: Add Employee Statutory Components Workflow (`/employees/create`) |
| **Module / Screen** | `code/frontend/src/core/employee/EmployeeCreate.jsx`, `code/frontend/src/core/employee/employeeService.js` |
| **Severity** | MAJOR |
| **Description** | In the legacy application (`legacy/Payroll-Fend-react/src/pages/mainPages/employee/basicDetails.js:912-1111`), creating a new employee on the first page ("Basic Details") dynamically displays the **Statutory Components** section if enabled in organization settings (`statutoryConfig.epfEnabled`, `esiEnabled`, `ptEnabled`).<br>1. When EPF is enabled for the tenant, an **"Employees' Provident Fund"** checkbox is presented. Selecting it exposes mandatory inputs for **PF Account Number** (with format validation `AA/AAA/0000000/XXX/0000000`) and **UAN** (12 numeric digits), plus EPS contribution toggles (`eligibleForEps`, `contributesEpsOnHigherWages`).<br>2. When ESI is enabled for the tenant, an **"Employees' State Insurance"** checkbox is presented. Selecting it exposes the **ESI Insurance Number** input (10 digits).<br>3. When Professional Tax is enabled, a **"Professional Tax"** checkbox is presented.<br>In the new HCM application (`EmployeeCreate.jsx`), the employee creation page completely lacks the Statutory Components section. HR cannot configure statutory eligibility, PF, or UAN numbers during onboarding, forcing them to find a disconnected `StatutoryProfileCard` at the bottom of the payroll salary tab after employee creation. |
| **Root Cause** | `EmployeeCreate.jsx` only handles basic core employee demographic fields (`core.employee`), without querying payroll statutory settings (`/v1/payroll/settings/epf`, `/v1/payroll/settings/esi`) or rendering statutory enrollment controls. |
| **Resolution** | 1. In `EmployeeCreate.jsx`, query tenant statutory settings (EPF, ESI, PT) on load.<br>2. Add a **Statutory Components** section to `EmployeeCreate.jsx` that dynamically renders EPF, ESI, and PT controls based on tenant configuration.<br>3. When EPF is checked, render inputs for PF Account Number and UAN (with standard regex validation) and EPS options.<br>4. When ESI is checked, render input for ESI Insurance Number.<br>5. Upon successful creation of the employee, persist the statutory profile via `statutoryProfileService.save(createdEmployee.id, statutoryPayload)` (or unified onboarding backend flow). |
| **Status** | **open** |

---

## 42. Defect D-130 — Salary Structure Active View and Version Modal Missing Statutory Contributions Breakdown (EPF, ESI, EDLI Rates & Amounts)

| Field | Value |
|---|---|
| **Defect ID** | `D-130` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-29, Legacy parity analysis: Salary Structure Statutory Breakdown & Preview (`/payroll/employees/:id/salary`) |
| **Module / Screen** | `code/frontend/src/payroll/salary/` (`SalaryTab.jsx`, `SalaryVersionForm.jsx`) |
| **Severity** | MAJOR |
| **Description** | In the legacy application (`legacy/Payroll-Fend-react/src/pages/mainPages/employee/salaryDetails.js:1208-1346`), when an employee is enrolled in statutory schemes (EPF, ESI):<br>1. The Salary Details view renders a dedicated **Benefits / Statutory Contributions** section displaying employer statutory components with their applicable percentages (e.g. `EPF - Employer Contribution: 12% of PF Wages`, `EDLI: 0.50% of PF Wages`, `EPF Admin Charges: 0.50% of PF Wages`, `ESI: 3.25% of Gross`) along with calculated monthly and annual amounts.<br>2. These statutory amounts are factored into the CTC reconciliation (`Earnings + Employer Statutory Contributions = Annual CTC`).<br>In the new HCM application:<br>- In `SalaryTab.jsx`, the active salary structure card displays only Earnings, Benefits, and Reimbursements. It completely ignores `currentVersion.statutory` returned by `SalaryVersionResponse.java`, omitting statutory contribution rates, shares, and amounts from the employee's salary overview.<br>- In `SalaryVersionForm.jsx`, the creation/revision modal provides zero preview or visibility of employer statutory contribution lines and their percentages against the entered Annual CTC. |
| **Root Cause** | `SalaryTab.jsx` lacks a table mapping `currentVersion.statutory` items. `SalaryVersionForm.jsx` does not simulate or fetch derived statutory lines (`StatutoryLineDeriver`) during structure draft editing. |
| **Resolution** | 1. In `SalaryTab.jsx`, render a dedicated **Statutory Contributions** table (`id="table-salary-statutory"`) under the active version card displaying component code, contribution share (Employer/Employee), rate/percentage, monthly amount, and annual amount from `currentVersion.statutory`.<br>2. In `SalaryVersionForm.jsx`, render an informational statutory preview or breakdown showing estimated employer EPF/ESI contributions based on employee statutory profile and entered Basic/Gross, reconciling total CTC. |
| **Status** | **open** |

---

## 43. Defect D-131 — Employee Creation Backend Missing Integrated Statutory Profile Onboarding Workflow

| Field | Value |
|---|---|
| **Defect ID** | `D-131` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Found** | 2026-10-29, Legacy parity analysis: Cross-Module Employee Statutory Onboarding Orchestration |
| **Module / Screen** | `code/backend/core/` and `code/backend/payroll/` (`EmployeeCreate` / Onboarding API) |
| **Severity** | NORMAL |
| **Description** | In the legacy system, basic employee information and statutory components are submitted as a single transactional payload. In the new modular architecture, `core.employee` (Core module) and `payroll.employee_statutory_profile` (Payroll module) are separated behind distinct API endpoints (`POST /v1/employees` and `PUT /v1/payroll/employees/{id}/statutory-profile`). If statutory fields are submitted during employee onboarding, a failure in the statutory call could leave an orphaned employee with unconfigured statutory profile, or vice-versa. |
| **Root Cause** | Absence of an orchestrated onboarding endpoint or domain event listener (`EmployeeCreatedEvent` triggering statutory profile initialisation with defaults). |
| **Resolution** | 1. Support composite onboarding orchestration (or domain event `EmployeeCreatedEvent` in backend) so that when an employee is created, their statutory profile is initialized with tenant defaults or provided values in a coherent workflow.<br>2. Ensure frontend handles onboarding gracefully with sequential promises or a composite transaction. |
| **Status** | **open** |









