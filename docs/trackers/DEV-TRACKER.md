# Dev Tracker

> The product itself — foundation, data, core platform, payroll, HRMS, frontend.
> Streams A to F, plus the product items in G and H.
> **GitHub is authoritative.** Status legend: [README.md](README.md).
> Last refreshed: **2026-10-03**, against `main` `8b7705c3` — mohit's `W-33.3`, `W-36.4` merged after 19 tickets from five lanes merged together on `integration-2026-10-03` (CI green, backend 15 min; the backend job limit raised to 30 min in the same push). Three lanes assigned 2026-09-25: **sayeed** (defects, then employee), **krushna** (tenant and onboarding), **devashis** (documents, notifications, reporting).

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
| sayeed | `dev-sayeed` | 1a employee & org · 5 statutory settings, lines and screens · tax deductor | ~~`W-52.1`~~ ~~`W-53.1`~~ **on main `5c07c45`** ~~`W-13.4`~~ **on main `e699699`** ~~`W-09.1`~~ **on main `c79755c`** ~~`W-13.3`~~ ~~`W-14.2`~~ **on main `ccc8e48`** ~~`W-39.1`~~ **on main `3bf5b10`** → D-9 manual checks → ~~`W-31.1`~~ ~~`W-31.2`~~ **on main `ac94662`** → ~~`W-31.3`~~ **on main `ed180b1`** ~~`W-35.1`~~ **on main `840bf2e`** → ~~`W-36.3`~~ ~~`W-31.4`~~ ~~`W-47.1a`~~ ~~`W-47.1b`~~ **on main `2780d098`** — queue empty, D-9 still open (`W-36.1` and `W-40` moved to karma 2026-10-02; `W-47.1a` from karma 2026-10-02) | `V026`–`V032` (`V030` used) · `V062`–`V069` · `V107` `W-36.3` used (**on main `2780d098`**) |
| krushna | `dev-krushna` | 2 tenant & onboarding · 7a working-day policy & schedule · 7c pay run compute | ~~`W-12.1`~~ ~~`W-12.2`~~ **on main `b7d03ec`** ~~`W-12.3`~~ **on main `a4f31ae`** ~~`W-24.1`~~ ~~`W-17`~~ ~~`W-24.2`~~ ~~`W-18.1`~~ ~~`W-28`~~ **on main `288f9fa`** ~~`W-29.1`~~ ~~`W-29.2`~~ ~~`W-29.3`~~ ~~`W-29.4`~~ **on main `7dfd74e`** ~~`W-18.2`~~ **on main `b580d8b`** ~~`W-30.2`~~ ~~`W-35.2`~~ ~~`W-36.2`~~ **on main `9d5c0a0`** ~~`W-47.2`~~ **on main `36c02d1`** ~~`W-38.1`~~ ~~`W-38.3`~~ ~~`W-47.6`~~ **on main `2780d098`** (branch `krushna-tickets`) ~~`W-38.2`~~ **on main `6e19dd89`** (built in `batch-2026-10-03`) | `V140` `W-38.1` used (**on main `2780d098`**) · `V033`–`V036` used · `V054` used · `V116`–`V119` used · `V055`–`V059` used · `V125` used · `V061`, `V100`–`V101`, `V103` used (**on main `9d5c0a0`**) |
| devashis | `dev-devashis` | 3 documents, notifications, reporting · 7b pay input ledger & captures · HRMS projects · admin console · proof of investment · payslips | ~~`W-21`~~ ~~`W-20.1`~~ ~~`W-23.1`~~ ~~`W-20.2`~~ ~~`W-22.2`~~ ~~`W-23.2`~~ ~~`W-19`~~ ~~`W-30.1`~~ ~~`W-39.2`~~ **on main `b6e6012`** ~~`W-41`~~ ~~`W-65.1`~~ ~~`W-65.2`~~ ~~`W-34.1`~~ ~~`W-34.3`~~ **on main `5fa04b1`** ~~`W-34.2`~~ **on main `5fa04b1` and `6e19dd89`** ~~`W-42.1`~~ ~~`W-42.2`~~ ~~`W-42.4`~~ ~~`W-42.3`~~ ~~`W-43.1`~~ ~~`W-43.2`~~ **on main `2780d098`** (branch `dev-devashish`) | `V031`–`V032` · `V037`–`V041` · `V060` · `V093`–`V096` — all used · `V110`–`V114` `W-34.1` (`V110` a `reference` script) · `V115` `W-34.2` · `V141`–`V144` `W-42.1` · `V145` `W-42.2` · `V146` `W-43.1` — used (**on main `2780d098`**) |
| karma | `dev-karma` | 1b approvals, leave & portal · employee TDS · HRMS attendance · salary structure screens | ~~`W-15.1`~~ ~~`W-15.2`~~ ~~`W-15.3`~~ **on main `a34c14f`** ~~`W-16.1`~~ ~~`W-16.2`~~ ~~`W-16.3`~~ ~~`W-16.4a`~~ ~~`W-16.4b`~~ ~~`W-25`~~ **on main `a53e311`** ~~`W-36.1`~~ **on main `9263c30`** ~~`W-40.1`~~ ~~`W-40.2`~~ ~~`W-40.5`~~ ~~`W-40.3`~~ **on main `2780d098`** ~~`W-40.4`~~ ~~`W-40.6`~~ **on main `6e19dd89`** (built in `batch-2026-10-03`) ~~`W-10.1`~~ **on main `889b09b`** → `W-52.2` queue by managed identity (assigned 2026-10-07, fixes `D-11`) (`W-36.1`, `W-40` from sayeed 2026-10-02, `W-47.1a` moved to sayeed 2026-10-02; resets `dev-karma` to `main` first) | `V089`–`V092` used · `V126`–`V134` used · `V102` used (**on main `9263c30`**) · `V120`–`V123` and `V139` used (**on main `2780d098`**; `V139` kept outside the block, founder 2026-10-03) · `V120`–`V124` `W-40` (`V121` `.1` · `V122` `.2`, a `reference` script · `V123` `.3` · `V124` `.4` · `V120` `.5`, a `core` script; `.6` has none — reserved 2026-09-30; `.1`–`.4` moved up from `V116`–`V119`, which krushna's lane used on main `288f9fa`) |
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
| D-11 | The Storage Queue was never connected on Azure, so every email waits 2–3 minutes for the delivery sweep. `W-52` asked for managed identity (`W-52-queue-worker.md:64`) but only the connection-string path was built (`StorageQueueConfig.java:16-18`); `W-52.1` carried it forward "unchanged" (`W-52-1-worker-fix.md:76`); `W-51` forbids the account key a connection string needs; no ticket passed the queue endpoint to the containers | 2026-10-07, founder on Azure dev (invitation emails late) | `W-52.2` | assigned — karma |
| D-12 | Every user saw a 404 right after login: Keycloak returns to `/`, and no route matched `/` (`AppShell.jsx` mounted only feed paths) | 2026-10-07, founder on Azure dev | claude: `/` redirects to the first path in the navigation feed | **fixed on main `c470db64`** — built by claude |
| D-13 | The signed-in tenant's name appeared nowhere in the app: `GET /api/v1/navigation` carried no tenant name | 2026-10-07, founder on Azure dev | claude: `tenantName` on the navigation reply, read under RLS (the target tenant's while acting as); shown in the header | **fixed on main `c470db64`** — built by claude |
| D-14 | Keycloak branding on login and admin pages: realm heading used `kc-logo-text` (Keycloak's CSS shows its logo and hides the text); stock titles, favicons and logos | 2026-10-07, founder on Azure dev | claude: `infinevo` Keycloak theme as server default; class dropped from both realm files; Azure dev realms fixed by hand | **fixed on main `c470db64`** — built by claude; reaches Azure with the next Keycloak image |
| D-15 | Leave Allocation fails with `VALIDATION_FAILED: No effective policy found` when `effectiveFrom` is null / unconfigured | 2026-10-06, manual QA Stage 4 (`S4-10`) | sayeed (`dev-sayeed` `4b400291`) | **fixed** (`PolicyForm.jsx`, `LeaveTypeServiceImpl.java`) |
| D-16 | Holiday Calendar creation with `isDefault: true` throws HTTP 409 Conflict if default calendar exists | 2026-10-06, manual QA Stage 4 (`S4-01`) | sayeed (`dev-sayeed` `4b400291`) | **fixed** (`Calendars.jsx`) |
| D-17 | Employee portal login blocked by Azure Front Door WAF rate-limiting rule (`The request is blocked. 20261007T...`) | 2026-10-07, manual QA Stage 19 (`S19-01`) | infra / WAF policy tuning (`frontdoor.bicep`) | open |
| D-18 | Employee invitation acceptance throws HTTP 500 (`An unexpected error occurred while processing the invitation`); compensation deletes Keycloak user after password email sent | 2026-10-07, manual QA employee invitation accept (`POST /api/v1/invitations/accept`) | backend (`InvitationServiceImpl`, `InvitationAcceptanceController`) | open |

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
- **Root Cause:** In `InvitationServiceImpl.acceptEmployeeInvitation()`, Keycloak user creation and password reset email dispatch succeed in step 1. During subsequent steps (local `core.user_account` sync, `core.user_tenant` insert, or `roleService.replaceUserRoles()` for seeded `employee` role), an unhandled `RuntimeException` is thrown. The controller's `@ExceptionHandler(Exception.class)` catches it and returns 500 `INTERNAL`. Crucially, the service's `catch (RuntimeException e)` invokes `compensate(provisioning)` which deletes the newly created Keycloak user, leaving the invitation stuck in `PENDING` and invalidating the password update email.
- **Impact:** Employees cannot complete invitation onboarding; password update email points to an already-deleted Keycloak account.

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
| `W-52.2` Queue by managed identity | `StorageQueueConfig` builds the queue client from an endpoint plus the container's managed identity (Azure) as well as a connection string (Azurite); one non-blank "queue configured" condition for client, producer and consumer loop; `azure-identity` on `shared`; Bicep passes `AZURE_STORAGE_QUEUE_ENDPOINT` to app and worker. No migration. Fixes `D-11`. Lands on `dev` when the founder runs the Bicep | `W-52` §2 item 2 | **Ready** · assigned — karma |
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

## Related

- Every ticket and its features: [../target-state/08-work-plan.md](../target-state/08-work-plan.md)
- What to build first: [../target-state/09-build-order.md](../target-state/09-build-order.md)
- Tables: [../target-state/02-data-model.md](../target-state/02-data-model.md)
- Rules new code must follow: [../CONVENTIONS.md](../CONVENTIONS.md)
- Other trackers: [INFRA-TRACKER.md](INFRA-TRACKER.md) · [HARNESS-TRACKER.md](HARNESS-TRACKER.md) · [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md)
