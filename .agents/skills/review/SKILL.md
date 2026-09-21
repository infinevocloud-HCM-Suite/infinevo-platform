---
name: review
description: Independent code review of a ticket branch against its spec acceptance criteria. Read-only; reports numbered findings (F-1, F-2).
---

# Review Skill

Trigger: `/review W-nn`

## Rules

1. Read-only review: never modifies code.
2. Inspect entire git diff: `git diff main...HEAD`.
3. Verify documentation and spec alignment.
4. Verify standing rules (`tenant_id`, Flyway, `BigDecimal`, module boundaries).
5. Categorize findings into High, Medium, Low. Write report to `.agents/outputs/<date>-review-W-nn.md`.
