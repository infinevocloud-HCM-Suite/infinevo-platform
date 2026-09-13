# HRMS_Backend

Java 21 · Spring Boot 3.2.4 · MySQL `hrmstestdb` · port 1010 · custom JWT (`jjwt` 0.12.5).
Root package `com.phegondev.usersmanagementsystem`. Branch `main`. See root `@CLAUDE.md`.

| Task | Command |
|---|---|
| Build | `./mvnw -q compile` — wrapper added 2026-09-11 (`.mvn/wrapper/`, Maven 3.9.11); DEBT-032 resolved locally. Note: `target/` is tracked in git, so compiling dirties the tree (DEBT-034) |
| Test | `mvn test` — no tests exist yet (DEBT-003) |
| Lint | none configured |
| Run | `mvn spring-boot:run` |

**Layering:** `controller → service → serviceimpl → repository → entity`. Logic only in `serviceimpl/`.

**Known issues** (`@docs/GAP_INVENTORY.md`): no tenancy at all — 0 files mention `organizationId`
(BUG-002) · duplicate entities `LeaveRequest`/`LeaveRequests`, `Timesheet`/`Timesheets`, all four
live tables (BUG-007, DEBT-006) · `UsersManagementService` God Service (DEBT-005) ·
`exception/` and `exceptions/` both exist (DEBT-009) · typo package `serviceimpl/timeshhet/` (DEBT-013).

**Naming:** no physical naming strategy set, so Hibernate converts CamelCase → snake_case
(`EmployeeDocument` → `employee_document`). Opposite of Payroll.
