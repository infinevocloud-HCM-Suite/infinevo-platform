# Payroll-Bend-SBoot

Java 17 · Spring Boot 3.2.5 · MySQL `payrollDB` · ports 3032 dev / 3029 prod · Keycloak OAuth2
resource server (realm `HRMS`). Root package `com.itsdev.payroll`. Branch **`taxation`** (not
`main` — that is 9 months stale). See root `@CLAUDE.md`.

| Task | Command |
|---|---|
| Build | `./mvnw -q compile` (wrapper works here) |
| Test | `./mvnw test` — one test exists: `service/LeaveAllocationImportTest.java` |
| Lint | none configured |
| Run | `./mvnw spring-boot:run` |

**Layering:** `controller → service → serviceimpl → repository → entity`. Logic only in
`serviceimpl/`. Every business endpoint takes `@RequestHeader("organizationId")`.
Entities carry both a DB PK (`Long id`) and a random 10-digit business ID — do not confuse them.

**Known issues** (`@docs/GAP_INVENTORY.md`): zero `@Index` on 99 entities (DEBT-018) ·
N+1 everywhere, 1 of 78 repos uses `JOIN FETCH` (DEBT-019) · in-memory permission cache blocks
scaling (DEBT-020) · unlocked schedulers double-fire (DEBT-021) · 57 of 144 queries not
org-scoped (DEBT-022) · `/api/test/**` unguarded (DEBT-023) · duplicate methods in
`ProfessionalTaxServiceImpl` (DEBT-024) · three parallel POI implementations (DEBT-025).

**Gotchas:** `BasicDetailsController`, `CtcStructureController`, `EmployeePersonalDetailController`
each keep a **commented-out old version above the live class — read from the bottom**.
Naming strategy is `PhysicalNamingStrategyStandardImpl`, so an entity without `@Table` becomes
a table named exactly like the class (`FBP`, `IncomeTaxDeclaration`). Typo package:
`entity/leaveAndAttedance/`. Money is `BigDecimal` only — see `@docs/CONVENTIONS.md` §2.
