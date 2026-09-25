# Active Work

> Live project state. **Read this before starting any task** (root `GEMINI.md` / `AGENTS.md` rule 2).
> Last refreshed: **2026-09-24**, checked row by row against `main`.
> **Layer 0 of Core is done — all three.** `W-22.1`, `W-10`, `W-13.1`. **Layer 1 is half
> done:** `W-11.1`, `W-11.2`, `W-13.2` and `W-14.1` are merged; six branches remain and
> can all run at once.
> Tracked, not gitignored — it is how everyone sees where the project stands.

## 2026-09-25 — `W-04.1` and `W-11.3` merged (`3350cf2`)

- The backend suite is green on `main` again (`D-1` fixed).
- The permission catalogue is corrected: leave, holiday and attendance codes are `core.*`, 24 codes added, no tenant admin holds `core.tenant.provision` (`D-4`, `D-5` fixed).
- **Newly unblocked:** every corrected Core spec that was waiting on `W-11.3` for its codes — `W-12.x`, `W-14.2`, `W-15.x`, `W-16.x`, `W-17`, `W-18.x`, `W-19`, `W-20.x`, `W-21`, `W-22.2`, `W-23.x`, `W-25` — and `W-52.1`.
- Defects still open: `D-2`, `D-3` (`W-52.1`), `D-6` (`W-13.4`), `D-7` (`W-53.1`), `D-8` (`W-09.1`), `D-9` (manual). All but `D-9` are in flight on `dev-claude`.
- The defect list lives in `docs/trackers/DEV-TRACKER.md` § Defects on `main`.

## Where the project is

**Design finished. Build started. Nothing in production.**

| | |
|---|---|
| Repository | `infinevocloud-HCM-Suite/infinevo-platform`, private |
| Tickets | **112** — 28 closed, 84 open. GitHub is authoritative, this file is the summary |
| Waves | 9. **Wave 1 is done — all 7.** Wave 2 starts at `W-09` |
| Merged | `W-01` skeleton (#1) · `W-02` local stack (#3) · process skills and merge gate (#97) · `W-03` build pipeline (#4) · docs route through gate 5 (#100) · `W-04` test foundation (#5) · `W-05` Postgres & schemas (#6) · docs back in line with `W-05` (#114) · `W-49` containerisation (#69) · `W-50` Azure IaC (#70) · `W-51` networking & identity (#71) · `D-50` Storage Queue (#127) · `W-06` Flyway (#7) · **`W-07` tenant model (#8)** · **`W-08` tenant binding filter (#9)** · **`W-09` reference schema & seed (#10)** · `W-52` queue & worker (#72) · `W-60` observability (#80) · **`W-53` redis cache & invalidation (#73)** · docs for `W-51` (#132) and `W-07` (#142) as built · **`W-22.1` audit trail (#26)** · **`W-10` identity (#11)** · **`W-13.1` employee record (#14)** · **`W-11.1` role catalogue (#12)** · **`W-11.2` permission check (#12)** · **`W-59` scanning (#79)** · **`W-61` alerting (#81)** · **`W-55` index & query standard (#75)** · **`W-62` backup & DR (#82)** · `W-54` deployment pipeline (#74) · `W-56` secrets (#76) · **`W-14.1` org masters (#15)** · **`W-13.2` employee detail (#14)** · **`W-63` load test (#83)** |
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

## `W-13.1` Employee record merged — layer 0 is done

`core.employee` (**`V010`**) is the first employee table isolated by row-level security
rather than by a `WHERE` clause someone remembered. **BUG-002 is fixed for this table**; the
other 38 HRMS entities are `W-67`'s.

| | |
|---|---|
| Merged | `7d0bab6`, closing **#14** |
| Tests | 34, none skipped. The RLS test asserts on a raw `app_user` connection, not through the service |
| Review | 4 findings, no High, all fixed on the branch |

Two legacy defects corrected rather than carried: `date_of_joining` is a real `DATE` where
`BasicDetails.java:44` holds a `String`, and `employee_number` is unique **within** a tenant
where `employeeUniqueId` (`:140`) is globally unique — which in a shared database lets one
customer's numbering block another's.

> **`W-13.3` search will trip on soft delete unless it is careful.** The repository extends
> `JpaRepository`, so `findById`, `findAll`, `getReferenceById` and `deleteById` are
> inherited and none filters `is_deleted`. Nothing uses them today. Search and listing is
> exactly the ticket that reaches for `findAll`, and it would return soft-deleted employees
> silently. The repository javadoc says so now, and names `W-13.3`.

> **No database `CHECK` on `status`.** The three-value vocabulary lives only in Java, so a
> row written outside the service is accepted at write and fails at **read**, as a Hibernate
> enum conversion error. `W-67` is the caller that will hit it, and it is the ticket that
> knows what the legacy values actually are.

---

## Layer 1 — four of ten merged, 2026-09-24

The three that unblocked the most are in. Six remain, all independent of each other.

| Branch | Blocks |
|---|---|
| ~~`W-14-1-org-masters`~~ **merged `235aab2`** | `W-14.2` and `W-17` — now unblocked |
| ~~`W-13-2-employee-detail`~~ **merged `5228385`** | `W-25` portal; **the audit opt-in is in, the trail now captures** |
| ~~`W-11-1-role-catalogue`~~ **merged `172eaaa`** | `W-11.2`, `W-12.2`, `W-24.2` — now unblocked |
| ~~`W-11-2-permission-check`~~ **merged `23d1126`** | — |
| `W-12-1-subscription` · `W-13-3-employee-search` · `W-19-pay-input-ledger` · `W-20-1-notifications` · `W-21-document-store` · `W-23-1-export` · `W-22-2-audit-retention`* | **the six still open**, plus `W-14.2` and `W-17` newly unblocked |

\* `W-22.2` is **layer 3 in practice**, not layer 1: its spec says "blocked by W-22.1", but
it also sweeps `core.notification` (`W-20.1`) and uses the scheduler (`W-20.2`). **It still
has no GitHub ticket and must be raised.**

> **Founder decision, 2026-09-23, now shipped: `W-13.2` turned the audit trail on.** `W-22.1` shipped the
> mechanism and it has recorded **nothing** since, because no production table carries
> `@Audited`. `W-13.2` annotates the employee tables and fixes
> the `@Embedded` redaction gap `W-22.1` deferred — without which the first audited entity
> holding an address or bank detail writes it in clear. **This is scope added to an approved
> spec, by the founder, and is recorded in the spec itself.**

> **`W-11.1` and `W-11.2` merged 2026-09-24.** `W-11.1` took `V020`–`V023`.
> Every `core`/`shared` endpoint now needs `@RequiresAction` — `EndpointGuardCoverageTest` fails
> otherwise. Run `infra/docker/seed/seed.sh` after `migrate`, or local logins get `403`.
> Outstanding: `app`'s `JobStatusController` is unguarded; employees cannot read their own
> record until an ownership ticket uses the `_own` codes.

**Flyway continues from `V024`.** Used on `main`: `V001`, `V002`, `V006`, `V008`, `V009`, `V010`, `V011`, `V012`–`V019` (`W-14.1`, `W-13.2`), `V020`–`V023` (`W-11.1`). The
rule is the next number **above everything on `main`**, not the next free one — `W-10` had to
be renumbered for exactly that, and CI cannot catch it because CI starts from an empty
database.

---

## `W-13.2` Employee detail merged — the audit trail now captures, 2026-09-24

Five optional one-to-one sections of `core.employee` (`V015`–`V019`), each written through
`PUT /api/v1/employees/{id}/{personal,contact,identification,employment,bank}`.

| | |
|---|---|
| Merged | `5228385`, closing **#14** |
| Tests | full suite at merge: 8 modules, 0 failures, 0 skips. CI green on the branch head `cdcb3aa` |
| Review | 5 findings, 2 Medium, all fixed on the branch |

**`@Audited` is on the five sections and on the root `Employee`** — the founder's added
scope. The redaction fix landed first and in that order, because these tables hold a bank
account number, an IFSC and a PAN. An association had been audited as
`String.valueOf(entity)`, so a bank change recorded `…Employee@1b6d3586` and named nobody.

Outstanding, and each names its owner:

| What | Owner |
|---|---|
| No `CHECK` on `payment_mode` or `bank_account_type` — a row written outside the service fails at **read**, as a Hibernate enum conversion error. Same shape as `core.employee.status` | `W-67` |
| PAN, Aadhaar and bank account number are **not** unique within a tenant. The frozen columns are globally unique, which in a shared database lets one customer's row block another's | `W-67` |
| HRMS's address `country` column is not carried | — |
| `check-done.mjs` gates 2 and 4 accept **any** `W-13*` spec for a `W-13.2` branch. A branch with no spec of its own would pass, and could edit a sibling's spec | #101, #104 |

---

## `W-14.1` Org masters merged — 2026-09-23

Department, designation and work location (`V011`–`V013`), each tenant-scoped and
RLS-isolated, plus the three nullable columns on `core.employee` (`V014`). Payroll modelled
these properly; HRMS kept department and job title as plain strings with no lookup table,
no foreign key and no validation.

| | |
|---|---|
| Merged | `235aab2`, closing **#15** |
| Unblocks | `W-14.2` reporting line, `W-17` holiday calendar |

> **The cross-tenant assignment check is load-bearing, not belt and braces.** The columns
> carry foreign keys, but PostgreSQL runs referential integrity as the table **owner** with
> row security off, and the owner is `migration_user` — so the database accepts an employee
> in tenant A pointing at a department in tenant B. `EmployeeAssignmentIT` demonstrates
> exactly that, then proves the service refuses it.

Free-text conversion is **deliberately not attempted** — it is `W-67`'s, with production
data in front of it, and three migration comments push back on it by name.

Two things carried forward: `EmployeeResponse.from` initialises up to three lazy proxies,
harmless today but an **N+1 across a page** the moment `W-13.3` adds search and listing; and
section 8's verification block was run by `/develop` but **not independently re-run**,
because the platform Postgres was not up during the review.

---

## `W-55` Index & query standard merged — 2026-09-24

Tenant-leading index convention, batch fetching, and HikariCP connection pool calibration are active.

| | |
|---|---|
| Merged | `2ccd723`, closing **#75** |
| Shipped | Tenant-leading composite index conventions documented in `code/backend/migration/README.md`; Flyway `V024__index_standard_optimizations.sql` (`core.employee`, `core.job_status`); automated convention enforcement in `DatabaseIndexConventionIT` (`core`, `hrms`, `payroll`); global batch fetching (`default_batch_fetch_size: 25`) in `app` and `worker`; HikariCP pool calibration (App: 10 max/5 min, Worker: 5 max/2 min, leak detection 30s) compatible with statement-level tenant binding proxy (`D-57`); `QueryCountIT` verifying N+1 elimination |
| Tests | 6 index convention tests, 4 Hikari pool calibration tests, 1 QueryCount integration test |

---

## `W-59` Scanning merged — 2026-09-23

Security scanning is now active on every pull request and push to `main`.

| | |
|---|---|
| Merged | `066ba6f` (via `ebb1d14`), closing **#79** |
| Shipped | Trivy dependency scanning (`code/backend`, `code/frontend`), Semgrep SAST (`p/java`, `p/javascript`, `p/owasp-top-ten`), Gitleaks secret detection, Trivy container image scanning, `.github/dependabot.yml` daily scanning |
| Upgrades | Spring Boot parent 3.3.13 → 3.5.16 (`D-60`, clearing 29 CVEs); Vite 5.4.10 → 8.0.16 & `@vitejs/plugin-react` 4.3.3 → 6.1.1 |
| Decisions | `D-60` (Spring Boot 3.5.x supersedes `D-39`), `D-61` (Dependabot alerts & config) — **neither is in `07-decisions.md`; the file still ends at `D-59`.** `sync-docs` work |

---

## `W-10` Identity merged — 2026-09-23

One Keycloak login now reaches the platform. **BUG-001 is closed** — HRMS minted its own
JWT with a hardcoded secret while Payroll validated Keycloak tokens, and neither recognised
the other.

| | |
|---|---|
| Merged | `ac1e531`, closing **#11** |
| Shipped | `core.user_account` (**`V009`**), one `SecurityFilterChain` in `shared`, `GET /api/v1/me`, per-request profile sync, the rewritten dev realm and its seed, Keycloak login in the frontend shell |
| Tests | 21 identity and security tests, none skipped. `LoginFlowIT` starts a real Keycloak 25 and mints a token |
| Review | one **High** and four Medium fixed on the branch; two deferred |

---

## `W-22.1` Audit trail merged — 2026-09-23

The first Stream C ticket built, and the first built one ticket to one branch.

| | |
|---|---|
| Merged | `6fb4012`, closing **#26** |
| Shipped | `core.audit_log` (`V008`), a Hibernate post-commit listener, `GET /api/v1/audit` |
| Tests | 34, **none skipped** — `docker.api.version` from `W-09` is why |
| Review | 9 findings, no High. Five fixed on the branch, three deferred with reasons |

---

## The Core build is one ticket to one branch — 2026-09-23

**`W-10`, `W-12` and `W-13` were being built together on a single branch. That branch cannot
merge and never could.** It is preserved, unmerged, as **`origin/salvage/W-10-old`**.

**The order Core is built in** is the dependency layering in
`.agents/outputs/2026-09-22-plan-W-*.md`. Layer 0 is the three tickets with nothing in front
of them: `W-10` · `W-13.1` · `W-22.1`. `W-22.1` is done. Branches for the other two exist
locally with their specs promoted and no code: `W-10-identity` (`77b9af1`) and
`W-13-1-employee-record` (`6bc7584`).

---

## Stream C is fully specced — 2026-09-23

**All 21 core-platform tickets are planned and founder-approved.** The nine that broke the
`plan-feature` size cap were split, so 16 tickets became 25; GitHub keeps the 9 parent tickets
and several specs now share one, with only the last saying `Closes #nn`.

| | |
|---|---|
| Specs approved | **33**, in `.agents/outputs/2026-09-22-plan-W-*.md` |
| Decisions settled | 69, recorded in `.agents/outputs/2026-09-22-plan-core-open-questions.md` |
| Evidence passes | 16, every claim carrying `file:line` |
| Can start today | **Layer 1 — ten branches, all in parallel.** Layer 0 is done |

---

## Current direction

**One platform replacing four applications, then Azure.** A customer buys HRMS,
Payroll, or both, and upgrades with a switch rather than a re-onboarding.

One repository · one backend with three enforced modules (`core` / `hrms` / `payroll`)
· one React frontend · **one Postgres database with four schemas** · one login
(Keycloak, single realm) · **Azure Container Apps**.

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

---

## Related

`docs/target-state/README.md` · `CONTRIBUTING.md` · `README.md` ·
`.agents/outputs/` for the investigations the design rests on
