# Feature: AI helper — Payroll read-only tools

| Field | Value |
|---|---|
| **Feature ID** | `W-114.7` · `PAYROLL-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/payroll` (new `ai` package) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.4` on `main` (the tool loop and `AiToolProvider` in use) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The helper answers a user's questions about their own payslips and tax declaration | 1 |
| Frontend area | none | 1 |

Size **S**.

## Decisions (this spec)

| Point | Choice | Why |
|---|---|---|
| "My salary structure" tool | **Dropped from this part** | No own-data permission for salary exists. The salary endpoints need `payroll.salary.read` or `payroll.salary.manage`, which are officer actions (`salary/EmployeeSalaryController.java:47-98`). A new action would need a migration and breaks size S. An employee's components are answered from the earning lines of their latest payslip |
| Which payslips | Paid runs only | That is what the employee's own screen shows: `payslip/PayslipServiceImpl.java:134`, `:155-157` |
| Payroll not bought by the customer | No Payroll tool is offered | The module check sits on the controller (`payslip/PayslipController.java:27`); a tool calls the service, so the provider checks the module itself |
| Explaining *why* a figure changed | Not here | `W-114.14` |

All paths in this spec are under `code/backend/payroll/src/main/java/com/infinevo/payroll/`.

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The helper cannot see a payslip or a tax declaration. | `W-114.4` "Core tools" |
| 2 | The own-data reads exist, each behind a permission. | `payslip/PayslipController.java:47-59`, `taxdeclaration/MyTaxDeclarationController.java:46-50`, `taxdeclaration/summary/MyTaxSummaryController.java:41-45` |

## Scope

**In scope:** one `AiToolProvider` in `payroll` with the four tools below.

**Out of scope:** a draft, computed or approved run; another employee's pay; a whole pay
run (`W-114.21`); changing a declaration; the payslip file or link; any screen.

## Flow

```
W-114.4 loop --> AiToolRegistry --> PayrollAiToolProvider.tools()
                                      customer holds PAYROLL?  no --> empty list
                                      yes --> four tools, each with its requiredAction
model asks for payroll.my_payslip { period: "2026-10" } --> core checks the action
        --> provider finds that period among the caller's own paid payslips --> PayslipService.own(payrunId)
        --> JSON, amounts as text, no ids --> core masks it --> model
```

## The tools

| Tool | Input | Calls | `requiredAction` | Evidence |
|---|---|---|---|---|
| `payroll.my_payslips` | — | `PayslipService.listOwn(first 12)` | `payroll.payslip.read_own` | `payslip/PayslipService.java:36`, `PayslipController.java:47-49` |
| `payroll.my_payslip` | `period` (`YYYY-MM`; latest when left out) | `PayslipService.own(payrunId)` | `payroll.payslip.read_own` | `payslip/PayslipService.java:41`, `PayslipController.java:57-59` |
| `payroll.my_tax_declaration` | `financialYear` (current when left out) | `TaxDeclarationService.readOwn(fy)` | `payroll.tax_declaration.read_own` | `taxdeclaration/MyTaxDeclarationController.java:46-50` |
| `payroll.my_tax_summary` | `financialYear` (current when left out) | `TaxSummaryService.summaryOwn(fy)` | `payroll.tax_declaration.read_own` | `taxdeclaration/summary/MyTaxSummaryController.java:41-45` |

**Rules**

| # | Rule |
|---|---|
| 1 | No tool takes an employee id or a pay run id. `payroll.my_payslip` takes a month; the provider maps it to a run id from the caller's own list (`PayslipSummaryResponse.period`, `payslip/PayslipSummaryResponse.java:11-15`). A month with no paid payslip answers "No payslip for that month yet." |
| 2 | **Money never becomes a floating-point number.** Every amount is a `BigDecimal` in the service response (`payslip/PayslipLineResponse.java:9-14`) and is written with `setScale(2).toPlainString()` as text, for example `"45000.00"`, with `"currency": "INR"` once per result. The mapper has no `double` and no `float`. |
| 3 | `tools()` returns an empty list unless `EntitlementService.holds(PlatformModule.PAYROLL)` (`code/backend/shared/src/main/java/com/infinevo/shared/entitlement/EntitlementService.java:46`). |
| 4 | The financial year is April to March; "current" is worked out from today's date in the provider. |
| 5 | Results carry no database id. A payslip result carries: period, paid on, paid days, each earning, deduction and reimbursement line (code, name, amount, taxable), gross, total deductions, net pay. |
| 6 | PAN, UAN, bank account and IFSC on a payslip are left in the result as the service returns them. `W-114.4` hides them by field name and by pattern before anything is stored or sent. The integration test proves it. |
| 7 | A service exception becomes an `AiToolResult` error with one plain sentence. |

## Backend changes

| Layer | File | Change |
|---|---|---|
| Tools | `ai/PayrollAiToolProvider.java` | **New.** `@Component implements AiToolProvider`; injects `PayslipService`, `TaxDeclarationService`, `TaxSummaryService`, `EntitlementService` |
| Mapping | `ai/PayrollAiToolResults.java` | **New.** Id-free JSON; the one place amounts become text |

**API contract:** none added. The tools are reached through `POST /api/v1/ai/sessions/{id}/messages` (`W-114.4`).

**Routes added:** none.

## Database changes

None.

- [x] Creates no table, so no new `tenant_id` or policy
- [x] No Flyway script; `ddl-auto` untouched
- [x] Money stays `BigDecimal`; no `double` or `float` is introduced
- [x] No new index
- [x] Nothing dropped or renamed
- [x] `payroll` references only `core` and `shared`: `AiToolProvider` is in `core/ai`

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/src/test/.../ai/PayrollAiToolProviderTest.java` | Four tools when Payroll is held, none when it is not; each tool's `requiredAction` equals the action on its controller; `payroll.my_payslip` with a month the caller has no payslip for answers the plain sentence and never calls `own`; "current" financial year is right on 31 March and 1 April; a service exception becomes an error result |
| Unit | `payroll/src/test/.../ai/PayrollAiToolResultsTest.java` | `45000` becomes `"45000.00"`; `0.005` rounds as the payslip does; `1234567.5` becomes `"1234567.50"`, not `1.2345675E6`; no result contains a UUID |
| Integration | `app/src/test/.../ai/PayrollAiToolsIT.java` | With the stub model scripted to call `payroll.my_payslip`: employee A gets A's paid payslip; a run that is only APPROVED is not found; the stored TOOL row shows `[hidden]` or `[PAN]` where the payslip carries a PAN and bank account; a login without `payroll.payslip.read_own` is not offered the tool; a customer without Payroll is offered none |

## Verification

```bash
cd code/backend && ./mvnw -B -pl payroll,app -am verify
```

| Check | Expected | Result |
|---|---|---|
| App starts | Log lists four Payroll tools registered | not run |
| Ask "what was my net pay in October?" (stub scripted to call `payroll.my_payslip` with `2026-10`) | TOOL row holds the caller's October lines with amounts as two-decimal text | not run |
| Same for a month whose run is not yet paid | "No payslip for that month yet." | not run |
| Stored TOOL row for a payslip | No PAN, no bank account number, no database id | not run |
| Same as an employee of a customer without Payroll | No Payroll tool in the list sent to the model | not run |
| Same as a login without `payroll.tax_declaration.read_own`, asking about tax | The two tax tools are not offered | not run |
| `grep -rnE "double|float" code/backend/payroll/src/main/java/com/infinevo/payroll/ai` | No match | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A payslip field that identifies the person leaks past the mask | Medium | Rule 6 and the integration test; the mapper sends a fixed list of fields, not the whole response |
| The model does arithmetic on amounts and gets it wrong | Medium | Totals (gross, deductions, net) are sent ready-made; the system prompt in `W-114.4` tells it not to guess a figure |
| Employees expect "what is my CTC" and there is no tool | Certain | Stated in the decisions; answered from payslip lines until an own-salary permission exists |

## Rollback

Revert the commit. The helper loses its Payroll answers; nothing else changes.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `payroll` | `PayrollAiToolProvider`, `PayrollAiToolResults`, the two unit tests |
| 2 | `app` (test only) | `PayrollAiToolsIT` |
