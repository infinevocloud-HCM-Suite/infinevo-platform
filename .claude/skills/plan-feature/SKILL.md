---
name: plan-feature
description: Write a ticket's spec straight into docs/target-state/features/ and assign it to a developer in the tracker. Covers feature and platform tickets (Azure, Docker, CI, Flyway, tenancy, security). Never writes code.
---

# plan-feature

Invoke as `/plan-feature W-nn [developer]`. Produces a spec; **never produces code.**

Handles both kinds of ticket. A platform ticket (`skill-INFRA`, `skill-DATA`,
`skill-SEC`) uses `TEMPLATE-INFRA.md`; everything else uses `TEMPLATE.md`. The steps are
the same.

**The founder writes every spec, and a written spec is ready to build.** There is no
approval stop and no review round. The founder assigns it to a developer in the tracker.

---

## The size cap — check this before writing a word

**A spec crosses at most one of each axis. If it crosses two on any line, it is two
tickets. Split it and say so before writing.**

| Axis | Limit |
|---|---|
| Backend module (`core`, `hrms`, `payroll`, `shared`) | 1 |
| Flyway migration | 1 |
| Externally testable behaviour | 1 |
| Frontend area | 1 |

W-08 crossed all four and took five spec reviews and three code rounds, merging with 13
findings open. Splitting costs one ticket. Not splitting costs weeks.

## Steps

0. **Apply the size cap.** If the ticket breaks it, propose the split and stop.
1. Read `.claude/work/active-work.md`, then the ticket's row in
   `docs/trackers/`. **If it is Blocked, say which ticket it waits on and stop.**
2. Read `docs/target-state/09-build-order.md` §3 for this item — what to build, how you
   know it is done, the trap to avoid. That is the spine of the spec.
3. Read `docs/target-state/01-platform-shape.md` for the capability, and `02-data-model.md`
   for the tables. **Which schema a table lives in is already decided — look it up in
   §2-§5, do not re-decide it.** `legacy/docs/DB_SCHEMA.md` is only for a table being
   *ported*, to get its source columns right.
4. Read `legacy/docs/GAP_INVENTORY.md` and list every BUG/DEBT ID the work overlaps. Fix,
   defer or discount each, explicitly.
5. Spawn **explorer** if the current state is unclear.
6. Write the spec straight into `docs/target-state/features/W-nn-<slug>.md` (for a part,
   `W-nn-n-<slug>.md`), set `**Status**: Ready`. **Do not commit** — leave it in the working tree; the founder commits and pushes so it stays visible in their Source Control list.
7. **Cite `file:line` for every claim about current state, and `legacy/file:line` for
   every piece of logic being ported.** This is the single most useful thing in the spec.
8. Split the work into implementer tasks, one area per task — `core`, `hrms`, `payroll`,
   `shared`, `infra/`, `.github/workflows/`.
9. State the impact on the standing rules, every time, even to say "creates no table":
   - `tenant_id` and an RLS policy on every new table outside `reference` — including
     history, lock and audit tables
   - Flyway script for every schema change, under `code/backend/migration/`. Never
     `ddl-auto`. **Read `code/backend/migration/README.md` first** — every statement
     names its schema, or the DDL silently lands in the wrong one
   - `Money` or `BigDecimal` with explicit precision. Never floating point
   - Index on `tenant_id` plus lookup columns (`DEBT-018`)
   - Expand / contract — no destructive step
   - No module references another module. If it seems to need one, the data belongs in
     `core` — say so
10. **Tracker:** set the ticket's row to `Assigned` with the developer as Owner, or
    `Ready` with no owner if none was named. Leave it uncommitted with the spec.
11. Reply with the summary below, and stop.

**The verification section is the part that matters.** Exact commands and their expected
output, not a description of testing. For a Bicep ticket that means `az bicep build` and
`az bicep lint` — not a deployment, which is the founder's step.

---

## What the founder reads

Plain English, no jargon.

```
W-nn — <title>: spec ready

What gets built: <two or three sentences a non-engineer would follow>
What could go wrong: <the honest risk, one sentence. Or "nothing unusual">

| File or area | New or changed | Why |
|---|---|---|
| code/backend/hrms/leave | new service | Works out remaining leave |

Spec: docs/target-state/features/W-nn-<slug>.md
Assigned to: <developer, or "nobody yet">

Open questions: <or "none">
Next: the developer runs /develop W-nn
```

Half a page for an `S` item, two pages for an `L`. If it runs longer, the ticket is too
big — propose a split rather than padding it.
