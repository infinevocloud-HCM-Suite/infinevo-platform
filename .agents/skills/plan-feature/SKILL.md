---
name: plan-feature
description: Turn a product ticket into a founder-reviewable spec using docs/target-state/features/TEMPLATE.md. Stops before any code is written.
---

# plan-feature

Invoke as `/plan-feature W-nn <developer>`. Drafts a specification from `docs/target-state/features/TEMPLATE.md`.

**STOPS FOR FOUNDER APPROVAL BEFORE ANY CODE IS WRITTEN.**

---

## Workflow

1. Read `.agents/work/active-work.md`. Stop if the ticket is blocked or lacks clarity.
2. Read `docs/target-state/09-build-order.md` §3 and `legacy/docs/GAP_INVENTORY.md`.
3. Investigate legacy code behavior in `legacy/` (controllers, entities, queries).
4. Draft the specification directly in `docs/target-state/features/W-nn-<slug>.md`.
5. Cite `legacy/` `file:line` for all ported logic.
6. Verify against `review-spec` checklist (Flyway migrations reserved, RLS policies, tenant boundaries).
7. Update `docs/trackers/DEV-TRACKER.md` row to `Assigned` with `<developer>` as Owner.
8. Present the summary to the founder and **STOP for approval**.
