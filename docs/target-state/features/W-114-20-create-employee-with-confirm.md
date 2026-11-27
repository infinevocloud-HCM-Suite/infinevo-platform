# Feature: AI helper adds an employee after HR confirms

| Field | Value |
|---|---|
| **Feature ID** | `W-114.20` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai/action`), `code/frontend/src/shell/assistant` (one line in `actionLinks.js`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.8` and `W-114.9` on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | HR gives the details in chat; no employee exists until HR confirms the card; a confirmed card creates exactly one employee | 1 |
| Frontend area | `shell` — one link line | 1 |

Size **M**.

## The rule

The model fills the form. **HR reads every field on the card and presses Confirm.** Confirm calls
the same `EmployeeService.create` the Add employee screen calls, so every rule of that screen applies.

## Decisions

| Point | Choice | Why |
|---|---|---|
| Fields the helper may set | Employee number, first, middle and last name, gender, date of joining, work email, department, designation, work location, employment type, probation end date, notice period | The fields of the Add employee form that are not sensitive (`employee/EmployeeRequest.java`) |
| Required | Employee number, first name, date of joining — what the service requires | `EmployeeServiceImpl.java:540-541`, `:560-563` |
| **Never set from chat** | Mobile number, status other than the service's default, termination fields, portal login, salary, bank, PAN, Aadhaar, any statutory data | Mobile is caught by the mask's number rule (`W-114.4`) and would arrive as `[hidden]`; the rest are money or identity. HR completes them on the employee's page, which the card links to |
| No invitation | `portalEnabled` is not sent; no invite is issued | Inviting is a separate, deliberate step on the screen |
| Reporting manager — **founder, 2026-11-23** | **Not set from chat**, and not offered as a second step. After the employee is created the helper says "Reporting manager is not set" and links to the employee's page, `/employees/{employeeId}` | Create has no manager field. A reporting line is its own endpoint with its own action, `core.reporting_line.manage` (`org/ReportingLineController.java:39-40`); HR sets it on the normal screen |
| Department, designation, location | Given by **name**, resolved on the server within the tenant. No match, or more than one, is refused with the list of valid names | The request takes ids (`EmployeeRequest.java`: `departmentId`, `designationId`, `workLocationId`); the model never handles ids |
| Employee number | HR supplies it. The helper does not invent one | The service only checks it is unique (`EmployeeServiceImpl.java:143-145`); the customer's numbering scheme is not in the system |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Adding an employee is only possible on the Add employee form. | `code/backend/core/src/main/java/com/infinevo/core/employee/EmployeeController.java:89-95`; route `/employees/new` (`code/frontend/src/core/index.js:133`) |
| 2 | Create checks the number is unique, the work email is unused and the plan's employee limit has room. | `EmployeeServiceImpl.java:143-158` |

## Scope

**In scope**

- Propose tool `core.propose_employee_create`; executor `core.employee.create`.
- Resolving the three org masters by name.

**Out of scope**

- Reporting manager, invitation, mobile, salary, bank, statutory and identity data, documents.
- Editing or terminating an employee; bulk add (the CSV import exists for that).

## Flow

```
"add Priya Nair, E-207, joining 2026-12-01, Finance, Accountant, Kochi, priya@acme.in"
  --> core.propose_employee_create { … }
        --> resolve Finance / Accountant / Kochi to the tenant's masters (unknown: error with valid names)
        --> early checks: number unused, email unused, limit has room, the service's field rules
        --> AiActionService.propose("core.employee.create", payload with resolved ids, summary, fields)
  --> card: every field, plus "Not set here: manager, mobile, salary, bank, portal login"

Confirm --> W-114.8 re-checks --> EmployeeCreateExecutor --> EmployeeService.create(request)
```

## Backend changes

Paths are under `code/backend/core/src/main/java/com/infinevo/core/`.

| Layer | File | Change |
|---|---|---|
| Tool | `ai/action/EmployeeActionToolProvider.java` | **New.** One tool, `core.propose_employee_create`; `requiredAction` `core.employee.create` |
| Executor | `ai/action/EmployeeCreateExecutor.java` | **New.** `actionCode` and `requiredAction` `core.employee.create`. Builds an `EmployeeRequest` from the payload — `mobile`, `status`, `terminationDate`, `terminationReason`, `terminationRemarks`, `portalEnabled` always null — and calls `EmployeeService.create(request)` |
| Service | `employee/EmployeeService.java`, `EmployeeServiceImpl.java` | **New method** `checkCreate(EmployeeRequest)`: runs `validate` (`:534`), the unique-number and unused-email checks (`:143-152`) and `limitGuard.checkRoomFor(1)` (`:157`) and writes nothing. `create` keeps its own checks; this is a read-only preview for the tool |

**The tool's input**

| Input | Rule |
|---|---|
| `employeeNumber`, `firstName`, `dateOfJoining` | Required. Missing: the tool's error names the field, and the helper asks HR for it |
| `middleName`, `lastName`, `gender`, `workEmail` | Optional. Gender is one of `MALE`, `FEMALE`, `OTHER`, `UNDISCLOSED` (`EmployeeServiceImpl.java:546-550`) |
| `department`, `designation`, `workLocation` | Optional names, matched ignoring case against active masters of the tenant |
| `employmentType`, `probationEndDate`, `noticePeriodDays` | Optional; the values the form accepts |

`checkCreate`'s refusals go back to the model with the service's own message: duplicate number,
email in use, limit reached, a bad date. No intent is written.

**At confirm** the `W-114.8` checks run (own intent, not expired, helper on, `core.employee.create`
still held). Then `create` runs every check again: a number taken in the meantime, or the limit
reached in the meantime, sets `FAILED` with the service's message. Step 7 of `W-114.8`'s table
(the caller has an employee profile) does not apply to this action — an HR user need not be an
employee. `EmployeeCreateExecutor.requiresEmployeeProfile()` returns `false` (the method is on
`W-114.8`'s `AiActionExecutor`, default `true`), and `AiActionService` skips the step for it.

`result`: `{ "employeeId", "employeeNumber", "traceId" }`.

**What else fires on this create**

| Hook | State |
|---|---|
| Employment terms section (D-40) | Written in the same transaction when any of the three terms is given (`EmployeeServiceImpl.java:165`) |
| Employee limit (`W-84.2`) | Enforced, answers with the limit message (`EmployeeController.java:163`, `409`) |
| Onboarding tasks (`W-102`) | Not on `main` today. When it merges, its create hook fires for an employee added here too, with no change to this spec |

## Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/shell/assistant/actionLinks.js` | Add `core.employee.create` → `/employees/{employeeId}` (`code/frontend/src/core/index.js:136`) |

**Routes added:** none.

## Database changes

None.

- [x] No new table, no migration
- [x] No money value
- [x] Stays inside `core`

## What is sent to the model

The new employee's name, number, joining date, work email and org placement, as HR typed them.
`W-114.4`'s mask hides a PAN, Aadhaar, bank or phone number if HR types one; the helper then
says it cannot take that here.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../employee/EmployeeServiceImplTest.java` (extend) | `checkCreate` refuses exactly what `create` refuses and writes nothing |
| Unit | `core/src/test/.../ai/action/EmployeeActionToolProviderTest.java` | Unknown department lists the valid names; two departments with the same name ask which; a missing required field names it; a duplicate number writes no intent; the payload never holds mobile, status or portal fields even when the model sends them |
| Integration | `core/src/test/.../ai/action/EmployeeCreateIT.java` | Confirm creates one employee with the resolved department; a second confirm creates none; ten parallel confirms create one; a number taken between proposal and confirm ends `FAILED`; at the plan limit the proposal is refused, and a limit reached between proposal and confirm ends `FAILED`; a user without `core.employee.create` is never offered the tool; the audit row's `trace_id` equals `result.traceId` |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
cd code/frontend && npx vitest run src/shell/assistant && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| The commands | Build success; existing employee tests pass unchanged | Pass 2026-11-27: `EmployeeServiceImplTest` 47, `EmployeeActionToolProviderTest` 8, `EmployeeCreateIT` 6 |
| HR: "add Priya Nair, E-207, joining 2026-12-01, Finance" | A card with every field; no employee until Confirm | not run |
| Confirm | One employee; the card links to `/employees/{id}`; the "N of M employees" badge goes up by one | not run |
| HR: "and make Ravi her manager" | The helper says the reporting manager is not set from chat and links to the employee's page; no card, no reporting line written | not run |
| Same again with E-207 | The helper says the number is taken; no card | not run |
| Department "Finanse" | The helper lists the valid departments | not run |
| "and her salary is 60,000, PAN ABCDE1234F" | The helper says salary and PAN are set on the employee's page; the PAN is not stored in the chat | not run |
| As an employee without the action | The helper says it cannot add employees | not run |
| At the plan's employee limit | Refused with the limit message; no card | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The model mis-types a name or a date | Medium | The card shows every field; nothing exists until Confirm; HR can correct on the employee's page |
| HR believes the employee is fully set up | Medium | The card lists what was not set; the "done" message repeats it with the link |
| `checkCreate` and `create` drift apart | Low | `checkCreate` calls the same private checks; the unit test compares the refusals |
| Two HR users add the same number at once | Low | The unique check at confirm and the database constraint decide; the loser sees `FAILED` |

**As built (2026-11-27, Claude).** `checkCreate` and `create` share one private method, `checkBeforeCreate`, so they cannot drift. The service's refusals are not `IllegalArgumentException` or `IllegalStateException`, so the executor turns them into those, and a confirm refused by the service ends `FAILED` with the service's message. A call naming a field chat never sets (mobile, status, portal, salary, bank, PAN, Aadhaar, the manager) is refused with a sentence saying where HR sets it, and nothing of it is stored. An employee added here gets the onboarding checklist, as one added on the screen does (`W-102`).

## Rollback

Revert the commit. The tool is no longer offered. Employees already created stay; they are
ordinary employees.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` (`employee`) | `checkCreate`, with tests |
| 2 | `core` (`ai/action`) | The tool provider, the executor (`requiresEmployeeProfile()` = `false`; the method itself comes with `W-114.8`), tests |
| 3 | frontend `shell` | One line in `actionLinks.js` and its test |
