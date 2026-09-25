# Spec review — W-06 — Flyway — revision 5 — 2026-09-17

Draft: `.claude/outputs/2026-09-17-plan-W-06-flyway-rev5.md`
Prior: `-v4.md` (6 High) · `-rev2.md` (3 High) · `-rev3.md` (1 High) · `-rev4.md` (2 High)
`main` at `32829f5`. Ticket #7 is `ready`.

## Verdict

**APPROVE WITH CONDITIONS** — no High. One Medium, one Low, both one-line fixes.

Both rev4 High findings are gone, and gone the right way: the paragraph that carried them
was removed rather than patched a third time.

## Checks

| # | Check | Result | Notes |
|---|---|---|---|
| 1 | Citations resolve | **all** | Delta from rev4 is two citations; 56 covered by the rev3 and rev4 sweeps. `03-code-structure.md:144` and `03-grants.sql:39-56` verified by hand |
| 2 | Template complete | **All sections** | `TEMPLATE-INFRA` plus Standing rules, Database changes, §5a, Implementer tasks, Decisions taken |
| 3 | Gaps and standing rules | **Complete** | 7 IDs dispositioned. The removed guard is a **deferral with a named owner** (`W-07`), not a silent drop — §2 out-of-scope, §10, §13 R7 |
| 4 | Work is buildable | **Yes** | Four tasks, one area each. No placeholder in any command; `-q` gone from check 11 |

**Worth recording:** `flyway_schema_history` now appears in `docs/target-state/02-data-model.md`
§5, added by the `sync-docs` change approved earlier today — so it satisfies Check 1's new
rule that a target object must be listed there. The four fixtures are covered by the
test-only carve-out, and §4 and §5 both state their lifetime explicitly, which is what
that carve-out requires.

## Findings

| ID | Severity | Finding | Where | Status |
|---|---|---|---|---|
| K-1 | Medium | **The control W-06 now ships has no mechanical check that it shipped.** §13 R7 makes `migration/README.md` the replacement for the database guard, and §12 item 15 marks it **R** — a human reads it. But its key line is greppable: `grep -q 'Read the first word after' code/backend/migration/README.md`. One command turns the one deliberately-unenforced control into a checked one. Without it, a README missing that section passes every gate | §12 item 15; §9 | OPEN |
| K-2 | Low | §9 check 9's comment reads "proved behaviourally by **break 6**". After break 5 was removed and the table renumbered, the ordering proof is **break 5**. Line 380 | §9 check 9 | OPEN |

Neither blocks approval. K-1 is worth doing because it costs one line and closes the only
gap R7 opens.

## What is good

- **The fix was a deletion.** Two revisions tried to build a database-level guard; rev5
  removes it, names the residual risk in §10 with three partial controls and an owner,
  and records why in §13 R7. A spec that says "we tried this twice, it does not work,
  here is what we do instead" is more useful to `W-07` than one that quietly succeeds on
  the third attempt.
- **§10's risk row states exposure rather than claiming mitigation** — "Medium, and
  **accepted for now**", with the window named as the interval to `W-07`, during which
  `W-07` and `W-09` write the only scripts. That is the honest version of a risk row.
- **§5a is written as the text that ships**, not a description of it, ending with an
  instruction a reviewer can execute: read the first word after `CREATE`, look for a dot.
- **The revision banner corrects itself** — an earlier draft of it claimed rev5 was
  smaller than rev4; it is 2.5KB longer, and the banner now says the *work* shrank while
  the document grew.

## Route

Fix K-2 (one word) and, if you want it, K-1 (one grep in §9 and item 15 becomes **V**).
Then this is approvable. It does not need another `/review-spec` pass for two one-line
changes — re-running the gate on that would cost more than the changes.

**A note on the sequence, for the founder.** Five revisions, five reviews, 15 High
findings. Rounds one and two found real design gaps that would have survived into the
code. Rounds three and four found problems the previous round introduced. That is the
signal that the paper phase has stopped paying — which is what R7 acted on. The remaining
uncertainty in this ticket is answered by running Flyway once, not by reviewing it again.
