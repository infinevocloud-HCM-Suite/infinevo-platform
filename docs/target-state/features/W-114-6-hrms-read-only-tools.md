# Feature: AI helper — HRMS read-only tools

| Field | Value |
|---|---|
| **Feature ID** | `W-114.6` · `HRMS-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/hrms` (new `ai` package) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.4` on `main` (the tool loop and `AiToolProvider` in use) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The helper answers a user's questions about their own attendance and timesheets | 1 |
| Frontend area | none | 1 |

Size **S**.

## Decisions (this spec)

| Point | Choice | Why |
|---|---|---|
| WFH tools | Not here. They are in `W-114.4` | WFH lives in `core`: `code/backend/core/src/main/java/com/infinevo/core/wfh/MyWfhController.java:30` |
| HRMS not bought by the customer | No HRMS tool is offered | The module check sits on the controllers (`ClockController.java:29`), and a tool calls the service, not the controller. So the provider must check the module itself |
| Whose data | The caller's only | A team or company-wide answer is `W-114.19` |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The helper (`W-114.4`) can answer from Core only. It knows nothing about attendance or timesheets. | `W-114.4` "Core tools" |
| 2 | The own-data reads already exist, each behind a permission. | `ClockController.java:50-62`, `RegularizationController.java:49-55`, `portal/MyTimesheetController.java:43-51`, `timesheet/TimesheetController.java:93-101` |

All paths in this spec are under `code/backend/hrms/src/main/java/com/infinevo/hrms/`.

## Scope

**In scope:** one `AiToolProvider` in `hrms` with the five tools below.

**Out of scope:** clock in or out, submitting a timesheet or a regularization, overtime,
anyone else's records, any screen.

## Flow

```
W-114.4 loop --> AiToolRegistry --> HrmsAiToolProvider.tools()
                                      customer holds HRMS?  no --> empty list
                                      yes --> five tools, each with its requiredAction
model asks for hrms.my_attendance_sessions --> core checks the action --> ClockService.mySessions(from, to)
                                      --> JSON without ids --> core masks it --> model
```

## The tools

| Tool | Input | Calls | `requiredAction` | Evidence |
|---|---|---|---|---|
| `hrms.my_attendance_today` | — | `ClockService.today()` | `hrms.attendance.mark` | `attendance/ClockService.java:34`, `ClockController.java:50-51` |
| `hrms.my_attendance_sessions` | `from`, `to` (dates) | `ClockService.mySessions(from, to)` | `core.attendance.read_own` | `attendance/ClockService.java:41`, `ClockController.java:56-57` |
| `hrms.my_regularizations` | `from`, `to` (dates) | `regularizationService.mine(from, to)` | `core.attendance.read_own` | `attendance/RegularizationController.java:49-55` |
| `hrms.my_timesheet_week` | `weekStart` (optional Monday) | `TimesheetService.forWeek(weekStart)` | `hrms.timesheet.read_own` | `portal/MyTimesheetController.java:43-51` |
| `hrms.my_timesheets` | `from`, `to`, `status` (all optional) | `TimesheetService.listMine(from, to, status, null)` | `hrms.timesheet.read_own` | `timesheet/TimesheetController.java:93-101`, `TimesheetServiceImpl.java:37` |

**Rules**

| # | Rule |
|---|---|
| 1 | No tool takes an employee id. Each service already resolves the caller itself. |
| 2 | `from` and `to` default to the last 30 days. A range over 93 days is refused with a plain sentence the model can pass on, because `ClockService.MAX_RANGE_DAYS = 93` (`attendance/ClockService.java:13`). |
| 3 | `tools()` returns an empty list unless `EntitlementService.holds(PlatformModule.HRMS)` (`code/backend/shared/src/main/java/com/infinevo/shared/entitlement/EntitlementService.java:46`). |
| 4 | Results are JSON with database ids removed. Times are sent as ISO text; hours as plain decimal text. |
| 5 | A service exception becomes an `AiToolResult` error with one plain sentence. It never reaches the user as a stack trace or a `500`. |
| 6 | Masking is not done here. `W-114.4` masks every tool result. |

## Backend changes

| Layer | File | Change |
|---|---|---|
| Tools | `ai/HrmsAiToolProvider.java` | **New.** `@Component implements AiToolProvider`; injects `ClockService`, the regularization service, `TimesheetService`, `EntitlementService` |
| Mapping | `ai/HrmsAiToolResults.java` | **New.** Turns each response into the id-free JSON |

**API contract:** none added. The tools are reached through `POST /api/v1/ai/sessions/{id}/messages` (`W-114.4`).

**Routes added:** none.

## Database changes

None.

- [x] Creates no table, so no new `tenant_id` or policy
- [x] No Flyway script; `ddl-auto` untouched
- [x] No money field
- [x] No new index
- [x] Nothing dropped or renamed
- [x] `hrms` references only `core` and `shared`: `AiToolProvider` is in `core/ai`

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `hrms/src/test/.../ai/HrmsAiToolProviderTest.java` | Five tools when HRMS is held, none when it is not; each tool's `requiredAction` equals the action on its controller; each calls its service with the given dates; a 120-day range is refused; a service exception becomes an error result; no result contains a UUID |
| Integration | `app/src/test/.../ai/HrmsAiToolsIT.java` | With the stub model scripted to call `hrms.my_attendance_sessions`: employee A gets only A's sessions; a login without `core.attendance.read_own` is not offered the tool; a customer without HRMS is offered none |

## Verification

```bash
cd code/backend && ./mvnw -B -pl hrms,app -am verify
```

| Check | Expected | Result |
|---|---|---|
| App starts | Log lists five HRMS tools registered | not run |
| Ask "what time did I clock in today?" (stub scripted to call `hrms.my_attendance_today`) | TOOL row holds today's state for the caller only | not run |
| Same as an employee of a customer without HRMS | The tool list sent to the model has no HRMS tool | not run |
| Same as a login without `hrms.timesheet.read_own`, asking about a timesheet | `hrms.my_timesheet_week` and `hrms.my_timesheets` are not offered | not run |
| `hrms.my_attendance_sessions` with a 120-day range | Error result: "Ask for 93 days or fewer." | not run |
| Any tool result in the stored TOOL row | No database id | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A new HRMS controller permission changes and the tool's `requiredAction` drifts from it | Low | The unit test compares each tool's action with the annotation on its controller method |
| A long range returns too many rows | Low | 93-day limit here; `W-114.4` cuts a result at 8,000 characters |

## Rollback

Revert the commit. The helper loses its HRMS answers; nothing else changes.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `hrms` | `HrmsAiToolProvider`, `HrmsAiToolResults`, unit test |
| 2 | `app` (test only) | `HrmsAiToolsIT` |
