# Feature: A leave policy can be limited to named employees

| Field | Value |
|---|---|
| **Feature ID** | `W-79.1` · `CORE-LEAVE` |
| **Owner** | claude |
| **Apps touched** | `code/backend/core` (`leave`), `code/backend/migration` (one Flyway script), `code/frontend` (`src/core/leave`) |
| **Related gaps** | founder 2026-11-02: legacy gave a leave type to picked employees (`legacy/HRMS_Backend/.../repository/LeaveTypeRepository.java:23-26`, `leave_type_employee_ids`). HCM can limit a policy only by department, designation, work location and gender (`LeaveEligibilityServiceImpl.java:82-119`, `LeavePolicyValidator.java:12`), so Comp Off and Probation Leave show in everyone's apply list |
| **Status** | **Done** — `c9fce7a3` |
| **Blocked by** | nothing |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one: allow dimension `employee` | 1 |
| Externally testable behaviour | A policy with named employees is eligible only for them | 1 |
| Frontend area | `core/leave` | 1 |

## Rule

A new eligibility dimension `employee`, value = employee id. When a policy has any `employee` rows, only those employees are eligible; it combines with the other dimensions by AND, as today. No rows → everyone, as today.

## Changes

| File | Change |
|---|---|
| `migration/.../core/V187__leave_policy_eligibility_employee.sql` | Drop and re-add `chk_leave_policy_eligibility_dimension` (`V128__leave_policy_eligibility.sql:14`) with `'employee'` added. Expand only; tenant_id and RLS unchanged |
| `core/leave/LeavePolicyValidator.java` | `ALLOWED_DIMENSIONS` gains `employee` (`:12`) |
| `core/leave/LeaveEligibilityServiceImpl.java` | After the work-location check (`:110-117`): an `employee` dimension must contain the employee's id |
| `core/leave/LeavePolicyRequest` / response | No change: eligibility is already a list of `{dimension, valueId}` rows (`LeavePolicyRequest.java:31`) |
| `core/leave/EmployeePicker.jsx` | `multiple` prop: several employees, names kept across searches, a saved id named by `employeeService.get` |
| `core/leave/PolicyForm.jsx` | "Employees (Empty for All)" picker below the other filters; loads and sends `employee` rows |

## Data (migrated company, after merge — founder step or claude on request)

Give policy `CO` the employees who hold a CO allocation and policy `PRL` those who hold a PRL allocation, so they disappear from everyone else's apply list.

## Tests

| File | Covers |
|---|---|
| `LeaveEligibilityServiceTest` | Named employee eligible; other employee not; no rows → everyone |
| `LeavePolicyValidationTest` | `employee` accepted, unknown dimension refused |
| `LeavePolicyEligibilityIT` | Insert of an `employee` row passes the CHECK |
| `PolicyForm.test.jsx` | A saved employee row is named and sent back as an `employee` row |

## Verification

```bash
cd code/backend && ./mvnw -B clean verify
cd code/frontend && npx vitest run src/core/leave && npm run lint && npm run build
```
