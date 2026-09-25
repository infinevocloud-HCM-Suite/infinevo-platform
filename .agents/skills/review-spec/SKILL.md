---
name: review-spec
description: Independent checklist inspection of a drafted spec before the founder sees it.
---

# review-spec

Invoke as `/review-spec <spec-path>`. Pre-review gatekeeper validating that a drafted spec complies with all platform architectural contracts.

---

## Checks

1. **Legacy Citations:** Every claim about existing behavior cites `legacy/path:line`.
2. **Acceptance Criteria:** Includes clear verification checks and done-when conditions.
3. **Migration Numbering:** Uses reserved migration blocks from `docs/trackers/DEV-TRACKER.md`, not collided numbers.
4. **Tenant Isolation:** Every new table has `tenant_id` and an RLS policy (unless in `reference` schema).
5. **No `ddl-auto`:** Confirms Flyway ownership.
6. **No Floating-point Money:** Verifies all currency/deduction/leave fields are typed `BigDecimal`.
7. **Module Boundaries:** Verifies `hrms` and `payroll` never reference each other.
