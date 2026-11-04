# Feature: Payroll screens use the new look; amounts line up

| Field | Value |
|---|---|
| **Feature ID** | `W-82.1` · `FE-PAYROLL` |
| **Owner** | krushna |
| **Apps touched** | `code/frontend` (`src/payroll`, `.eslintrc.cjs`) |
| **Related gaps** | founder 2026-11-04: UI redesign |
| **Status** | Ready |
| **Blocked by** | `W-83.1` |

## What and why

Payroll screens pick up the theme from `W-83.1`, but 11 files still hard-code colours, so the old
blue and green sit beside the new look (for example `payroll/payrun/RunPage.jsx:467,493,498,523,532,541`).
Amounts should also line up in columns so they are easy to scan. Nothing about how a screen works
changes, and no figure changes.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | Payroll screens show the new look, with amounts right-aligned in even figures | 1 |
| Frontend area | `payroll` | 1 |

## Changes

| Kind | Files | Change |
|---|---|---|
| Pages | `claims/ClaimDetail`, `ClaimList`, `DeductionList` · `dashboard/DashboardPage` · `form16/Form16Page` · `payrun/OffCycleCreate`, `RunList`, `RunPage` · `priorpayroll/PriorPayrollPage` · `salary/ComponentsScreen` · `settings/EpfScreen`, `EsiScreen`, `FbpPlanScreen`, `PayScheduleScreen`, `ProfessionalTaxScreen`, `SettingsLayout` · `tax/DeclarationPage`, `OfficerDeclarationView`, `TaxWindowScreen` | `PageHeader`, `<Card variant="borderless">`, tokens |
| Parts | Every other non-test `.jsx` in `src/payroll`, except below | Tokens only, where they hold a literal |
| Amounts | Every amount in a table or statistic, for example `payrun/RunEmployees.jsx:149,163`, `LinesDrawer.jsx:70`, `RunList.jsx:175` | `align: 'right'` and `MoneyText` |
| Lint | `.eslintrc.cjs` | Hex rule on for `src/payroll/**/*.jsx`, except `payslip/PayslipDocument.jsx` |

**Colours by meaning:** use `colorSuccess`, `colorWarning`, `colorError` and `colorPrimary` where a
hex meant the same thing. `tax/SummarySection.jsx` (13) and `tax/HousingSection.jsx` (5) hold most of
them outside the payslip.

**`payslip/PayslipDocument.jsx` stays as it is.** Its 41 colours are the fixed look of a document, used by the
signed-in and public payslip alike. Changing it is not part of a screen restyle.

**Stays exactly as it is** (founder 2026-11-04)

| What | Where |
|---|---|
| Approve and Pay are two separate actions; Pay asks for the paid date | `payrun/RunPage.jsx:182,190-193` |
| Every server figure, shown as sent. The screen does no new arithmetic | All |
| The salary setup drawer's calculation and preview (`W-75`) | `salary/SalarySetupDrawer.jsx` |

## Out of scope

| Why | Item |
|---|---|
| Held back by the founder | Payslip Download |
| Waits on the `W-47` spec (`D-25`) | Proof-review workbench, receipt viewer |
| Not in the code | Manual pay adjustment, tax regime comparison, digital signature (DSC), payslip QR code, salary hike % and revision reason |
| Other new behaviour | Countdown banner, statutory deadline calendar, off-cycle wizard, receipt auto-extract, bulk Form 16 publish, FBP slider, pay calendar simulator |

Each becomes its own ticket only if the founder asks. **Manual pay adjustment changes what people
are paid, so it would need its own spec and care.** Phone-specific fixed widths stay with `W-80.4`.

## Standing rules

Creates no table, no migration, no backend code. Money stays as the server's value; `MoneyText` only
displays it, and no `parseFloat` or `Number` is added to an amount. `payroll` imports nothing from
`hrms`. Every element `id`, `data-testid`, permission check, route and service call stays as on `main`.

## Tests

| File | Covers |
|---|---|
| `payroll/payrun/RunEmployees.test.jsx` | Amount columns are right-aligned and show the same text as before |
| `payroll/payrun/RunPage.test.jsx` | Approve and Pay stay separate; Pay still asks for the date |

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
grep -rnE "#[0-9a-fA-F]{3,8}\b" src/payroll --include=*.jsx --exclude=*.test.jsx --exclude=PayslipDocument.jsx
git -C ../.. diff --stat origin/main...HEAD -- code/backend
```

The first line passes. The grep and the diff print nothing. The `e2e` job is green. By eye, at
1280 px and 375 px: `/payroll/dashboard`, a pay run through Approve and Pay, `/payroll/claims`,
`/payroll/settings/epf`, a tax declaration.

## Risks

This ticket touches about 48 files on money screens. A slip would change how a figure looks, not how
it is calculated, since no backend or arithmetic changes. Check one pay run's totals against `main`
by eye before merging. If the diff grows past review, split by folder (`W-82.2`, `W-82.3`).

## Rollback

Revert the squash commit. No data changes.
