# Spec review — W-49 — containerisation — 2026-09-15 (fifth pass)

Draft: `.claude/outputs/2026-09-15-W-49-containerisation-revised.md`, produced by
`/infra-task W-49` from branch spec `263e36c` with review findings F-1, F-4, F-5, F-6, F-7
applied. **Disclosure: the same session wrote this draft and this review.** The checks
below are mechanical and the §5 run is real, but the founder should treat "what is good"
with that in mind.

Issue #69: `ready`, one comment (bot). No PR. Branch images unchanged since `36d4503`.

## Verdict
**APPROVE WITH CONDITIONS**

## Checks
| # | Check | Result | Notes |
|---|---|---|---|
| 1 | Citations resolve | 14 of 14 | fabricated #69 quotations removed; two re-spot-checked by hand |
| 2 | Template complete | 9 of 9 | result table blank, correctly; `Owner` field empty (Low) |
| 3 | Gaps and standing rules | pass | DEBT-004/020/021 disposed; no schema, no money, no `ddl-auto` |
| 4 | Work is buildable | pass | §5 script run verbatim on the branch: 13 of 13 PASS, exit 0, including step 8 which failed last pass |

## Stale-text sweep
No `25.0.0`, no 48/620 MB, no old thresholds, no "Accepted/Confirmed by Founder", no
local profile, no owner login for the backend. One hit: §5 step 7 starts Keycloak as
`postgres` in the throwaway database (F-8, Low).

## Findings
| ID | Severity | Finding | Where | Status |
|---|---|---|---|---|
| C-1 | Condition | Decisions 1–4 carry "awaiting founder confirmation on issue #69". The founder confirms there in their own words; the developer then replaces each placeholder with the comment link | §3 line 145; "Decisions requiring founder confirmation" | OPEN — founder |
| C-2 | Condition | The draft lives in `.claude/outputs/`. The developer copies it over `docs/target-state/features/W-49-containerisation.md` on the branch, runs §5 once more, pastes the verbatim output into the result table, and pushes | whole file | OPEN — developer |
| F-8 | Low | §5 step 7 connects Keycloak as `postgres`. Acceptable in a throwaway container and identical to today's compose; W-05 Q3 introduces `keycloak_user`, after which this step should use it | §5 step 7, line 341 | OPEN |
| F-9 | Low | `Owner` field empty; assignee is KarmaveerM | header line 10 | OPEN |
| F-10 | Low | Step 8's line continuation was flattened to spaces; runs correctly, reads badly | §5 step 8 | OPEN |

## What is good
The §5 script is a complete, self-contained gate that a verifier can run with Bash alone,
and it has now been proven both ways: it failed on a real defect (F-6) and passes on the
fix. The decisions section says what it does not yet have instead of inventing it.

## Finishing
Founder: confirm decisions 1–4 on #69, then approve. Developer: C-2, then `/verify W-49`
and the pull request with `Closes #69`.
