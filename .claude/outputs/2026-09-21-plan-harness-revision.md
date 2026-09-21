# Plan — harness revision: stop the review loop, ship a feature

**Date** 2026-09-21 · **Status** Awaiting founder approval · **Changes nothing until approved**

Founder decisions taken 2026-09-21, and this plan is built on them:

| Decision | Chosen |
|---|---|
| Finding bar | High **and** Medium block; Low leaves as a ticket |
| Next wave | A vertical HCM slice end to end |
| Scope now | Write this plan, change nothing |

---

## 1. The problem, measured

| Measure | Value |
|---|---|
| Days building | 8 |
| Tickets closed / open | 9 / 91 |
| Backend Java files / frontend src files | 31 / 9 |
| HCM features shipped | 0 |
| Words of gate reports in `.claude/outputs/` | 110,725 across 111 files |
| W-08 gate passes consumed | 11 (5 spec reviews, 3 verify, 3 review) |
| W-08 findings still OPEN after 3 rounds | 13 — 0 High, 7 Medium, 6 Low |

Ten merges, all infrastructure. No leave request, no payslip, no attendance record exists.

---

## 2. Why the loop does not terminate

Five mechanisms, each with its evidence.

**The skill and the merge gate disagree about what "done" means.**
`.claude/skills/develop/SKILL.md:88` — "Collect every finding whose status is OPEN"
`.claude/scripts/check-done.mjs:127` — gate 3 blocks only on **High**

**Every round re-reviews the whole branch, so the surface never shrinks.**
`.claude/skills/review/SKILL.md:24` fixes the diff at `main...HEAD` each round
`.claude/outputs/2026-09-21-review-W-08-rev3.md:66-74` — F-19 to F-22 are new at round 3

**Fixes manufacture the next round's findings.**
Round 1 F-1 fixed produced round 2 F-10 and F-11, both High
Round 2 F-10 fixed produced round 3 F-19 and F-20

**A real-but-not-blocking finding has no way to leave the branch.**
`.claude/skills/develop/SKILL.md:93` requires a GitHub ticket for DEFERRED; no gate checks it
`.claude/outputs/2026-09-21-review-W-08-rev3.md:68-78` — six findings marked "OPEN, carried"

**Round exhaustion has no defined outcome.**
`.claude/skills/develop/SKILL.md:47` says hand it to the founder; W-08 did, and stopped

The escape that worked was invented by hand and never written down: rev5 of the spec review
converted three findings into Conditions C-1 to C-3 and approved, ending a five-round loop in
one pass.
`.claude/outputs/2026-09-20-review-spec-W-08-rev5.md:26-29`

---

## 3. Two causes outside the harness

**Ticket size.** W-08 bundles a servlet filter, a DataSource proxy, a Flyway migration, an RLS
policy, a `SECURITY DEFINER` function, a Spring Security chain, an OAuth2 resource server and 34
integration tests. A 13-finding tail is the arithmetic of that scope, not a failure of review.

**Build order.** `.claude/work/active-work.md:16` records that the W-51 Azure estate "has never
been deployed" — verified as code, not as an environment. Secrets and scanning shipped ahead of
any HCM capability.

---

## 4. A concern to record before proceeding

Medium still blocking is the founder's call and this plan implements it. It should be recorded
that W-08's tail was 7 Medium and 6 Low, so on that ticket this change alone would have saved one
round, not three. The weight therefore shifts onto changes 3, 4 and 5 below — scoped re-review,
approve-with-conditions and a hard round cap. If the loop persists after one ticket under these
rules, the Medium bar is the next thing to revisit.

---

## 5. The changes

| # | Change | File | Kills |
|---|---|---|---|
| 1 | Low findings leave as tickets; High and Medium are fixed | `develop`, `review`, `verify` | Mechanism 1, partly 4 |
| 2 | Gate 9 — every Low in the latest report names a ticket number | `check-done.mjs` | Mechanism 4 |
| 3 | Re-review reads only the diff since the previous review | `review`, `verify` | Mechanism 2 |
| 4 | Approve-with-conditions for code review | `review` | Mechanism 5 |
| 5 | Two rounds, then split the ticket and merge the passing half | `develop` | Mechanism 5 |
| 6 | Spec cap — one module, one migration, one testable behaviour | `plan-feature`, `review-spec` | Mechanism 3, ticket size |
| 7 | Halve the harness prose | all `SKILL.md` | Context cost |
| 8 | Wire `prune-outputs.mjs` to the Stop hook | `settings.json` | Report bloat |
| 9 | `verify-app` hook drops to frontend lint only | `settings.json` | Edit-time tax |
| 10 | Next wave is a vertical HCM slice | `active-work.md` | Build order |

### Change 1 — the finding bar

The `develop` disposition table becomes:

| Severity | What happens |
|---|---|
| High | Fixed on this branch. Blocks the merge |
| Medium | Fixed on this branch, **or** converted to a Condition under change 4. Blocks the merge |
| Low | **Never fixed on this branch.** A GitHub ticket is opened and its number recorded in the report |

The report's Status column gains one value: `TICKETED #nnn`.

### Change 2 — gate 9

New gate in `check-done.mjs`, alongside gate 3, reading the same latest-report-per-kind logic:

> Every finding in the latest review and verify reports is `FIXED`, `CLOSED`, `TICKETED #nnn`, or
> a named Condition. Anything still `OPEN` fails the gate, whatever its severity.

This is what makes "no issues left behind" machine-checked rather than aspirational. Today a Low
can be carried forever and nothing notices.

### Change 3 — scoped re-review

`review` and `verify` gain a step that `review-spec/SKILL.md:52` already has:

> Look in `.claude/outputs/` for a previous report on this ticket. If one exists, this is a
> re-review. The diff under review is `<sha of the previous review's HEAD>..HEAD`, not
> `main...HEAD`. Re-read the full branch only for the specific findings being re-checked.

Each report gains a `Reviewed at: <sha>` line so the next round can find it.

### Change 4 — approve-with-conditions

A Medium may be discharged as a **Condition** instead of a fix when all three hold:

- the fix is smaller than the round it would cost
- it does not change behaviour already covered by a passing test
- it is written into the report as `C-n` with the exact change required

Conditions are carried into the merge commit message and closed by the next ticket to touch the
file. This is the rev5 mechanism, promoted from ad hoc to written rule.

### Change 5 — two rounds, then split

Round 3 never starts. When round 2 ends with findings open:

1. Split the ticket at the module boundary where the findings cluster.
2. Merge the half that passes, under the normal gates.
3. Open a ticket for the other half, carrying the open findings as its acceptance criteria.

A stall becomes a decision. W-08 would have merged the filter and the migration, and opened a
second ticket for the DataSource proxy — which is where 9 of its 13 findings sit.

### Change 6 — the spec cap

`plan-feature` refuses to write, and `review-spec` blocks, a spec that crosses more than one of:

| Axis | Limit |
|---|---|
| Backend module | 1 |
| Flyway migration | 1 |
| Externally testable behaviour | 1 |
| Frontend area | 1 |

W-08 crosses all four. Under this rule it would have been written as four tickets and none of
them would have looped.

### Change 7 — halve the prose

`review-spec/SKILL.md` is 360 lines and opens by warning that volume hides the finding that
matters. 16,296 words of skills and agents load on every invocation. Target: no `SKILL.md` over
120 lines. The rationale paragraphs move to one `.claude/skills/README.md` that records why each
rule exists, read once by a human, not on every run.

### Changes 8 and 9 — the hooks

| | Now | Proposed |
|---|---|---|
| `prune-outputs.mjs` | Exists, wired to nothing | Stop hook; keeps the latest report per ticket per kind |
| `verify-app.mjs` | `mvn compile` up to 240s after every backend edit | Frontend lint only; the backend compiles in CI and at `/verify` |

---

## 6. Change 10 — the vertical slice

The leave engine `W-16` is not reachable: it sits behind `W-10`, `W-13`, `W-14` and `W-15`.
`docs/target-state/08-work-plan.md:67`

The earliest real HCM capability is the **employee master, `W-13`**, whose only dependency is
`W-08`. The work plan already names it one of the two items carrying most of the risk.
`docs/target-state/08-work-plan.md:64,210-211`

Cut thin under the change 6 cap, it becomes:

| Ticket | Scope | Depends on |
|---|---|---|
| `W-13a` | One employee: create, read, list. `core` module, one migration, one screen, tenant-scoped | `W-08` |
| `W-13b` | Personal details, contact, identification | `W-13a` |
| `W-13c` | Employment history, bank details | `W-13a` |
| `W-13d` | Search and listing filters | `W-13a` |

`W-13a` is the first thing in this repository a person could use. Port source is the employee
section of `legacy/docs/FEATURE_MAP.md`, cited `file:line` in the commit per the existing rule.

**Sequence:** land `W-08` first, under the new rules, as the test of whether they work. Then
`W-13a`.

---

## 7. What this does not change

| | |
|---|---|
| Founder approval before implementation | Unchanged, hard rule 1 |
| Checkers cannot edit | Unchanged — enforced by the verifier and reviewer toolsets |
| `legacy/`, `docs/`, `*.properties` guarded | Unchanged, `guard-edit` |
| CI green for the exact commit | Unchanged, gate 8 |
| `tenant_id` and RLS on every new table | Unchanged |
| Flyway, never `ddl-auto` | Unchanged, gate 6 |

No gate is removed. Gate 9 is added. The separation that caught the W-02 Keycloak 401 is intact.

---

## 8. Order of work, once approved

| Step | Change | Touches |
|---|---|---|
| 1 | Finding bar, scoped re-review, conditions, round cap | `develop`, `review`, `verify` |
| 2 | Gate 9 | `check-done.mjs` |
| 3 | Hooks | `settings.json` |
| 4 | Spec cap | `plan-feature`, `review-spec` |
| 5 | Prose halving | all `SKILL.md` |
| 6 | Re-run W-08 under the new rules | branch `W-08-tenant-binding-filter` |
| 7 | Write the `W-13a` spec | `/plan-feature` |

Steps 1 to 5 are harness-only and land on one branch, which gate 1 classifies as tooling work.
