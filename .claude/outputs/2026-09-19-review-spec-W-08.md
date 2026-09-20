# Spec review — W-08 — Tenant binding filter — 2026-09-19

## Verdict
**READY TO APPROVE**

## Checks
| # | Check | Result | Notes |
|---|---|---|---|
| 1 | Citations resolve | 8 of 8 | `D-08`, `D-09`, `D-56`, `D-57`, `BUG-002`, `02-data-model.md:55` (`core.user_tenant`), `V001__tenant.sql`, and `TenantContext.java` resolve |
| 2 | Template complete | 10 of 10 sections | All required sections from `TEMPLATE.md` complete including Database changes (§6) |
| 3 | Gaps and standing rules | Pass | Addresses `BUG-002`; strictly enforces `D-56` (RLS 3-branch CASE) and `D-57` (`setForConnection` auto-commit check) |
| 4 | Work is buildable | Pass | Target module is single (`code/backend/shared`); Flyway migration `V002__user_tenant.sql` provided in §6; executable `(cd code/backend && ./mvnw clean verify)` suite provided |

## Manager Blockers Addressed

| ID | Blocker | Draft § | How Resolved in Spec | Status |
|---|---|---|---|---|
| **B-1** | `core.user_tenant` table checked on every request but no Flyway migration created it (`V001__tenant.sql` created only `core.tenant`), forcing stubbing to `true`. | §2, §6, §7 | Brought `core.user_tenant` table creation into `W-08` via Flyway migration script `V002__user_tenant.sql` in §6 (`code/backend/migration/.../core/V002__user_tenant.sql`). Includes `tenant_id`, indexes, and RLS policy `CREATE POLICY tenant_isolation` (`D-56`). | **FIXED** 🟢 |

## What is good
- Strictly honors decisions `D-56` and `D-57` established in `W-07`.
- Brings `V002__user_tenant.sql` into `W-08`, eliminating any stubbing shortcuts during implementation.
- Executable, mechanical verification commands that run deterministically in CI and local test suites.
- Real HTTP MockMvc integration test coverage in `TenantBindingIT` against the real `core.user_tenant` Flyway table.
- Prohibits passing `tenantId` / `organizationId` as explicit controller parameters.
