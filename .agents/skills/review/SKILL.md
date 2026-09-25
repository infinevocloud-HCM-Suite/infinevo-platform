---
name: review
description: Independent code review of a ticket branch against its spec acceptance criteria. Read-only; reports numbered findings (F-1, F-2).
---

# review

Invoke as `/review W-nn`. Executes a read-only code review of a feature branch against the ticket specification.

**READ-ONLY: NEVER FIXES CODE OR EDITS FILES.**

---

## Rules & Workflow

1. Read the full diff: `git diff origin/main...HEAD`.
2. Compare implemented code against acceptance criteria in `docs/target-state/features/W-nn-*.md`.
3. Check standing platform rules:
   - `tenant_id` on all queries and non-reference entities.
   - Row-level security active.
   - Zero `ddl-auto`.
   - `BigDecimal` for money and deduction values.
   - Architecture boundaries (`hrms` and `payroll` never depend on each other).
4. Output structured report to `.agents/outputs/<date>-review-W-nn.md`:
   - Numbered findings: `F-1`, `F-2`, etc.
   - Severities: **High** (blocks merge), **Medium** (requires fix or explicit deferral), **Low** (polish).
   - Every finding must cite exact `file:line`.
