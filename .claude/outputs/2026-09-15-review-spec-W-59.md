# Spec review — W-59 — scanning — 2026-09-15

Draft reviewed: `docs/target-state/features/W-59-scanning.md` on `origin/w-59` (`b7e36d1`),
212 lines. Ticket #79, label `ready`, owner BirenGit. Not blocked.
Citation evidence: `.claude/outputs/2026-09-15-review-spec-W-59-citations.md`.
Baseline evidence: `.claude/outputs/2026-09-15-infra-w59-scanning-evidence.md`.

## Verdict

**NOT READY** — 7 High findings. Return to `/infra-task W-59`.

The spec is well-written and its tool choice is right. It fails on one thing, and
everything else follows from it: **it never ran the scanners it is specifying.** The
baseline was measured with `npm audit` and `mvn dependency:tree`; neither reports CVEs
for the backend, and neither is the gate the spec proposes. Running the spec's own gate
against `main` finds 30 fixable CVEs the spec records as "clean". That one wrong number
sets the size, the "starts 100% green" promise, and four acceptance criteria.

## Checks

| # | Check | Result | Notes |
|---|---|---|---|
| 1 | Citations resolve | 15 of 17 | 2 drifted (F-9, F-10). Doc line numbers are all exact |
| 2 | Template complete | 8 of 10 sections | No §3 Flow as such, no §7 Tests; §6 DB correctly N/A but not stated |
| 3 | Gaps and standing rules | 1 High, 2 Medium | DEBT-004 mis-dispositioned; DEBT-033 silent; D-39 contradicted and unmentioned |
| 4 | Work is buildable | 4 High, 1 Medium | Verification path does not exist; 3 of 4 checks have no command; container-scan has no input |

Standing rules on schema, `tenant_id`, Flyway, `Money`/`BigDecimal`, indexes and
expand/contract are **not applicable** — this ticket changes no table. Nothing to report.

## Citations

| Cited | Exists? | Line holds | Verdict |
|---|---|---|---|
| `09-build-order.md:285` | yes | `**W-59 Scanning** · Build: … Watch: fix the initial backlog once…` | OK — quoted verbatim |
| `08-work-plan.md:158` | yes | `W-64 · Penetration test · External engagement · Remediation` | OK |
| `08-work-plan.md:152` | yes | `W-58 · Tenant isolation tests · …` | OK |
| `.github/workflows/ci.yml` | yes | jobs `backend` `frontend` `static` `images`; `on:` pull_request + push main | OK |
| `code/frontend/package.json` | yes | `vite ^5.4.10` (devDep), `react-router-dom ^6.28.0` (dep) | OK |
| `code/frontend/package-lock.json` | yes | — | OK |
| `code/backend/pom.xml` | yes | parent 3.3.13; 7 modules | OK |
| `code/backend/mvnw` | yes | wrapper + `.mvn/wrapper/maven-wrapper.properties` present | OK — DEBT-032 does not apply to the new backend |
| `infinevo-backend:dev` / `infinevo-frontend:dev` | yes | `ci.yml:209`, `ci.yml:212` — `docker build -t …:dev .` | OK |
| `DEBT-004` | yes | `GAP_INVENTORY.md:42` — secrets hardcoded in `.properties`, both **frozen** backends | OK as a citation; mis-dispositioned — F-7 |
| `PLAT-09` as a gap | **no** | capability, `01-platform-shape.md:161`. Absent from `GAP_INVENTORY.md` | **DRIFTED** — F-10 |
| `D-38` Java 21 / `D-42` Node 24 / `D-43` no branch protection | yes | `07-decisions.md` | OK |
| `W-56` Secrets (Key Vault) | yes | `08-work-plan.md:150` | OK |
| `W-49` Containerisation · `W-64` Penetration test | yes | — | OK |
| "Hard rule 5" = legacy frozen | **no** | `CONVENTIONS.md:19` rule 5 = **upstream remotes are read-only** | **DRIFTED** — F-9 |
| `D-39` Spring Boot 3.3.x | **not cited** | `07-decisions.md:51` — the decision this ticket's remediation reopens | **MISSING** — F-2 |

Explorer's report was spot-checked on four items (`09-build-order.md:285`,
`08-work-plan.md:152`/`158`, `CONVENTIONS.md:19`, `ci.yml:209`/`212`) and held.

## Findings

| ID | Severity | Finding | Where (draft §) | Status |
|---|---|---|---|---|
| F-1 | **High** | The backend baseline is recorded as "clean dependency tree"; the spec's own gate (`trivy fs --severity HIGH,CRITICAL --ignore-unfixed`) finds **30 fixable CVEs — 6 CRITICAL, 24 HIGH** across all 8 POMs. `dependency:tree` reports dependencies, not advisories | §1 line 41; §3 line 108 | OPEN |
| F-2 | **High** | "Pipeline starts 100% green" and done-when 3 are unachievable on Spring Boot 3.3.13 — 3.3.x is out of OSS patch support and every fix is in the 3.5 line. That reopens **`D-39`** (`07-decisions.md:51`), which the spec never mentions. A framework minor upgrade is not size S | §2 line 66; §9 item 3 | OPEN |
| F-3 | **High** | Verification step 3 mounts `d:/InfinivoHCM/infinevo-platform`. The repository is `D:\Infinevoclouds`. The command cannot run as written | §5 line 141 | OPEN |
| F-4 | **High** | Gate 10 of the done-check counts **only the workflow named `CI`** — `check-done.mjs:520`, and `:559-561` "other workflows do not count". A separate `security.yml` can be red and `check-done.mjs` still passes. With `D-43` (no branch protection) nothing else enforces it, so done-when 2 is satisfied while the ticket's mandate — gating every merge — is not | §9 items 2 and 8; Decision 1(b) line 201 | OPEN |
| F-5 | **High** | `container-scan` is specified to scan "container images built by the pipeline", but a separate workflow cannot reach `ci.yml`'s `images` job — it builds to the local daemon (`ci.yml:209`, `:212`), pushes to no registry and uploads no artifact. On the daily cron there is no build at all. The job has no input as written | §2 line 65; §3 line 94 | OPEN |
| F-6 | **High** | Done-when 5 requires SAST and secret scanning to run "with zero findings", but **Semgrep has never been run against `code/`** and no secret-scan baseline was taken. The value is unknown at approval time, so the "fix the initial backlog once" mandate the spec itself quotes cannot be scoped, and the criterion may be unreachable | §5 table line 150; §9 item 5 | OPEN |
| F-7 | **High** | `DEBT-004` is dispositioned **"Addressed"**. The secrets it names live in `legacy/**/*.properties`, which §2 line 63, §3 line 107 and §7 line 170 all explicitly **exclude** from scanning. The gate is preventative only — as the spec's own header line 15 says. Marking it Addressed would close a live incident (`active-work.md` Incidents #1, "rotate now") this ticket does not touch | §6 line 159 | OPEN |
| F-8 | Medium | Done-when 4 gates on `npm audit` reporting 0, but the pipeline gate is Trivy, which **already reports 0** for the frontend (dev deps suppressed, `react-router` below HIGH in Trivy's data). Two tools, two answers, and the one in the acceptance criterion is not the one in the pipeline. Reaching `npm audit` 0 needs `vite` 5→8 and `react-router-dom` 6→7, both major | §2 line 67; §9 item 4 | OPEN |
| F-9 | Medium | "Hard rule 5" is cited for legacy staying frozen. Rule 5 is *upstream remotes are read-only* (`CONVENTIONS.md:19`). Legacy-frozen is the `guard-edit` hook and gate 4 (`check-done.mjs:163`) | §3 line 107 | OPEN |
| F-10 | Medium | `PLAT-09` is listed under **Gaps addressed**. It is a capability (`01-platform-shape.md:161`), not a gap; it does not appear in `GAP_INVENTORY.md` | header line 15; §6 line 160 | OPEN |
| F-11 | Medium | `DEBT-033` — `X-API-KEY: md5("12345AB")`, a shared secret committed in plaintext (`GAP_INVENTORY.md:83`) — is not mentioned. A secret-scanning ticket should say whether the scanner catches this class, or defer it explicitly | §6 | OPEN |
| F-12 | Medium | Out-of-scope hands production Dockerfiles to `W-49` (#69) but does not note that `W-49` **replaces the dev image targets this ticket's `container-scan` consumes** (`active-work.md`, the queue). Two in-flight tickets, the same two images, different owners | §2 line 72 | OPEN |
| F-13 | Medium | Three of the four verification checks have no runnable command: "Ensure `security.yml` is valid YAML", "all 4 security jobs pass green", "under 3 minutes total". The `verifier` agent has only Bash | §5 lines 143-151 | OPEN |
| F-14 | Medium | Template deviation: no §3 Flow and no §7 Tests section. The §4 deliberate breaks are the right instinct but are not named as tests and no file holds them, so nothing survives the PR. §6 Database is correctly absent but should say "no schema change" rather than be silent | whole draft | OPEN |
| F-15 | Low | Break 3 uses a fabricated `ghp_` token; whether Trivy's `github-pat` rule matches a non-checksummed value is untested. Name the expected rule ID so the break proves the rule and not entropy | §4 line 120 | OPEN |

## What could pass while the feature is broken

Asked of every criterion in §5 and §9, three answers came back — they are F-4, F-5 and F-8:

- **F-8** is the `W-02` pattern exactly. Done-when 4 checks `npm audit`; the pipeline gates
  on Trivy. Trivy is green on the frontend **today**, before any work. A run can show four
  green jobs and one satisfied criterion with the dependency gate never having refused
  anything.
- **F-4** lets every job go red without blocking a merge.
- **F-5** lets `container-scan` "pass" by scanning an image that was never built.

## What is good

- **The tool choice is correct and the reasoning is sound.** Trivy plus Semgrep, argued
  from `D-43` rather than asserted, is the right answer for a private repository on the
  Free plan. Decision 3's rejection of both "fail on everything" and "report only" is the
  judgement the build-order note asks for.
- **`--ignore-unfixed` is the right default** and is applied consistently across the
  severity gate, the risk table and the decision. That is the single choice that decides
  whether this pipeline is still running in six months.
- **§4 deliberate breaks are the best section in the draft.** Proving a gate fails when it
  should is exactly what `#104` found missing from `W-03` — "`images` has never been
  observed red". This spec proposes to prove it before merge. Keep that section, give it a
  home in the repository, and it fixes the pattern rather than repeating it.
- **Every documentation line number resolves.** 15 of 17 citations exact, and the two that
  drifted are label errors, not imaginary targets. The frozen-system discipline held.

## The one change that matters most

Re-measure the baseline with the gate the spec proposes, not with `dependency:tree`, and
let the real number choose the ticket's shape. If the backend backlog is 30 CVEs whose
fixes require Spring Boot 3.5, then either `W-59` gets bigger and reopens `D-39`
deliberately, or it ships the pipeline **non-blocking on the backend** with the backlog
filed as its own ticket. Both are defensible. "Clean" is not.
