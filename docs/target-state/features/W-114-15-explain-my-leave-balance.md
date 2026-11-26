# Feature: AI helper — explain my leave balance

| Field | Value |
|---|---|
| **Feature ID** | `W-114.15` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.4` on `main` (`CoreAiToolProvider`, the tool loop) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The helper breaks a user's own leave balance into its parts | 1 |
| Frontend area | none | 1 |

Size **S**.

All paths in this spec are under `code/backend/core/src/main/java/com/infinevo/core/`.

## What the data holds

The brief asked for opening, accrued, used, pending, carried forward, lapsed and the balance.
This is what exists.

| Asked for | In the data? | Where |
|---|---|---|
| Granted for the year | Yes — `entitlementDays` | `leave/LeaveAllocation.java:58` |
| Accrued so far | Yes — `accruedDays`, with `lastAccruedOn` | `leave/LeaveAllocation.java:61,73` |
| Carried forward | Yes — `carriedForwardDays`, already cut to what still counts after its expiry date | `leave/LeaveAllocation.java:64,67`; `leave/LeaveBalanceServiceImpl.java:114-117` |
| Used | Yes — the sum of `core.leave_consumption` for the allocation | `leave/LeaveBalanceServiceImpl.java:105-112` |
| Pending requests | Yes — pending requests inside the leave year | `leave/LeaveBalanceServiceImpl.java:122-141` |
| Balance | Yes — `remainingDays` and `availableDays` | `leave/LeaveBalanceResponse.java:14-29` |
| Leave year dates | Yes — `leaveYear`, `yearStartDate`, `yearEndDate` | `leave/LeaveAllocation.java:49-55` |
| Part-year share | Yes — `proRateFactor` | `leave/LeaveAllocation.java:70` |
| **Opening balance** | **No.** There is no opening figure; the year starts with the grant and the carry-forward | — |
| **Days lapsed** | **No.** Only the carry-forward expiry date and an `expired` flag are held; the number of days lost is not stored | `leave/LeaveBalanceResponse.java:25-27` |

So the tool returns the first eight and says nothing about the last two.

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | `core.my_leave_balances` (`W-114.4`) returns the figures the balance screen shows. It does not say how they add up, when the year ends, or when a carry-forward runs out. | `W-114-4-model-gateway-masking-and-core-answers.md`, "Core tools" |
| 2 | The balance is a formula the user never sees: grant + accrued + carried forward − used. | `leave/LeaveBalanceServiceImpl.java:119-120` |

## Scope

**In scope:** one tool, `core.explain_leave_balance`, in its own `LeaveBalanceAiToolProvider` beside `CoreAiToolProvider`.

**Out of scope:** another employee's balance; the list of each day taken (`core.my_leave_requests`
has it); how many days were lost at a reset; changing anything.

## The tool

| Tool | Input | Calls | `requiredAction` | Evidence |
|---|---|---|---|---|
| `core.explain_leave_balance` | `leaveType` (optional; a code or a name), `asOf` (optional date, default today) | `LeaveBalanceService.getBalancesForEmployee(tenantId, caller.id(), asOf)`, and the caller's own allocations through `LeaveAllocationRepository.findByTenantIdAndEmployeeId` for the year dates | `core.leave.read_own` | `leave/LeaveBalanceService.java:18`; `leave/LeaveAllocationController.java:100-110`; `leave/LeaveAllocationRepository.java:32` |

**Result, one entry per leave type**

| Field | From |
|---|---|
| `leaveType` (code and name) | `LeaveBalanceResponse.leaveTypeCode`, `leaveTypeName` |
| `leaveYear`, `yearStart`, `yearEnd` | the allocation |
| `granted`, `accrued`, `carriedForward`, `used`, `balance`, `pending`, `available` | `entitlementDays`, `accruedDays`, `carriedForwardDays`, `consumedDays`, `remainingDays`, `pendingDays`, `availableDays` |
| `carryForwardExpiresOn` | the same response |
| `asOf` | the day read (today when not given) |
| `carryForwardExpired` | `asOf` is after `carryForwardExpiresOn` — the day the carried figure stops counting (`leave/LeaveDateUtils.java:130`) |
| `leaveTypeEnded` | the response's `expired`, which is about the leave type (switched off or past Valid To, `leave/LeaveBalanceServiceImpl.java:154`), not the carry-forward. Renamed so the model does not read it as the carry-forward running out |
| `proRateFactor`, `lastAccruedOn` | the allocation; left out when null |
| `howItAddsUp` | one fixed sentence built by the tool: "balance = granted + accrued + carried forward − used; available = balance − pending, never below zero" with the numbers filled in (`leave/LeaveBalanceServiceImpl.java:120,143-145`) |

**Rules**

| # | Rule |
|---|---|
| 1 | The tool takes no employee id. The caller is `EmployeeService.currentEmployee()`, as the controller does (`leave/LeaveAllocationController.java:106-108`). A login with no employee record gets "No employee record is linked to your login." |
| 2 | `leaveType` is matched on code first, then on name, ignoring case. No match answers "You have no allocation for that leave type." with the list of types the caller does have. |
| 3 | Days are `BigDecimal`, written with two decimals as text. No `double`, no `float`. |
| 4 | No result carries an id: not the employee's, not the leave type's, not the allocation's, not the policy's. |
| 5 | `howItAddsUp` is written by the tool from the service's figures, so the model does not do the sum. Every number in it is the service's own figure; the tool adds nothing up itself. |

## Backend changes

| Layer | File | Change |
|---|---|---|
| Tools | `ai/LeaveBalanceAiToolProvider.java` | **New.** `core.explain_leave_balance`, a provider of its own so `CoreAiToolProvider`'s constructor does not grow |
| Mapping | `ai/LeaveBalanceExplanation.java` | **New.** Builds the id-free result and the `howItAddsUp` sentence |

**API contract:** none added. **Routes added:** none.

## Database changes

None.

- [x] Creates no table, so no new `tenant_id` or policy
- [x] No Flyway script; `ddl-auto` untouched
- [x] No money; day counts stay `BigDecimal`
- [x] No new index
- [x] Nothing dropped or renamed
- [x] `core` only

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/LeaveBalanceExplanationTest.java` | Sentence for granted 12, accrued 0, carried 3, used 4.5, pending 1 reads balance 10.50, available 9.50; a null pro-rate factor is left out; no UUID in the result; `4.5` is `"4.50"` |
| Unit | `core/src/test/.../ai/LeaveBalanceExplanationTest.java` (same file) | `requiredAction` is `core.leave.read_own`; match by code, then name; unknown type answers with the caller's types; no employee record answers the plain sentence |
| Integration | `core/src/test/.../ai/LeaveBalanceToolIT.java` (new; `app` has no AI tools IT) | Employee A gets A's figures, equal to `GET /api/v1/me/leave-balances`; employee B's allocation never appears; an expired carry-forward shows `carryForwardExpired: true` and a carried figure of `0.00`; a login without `core.leave.read_own` is not offered the tool |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core,app -am verify
```

| Check | Expected | Result |
|---|---|---|
| Ask "why is my casual leave balance 10.5?" (stub scripted to call the tool with `CL`) | TOOL row holds the parts and the sentence; figures match the Leave screen | not run |
| Ask about a type the user has no allocation for | The plain sentence and the user's own types | not run |
| Stored TOOL row | No id of any kind | not run |
| A login without `core.leave.read_own` | The tool is not offered | not run |
| `grep -nE "double|float" code/backend/core/src/main/java/com/infinevo/core/ai/LeaveBalanceExplanation.java` | No match | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Users ask "how many days did I lose" and the data cannot say | Medium | The result carries the expiry date and the flag; the tool description says the lost amount is not recorded |
| Overlap with `core.my_leave_balances` confuses the model | Low | The descriptions differ: one is "the figures", this one is "how the figure is made up" |

## Rollback

Revert the commit. `core.my_leave_balances` still answers the plain question.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `core` | The tool, `LeaveBalanceExplanation`, unit tests |
| 2 | `app` (test only) | The extended integration test |
