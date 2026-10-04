# Feature: HR overtime list — seed grant and screen

| Field | Value |
|---|---|
| **Feature ID** | `W-68` · follows `W-48.5` · `HRMS-*` |
| **Promoted to** | `docs/target-state/features/W-68-hr-overtime-list.md` |
| **Owner** | Claude — `dev-claude` |
| **Apps touched** | `code/backend/core` — one seed migration, one added field; `code/frontend/src/hrms/requests` — one screen |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-04 |
| **Blocked by** | nothing — `W-48.5` on `main` `789b69a0` |
| **Size** | S |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` — seed function redefined, one field | 1 |
| Flyway migration | `core/V148__overtime_read_seed_roles.sql` | 1 |
| Externally testable behaviour | HR opens the overtime list and sees everyone's requests with names | 1 |
| Frontend area | `src/hrms/requests` | 1 |

---

## 1. Problem

`W-48.5` §2 left HR's overtime list out: `GET /api/v1/overtime` needs `core.overtime.read`
(`core/.../overtime/OvertimeController.java:52-53`), and no seeded role holds it. `V025__catalogue_correction.sql:62-63`
defines `core.overtime.read` and `core.overtime.manage`; `V139__hrms_request_seed_roles.sql:34-139` grants neither to
any role. So HR can see every regularization (`core.attendance.read`, `V139:54`) but no overtime request, and the
reply carries `employee_id` only (`OvertimeResponse.java:11-22`).

## 2. Scope

**In scope**

- `core`: grant `core.overtime.read` to `hr` and `payroll-officer` (who already reads attendance, `V139:84`); backfill
  existing tenants, as every seed migration does
- `core`: `employee_name` on `OvertimeResponse`, filled on `list` with one batch read
- `/hrms/overtime-requests/all` — HR: everyone's requests for a range, filter by employee; row opens the detail

**Out of scope**

- `core.overtime.manage` — approving is the engine's (`W-40.6`); cancelling from a screen is its own ticket
- Any change to `hrms`

## 3. Flow

```
[hr | payroll-officer] /hrms/overtime-requests/all
   --> GET /v1/overtime?from=&to=&employeeId=     core.overtime.read; bare reply, snake_case
   row --> /hrms/overtime-requests/{id}            W-48.5's detail; GET /{id} admits core.overtime.read
```

No request until a range is picked, as `W-48.5` §13 decision 4.

## 4. Backend changes

All in `code/backend/core`.

| Layer | File | Change |
|---|---|---|
| Migration | `migration/.../core/V148__overtime_read_seed_roles.sql` | redefine `core.seed_system_roles` exactly as `V139` with two rows added — `('hr', 'core.overtime.read')`, `('payroll-officer', 'core.overtime.read')` — then `SELECT core.seed_system_roles(t.tenant_id) FROM core.tenant t` |
| DTO | `overtime/OvertimeResponse.java` | add `employee_name` (`@JsonProperty`, String, null unless filled) after `employee_id`; `from(entity)` leaves it null; `from(entity, name)` fills it |
| Service | `OvertimeServiceImpl.list` | one `EmployeeService.displayNames(ids)` batch read for the page (inject `EmployeeService`); every other method leaves the name null |

**API contract — changed**

| Method | Path | Response | Auth |
|---|---|---|---|
| GET | `/api/v1/overtime` | as today plus `employee_name` | `core.overtime.read` — now held by `hr`, `payroll-officer` |

## 5. Frontend changes

| File | Change |
|---|---|
| `src/hrms/requests/requestService.js` | add `allOvertime(from, to, employeeId)` → `/v1/overtime`, mapped through the existing `toOvertime` (which gains `employeeName`) |
| `src/hrms/requests/OvertimeLog.jsx` | **new** — as `RegularizationLog.jsx`: `NotEntitled` without `core.overtime.read`; range with presets, nothing requested until picked; employee `Select` from the names in the rows; `Table` (employee, date, hours, amount, status `Tag`, source, posted period); row opens the detail |
| `src/hrms/requests/MyOvertime.jsx` | "Everyone's requests" link to `/all`, shown with `useCan('core.overtime.read')`, as `MyRegularizations.jsx:60` |
| `src/hrms/index.js` | `/hrms/overtime-requests/all` declared before `/:id` |

## 6. Database changes

One Flyway script, `core/V148__overtime_read_seed_roles.sql`. It writes `core.role_action` rows only.

- [x] no new table; `core.role_action` already carries `tenant_id` and RLS
- [x] Flyway script; no `ddl-auto`
- [x] no money; `amount` shown as sent
- [x] no new index needed
- [x] expand only — rows added, none removed

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `core/.../overtime/OvertimeSeedRolesIT.java` | after migration, `hr` and `payroll-officer` hold `core.overtime.read` in an existing tenant; `manager` and `employee` do not; `list` carries `employee_name`, `get` does not |
| Unit | `requestService.test.js` (extend) | `allOvertime` hits `/v1/overtime` with its params and maps `employee_name` |
| Component | `OvertimeLog.test.jsx` | `NotEntitled` without the action; nothing requested until a range is picked; employee filter sends `employeeId` |

## 8. Verification

```bash
cd code/backend && ./mvnw -q -pl core -am spotless:check verify -Dit.test='OvertimeSeedRolesIT'
cd code/frontend && npm run lint && npx vitest run src/hrms/requests
```

| Check | Expected |
|---|---|
| backend, lint, tests | `BUILD SUCCESS`; clean |
| HR login | My overtime shows "Everyone's requests"; the list shows names |
| employee login | no link; `/hrms/overtime-requests/all` shows `NotEntitled` |
| `check-done.mjs W-68` | one migration, no new table, no `double`, no `ddl-auto` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Another branch adds `V148` | low | check `main` at branch time |
| The seed function body drifts from `V139` while being copied | medium | the IT checks every `V139` grant still holds for one role (`hr`), not just the new rows |

## 10. Rollback

A later migration removes the two `core.role_action` rows. The field is additive.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no new table |
| Flyway | `V148` |
| `Money` / `BigDecimal` | nothing summed |
| Index | none |
| Expand / contract | rows added only |
| No module references another | `core` only; `src/hrms` imports `@shared/*` and `@shell/screens` only |

## 12. Gap inventory

None.

## 13. Decisions — founder, 2026-10-04

| # | Question | Answer |
|---|---|---|
| 1 | Who gets `core.overtime.read`? | **`hr` and `payroll-officer`**, mirroring `core.attendance.read` |
| 2 | `core.overtime.manage` too? | **No.** Nothing on a screen needs it yet |
