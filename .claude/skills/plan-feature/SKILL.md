---
name: plan-feature
description: Turn a feature or fix request into a founder-reviewable implementation plan using docs/target-state/features/TEMPLATE.md. Stops before any code is written.
---

# plan-feature

Produces a plan; never produces code. Founder approval is the exit condition.

## Steps
1. Read `agents/active-work.md`. If the request touches something listed under **Frozen**
   (HRMS apps, Payroll `main` branches, LOP integration), say so and stop.
2. Read `docs/legacy/GAP_INVENTORY.md` and list every BUG/DEBT ID the request overlaps — the plan
   must either fix them, explicitly defer them, or explain why they are unaffected.
3. Spawn **explorer** to map the current behaviour: entry points (controller / route), service
   methods, repository queries, tables (real names from `docs/legacy/DB_SCHEMA.md`), screens. Output
   to `agents/outputs/<date>-plan-<slug>-evidence.md`.
4. Fill `docs/target-state/features/TEMPLATE.md` **into a new file in `agents/outputs/`** named
   `<date>-plan-<slug>.md` (not into `docs/` — the guard hook blocks that; the approved plan
   is copied to `docs/target-state/features/` later via `sync-docs`). Sections: Problem, Scope (in/out),
   Flow, Backend changes, Frontend changes, DB changes (Flyway script name, `tenant_id`
   checklist), Tests to add, Verification commands, Risks, Rollback.
5. Split the work into implementer tasks, **one app per task**, each with acceptance
   criteria the **verifier** can run as a command.
6. State the impact on the four hard constraints: no behaviour change to existing endpoints,
   no `ddl-auto` reliance, `organizationId`/`tenant_id` scoping, no edits to `docs/` or
   `*.properties`.
7. Reply with the plan path, a ≤10-line summary, the task list, and the open decisions the
   founder must make (as numbered questions).
8. **STOP and wait for "approved".** Do not spawn **implementer**. Do not edit any file
   outside `agents/outputs/`.
