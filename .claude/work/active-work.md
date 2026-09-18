# Active Work

> Live project state. **Read this before starting any task** (root `CLAUDE.md` rule 2).
> Last refreshed: **2026-09-18**, after `W-49` Containerisation (#69, PR #116) merged
> as `c0a8643` and its `sync-docs` pass.
> Tracked, not gitignored — it is how everyone sees where the project stands.

## Where the project is

**Design finished. Build started. Nothing in production.**

| | |
|---|---|
| Repository | `infinevocloud-HCM-Suite/infinevo-platform`, private |
| Tickets | **100** — 9 closed, 91 open. Three of them (#100 #101 #104) are defects the gates found in themselves while `W-03` was built; #100 is already closed. GitHub is authoritative, this file is the summary |
| Waves | 9. **Wave 1 has 5 of 7 done** |
| Merged | `W-01` skeleton (#1) · `W-02` local stack (#3) · process skills and merge gate (#97) · `W-03` build pipeline (#4) · docs route through gate 5 (#100) · `W-04` test foundation (#5) · `W-05` Postgres & schemas (#6) · docs back in line with `W-05` (#114) |
| Team | `developers`, Write access. Gau318 `#6` · BirenGit `#69` · SayInfi `#5` |

---

## Current direction

**One platform replacing four applications, then Azure.** A customer buys HRMS,
Payroll, or both, and upgrades with a switch rather than a re-onboarding.

One repository · one backend with three enforced modules (`core` / `hrms` / `payroll`)
· one React frontend · **one Postgres database with four schemas** · one login
(Keycloak, single realm) · **Azure Container Apps**.

> **Not AKS. Not MySQL. No subtree, no sync.** If a document or skill says otherwise it
> is stale — the decisions are `D-09` Postgres, `D-10` Container Apps, `D-17` no sync.

Design: `docs/target-state/` — 12 documents, **49 decisions (`D-01`–`D-49`), zero open
questions.** Start at `docs/target-state/README.md`.

> **`W-06` Flyway is closed as a ticket but its code is NOT on `main`.** It merged as
> `a0cdb2f` and was reverted by Sayeed as `ae761ed` on 2026-09-17; issue #7 is still
> CLOSED. `main` carries only the empty `.gitkeep` migration directories from `W-01`.
> The work is on `origin/W-06-flyway-migrations` at `ad3f3c6`. **This blocks the tenancy
> chain** — `W-07` and `W-08` wait on `W-06`, and the ticket currently reads as done
> while the code is absent. Needs a decision: re-merge, reopen #7, or record why it was
> reverted.

### The three threads

1. **The platform repository and a runnable stack — done.** `W-01` gave seven Maven
   modules with the `hrms` ↔ `payroll` boundary enforced by the build. `W-02` gave nine
   containers from one command — `docker compose -f infra/docker/compose.yml up -d`,
   all healthy in 114 seconds. `app` connects as `app_user` and is refused DDL.
   `W-05` made the database posture canonical: `infra/postgres/` holds the three SQL
   scripts and `provision.sh` that local Docker, Testcontainers and (at `W-50`) Azure
   all run, four schemas owned by `migration_user`, four roles none of which is
   superuser or `BYPASSRLS`, and `DatabasePrivilegesIT` asserting the matrix in both
   directions.
2. **Multi-tenancy — the highest-value work in the project. Blocked: see the `W-06`
   note above — its code is not on `main`. `W-05` merged, so the
   chain is unblocked and `W-06` Flyway (#7) is the next link.** Payroll is
   org-scoped on 63 of 97 entities; HRMS on **none at all** (0 of 39, `BUG-002`).
   Target: `tenant_id` on every table outside `reference`, enforced by Postgres
   row-level security (`D-09`). `W-06` → `W-07` → `W-08` is the one rigid chain.
3. **Azure — opened.** `W-49` containerisation **merged 2026-09-17 (#116)**: three
   production images — backend carrying both `app.jar` and `worker.jar` selected by
   `INFINEVO_ROLE` (`D-48`), unprivileged nginx on 8080 (`D-49`), and Keycloak with no
   realm baked in. All non-root, no secrets, 154 / 24 / 225 MB, built and gated by CI
   which pushes nothing. **`W-50` is now unblocked** and is the next Azure step.
   Two things `W-49` left behind: the container scan's "report-only until `W-49`"
   condition has expired, so `W-59` must now decide whether it blocks; and the Keycloak
   image runs with primary group 0 (root), also handed to `W-59`.

### Code is ported, never synced

The four frozen applications are snapshots in `legacy/`, taken 2026-09-13. **Production
fixes made after that date do not arrive automatically** (`D-17`). Payroll was actively
developed to the day of the freeze, so divergence starts now — keep a list of post-freeze
production fixes or they are lost at cutover.

---

## The queue — claimed, not assigned (#107, 2026-09-15)

Developers pull the next `ready` ticket themselves; `tickets.yml` enforces one owner,
a WIP limit of 2, and releases blocked tickets when their blockers close. This file
no longer tracks who holds what — **the assignee field on GitHub is the only truth**:
`gh issue list --label ready --no-assignee` is the queue, `--label next` is its head.

| Issue | Ticket | Size | Skill | Why it is at the head |
|---|---|---|---|---|
| **#7** | `W-06` Flyway migrations | M | DATA | **Closed, but reverted off `main` — see the note above.** `W-06` → `W-07` → `W-08` is what Wave 3 waits on, so nothing in the chain can start until this is resolved |
| **#70** | `W-50` Azure infrastructure as code | L | INFRA | **Newly unblocked** — `W-49` merged 2026-09-17, so the images `W-50` deploys now exist |
| #79 | `W-59` Scanning | M | INFRA | **Two inherited decisions have come due**: the container scan's "report-only until `W-49`" condition has expired, and the Keycloak image's primary group 0. Also modifies the same `images` job `W-49` just rewrote — read it before editing |
| #86 | `W-66` Marketing website | L | FE | Independent of the chain |
| #117 | Testcontainers does not detect Docker — `DatabasePrivilegesIT` skips silently | S | INFRA | Found by `/verify W-49`. 13 of 34 backend tests skip under a green build, including the whole owner-privilege suite. Every `verify` proves less than it appears to until this is fixed |
| #101 | Merge-gate hardening — 3 defects from `W-03` | S | INFRA | Small, unblocks nothing but hardens `/merge` |
| #104 | Three gate paths never executed; harness not in CI | S | INFRA | Same |
| #112 | `W-05` follow-ups — test classpath, CI never runs the stack | S | INFRA | **`F-18` is the leverage item.** CI runs neither `compose up` nor `smoke.sh`, which is why three High defects that stopped Postgres booting passed gate 10 green. Fold into #101/#104 |

The founder steers by keeping the `next` label on three to five tickets, in order.

`W-49` (#69) inherited the `images` job from `W-03` and replaced its dev Dockerfile
targets with the production ones — **done, merged 2026-09-17**. The job now enables the
containerd image store (`D-47`), builds three production and two dev images, runs a
three-part secret scan and asserts size thresholds. **`W-59` (#79) edits the same job**
and was written before any of that existed; whoever picks it up reads `ci.yml` first.

---

## What the gates found in themselves

`W-03` built the pipeline; building it surfaced defects in the gates that were supposed
to be checking the work. Recorded here because the pattern matters more than the items:
**every one was found by verify or review, none by the person who wrote it.**

| # | Open | What |
|---|---|---|
| #101 | yes | Gate 10's bootstrap fallback is dead code now `ci.yml` is on `main`; gate 10 checks the pushed PR head while gates 5-9 check the local tree; `guard-merge` diffs the current `HEAD` rather than the ref being pushed |
| #104 | yes | The `legacy/` gate has never been proved, `images` has never been observed red, and the 62-case gate-5 harness is invoked by nothing |
| #100 | closed | Gate 5 had no route for a `docs/` file that is not a ticket spec — and the first fix had four working bypasses, each reproduced before merge |

The four bypasses in #100 are worth knowing about, because they are how a gate stops
refusing: an approval that bound paths but not content; a marker hidden in a fenced code
block, which made the skill's own template a passing approval file; a branch named
`w-04-tenant` taking the permissive route on one lowercase letter; and the same hiding
trick in markdown's other code syntax. Gate 5 now binds content by blob sha, requires the
approval to be **added** by the pull request, and gates 2, 3 and 5 share one ticket
matcher so no seam opens between them.

---

## Frozen — `legacy/`

All four applications are frozen as of 2026-09-13 and live in `legacy/`. Read them,
port logic out of them, cite their `file:line`. **Never edit them** — `guard-edit`
blocks it, and a change there is not deployed anywhere.

| Folder | Branch taken | Commit |
|---|---|---|
| `legacy/HRMS_Backend` | `main` | `d984c64` · 2026-06-16 |
| `legacy/HRMS_Frontend` | `main` | `c72116c` · 2025-12-17 |
| `legacy/Payroll-Bend-SBoot` | **`taxation`** | `39b37d6` · 2026-09-09 |
| `legacy/Payroll-Fend-react` | **`employee`** | `053ca62` · 2026-09-10 |

**The two Payroll apps are not on `main`.** `taxation` and `employee` are the live
branches; their `main` is 9 and 13 months behind. The snapshots are of the live branches.

How they work: `legacy/docs/` — `ARCHITECTURE.md`, `DB_SCHEMA.md`, `FEATURE_MAP.md`,
`GAP_INVENTORY.md`.

---

## Incidents — the running system

**These are operational, not target-state work.** Do not wait for this programme.

| # | Issue | Action |
|---|---|---|
| 1 | Keycloak administrative credentials committed with a trivial password — `legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:16-21` | **Rotate now.** Assume the value is compromised; check the realm for unexpected users and clients |
| 2 | `POST /public/get-employee-leaves` returns employee leave data with **no authentication** — `legacy/HRMS_Backend/.../IntegrateWithPayroll.java:29,64` | Check whether it is reachable from the internet |
| 3 | Signed payslip token written to the logs | Stop logging it |

Evidence: `.claude/outputs/2026-09-11-security-finding-public-endpoint.md`.
The target state removes all three (`D-22`, `D-23`, `W-57`).

---

## Open questions

**None.** The design closed on 2026-09-13 — `docs/target-state/07-decisions.md` §2.

Three former questions became migration decisions rather than design blockers, and are
settled at `W-67` before any row moves: which timesheet system survives, which leave
entities are authoritative, and whether the HRMS→Payroll leave integration carries over.

---

## Known constraints

| | |
|---|---|
| **No branch protection** | GitHub refuses it on private repositories on the Free plan (`D-43`). `main` is convention, not enforcement, until the plan changes |
| **Pipeline is advisory** | `W-03` merged 2026-09-14. Four jobs on every PR, but `D-43` means CI cannot be a *required* check — gate 10 of the ten-gate done-check enforces it locally instead |
| **`W-07` owes the two-tenant seed** | `W-02` shipped the loader; `core.tenant` does not exist yet. Seed one tenant holding everything and entitlement bugs stay invisible until a customer buys one module |
| **Nobody has run the stack but me** | `W-02` done-when item 11 is unticked. Have a developer run `up -d` and `smoke.sh` |
| **Test foundation is in, coverage is not** | `W-04` merged 2026-09-15. `AbstractIntegrationTest` runs a real Postgres 16 as non-owner `app_user`; 24 tests, all in `shared`. Three things to know: integration tests are **skipped silently without Docker** (CI has it, laptops may not); `W-05` gave Failsafe its first real match, `DatabasePrivilegesIT` (10 tests, green in CI); `app_user` was created by the initializer, not the bootstrap script — **`W-05` switched it**, so the initializer now runs the canonical `infra/postgres/` scripts |
| **Toolchain** | Java 21, Maven 3.9.11 (`C:/Tools/apache-maven-3.9.11`), Node 24. `D-38`–`D-42` |

---

## Related

`docs/target-state/README.md` · `CONTRIBUTING.md` · `README.md` ·
`.claude/outputs/` for the investigations the design rests on
