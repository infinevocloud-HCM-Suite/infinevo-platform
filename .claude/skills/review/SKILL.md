---
name: review
description: Review a branch against its spec's acceptance criteria. Reports numbered findings and fixes nothing. Reads for what verify cannot run.
---

# review

Invoke as `/review W-nn`, on the ticket's branch. Usually called by `/develop` rather
than typed by hand.

Answers a different question from `/verify`. Verify asks *does it work* by running
things. **Review asks *is it right* by reading.**

Both are needed. `W-02` passed 23 of 23 while its Keycloak admin login returned 401 —
the test checked the realm endpoint, which is independent of the admin user existing.
Only reading the diff against the documentation found it.

**This skill fixes nothing.** The work runs through the **reviewer** agent, which has
no edit tools — so that is enforced by its toolset, not by good intentions. Findings go
to `/develop`.

---

## Steps

1. Confirm the branch and its ticket. `git rev-parse --abbrev-ref HEAD` names it.

   **Then work out whether this is a first review or a re-review**, because they read
   different diffs. Look in `.claude/outputs/` for the newest `*-review-W-nn*.md`.

   | | Diff under review |
   |---|---|
   | No previous report | `git diff main...HEAD` — everything the branch adds |
   | A previous report exists | `git diff <its Reviewed at sha>..HEAD` — only what changed since |

   On a re-review, read the full branch **only** for the specific findings being
   re-checked. Everything else was reviewed last round and has not moved.

   This is not a shortcut, it is the fix for a loop. Re-reading `main...HEAD` every round
   meant the surface never shrank, so each round found things the last one had not got to:
   W-08's round 3 produced F-19 to F-22, all new, on a branch that had already been
   reviewed twice. A review that grows new findings faster than the developer closes them
   never terminates.

   Open the report with `Reviewed at: <sha>` so the next round can find its base.
2. Read the ticket's spec under `docs/target-state/features/W-nn-*.md`. **Its §9
   verification table and §13 done-when list are the standard.** Not your taste —
   disagreements about approach belong at spec approval, not here.
3. Spawn **reviewer** with the branch diff, the spec path, and any `/verify`
   report already written for this ticket. The agent's own instructions carry the
   checklist — what to look for and in what order.
4. Read the returned report. **Spot-check at least two `path:line` citations** yourself
   with Read; if one is wrong, send the reviewer back with the correction.
5. Write the report to `.claude/outputs/<date>-review-W-nn.md`. The reviewer cannot
   write files — this step is yours.

---

## The report

Same finding format as `/verify`, so `/develop` and `check-done.mjs` can read both.

```
# Review — W-nn — <date>
Reviewed at: <sha>

## Verdict
APPROVE / APPROVE WITH CONDITIONS / CHANGES REQUIRED

## Spec acceptance
| # | Criterion | Met? | Evidence |

## Findings
| ID | Severity | Finding | Where (path:line) | Status |
| F-1 | High | <one sentence> | <file:line or command output> | OPEN |

## Conditions
| ID | Discharges | The exact change required | Closed by |
| C-1 | F-7 | <what must change, precisely enough to do without asking> | next ticket touching <file> |

## What is good
<Say it. A review that only lists faults teaches nothing about what to repeat.>
```

| Severity | Means |
|---|---|
| **High** | Wrong behaviour, a security hole, or documentation that does not work. **Blocks the merge** |
| **Medium** | A real defect with a workaround, a missing test, a trap for later |
| **Low** | Cosmetic, dead configuration |

Cite `path:line` for every finding. A finding without a location is an opinion.

---

## Approve with conditions

A **Medium** may be discharged as a Condition instead of a fix. All three must hold:

| | |
|---|---|
| 1 | The fix is smaller than the round it would cost |
| 2 | It does not change behaviour already covered by a passing test |
| 3 | It is written as `C-n` with the exact change required — not "improve this" |

A Condition is carried into the merge commit message and closed by the next ticket to
touch that file. **A High is never a Condition.**

This is not new. It is how W-08's spec review escaped a five-round loop: rev5 converted
three findings into C-1 to C-3 and approved in one pass
(`.claude/outputs/2026-09-20-review-spec-W-08-rev5.md:26-29`). That worked, nobody wrote
it down, and the code review then looped three more rounds without it.

The test of a Condition is whether you would still write it if you knew nobody would read
it again. If the answer is no, it is not a Condition — it is a Low, and it gets a ticket.

---

## Reviewing your own work

You will often be reviewing something you wrote. Running it through the **reviewer**
agent is the point: it reads the diff without the memory of having written it. Tell it
so — and **go looking for what you would have got wrong**: the thing you did not test,
the version you assumed, the documentation you wrote before the code changed.

Doing this on `W-02` found five defects in an hour, one of them a documented login that
had never worked. Self-review is weaker than a second pair of eyes; it is far stronger
than none.

## Finishing

Hand the report back to `/develop`, which fixes what is open and calls this skill again.
This skill never fixes and never merges.

- **CHANGES REQUIRED** or any High finding → `/develop W-nn` fixes them, then re-runs this
- **APPROVE WITH CONDITIONS** → `/merge W-nn`, conditions carried into the commit message
- **APPROVE** → `/merge W-nn`

**After round 2, this skill stops being the answer.** If round 2 ends with findings open,
`/develop` splits the ticket rather than calling for a round 3. See `develop`, "Two
rounds, then split".

**Say it in plain English too.** Whoever reads this is about to change code because of
it, so after the report, one line per finding in ordinary words: what breaks, and for
whom. "Leave balance shows a day too many when the year rolls over" beats "off-by-one in
the accrual boundary condition".
