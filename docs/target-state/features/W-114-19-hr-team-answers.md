# Feature: AI helper answers "who is on leave", "who joined", "who left" and headcount

| Field | Value |
|---|---|
| **Feature ID** | `W-114.19` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai`, three read queries in `employee`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.4` (the gateway and the tool plug-in point in use) on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A caller who may see the team on screen gets the same people from the helper; a caller who may not gets nothing | 1 |
| Frontend area | none | 1 |

Size **M**. Read-only. No action, no card.

## The rule

**The helper shows a caller only what that caller can already open on a screen**, using the same
service and the same action code as that screen. It adds no new way to see anyone.

## What is sent to the model — stated plainly

These tools send **other employees' names, employee numbers, departments and leave or joining
dates** to the AI model. Until now the helper sent only the caller's own data. It happens only
for a caller who holds the action that shows the same rows on screen. Nothing else about those
people is sent: no salary, no contact details, no identifiers, no leave reason and no leave type.

## Decisions

| Point | Choice | Why |
|---|---|---|
| Who is on leave, for HR | Callers holding `core.leave.read` see everyone | It is what the Leave requests screen does (`LeaveRequestController.java:89-91`) |
| Who is on leave, for a manager | Callers holding `core.leave.read_team` see themselves and their direct reports, through `reportingLineService.directReportIds(caller, today)` | Manager scoping exists and is reused exactly (`LeaveRequestController.java:93-106`; `org/ReportingLineService.java:102`) |
| Joiners, leavers, headcount | Callers holding `core.employee.read` only. No manager version | The employee list has one guard, `core.employee.read` (`EmployeeController.java:79-80`), and no team scoping in its query. Inventing one here would be a new way to see people |
| Leave type — **founder, 2026-11-23** | Never returned and never sent to the model, for managers and HR alike. The helper says only "On leave" | "Sick" or "Maternity" is health information. "Who is away" needs the name and the dates, not the type. A caller who needs the type opens the Leave requests screen |
| Leave reason | Not returned | A reason can be medical. The dates are enough for "who is out" |
| Row limit | 50 rows, then "and N more — open the screen" | Keeps the answer readable and the tool result inside `W-114.4`'s 8,000-character cut |
| Leavers | Offered. The employee row holds `terminationDate` as a date | `employee/Employee.java:41-45`; `EmployeeRequest` carries `terminationDate` |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The helper answers only about the caller. HR must open three screens to answer "who is out today, who joined this month". | `W-114.4` and `W-114.6`/`.7` tools are own-data only |
| 2 | The employee search has a text and a status filter and no date filter. | `code/backend/core/src/main/java/com/infinevo/core/employee/EmployeeController.java:81-87`; `EmployeeQueryService.java:46-60` |

## Scope

**In scope**

- Four read tools: `core.team_on_leave`, `core.joiners`, `core.leavers`, `core.headcount`.
- Three read queries on the employee repository (joined between, left between, count by department or location).

**Out of scope**

- WFH, attendance and timesheet team views (`hrms` has its own tools in `W-114.6`; team versions are a later part).
- Any export, any salary or pay data, any change.
- A manager's view of joiners or headcount.

## Flow

```
"who is on leave today?"
  --> core.team_on_leave { from: today, to: today }
        holds core.leave.read       --> leaveRequestService.searchRequests(tenant, null, APPROVED, from, to, first 50)
        holds core.leave.read_team  --> searchRequestsForEmployees(tenant, [caller + direct reports], APPROVED, …)
        neither                     --> the tool is not offered
  --> rows: name, employee number, department, from, to   (no leave type, no reason)
```

## Backend changes

Paths are under `code/backend/core/src/main/java/com/infinevo/core/`.

| Layer | File | Change |
|---|---|---|
| Tools | `ai/TeamAiToolProvider.java` | **New.** An `AiToolProvider` with the four tools |
| Repository | `employee/EmployeeRepository.java` | **Add** three tenant-scoped reads, all `deleted = false`: joined between two dates; `terminationDate` between two dates; a grouped count by department or by work location for employees whose status is not `TERMINATED` (the count the limit guard uses, `:104`) |
| Service | `employee/EmployeeQueryService.java` | **Add** `joinedBetween`, `leftBetween`, `headcountBy(group)` — thin, clamped to 50 rows, sorted by date |

**The tools**

| Tool | Input | Returns | `requiredAction` |
|---|---|---|---|
| `core.team_on_leave` | `from`, `to` (ISO; default today; range at most 31 days) | Approved leave overlapping the range: name, employee number, department, from, to, half day. **No leave type** — every row reads "On leave" | `core.leave.read_team`, with `requiredAnyOf = [core.leave.read]` (`W-114.3`); the tool picks the wider scope when the caller has it |
| `core.joiners` | `month` (`YYYY-MM`, default this month) | Name, employee number, department, designation, date of joining | `core.employee.read` |
| `core.leavers` | `month` | Name, employee number, department, last working date | `core.employee.read` |
| `core.headcount` | `by` (`department` / `location` / none) | Total active employees, and a count per group with the group's name | `core.employee.read` |

Every row is built by the tool from named fields; it never passes an entity or a response
record through. Ids are dropped, department and designation come back as names. Pending leave is
not listed — "on leave" means approved.

`core.team_on_leave` is one tool for both audiences. `W-114.3`'s `requiredAnyOf` lets the
registry offer it to a caller holding `core.leave.read_team` or `core.leave.read`, so an HR role
that holds `read` without `read_team` does not lose it.

## Frontend changes

None. **Routes added:** none.

## Database changes

None. The three reads use existing columns. `idx_employee_tenant_status` exists
(`employee/Employee.java:87`); the date reads scan one tenant's employees, which is small.

- [x] No new table, no migration, no new index
- [x] No money value
- [x] Stays inside `core`: employees, leave, reporting lines and the helper are all in `core`

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/TeamAiToolProviderTest.java` | Each tool's `requiredAction` equals the screen's guard; no row carries an id, an email, a mobile, a reason or a leave type (the row type has no such field, and the test seeds a leave type named "Maternity" and asserts the word is in no tool result, for an HR caller and for a manager); 51 matches return 50 rows and "1 more"; a 60-day range is refused |
| Integration | `core/src/test/.../ai/TeamAiToolsIT.java` | HR sees every employee on approved leave today; a manager sees only self and direct reports; a manager cannot call `core.joiners`; an employee with only own-data actions is offered none of the four; tenant B never sees tenant A; a soft-deleted employee is in no answer; headcount equals the limit guard's count |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
```

| Check | Expected | Result |
|---|---|---|
| The command | Build success | not run |
| HR: "who is on leave today?" | The same people the Leave requests screen shows for today, approved only | not run |
| Manager with two reports, one on leave | That one report (and the manager, if on leave); nobody else | not run |
| Employee: same question | "I can't see that" | not run |
| HR: "who joined this month?" | Matches the employee list sorted by joining date | not run |
| Manager: "who joined this month?" | "I can't see that" | not run |
| HR: "headcount by department" | Totals add up to the "N of M employees" badge | not run |
| Grep the tool results in the test for `@`, a mobile pattern, `reason` | Not found | not run |
| HR and manager: "who is on sick leave today?" | The helper lists who is on leave and says it cannot see the type of leave; no leave type name appears in the answer or in the stored `TOOL` row | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A tool shows more than the screen does | Low | Same service, same action, and a unit test that pins each tool's action to the controller annotation |
| Third-party names reach the model | Certain, by design | Stated above; limited to callers who already see them; only name, number, department and dates |
| Leave type reveals something private ("Maternity", "Sick") | Closed | Founder 2026-11-23: the tool returns no leave type at all; a test proves it |
| A prompt hidden in an employee's name or a department name | Low | `W-114.4` treats tool results as data; these tools change nothing |

## Rollback

Revert the commit. The four tools are no longer offered. Nothing was written.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` (`employee`) | Three repository reads and the three query-service methods, with tests |
| 2 | `core` (`ai`) | `TeamAiToolProvider`, unit and integration tests |
