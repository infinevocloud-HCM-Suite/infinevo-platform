# Feature: Leave administration screens

| Field | Value |
|---|---|
| **Feature ID** | `W-46.2` · from ticket #59 · `CORE-07` |
| **Spec file** | `docs/target-state/features/W-46-2-leave-admin-screens.md` |
| **Owner** | biren |
| **Apps touched** | `code/frontend/src/core/leave` · `code/backend/core` — `NavigationCatalogue.java` menu items only, no table |
| **Related gaps** | BUG-007 / DEBT-006 (closed by `W-16.3`), DEBT-026 (prevented) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 |
| **Blocked by** | `W-45`; `W-16.1`, `W-16.2`, `W-16.3`, `W-16.4a`, `W-16.4b` (karma) — every endpoint here is spec-only today |
| **Size** | **L** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` — menu items in `NavigationCatalogue.java`, nothing else | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an administrator configures leave and records it for employees from the browser; a Payroll-only tenant does this without any request-and-approve experience (`D-35`) | 1 |
| Frontend area | `src/core/leave` | 1 |

Within cap. The employee's own apply / withdraw screens are **`W-46.5`**, the approver's inbox is **`W-46.4`**. This ticket is the administrator's side only, which is what keeps it to one behaviour.

---

## 1. Problem

Leave is the merge `09-build-order.md:255` warns about. HRMS has the request-and-approve screens; Payroll has the allocation-and-consumption screens; neither has the other half.

| Frozen screen | Side | Ported? |
|---|---|---|
| `legacy/HRMS_Frontend/src/components/adminDashboard/LeaveType.jsx` | types | shape only; MUI retired |
| `legacy/HRMS_Frontend/src/components/adminDashboard/AdminLeaveBalance.jsx`, `EmployeeLeaves.jsx`, `EmployeeLeaveDetails.jsx` | balances, per-employee history | shape only |
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/leaveAttendence/leaveTypes.js`, `addLeaveTypes.js`, `editLeaveType.js` | types and policy | **yes**, the policy form fields |
| `legacy/Payroll-Fend-react/src/pages/mainPages/leaveManagement/leaveAllocation/leaveAllocation.jsx` | allocation | **yes**, the table |
| `legacy/Payroll-Fend-react/src/pages/mainPages/leaveManagement/markLeaveTaken/markLeaveTaken.jsx` | administrator records leave taken | **yes** — this is `D-35` in the frozen system |
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/leaveAttendence/importLeaveBalance.js:2239-2269` | import with a per-row error table | **yes**, the error table |

The backend has settled the merge: types and policy (`W-16-1`), allocation and balance (`W-16-2`), request with an on-behalf path that creates an `APPROVED` record with no approval instance (`W-16-3-request-approval.md:105`), consumption and loss of pay (`W-16-4a`), import (`W-16-4b`). The dual entity (BUG-007) is gone in the target; there is one `core.leave_request`.

## 2. Scope

**In scope**

- Leave types: list, create, edit, and the policy form (accrual, reset, carry-forward, exceed-balance mode, eligibility matrix)
- Allocations: per-employee balance table for a year, manual allocation, "run accrual as of" button
- Record leave on behalf of an employee (`POST /leave-requests/on-behalf`), with the same fields as the employee form
- All requests: filterable list (employee, status, dates) and a request detail with its approval trail; withdraw and cancel as `core.leave.manage`
- Per-employee consumption and loss-of-pay view for a period
- Import: upload the sheet (through the `W-21` document upload), dry run, results with the per-row error table, history
- Menu items under a `core.leave` parent

**Out of scope**

- Applying, withdrawing and cancelling one's **own** leave — `W-46.5`
- Approving — `W-46.4`
- Holidays — `W-46.3b`
- Loss-of-pay policy screens (`W-18`) — a Payroll screen ticket
- Any change to the leave endpoints. A missing field is a defect in `W-16.x`

## 3. Flow

```
[admin] --> /leave/types                 --> leaveTypeService.list() / create / update / savePolicy(id, body)
        --> /leave/allocations?year=     --> leaveBalanceService.forEmployee(id, asOf) per row
                                             leaveBalanceService.allocate({employeeId, typeId, year, openingDays})
                                             leaveBalanceService.accrue(asOf)
        --> /leave/requests              --> leaveRequestService.list({employeeId, status, from, to, page})
        --> /leave/requests/new          --> leaveRequestService.onBehalf(body)     (created APPROVED)
        --> /leave/requests/:id          --> leaveRequestService.get(id); withdraw(id, reason); cancel(id, reason)
        --> /leave/employees/:id         --> leaveConsumptionService.rows(id, year); lop(id, period)
        --> /leave/import                --> documentService.upload(file) --> leaveImportService.start({documentId, leaveYear, dryRun})
                                             leaveImportService.get(id) polled until done; history()
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `code/backend/core/.../navigation/NavigationCatalogue.java` | add `core.leave` (`nav.leave`, `/leave/types`, target `/api/v1/leave-types`, action `core.leave_type.read`) with children `core.leave.types`, `core.leave.allocations` (`/leave/allocations`, target `/api/v1/leave-types`, `core.leave_balance.manage`), `core.leave.requests` (`/leave/requests`, target `/api/v1/leave-requests`, `core.leave.read`), `core.leave.import` (`/leave/import`, target `/api/v1/leave-imports`, `core.leave_balance.manage`). `requiredModule` is `null` — leave is Core (`D-35`) |

The `NavigationCatalogueValidator` refuses to start if a leaf's `GET` does not exist (`W-12-3` §14a item 6), which is why this ticket waits for `W-16` on `main` rather than adding the items early. **No `W-16.x` spec adds these items** — recorded so nobody looks for them there.

**API contract** — consumed, not changed. `W-16-1` §4, `W-16-2-allocation-balance.md:92-94`, `W-16-3-request-approval.md:104-110`, `W-16-4a-consumption-lop.md:101-102`, `W-16-4b-leave-import.md:91-93`.

## 5. Frontend changes

`W-45` contract throughout (services, slice, no `axios`, no storage, tokens, Formik + Yup, `msgHelper`).

| File | Change |
|---|---|
| `src/core/leave/leaveTypeService.js` | **new.** `createService('/v1/leave-types')` spread, plus `savePolicy(id, body)` → `PUT /{id}/policy`, `eligible(employeeId)` |
| `src/core/leave/leaveBalanceService.js` | **new.** `forEmployee(id, asOf)`, `allocate(body)`, `accrue(asOf)` |
| `src/core/leave/leaveRequestService.js` | **new.** `list(params)`, `get(id)`, `onBehalf(body)`, `withdraw(id, reason)`, `cancel(id, reason)`. Shared with `W-46.5`, which adds `create`, `submit` to the same file |
| `src/core/leave/leaveConsumptionService.js` | **new.** `rows(id, year)`, `lop(id, period)` |
| `src/core/leave/leaveImportService.js` | **new.** `start(body)`, `get(id)`, `history(page)` |
| `src/core/leave/leaveSlice.js` | **new.** `{ types: [], loadedAt }` — the type list is read on every screen; cached, invalidated on type write |
| `src/core/leave/LeaveTypes.jsx` | **new.** Table: name, code, paid, unit, half-day, valid from / to, current policy summary. Create / edit `Drawer` |
| `src/core/leave/PolicyForm.jsx` | **new.** Accrual frequency, reset frequency, carry-forward limit, exceed-balance mode (`NO_LIMIT`, `YEAR_END_LIMIT` with limit days, `MARK_AS_LOP`), eligibility matrix as four multi-selects (department, designation, location, gender). Fields from `addLeaveTypes.js`; semantics from `W-16-1` §6 |
| `src/core/leave/Allocations.jsx` | **new.** Year picker; employee table with one column per type showing balance; row expand shows the working the endpoint returns; "Allocate" `Modal` (employee, type, year, opening days); "Run accrual as of" with confirm. From `leaveAllocation.jsx` |
| `src/core/leave/LeaveRequests.jsx` | **new.** Filters employee, status, from, to; paged table: employee, type, dates, days, status `Tag`, submitted. "Record leave" button opens `RecordLeave` |
| `src/core/leave/RecordLeave.jsx` | **new.** Employee `Select`, type (from `eligible`), from, to, half-day + period, reason. Working days shown from the response. This is `markLeaveTaken.jsx` on the on-behalf endpoint |
| `src/core/leave/LeaveRequestDetail.jsx` | **new.** `Descriptions` plus the approval trail as `Timeline`; Withdraw / Cancel with reason `Modal`, gated by `useCan('core.leave.manage')`. `W-46.5` reuses this component read-only |
| `src/core/leave/EmployeeLeave.jsx` | **new.** Consumption rows for a year and the loss-of-pay figure per month with its working. From `EmployeeLeaveDetails.jsx` in shape |
| `src/core/leave/LeaveImport.jsx` | **new.** `Upload` (one file) → document id; leave year; dry-run `Switch` default on; result panel: counts and the per-row error table (row, employee, year, type, message — `importLeaveBalance.js:2239-2269`); history table. Polls `get(id)` every 2 s until terminal |
| `src/core/index.js` | `routes` gains the seven below; `reducers` gains `leave` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/leave/types` | `LeaveTypes` | inside `AppShell`; present when the feed carries `core.leave.types` |
| `/leave/allocations` | `Allocations` | `core.leave.allocations` |
| `/leave/requests` | `LeaveRequests` | `core.leave.requests` |
| `/leave/requests/new` | `RecordLeave` | same item; button hidden without `core.leave.manage` |
| `/leave/requests/:id` | `LeaveRequestDetail` | same item |
| `/leave/employees/:id` | `EmployeeLeave` | same item |
| `/leave/import` | `LeaveImport` | `core.leave.import` |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `src/core/leave/*Service.test.js` | each call hits its path and method; `onBehalf` posts to `/v1/leave-requests/on-behalf` |
| Component | `LeaveTypes.test.jsx`, `PolicyForm.test.jsx` | `YEAR_END_LIMIT` reveals the limit field; save calls `savePolicy` |
| Component | `Allocations.test.jsx` | allocate posts the four fields; accrual asks for confirmation |
| Component | `RecordLeave.test.jsx` | type list comes from `eligible(employeeId)`; submit calls `onBehalf` |
| Component | `LeaveImport.test.jsx` | dry run on by default; error rows render; polling stops on a terminal status |
| Backend unit | `core/.../navigation/NavigationCatalogueTest` | the four leave leaves are present and point at existing `GET`s (the validator test that already exists gains the rows) |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
cd ../backend && ./mvnw -pl core test -Dtest=NavigationCatalogue*
docker compose -f infra/docker/compose.yml up -d
```

| Check | Expected |
|---|---|
| menu | admin@acme.local sees Leave with four children; an employee-role user sees none |
| types | create "Casual leave", set `MARK_AS_LOP`; reload, policy persists |
| allocation | allocate 12 days to one employee for the year; the balance column shows 12 |
| record | record 2 days for that employee; the request is `APPROVED` with no approval trail; balance shows 10 |
| import | upload the `W-16.4b` fixture sheet with dry run: counts, zero writes; run again without dry run: balances change |
| Payroll-only | log in as a tenant holding Payroll only: every screen above works, nothing mentions approval (`D-35`) |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Six screens against five backend tickets: one endpoint shape changes late | medium | services are one file each; the component tests mock the service, not HTTP |
| The record-on-behalf form and the employee's apply form drift apart | medium | one `LeaveRequestFields` component in this ticket, imported by `W-46.5` |
| Upload path depends on `W-21`'s document endpoint | medium | `W-16.4b` already takes a `documentId`; if `W-21` is late, the import screen ships last, the rest does not wait |
| Menu items added before the endpoints exist crash startup | low | items ride in this ticket, which is blocked on `W-16` |

## 10. Rollback

Revert the branch. The catalogue rows go with it; no data.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | no money; days are decimals from the API and displayed as received |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/core/leave` imports `@shared/*`, `@shell/screens` and `@core/employee` (the employee `Select`); the catalogue change is in `core` |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| BUG-007 / DEBT-006 (two leave entities) | requests list | **closed by `W-16.3`**; one endpoint, one screen |
| DEBT-026 (admin/user file pairs) | record vs apply form | **prevented** by the shared `LeaveRequestFields` |

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` (backend) | catalogue rows and the test |
| 2 | `src/core/leave` | services, slice, `LeaveRequestFields`, tests |
| 3 | `src/core/leave` | `LeaveTypes`, `PolicyForm`, `Allocations`, tests |
| 4 | `src/core/leave` | `LeaveRequests`, `RecordLeave`, `LeaveRequestDetail`, `EmployeeLeave`, tests |
| 5 | `src/core/leave` | `LeaveImport`, tests; `src/core/index.js` registration |

## 14. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | Is the administrator's record-leave the same screen as the employee's apply? | **Same fields, different endpoint.** On-behalf creates an approved record (`W-16-3:105`); apply starts an approval. One field component, two thin screens |
| 2 | Who adds the menu items? | **This ticket.** No `W-16.x` spec does, and the validator forbids adding them before the endpoints exist |
| 3 | Could this start before `W-16` lands? | **Services and forms yes, against the spec contracts; nothing merges before `W-16.4b` is on `main`** because the catalogue rows would stop the app |
