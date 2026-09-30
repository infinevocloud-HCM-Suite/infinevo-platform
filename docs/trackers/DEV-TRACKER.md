# Dev Tracker

> The product itself — foundation, data, core platform, payroll, HRMS, frontend.
> Streams A to F, plus the product items in G and H.
> **GitHub is authoritative.** Status legend: [README.md](README.md).
> Last refreshed: **2026-09-26**, against `main` — every row checked against a merge commit. Three lanes assigned 2026-09-25: **sayeed** (defects, then employee), **krushna** (tenant and onboarding), **devashis** (documents, notifications, reporting).

## Summary

| Stream | Tickets | Code on main | Feature done | Where it stands |
|---|---|---|---|---|
| A — Foundation | 2 | 2 | 2 | Done |
| B — Data foundation | 5 | 5 | 5 | **Done.** Tenancy chain complete; suite green again (`W-04.1`) |
| C — Core platform | 26 | 19 | 19 | **Building.** `W-19`, `W-20.1`, `W-20.2`, `W-21`, `W-22.2`, `W-23.1`, `W-23.2`, `W-39.2` on `main` (`b6e6012`) — pay input ledger, notifications and reminders, document store, retention, reporting, overtime; `W-39.1` on `main` (`3bf5b10`) — administrator-entered attendance for Payroll-only tenants; `W-15.1`–`W-15.3` on `main` (`a34c14f`); `W-16.1` is Ready |
| D — Payroll | 23 | 10 | 10 | **Building.** `W-26.1`–`W-27.2` on `main` (`79c8825`) — component catalogue, dated CTC versions, FBP plan and declaration; `W-30.1` on `main` (`b6e6012`); `W-32.1`–`.4` tax declaration on `main` (`b704f3c`), `W-33.1` Ready; `W-31.1`, `W-31.2` EPF, ESI and professional tax on `main` (`ac94662`); `W-31.3` employee EPF and ESI lines on `main` (`ed180b1`); `W-35.1` reimbursement claims on `main` (`840bf2e`); `W-28` next, `W-29.1` waits on `W-28` only (`W-19` is on `main`) |
| E — HRMS | 5 | 0 | 0 | `W-15` is on `main` (`a34c14f`); `W-41` Ready, assigned to devashis 2026-09-29; `W-16.1` is Ready, the rest wait on `W-16` |
| F — Frontend | 13 | 0 | 0 | **Assigned 2026-09-28: `W-45` and all six `W-46` parts to biren.** `W-12` is on `main` (`a4f31ae`); `W-46` specs written 2026-09-28, six tickets after the `W-46.3` split |
| G/H — product items | 3 | 1 | 1 | `W-55` merged `2ccd723`. `W-65.1`, `W-65.2` assigned to devashis 2026-09-29. `W-66` unassigned |

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
| sayeed | `dev-sayeed` | 1a employee & org · 5 statutory settings, lines and screens | ~~`W-52.1`~~ ~~`W-53.1`~~ **on main `5c07c45`** ~~`W-13.4`~~ **on main `e699699`** ~~`W-09.1`~~ **on main `c79755c`** ~~`W-13.3`~~ ~~`W-14.2`~~ **on main `ccc8e48`** ~~`W-39.1`~~ **on main `3bf5b10`** → D-9 manual checks → ~~`W-31.1`~~ ~~`W-31.2`~~ **on main `ac94662`** → ~~`W-31.3`~~ **on main `ed180b1`** ~~`W-35.1`~~ **on main `840bf2e`** → `W-31.4` (after `W-29.3`) → `W-47.1b` (after `W-45`, `W-28`, `W-18.1`) | `V026`–`V032` (`V030` used) · `V062`–`V069` |
| krushna | `dev-krushna` | 2 tenant & onboarding · 7a working-day policy & schedule · 7c pay run compute | ~~`W-12.1`~~ ~~`W-12.2`~~ **on main `b7d03ec`** ~~`W-12.3`~~ **on main `a4f31ae`** → `W-24.1` **Ready** → `W-17` → `W-24.2` → `W-18.1` → `W-18.2` → `W-28` → `W-29.1` → `W-29.2` → `W-29.3` → `W-29.4` → `W-36.2` | `V033`–`V034` used · `V035`–`V036` · `V054` · `V055`–`V059` · `V103` · `V116` (`W-18.1`) · `V117`–`V118` (`W-24.2`) |
| devashis | `dev-devashis` | 3 documents, notifications, reporting · 7b pay input ledger & captures · HRMS projects · admin console | ~~`W-21`~~ ~~`W-20.1`~~ ~~`W-23.1`~~ ~~`W-20.2`~~ ~~`W-22.2`~~ ~~`W-23.2`~~ ~~`W-19`~~ ~~`W-30.1`~~ ~~`W-39.2`~~ **on main `b6e6012`** → `W-41` **Ready** → `W-65.1` **Ready** → `W-65.2` (assigned 2026-09-29; resets `dev-devashis` to `main` first) | `V031`–`V032` · `V037`–`V041` · `V060` · `V093`–`V096` — all used |
| karma | `dev-karma` | 1b approvals, leave & portal | ~~`W-15.1`~~ ~~`W-15.2`~~ ~~`W-15.3`~~ **on main `a34c14f`** → `W-16.1` **Ready** → `W-16.2` → `W-16.3` → `W-16.4a` → `W-16.4b` → `W-25` → `W-40` | `V089`–`V092` used |
| biren | `dev-biren` | 4 salary structure & FBP · 8 frontend shell & core screens | ~~`W-26.1`~~ ~~`W-26.2`~~ **on main `a3ad0a3`** ~~`W-27.1`~~ ~~`W-27.2`~~ **on main `79c8825`** → `W-45` **Ready** → `W-46.1` → `W-46.3a` → `W-46.3b` (after `W-17`) → `W-46.4` (after `W-15`) → `W-46.2` (after `W-16`) → `W-46.5` (after `W-25`) | `V042`–`V053` used · frontend: none |
| mohit | `dev-mohit` | 6 tax declaration, calculator & screens | ~~`W-32.1`~~ ~~`W-32.2`~~ ~~`W-32.3`~~ ~~`W-32.4`~~ **on main `b704f3c`** → `W-47.3` (after `W-45`, `W-25`) → `W-36.1` (after `W-29.2`) → `W-33.1` **Ready** → `W-33.2` → `W-33.3` → `W-34.1` **Ready** → `W-34.2` (after `W-33.3`) → `W-34.3` → `W-36.3` → `W-36.4` (after `W-33.3`) → `W-36.5` | `V070`–`V081` used · `V105`–`V106` used (`reference`) · `V102` `W-36.1` · `V104` `W-33.3` · `V107` `W-36.3` · `V108`–`V109` `W-36.5` · `V110`–`V114` `W-34.1` (`V110` a `reference` script) · `V115` `W-34.2` |
| *unassigned* | — | 7d payroll outputs | `W-30.2` (after `W-29.4`) → ~~`W-35.1`~~ **on main `840bf2e`** (built by sayeed) → `W-35.2` **Ready** → `W-37` **Ready** (after `W-29.2`, `W-36.2`) → `W-38` | `V097`–`V098` `W-35.1` (`V097` a `reference` script) · `V100`–`V101` `W-35.2` (`V100` a `reference` script) · `V102` `W-36.1` · `V103` `W-36.2` — reserved 2026-09-29; `V099` is the annual-update test fixture, not free · `V042`–`V081` reserved 2026-09-25 (extended by one for `W-27.2`, one for `W-28`, two for `W-29.1`, one each for `W-29.2`, `W-29.3`, `W-29.4`, `W-30.1`, `W-30.2`, eight for `W-31`, twelve for `W-32`); `V042`–`V045` are `W-26.1`, `V046`–`V050` are `W-26.2`, `V051` is `W-27.1`, `V052`–`V053` are `W-27.2`, `V054` is `W-28`, `V055`–`V056` are `W-29.1`, `V057` is `W-29.2`, `V058` is `W-29.3`, `V059` is `W-29.4`, `V060` is `W-30.1` (a `core` script, **used, on main `b6e6012`**), `V061` is `W-30.2`, `V062`–`V063` are `W-31.1`, `V064`–`V067` are `W-31.2` (`V064` a `reference` script), `V068`–`V069` are `W-31.3`, `V070`–`V072` are `W-32.1` (`V070` a `reference` script), `V073`–`V076` are `W-32.2`, `V077`–`V079` are `W-32.3`, `V080`–`V081` are `W-32.4` |

**2026-09-25.** sayeed holds every open defect (D-2, D-3, D-6, D-7, D-8, D-9) and follows
them with the employee chain, since `W-13.4` and `W-13.3` both touch `core.employee`. No
`dev-<name>` branch exists yet — the stray `dev-claude` and `W-10-identity` branches were
deleted the same day. Each developer creates `dev-<name>` from `main` when they start their
first ticket. `W-11.3` is on `main`, so nothing waits on permission codes.

The specs still cite migration numbers already used on `main`; **use the lane's reserved block,
not the number in the spec.** `V026` `W-13.4` · `V027` `W-09.1` · `V028`–`V029` `W-14.2` ·
`V030` `W-39.1` (**used, on main `3bf5b10`**) · `V031`–`V032` `W-19` (**used, on main `b6e6012`**) · `V033`–`V034` `W-12.1` (**used, on main `b7d03ec`**) · `V035` `W-24.1` · `V036` `W-17` ·
`V037` `W-21` · `V038`–`V039` `W-20.1` · `V040` `W-23.1` · `V041` `W-39.2` (**all used, on main `b6e6012`**). `V082` `W-65.1` · `V083`–`V084` `W-65.2` (reserved 2026-09-27, above the `V042`–`V081` block) · `V085`–`V088` `W-41` (reserved 2026-09-28) · `V089`–`V092` `W-15.1`–`W-15.3` (**used, on main `a34c14f`**) · `V093` `W-20.2` · `V094` `W-22.2` · `V095`–`V096` `W-23.2` (**used, on main `b6e6012`**; reserved 2026-09-28, above the `V089`–`V092` block — manager's review item B-1: devashis's branch first shipped these as `V082`–`V085`, which collided with `W-65.1`, `W-65.2` and `W-41`'s reservations above; renumbered before merge) · `V104` `W-33.3` (reserved 2026-09-29, above `W-36.2`'s `V103`; `W-33.1` and `W-33.2` create no table) · `V107` `W-36.3` · `V108` (`core`)–`V109` `W-36.5` (reserved 2026-09-29, above `V104`; `W-36.4` creates no table) · `V110`–`V114` `W-34.1` · `V115` `W-34.2` (reserved 2026-09-29, above `V109`; `W-34.3` creates no table) · `V116` `W-18.1` · `V117`–`V118` `W-24.2` (reserved 2026-09-29, above `V115`).

**Assign later, cross-lane:** `W-24.2` is now **Ready** (`W-12.1` and `W-20.1` on `main`); `W-20.2`, `W-22.2`,
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
| D-9 | `W-10` spec §8 login flow never run by hand; `W-14.1` §8 never independently re-run | at merge | sayeed runs the two §8 checks | assigned — sayeed |

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
| `W-53.1` Cache cleanup | Delete the unused `core.cache` permission classes and the `core.queue` package | §5 row 22 | **on main `5c07c45`** · done — built by sayeed |
| `W-04.1` Test connection budget | `mvn verify` **fails on main** (2026-09-25, reproducible serially): `shared`'s `DatabasePrivilegesIT` dies with `53300 too_many_connections`. 16 `@SpringBootTest` classes each hold a Hikari pool of 10 against a 100-slot Testcontainers Postgres. Fix: one shared test context config with a small pool, or raise the container's `max_connections` | suite green | **on main `3350cf2`** · done — built by claude |
| `W-09.1` Age category seed | Seed `SENIOR` and `SUPER_SENIOR` slab rows for three financial years (defect #146). Migration `V027`. Deferred: the `V099` annual fixture gains the age pair when `W-33` first reads them | Stream B defect | **on main `c79755c`** · done — built by sayeed |

`W-52.1` and `W-53.1` are backend fixes to tickets tracked in [INFRA-TRACKER.md](INFRA-TRACKER.md).

| # | Ticket | What it is | Ready? |
|---|---|---|---|
| #11 | `W-10` Identity | Realm configuration, login flow, token validation, user profile sync, password reset delegated to Keycloak | **on main `ac1e531`** · code done — **spec §8 login never run by hand** — checked in by sanjib |
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
| #17–20 | `W-16.1`–`.4` Leave engine | Types and policy · allocation and balance · request, approval and documents · consumption, loss-of-pay derivation and bulk import. **The other riskiest ticket — a merge** | **`W-16.1` Ready — karma** (`W-15` on `main` `a34c14f`); `.2`–`.4b` follow |
| #21 | `W-17` Holiday calendar | Calendar per work location, holiday management, bulk import | **assigned — krushna**, after `W-24.1` |
| #22 | `W-18` Loss-of-pay & working-day policy | Policy model, working-day basis, derivation rules, the policy stamped on every pay figure | **assigned — krushna**, after `W-16`, `W-17` |
| #23 | `W-19` Pay input ledger | Write API for modules, read API for the pay run, period locking (`V031`–`V032`); one reversal per row | **Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: no employee-ownership check on write; null `source_ref` allowed over HTTP; lock race under READ COMMITTED; reversal of a locked tagged row becomes an untagged correction |
| #24 | `W-20` Notifications | Templates, email delivery, in-app notification, reminder rules, scheduler (`V038`–`V039`, `V093`) | **`W-20.1` and `W-20.2` Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: delivery claim ignores `next_attempt_at` and the attempt cap; rule PUT can overwrite a concurrent claim |
| #25 | `W-21` Document store | Upload, download by signed link (streamed through the app), soft delete, `read_own` (`V037`) | **Done 2026-09-29 on `b6e6012` — Built by devashis.** `document-link-secret` must exist before the automatic deploy |
| #27 | `W-23` Reporting & export | Report definitions, streamed CSV/XLSX export, schedules, async export, read-only pool (`V040`, `V095`–`V096`) | **`W-23.1` and `W-23.2` Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: report rows not yet read as `readonly_user`; `async=true` path untested; spec § 8 names `azurite`, the service is `blob` |
| #28 | `W-24` Setup checklist & invitations | A module-aware checklist, progress tracking, user and employee invitation | **assigned — krushna** (`W-24.1`), after `W-12.1`; `W-24.2` **Ready** (`W-20.1` on `main` `b6e6012`) |
| #29 | `W-25` Employee self-service portal | My profile, leave, documents, payslips (Payroll only), timesheet (HRMS only) | **assigned — karma**, after `W-16` |
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

`W-26.1` and `W-26.2` are on `main` (`a3ad0a3`); `W-27.1` and `W-27.2` on `main` (`79c8825`). `W-19` and `W-30.1` are on `main` (`b6e6012`); the rest is blocked behind `W-28`.

| # | Ticket | What it is |
|---|---|---|
| #30 | `W-26.1` Salary component catalogue | `payroll.earning`, `deduction`, `benefit`, `reimbursement` — tenant-scoped definitions; one `default_value` + `calculation_type` replaces two amount fields and a flag; `max_limit` becomes numeric. **Done 2026-09-28 on `a3ad0a3` — Built by biren.** Migrations `V042`–`V045` |
| #30 | `W-26.2` CTC structure & revisions | `payroll.ctc_structure` as one row per dated version plus its component rows and the statutory eligibility profile from `W-13.1` decision 1; the split is computed server-side, the pay run reads by date and writes nothing. **Done 2026-09-28 on `a3ad0a3` — Built by biren.** The three competing amount fields and the floating-point money are gone. Accepted as-is: `PUT` does not check the new date against today; BASIC matched by code for benefits; missing `calculation_type` defaults to `FLAT`; bare DTOs, no envelope (DEBT-008 open). Outstanding: no raw-SQL RLS write check in `ComponentRlsIT`; unused validation starter in the payroll pom. Migrations `V046`–`V050` |
| #31 | `W-27.1` FBP plan definition | `payroll.fbp`, one row per tenant: enabled, declaration window, lock, notification flags, reminder days; the plan's components are the `W-26.1` rows flagged `is_fbp_component`. Mails stored, not sent (`W-20.x`). **Done 2026-09-28 on `79c8825` — Built by biren.** Migration `V051` |
| #31 | `W-27.2` FBP employee declaration | `payroll.employee_fbp_component`, one row per FBP line per salary version; the employee declares under `/me/fbp-declaration` while the window is open, the officer any time; the version-in-force read shows the declared and unallocated amounts, which `W-29` consumes; carried forward on revise. Three new action codes. **Done 2026-09-28 on `79c8825` — Built by biren.** Outstanding in the merge commit; the future-dated-revise declaration gap carried into `W-29.1`. Migrations `V052`–`V053` — size-cap exception granted 2026-09-25 |
| #32 | `W-28` Pay schedule | `payroll.pay_schedule`, one row per tenant: work week, pay-day rule, input cut-off day, first period; implements `W-18.1`'s `WorkingWeekSource` and derives every period's dates for `W-29`. **Per `D-60`:** the basis and the payable flags stay on `core.lop_policy`; the screen is `W-47`'s. **Spec Ready 2026-09-25 — assigned krushna**, after `W-18.1`. Migration `V054` |
| #33 | `W-29.1` Pay run — creation, inclusion, locking | `payroll.payrun` and `payroll.employee_payrun`; one non-cancelled run per tenant and period by a partial unique index; every employee considered gets a row, `INCLUDED` or `SKIPPED` with a reason; lock calls `W-19`'s `PayInputService.lock`; the eight-value status vocabulary for all four parts. **Assigned 2026-09-29 — krushna**, after `W-28` (`W-26.2` and `W-19` are on `main`). Migrations `V055`–`V056` — size-cap exceptions (two scripts, one `core` read method) granted 2026-09-25 |
| #34 | `W-29.2` Pay run — earnings and deductions | `payroll.employee_payrun_line` with code and name snapshots, money columns on the two `W-29.1` tables, all `numeric(19,4)`; `POST /compute` runs every `PayLineContributor` in order and sums; this ticket ships the `STRUCTURE` contributor only — statutory is `W-31`, tax is `W-36`, LOP and pay inputs are `W-29.3`. **Assigned 2026-09-29 — krushna**, after `W-29.1` (`W-26.2` and `W-27.2` are on `main`). Migration `V057` |
| #35 | `W-29.3` Pay run — loss of pay and pay inputs | Two more contributors in the `W-29.2` loop: `LOP` scales the pro-rata lines by `W-18.1`'s divisor for LOP days and days outside the employment window, leavers included; `PAY_INPUT` turns the `W-19` ledger into lines, one per kind, read once per run. No HRMS call. Negative net kept and counted; hours-only overtime counted as unpriced, not paid. The stamp columns stay `W-18.2`'s. **Assigned 2026-09-29 — krushna**, after `W-29.2`, `W-18.1` (`W-19` is on `main`). Migration `V058` |
| #36 | `W-29.4` Pay run — async on the worker | `POST /compute` returns `202` and enqueues on `payrun`; `PayrunQueueListener` calls `W-29.2`'s service; progress on the run and on `core.job_status`; resume by attempt number after a dead worker, stale after 15 minutes; duplicate messages never compute twice. The last part of the merge. **Assigned 2026-09-29 — krushna**, after `W-29.3` (`W-52.1` is on main `5c07c45`). Migration `V059` |
| #37 | `W-30.1` Pay input run tag | `run_ref` on `core.pay_input` and on the lock table, no FK; `forRun`, `lockRun`; a tagged row obeys its run's lock, not the period's, and `forPeriod` returns untagged rows only. No table. **Done 2026-09-29 on `b6e6012` — Built by devashis.** Outstanding in the merge commit: the tagged-row-ignores-period-lock rule proven only against a mock; `lockRun` tenant scoping untested. Migration `V060` |
| #37 | `W-30.2` Off-cycle pay run | `run_type = 'OFF_CYCLE'` on `payroll.payrun`, zero new tables (founder 2026-09-25); named employees, bank only; `POST /payruns/{id}/inputs` writes tagged ledger rows; `STRUCTURE` and `LOP` contribute nothing, `PAY_INPUT` reads `forRun`; the one-per-period index narrowed to regular runs. One-time payout and bonus are `W-19`/`W-29.x`'s; withheld-salary release dropped. **Spec Ready 2026-09-25 — unassigned**, after `W-29.3` (`W-30.1` is on `main`). Migration `V061` |
| #38 | `W-31.1` EPF and ESI settings | `payroll.epf_setting`, `payroll.esi_setting`, one row per tenant; numeric rates and wage ceilings replace text like `"12.00%"` and the browser's `15000`; defaults returned without a row. **Done 2026-09-29 on `ac94662` — Built by sayeed.** Outstanding in the merge commit. Migrations `V062`–`V063` |
| #38 | `W-31.2` Professional tax | Shared state slabs in `reference.pt_state` / `pt_slab` (founder decision 2026-09-25), seeded from the state Acts for the 21 states legacy supports; tenant override as rows in `payroll.org_pt_override` / `org_pt_override_slab`, every change in `pt_history`; `resolve(gross, gender, period)` for `W-31.4`; female exemption and deduction months kept as slab columns. **Done 2026-09-29 on `ac94662` — Built by sayeed.** Outstanding in the merge commit. Migrations `V064` (`reference`), `V065`–`V067` |
| #38 | `W-31.3` Employee EPF and ESI lines | `payroll.ctc_epf_component`, `ctc_esi_component` — the two tables `W-26.2` §13 handed here; derived server-side on every version from `W-31.1`'s rates and the statutory profile, employee share included, scale 4, no rounding. **Done 2026-09-29 on `ed180b1` — Built by sayeed.** Outstanding in the merge commit. Migrations `V068`–`V069` |
| #38 | `W-31.4` Statutory pay-run lines | `StatutoryLineContributor` `@Order(400)`, the `STATUTORY` slot `W-29.2` reserved: employee PF, employee ESI and PT deducted, employer shares as `BENEFIT` (founder decision 2026-09-25); earned-wage scaling, rupee rounding once per line, PT on the period's month. No table. **Assigned 2026-09-29 — sayeed**, after `W-31.3`, `W-31.2`, `W-29.3` |
| #39 | `W-32.1` Tax declaration — window, submission and revision | `payroll.income_tax_declaration`, one row per tenant **per financial year** (window dates, manual lock, default regime, PAN-for-rent rule); `payroll.employee_investment_declaration`, one per employee per year with `DRAFT`/`SUBMITTED` and a per-employee lock; the employee submits and reopens under `/me/tax-declaration/{fy}` while the window is open, the officer any time; `editable()` for `.2`–`.4`; `FinancialYear` holds the April–March rule once; no auto-lock job (the date decides). Four action codes. **Done 2026-09-29 on `b704f3c` — Built by mohit.** Outstanding in the merge commit: declaration FK does not tie employee to tenant (service check covers it); a first-read race can return 500; 409/404 mappings not proven over HTTP. Migrations `V070` (`reference`), `V071`–`V072` |
| #40 | `W-32.2` Tax declaration — house rent, home loan, let-out property | Four tables `payroll.employee_inv_house_rent`, `_home_loan`, `_let_out_property`, `_let_out_property_line`; months as `date`, rent periods non-overlapping, landlord PAN enforced server-side over `reference.hra_rule_master`'s threshold; `net_income_loss` derived with the reference 30 %; no caps applied (that is `W-33`). **Done 2026-09-29 on `b704f3c` — Built by mohit.** Outstanding in the merge commit: declaration FK does not tie employee to tenant (service check covers it); a first-read race can return 500; 409/404 mappings not proven over HTTP. Migrations `V073`–`V076`, and `V105` (`reference`, FY 2026-27 HRA and let-out rules carried forward by founder decision) |
| #41 | `W-32.3` Tax declaration — section 6A, pre-tax deductions, previous employment | `payroll.employee_inv_section6a` (FK to `reference.section6a_item_master`, a description, several rows per item), `_pre_tax_deduction`, `_prev_employment` (`entered_by` — the officer's rows lock the section); per-item and per-group (`80C_GROUP`) limits enforced from the seed; regime filter on the catalogue. **Done 2026-09-29 on `b704f3c` — Built by mohit.** Outstanding in the merge commit: declaration FK does not tie employee to tenant (service check covers it); a first-read race can return 500; 409/404 mappings not proven over HTTP. Migrations `V077`–`V079` |
| #42 | `W-32.4` Tax declaration — other income and tax summary | `payroll.employee_inv_other_income` (four kinds) and `payroll.employee_inv_tax_summary`, one row per declaration per regime whose computed columns are nullable and written only by `W-33` through `TaxSummaryService.record`; `GET …/summary` totals every declared section server-side, never stored. **Done 2026-09-29 on `b704f3c` — Built by mohit.** Outstanding in the merge commit: declaration FK does not tie employee to tenant (service check covers it); a first-read race can return 500; 409/404 mappings not proven over HTTP. Migrations `V080`–`V081`, and `V106` (`reference`, FY 2026-27 home-loan and 80TTA/TTB rules for `W-33`, carried forward) |
| #43 | `W-33.1` Tax calculator — new regime and the engine | `RegimeCalculator` seam plus the shared engine (`SalaryProjection` from the CTC versions month by month, `SlabTax`, `Rebate87A`, `SurchargeAndCess` **with marginal relief**, `AgeCategory` by 31 March); `NewRegimeCalculator`; `GET …/tax` pure preview, `POST …/tax/compute` fills every regime's summary row through `W-32.4`'s `record`; every figure from a `reference` rule row keyed on year, regime and age; no table. **Ready 2026-09-29 — assigned mohit.** `W-32.1`–`.4` on `main` (`b704f3c`) |
| #44 | `W-33.2` Tax calculator — old regime | `OldRegimeCalculator` on the `.1` engine: HRA month by month, house-property loss capped **as a total**, 80TTA/80TTB by age, Chapter VI-A per-item then per-group caps, 80EE/80EEA by sanction window, standard deduction and PT under section 16, pre-tax precedence (structure first, declared row second); `V027` senior slabs read for the first time and the `V099` fixture gains them; no table, no hard-coded cap (grep gate). **Spec Ready 2026-09-29 — assigned mohit**, after `W-33.1`, `W-32.2`–`.4` (`W-31.2`, `W-31.3` optional) |
| #45 | `W-33.3` Tax calculator — revisions and recalculation | `payroll.tax_computation`, append-only, one row per computation with the working as JSONB (replaces legacy's five result tables); `TaxRecalculationService` runs after commit on `DeclarationSubmittedEvent` (`W-32.1`), `SalaryVersionChangedEvent` (`W-26.2`), `ProofVerifiedEvent` (`W-34` later) and an officer `POST`; calls `TaxSummaryService.record` and `EmployeeTdsService.record`; legacy's default TDS becomes a `SALARY_DEFAULT` computation on the first salary event, never in the run. **Spec Ready 2026-09-29 — assigned mohit**, after `W-33.2` and `W-36.1` (assigned mohit 2026-09-29). Migration `V104` |
| #46 | `W-34.1` Proof of investment — window and submission | Five `poi_*` columns on the per-year window; `payroll.employee_proof_of_investment`, `employee_proof_item` (one per declared line: house rent, home-loan principal and interest, let-out line, 6A, employee-entered previous employment), `employee_proof_item_document` → `core.document`. Employee claims, uploads, submits against a `SUBMITTED` declaration; publishes `ProofSubmittedEvent`; declaration reopen refused while proof `SUBMITTED`/`APPROVED`. **Spec Ready 2026-09-29 — assigned mohit.** Size-cap exception requested (five scripts). Migrations `V110`–`V114` |
| #46 | `W-34.2` Proof of investment — verification, comments and return | Starts the `PROOF_OF_INVESTMENT` engine instance on submit; item `APPROVE`/`DISALLOW`/`RETURN` and final decision through a payroll wrapper; `ProofOutcomeHandler` writes the outcome, publishes `ProofVerifiedEvent`, notifies; `TaxInputGatherer` prefers approved amounts; flat item comments. **Spec Ready 2026-09-29 — assigned mohit**, after `W-34.1`, `W-33.3`. Migration `V115` |
| #46 | `W-34.3` Proof of investment — reminders and chase list | `POI_DUE_DATE` anchor and `POI_PENDING` audience resolvers for `W-20.2`'s sweep (replaces legacy's own cron, DEBT-021); chase list with `NOT_STARTED`, status counts. **Spec Ready 2026-09-29 — assigned mohit**, after `W-34.1`. No migration |
| #47 | `W-35.1` Reimbursement claims | `payroll.employee_reimbursement_request` against a `payroll.reimbursement` component, receipt as a `W-21` `document_id`; submit starts the seeded `REIMBURSEMENT` flow; the outcome handler writes one `REIMBURSEMENT` ledger row for the approved amount and stores `posted_period` (the ledger redirects a locked period); no approve endpoint, no paid flag; legacy `ReimbursementClaim` settings row retired. **Done 2026-09-29 on `840bf2e` — Built by sayeed.** Outstanding in the merge commit. Migrations `V097` (`reference`), `V098` |
| #47 | `W-35.2` Ad-hoc salary deductions | `payroll.employee_deduction`, batch entry 1–500 all-or-nothing, each line an `AD_HOC_DEDUCTION` ledger row in the same transaction; `DELETE` reverses on the ledger, never edits; `POSTED` / `REVERSED` only; closed list of six types; proof as `EMPLOYEE_DOCUMENT`. **Spec Ready 2026-09-29 — unassigned.** Migrations `V100` (`reference`), `V101` |
| #48 | `W-36.1` Employee TDS record and pay run tax line | `payroll.employee_tds`, one active row per employee per financial year, superseded not edited; written by `EmployeeTdsService.record` (officer `PUT` now, `W-33` later), never by the run; `TaxLineContributor` `@Order(500)` writes one `TAX` `DEDUCTION` line = (annual tax − year-to-date from the lines of `COMPUTED`/`APPROVED`/`PAID` runs) ÷ months left to March. **Assigned 2026-09-29 — mohit.** After `W-29.2`, `W-32.1`. Migration `V102` |
| #48 | `W-36.2` Payslips — approve, pay, render, signed link | `approve` (`COMPUTED→APPROVED`) and `pay` (`APPROVED→PAID`, `paid_on`) land here, closing `W-47.2` §13 decision 1; on `PAID` one `PAYSLIP_READY` per employee with a 7-day HMAC link, same secret as `W-21` under its own domain tag; slip rendered from the row and lines, never stored; anonymous read at `PublicEndpoints.PAYSLIP_OPEN`, every refusal `404`, token never logged (`PayslipLogIT`). **Assigned 2026-09-29 — krushna.** After `W-29.3`; one constant added in `shared`. Migration `V103` (columns on `payroll.payrun`) |
| #48 | `W-36.3` Tax deductor details | `payroll.tax_deductor`, one row per tenant: TAN, PAN, TDS circle, signatory (an employee or a named person), real formats checked server-side; `PUT`/`GET` under `/api/v1/payroll/settings/tax-deductor`, `payroll.settings.manage`; ports legacy `incomeTaxDetails` (`02-data-model.md` `income_tax_detail`), deposit schedule dropped. **Spec Ready 2026-09-29 — assigned mohit.** Split from the first `W-36.3` (Form 16) on 2026-09-29. Migration `V107` |
| #48 | `W-36.4` Annual statement (Form 16) | Rendered, never stored: deductor (`W-36.3`), quarterly tax deducted from `TAX` lines of `PAID` runs, payable = `employee_tds.annual_tax`, Part B breakdown from the latest `tax_computation` only when it matches (else `OFFICER_OVERRIDE`), `final` once March is paid; officer read and `/me`; JSON, no PDF. **Spec Ready 2026-09-29 — assigned mohit**, after `W-33.3`, `W-36.1`, `W-36.3`. No table |
| #48 | `W-36.5` Form 16 Part A upload | Officer uploads the TRACES Part A ZIP; each PDF is matched to an employee by the PAN in its file name and stored as a `W-21` document of new system kind `FORM16_PART_A`; `payroll.form16_part_a` links employee and year, re-upload supersedes; employee downloads own by signed link. **Spec Ready 2026-09-29 — assigned mohit**, after `W-36.4`. Migrations `V108` (`core`), `V109` — size-cap exception requested (two small `core` seams). Open: encrypted ZIPs, real TRACES file naming |
| #49 | `W-37` Payroll dashboard | One read-only `GET /api/v1/payroll/dashboard` for the bound tenant: active headcount, the last run's skipped-by-reason, current and recent run cards, April-to-March months with `PAID` year totals, EPF/ESI/PT/TDS tiles summed from `employee_payrun_line`; no table, no cache, no screen (`W-47.5`). **Ready** — spec `W-37-payroll-dashboard.md` written 2026-09-29; waits on `W-29.2` and `W-36.2` |
| #50 | `W-38` Prior payroll import | Import template, validation, load. **Source settled 2026-09-25: a fixed spreadsheet template, one row per employee per month**, loaded through the `W-16.4b` import pattern |

> **Tax is the largest and most compliance-exposed area.** `W-33.2` (#44) was next in the
> queue once `W-09` shipped its 15 reference tables, and carries three conditions from
> `W-09`'s review: Chapter VI-A has no `financial_year`, `home_loan_rule_master` has no
> regime column, and loss carry-forward defaults FALSE.

---

## 5. Stream E — HRMS

Nothing started. `W-41` is Ready, assigned to devashis (2026-09-29), and needs only `W-13`; the rest wait on `W-16`.

> **Renumbered 2026-09-24**, following `10-scoping.md:93,117`. `W-39` is Core's basic
> attendance and overtime capture (`D-35`), now in Stream C. HRMS attendance and the
> overtime request are one ticket, `W-40`.

| # | Ticket | What it is |
|---|---|---|
| #51–52 | `W-40` Clock attendance & request workflows | Clock in and out, multiple sessions a day, attendance preferences moved over from Payroll, and the employee-submitted, manager-approved regularization and overtime requests. Blocked on `W-16` only (`W-39.1`, `W-39.2` and `W-15` on `main`) — **assigned — karma** |
| #53 | `W-41` Projects, tasks, assignments | Project and task management, employee assignment — **Ready — devashis** (assigned 2026-09-29; spec 2026-09-28; needs only `W-13`, on `main`) |
| #54 | `W-42` Timesheets | Weekly timesheet, project, day and task entry, submit, approve |
| #55 | `W-43` Timesheet reminders | Reminder rules, escalation, notification trigger |
| #56 | `W-44` HRMS dashboards | Manager view, employee view |

---

## 6. Stream F — Frontend

Nothing started. `W-45` and the six `W-46` parts are **assigned to biren** (2026-09-28), in the order in his lane above. `W-45` is Ready with a spec (2026-09-27) now that `W-12` is on `main` (`a4f31ae`).

| # | Ticket | What it is |
|---|---|---|
| #57 | `W-45` Shell | Layout, navigation driven by entitlement, **a real API service layer** (Payroll has none today), Keycloak adapter, runtime configuration, design tokens. **Spec written 2026-09-27** — `W-45-frontend-shell.md`; four of the six items shipped under `W-12.3`, so the ticket is the service-layer pattern, full tokens, shell error screens and the lint rules that enforce module boundaries. Size M. **Ready — biren** |
| #58 | `W-46.1` Employee screens | List, create, employee page with the five detail sections and reporting line. **Spec written 2026-09-28** — `W-46-1-employee-screens.md`. Size M. **Ready — biren**, after `W-45` only |
| #59 | `W-46.2` Leave administration screens | Types and policy, allocations, record on behalf (`D-35`), all requests, consumption and LOP, import; adds the `core.leave` menu items. **Spec written 2026-09-28** — `W-46-2-leave-admin-screens.md`. Size L. **Assigned — biren**, after `W-45`, `W-16.1`–`.4b` |
| #60 | `W-46.3a` Organisation setup screens | Departments, designations, work locations. **Split 2026-09-28** from `W-46.3`; spec `W-46-3a-org-setup-screens.md`. Size S. **Ready — biren**, after `W-45` only |
| #60 | `W-46.3b` Holiday calendar screens | Calendars per location, holidays, lookup; adds the `core.holiday` menu item. Spec `W-46-3b-holiday-screens.md`. Size S. **Assigned — biren**, after `W-45`, `W-17` |
| #61 | `W-46.4` Approval screens | One inbox for every flow type, decide, delegations, definitions, history, reassign; adds the `core.approvals` menu items. **Spec written 2026-09-28** — `W-46-4-approval-screens.md`. Size M. **Assigned — biren**, after `W-45`, `W-15.1`–`.3` |
| #62 | `W-46.5` Employee self-service actions | Apply, withdraw, cancel leave; edit own personal and contact, inside `W-25`'s portal panels. Owns the apply screen `W-25` and `W-16.3` each assign to the other. **Spec written 2026-09-28** — `W-46-5-self-service-actions.md`. Size M. **Assigned — biren**, after `W-45`, `W-25`, `W-16.3` |
| #63 | `W-47.1a` Salary structure screens | Salary component catalogue (four kinds), Salary tab on the employee page with dated versions, revise, cancel, statutory profile. **Split 2026-09-28** from `W-47.1`; spec `W-47-1a-salary-structure-screens.md`. Every endpoint on `main` (`a3ad0a3`). Size M. **Ready — unassigned**, after `W-45`, `W-46.1`. Open: no `payroll.*` menu item exists in the catalogue (spec § 14) |
| #63 | `W-47.1b` Payroll settings screens | Pay schedule **with** the LOP basis on one screen (`D-60`), EPF, ESI, professional tax with per-state override, FBP plan, FBP tab on the employee page. Spec `W-47-1b-payroll-settings-screens.md`. Size M. **Assigned 2026-09-29 — sayeed**, after `W-45`, `W-28`, `W-18.1`, `W-31.1`, `W-31.2` (`W-27.1` and `W-27.2` are on `main`) |
| #64 | `W-47.2` Pay run screens | Run list, create for a period, run page with included and skipped, compute with progress, lines per employee, lock, cancel, off-cycle run with tagged inputs. No approve, pay or payslip: no endpoint exists (spec § 14). **Spec written 2026-09-28** — `W-47-2-pay-run-screens.md`. Size L. **Ready — unassigned**, after `W-45`, `W-29.1`–`.4`, `W-30.2` |
| #65 | `W-47.3` Tax declaration screens | Declaration window settings per FY; the employee's declaration under `/me` in four sections with submit and reopen; officer header view. Calculator, proof and Form 16 wait for `W-33`, `W-34`, `W-36` specs. **Spec written 2026-09-28** — `W-47-3-tax-declaration-screens.md`. Size L. **Assigned 2026-09-29 — mohit**, after `W-45`, `W-25`, `W-32.1`–`.4`. Open: the `/me` panel bean and mount slot (spec § 14) |
| #66 | `W-47.4` Claims screens | Reimbursement claim, approval queue, ad-hoc deductions. **Blocked — waits on `W-35.2`** (`W-35.1` on `main` `840bf2e`), whose specs are Ready 2026-09-29 and give the API contract (`W-35-1-reimbursement-claims.md` §4, `W-35-2-ad-hoc-deductions.md` §4). Screen spec not written |
| #67 | `W-47.5` Payroll dashboard | Run status and summary widgets, onboarding checklist. **Blocked — waits on `W-37`**, whose spec is Ready 2026-09-29 and gives the API contract (`W-37-payroll-dashboard.md` §4). The frozen dashboard is demo data plus one call (`dashboardPage/index.js:15-34,120`). Spec not written 2026-09-28 |
| #68 | `W-48` HRMS screens | Attendance, timesheet, projects, dashboards. **Rewritten from MUI to Ant Design** |
| #85 | `W-65.3` Admin console screens | Tenant list, create, module and status switches, act-as banner, audit tab, in `src/core/admin`. **Spec written 2026-09-27** — `W-65-3-admin-console-screens.md`. Size M. **Ready — unassigned**, after `W-45`, `W-65.1`, `W-65.2` |

---

## 7. Product items in streams G and H

| # | Ticket | What it is | Status |
|---|---|---|---|
| #75 | `W-55` Index & query standard | Tenant-leading index conventions, related-data fetching in one query, connection pooling. Calibrated to scale — the tables that matter are the ones growing with time: attendance, pay run lines, tax detail | **on main `2ccd723`** · done — checked in by biren |
| #85 | `W-65` Admin console | Tenant list, subscription management, module toggle, support impersonation, audit view. **The real onboarding tool, since there is no payment step.** Split 2026-09-27 into three, specs written: `W-65.1` tenant list and the Infinevo platform tenant (`core`, `V082`) · `W-65.2` support impersonation (`shared`, `V083`–`V084`, after `W-65.1`) · `W-65.3` screens (frontend, after `W-45`, `W-65.1`, `W-65.2`). Audit is read one tenant at a time while impersonating, no cross-tenant view | **`W-65.1` Ready — devashis** (assigned 2026-09-29) · `W-65.2` **devashis**, after `W-65.1` · `W-65.3` in stream F |
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
