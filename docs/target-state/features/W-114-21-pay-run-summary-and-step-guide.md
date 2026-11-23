# Feature: AI helper — pay run summary and next-step guide (read-only)

| Field | Value |
|---|---|
| **Feature ID** | `W-114.21` · `PAYROLL-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/payroll` (`ai`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.7` on `main` (`PayrollAiToolProvider`, `PayrollAiToolResults`) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A payroll officer gets a month's pay run totals, what is unusual in it, and the next step | 1 |
| Frontend area | none | 1 |

Size **M**.

All paths in this spec are under `code/backend/payroll/src/main/java/com/infinevo/payroll/`.

## Founder decision (2026-11-23)

**The helper summarises a pay run and says what to do next. It never creates, computes,
locks, approves, pays or cancels a run.**

| Consequence | How it is held |
|---|---|
| No action for a pay run exists in the helper | This part adds no `AiActionExecutor`, no propose tool and no intent with a pay run action code. A test fails the build if one appears |
| The officer presses every button | `payroll.payrun_next_step` answers in words and names the Payroll runs screen |
| Later parts may not add one quietly | The rule is a founder decision; changing it needs a new one |

## Decisions (this spec)

| Point | Choice | Why |
|---|---|---|
| Which run | The **regular** run of the month asked for | Off-cycle runs are many per month (`payrun/PayRunRepository.java:22-24`); a summary of "the month" means the regular one. The result gives the count of off-cycle runs in that month and no more |
| When there are figures | Status `COMPUTED`, `APPROVED` or `PAID` | Before that the totals are zero (`payrun/PayRunStatus.java:31-37`) |
| "New joiners" and "leavers" | Reported as **"in this run, not in last month's"** and **"in last month's run, not in this one"** | A run row holds no joining or exit date (`payrun/EmployeePayRunResponse.java:17-36`). Membership of the two runs is what the data proves |
| "Large change" threshold | Net pay moved by **20% or more and by at least ₹1,000** against last month | Both, so a small salary does not flood the list. Two constants in one place |
| Employer cost | Sum of `total_benefits` over the included rows, named "employer contributions" | The run stores gross, deductions and net only (`payrun/PayRunResponse.java:28-30`); the per-employee row stores benefits (`payrun/EmployeePayRun.java:72`) |
| Missing bank details | **Knowable**: a skipped row carries `NO_BANK_DETAILS` or `NO_SALARY` | `payrun/SkipReason.java`; `payrun/PayRunInclusionServiceImpl.java:90-93` |
| Screen path returned | `/payroll/runs`, the list — never `/payroll/runs/{id}` | The model does not receive a database key; `W-114.13` links only exact feed paths (`navigation/PayrollNavigation.java:25-31`; `code/frontend/src/payroll/index.js:87-89`) |
| Run size limit for the lists | Up to 5,000 rows are read, 200 at a time; above that the totals are returned and the lists say "too many rows to list" | Keeps one request bounded |

## Other employees' names go to the AI model here

| Fact | Detail |
|---|---|
| What is sent | For each exception row: employee name, employee number and one figure. Nothing else about the person |
| Who can cause it | Only a caller holding `payroll.run.read`, who can already open the run and its export (`payrun/PayRunController.java:84-104`) |
| What is never sent | PAN, Aadhaar, UAN, bank account, any id, any line detail of another employee |
| Bound | Six lists, 20 rows each at most |
| Stored | The TOOL row is stored masked like any other (`W-114.4`) and deleted after 30 days (`W-114.10`) |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Before approving, an officer compares this run with last month by eye, across two screens. | the run page shows one run: `code/frontend/src/payroll/index.js:89` |
| 2 | The totals and the per-employee rows are stored and readable behind one action. | `payrun/PayRunResponse.java:26-32`; `payrun/EmployeePayRunResponse.java:20-36`; `payrun/PayRunController.java:69-70,84-85,90-91` |
| 3 | The allowed moves between statuses are a fixed table in code. | `payrun/PayRunStatus.java:31-37` |

## Scope

**In scope:** two read-only tools added to `PayrollAiToolProvider`.

**Out of scope:** every change to a run; an off-cycle run's detail; one employee's pay
lines (the officer opens the run); bank files; statutory returns; a screen.

## The tools

| Tool | Input | Calls | `requiredAction` | Evidence |
|---|---|---|---|---|
| `payroll.payrun_summary` | `period` (`YYYY-MM`; the newest regular run when left out) | `PayRunService.list(null, REGULAR, …)` to find this month's and the previous month's run; `PayRunService.employees(id, null, …)` for both | `payroll.run.read` | `payrun/PayRunService.java:61,64`; `payrun/PayRunController.java:69-70,90-91` |
| `payroll.payrun_next_step` | `period` (`YYYY-MM`; the current month when left out) | `PayRunService.list(null, REGULAR, …)`; `PermissionService.holds` for the action the next step needs | `payroll.run.read` | `payrun/PayRunController.java:69-70`; `code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionService.java:102` |

**`payroll.payrun_summary` returns**

| Part | Content |
|---|---|
| `period`, `status`, `computedAt`, `comparedWith` | This run and the previous month's regular run, or `null` |
| `totals` | Included, skipped, total gross, total deductions, total net pay, employer contributions — for both months, with the differences |
| `offCycleRunsThisMonth` | A count |
| `inThisRunNotLast` | Up to 20: name, number, net pay |
| `inLastRunNotThis` | Up to 20: name, number, last month's net pay |
| `zeroOrNegativeNet` | Up to 20: name, number, net pay. The run's own `negative_net_count` is given beside it |
| `largeChange` | Up to 20, largest first: name, number, last net, this net, difference |
| `skipped` | Up to 20: name, number, `NO_SALARY` or `NO_BANK_DETAILS` |
| `needsAttention` | Up to 20: name, number, and either "unpriced inputs: n" or "computation error" (the error text is not sent) |
| each list | carries `total`, so "20 of 37" can be said |

A run that is `DRAFT`, `LOCKED`, `COMPUTING` or `FAILED` returns the status and
"This run has no figures yet." A month with no regular run returns "There is no pay run for
that month."

**`payroll.payrun_next_step` returns**

| Status | `nextStep` | Action it needs |
|---|---|---|
| no run | "Create the run for this month on Payroll runs." | `payroll.run.execute` |
| `DRAFT` | "Check the month's inputs, then lock the run." | `payroll.run.execute` |
| `LOCKED` | "Compute the run." | `payroll.run.execute` |
| `COMPUTING` | "The run is being computed. Wait for it to finish." (with done and total) | — |
| `FAILED` | "The compute failed. Read the reason on the run, fix it, then compute again." | `payroll.run.execute` |
| `COMPUTED` | "Review the figures. Approve the run, or compute again after a correction." | `payroll.run.approve` |
| `APPROVED` | "Record the payment to release the payslips." | `payroll.payslip.publish` |
| `PAID` | "Nothing is left to do. Payslips are released." | — |
| `CANCELLED` | "This run was cancelled. It cannot be reopened." | — |

Each row follows the allowed moves in `payrun/PayRunStatus.java:31-37` and the action on its
endpoint (`payrun/PayRunController.java:61-62,158-159,170-171,198-199,204-205`). The result
also carries `youCanDoThis` (true or false, from `PermissionService.holds`) and
`screenPath: "/payroll/runs"`.

**Rules**

| # | Rule |
|---|---|
| 1 | No tool takes a pay run id or an employee id, and no result carries one. Rows are matched between the two runs by employee id inside the provider; the id is dropped before the result is built. |
| 2 | Every amount is a `BigDecimal`; sums and differences are `BigDecimal`; the percentage test is done with `BigDecimal.compareTo`, never a division into a floating-point number. Amounts leave through `PayrollAiToolResults` as two-decimal text with `"currency": "INR"` once. |
| 3 | The tool works out every total, difference and list. The description tells the model to quote them, not to compute. |
| 4 | `tools()` stays empty unless the customer holds Payroll (`W-114.7` rule 3). |
| 5 | Neither tool calls `create`, `lock`, `compute`, `approve`, `pay`, `cancel`, `addInputs` or `refreshScheduledEarnings` on `PayRunService` (`payrun/PayRunService.java:23-121`). A test asserts it. |
| 6 | The failure reason and a row's computation error are not sent to the model: they are free text that may quote data. The result says only that one exists. |

## Backend changes

| Layer | File | Change |
|---|---|---|
| Tools | `ai/PayrollAiToolProvider.java` | Adds the two tools; injects `PayRunService` and `PermissionService` |
| Logic | `ai/PayRunSummary.java` | **New.** Pure function of two `PayRunResponse`s and two row lists → totals, differences and the six capped lists. Holds the two threshold constants |
| Logic | `ai/PayRunNextStep.java` | **New.** The status table above |
| Mapping | `ai/PayrollAiToolResults.java` | Adds the two result shapes |

**API contract:** none added. **Routes added:** none.

## Database changes

None.

- [x] Creates no table, so no new `tenant_id` or policy
- [x] No Flyway script; `ddl-auto` untouched
- [x] Money stays `BigDecimal`; no `double` or `float` is introduced
- [x] No new index
- [x] Nothing dropped or renamed
- [x] `payroll` references only `core` and `shared`

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/src/test/.../ai/PayRunSummaryTest.java` | Totals and differences; employer contributions is the sum of row benefits; an employee in this run only, and one in last run only, land in the right list; net `0.00` and `-250.00` are listed; a move of 25% and ₹5,000 is listed, 25% and ₹400 is not, 5% and ₹5,000 is not; a list of 37 returns 20 with `total: 37`; no UUID in the result; a row's error text is absent |
| Unit | `payroll/src/test/.../ai/PayRunNextStepTest.java` | One case per status; every status in `PayRunStatus` has a row (the test fails when a status is added); `youCanDoThis` false when the caller lacks the action |
| Unit | `payroll/src/test/.../ai/PayrollAiToolProviderTest.java` (extend) | Both tools need `payroll.run.read`; a mock `PayRunService` records that only `list`, `get` and `employees` were called |
| Integration | `app/src/test/.../ai/NoPayRunActionIT.java` | In the whole application context: no `AiActionExecutor` bean has an action code starting `payroll.`, and the only `AiTool` names starting `payroll.payrun_` are these two read tools. It lives in `app` because the executors of other modules are only visible there |
| Integration | `app/src/test/.../ai/PayRunSummaryToolIT.java` | A payroll officer gets the summary of a computed run, equal to the run page's totals; an employee without `payroll.run.read` is not offered either tool; a customer without Payroll is offered none; the stored TOOL row holds names and numbers but no id, PAN or bank number; the run's status is unchanged after both tools ran |

## Verification

```bash
cd code/backend && ./mvnw -B -pl payroll,app -am verify
```

| Check | Expected | Result |
|---|---|---|
| As payroll officer, ask "summarise November's pay run" on a computed run | TOOL row holds totals for November and October, the differences and the six lists; totals equal the run page | not run |
| Same on a `DRAFT` run | "This run has no figures yet." and the status | not run |
| Ask "what do I do next on this run?" on a `COMPUTED` run, as an officer without `payroll.run.approve` | The approve step in words, `youCanDoThis: false`, `/payroll/runs` | not run |
| Ask the helper to "approve the run" | It answers that it cannot and gives the next step; the run stays `COMPUTED` | not run |
| As an employee | Neither tool is in the list sent to the model | not run |
| Stored TOOL row | No id, no PAN, no bank account, no error text | not run |
| `grep -rnE "double|float" code/backend/payroll/src/main/java/com/infinevo/payroll/ai` | No match | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Other employees' names and pay figures reach the model | Certain, by design | Limited to holders of `payroll.run.read`; capped lists; no identifiers; stated in the section above so the founder decides with it in view |
| "Not in this run" is read as "has left" | Medium | The list names say what was compared; the description tells the model to use those words |
| The officer trusts the summary and skips the review | Medium | The summary lists what is unusual, not that the rest is right; `nextStep` for `COMPUTED` starts with "Review the figures" |
| A very large run makes the request slow | Low | 5,000-row limit, 200 a page, both runs read once |
| Someone later adds an action for pay runs | Low | `NoPayRunActionIT` and the founder decision at the top |

## Rollback

Revert the commit. The helper loses the two tools; no run is touched, because none ever was.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `payroll` | `PayRunSummary`, `PayRunNextStep`, their unit tests |
| 2 | `payroll` | The two tools in `PayrollAiToolProvider`, the result shapes |
| 3 | `app` (test only) | `PayRunSummaryToolIT`, `NoPayRunActionIT` |
