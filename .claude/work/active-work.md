# Active Work

> Live project state. **Read this before starting any task** (root `CLAUDE.md` rule 2).
> Last refreshed: **2026-09-15**, after `W-04` test foundation (#5, PR #105) merged.
> Tracked, not gitignored — it is how everyone sees where the project stands.

## Where the project is

**Design finished. Build started. Nothing in production.**

| | |
|---|---|
| Repository | `infinevocloud-HCM-Suite/infinevo-platform`, private |
| Tickets | **97** — 6 closed, 91 open. Three of them (#100 #101 #104) are defects the gates found in themselves while `W-03` was built; #100 is already closed. GitHub is authoritative, this file is the summary |
| Waves | 9. **Wave 1 has 4 of 7 done** |
| Merged | `W-01` skeleton (#1) · `W-02` local stack (#3) · process skills and merge gate (#97) · `W-03` build pipeline (#4) · docs route through gate 5 (#100) · `W-04` test foundation (#5) |
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

Design: `docs/target-state/` — 12 documents, **44 decisions (`D-01`–`D-44`), zero open
questions.** Start at `docs/target-state/README.md`.

### The three threads

1. **The platform repository and a runnable stack — done.** `W-01` gave seven Maven
   modules with the `hrms` ↔ `payroll` boundary enforced by the build. `W-02` gave nine
   containers from one command — `docker compose -f infra/docker/compose.yml up -d`,
   all healthy in 114 seconds. `app` connects as `app_user` and is refused DDL.
2. **Multi-tenancy — not started, the highest-value work in the project.** Payroll is
   org-scoped on 63 of 97 entities; HRMS on **none at all** (0 of 39, `BUG-002`).
   Target: `tenant_id` on every table outside `reference`, enforced by Postgres
   row-level security (`D-09`). `W-06` → `W-07` → `W-08` is the one rigid chain.
3. **Azure — not started.** `W-49` containerisation opens it, then `W-50`.

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
| **#6** | `W-05` Postgres & schemas | S | DATA | Opens `W-06` → `W-07` → `W-08`, the tenancy chain everything in Wave 3 waits on |
| #69 | `W-49` Containerisation | M | INFRA | Inherits the `images` job from `W-03` |
| #86 | `W-66` Marketing website | L | FE | Independent of the chain |
| #101 | Merge-gate hardening — 3 defects from `W-03` | S | INFRA | Small, unblocks nothing but hardens `/merge` |
| #104 | Three gate paths never executed; harness not in CI | S | INFRA | Same |

The founder steers by keeping the `next` label on three to five tickets, in order.

`W-49` (#69) **inherits the `images` job** from `W-03` and must replace its dev
Dockerfile targets with the production ones. BirenGit needs telling — the two tickets
were written by different people.

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
| **Test foundation is in, coverage is not** | `W-04` merged 2026-09-15. `AbstractIntegrationTest` runs a real Postgres 16 as non-owner `app_user`; 24 tests, all in `shared`. Three things to know: integration tests are **skipped silently without Docker** (CI has it, laptops may not); the proof test is a `*Test`, so Failsafe's `*IT` pattern matches nothing yet; `app_user` is created by the initializer, not `00-bootstrap.sql` — `W-06` switches it when the four schemas arrive |
| **Toolchain** | Java 21, Maven 3.9.11 (`C:/Tools/apache-maven-3.9.11`), Node 24. `D-38`–`D-42` |

---

## Related

`docs/target-state/README.md` · `CONTRIBUTING.md` · `README.md` ·
`.claude/outputs/` for the investigations the design rests on
