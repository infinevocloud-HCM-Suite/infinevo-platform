---
name: review-spec
description: Check a draft spec before the founder approves it — citations resolve, template complete, gaps covered, acceptance criteria runnable. Reports numbered findings and a verdict. Never edits the spec.
---

# review-spec

Invoke as `/review-spec W-nn` or `/review-spec <path-to-draft>`.

**The approver's gate.** Every other gate sits after the code exists: `/verify` asks
*does it work*, `/review` asks *is it right*, `/merge` asks *may it land*. This one asks
the question that comes before all of them — **is this spec fit to approve?**

It matters because approval is binding. Once the founder approves, `/develop` refuses to
start without a spec and then builds exactly what the spec says. A wrong `legacy/` line
number, a missing `tenant_id` row, an acceptance criterion no **verifier** can run —
each becomes a defect that survives implementation, review and merge, because every
later gate measures the code *against the spec*, never the spec itself.

**This skill fixes nothing.** It does not edit the draft, does not fill in a blank
section, does not correct a citation. Findings go back to `/plan-feature W-nn` or
`/infra-task W-nn`, whichever wrote it.

---

## Steps

1. Locate the draft. `/plan-feature` and `/infra-task` write to
   `.claude/outputs/<date>-plan-<slug>.md` — **not** to `docs/`, which `guard-edit`
   blocks. If the path given is already under `docs/target-state/features/`, the spec is
   approved and live: say so and stop. Re-reading a live spec is `/analyze`'s job.
2. Read `.claude/work/active-work.md` and the ticket's GitHub issue. **If the ticket is
   labelled `blocked`, stop** — a spec written against a foundation that does not exist
   is rewritten when it does, so reviewing it now spends the founder's attention twice.
3. Run the four checks below, in order. They are ordered by what costs most to discover
   late: a bad citation costs a day of implementation, a missing section costs a round
   trip.
4. Spawn **explorer** with the full list of `file:line` citations lifted from the draft.
   Ask it to report, for each one, whether the file exists, whether the line exists, and
   what that line actually contains. Evidence to
   `.claude/outputs/<date>-review-spec-<slug>-citations.md`.
5. **Spot-check at least two citations yourself** against explorer's report. It runs on
   `haiku`; a citation check that is itself wrong is worse than none.
6. Write the report to `.claude/outputs/<date>-review-spec-W-nn.md`. Hand the founder
   the verdict and the High findings in chat — not the whole report.

---

## Check 1 — citations resolve

The most valuable check, because `/plan-feature` step 7 calls citing `legacy/`
`file:line` "the single most useful thing in the spec" and nothing else verifies it.

| Fault | Finding |
|---|---|
| File does not exist | **High** — the port target is imaginary |
| Line does not exist (file is shorter) | **High** |
| Line exists but holds unrelated code | **High** — drifted citation, the worst kind: it looks right |
| Ported logic with no citation at all | **High** — reviewer cannot tell a port from a reinvention |
| Table named that is not in `legacy/docs/DB_SCHEMA.md` | **High** |

Typos in the frozen packages are real and load-bearing — `timeshhet/`,
`leaveAndAttedance/`, `EmployyePortalContoller.java`. A citation carrying one is
**correct**. Do not report it as a typo, and do not let explorer "fix" it.

## Check 2 — template complete

Against `docs/target-state/features/TEMPLATE.md`. A section left as its placeholder text
is not filled in.

| Must carry | Section |
|---|---|
| A problem with evidence, not a solution | §1 |
| Explicit **out of scope** list | §2 |
| Every layer named that the change touches | §4 |
| Flyway migration named, `V__` filled in | §6 |
| Unit **and** integration rows | §7 |
| Runnable commands with expected output | §8 |
| At least one real risk | §9 |
| A rollback that is more than "revert" | §10 |

An empty §2 out-of-scope list is a **Medium** every time. Scope that is not bounded on
paper gets bounded by whoever implements it.

## Check 3 — gaps and standing rules

`/plan-feature` step 4 requires every overlapping BUG/DEBT ID from
`legacy/docs/GAP_INVENTORY.md` to be fixed, explicitly deferred, or explained as
unaffected. Read the inventory yourself and find the IDs the draft **did not** mention —
a silent gap is the one that gets ported forward.

Then the standing rules from `CLAUDE.md`, each a **High** where the draft contradicts it
and a **Medium** where the draft is simply silent:

- `tenant_id` on every new entity, table and query outside `reference`, lookup tables
  included — plus a row-level security policy
- Flyway script for every schema change. **Never `ddl-auto`** — both frozen backends run
  `ddl-auto=update`; that is a known defect, not a pattern to copy
- `Money` or `BigDecimal` with explicit precision and scale for money. Never a
  floating-point type
- Index on `tenant_id` plus lookup columns (DEBT-018)
- Expand / contract sequencing — no destructive migration step

## Check 4 — the work is buildable

Each implementer task must name **one module** — `core`, `hrms`, `payroll`, `shared`. A
task spanning two is a **High**: the `implementer` agent works inside one module and
will either stop or quietly exceed its brief.

Then read §8 as the **verifier** would, which has only Bash:

| Fault | Finding |
|---|---|
| Criterion needs a human to look at a screen | **High** — verifier cannot run it |
| Criterion has no command | **High** |
| Command has no expected output to compare against | **Medium** |
| Criterion is "works correctly" | **High** — unfalsifiable |

`W-02` passed 23 of 23 while its Keycloak admin login returned 401: the test checked the
realm endpoint, which is independent of the admin user existing. **Ask of every
criterion what could pass while the feature is broken.** That question belongs here, at
the spec, where it costs a sentence — not after implementation, where it cost an hour
and five defects.

---

## The report

Same finding format as `/verify` and `/review`, so `/develop` and `check-done.mjs` read
all three the same way.

```
# Spec review — W-nn — <slug> — <date>

## Verdict
READY TO APPROVE / APPROVE WITH CONDITIONS / NOT READY

## Checks
| # | Check | Result | Notes |
| 1 | Citations resolve | 14 of 16 | 2 High |
| 2 | Template complete | 8 of 10 sections | |
| 3 | Gaps and standing rules | | |
| 4 | Work is buildable | | |

## Citations
| Cited | Exists? | Line holds | Verdict |
| legacy/.../X.java:212 | yes | `public void save(...)` | OK |

## Findings
| ID | Severity | Finding | Where (draft §) | Status |
| F-1 | High | <one sentence> | §4, line 61 | OPEN |

## What is good
<Say it. A spec review that only lists faults teaches nothing about what to repeat.>
```

| Severity | Means |
|---|---|
| **High** | Would produce wrong code, or cannot be implemented as written. **Blocks approval** |
| **Medium** | Will cost a round trip during implementation. Approve with it as a condition |
| **Low** | Wording, a missing cross-reference |

Cite the draft's **section and line** for every finding. A finding the author cannot
locate is an opinion.

## What this skill does not judge

**Approach.** If the spec puts a table in the wrong schema, that is a Check-3 finding
only where it contradicts `docs/target-state/02-data-model.md` — which schema a table
lives in is a design decision already made, not one to re-litigate. Whether the founder
*wants* the feature this way is the founder's call, and this skill exists to hand them a
sound draft to make it on, not to make it for them.

## Finishing

- **NOT READY** or any High finding → `/plan-feature W-nn` (or `/infra-task W-nn`) with
  the report path. Re-run `/review-spec` on the revision.
- **READY TO APPROVE** → hand the founder the verdict. They approve; the spec moves to
  `docs/target-state/features/W-nn-<slug>.md`, `Status` becomes `Approved` and
  `Approved by` / `Approved on` are filled in. **Only then** may `/develop W-nn` start.

Approval stays the founder's, always. This skill only makes sure that when they give it,
they are giving it to something that holds together.
