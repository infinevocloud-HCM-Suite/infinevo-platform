---
name: plan-feature
description: Turn a product ticket into a founder-reviewable spec using docs/target-state/features/TEMPLATE.md. Stops before code is written.
---

# Plan Feature Skill

Trigger: `/plan-feature W-nn`

## Workflow

1. Read `.agents/work/active-work.md` and the ticket issue. Stop if ticket is blocked.
2. Read `docs/target-state/09-build-order.md` §3 and `legacy/docs/GAP_INVENTORY.md`.
3. Investigate legacy implementation: trace controllers, services, queries, and schema tables from `legacy/docs/DB_SCHEMA.md`.
4. Draft spec in `.agents/outputs/<date>-plan-<slug>.md`.
5. Cite `legacy/` `file:line` for all ported business logic.
6. Run `/review-spec <draft-path>` to validate spec completeness.
7. Present summary to founder highlighting required architectural decisions.
8. **STOP and wait for founder approval before writing code.**
