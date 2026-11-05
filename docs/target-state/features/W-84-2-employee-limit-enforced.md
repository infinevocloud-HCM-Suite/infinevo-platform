# Feature: The employee limit is enforced and usage shows on screen

| Field | Value |
|---|---|
| **Feature ID** | `W-84.2` · `PLAT-01` |
| **Owner** | claude (`dev-claude-w84`) |
| **Apps touched** | `code/backend/core` (`employee`, `employeeimport`), `code/frontend` (`src/core/employee`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-84.1` on `main` (the `employee_limit` column and `GET …/subscription` carrying it) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | Adding, importing or reactivating an employee that would pass the tenant's limit is refused | 1 |
| Frontend area | `core/employee` | 1 |

## Rule

**Usage** is the number of employees in the tenant that are not soft-deleted and whose status is not `TERMINATED`. It is counted live from `core.employee` every time, never stored. `SUSPENDED` employees count, because reactivating one from `SUSPENDED` is a plain status edit and must not open a side door.

**The gate**: an action that would make usage exceed `employee_limit` is refused with `409` and the message `Your plan allows M employees and N are in use. Contact Infinevo to raise the limit.` A `NULL` limit means no gate. The gate runs in every subscription status, as the founder decided 2026-11-05.

Three paths add a counted employee, and all three go through one check:

| Path | Where | Today |
|---|---|---|
| Add employee | `EmployeeServiceImpl.create` (`code/backend/core/src/main/java/com/infinevo/core/employee/EmployeeServiceImpl.java:132-154`) | No limit anywhere |
| Reactivate | `EmployeeServiceImpl.update` (`:216-245`) when status moves from `TERMINATED` to `ACTIVE` or `SUSPENDED`; the screen's Reactivate button sends exactly this (`code/frontend/src/core/employee/EmployeePage.jsx:159-177`, `:207-208`) | No limit |
| CSV import | `EmployeeImportServiceImpl.importOne` calls `employeeService.create` per row (`code/backend/core/src/main/java/com/infinevo/core/employeeimport/EmployeeImportServiceImpl.java:339-342`); the dry run checks each row with `Checker.check` (`:498-520`) | No limit |

A reactivation from `SUSPENDED` does not change usage and is not checked. A terminate, a soft delete and an edit that keeps the status are not checked.

## Flow

```
HR --> EmployeeCreate / EmployeePage Reactivate / EmployeeImport
   --> POST|PUT /api/v1/employees… --> EmployeeServiceImpl --> EmployeeLimitGuard.checkRoomFor(n)
   --> core.subscription.employee_limit  vs  count(core.employee where deleted=false and status<>'TERMINATED')
   --> 409 EMPLOYEE_LIMIT_REACHED, or the write goes ahead
HR --> EmployeeList header --> GET /api/v1/tenants/{id}/subscription --> "N of M employees"
```

## Backend changes

| Layer | File | Change |
|---|---|---|
| Repository | `core/employee/EmployeeRepository.java` | `long countByTenantIdAndDeletedFalseAndStatusNot(UUID tenantId, EmploymentStatus status)`. Hits `idx_employee_tenant_status` (`Employee.java:87`) |
| Service | `core/employee/EmployeeLimitGuard.java` | new, package-private. `checkRoomFor(int adding)`: reads the bound tenant's `Subscription.employeeLimit` through `SubscriptionRepository.findByTenantId` (`SubscriptionRepository.java:17`, RLS-scoped, no platform bypass needed); `null` returns at once; otherwise counts usage and throws `EmployeeLimitReachedException(limit, used)` when `used + adding > limit`. `room()` returns the number that can still be added, or `null` for unlimited, for the import dry run |
| Service | `core/employee/EmployeeService.java` | new nested `EmployeeLimitReachedException` beside `DuplicateEmployeeNumberException` (`:111`) carrying `limit` and `used` |
| ServiceImpl | `core/employee/EmployeeServiceImpl.java` | `create`: `guard.checkRoomFor(1)` after the duplicate checks and before `new Employee` (`:146`). `update`: when `employee.getStatus() == TERMINATED` and `fields.status() != TERMINATED`, `guard.checkRoomFor(1)` before `applyTo` (`:236`). Both inside the existing transaction, so the count and the insert are read together |
| Controller | `core/employee/EmployeeController.java` | `@ExceptionHandler(EmployeeLimitReachedException)` → `409`, `ApiError.CONFLICT`, code `EMPLOYEE_LIMIT_REACHED`, the message above (`:131-136` pattern) |
| Import | `core/employeeimport/EmployeeImportServiceImpl.java` | `Checker` takes `guard.room()` once (`:479`); `check` marks every valid row past the room with error `Over the employee limit (M). Raise the limit before importing this row` (`:498`). Rows the dry run already rejects do not use up room. `importOne` needs no change: the row transaction (`:339-357`) calls `create`, which throws, and `reason(e)` puts the message in the row result |

**API contract**

| Method | Path | Change |
|---|---|---|
| `POST` | `/api/v1/employees` | new `409 EMPLOYEE_LIMIT_REACHED` |
| `PUT` | `/api/v1/employees/{id}` | new `409 EMPLOYEE_LIMIT_REACHED` when leaving `TERMINATED` |
| `POST` | `/api/v1/employees/import/dry-run`, `/import` | over-limit rows come back `ERROR` with the message above |

No new endpoint. Usage on screen reads `GET /api/v1/tenants/{id}/subscription`, which already answers for the bound tenant (`SubscriptionController.java:38-46`) and carries `employeeLimit` from `W-84.1`. It needs `core.tenant.read`; HR holds it already, because the setup checklist reads the same endpoint.

## Frontend changes

| File | Change |
|---|---|
| `src/core/employee/employeeService.js` | `limit()`: `GET /v1/tenants/{tenantId}/subscription` through `tenantService.subscription` (`src/core/admin/tenantService.js:88-91`), returns `{ limit, used }` where `used` is the server count; no new endpoint, `used` comes from the list total with `status` unfiltered. Cached for the page |
| `src/core/employee/EmployeeList.jsx` | `PageHeader` `count` (`:256-258`) reads `N of M employees` when a limit is set, `N employees` when not. Red when `N >= M`, with a tooltip "Your plan allows M employees". N is the unfiltered total, not the filtered page's `totalElements` (`:68`) |
| `src/core/employee/EmployeeCreate.jsx` | On a `409` with code `EMPLOYEE_LIMIT_REACHED`, show the server message in an `Alert` above the form instead of a toast (`:248` `errorMsg` path); Save stays enabled so a corrected count can be retried |
| `src/core/employee/EmployeePage.jsx` | The Reactivate confirm (`:159-177`) shows the same `Alert` on `409 EMPLOYEE_LIMIT_REACHED` |
| `src/core/employee/EmployeeImport.jsx` | No change: over-limit rows are ordinary `ERROR` rows (`:59-60`, `:109`) |

No route added. Nothing on the tenant admin's side lets them raise the limit; the message says to contact Infinevo.

## Database changes

None. No table, no column, no migration. The count is a query on `core.employee` through the existing index.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/employee/EmployeeLimitGuardTest.java` | `null` limit never throws; `used + adding > limit` throws with both numbers; equal passes; terminated and deleted rows are not in `used`, suspended rows are |
| Unit | `core/employee/EmployeeServiceTest.java` | `create` at the limit throws; `update` `TERMINATED → ACTIVE` at the limit throws; `SUSPENDED → ACTIVE` does not; `ACTIVE → TERMINATED` does not |
| Integration | `core/employee/EmployeeLimitIT.java` | new. Limit 2, two active: `POST` answers `409 EMPLOYEE_LIMIT_REACHED`; terminate one, `POST` answers `201`; reactivate the terminated one answers `409`; limit `NULL` lets ten through |
| Integration | `core/employeeimport/EmployeeImportServiceImplTest.java` | Limit 3 with 1 used, five valid rows: dry run marks rows 3 to 5 `ERROR` "Over the employee limit"; the import creates two |
| Component | `src/core/employee/EmployeeList.test.jsx` | Badge reads `12 of 50 employees`; `50 of 50` is red; no limit reads `12 employees` (keeps the `W-83.2` test at `:91`) |
| Component | `src/core/employee/EmployeeCreate.test.jsx` | A `409 EMPLOYEE_LIMIT_REACHED` renders the Alert with the server message; Save stays enabled |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
cd code/frontend && npx vitest run src/core/employee && npm run lint && npm run build
```

| Check | Expected |
|---|---|
| Platform admin sets the test tenant's limit to its current headcount (`W-84.1` card) | Employees page badge turns red, `N of N employees` |
| Add employee | `409`, Alert "Your plan allows N employees and N are in use. Contact Infinevo to raise the limit." |
| Terminate one employee, add again | `201`, badge `N of N` again |
| Reactivate the terminated one | `409`, same message in the Reactivate dialog |
| Import a CSV of 3 valid rows with room for 1 | Dry run: row 1 OK, rows 2 and 3 `ERROR` "Over the employee limit"; import creates 1 |
| Platform admin clears the limit | Badge reads `N employees`, every path works as before |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Two HR users add at the same moment and both pass the count | Low | Both run inside the write transaction; at worst one over the limit, visible on the badge and in the tenant list. A row lock on `core.subscription` is not worth the contention for a billing cap |
| The import dry run and the real import disagree because someone was added between them | Low | The real import re-checks per row through `create`; the extra row comes back `ERROR` with the same message |
| The badge double-counts because it reads a filtered total | Medium | The spec says unfiltered total; the test pins it |

## Rollback

Revert the application. No schema change. The limit stays stored and simply stops being enforced.
