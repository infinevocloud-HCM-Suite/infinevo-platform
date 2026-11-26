# Feature: AI helper — explain my payslip and my TDS

| Field | Value |
|---|---|
| **Feature ID** | `W-114.14` · `PAYROLL-AI` |
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
| Externally testable behaviour | The helper says what changed on a user's own payslip since last month, and why where the data proves it | 1 |
| Frontend area | none | 1 |

Size **M**.

All paths in this spec are under `code/backend/payroll/src/main/java/com/infinevo/payroll/`.

**The helper explains; it gives no tax advice.** That sentence is in both tool descriptions.

## What the data can prove

A reason is offered only when a stored fact proves it. Everything else is "not known from the data".

| Reason code | Proven by | Evidence |
|---|---|---|
| `LOSS_OF_PAY` | The line's source is `LOP` and it is not a refund, with this month's `lop_days` and `unpaid_days` | `payrun/LineSource.java`; `payslip/PayslipLineResponse.java:13`; `payslip/PayslipResponse.java:54-58` |
| `LOSS_OF_PAY_REFUNDED` | A `LOP` line whose code ends `_REVERSAL` (`LOP_REVERSAL`, `LOP_BENEFIT_REVERSAL`): loss of pay given back, never a loss this month | `payrun/LopLineContributor.java:34-39` |
| `MONTH_ONLY_ENTRY` | The line's source is `PAY_INPUT`: overtime, a reimbursement, a one-off deduction, a one-time or scheduled payout entered for this month | `payrun/LineSource.java`; `code/backend/core/src/main/java/com/infinevo/core/payinput/PayInputKind.java` |
| `NEW_COMPONENT` | A `STRUCTURE` line whose code was not on last month's payslip | the two payslips |
| `ENDED_COMPONENT` | A `STRUCTURE` line on last month's payslip and not on this one | the two payslips |
| `SALARY_REVISED` | The caller's two pay run rows carry a different `salary_version_id` | `payrun/EmployeePayRun.java:55`; `payrun/EmployeePayRunRepository.java:28-31` |
| `PAID_DAYS_CHANGED` | On the loss-of-pay line only: `payable_days` or `paid_days` differ between the two months. Structure lines are full-month amounts, so days never move them | `payslip/PayslipResponse.java:54-58`; `payrun/StructureLineContributor.java:20`; `payrun/LopLineContributor.java:19` |
| `JOINED_THIS_MONTH` | On the loss-of-pay line only: `date_of_joining` falls inside this payslip's month | `payslip/PayslipResponse.java:43` |
| `FOLLOWS_EARNINGS` | A `STATUTORY` line changed (or is new) and gross earnings changed too | `payslip/PayslipResponse.java:60-65` |
| `TAX_SEE_TDS` | The line's source is `TAX`; the answer is in `payroll.explain_tds` | `tds/TaxLineContributor.java:27-29,41` |
| `FIRST_PAYSLIP` | The caller has no earlier paid payslip | `payslip/PayslipService.java:36` |
| `NOT_KNOWN` | None of the above | — |

**Asked for and dropped, because the data cannot prove it**

| Reason | Why not |
|---|---|
| Arrear | There is no arrear kind. An arrear is entered as a one-time or scheduled payout, so it shows as `MONTH_ONLY_ENTRY` (`PayInputKind.java`) |
| Statutory rate or ceiling change | A statutory line stores an amount, not the rate or ceiling used (`payrun/EmployeePayRunLine.java:46-65`). When it changes with no change in earnings the reason is `NOT_KNOWN` |
| Left part-way through the month | The payslip carries a joining date and no exit date (`payslip/PayslipResponse.java:37-46`). Fewer paid days show as `PAID_DAYS_CHANGED` on the loss-of-pay line |
| Which kind of month-only entry | The payslip line carries the source, not the pay input kind (`payslip/PayslipLineResponse.java:9-14`) |

**TDS: what is stored**

| Fact | Stored? | Evidence |
|---|---|---|
| The year's tax record: regime, source, annual gross, taxable income, annual tax, the month it applies from | Yes | `tds/EmployeeTds.java:49-68` |
| Tax deducted so far this year, and what remains | Yes: the TDS lines of the caller's **paid** payslips this year (founder 2026-11-26: paid runs only; the TDS screen's `yearToDate` also counts computed and approved runs), and the annual tax less that | `tds/EmployeeTdsService.java:35`; `tds/EmployeeTdsController.java:110-121` |
| Each recalculation, with what caused it and the annual tax it gave | Yes — an immutable history | `taxcalc/recalc/TaxComputationRecord.java:45-98`; `taxcalc/recalc/TaxTrigger.java:6-12`; `taxcalc/recalc/MyTaxHistoryController.java:38-41` |
| The rule for one month's TDS | In code: remaining annual tax spread over the remaining months | `tds/TaxLineContributor.java:27-29` |
| The working of one month's TDS line (months left, tax remaining at that moment) | **No.** Only the line's amount is stored | `payrun/EmployeePayRunLine.java:46-65` |

So `payroll.explain_tds` gives the line, the year's record and the recalculations in between.
It does not re-derive the month's figure.

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | `payroll.my_payslip` (`W-114.7`) returns one month's lines. It cannot say what changed or why. | `W-114-7-payroll-read-only-tools.md`, decisions, "Explaining why a figure changed" |
| 2 | The facts that explain a change are stored, each behind an own-data permission. | the two tables above |
| 3 | An employee may already read how their own pay figure was priced. | `payrun/PayRunExplainController.java:35-36`; `payrun/PayFigureExplanationService.java:16-24` |

## Scope

**In scope:** two tools added to `PayrollAiToolProvider`.

**Out of scope:** another employee's pay; a run that is not paid; a tax estimate for a
what-if; advice on which regime or deduction to choose; changing a declaration; a screen.

## The tools

| Tool | Input | Calls | `requiredAction` | Evidence |
|---|---|---|---|---|
| `payroll.explain_payslip` | `period` (`YYYY-MM`; latest paid when left out) | `PayslipService.listOwn` to find this month's and the previous regular payslip, `PayslipService.own` for each, the caller's two rows from `EmployeePayRunRepository.findOwnPaidRuns`, and `PayFigureExplanationService.explain` for the caller's own row | `payroll.payslip.read_own` | `payslip/PayslipController.java:47-48,57-58`; `payslip/PayslipService.java:36,41`; `payrun/PayFigureExplanationService.java:24,52` (an own-data caller may read only their own row) |
| `payroll.explain_tds` | `period` (`YYYY-MM`; latest paid when left out) | the TDS line of that payslip, `EmployeeTdsService.active` for the caller, the TDS lines of the caller's paid payslips this year, `TaxRecalculationService.historyOwn(fy)` | `payroll.tax_declaration.read_own` | `tds/EmployeeTdsController.java:110-121`; `tds/EmployeeTdsService.java:24,35`; `taxcalc/recalc/TaxRecalculationService.java:31` |

**`payroll.explain_payslip` returns**

| Part | Content |
|---|---|
| `period`, `comparedWith` | This month and the previous regular paid month, or `null` |
| `days` | Payable, paid, loss-of-pay and unpaid days for both months |
| `lines` | Every earning, deduction, reimbursement and benefit line of this month: `code`, `name`, `kind`, `amount`, `previousAmount`, `difference`, `reasons[]` |
| `gone` | Lines on last month's payslip that are not on this one, with `ENDED_COMPONENT` or `MONTH_ONLY_ENTRY` |
| `totals` | Gross, deductions, net for both months and the differences |
| `lossOfPay` | When there is any: days, the amount, the divisor and the day basis, from `PayFigureExplanationResponse` (`payrun/PayFigureExplanationResponse.java`) |

**`payroll.explain_tds` returns**

| Part | Content |
|---|---|
| `period`, `tdsThisMonth`, `tdsLastMonth`, `difference` | From the two payslips; `0.00` when there is no TDS line |
| `yearRecord` | Financial year, regime, where it came from (declaration or officer), annual gross, annual taxable income, annual tax, the month it applies from, deducted so far (paid payslips only), remaining. Marked `asItStands: today` with a fixed note: it is today's record, which may be later than the month asked about (founder 2026-11-26) |
| `recalculations` | Each stored recalculation dated after the previous payslip's month end and up to this payslip's month end: date, cause (`DECLARATION_SUBMITTED`, `SALARY_REVISION`, `SALARY_DEFAULT`, `PROOF_VERIFIED`, `OFFICER`), annual tax before and after |
| `rule` | The fixed sentence: "Each month's TDS is the tax still to be deducted for the year, spread over the months left." |
| `noRecord` | `true` with "There is no tax record for this year yet." when `active` is empty |

**Rules**

| # | Rule |
|---|---|
| 1 | No tool takes an employee id or a pay run id. A month is mapped to a run from the caller's own paid list, as `W-114.7` does. |
| 2 | Paid runs only: `listOwn` and `own` already refuse anything else (`payslip/PayslipServiceImpl.java:134,155-157`). |
| 3 | The comparison month is the newest earlier **regular** paid payslip. An off-cycle payslip is never the comparison. When the payslip asked about is itself off-cycle (it has no structure, loss-of-pay or tax lines), it is not compared at all: `offCycle: true` and a fixed note. `payroll.explain_tds` on a named off-cycle month answers that an off-cycle payslip deducts no TDS; with no month named it explains the newest regular payslip. |
| 3a | Recalculations are put in order by the instant they were made, so two on one day keep their before and after. |
| 3b | `payroll.explain_tds` reads payslips, so it also needs `payroll.payslip.read_own`; without it, it answers as if there were no payslip. |
| 4 | Every amount is a `BigDecimal`, subtracted as `BigDecimal`, and written through `PayrollAiToolResults` as two-decimal text with `"currency": "INR"` once per result. No `double`, no `float`. |
| 5 | Differences are worked out by the tool. The model is told in the description to quote them, not to compute. |
| 6 | `reasons` holds codes from the table above and nothing free-text. A line with no proven reason gets `NOT_KNOWN`; the tool never guesses. |
| 7 | No result carries a database id, and no salary version id: the tool only says whether the two differ. |
| 8 | PAN, UAN and bank details on the payslip are not copied into these results at all. |
| 9 | `tools()` stays empty unless the customer holds Payroll (`W-114.7` rule 3). |
| 10 | The financial year of a month is April to March, from the payslip's period. |

## Backend changes

| Layer | File | Change |
|---|---|---|
| Tools | `ai/PayrollAiToolProvider.java` | Adds the two tools; injects `EmployeePayRunRepository`, `PayFigureExplanationService`, `EmployeeTdsService`, `TaxRecalculationService` |
| Logic | `ai/PayslipComparison.java` | **New.** Pure function of two `PayslipResponse`s and the two salary version ids → lines, `gone`, totals and reason codes |
| Logic | `ai/TdsExplanation.java` | **New.** Pure function of the two TDS amounts, the year record, year-to-date and the history list → the result above |
| Mapping | `ai/PayrollAiToolResults.java` | Adds the two result shapes; still the one place amounts become text |

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
| Unit | `payroll/src/test/.../ai/PayslipComparisonTest.java` | One case per reason code in the table; a statutory line that moved with unchanged gross is `NOT_KNOWN`; an off-cycle payslip is skipped as the comparison; first payslip gives `FIRST_PAYSLIP` and null previous amounts; `45000.00 − 43500.00` is `"1500.00"` |
| Unit | `payroll/src/test/.../ai/TdsExplanationTest.java` | Only recalculations inside the window are listed; before and after annual tax come from consecutive records; no active record gives `noRecord`; no TDS line gives `"0.00"`; the April payslip uses the new financial year and the March one the old |
| Unit | `payroll/src/test/.../ai/PayrollAiToolProviderTest.java` (extend) | Each new tool's `requiredAction` equals its controller's action; neither is offered without Payroll |
| Integration | `payroll/src/test/.../ai/PayrollExplainToolsIT.java` (in `payroll`, beside `PayrollAiToolsIT`, as `W-114.7` did) | Employee A with loss of pay in October and none in September gets `LOSS_OF_PAY` on the LOP line with the days; a salary revision between the months gives `SALARY_REVISED`; employee B's figures never appear; an APPROVED run is not found; the stored TOOL row holds no id, no PAN and no bank number |

## Verification

```bash
cd code/backend && ./mvnw -B -pl payroll,app -am verify
```

| Check | Expected | Result |
|---|---|---|
| Ask "why is my October pay lower?" (stub scripted to call `payroll.explain_payslip` with `2026-10`) | TOOL row lists each line with last month's amount, the difference and reason codes; totals match the two payslip screens | not run |
| Ask "why did my TDS change?" (stub calls `payroll.explain_tds`) | TOOL row holds both months' TDS, the year record and any recalculation in between with its cause | not run |
| A month with no paid payslip | "No payslip for that month yet." | `PayrollExplainToolsIT`: an APPROVED month answers this |
| A user with no tax record for the year | `noRecord: true` and the plain sentence | not run |
| A login without `payroll.tax_declaration.read_own` | `payroll.explain_tds` is not offered | `PayrollAiToolProviderTest.explainTools_gated` |
| Both tool descriptions | Contain "The helper explains; it gives no tax advice." | `PayrollAiToolProviderTest.explainTools_shape` |
| `grep -rnE "double|float" code/backend/payroll/src/main/java/com/infinevo/payroll/ai` | No match | no match (2026-11-26) |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The model states a cause the data did not prove | Medium | Reasons are codes from a closed list; `NOT_KNOWN` is explicit; the system prompt already says to answer only from tool results |
| A recalculation is listed against the wrong month | Medium | The window is by calendar month, not by when the run was computed, which the payslip does not carry. The result says "recalculated between these two payslips", not "this caused it" |
| Users read an explanation as tax advice | Medium | The sentence in both descriptions; the tools offer no what-if and no recommendation |
| A statutory change is reported as "not known" | Certain | Stated above; proving it needs the rate stored on the line, which is a payroll ticket, not this one |

## Rollback

Revert the commit. `payroll.my_payslip` and `payroll.my_tax_summary` (`W-114.7`) still answer.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `payroll` | `PayslipComparison`, `TdsExplanation`, their unit tests |
| 2 | `payroll` | The two tools in `PayrollAiToolProvider`, the result shapes |
| 3 | `app` (test only) | `PayrollExplainToolsIT` |
