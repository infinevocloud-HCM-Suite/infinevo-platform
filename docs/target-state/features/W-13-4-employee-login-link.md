# Feature: Employee ↔ login link

| Field | Value |
|---|---|
| **Feature ID** | `W-13.4` · from ticket `W-13` · `12-core-contracts.md` §5 row 14 |
| **Promoted to** | `docs/target-state/features/W-13-4-employee-login-link.md` on branch `W-13-4-employee-login-link` — **`W-13-4` with hyphens**, never `W-13.4`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration`, `code/backend/shared` (annotation, ~10 lines) |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | nothing — `W-13.1`/`W-13.2` are on main |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core`, plus one attribute on `shared.authz.RequiresAction` | 1 (+ exception below) |
| Flyway migration | 1 script, one `ALTER TABLE` | 1 |
| Externally testable behaviour | an employee linked to their login can `PUT` their own personal and contact sections with `core.employee.update_own`, and nobody else's | 1 |
| Frontend area | none | 1 |

Exception: `@RequiresAction` takes one code (`RequiresAction.java:15`) and the aspect calls
`require(value)` (`RequiresActionAspect.java:43`). "`update` or, for yourself, `update_own`"
cannot be said with it. The smallest fix is an `anyOf` attribute in `shared`; the alternative,
a parallel `/me/personal` controller, duplicates two endpoints.

---

## 1. Problem

Nothing joins a login to an employee. `core.user_account` is the login (`V009__user_account.sql:4-18`),
`core.employee` the person (`V010__employee.sql:4-23`), and no column connects them
(`12-core-contracts.md:24`). So `core.employee.update_own` and `read_own` exist in the
catalogue (`V020__action.sql:58-60`) and are granted to `employee` (`V022__role_action.sql:164-165`),
but no endpoint can honour them: the personal and contact `PUT`s demand `core.employee.update`
(`EmployeeDetailController.java:100,113`), which only `hr` and the admins hold.

Legacy HRMS solved this with a one-to-one from the user to the employee
(`legacy/HRMS_Backend/.../entity/OurUsers.java:49-52`, `@OneToOne @JoinColumn(name = "employee_id")`).
The platform puts the FK on the employee side: an employee may have no login yet
(`is_portal_enabled`, `V010:17`), and a login belongs to one tenant already (`V009:6`).

## 2. Scope

**In scope**

- `core.employee.user_account_id UUID NULL`, unique per tenant where set
- `EmployeeService.currentEmployee()` — the caller's own employee row
- `PUT /employees/{id}/personal` and `/contact` admit `core.employee.update_own` when `{id}` is the caller's own employee
- `PUT /employees/{id}/login` — an administrator sets or clears the link
- `userAccountId` on `EmployeeResponse`

**Out of scope**

- Creating the Keycloak user or the `user_account` row — `W-24.2` (invitations)
- `GET /me/*` self-service reads — `W-25`
- Identification, employment and bank sections stay admin-only

## 3. Flow

```
Employee --> PUT /api/v1/employees/{id}/contact --> aspect: holds update OR update_own
         --> ContactService.put: caller lacks update ⇒ {id} must equal currentEmployee().id, else 403
currentEmployee(): TenantContext.require() + JWT subject --> UserProfileSyncService.find --> user_account.id
                   --> EmployeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse
Admin    --> PUT /api/v1/employees/{id}/login {userAccountId} --> EmployeeService.linkLogin --> core.employee
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Annotation | `shared/.../authz/RequiresAction.java` | Add `String[] anyOf() default {}`. Aspect (`RequiresActionAspect.java:43`): pass if `holds(value)` or `holds` any of `anyOf`; else the existing `require(value)` refusal |
| Migration | `core/V026__employee_user_account.sql` | §6 |
| Entity | `core/.../employee/Employee.java` | `@Column(name = "user_account_id") UUID userAccountId` |
| Repository | `EmployeeRepository.java` | `Optional<Employee> findByTenantIdAndUserAccountIdAndDeletedFalse(UUID, UUID)` |
| Service | `EmployeeService.java`, `EmployeeServiceImpl.java` | `Optional<EmployeeResponse> currentEmployee()`; `EmployeeResponse linkLogin(UUID id, UUID userAccountId)` (null clears). `currentEmployee` reads the tenant as every method does (`EmployeeServiceImpl.java:78,211`), the subject as `MeController.java:39` and `PermissionService.java:135` do, then `UserProfileSyncService.find(tenantId, keycloakUserId)` (`UserProfileSyncService.java:85`) → `UserAccount.getId()` |
| Service | `detail/EmployeePersonalServiceImpl`, `EmployeeContactServiceImpl` (`put`, contract `EmployeeDetailService.java:50`) | Before writing: if `!permissionService.holds("core.employee.update")` (`PermissionService.java:52`) and `id` ≠ `currentEmployee().id` → `PermissionDeniedException` |
| Controller | `EmployeeDetailController.java:99-104,112-117` | `@RequiresAction(value = "core.employee.update", anyOf = "core.employee.update_own")` on the two `PUT`s only |
| Controller | `EmployeeController.java` | New `PUT /{id}/login`, `@RequiresAction("core.employee.update")` |
| DTO | `EmployeeResponse`, new `EmployeeLoginRequest(UUID userAccountId)` | `userAccountId` field |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `PUT` | `/api/v1/employees/{id}/personal` | unchanged | unchanged | `core.employee.update`, or `core.employee.update_own` for own `{id}` |
| `PUT` | `/api/v1/employees/{id}/contact` | unchanged | unchanged | same |
| `PUT` | `/api/v1/employees/{id}/login` | `{userAccountId: uuid \| null}` | `200`, `EmployeeResponse` | `core.employee.update` |

**Rules the service enforces**

| Rule | Refusal |
|---|---|
| Caller holds only `update_own` and `{id}` is not their employee, or they have no linked employee | `403 PERMISSION_DENIED` — same body as any denial, so ids cannot be probed |
| `userAccountId` not in this tenant (`UserAccountRepository`, already used at `RoleServiceImpl.java:89`) | `400 VALIDATION_FAILED` |
| `userAccountId` already linked to another live employee | `409` |
| Employee soft-deleted | `404` (`EmployeeService.NotFoundException`) |

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V026__employee_user_account.sql` — **reserved 2026-09-25**; `W-11.3` holds `V025`, `W-09.1` holds `V027` | `core.employee` (column + index) | yes — existing RLS (`V010`) covers the column | forward-only; nullable column, nothing destroyed |

```sql
ALTER TABLE core.employee
    ADD COLUMN user_account_id UUID NULL REFERENCES core.user_account(id);   -- PK id, V009:5
CREATE UNIQUE INDEX uk_employee_tenant_user_account
    ON core.employee (tenant_id, user_account_id) WHERE user_account_id IS NOT NULL;
```

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | no new table; `core.employee` already has both |
| Flyway only, `ddl-auto` nowhere | one script; the `@Table(indexes)` list on `Employee.java:84-90` is documentation only and cannot express a partial index — leave it |
| Money as `Money` / `BigDecimal` | no money column |
| Index on `tenant_id` plus lookup columns (`DEBT-018`) | the unique index leads with `tenant_id` and covers the FK (`README.md` rule 5) |
| Expand / contract | expand only |
| No module references another | `core` → `shared` only, which is allowed |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `shared/.../authz/RequiresActionAspectTest` (extend) | `anyOf` admits the second code; neither code → denied |
| Unit | `core/.../employee/EmployeeServiceTest` (extend) | `currentEmployee` empty when no `user_account` or no link; `linkLogin` refusals in §4 |
| Integration | `core/.../employee/EmployeeSelfServiceIT.java` | employee `PUT`s own contact with `update_own` → `200`; another employee's id → `403`; `hr` without `update_own` still `200` on any id |
| Integration | `core/.../employee/EmployeeLoginLinkIT.java` | unique per tenant; the same `user_account` id in tenant B is invisible (`400`), never `409` |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexdef FROM pg_indexes WHERE schemaname='core' AND indexname='uk_employee_tenant_user_account';"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Index | one row, `UNIQUE ... (tenant_id, user_account_id) WHERE (user_account_id IS NOT NULL)` |
| Suite | green, no skips; `EndpointGuardCoverageTest` still passes (both `PUT`s keep an annotation) |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A later ticket uses `anyOf` to loosen a guard rather than for self-service | medium | Javadoc on the attribute: only `*_own` alternatives, and the service must check ownership |
| Ownership check forgotten in a new section service | medium | `EmployeeSelfServiceIT` asserts the `403` for another employee's id on both sections |
| `W-24.2` needs the link set at invitation acceptance | certain | It calls `linkLogin`; no second write path |

## 10. Rollback

Revert the application commit. The column and index stay, unused; Flyway is forward-only.
