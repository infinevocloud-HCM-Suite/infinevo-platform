# Feature: Component type shown on the salary screens

| Field | Value |
|---|---|
| **Feature ID** | `W-117` · `FE-PAYROLL` |
| **Owner** | Claude |
| **Apps touched** | `code/frontend` (`src/payroll/salary`) |
| **Related gaps** | founder 2026-11-10: the Components list and the employee's structure list do not say which component is fixed, variable or scheduled |
| **Status** | Ready |
| **Blocked by** | nothing |

## What and why

The Components screen lists every earning but tags only EPF, ESI, FBP and Taxable
(`code/frontend/src/payroll/salary/ComponentsScreen.jsx:143-152`). Whether a component is
Variable, on what frequency, Scheduled or One-time is visible only inside the row's drawer. The
employee's structure list shows code, name, calculation type and amounts and no component type
either (`SalaryTab.jsx:120-172`), so a quarterly incentive looks like a monthly allowance. The
setup drawer's dropdown shows `name (code)` (`SalarySetupDrawer.jsx:552-558, 713-717`).

The API already returns the flags: `variable`, `earningFrequency`, `oneTime`, `scheduledEarning`,
`fbpComponent` on every earning (`code/backend/payroll/src/main/java/com/infinevo/payroll/component/EarningResponse.java:21-30`).
The screens drop them. This ticket shows a **Type** in place of the code on the two lists and
beside the name in the dropdown. Nothing about how a component works changes.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | the Components list and the structure list show a component type instead of its code | 1 |
| Frontend area | `payroll/salary` | 1 |

## The type label

One helper, `componentType(earning)` in a new `src/payroll/salary/componentType.js`, returns a
label from the flags, first match wins:

| Flags | Label | Tag colour |
|---|---|---|
| `fbpComponent` | FBP | purple (as today) |
| `scheduledEarning` | Scheduled | gold |
| `oneTime` | One-time | gold |
| `variable` | Variable · *frequency* — Monthly, Quarterly, Half-yearly, Yearly from `earningFrequency` via `humanize` | blue |
| none of the above | Fixed | default |

Benefits and reimbursements have no such flags; their lists keep today's tags and only swap
the Code column for the drawer as below.

## Changes

| File | Change |
|---|---|
| `componentType.js` (new) | the helper above, plus `componentTypeTag(earning)` returning the `<Tag>` |
| `ComponentsScreen.jsx:109-113` | Code column replaced by **Type**, rendered with `componentTypeTag`. On the earnings tab the existing Tags column stays and drops nothing. On the benefits and reimbursements tabs the Code column goes too; the code stays visible in the row's drawer |
| `ComponentDrawer.jsx` | show the code read-only under the name field when editing, so it is still findable. Only if it is not already shown; check first |
| `SalaryTab.jsx:120-127` | Code column replaced by **Component type** with the same tag. The structure item carries `componentCode` and `earningFrequency` only (`SalaryComponentItemResponse.java:13-23`), so the tab dispatches `fetchActiveComponents` on mount (`salarySlice.js:4`, as `SalarySetupDrawer.jsx:320` does) and looks the earning up by `componentId`. Unknown id, for a component deleted since the version was written: show "—". Rename the existing `Type` column (`:135-139`, calculation type) to **Calculation** so the two do not clash |
| `SalarySetupDrawer.jsx:552-558, 713-717` | option text `name` with the type label in `type="secondary"` after it; the code no longer shown |

Out of scope: pay run lines drawer, payslip, any backend change, deductions tab (deductions have
no type flags).

## Standing rules

Creates no table, no migration, no backend code, no money handling. No module references another.
Every element `id`, `data-testid`, permission check, route and service call stays as on `main`;
the one new `data-testid` is `component-type` on the tag.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `componentType.test.js` (new) | each row of the label table, and precedence: FBP over Variable, Scheduled over Variable, Variable with each of the four frequencies, Fixed when all flags are false |
| Unit | `ComponentsScreen.test.jsx` | the earnings table has a Type column and no Code column; a variable quarterly row shows "Variable · Quarterly"; benefits tab shows no Code column |
| Unit | `SalaryTab.test.jsx` | the structure list shows "Component type" and "Calculation" headers; a row whose component is scheduled shows the Scheduled tag; a row with an unknown `componentId` shows "—" |
| Unit | `SalarySetupDrawer.test.jsx` | the dropdown option for a variable earning carries the type text |

Existing tests that pinned the `code` text in these tables change, and the commit says which.

## Verification

```bash
cd code/frontend && npx vitest run src/payroll/salary && npm run lint && npm run build
git -C ../.. diff --stat origin/main...HEAD -- code/backend
```

The first line passes. The diff prints nothing. By eye at 1440 px on the test tenant:

| Check | Expected |
|---|---|
| Payroll › Components › Earnings | Type column with Fixed, Variable · Quarterly, Scheduled, FBP tags; no Code column |
| Employee › Salary tab | Component type column first, Calculation column after the name |
| Salary setup drawer, add component | each option reads `Name  Fixed` or `Name  Variable · Yearly` |

## Risks

Nothing unusual.

## Rollback

Revert the squash commit. No data changes.
