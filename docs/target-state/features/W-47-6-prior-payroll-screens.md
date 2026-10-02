# Feature: Prior payroll screens and the mid-year warning

| Field | Value |
|---|---|
| **Feature ID** | `W-47.6` · screens for `W-38` (ticket #50) · `PAY-17` |
| **Promoted to** | `docs/target-state/features/W-47-6-prior-payroll-screens.md` — **`W-47-6` with hyphens**, never `W-47.6`; `guard-edit` blocks the dotted form |
| **Owner** | krushna |
| **Apps touched** | `code/frontend/src/payroll/priorpayroll`, one banner in `code/frontend/src/payroll/payrun/RunList.jsx` |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | `W-38.1` (every endpoint) · `W-47.2` (`RunList.jsx`, krushna). Uses `W-38.3`'s `setup_step_skipped` when present, works without it |
| **Size** | S |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none — the menu item ships in `W-38.1` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an officer downloads the template, uploads a filled file, sees the dry-run result and error file, imports, and sees which months are loaded; a mid-year tenant that has loaded nothing sees a warning on the pay run list | 1 |
| Frontend area | `src/payroll` — a new `priorpayroll` folder and one banner in `payrun` | 1 |

---

## 1. Problem

`W-38.1` gives an API and no screen. The frozen screen never existed: its card is commented out
(`legacy/Payroll-Fend-react/src/pages/mainPages/dashboardPage/onboardingDashboard.js:95-104`).
Without a warning, a mid-year tenant that skips the import has every employee over-deducted
from the first run, and nothing on screen says why.

## 2. Scope

**In scope**

- `/payroll/prior-payroll`: template download, upload, dry run, import, history, imported months
  with delete
- `MidYearBanner` on the pay run list and on this page

**Out of scope**

- Editing a row. Delete and re-import (`W-38-1-prior-payroll-import.md` §4)
- The setup checklist entry — `W-38.3`, rendered by `W-46.6` unchanged
- Any backend change

## 3. Flow

```
[officer] /payroll/prior-payroll
   "Download template"   --> priorPayrollService.template()           --> saves prior-payroll-template.csv
   choose FY + file       --> documentService upload (kind EMPLOYEE_DOCUMENT)  --> documentId
   "Check file"           --> priorPayrollService.import({documentId, financialYear, dryRun: true})
                          <-- rows_total / rows_failed, error file link when rows_failed > 0
   "Import"               --> same with dryRun: false              --> result, months table refreshes
   months table           --> priorPayrollService.months(fy, page)  --> Delete per row (confirm)

[officer] /payroll/runs  (W-47.2)
   MidYearBanner          --> priorPayrollService.status(current fy)
                              missing_periods not empty and not setup_step_skipped
                              → warning with the months and a link to /payroll/prior-payroll
```

## 4. Backend changes

None. Endpoints used, all from `W-38-1-prior-payroll-import.md` §4, plus `W-21`'s upload:

| Method | Path | Action |
|---|---|---|
| GET | `/api/v1/payroll/prior-payroll/template` | `payroll.run.read` |
| POST | `/api/v1/documents` (multipart, `kind=EMPLOYEE_DOCUMENT`) | `core.document.upload` (`core/.../document/DocumentController.java:68-82`) |
| POST | `/api/v1/payroll/prior-payroll-imports` | `payroll.run.execute` |
| GET | `/api/v1/payroll/prior-payroll-imports?page=&size=` | `payroll.run.read` |
| GET | `/api/v1/payroll/prior-payroll?fy=&page=&size=` | `payroll.run.read` |
| DELETE | `/api/v1/payroll/prior-payroll/{id}` | `payroll.run.execute` |
| GET | `/api/v1/payroll/prior-payroll/status?fy=` | `payroll.run.read` |
| GET | `/api/v1/documents/{id}/link` (the error file) | `core.document.read` (`DocumentReadController.java:50-51`) |

## 5. Frontend changes

Follows the `W-45` contract and the `payroll/tax` service pattern: `apiClient` from
`@shared/api/client`, paths from `/v1/…`, reply unwrapped as `res.data.data`
(`src/payroll/tax/taxSettingsService.js:18-19`). Financial year labels through
`src/payroll/tax/financialYear.js`'s `fyForApi`.

| File | Change |
|---|---|
| `src/payroll/priorpayroll/priorPayrollService.js` | **new.** `template`, `upload(file)`, `import({documentId, financialYear, dryRun})`, `imports(page)`, `months(fy, page)`, `remove(id)`, `status(fy)`, `errorFileLink(documentId)`; one function per row of §4 |
| `src/payroll/priorpayroll/PriorPayrollPage.jsx` | **new.** FY `Select` (current and previous); "Download template"; `Upload` (`.csv` only, one file); "Check file" runs the dry run and shows counts in a `Result`, with "Download errors" when `rows_failed > 0`; "Import" enabled only after a check of the same file, with confirm; history `Table` (date, file result, imported, failed, status `Tag`); months `Table` (employee number, period, the six amounts, Delete with confirm, `useCan('payroll.run.execute')`). `MidYearBanner` at the top |
| `src/payroll/priorpayroll/MidYearBanner.jsx` | **new.** Ant `Alert` type `warning`: "Payroll for {months} is not loaded. Tax already deducted in those months will be charged again." with a link to `/payroll/prior-payroll`. Renders nothing when `missing_periods` is empty, when `setup_step_skipped` is true, or when the status call fails |
| `src/payroll/payrun/RunList.jsx` (`W-47.2`) | renders `MidYearBanner` above the table |
| `src/payroll/index.js` | `routes` gains `/payroll/prior-payroll` |

Amounts are shown as the API sends them, formatted for display only; the screen sums nothing.

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/payroll/prior-payroll` | `PriorPayrollPage` | inside `AppShell`; present when the feed carries `payroll.prior_payroll` (`W-38.1` §4) |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `priorPayrollService.test.js` | every function hits its path; `import` posts `financialYear` as `2026-2027`; replies unwrapped from `data` |
| Component | `PriorPayrollPage.test.jsx` | upload then "Check file" sends `dryRun: true`; "Import" disabled before a check and after the file changes; import sends `dryRun: false`; "Download errors" shown only when `rows_failed > 0`; Delete hidden without `payroll.run.execute` |
| Component | `MidYearBanner.test.jsx` | months listed when `missing_periods` is `['2026-04','2026-05']`; nothing when empty; nothing when `setup_step_skipped` is true; nothing when the call fails |
| Component | `RunList.test.jsx` (extend) | the banner renders above the table |
| Lint | existing `lint-rules.test.js` | unchanged |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
# browser: log in as admin@acme.local
```

| Check | Expected |
|---|---|
| lint, tests | clean |
| template | the downloaded file has the 8 headers of `W-38-1-…md` §4 |
| dry run | a file with one unknown employee number ⇒ 1 failed, error file lists its line; nothing in the months table |
| import | same file ⇒ the good rows appear in the months table |
| banner | with no import and the current month after April, `/payroll/runs` shows the warning; after importing every missing month it is gone; after skipping "Prior payroll" in `/setup` it is gone |
| isolation | admin@globex-full.local sees none of Acme's months or imports |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| "Import" runs on a file that was never checked | medium | disabled until a dry run of the same file; the component test |
| The banner blocks the run list when the status call fails | low | renders nothing on error; the component test |
| `RunList.jsx` changes under `W-47.2` while this is built | medium | same developer; this ticket starts after `W-47.2` is on `main` |

## 10. Rollback

Revert the branch. No data, no migration.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | none |
| `Money` / `BigDecimal` | amounts displayed as sent; nothing summed |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/payroll` imports `@shared/*` and `@shell/screens` only; the upload is an HTTP call to a `core` path |

## 12. Gap inventory

None.

## 13. Decisions — founder, 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | Where does the warning show? | **The pay run list and the prior payroll page** |
| 2 | When does it go away? | **When every missing month is imported, or the setup step is skipped** |
