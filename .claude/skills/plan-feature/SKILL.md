---
name: plan-feature
description: Turn a product ticket into a founder-reviewable spec using docs/target-state/features/TEMPLATE.md. Stops before any code is written.
---

# plan-feature

Produces a spec; **never produces code.** Founder approval is the exit condition.

Invoke as `/plan-feature W-nn`. For platform tickets (`skill-INFRA`, `skill-DATA`,
`skill-SEC`) use `/infra-task` instead.

## The size cap — check this before writing a word

**A spec crosses at most one of each axis. If it crosses two on any line, it is two
tickets. Split it and say so before writing.**

| Axis | Limit |
|---|---|
| Backend module (`core`, `hrms`, `payroll`, `shared`) | 1 |
| Flyway migration | 1 |
| Externally testable behaviour | 1 |
| Frontend area | 1 |

This is the cheapest correction available. W-08 crossed all four — a servlet filter, a
DataSource proxy, a migration, an RLS policy, a `SECURITY DEFINER` function, a Spring
Security chain and 34 integration tests in one ticket. It took five spec reviews and
three code review rounds and still merged with 13 findings open. A 13-finding tail is
the arithmetic of that scope, not a failure of review.

Splitting costs one ticket each time. Not splitting costs rounds, and rounds are where
the weeks went.

## Steps

0. **Apply the size cap above.** If the ticket as written breaks it, propose the split to
   the founder and stop. Do not write a spec you already know is too big.
1. Read `.claude/work/active-work.md` for where the project stands, then the ticket's
   GitHub issue for its features and blockers. **If the ticket is labelled `blocked`, say
   which ticket it waits on and stop.** A spec written against a foundation that does not
   exist is rewritten when it does.
2. Read `docs/target-state/09-build-order.md` §3 for this item — what to build, how you
   know it is done, and the trap to avoid. That is the spine of the spec.
3. Read `docs/target-state/01-platform-shape.md` for the capability this delivers, and
   `02-data-model.md` for the tables and schema it belongs in. **Which schema a table
   lives in is a design decision already made — do not re-decide it.**
4. Read `legacy/docs/GAP_INVENTORY.md` and list every BUG/DEBT ID the work overlaps. The
   spec must fix them, explicitly defer them, or explain why they are unaffected.
5. Spawn **explorer** to map how it works in the frozen system: entry points, service
   methods, repository queries, real table names from `legacy/docs/DB_SCHEMA.md`, screens.
   Evidence to `.claude/outputs/<date>-plan-<slug>-evidence.md`.
6. Write the spec from `docs/target-state/features/TEMPLATE.md` into
   `.claude/outputs/<date>-plan-<slug>.md` — **not** into `docs/`, which `guard-edit`
   blocks. It moves to `docs/target-state/features/W-nn-<slug>.md` once approved.
7. **Cite `legacy/` `file:line` for every piece of logic being ported**, so the reviewer
   can check it was carried over rather than reinvented. This is the single most useful
   thing in the spec.
8. Split the work into implementer tasks, **one module per task** — `core`, `hrms`,
   `payroll`, `shared` — each with acceptance criteria the **verifier** can run.
9. State the impact on the standing rules:
   - `tenant_id` and a row-level security policy on every new table outside `reference`
   - Flyway script for every schema change. **Never `ddl-auto`**
   - `Money` or `BigDecimal` for money. Never a floating-point type
   - **No module references another module.** If the design seems to need it, the data
     belongs in `core` — say so rather than proposing a workaround; the build rejects it
   - Every new endpoint authenticated, or added to the reviewed exception list
   - Nothing under `legacy/` or `docs/` is edited
10. **Run `/review-spec <draft path>` and fix what it finds, before the founder sees the
    draft.** It spawns its own reader and applies a different checklist — citations that
    resolve, template complete, gaps covered, criteria the **verifier** can actually run
    — so it finds what re-reading your own draft cannot. On `W-06` it returned 6 High on
    the first draft and 3 on the second, none cosmetic. Skip it only for a genuinely
    small ticket — one module, one or two files, no new table — and **say in step 11
    whether you ran it and what it found.** `/review-spec` never edits the spec; it
    hands back findings for this skill to fix. Running it is not approval and does not
    substitute for it.
11. Reply with the summary below.
12. **STOP and wait for approval.** Do not spawn **implementer**. Do not write any file
    outside `.claude/outputs/`.

---

## What the founder reads

Plain English, no jargon. The founder is deciding whether to approve, so say what will
be built and what could go wrong — not how it is structured.

```
W-nn — <title>: spec ready

What gets built: <two or three sentences a non-engineer would follow>
What could go wrong: <the honest risk, one sentence. Or "nothing unusual">

| File or area | New or changed | Why |
|---|---|---|
| code/backend/hrms/leave | new service | Works out remaining leave |
| code/frontend/src/hrms | changed form | Shows the balance before you submit |

/review-spec: ran, <n> blockers, all fixed   (or: skipped - small ticket, one file)
Spec: docs/target-state/features/W-nn-<slug>.md

Decisions I need from you:
1. <question>
2. <question>

Approve and I start.
```

## What a good spec looks like

Half a page for an `S` item; two pages for an `L`. If approval takes more than a few
minutes, the spec is too vague or the ticket is too big — say so and propose a split
rather than padding it.

**Section 9, verification, is the part that matters.** It is what makes the ticket
checkable rather than a matter of opinion. Write the commands and their expected output,
not a description of testing.
