---
name: review-spec
description: Check a draft spec before the founder approves it — template complete, gaps covered, acceptance criteria runnable, citations resolve. Reports blockers (what would ship broken and stay silent) and a verdict, not every fault it can find. Never edits the spec.
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

## The one test every finding must pass

> **If this ships unfixed, what breaks — and would anyone notice?**

If nothing breaks, it is not a finding. If the first command the implementer runs reveals
it, it is not a finding — a wrong CLI flag costs three seconds to discover and a round
trip to report. **Report what would ship broken and stay silent.**

This test exists because the opposite instinct is the natural one. Asked to review, it is
easy to produce everything that can be pointed at — a typo, a bare filename, a paraphrase
that drifted — and each one looks like diligence. It is not. `W-50` took four passes and
about forty-nine findings, twelve of them wording, and the defect that mattered was in
none of them: `AcrPull` was declared but never exercised, so all four Container Apps ran
Microsoft's **public** starter image while the private registry was created empty and
never read from. It was found by the founder asking "what is really necessary", after
four clean gate reports.

**Volume is not thoroughness. It hides the finding that matters.**

---

## Steps

1. Locate the draft. `/plan-feature` and `/infra-task` write to
   `.claude/outputs/<date>-plan-<slug>.md` — **not** to `docs/`, which `guard-edit`
   blocks. If the path given is already under `docs/target-state/features/`, the spec is
   approved and live: say so and stop. Re-reading a live spec is `/analyze`'s job.
2. Read `.claude/work/active-work.md` and the ticket's GitHub issue. **If the ticket is
   labelled `blocked`, stop** — a spec written against a foundation that does not exist
   is rewritten when it does, so reviewing it now spends the founder's attention twice.
3. **Check whether a previous pass reviewed this spec** — look in `.claude/outputs/`. If
   one did, this is a **re-review: report blockers only.** Do not re-sweep a draft that
   has already been swept. Notes from the earlier pass stay closed whether or not they
   were acted on; the founder saw them once.
4. Run the four checks below, in order.
5. **Only if the draft cites `legacy/`**, spawn **explorer** with those citations and ask
   whether each file and line exists and what it holds. Evidence to
   `.claude/outputs/<date>-review-spec-<slug>-citations.md`, and spot-check at least two
   yourself — explorer runs on `haiku`, and a citation check that is itself wrong is worse
   than none. **A draft that cites no `legacy/` path gets no sweep**: the check exists to
   catch a port pointing at the wrong line, and greenfield work has nothing to port. Read
   its handful of design-doc citations yourself instead.
6. Write the report to `.claude/outputs/<date>-review-spec-W-nn.md`. Hand the founder the
   verdict and the blockers in chat — not the whole report.

---

## Check 1 — citations resolve

The most valuable check, because `/plan-feature` step 7 calls citing `legacy/`
`file:line` "the single most useful thing in the spec" and nothing else verifies it.

| Fault | Finding |
|---|---|
| File does not exist | **Blocker** — the port target is imaginary |
| Line does not exist (file is shorter) | **Blocker** |
| Line exists but holds unrelated code | **Blocker** — drifted citation, the worst kind: it looks right |
| Ported logic with no citation at all | **Blocker** — reviewer cannot tell a port from a reinvention |
| A table being **ported** whose source is not in `legacy/docs/DB_SCHEMA.md` | **Blocker** |
| A **new or target** table not listed in `docs/target-state/02-data-model.md` §2-§5 | **Blocker** — that is the authority for all 130 target tables and which schema each lives in |
| A table placed in a different schema from the one `02-data-model.md` §2-§5 gives it | **Blocker** — the schema is a decision already made (`D-09`, `02-data-model.md:14`), not one to settle in a migration |

**Check which of those two documents applies before grading.** `legacy/docs/DB_SCHEMA.md`
describes the **frozen MySQL** schema and its own header says it is "not a specification
for new work" — so a genuinely new target table (`subscription`, `lop_policy`,
`subscription_module`) will correctly be absent from it. Grading that as a fault is a
false positive, and the reverse — accepting a ported table with no legacy source — lets a
reinvention through as a port.

**Neither rule applies to an object that never reaches a real database**: a test-only
fixture created and destroyed inside a Testcontainer, or a tool's own bookkeeping table.
Those are correctly absent from both documents. Require instead that the spec says
plainly that they are test-only or infrastructural, and where they live — an object whose
lifetime is not stated is the thing to query, not one that is missing from a table list.

Typos in the frozen packages are real and load-bearing — `timeshhet/`,
`leaveAndAttedance/`, `EmployyePortalContoller.java`. A citation carrying one is
**correct**. Do not report it as a typo, and do not let explorer "fix" it.

## Check 2 — template complete

Against `docs/target-state/features/TEMPLATE.md` — or, for a ticket labelled
`skill-INFRA`, `skill-DATA` or `skill-SEC`, against `TEMPLATE-INFRA.md`, which has its
own section numbering and deliberately drops Flow, Frontend changes and the API
contract. **Check which template the ticket's label calls for before judging a section
missing.** A section left as its placeholder text is not filled in.

`TEMPLATE-INFRA.md:14-15` makes one section conditional: an infra ticket that creates
**any** database object must carry a Database changes section, with the `V__` script
names filled in. Missing it on a ticket that creates objects is a Note; a
`skill-DATA` ticket whose whole subject is the schema, a **Blocker**.

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

An empty §2 out-of-scope list is a **Blocker** every time. Scope that is not bounded on
paper gets bounded by whoever implements it.

## Check 3 — gaps and standing rules

`/plan-feature` step 4 requires every overlapping BUG/DEBT ID from
`legacy/docs/GAP_INVENTORY.md` to be fixed, explicitly deferred, or explained as
unaffected. Read the inventory yourself and find the IDs the draft **did not** mention —
a silent gap is the one that gets ported forward.

Then the standing rules from `CLAUDE.md`, each a **Blocker** where the draft contradicts
it and a Note where the draft is simply silent:

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
task spanning two is a **Blocker**: the `implementer` agent works inside one module and
will either stop or quietly exceed its brief.

Then read §8 as the **verifier** would, which has only Bash:

| Fault | Finding |
|---|---|
| Criterion needs a human to look at a screen | **Blocker** — verifier cannot run it |
| Criterion has no command | **Blocker** |
| Criterion is "works correctly" | **Blocker** — unfalsifiable |
| Command has no expected output to compare against | **Blocker** if it can therefore never fail; otherwise a Note |

`W-02` passed 23 of 23 while its Keycloak admin login returned 401: the test checked the
realm endpoint, which is independent of the admin user existing. **Ask of every
criterion what could pass while the feature is broken.** That question belongs here, at
the spec, where it costs a sentence — not after implementation, where it cost an hour
and five defects.

### Declared, or exercised?

The sharper form of the same question, and the one easiest to miss:

> For each "done when" item — does a command **attempt** the thing, or does the spec
> merely **assert** it?

"`AcrPull` is assigned to the managed identity" is an assertion. "An app pulled an image
from the private registry" is an attempt. `W-50` cleared four gate passes with the first
and none of the second: every Container App ran `mcr.microsoft.com/k8se/quickstart` — a
**public** registry — so the apps reaching `Running` proved nothing about the private one,
which was created empty and never read from. It would first have been exercised at `W-54`,
three tickets later.

The pattern generalises. A role granted but never used. A secret stored but never read
back. A constraint declared but never violated on purpose. A queue created but never sent
to. **Where §1 names a problem, find the command that proves that problem is solved.** If
there is none, that is the blocker, and it outranks everything else in the report.

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

## Blockers
| ID | Blocker | Where (draft §) | What closes it |
| F-1 | <one sentence> | §4, line 61 | <the command or change that answers it> |

## Notes
<Ungraded, one line each, no round trip expected. Fix if already in the file.>

## What is good
<Say it. A spec review that only lists faults teaches nothing about what to repeat.>
```

**Two severities, not three.**

| | Means |
|---|---|
| **Blocker** | Would ship broken **and stay silent**. Wrong infrastructure, wrong data, a credential nobody notices, or a criterion that cannot fail. **Blocks approval** |
| **Note** | Everything else. One line, ungraded, no round trip, no reply expected |

**Nothing the first run reveals is ever a blocker.** A wrong CLI flag, two conflicting
arguments, a command that does not exist — `az` or `mvn` says so in seconds, for free, and
reporting it costs a round trip to save nothing. A blocker is something that *passes*, and
goes on passing, while the thing is broken.

**Do not grade wording.** A paraphrase that drifted, a citation missing its folder, a
header saying "full" where §6 says "partial" — none change what gets built. One Note at
most, usually nothing. `W-50` spent twelve findings across four passes on this class and
not one altered the work.

Cite the draft's **section and line** for every blocker. But note the pull that creates:
locatable findings are easy to manufacture, and a report grows to fill the space it is
given. **A short report of real blockers is the goal; a long one is the failure mode.**

## Re-reviewing — grade the question, never the patch

When a previous pass raised something, the temptation is to check whether the author did
what was suggested and close it if they did. **That is the wrong question.** Go back to
what the finding was *worried about*, and ask whether anything now answers it.

`W-50`'s second pass raised "no `AcrPull` — no container app can pull its image". The
third pass closed it because `AcrPull` had been moved into scope and a role assignment
named. But the worry was *can an app pull from the registry*, and nothing pulled from the
registry; the apps ran a public image. **The patch landed. The question was still open.**

So: re-state the original worry in your own words before deciding. If you cannot name the
command that now answers it, it stays open — whatever the author changed.

## What this skill does not judge

**Approach.** If the spec puts a table in the wrong schema, that is a Check-3 finding
only where it contradicts `docs/target-state/02-data-model.md` — which schema a table
lives in is a design decision already made, not one to re-litigate. Whether the founder
*wants* the feature this way is the founder's call, and this skill exists to hand them a
sound draft to make it on, not to make it for them.

**Questions addressed to the founder.** The template's closing section exists for the
author to ask and the founder to answer. A question sitting there is the template working,
not failing — do not convert it into a defect. The one thing worth saying is where a
question re-opens something already decided: `W-50` asked "Bicep or Terraform?" when
`03-code-structure.md:42` had settled it and the whole spec was written in Bicep. That is
a Note naming the citation, not a graded finding.

**Anything reversible in one line.** A development database SKU, a cache tier, a naming
convention. If it is a parameter change later, it is not a finding now.

**Anything that is not the spec file.** Not the author's working notes, not their branch
hygiene, not their commit messages, not a stray file in `.claude/outputs/`. Those belong
in a sentence to the developer, never as a graded finding in a gate report. The scope of
this skill is one document.

**The author.** Report the defect and what closes it. No commentary on how it came to be
there, no comparison to past mistakes, no remarks about who reviewed what. A gate report
is read by someone about to do a day's work — give them the defect, not a verdict on their
judgement.

## Finishing

- **NOT READY** or any blocker → `/plan-feature W-nn` (or `/infra-task W-nn`) with
  the report path. Re-run `/review-spec` on the revision.
- **READY TO APPROVE** → hand the founder the verdict. They approve; the spec moves to
  `docs/target-state/features/W-nn-<slug>.md`, `Status` becomes `Approved` and
  `Approved by` / `Approved on` are filled in. **Only then** may `/develop W-nn` start.

Approval stays the founder's, always. This skill only makes sure that when they give it,
they are giving it to something that holds together.
