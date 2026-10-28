# Fix steps for the 13 QA defects — 2026-10-28

Checked against `origin/main` at `6629b1ac`.

| Status | Count | Defects |
|---|---|---|
| Fixed | 8 | #1, #2, #3, #4, #9, #10, #11, #12 |
| Half fixed | 1 | #5 |
| Open | 4 | #6, #7, #8, #13 |
| **Total** | **13** | |

### Fixed (8)

| # | Defect | Commit | Follow-up |
|---|---|---|---|
| 1 | Employees read Departments | `2547ab2d` (D-106) | None |
| 2 | "HR dashboard" for Employee | `a82fbffd` (D-110) | None |
| 3 | Login opens previous user's page | `1ac99343` (D-108) | None |
| 4 | Six employee fields free text | `b1296254` + `0da553d0` (D-109, D-111) | F3 |
| 9 | Approver can't see descriptions | `f7cf7b51` (D-112) | None |
| 10 | No Approve/Reject on timesheet screen | `78ce6ed2` (D-113) | F1, F2 |
| 11 | No Approve/Reject on overtime screen | `71135a50` (D-114) | F1, F2 |
| 12 | No Approve/Reject on regularization screen | `a0758fea` (D-115) | F1, F2 |

### Half fixed (1)

| # | Defect | Done | Left |
|---|---|---|---|
| 5 | Earning Type forced; no "Fixed Allowance" | "Fixed Allowance" added, `d2f9a944` (D-116) | Earning Type still mandatory: Step A, founder decides first |

### Open (4)

| # | Defect | Step |
|---|---|---|
| 6 | Component's percentage not used on structure | Step B |
| 7 | No Deduction section on structure | Step C |
| 8 | Deduction default not applied in pay run | Step D (needs C) |
| 13 | Server accepts hours with no description | Step E |

Order: E (small, separate) → A → B → C → D. C and D ship together, or D right after C.

---

## Step A — #5 Earning Type not mandatory (only if the founder agrees)

D-116 added "Fixed Allowance". The type is still required in three places. If the founder wants it optional:

1. Frontend: drop `.required(...)` from `earningType` in the earnings schema; keep `.oneOf(EARNING_TYPE_NAMES)` with `.nullable()`.
   - `code/frontend/src/payroll/salary/componentFields.js` (earnings schema, `earningType`)
2. Frontend: remove the required marker on the Earning Type field in the drawer.
   - `code/frontend/src/payroll/salary/ComponentDrawer.jsx:357-371`
3. Backend: remove the "Earning type is required" checks on create and update; keep the 32-character check.
   - `code/backend/payroll/src/main/java/com/infinevo/payroll/component/EarningServiceImpl.java:51-52`, `:129-130`
4. Flyway: new migration `ALTER TABLE payroll.earning ALTER COLUMN earning_type DROP NOT NULL;`.
   - Column defined at `code/backend/migration/src/main/resources/db/migration/payroll/V042__earning.sql:10`
5. Check nothing assumes a non-null type: `EarningKinds.isHra/isBasic` and `SalarySplitCalculator:352,360` must treat null as "not Basic / not HRA".
   - `code/backend/payroll/src/main/java/com/infinevo/payroll/taxcalc/EarningKinds.java:17-22`
6. Tests: drawer saves without a type; `ComponentValidationTest` accepts null; one IT creates an earning with no type.
7. Small fix while there: the comment above `EARNING_TYPE_NAMES` still says "copied verbatim from legacy" — add "plus Fixed Allowance (D-116)".

If the founder keeps it mandatory, #5 is closed and only item 7 remains.

---

## Step B — #6 Copy the component's own values into the structure

Today "Add" makes a blank FLAT row and picking a component copies nothing.
`code/frontend/src/payroll/salary/SalaryVersionForm.jsx:174-178`, `:208`

1. Frontend: when a row is added, prefill it from the first component's catalogue values: `calculationType`, `defaultValue` → `value`, `percentageOf`.
   - `SalaryVersionForm.jsx:174-178`
2. Frontend: add an `onChange` to the component picker that copies the same three fields from the chosen component.
   - `SalaryVersionForm.jsx:208`
3. Frontend: add `GROSS` to the percentage-base picker so it matches the component form.
   - `SalaryVersionForm.jsx:259-262`; options already in `componentFields.js:21-25`
4. Decide with the founder: may the structure override the component's value? Recommended: yes, prefilled but editable (legacy behaviour). The value saved on the structure remains what the pay run uses.
5. Backend (safety net): if a request line has no `calculationType` or `value`, fill them from the catalogue component instead of failing or saving zero.
   - `code/backend/payroll/src/main/java/com/infinevo/payroll/salary/EmployeeSalaryServiceImpl.java:571-581`
6. Tests: form test — adding and picking a component shows its percentage; service test — blank line takes catalogue values.

---

## Step C — #7 Deduction section on the salary structure

A structure holds only earnings, benefits and reimbursements, each in its own table.
`code/backend/payroll/src/main/java/com/infinevo/payroll/salary/SalaryVersionRequest.java:10-16`

1. Flyway: new table `payroll.employee_structure_deduction`, the same shape as `payroll.employee_earning` (`V047__employee_earning.sql`), with `deduction_id` referencing `payroll.deduction`.
   - Must have `tenant_id` and an RLS policy (CI rule).
   - Do not reuse `payroll.employee_deduction` (`V101`) — that is the separate per-employee ad-hoc feature.
2. Backend: entity + repository `EmployeeStructureDeduction`, beside `EmployeeEarning.java`.
3. Backend: add `List<SalaryComponentItemRequest> deductions` to `SalaryVersionRequest` and `deductions` to `SalaryVersionResponse` (all its factory methods, `:24-103`).
4. Backend: save and load the lines in `EmployeeSalaryServiceImpl` the same way as benefits; resolve monthly/annual amounts in `SalarySplitCalculator`.
5. Backend: deductions must **not** count toward CTC in `validateCtcBalance` (`SalarySplitCalculator.java:147`).
6. Frontend: add a "Deductions" section to `SalaryVersionForm`; the deduction components are already loaded into state (`SalaryVersionForm.jsx:42-47`) but never shown. Reuse the row component used for Benefits (`:103-127`, `:364-366`), with Step B's prefill.
7. Frontend: show deduction lines on the read-only salary view (`SalaryTab.jsx`).
8. Tests: IT saving and reading back a version with deductions; RLS test for the new table; form test for the new section.

---

## Step D — #8 Apply deduction defaults in the pay run

No pay-run code reads deduction components today. Deduction lines come only from LOP, pay inputs, statutory and TDS.
`code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/StructureLineContributor.java:62-84`

1. Backend: in `StructureLineContributor.contribute`, loop `version.deductions()` and write `LineKind.DEDUCTION` lines from each enabled line's monthly amount (`LineKind.java` already has `DEDUCTION`).
2. Percentage of GROSS: compute it in a new contributor ordered after LOP, e.g. `@Order(250)`, using `ctx.priorLines()` to sum gross earnings, so LOP-reduced gross is used. FLAT and percentage of BASIC/CTC can stay in `StructureLineContributor`.
3. Respect the component's `max_limit` (cap the amount) and `is_recurring` (non-recurring: first run only, or ask the founder).
   - Columns in `code/backend/migration/src/main/resources/db/migration/payroll/V043__deduction.sql`
4. Pre-tax deductions: pass `is_pre_tax` lines to the TDS calculation so taxable income drops (`tds/TaxLineContributor.java`, `@Order(500)` — runs later, can read `priorLines`).
5. A deduction component deleted after the version was written: throw, same as earnings (`requireKnown`).
6. Payslip: check the new lines appear under Deductions in the payslip view and PDF (D-102 layout).
7. Tests: contributor unit test (flat, % of basic, % of gross after LOP, max limit); pay run IT where net pay = gross − structure deduction − statutory − TDS.

---

## Step E — #13 Server rejects hours without a description

The server checks only the 500-character limit.
`code/backend/hrms/src/main/java/com/infinevo/hrms/timesheet/TimesheetRules.java:180-182`

1. Backend: in the same day loop, when hours > 0 and the description is null or blank, add
   `errors.put(dk + ".description", "description is required when hours are entered")`.
   - `TimesheetRules.java:180` (inside the loop that already computes `hours`)
2. It then covers save and resubmit, which both call these rules.
   - `TimesheetServiceImpl.java:79,121`; `TimesheetSubmitServiceImpl.java:141`
3. Use the same wording as the screen's message.
   - `code/frontend/src/hrms/timesheet/weekGrid.js:223-235`
4. Migrated legacy sheets with blank descriptions: the rule blocks re-saving them. Confirm with the founder that this is wanted.
5. Tests: `TimesheetValidationTest` — hours + blank → error, hours 0 + blank → ok; `TimesheetCrudIT` — API call with hours and no description returns 400.

---

## Follow-ups on the fixes already on main

**F1 — Approve/Reject can silently disappear (D-113, D-114, D-115).**
1. The screen finds its approval step by scanning only the first 100 items of the approver's inbox.
   - `OvertimeDetail.jsx`, `RegularizationDetail.jsx`, `EntryView.jsx` — `approvalService.pending(0, 100)`
2. An approver with more than 100 pending items sees no buttons.
3. Fix: a backend lookup "my pending step for item X", or filter `pending` by item id, then call it from all three screens.

**F2 — Status after a decision (same three screens).**
1. After a decision the screen sets the item's status to APPROVED or REJECTED itself.
2. If the approval chain has another step, the item is really still pending.
3. Fix: reload the item from the server after `onSuccess` instead of setting the status locally.

**F3 — Old values in the new dropdowns (D-109, D-111).**
1. Employees saved before the change, or migrated by W-68, may hold free-text values that are not in the new option lists.
2. Check a few real rows. If they don't match, map them in the W-68 loader, or keep showing the stored value as-is.
