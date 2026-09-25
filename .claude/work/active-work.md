# Active Work

> Live project state. **Read this before starting any task** (root `CLAUDE.md` rule 2).
> Last refreshed: **2026-09-24**, checked row by row against `main`.
> **Layer 0 of Core is done — all three.** `W-22.1`, `W-10`, `W-13.1`. **Layer 1 is half
> done:** `W-11.1`, `W-11.2`, `W-13.2` and `W-14.1` are merged; six branches remain and
> can all run at once.
> Tracked, not gitignored — it is how everyone sees where the project stands.

## 2026-09-25 — `W-12.1` and `W-12.2` merged (`b7d03ec`)

- Subscription, module selection and entitlement enforcement are on `main`: `core.subscription`, `core.subscription_module` (`V034`), tenant locale columns (`V033`), `@RequiresModule` in `shared`.
- Dev seed now differs: Acme holds `PAYROLL`, Globex `HRMS` + `PAYROLL` (`infra/docker/seed/04-subscriptions.sql`).
- **Newly unblocked:** `W-12.3` (krushna, next), `W-24.1` (krushna, after `W-12.3`), and the `W-12.1` half of `W-20.2` and `W-24.2`.
- Merged as code and tests only; the branch's harness move to `.agents/` was left out on purpose.

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
RLS-isolated, plus the three nullable columns on `core.employee` that let an employee hold
them (`V014`). Payroll modelled these properly; HRMS kept department and job title as plain
strings with no lookup table, no foreign key and no validation.

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

> **Nobody has ever actually logged in.** CI is green and `LoginFlowIT` runs a real
> Keycloak, but spec §8 steps 2–4 — bring the stack up, mint a token with `curl`, call
> `/api/v1/me` — have never been run by anyone. That matters more here than usual, because
> the High the review found was exactly the class of defect that survives a green suite:
> Keycloak stamps `iss` from the host the request arrived on, so a browser at
> `localhost:8081` and a backend validating `keycloak:8081` disagreed on every token, and
> `LoginFlowIT` could not see it because it mints and validates through one mapped port.
> **The fix — pinning `KC_HOSTNAME_URL` and splitting `issuer-uri` from `jwk-set-uri` — is
> reasoned and CI-green, not exercised against a running stack.**

Three other defects worth carrying forward as patterns, not items:

| Found | Shape |
|---|---|
| `V007` sat below the already-applied `V008` | CI starts from an empty database, so out-of-order migration is invisible to it and breaks every existing dev volume. **Renumbered to `V009`** |
| Liveness probes passed security and were then refused `401` | Two permit lists that must agree — `ResourceServerConfig` and `TenantContextFilter` — and only one was updated |
| Genuine `404`s and `500`s came back as `401` | Error dispatch re-runs the filter chain; nothing permitted `/error` |

**Deferred, and recorded in the merge commit:** `MeController` lives in `shared`, so the
worker also serves `/api/v1/me` — authenticated, but outside its stated contract and as a
role with no `USAGE` on `core`; it belongs in `app`, which is a module move. Three READMEs
still describe the pre-`W-10` realm and none records the dev logins §8 depends on — that is
`sync-docs` work.

> **Nobody can self-register.** A user with no `core.user_tenant` row gets `401` and nothing
> creates one. That is `W-24.2` invitations, by decision, not an oversight.

---

## `W-22.1` Audit trail merged — 2026-09-23

The first Stream C ticket built, and the first built one ticket to one branch.

| | |
|---|---|
| Merged | `6fb4012`, closing **#26** |
| Shipped | `core.audit_log` (`V008`), a Hibernate post-commit listener, `GET /api/v1/audit` |
| Tests | 34, **none skipped** — `docker.api.version` from `W-09` is why |
| Review | 9 findings, no High. Five fixed on the branch, three deferred with reasons |

> **It captures nothing today. No production class carries `@Audited`.** The spec named
> `core.tenant` and `core.user_tenant` as the proof; neither has a JPA entity — both are
> raw JDBC in `TenantMembershipService.java:44-58` — so a Hibernate listener cannot observe
> them, and annotating them would have proved a dead path. §2 of the spec records this.
> **The first real opt-in belongs to `W-13`.** Until then the mechanism is proved only
> against a test entity.

> **`W-22.2` retention is not raised and must be.** Nothing deletes an audit row; growth is
> unbounded, which the spec's own §9 calls "certain". The seven-year window exists only as a
> decision inside `W-22-1-audit-trail.md` §13.

Two deferrals worth knowing before the next audited entity lands: an audit insert that fails
does so **after** the business transaction has committed, so the caller sees a failure for
data that is already durable (belongs with `W-22.2`); and a property mapping to several
columns — an `@Embedded` or a `@ManyToOne` — falls back to its Java name and
`String.valueOf`, which the redaction deny-list cannot match. **`W-13` is the first ticket
that will have an association, so `W-13` must fix that or it writes PII in clear.**

One guard-shape lesson worth repeating: `@Audited` was written without `@Inherited`, so it
would have captured nothing on any subclass, silently and forever. Found by the review, not
by the build, and it is the same shape as `W-07`'s per-file grep (#137).

---

## The Core build is one ticket to one branch — 2026-09-23

**`W-10`, `W-12` and `W-13` were being built together on a single branch. That branch cannot
merge and never could.** It is preserved, unmerged, as **`origin/salvage/W-10-old`**.

| Why it is stuck | Detail |
|---|---|
| Gate 5 | A `W-10` branch may change only `docs/target-state/features/W-10-*`. It also wrote the `W-12` spec |
| Flyway collisions | It holds `core/V003`, `V004`, `V005`; `main` holds `reference/V003`, `V004`, `V005`. One global sequence — the migrate job dies |
| Harness deleted | It removes all of `.claude/` — four hooks including `guard-edit` — and installs `.agents/`. That is why a `W-10` branch could write a `W-12` spec |
| 35 commits behind | Predates `W-09`, `W-52`, `W-53`, `W-56`, `W-60` and `W-22.1` |
| Tests never ran | No `docker.api.version`, so every integration test skipped under a green build (#117). `EntitlementEnforcementIT` — the only proof of the `403` — has never executed |

> **`origin/W-10-identity` was re-pushed at 16:01 on 2026-09-23 with `W-13` added on top**
> (`2c524b5`, Sayeed). It now carries three tickets and a third collision, `core/V005`
> against `reference/V005`. **Work on it is not reaching `main` and each addition makes the
> salvage larger.** This needs a decision, not a further commit.

**The order Core is built in** is the dependency layering in
`.claude/outputs/2026-09-22-plan-W-*.md`. Layer 0 is the three tickets with nothing in front
of them: `W-10` · `W-13.1` · `W-22.1`. `W-22.1` is done. Branches for the other two exist
locally with their specs promoted and no code: `W-10-identity` (`77b9af1`) and
`W-13-1-employee-record` (`6bc7584`).

> **Flyway numbers are pre-allocated across the programme so parallel branches cannot
> collide** — but the allocation has already had to move once, and the rule is stricter than
> "don't repeat a number". `V008` went to `W-22.1`. `W-10` was allocated `V007` and **had to
> be renumbered to `V009`**, because a script below the highest already applied fails
> `validate-on-migrate` on any database that has run the later one. CI never sees this: it
> starts from an empty database every time.
>
> **So the live rule is: take the next number above everything on `main`, not the number the
> plan reserved.** `V001`–`V002`, `V006`, `V008`–`V009` are used. **`W-13.1` takes `V010`**,
> and layer 1 continues from `V011`. A branch that picks its own number without checking
> `main` is how the salvage branch got three collisions.

**Two CI runs per ticket, and one is waste.** `ci.yml` triggers on `push: branches: ['**']`,
so pushing at the end of `/develop` and again after the review's fixes burns two runs, and
gate 7 only ever reads the second. The fix is to push once, after the review — a change to
the `develop` skill, on its own branch.

---

## `W-54` Deployment pipeline merged — 2026-09-22

`.github/workflows/deploy.yml` promotes the image CI already built rather than rebuilding
it, runs the Flyway migration job, switches revision, and rolls back by shifting traffic.

| | |
|---|---|
| Merged | `b1f9533`, closing **#74** and **#138** |
| Feature | **code done — it has never run.** No deployment, no migration job, no rollback has been executed against a real subscription (#124) |

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

**Rotation was removed entirely on 2026-09-23 (spec revision 6).** `rotate-secrets.sh` and
`SECRET_ROTATION.md` are deleted. The review found the script promised more than it did —
`--postgres-url` was ignored, two targets returned success having rotated nothing, and the
runbook gave a cadence to four secrets no code touched. A documented procedure that is not
real is worse than none.

> **There is now no rotation procedure at all.** Rotating a leaked secret means changing the
> database and Key Vault by hand and restarting the revisions, with nothing written down.
> That is the largest open gap in `W-56` and belongs to `W-64` or the first incident.

**§8 Rollback was corrected on 2026-09-23.** It told an operator to revert a Container App
secret reference to a previous version GUID. The references are unversioned by design — that
is what lets a revision restart pick up a changed secret — so there was no version to revert
to. The section now states what actually works: Container Apps keeps the last healthy revision
serving traffic, and a desynchronised password is reset by hand with `psql-admin-pw`.

Also outstanding: `id-web-dev` holds Key Vault read access the spec says it should not, and a
secret overwritten with a wrong value has no recovery path from the deployment.

---

## Build order corrected — 2026-09-23

Two lines in `09-build-order.md` described the frozen system wrongly and would have sent an
implementer down the wrong path.

| Said | Actually |
|---|---|
| "Payroll models locations properly; use that" | It models **work locations** properly. The holiday-to-location link is a `Set<String>`, so renaming an office orphans its holidays. Take the entity, fix the link |
| "a payroll event sends an email — something Payroll has never done" | It sends one: the payslip after a run, hard-coded in `PayRunServiceImpl`. The gap is the absence of a framework |

`legacy/docs/FEATURE_MAP.md` has a third error of the same kind — it says the pay run reads
loss-of-pay days from Payroll's own consumption table, while the code calls HRMS over HTTP.
**Deliberately not corrected:** `legacy/docs/` describes the frozen system and is left alone.

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
| Can start today | **Layer 1 — ten branches, all in parallel.** Layer 0 is done |

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

Design: `docs/target-state/` — 12 documents, **59 decisions recorded (`D-01`–`D-59`), zero
open questions.** `D-60` and `D-61` were taken at `W-59` and are not written down yet. Start at `docs/target-state/README.md`.

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
   (#124). **`W-54` deployment pipeline and `W-56` secrets are also merged** —
   `deploy.yml` promotes the image CI built and runs the migration job; neither has ever
   executed against Azure. `W-59` scanning closed the two things `W-49` left behind.

### Code is ported, never synced

The four frozen applications are snapshots in `legacy/`, taken 2026-09-13. **Production
fixes made after that date do not arrive automatically** (`D-17`). Payroll was actively
developed to the day of the freeze, so divergence starts now — keep a list of post-freeze
production fixes or they are lost at cutover.

---

## The queue — assigned by the founder, 2026-09-24

**Founder decision, 2026-09-24: the founder writes the spec and assigns it in the tracker.**
There is no self-claiming on GitHub and no spec-approval stop. Each developer works one
ticket at a time on their own `dev-<name>` branch.

| Issue | Ticket | Size | Skill | Why it is at the head |
|---|---|---|---|---|
| **none yet** | `W-22.2` audit retention | S | BE | **Must be raised.** Nothing deletes an audit row and `W-22.1` shipped without it. Spec and seven-year window are in `W-22-1-audit-trail.md` §13 |
| **#44** | `W-33.2` Tax calculator — old regime with section deductions | XL | BE | **Newly unblocked.** `W-09` shipped the 15 `reference` tables it reads. Carries three conditions from W-09's review (see `55a5a83`): C-1 Chapter VI-A has no `financial_year`, C-2 `home_loan_rule_master` has no regime column, C-3 loss carry-forward defaults FALSE |
| **#146** | `W-09` follow-up — senior-citizen tax slabs are not seeded | S | DATA | `W-09` seeded only `age_category = 'GENERAL'`. Under the old regime a senior gets ₹2.5L exemption instead of ₹3L, and a super-senior instead of ₹5L — both over-deducted. `W-33` cannot fix it without a new migration |
| #139 | `W-06` code reached `main` inside a documentation-only commit | S | INFRA | A gate defect, not a code defect. Belongs with #101 and #104 — the same merge gate, the same failure shape |
| #138 | Migrate job reports success while applying nothing | S | INFRA | `compose up migrate` without `--build` finds zero scripts and exits 0 |
| #124 | `W-50`/`W-51` follow-up — prove the unverified checks against a real dev environment | M | INFRA | Everything Azure is verified as code only. Nothing has been deployed |
| #101 | Merge-gate hardening — 3 defects from `W-03` | S | INFRA | Small, unblocks nothing but hardens `/merge` |
| #104 | Three gate paths never executed; harness not in CI | S | INFRA | Same |
| #86 | `W-66` Marketing website | L | FE | Independent of the chain |

Newly claimable after `W-14.1` and `W-13.2`: `W-13.3` search, `W-14.2` reporting line,
`W-17` holiday calendar, `W-12.1` subscription, `W-19` pay input ledger, `W-20.1`
notifications, `W-21` document store, `W-23.1` export.

> **`W-09` closed #10, #136, #137 and #117 together.** The three fixes rode with it because nothing in `W-09` could be proved without them: until #136 nothing ran a shipped migration through Flyway, until #137 a two-table script could ship an unprotected table, and until #117 every integration test skipped under a green build. The backend suite went from 46 passing with 34 skipping to **100 passing with none skipped**.

`W-49` (#69) inherited the `images` job from `W-03` and replaced its dev Dockerfile
targets with the production ones — **done, merged 2026-09-17**. The job now enables the
containerd image store (`D-47`), builds three production and two dev images, runs a
three-part secret scan and asserts size thresholds. **`W-59` (#79) is merged** (`ebb1d14`) and
landed as its own `security.yml`, closing both inherited conditions.

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
