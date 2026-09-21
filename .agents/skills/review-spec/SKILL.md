---
name: review-spec
description: Independent checklist inspection of a drafted spec before the founder sees it.
---

# Review Spec Skill

Trigger: `/review-spec <spec-path>`

## Checks

1. Verify legacy `file:line` citations exist for all business logic.
2. Check complete acceptance criteria table.
3. Validate Flyway migration names and order (no `ddl-auto`).
4. Enforce mandatory tenant isolation (`tenant_id`).
5. Ensure no cross-module coupling between `hrms` and `payroll`.
