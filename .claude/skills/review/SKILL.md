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

1. Confirm the branch and its ticket. The diff under review is
   `git diff main...HEAD` — everything this branch adds on top of main.
   `git rev-parse --abbrev-ref HEAD` names the ticket.
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

## Verdict
APPROVE / APPROVE WITH FINDINGS / CHANGES REQUIRED

## Spec acceptance
| # | Criterion | Met? | Evidence |

## Findings
| ID | Severity | Finding | Where (path:line) | Status |
| F-1 | High | <one sentence> | <file:line or command output> | OPEN |

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
- **APPROVE** → `/merge W-nn`

**Say it in plain English too.** Whoever reads this is about to change code because of
it, so after the report, one line per finding in ordinary words: what breaks, and for
whom. "Leave balance shows a day too many when the year rolls over" beats "off-by-one in
the accrual boundary condition".
