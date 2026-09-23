# Active Work

> Live project state. **Read this before starting any task** (root `CLAUDE.md` rule 2).
> Last refreshed: **2026-09-22**, after `W-09` Reference schema & seed (#10) merged as
> `55a5a83`, carrying the #136, #137 and #117 fixes with it.
> Tracked, not gitignored — it is how everyone sees where the project stands.

## Where the project is

**Design finished. Build started. Nothing in production.**

| | |
|---|---|
| Repository | `infinevocloud-HCM-Suite/infinevo-platform`, private |
| Tickets | **112** — 20 closed, 92 open. GitHub is authoritative, this file is the summary |
| Waves | 9. **Wave 1 is done — all 7.** Wave 2 starts at `W-09` |
| Merged | `W-01` skeleton (#1) · `W-02` local stack (#3) · process skills and merge gate (#97) · `W-03` build pipeline (#4) · docs route through gate 5 (#100) · `W-04` test foundation (#5) · `W-05` Postgres & schemas (#6) · docs back in line with `W-05` (#114) · `W-49` containerisation (#69) · `W-50` Azure IaC (#70) · `W-51` networking & identity (#71) · `D-50` Storage Queue (#127) · `W-06` Flyway (#7) · **`W-07` tenant model (#8)** · **`W-08` tenant binding filter (#9)** · **`W-09` reference schema & seed (#10)** · `W-52` queue & worker (#72) · `W-60` observability (#80) · **`W-53` redis cache & invalidation (#73)** · docs for `W-51` (#132) and `W-07` (#142) as built |
| Team | `developers`, Write access. Gau318 `#6` · BirenGit `#69` · SayInfi `#5` |

> **`W-50` and `W-51` are verified as code and not as an environment.** The Bicep builds
> and lints clean and the scripts parse, but no live check has ever run: not Front Door
> routing, not the WAF, not the six private-path probes, not the 16 role assignments, not
> the migration job. **#124** is the follow-up covering both and should be treated as part
> of the tickets, not as polish. The shape of the risk is `W-50`'s `AcrPull`: declared,
> never exercised, green through four gate passes.

> **`D-50`: the job queue is Azure Storage Queue, not Service Bus.** A private endpoint on
> Service Bus is Premium-tier only, roughly ten times Standard, which `D-19` scale does not
> justify. `D-44` is superseded. Consequences that outlive this ticket: **`W-52` must
> handle idempotency in code**, since Storage Queue does not guarantee ordering, and the
> local stand-in moves from RabbitMQ to Azurite's queue service.

---

## `W-56` Secrets merged — 2026-09-23

Ten platform secrets now come from Key Vault, with no default value anywhere: the app fails
to start rather than running on a placeholder. `worker` gained its own database role.

| | |
|---|---|
| Merged | `64ec5fd`, closing **#76** |
| Roles | **five now** — `worker_user` and `keycloak_user` joined the three. None superuser or `BYPASSRLS` |
| Review | one blocker fixed on the branch: `--postgres-url` was parsed and ignored, so a rotation aimed at a test server hit the real Azure server |

**Verified as code, not as an environment.** Nothing here is proved against a deployment —
that is **#129**, and it covers whether a container actually resolves a Key Vault secret and
starts at all.

> **Two workflows are red on `main` and were red before this merge.** `Deploy` fails because
> `AZURE_CLIENT_ID`, `AZURE_TENANT_ID` and `AZURE_SUBSCRIPTION_ID` are not set as repository
> variables; `Azure Infrastructure CI` fails because no federated identity record matches
> `refs/heads/main`. Both are missing Azure configuration, not code, and both block #129.

Outstanding from the review, recorded rather than fixed: Keycloak and Brevo rotation are not
implemented and now refuse instead of returning success; `SECRET_ROTATION.md` promises a
90-day cadence for four secrets no script rotates; secret references are unversioned while
§8.1 tells an operator to revert to a version GUID that does not exist; `id-web-dev` holds
Key Vault read access the spec says it should not.

---

## Stream C is fully specced — 2026-09-23

**All 21 core-platform tickets are planned and founder-approved.** The nine that broke the
`plan-feature` size cap were split, so 16 tickets became 25; GitHub keeps the 9 parent tickets
and several specs now share one, with only the last saying `Closes #nn`.

| | |
|---|---|
| Specs approved | **33**, in `.claude/outputs/2026-09-22-plan-W-*.md` |
| Decisions settled | 69, recorded in `.claude/outputs/2026-09-22-plan-core-open-questions.md` |
| Evidence passes | 16, every claim carrying `file:line` |
| Can start today | `W-10` · `W-13.1` · `W-22.1` — everything else waits on these |

Nine decisions went against the recommendation in the spec and are worth reading before
building: the pay divisor stays **calendar days** and a missing policy **falls back silently**
(both to keep payslip amounts identical at cutover), a leave-policy change applies to the
**year in progress**, self-approval is **allowed**, and the per-employee portal switch is
**kept**. All nine are listed in §6 of the open-questions file.

> **Approval is not promotion.** The specs live in `.claude/outputs/`. Each moves to
> `docs/target-state/features/W-nn-<slug>.md` on its own ticket branch during `/develop` —
> that is the only path `guard-edit` allows.

---

## Current direction

**One platform replacing four applications, then Azure.** A customer buys HRMS,
Payroll, or both, and upgrades with a switch rather than a re-onboarding.

One repository · one backend with three enforced modules (`core` / `hrms` / `payroll`)
· one React frontend · **one Postgres database with four schemas** · one login
(Keycloak, single realm) · **Azure Container Apps**.

> **Not AKS. Not MySQL. No subtree, no sync.** If a document or skill says otherwise it
> is stale — the decisions are `D-09` Postgres, `D-10` Container Apps, `D-17` no sync.

Design: `docs/target-state/` — 12 documents, **59 decisions (`D-01`–`D-59`), zero open
questions.** Start at `docs/target-state/README.md`.

> **`W-06` Flyway is on `main` and the tenancy chain is complete.** This file said
> otherwise from 2026-09-17 to 2026-09-21; it was wrong, and it steered work with a
> phantom blocker. `a0cdb2f` was reverted by `ae761ed`, but the whole migration module
> came back inside `365a319` — a commit whose message says *"Documentation only. No code
> changed."* **How a documentation-only approval carried thirteen code files past the
> merge gate is #139, and it is a gate defect, not a `W-06` defect.** `W-06` → `W-07`
> (`c1cb5ee`) → `W-08` (`f99e712`) are all merged.

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
2. **Multi-tenancy — the mechanism is in. `W-06` → `W-07` → `W-08` are all merged.**
   Flyway runs four locations; `core.tenant` and `core.user_tenant` exist with
   row-level security; a request carries its tenant from the JWT through
   `TenantContextFilter` into the database session, so RLS has something to match on
   (`code/backend/shared/src/main/java/com/infinevo/shared/tenant/`). **What is in is the
   mechanism, not the coverage** — no business table is under it yet. Payroll is
   org-scoped on 63 of 97 entities; HRMS on **none at all** (0 of 39, `BUG-002`).
   Target stays `tenant_id` on every table outside `reference`, enforced by Postgres
   row-level security (`D-09`). Four follow-ups came out of the chain and are open:
   #136 #137 #138 #139.
3. **Azure — opened.** `W-49` containerisation **merged 2026-09-17 (#116)**: three
   production images — backend carrying both `app.jar` and `worker.jar` selected by
   `INFINEVO_ROLE` (`D-48`), unprivileged nginx on 8080 (`D-49`), and Keycloak with no
   realm baked in. All non-root, no secrets, 154 / 24 / 225 MB, built and gated by CI
   which pushes nothing. **`W-50` and `W-51` followed and are merged** — the Bicep,
   the network perimeter and the identities all exist as code. Nothing has been deployed
   (#124). Two things `W-49` left behind: the container scan's "report-only until `W-49`"
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
`gh issue list --label ready --search "no:assignee"` is the queue, `--label next` is its
head. (`--no-assignee` is not a flag in the installed `gh`; use the search form.)

| Issue | Ticket | Size | Skill | Why it is at the head |
|---|---|---|---|---|
| **#44** | `W-33.2` Tax calculator — old regime with section deductions | XL | BE | **Newly unblocked.** `W-09` shipped the 15 `reference` tables it reads. Carries three conditions from W-09's review (see `55a5a83`): C-1 Chapter VI-A has no `financial_year`, C-2 `home_loan_rule_master` has no regime column, C-3 loss carry-forward defaults FALSE |
| **#146** | `W-09` follow-up — senior-citizen tax slabs are not seeded | S | DATA | `W-09` seeded only `age_category = 'GENERAL'`. Under the old regime a senior gets ₹2.5L exemption instead of ₹3L, and a super-senior instead of ₹5L — both over-deducted. `W-33` cannot fix it without a new migration |
| #139 | `W-06` code reached `main` inside a documentation-only commit | S | INFRA | A gate defect, not a code defect. Belongs with #101 and #104 — the same merge gate, the same failure shape |
| #138 | Migrate job reports success while applying nothing | S | INFRA | `compose up migrate` without `--build` finds zero scripts and exits 0 |
| #79 | `W-59` Scanning | M | INFRA | **Two inherited decisions have come due**: the container scan's "report-only until `W-49`" condition has expired, and the Keycloak image's primary group 0. Also modifies the same `images` job `W-49` rewrote — read `ci.yml` before editing |
| #124 | `W-50`/`W-51` follow-up — prove the unverified checks against a real dev environment | M | INFRA | Everything Azure is verified as code only. Nothing has been deployed |
| #101 | Merge-gate hardening — 3 defects from `W-03` | S | INFRA | Small, unblocks nothing but hardens `/merge` |
| #104 | Three gate paths never executed; harness not in CI | S | INFRA | Same |
| #86 | `W-66` Marketing website | L | FE | Independent of the chain |

The founder steers by keeping the `next` label on three to five tickets, in order.

> **`W-09` closed #10, #136, #137 and #117 together.** The three fixes rode with it because nothing in `W-09` could be proved without them: until #136 nothing ran a shipped migration through Flyway, until #137 a two-table script could ship an unprotected table, and until #117 every integration test skipped under a green build. The backend suite went from 46 passing with 34 skipping to **100 passing with none skipped**.

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
| #139 | yes | A commit whose message reads "Documentation only. No code changed." carried the entire thirteen-file migration module onto `main`. Found by `/review` of `W-07`, two days later, by accident |

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
| **Pipeline is advisory** | `W-03` merged 2026-09-14. Four jobs on every branch push — **not on `pull_request`**, because the develop loop has no pull requests (`de14751`). `D-43` means CI cannot be a *required* check; `check-done.mjs` reads the run conclusion for the exact `HEAD` commit and is the gate that enforces it |
| **The two-tenant seed is now `W-09`'s** | `W-02` shipped the loader and `W-07` created `core.tenant`, but nothing seeds two tenants. Seed one tenant holding everything and entitlement bugs stay invisible until a customer buys one module |
| **Nobody has run the stack but me** | `W-02` done-when item 11 is unticked. Have a developer run `up -d` and `smoke.sh` |
| **Test foundation is in, coverage is not** | `W-04` merged 2026-09-15. `AbstractIntegrationTest` runs a real Postgres 16 as non-owner `app_user`; 24 tests, all in `shared`. Three things to know: integration tests were **skipped silently** on any machine running Docker Engine 25 or newer until `W-09` fixed it — docker-java asked for API 1.32, the engine answered "minimum 1.40" with a 400, and Testcontainers read that as "no Docker here", so 34 tests skipped under a green build (#117, `code/backend/pom.xml` `docker.api.version`). They still skip silently when Docker is genuinely absent; `W-05` gave Failsafe its first real match, `DatabasePrivilegesIT` (10 tests, green in CI); `app_user` was created by the initializer, not the bootstrap script — **`W-05` switched it**, so the initializer now runs the canonical `infra/postgres/` scripts |
| **Toolchain** | Java 21, Maven 3.9.11 (`C:/Tools/apache-maven-3.9.11`), Node 24. `D-38`–`D-42` |

---

## Related

`docs/target-state/README.md` · `CONTRIBUTING.md` · `README.md` ·
`.claude/outputs/` for the investigations the design rests on
