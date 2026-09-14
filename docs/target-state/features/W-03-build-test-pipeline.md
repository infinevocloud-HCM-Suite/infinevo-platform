# W-03 — Build & test pipeline

| Field | Value |
|---|---|
| **Work item** | `W-03` · issue [#4](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/4) |
| **Kind** | **Infra** — no screen, no endpoint, no table |
| **Stream / track** | Stream A — Foundation · Track P |
| **Wave** | 1 — Foundations |
| **Size / skill** | S · INFRA |
| **Owner** | SayInfi |
| **Blocked by** | — (`W-01` merged) |
| **Blocks** | `W-54` deployment pipeline · `W-59` dependency and code scanning |
| **Capabilities** | `PLAT-11` build & deploy pipeline |
| **Decisions** | `D-38` Java 21 · `D-42` Node 24 · `D-43` no branch protection |
| **Gaps addressed** | `DEBT-002` ddl-auto · `DEBT-004` secrets — fixed forward, §6. `DEBT-003` deferred to `W-04` |
| **Status** | **Approved 2026-09-14 — in progress** |
| **Approved by** | Founder |
| **Approved on** | 2026-09-14 |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

There is no continuous integration. `.github/workflows/` contains one file, `.gitkeep`
(62 bytes). Every check that exists runs on the machine of whoever is merging:
`.claude/scripts/check-done.mjs` runs nine gates and writes a receipt, and
`.claude/hooks/guard-merge.mjs` refuses a merge without one.

That arrangement has three holes:

1. **It is not reproducible.** `check-done.mjs:163-176` shells out to the local Maven
   wrapper and `:177-189` to the local `npm`. A developer with a different JDK, a stale
   `node_modules`, or a warm `~/.m2` gets a different answer than a clean machine would.
2. **It proves nothing to a reviewer.** The only artefact reaching the pull request is
   text pasted into the body by the author.
3. **Nobody but the founder has run the stack.** `active-work.md` records `W-02`
   done-when item 11 as unticked. A build that only ever runs in one place is a build
   that works in one place.

`D-43` records that GitHub refuses branch protection on a private repository on the Free
plan, so CI **cannot** be a required check. This spec does not pretend otherwise — see
Decision 1.

**Baseline, measured 2026-09-14 on `main` at `e01bc79`:**

| Command | Exit | Output |
|---|---|---|
| `./mvnw -B -q clean verify` (`code/backend`) | 0 | silent — BUILD SUCCESS |
| `npm run lint --silent` (`code/frontend`) | 0 | no findings |
| `npm run build --silent` (`code/frontend`) | 0 | 1443 modules, 432.55 kB, built in 4.83s |

All four gates are green before the pipeline exists. That is what makes the build-order
*Watch* achievable — "turn the gates on from the first commit, not once there is code to
fix" (`09-build-order.md:167`).

## 2. Scope

**In scope**

- One workflow, `.github/workflows/ci.yml`, on `push` to `main` and on `pull_request`
- **Compile + test gate** — `./mvnw -B clean verify` across all seven backend modules
- **Lint gate** — frontend `npm run lint`; backend linting added, see Decision 2
- **Image build gate** — `docker build` of the two existing dev Dockerfiles, no push
- **Static gates mirrored from `check-done.mjs`** — `legacy/` untouched, no `ddl-auto`,
  no `float`/`double` money field. Cheap greps that belong server-side too
- Toolchain pinned to the decisions: Java 21 (`D-38`), Maven 3.9.11, Node 24 (`D-42`)
- Caching for `~/.m2` and npm, so a warm run stays under five minutes
- Surefire XML and the frontend `dist/` uploaded as artefacts

**Out of scope**

- **Registry push.** `W-49` (#69) owns the production Dockerfiles, `W-50` the registry
- **Deployment.** `W-54` needs `W-50` and this ticket — `09-build-order.md:63`
- **Dependency and secret scanning.** `W-59` needs this ticket — `:64`
- **Coverage thresholds.** `W-04` (#5) builds the test foundation. A threshold over two
  test files would be theatre
- **Flyway validation** (`W-06`) and the **tenant-column check** (`W-07`). Both are named
  in `09-build-order.md:170-171` as later pipeline steps. This ticket's job is to leave a
  workflow they can be added to, not to add them

## 3. What gets built

One workflow. Four jobs — the first three in parallel, `images` waiting on the two that
produce something to package.

```
push to main / pull_request
  |- backend   setup-java 21 (temurin) + cache ~/.m2 -> mvnw -B clean verify -> surefire XML
  |- frontend  setup-node 24 + cache npm -> npm ci -> npm run lint -> npm run build -> dist/
  |- static    grep gates: legacy/ untouched · no ddl-auto · no float/double money
  |- images    docker build dev.Dockerfile.backend + dev.Dockerfile.frontend  [needs: backend, frontend]
```

| File | Change |
|---|---|
| `.github/workflows/ci.yml` | New. The four jobs above |
| `.github/workflows/.gitkeep` | Deleted — no longer needed once a real workflow exists |
| `code/backend/pom.xml` | `spotless-maven-plugin` + `palantir-java-format`, `check` bound to `validate` (Q2) |
| `.claude/scripts/check-done.mjs` | **Tenth gate** — CI green for the exact `HEAD` commit (Q1) |
| `.claude/skills/merge/SKILL.md` | Gate table updated from nine rows to ten (Q1) |

`concurrency` cancels superseded runs on a branch, so a push during a run does not
burn two sets of Free-plan minutes.

Nothing else is touched. No module source, no frontend source, no migration, no
configuration file, no secret, no environment.

## 4. Proving the gates

The deliverable is a gate, so the only meaningful test is that **each one fails when it
should**. Every case is proved on a throwaway branch `W-03-gate-proof`, linked from the
pull request by run URL, and never merged.

| # | Deliberate break | Job that must go red |
|---|---|---|
| 1 | Failing assertion in a `shared` test | `backend` |
| 2 | Unused variable in `code/frontend/src` | `frontend` |
| 3 | `spring.jpa.hibernate.ddl-auto=update` in any `.properties` | `static` |
| 4 | `private double salary;` in a Java file | `static` |
| 5 | One-character edit under `legacy/` | `static` |
| 6 | Syntax error in a Dockerfile | `images` |
| 7 | *(control)* clean tree | none — all four green |

## 5. Verification

Run by **verifier** on a clean checkout.

```bash
# 1 - the workflow is pinned to the decisions
grep -nE "java-version|node-version|distribution" .github/workflows/ci.yml
#    expect: java-version: '21'   node-version: '24'   distribution: 'temurin'

# 2 - the gates, exactly as CI runs them
cd code/backend && ./mvnw -B clean verify            # expect exit 0, BUILD SUCCESS
cd ../frontend  && npm ci && npm run lint            # expect exit 0, no findings
npm run build                                        # expect exit 0, dist/ written
# Context is the REPOSITORY ROOT, not code/backend - both Dockerfiles COPY
# repo-root-relative paths, and compose.yml uses `context: ../..`.
cd ../.. && docker build -f infra/docker/dev.Dockerfile.backend  -t infinevo-backend:dev  .
docker build -f infra/docker/dev.Dockerfile.frontend -t infinevo-frontend:dev .

# 3 - CI ran, and on this commit
gh run list --workflow=ci.yml --limit 5
gh run view --log-failed || echo "no failures"

# 4 - the local gate still works
node .claude/scripts/check-done.mjs <pr>
```

| Check | Expected | Result |
|---|---|---|
| `ci.yml` present, `.gitkeep` gone | file exists | |
| `backend` job on a clean runner | BUILD SUCCESS, surefire XML uploaded | |
| `frontend` job | lint exit 0, `dist/` uploaded | |
| `static` job on a clean tree | all three greps find nothing | |
| `images` job | both images build, **neither is pushed** | |
| Each break in §4 | the named job goes red, run URL in the PR body | |
| Whole run, warm cache | under 5 minutes | |
| `check-done.mjs` | still passes — this ticket does not break the local gate | |

## 6. Gap disposition

| Gap | Disposition |
|---|---|
| `DEBT-003` no tests | **Deferred to `W-04`** (#5). This ticket builds the gate, not the coverage behind it |
| `DEBT-002` ddl-auto | **Fixed forward.** The `static` job makes it unable to enter the new codebase |
| `DEBT-004` secrets in properties | **Fixed forward**, same job. Rotation of the live credential stays an incident, not this ticket |
| `DEBT-018` no indexes | **Discounted.** A property of the frozen Payroll backend; no new schema exists to check |
| `DEBT-021` unlocked schedulers | **Discounted**, same reason |

> Note for `sync-docs`: the `infra-task` skill maps `DEBT-001` to ddl-auto and `DEBT-002`
> to secrets. `GAP_INVENTORY.md:39-42` reads `DEBT-001` dead code, `DEBT-002` ddl-auto,
> `DEBT-004` secrets. The inventory is right; the skill text is stale.

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| CI reports but cannot block, so a red PR is merged anyway (`D-43`) | **High** | Decision 1. Until then `check-done.mjs` stays the real gate and CI is evidence |
| A backend linter added now flags the whole tree at once | Medium | Pick a formatter with an apply mode; one formatting commit before the gate goes on |
| The image gate duplicates or pre-empts `W-49` (#69) | Medium | Decision 3. Dev Dockerfiles only, never pushed, job named provisional |
| Free-plan Actions minutes exhausted by a build on every push | Low | Cache `~/.m2` and npm; `concurrency` cancels superseded runs |
| Gates pass on the runner but fail locally, or the reverse | Low | CI runs the identical commands `check-done.mjs` runs — no CI-only flags |
| `CONTRIBUTING.md:44` says Node 20, `D-42` says Node 24 | Certain | Pin CI to 24. Flag the doc for `sync-docs`; do **not** edit it in this ticket |
| `*.java text eol=lf` breaks existing Windows checkouts silently | **Certain** | F-15. The clean filter still matches the index, so `git status` reads clean while the disk stays CRLF and Spotless fails on untouched files. `git add --renormalize .` fixes it; documented in `CONTRIBUTING.md` §1 |

## 8. Rollback

Nothing is in production and the pipeline deploys nothing, so rollback is total:
`git revert` the merge commit. The workflow disappears, `check-done.mjs` and
`guard-merge` are untouched and still enforce the other gates locally, and no image,
registry, environment, or secret was created to clean up. A single misbehaving job can
be disabled from the Actions tab without reverting the rest.

## 9. Done when

1. `.github/workflows/ci.yml` exists and `.gitkeep` is deleted.
2. The workflow triggers on `pull_request` and on `push` to `main`.
3. Java is pinned to 21 (`D-38`), Maven to 3.9.11, Node to 24 (`D-42`).
4. The `backend` job runs `./mvnw -B clean verify` over all seven modules and uploads
   the surefire XML.
5. The `frontend` job runs `npm ci`, `npm run lint`, `npm run build`, uploads `dist/`.
6. The `static` job fails on a change under `legacy/`, on `ddl-auto` in any
   configuration file, and on a `float` or `double` money field.
7. The `images` job builds both dev Dockerfiles and **pushes neither**.
8. Spotless runs in the `backend` job; `mvn spotless:apply` fixes a violation locally.
9. `check-done.mjs` has a **tenth gate** that fails when CI is red, missing, or green
   for a commit other than the one at `HEAD`.
10. `merge/SKILL.md` documents ten gates, not nine.
11. All seven cases in §4 have been demonstrated, with run URLs in the PR body.
12. A clean `main` is green and a warm run takes under five minutes.
13. `check-done.mjs` passes on this pull request — all ten gates.
14. `active-work.md` updated: `W-59` (#88) becomes unblocked by this merge.

---

## Decisions — confirmed by the founder 2026-09-14

### Q1 — CI reports, but `D-43` means it cannot block. Does `check-done.mjs` read it? → **B, add a tenth gate**

A red CI run must be fixed before merge. GitHub cannot enforce that on a private
repository on the Free plan, so the enforcement is added where enforcement already
lives: `check-done.mjs` gains a **tenth gate** that asks
`gh run list --commit <HEAD> --workflow=ci.yml` and refuses unless the conclusion is
`success` for the exact commit being merged.

This fits the existing design rather than bolting onto it. The receipt is already
invalidated by any commit made after the check (`guard-merge.mjs`), so pinning the CI
result to `HEAD` uses the same guarantee. The nine-gate done-check becomes a ten-gate
done-check, and `merge/SKILL.md` needs its gate table updated to match.

**Consequence accepted:** this ticket edits harness code (`.claude/scripts/`), not only
`.github/workflows/`. That is outside `code/` and outside the usual shape of a `W-nn`
ticket, and was approved knowingly.

### Q2 — What is the backend lint gate? → **A, Spotless**

`spotless-maven-plugin` with `palantir-java-format`, bound so `mvn spotless:check` runs
in CI and `mvn spotless:apply` fixes locally. Chosen over Checkstyle because an apply
mode means nobody ever hand-fixes formatting, and chosen **now** because the backend is
seven modules, two test files and no controllers: turning it on costs one commit today
and a week at `W-20`.

### Q3 — Does the image gate wait for `W-49`? → **A, build now**

CI builds `infra/docker/dev.Dockerfile.backend` and `dev.Dockerfile.frontend` and
pushes neither. The job is named to show it is provisional; `W-49` (#69) replaces the
targets with the production Dockerfiles and the single web/worker image. This keeps the
build-order *Watch* honest — the gate exists from the first commit.

**Both tickets are `ready` and assigned to different people. SayInfi (#4) and BirenGit
(#69) must be told together**, or `W-49` will not know it inherits this job.

### Q4 — Scope boundary → **confirmed narrow**

`W-03` ships four gates: compile+test, lint, static, image build. It does **not** ship:

| Excluded | Owned by |
|---|---|
| Registry push | `W-50` |
| Deployment | `W-54` |
| Coverage threshold | `W-04` (#5) — a threshold over two test files is theatre |
| Dependency and secret scanning | `W-59` (#88) |

Each excluded item has a ticket that owns it and adds its layer to the same `ci.yml`.
