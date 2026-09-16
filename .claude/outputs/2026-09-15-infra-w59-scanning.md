# W-59 — Dependency, Code & Container Scanning

| Field | Value |
|---|---|
| **Work item** | `W-59` · issue [#79](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/79) |
| **Kind** | **Infra** — security scanning in CI |
| **Stream / track** | Stream H — Security, operations & go-to-market |
| **Wave** | 2 — Data platform |
| **Size / skill** | **M** (was S) · INFRA — founder-confirmed 2026-09-16. Relabel `#79` `size-S` → `size-M` |
| **Owner** | BirenGit |
| **Branch** | `W-59-scanning` |
| **Blocked by** | `W-03` (#4) — merged |
| **Blocks** | — |
| **Capabilities** | `PLAT-09` Security hardening (`01-platform-shape.md:161`) |
| **Decisions** | `D-38` Java 21 · `D-42` Node 24 · `D-43` no branch protection · **`D-45` approved 2026-09-16** — Spring Boot 3.5.x, supersedes `D-39` · **`D-46` approved 2026-09-16** — Dependabot alerts + config |
| **Gaps addressed** | **None.** `DEBT-004` is preventative only — see §6 |
| **Status** | **Approved** |
| **Approved by** | sanjib (founder) |
| **Approved on** | 2026-09-16 |

> **Approved by the founder on 2026-09-16.** Hard rule 1 is satisfied: `/develop W-59`
> may start.
>
> `D-45` and `D-46` were approved the same day and **do not yet exist in
> `07-decisions.md`**, which still ends at `D-44` (`:56`). Writing them into the register
> is a `sync-docs` change, listed as task 6 in §14. **Until that lands, this spec is the
> only record of them** — so task 6 is not optional housekeeping, it is what stops a
> reversed decision from being invisible to everyone who reads the register.

Revision 4. Supersedes the draft at `origin/w-59` `542ee55`, reviewed in
`.claude/outputs/2026-09-15-review-spec-W-59-rev2.md`. Changes from that draft are in
§15; the founder's five decisions of 2026-09-16 are recorded in §16.

---

## 1. Problem

`W-03` built the pipeline — `.github/workflows/ci.yml`, four jobs on every pull request.
None of them looks at security. Dependencies, source and container images enter `main`
uninspected, so three things can land unnoticed:

1. A dependency with a published, fixed CVE.
2. An insecure code pattern that compiles and lints cleanly.
3. A credential committed in plaintext — which is how `DEBT-004` happened in the frozen
   system.

`09-build-order.md:285` sets the trap to avoid:

> *"Watch: fix the initial backlog once, or the noise gets ignored permanently."*

So the backlog has to be measured before the gate is written, not after.

### Baseline — measured 2026-09-15 on `main` at `d3eb07e`

Raw scanner output: **`.claude/outputs/2026-09-15-infra-w59-scan-raw.md`**.
Independent earlier run: `.claude/outputs/2026-09-15-infra-w59-scanning-evidence.md`.
Both agree. Nothing in this section is summarised by hand — the numbers below are
`grep`-able from the raw file.

Trivy `0.60`-series via `aquasec/trivy:latest`, Docker 29.5.3. Backend scanned with
`--offline-scan` against a warm `~/.m2`, because Maven Central rate-limits unauthenticated
POM resolution (HTTP 429, `Retry-After: 1800`).

**Backend** — `trivy fs --offline-scan --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed code/backend`

| POM | Findings |
|---|---|
| `pom.xml` (root) | **72** — HIGH 60, CRITICAL 12 |
| `app/pom.xml` | 30 — HIGH 24, CRITICAL 6 |
| `worker/pom.xml` | 30 — HIGH 24, CRITICAL 6 |
| `core` · `hrms` · `payroll` · `shared` | 3 each — HIGH 3, CRITICAL 0 |
| `migration/pom.xml` | 0 |

Findings are counted per POM, so the same CVE appears in several. Deduplicated, the
backlog is **29 unique CVE IDs across 11 packages**:

| Package | Installed | Fixed in |
|---|---|---|
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.42 | 10.1.54 / **10.1.55** |
| `org.springframework.boot:spring-boot` | 3.3.13 | **3.5.14**, 4.0.6 |
| `org.springframework.boot:spring-boot-starter-actuator` | 3.3.13 | **3.5.12**, 4.0.4 |
| `org.springframework.data:spring-data-commons` | 3.3.13 | **3.5.12**, 4.0.6 |
| `org.springframework:spring-core` | 6.1.21 | **6.2.11** |
| `org.springframework:spring-expression` | 6.1.21 | **6.2.19**, 7.0.8 |
| `org.springframework:spring-webmvc` | 6.1.21 | 6.2.x |
| `com.fasterxml.jackson.core:jackson-core` · `jackson-databind` | 2.17.3 | **2.18.8**, 2.21.4 |
| `org.postgresql:postgresql` | 42.7.7 | 42.7.11 / **42.7.12** |
| `io.micrometer:micrometer-core` | 1.13.15 | **1.15.12**, 1.16.6 |

> **The row that decides `D-45`:** `spring-boot` *itself* — not a transitive library — is
> vulnerable at 3.3.13 (`CVE-2026-40973`, arbitrary code execution and session hijacking)
> and is **fixed only in 3.5.14 or 4.0.6**. There is no 3.3.x patch. Spring Boot 3.3.x no
> longer receives OSS security patches. Every other row above is a managed dependency
> whose version is set by the parent, so they move together with it.
>
> This is why the backlog cannot be cleared on the 3.3 line, and therefore why a scanning
> ticket cannot "start green" without reopening `D-39`.

**Frontend** — `trivy fs --include-dev-deps --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed code/frontend`

```
package-lock.json (npm)
=======================
Total: 1 (HIGH: 1, CRITICAL: 0)

│ vite │ CVE-2026-53571 │ HIGH │ fixed │ 5.4.21 │ 8.0.16, 7.3.5, 6.4.3 │
│      │ vite: `server.fs.deny` bypass on Windows alternate paths          │
```

One finding. **Fixed in 6.4.3** — a single major from the installed 5.4.21. `vite` is a
`devDependency` (`package.json:23`), so it is only reachable with `--include-dev-deps`;
the gate uses that flag deliberately, because the dev server is what developers run.

`react-router-dom` 6.28.0 carries a **Moderate** open redirect. Moderate is below the
`HIGH,CRITICAL` gate, so it does **not** block and is **not** in scope. Question 4.

**SAST** — Semgrep Community v1.177.0, `semgrep scan --config auto code/`:
**0 findings**, 372 rules, 43 files. Corroborated independently — `git ls-files code/`
returns 16 `.java` files, matching Semgrep's Java count exactly. The backend is a
skeleton with no domain logic ported, so zero is expected, and it means done-when 6 is
falsifiable from day one rather than aspirational.

**Secrets** — `trivy fs --scanners secret code/`: **0**.

**Containers** — `infra/docker/dev.Dockerfile.*` are development images: full JDK
toolchain, root user. Unmeasured, and expected to be noisy. §2 handles this.

### Sizing

The original **S** assumed configuring scanners against a clean tree. The measured
backlog makes a Spring Boot major-minor upgrade part of the ticket, which is not an S.
Proposed **M**. Doing it now is cheap in a way it will never be again: `code/backend`
holds 16 Java files and zero ported domain logic, so the blast radius is `pom.xml` plus
whatever the compiler complains about. After Wave 3 it is a migration project.

---

## 2. Scope

**In scope**

**(a) Blocking security jobs, inside `.github/workflows/ci.yml`**

They go in `ci.yml` and nowhere else, because `check-done.mjs` gate 10 evaluates **only**
the workflow named `CI` (`check-done.mjs:520`, and `:559-561` — *"other workflows do not
count"*). A job in any other workflow file cannot block a merge. `ci.yml:11` is `name: CI`.

| Job | Does | Fails the build on |
|---|---|---|
| `security-deps` | `trivy fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --include-dev-deps code/` | any fixable HIGH or CRITICAL |
| `security-code` | `semgrep --config auto code/` | any blocking finding |
| `security-secrets` | `trivy fs --scanners secret code/ infra/` | any detected credential |

**(b) Container scan — inside the existing `images` job, report-only**

`ci.yml:200-213` builds `infinevo-backend:dev` and `infinevo-frontend:dev` on an
ephemeral runner. There is no registry login, no `docker push` and no
`actions/upload-artifact` for the images anywhere in the file — `ci.yml:192` says so in a
comment. GitHub Actions jobs are isolated, so **a separate job or workflow has no image
to scan.** The scan therefore runs as a step inside `images`, immediately after
`docker build`, with `continue-on-error: true` until `W-49` delivers hardened production
images.

**(c) Daily rescan — `.github/workflows/security.yml`, informational only**

`schedule: '0 2 * * *'` plus `workflow_dispatch`. Catches CVEs published against
dependencies that have not changed. **It cannot block anything** — gate 10 ignores it by
name — and the spec does not pretend otherwise. No container scan here: no registry
until `W-50`.

**(d) Clear the measured backlog**

- Backend: Spring Boot parent 3.3.13 → **3.5.x** (`D-45`, approved). Clears all 29.
- Frontend: `vite` → **`^8`** (founder decision, 2026-09-16). Clears the 1.
  `CVE-2026-53571` is fixed in 6.4.3, 7.3.5 or 8.0.16; the founder chose to go current
  rather than upgrade twice. **This forces a second bump:** `@vitejs/plugin-react` is
  `^4.3.3` (`package.json:28`) and does not support vite 8, so it moves to its matching
  major in the same commit. `vite.config.js` uses only `defineConfig`, `plugins`,
  `resolve.alias` and `server.port` — all stable across 5→8, so the config itself is
  expected to carry over unchanged.

**(e) Dependabot** — `.github/dependabot.yml` for Maven and npm, plus enabling alerts
(currently off: `GET /vulnerability-alerts` → 404). `D-46`, approved. Free on private
repositories, costs no Actions minutes. Dependabot will open upgrade pull requests; those
run the full `CI` gate like any other.

**(f) A test that the gates still bite** — see §7. This is what makes the deliberate
breaks survive the pull request.

**Out of scope**

| | Owner |
|---|---|
| Blocking on container CVEs; hardened production images | `W-49` (#69, `08-work-plan.md`) |
| Container scanning in the scheduled workflow (needs a registry) | `W-50` |
| DAST / penetration testing | `W-64` (`08-work-plan.md:158`) |
| Azure Key Vault runtime integration | `W-56` (`08-work-plan.md:150`) |
| Tenant isolation policy tests | `W-58` (`08-work-plan.md:152`) |
| **Rotating the exposed legacy credentials** | Operational incident, not this programme — `active-work.md` Incidents #1 |
| `react-router-dom` 7 — Moderate, below the gate | Deferred; Question 4 |
| Upgrading `eslint` / other non-security bumps | Not this ticket |

---

## 3. Flow

```
pull_request → main   ──►  .github/workflows/ci.yml   (name: "CI")
  │                          ↑ the ONLY workflow check-done.mjs gate 10 evaluates
  ├── backend           compile · test · spotless
  ├── frontend          lint · build
  ├── static            legacy/ untouched · ddl-auto · money types
  ├── security-deps     Trivy vuln   HIGH,CRITICAL --ignore-unfixed   [BLOCKS]
  ├── security-code     Semgrep SAST                                  [BLOCKS]
  ├── security-secrets  Trivy secret                                  [BLOCKS]
  └── images            needs: [backend, frontend]
        ├── docker build  backend:dev · frontend:dev
        └── trivy image   same runner, same job        [REPORT-ONLY until W-49]

schedule 0 2 * * *    ──►  .github/workflows/security.yml
  └── rescan deps + secrets          [INFORMATIONAL — cannot block any merge]
```

## 4. Backend changes

Not applicable — no controller, service, entity or endpoint changes. The only backend
file touched is `code/backend/pom.xml` (parent version).

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/package.json` | `vite` `^5.4.10` → `^8` and `@vitejs/plugin-react` `^4.3.3` → its vite-8-compatible major (devDependencies) |
| `code/frontend/package-lock.json` | regenerated |
| `code/frontend/vite.config.js` | only if vite 8 rejects the current options — no change expected |

No route or component change. `code/frontend/src/main.jsx:5` keeps
`import { BrowserRouter } from 'react-router-dom';` — untouched, because `react-router-dom`
stays on 6: its open redirect is **Moderate**, below the gate (§2, out of scope).

## 6. Database changes

**Not applicable.** No schema change, no Flyway script, no new table, no entity. The
standing rules on `tenant_id`, row-level security, `Money`/`BigDecimal`, indexes
(`DEBT-018`) and expand/contract sequencing have nothing to bind to in this ticket.
`ddl-auto` appears in no file this ticket writes.

## 7. Tests

The `W-03` lesson recorded in `#104` is that a gate proved once, on a branch that is then
deleted, is not proved: *"the `legacy/` gate has never been proved, `images` has never
been observed red."* So the deliberate breaks land as a script that re-runs, not as a
screenshot in a pull request.

| Type | File | Covers |
|---|---|---|
| Integration | `.github/scripts/security-gate-selftest.sh` | Builds four throwaway fixtures in a temp dir and asserts each scanner **exits non-zero**: a `pom.xml` pinning `log4j-core:2.14.1`; a `package.json` pinning a known-vulnerable package; a file holding a synthetic AWS key that matches Trivy's `aws-access-key-id` rule; a `.java` file with `Runtime.getRuntime().exec(request.getParameter("cmd"))`. Asserts a clean fixture **exits zero**, so the test fails if a scanner is misconfigured to pass everything |
| Integration | same script, `--self-check` | Asserts the four fixtures never touch the working tree and are removed on exit |
| Unit | — | No application code changes; nothing to unit-test |

The script runs in `security-code`'s job on every pull request, so a scanner that silently
stops working fails CI that day rather than at the next audit.

| # | Deliberate break | Expected |
|---|---|---|
| 1 | `log4j-core:2.14.1` in a fixture POM | `security-deps` exit 1, names `CVE-2021-44228` |
| 2 | vulnerable npm package in a fixture lockfile | `security-deps` exit 1 |
| 3 | synthetic AWS key in a fixture file | `security-secrets` exit 1, names file and line |
| 4 | `Runtime.getRuntime().exec(<untrusted>)` in a fixture | `security-code` exit 1 |
| 5 | clean fixture | all three exit 0 — proves the gates are not failing everything |
| 6 | dev image scan | `images` reports CVEs, **exit 0**, build stays green |

## 8. Verification

Every command runs from the repository root on any machine. No absolute paths.

```bash
# 1 — backend compiles and tests on Spring Boot 3.5.x
cd code/backend && ./mvnw -q clean verify && cd ../..

# 2 — frontend lints and builds on vite 6
cd code/frontend && npm ci && npm run lint && npm run build && cd ../..

# 3 — dependency gate, the exact command CI runs
docker run --rm -v "$PWD:/src:ro" -v "$HOME/.m2:/root/.m2:ro" aquasec/trivy:latest \
  fs --offline-scan --include-dev-deps --scanners vuln \
     --severity HIGH,CRITICAL --ignore-unfixed /src/code

# 4 — secret gate
docker run --rm -v "$PWD:/src:ro" aquasec/trivy:latest \
  fs --scanners secret /src/code /src/infra

# 5 — SAST gate
docker run --rm -v "$PWD:/src:ro" returntocorp/semgrep:latest \
  semgrep scan --config auto --error /src/code

# 6 — the gates still bite
bash .github/scripts/security-gate-selftest.sh

# 7 — gate 10 still passes
node .claude/scripts/check-done.mjs <pr>
```

| # | Check | Expected output | Exit |
|---|---|---|---|
| 1 | `mvnw clean verify` | `BUILD SUCCESS`; 24 existing tests pass, 0 failures | 0 |
| 2 | `npm run lint && npm run build` | no ESLint warnings (`--max-warnings 0`), `vite build` writes `dist/` | 0 |
| 3 | Trivy vuln over `code/` | every POM row `0`, `package-lock.json` `0`. No `Total:` line with a non-zero count | 0 |
| 4 | Trivy secret over `code/` and `infra/` | `0` | 0 |
| 5 | Semgrep | `0 findings` | 0 |
| 6 | self-test | `6/6 fixtures behaved as expected` | 0 |
| 7 | `check-done.mjs` | all 10 gates PASS, gate 10 names a green `CI` run | 0 |

Baseline for comparison, so a reviewer can see the delta rather than trust a claim:
today checks 3 gives 72/30/30/3/3/3/3/0 across the eight POMs and 1 for the frontend
(`.claude/outputs/2026-09-15-infra-w59-scan-raw.md`).

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| **The Spring Boot 3.3 → 3.5 upgrade breaks the build or the 24 existing tests** — the largest risk in this ticket, and the reason it is no longer size S | Medium | Do it as the **first commit on the branch, alone**, so `mvnw clean verify` either passes or points at exactly one change. `code/backend` is 16 Java files with no ported domain logic. If it does not go cleanly, stop and re-scope rather than patching around it — Question 2 exists so this is the founder's call, not the implementer's |
| Maven Central returns HTTP 429 and blocks the runner for 30 minutes | **High — already observed** | `actions/setup-java` with `cache: 'maven'`, `./mvnw dependency:resolve` before scanning, and Trivy with `--offline-scan` |
| **`vite` 5 → 8 breaks `npm run build`, or trips `eslint --max-warnings 0`** — three majors, plus a forced `@vitejs/plugin-react` bump, in a pull request that also moves Spring Boot | Medium-High | Own commit, separate from the backend upgrade (§14 task 2), so a red build names which upgrade did it. The frontend is 10 tracked files and `vite.config.js` uses only stable options. Verification step 2 catches it before the pull request. If vite 8 does not go cleanly, fall back to `^6.4.3` — the scanner accepts it and the gate still goes green — and raise vite 8 as its own ticket rather than absorbing it here |
| Dev images are noisy — full JDK, root user | High | Container scan is report-only until `W-49`. It reports; it never blocks |
| Alert fatigue turns the gate into something people route around | Medium | `HIGH,CRITICAL` plus `--ignore-unfixed` only. Nothing blocks that has no published fix |
| A new CVE lands overnight and fails an unrelated pull request | Medium | The daily rescan surfaces it against `main` first, so it is a known item rather than a surprise on someone's branch |
| A scanner silently stops finding anything and every build goes green | Low | Fixture 5 in §7 — a clean fixture must pass *and* dirty fixtures must fail |

## 10. Rollback

Nothing here reaches a database, a cloud resource or a deployed system.

1. **Scanning too noisy** — set the three `security-*` jobs to `continue-on-error: true`.
   One line each; they report without blocking, and the pipeline is back to `W-03`
   behaviour within a commit.
2. **Spring Boot 3.5 upgrade goes wrong after merge** — revert the single pom commit.
   It is deliberately isolated (§9) so this does not unpick the scanning work.
3. **Whole ticket** — revert the pull request. `ci.yml` returns to its four jobs,
   `security.yml` and `dependabot.yml` disappear, `pom.xml` and `package.json` return to
   `d3eb07e`. No state to unwind.
4. `D-45` and `D-46`, if approved, are recorded in `07-decisions.md` through `sync-docs`.
   Reverting the code does not silently revert the decision — raise a superseding entry.

## 11. Gap disposition

| Gap | Disposition |
|---|---|
| `DEBT-004` secrets in `.properties` | **Not addressed. Preventative only.** `security-secrets` stops a *new* credential entering `code/` or `infra/`. The credentials the gap names are in `legacy/`, which is frozen and excluded from the gate, and the exposed Keycloak administrator password (`legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:16-21`) is a **live incident** — `active-work.md` Incidents #1 says rotate now. This ticket does not rotate it and must not be read as closing it |
| `DEBT-033` HRMS↔Payroll `X-API-KEY: md5("12345AB")` committed in plaintext (`GAP_INVENTORY.md:83`) | **Deferred, and named here because it is the most on-topic gap in the inventory.** The secret is in `legacy/`, so the gate never sees it. It matters at port time: whoever ports the integration must not carry the shared secret across. Belongs to `W-57` deny-by-default auth. Flagged, not fixed |
| `DEBT-002` no Flyway / `ddl-auto` | Discounted — `W-06`. `ci.yml`'s `static` job already greps for `ddl-auto` |
| `DEBT-003` no test coverage | Discounted — `W-04` merged. §7 adds tests for what this ticket changes |
| `DEBT-018` no indexes | Discounted — `W-55`. No schema change here |
| `DEBT-021` unlocked schedulers | Discounted — `W-52`. The cron in `security.yml` is a GitHub Actions schedule, not an in-process `@Scheduled` bean, so the gap does not apply |
| `BUG-004` `ddl-auto` schema drift | Discounted — `W-06` |

## 12. Standing rules

| Rule | Impact |
|---|---|
| 1 — founder approval first | This spec. Plus `D-45` and `D-46` separately — Questions 2 and 3 |
| 3 — never edit `docs/` in feature work | Spec written to `.claude/outputs/`; moves to `docs/` on approval via `sync-docs` |
| 4 — Flyway, never `ddl-auto` | No schema change. `ddl-auto` written nowhere |
| 5 — upstream remotes read-only | Nothing is pushed to the four origin repos |
| 7 — `tenant_id` everywhere | No new entity, table or query |
| `legacy/` frozen | Not edited, and explicitly excluded from all scanners. Enforced by `guard-edit` and gate 4 (`check-done.mjs:163`) |
| No module references another | No module code changes; the pom parent version is module-neutral |
| Every endpoint authenticated | No new endpoint |
| No secret value in any file | The fixtures in §7 use synthetic keys in a temp dir, never committed |
| `D-43` — CI cannot be a required check | The plan does **not** assume one. Enforcement is gate 10 locally, which is exactly why the blocking jobs are in `ci.yml` |

## 13. Done when

1. `security-deps`, `security-code` and `security-secrets` run in `.github/workflows/ci.yml` and fail the build on a fixable HIGH/CRITICAL, a Semgrep finding, or a detected secret.
2. The container scan runs inside the `images` job with `continue-on-error: true`, reporting without blocking.
3. `.github/workflows/security.yml` runs daily at 02:00 and on `workflow_dispatch`, and is documented as non-gating.
4. `code/backend/pom.xml` is on Spring Boot 3.5.x and `./mvnw clean verify` passes with the 24 existing tests green.
5. `code/frontend` is on `vite ^8` with a compatible `@vitejs/plugin-react`; `npm run lint` and `npm run build` pass with zero warnings.
6. Verification steps 3, 4 and 5 report **0** across `code/`.
7. `.github/scripts/security-gate-selftest.sh` passes 6/6 and runs in CI.
8. `.github/dependabot.yml` is committed and alerts are enabled on the repository.
9. `node .claude/scripts/check-done.mjs <pr>` passes all 10 gates.
10. This spec is updated to match what was built, and moved to `docs/target-state/features/W-59-scanning.md`.

## 14. Implementer tasks

One area each, sequenced so the riskiest change is isolated and reviewable on its own.

| # | Area | Task | Verifier runs |
|---|---|---|---|
| 1 | `code/backend` | Spring Boot parent 3.3.13 → 3.5.x. **Nothing else in this commit** | §8 step 1 |
| 2 | `code/frontend` | `vite` → `^8` and `@vitejs/plugin-react` to match, regenerate lockfile. **Nothing else in this commit** | §8 step 2 |
| 3 | `.github/workflows` | Three `security-*` jobs in `ci.yml`; container scan step inside `images` | §8 steps 3-5 |
| 4 | `.github/workflows` | `security.yml` daily rescan; `dependabot.yml` | §8 step 3 |
| 5 | `.github/scripts` | `security-gate-selftest.sh`, wired into `security-code` | §8 step 6 |
| 6 | `docs/` via `sync-docs` | Record `D-45` and `D-46` in `07-decisions.md`, and `D-39` → `D-45` in its supersession table (`:81-92`). **Not an implementer task** — `guard-edit` blocks `docs/`; runs through `sync-docs` with an approved diff | gate 5 |

## 15. What changed from `542ee55`

| Review finding | Change |
|---|---|
| **R-1 High** — §1 table was not real output and contradicted the repo's own evidence | §1 rebuilt from a scan run today. Raw output committed to `.claude/outputs/2026-09-15-infra-w59-scan-raw.md`. Real figures: 72/30/30/3/3/3/3/0 per POM, **29 unique CVEs across 11 packages**, and the decisive row is `spring-boot` itself at 3.3.13 fixed only in 3.5.14 — which is the actual argument for `D-45`, and was the row the old table omitted. The old table's CVE IDs (CVE-2024-52316 etc.) do not appear in the real output at all |
| R-2 — `D-45`/`D-46` shown as settled | Header marks both **proposed**, with a callout that the register ends at `D-44`. Questions 2 and 3 |
| R-3 — "Hard rule 5" miscited | §12 cites rule 5 correctly (upstream remotes read-only) and attributes legacy-frozen to `guard-edit` and gate 4 |
| R-4 — `DEBT-033` silent | §11 names it, explains why the gate cannot see it, routes it to `W-57` |
| R-5 — no Tests section | §7 added. Deliberate breaks become `security-gate-selftest.sh`, run in CI, with a clean fixture so the gates cannot pass by failing everything |
| R-6 — Vite 8 overshoots | Raised as Question 4 with the scanner's own minimum (`^6.4.3`) recommended. **Founder chose `^8`** to avoid a second upgrade — see §16. The finding stands as a risk rather than a change: §9 carries it, with `^6.4.3` as the documented fallback if vite 8 does not go cleanly |
| R-7 — react-router 7 below the gate | Out of scope. Done-when 5 and 6 no longer disagree |
| R-8 — biggest risk missing | §9 leads with the Spring Boot upgrade and isolates it as task 1 |
| R-9 — `--max-warnings 0` | §9 risk row; §8 step 2 |
| R-10 — no task breakdown | §14 |

---

## 16. Decisions taken — 2026-09-16

All five questions from revision 3 were put to the founder and answered.

| # | Question | Decision | Effect |
|---|---|---|---|
| 1 | Size, now the upgrade is in scope | **M** | Relabel `#79` `size-S` → `size-M` |
| 2 | Approve `D-45`, Spring Boot 3.5.x superseding `D-39`? | **Approved** | §14 task 1; `D-39` superseded; task 6 records it |
| 3 | Approve `D-46`, Dependabot? | **Approved — alerts and `dependabot.yml`** | §2(e); task 4 |
| 4 | `vite ^6.4.3` or `^8`? | **`^8`** — against the recommendation, to avoid upgrading twice | §5; forces `@vitejs/plugin-react`; §9 carries the fallback to `^6.4.3` |
| 5 | Widen the gate? | **No — the three blocking scans as specified** | Three follow-ups filed instead, below |

On question 5 the founder initially selected all four options, including both "no
additions" and the three additions. Re-asked; resolved to **no additions now**, with the
three recorded as follow-up tickets so they are sequenced rather than dropped:

| Follow-up | Blocked by | Why not now |
|---|---|---|
| Block on container CVEs | `W-49` | The dev images are being replaced; gating artefacts on their way out buys nothing |
| Licence scanning (GPL/AGPL/SSPL in a commercial product) | Needs an allowlist policy decision | Real commercial risk, but new scope and no recorded decision to hang it on |
| Lower the threshold to MEDIUM | This ticket proving quiet first | `09-build-order.md:285` — MEDIUM is where alert fatigue usually starts |

---

## Where this stands

**The five decisions are made. The spec itself is not yet approved.**

| | |
|---|---|
| Spec | `.claude/outputs/2026-09-15-infra-w59-scanning.md` (this file) |
| Raw scanner output | `.claude/outputs/2026-09-15-infra-w59-scan-raw.md` |
| Prior review | `.claude/outputs/2026-09-15-review-spec-W-59-rev2.md` |
| Superseded draft | `origin/w-59` `542ee55` |

On approval: this file moves to `docs/target-state/features/W-59-scanning.md` with
`Status: Approved`, `Approved by` and `Approved on` filled in, `#79` is relabelled to
`size-M`, and `/develop W-59` may start. Not before.
