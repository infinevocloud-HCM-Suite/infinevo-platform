# Feature: Salary setup drawer

| Field | Value |
|---|---|
| **Feature ID** | `W-75.2` · `PAY-SALARY-SETUP` (part 2 of 2) |
| **Owner** | claude |
| **Apps touched** | `code/frontend` (`src/payroll/salary`) |
| **Related gaps** | `DEBT-027` (`legacy/docs/GAP_INVENTORY.md:77`); absorbs `D-127` (defaults copied in), the screen half of `D-132`, and the saved-view table from `D-130` — founder 2026-10-30. `D-129`–`D-131` dropped: statutory details stay on the Salary tab |
| **Status** | **Ready to merge** — `dev-claude-w75` |
| **Blocked by** | `W-75.1` (preview endpoint and `balancing` flag) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A payroll officer sets up a new employee's salary and statutory details in one drawer by typing only the CTC, sees the take-home before saving, and the save succeeds | 1 |
| Frontend area | `payroll/salary` | 1 |

---

## 1. Problem

| Today | Evidence |
|---|---|
| "Add Earning" starts every row as `FLAT` with no value; the company defaults (Basic 50% of CTC, HRA 50% of Basic) are not used | `code/frontend/src/payroll/salary/SalaryVersionForm.jsx:168-180` |
| The officer works out Special Allowance by hand; a wrong figure is refused only after saving ("Difference: X") | `code/backend/payroll/.../salary/SalarySplitCalculator.java:147-179` |
| Statutory details (EPF, ESI, PT, UAN) are a separate card **below** the structure, must be saved **before** the structure, and have no format checks | `SalaryTab.jsx:375-376`; `StatutoryProfileCard.jsx:51-68` |
| PF and ESI amounts appear only after saving | no preview before `W-75.1` |

Legacy did this on one form: % defaults, automatic Fixed Allowance, live EPF/ESI (`legacy/Payroll-Fend-react/src/pages/mainPages/employee/salaryDetails.js:241-290, 392-396, 685-688`), with UAN checked as 12 digits (`basicDetails.js:279-297`).

## 2. Scope

**In scope**

- A **Set up salary** drawer replacing `SalaryVersionForm` for create, revise and edit-future-version
- Rows pre-filled from the company catalogue; one balancing row computed by the server
- A statutory section in the same drawer
- A live summary from `POST …/salary/preview` (`W-75.1`)
- Field checks before save
- A **Statutory Contributions** table on the saved salary view, from `currentVersion.statutory` (from `D-130`). The reply already carries it (`SalaryVersionResponse.java:27`); `SalaryTab.jsx` shows only earnings, benefits and reimbursements (`:311-345`)

**Out of scope**

- Any server change (`W-75.1`)
- Moving statutory fields into Add Employee (was `D-129`/`D-131`, dropped 2026-10-30): HR holds no payroll action, and `core` screens may not import `payroll` (`code/frontend/.eslintrc.cjs:150-172`)
- Deduction components on the salary version or the pay run (was `D-126` salary / `D-128`, dropped 2026-10-30)
- Benefits and reimbursements pre-fill — they stay as today ("Add" rows)

## 3. Flow

```
Payroll officer --> Employee > Salary tab > "Set up salary" / "Revise"
   --> drawer: CTC, date --> rows pre-filled --> statutory ticks + numbers
   --> each change (debounced 400 ms) --> POST /salary/preview --> summary panel
   --> Save --> PUT /statutory-profile --> POST /salary (or /revisions) with balancing flag
```

## 4. Backend changes

None.

## 5. Frontend changes

| File | Change |
|---|---|
| `payroll/salary/SalarySetupDrawer.jsx` (new) | The drawer. Replaces `SalaryVersionForm.jsx` for all three modes; delete `SalaryVersionForm.jsx` and its test once nothing imports it |
| `payroll/salary/salaryDefaults.js` (new) | Builds the starting rows from the active earnings (`EarningResponse`: `calculationType`, `defaultValue`, `percentageOf`, `includedInCtc` — `code/backend/payroll/.../component/EarningResponse.java:13-33`): every active, in-CTC, non-variable, non-FBP earning whose `defaultValue` > 0, plus the balancing row |
| `payroll/salary/SalaryTab.jsx` | "Create structure" → **Set up salary**; Revise opens the drawer from the current version; the `StatutoryProfileCard` stays as a read view (`:376`) |
| `payroll/salary/salaryService.js` | `preview(employeeId, body)` |
| `payroll/salary/StatutoryProfileCard.jsx` | same field checks as the drawer (shared `statutoryRules.js`), plus `maxLength` 16 / 32 / 32 on the three inputs; a `400` from `W-75.1` shows under its field |
| `payroll/salary/StatutoryContributionsTable.jsx` (new) | `<Table id="table-salary-statutory">` under the active version in `SalaryTab.jsx`: component name (not code), Employer / Employee, rate and base ("12% of ₹15,000"), monthly, annual, "In CTC" tag — money through `shared/ui/format.js` |

**Drawer layout**

| Section | Content |
|---|---|
| 1. Basics | Annual CTC (₹), effective from (default: date of joining for a first version, today for a revision) |
| 2. Earnings | Pre-filled rows, each editable (component, Flat / %, value, % of). **Balancing row**: the earning with code `SPECIAL` if active, else `FIXED_ALLOWANCE` if active (the migrated company has only this — founder 2026-10-30), else the officer picks one; its value is read-only and shown from the preview, labelled "Balances to CTC". "Add earning" for the rest |
| 3. Statutory | EPF, EPS, EPS on higher wages, PT, ESI ticks; PF account number, UAN, ESI number — loaded from the profile. Alerts: "EPF is switched off for the company" / "ESI is switched off for the company" with a link to Payroll → Settings (read from `payroll/settings/statutoryService.js`) |
| 4. Summary | From the preview: per-component monthly and annual; employer EPF/EPS/EDLI/admin/ESI, marked "in CTC" or not; gross; employee EPF, ESI, PT; **estimated take-home** ("before TDS and loss of pay") |
| Footer | Save (disabled while a preview is loading or has errors), Cancel |

**Field checks (shared `statutoryRules.js`)**

| Field | Rule | Message |
|---|---|---|
| UAN | 12 digits, if EPF ticked and entered | "UAN must be exactly 12 digits" |
| ESI number | 10 digits, if ESI ticked and entered | "ESI number must be exactly 10 digits" |
| PF account number | at most 32 characters | "At most 32 characters" |
| ESI tick | warn (not block) when preview gross > ESI ceiling | "Gross is above ₹21,000 — no ESI will be deducted" |
| Balancing | preview returns a negative-balance error | shown under CTC, Save disabled |

**Save order:** statutory profile first, then the salary. If the salary save fails, the profile change stays saved and the drawer stays open with the server message — the profile is valid on its own.

**Routes added:** none.

**Who sees what:** the drawer and its buttons need `payroll.salary.manage` (payroll officer, tenant admin); finance keeps the read-only tab (`payroll/index.js:164-167`).

## 6. Database changes

None. Creates no table and no Flyway script; money values are strings from the server, formatted with `shared/ui/format.js`, never parsed to floating point for arithmetic — all sums come from the preview.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Component | `payroll/salary/SalarySetupDrawer.test.jsx` | catalogue with Basic 50% CTC, HRA 50% Basic, Special → three rows pre-filled, Special marked balancing; typing CTC calls preview once after debounce; summary shows take-home; Save sends `balancing: true` on Special and calls statutory save first; EPF-off alert; finance sees no Save |
| Unit | `payroll/salary/salaryDefaults.test.js` | inactive, variable, FBP, zero-default earnings left out; `SPECIAL` chosen as balancing over `FIXED_ALLOWANCE` when both are active; no `SPECIAL` → `FIXED_ALLOWANCE` chosen; neither → no balancing row chosen |
| Unit | `payroll/salary/statutoryRules.test.js` | UAN 11/12/13 digits; ESI 10 digits; PF account number 33 characters refused; ESI-over-ceiling warning |
| Component | `payroll/salary/StatutoryContributionsTable.test.jsx` | renders `EPF_EMPLOYER`, `EPS_EMPLOYER`, `EDLI` rows with names, rates, ₹ amounts and the In CTC tag; empty state when the version has no statutory lines |
| E2E | `e2e/payroll-officer.spec.js` | payroll officer: employee → Salary → Set up salary → CTC 600000 → Save → structure shows; no "Difference" error |

## 8. Verification

```bash
cd code/frontend
npm test -- src/payroll/salary
npm run lint
```

| Check | Expected |
|---|---|
| Vitest | all `src/payroll/salary` tests pass |
| Lint | `0 problems` |
| CI `e2e` job | payroll officer walk green |
| Manual, local stack | officer types only CTC 6,00,000 with the India catalogue → Basic 25,000, HRA 12,500, Special 12,500 (employer PF not in CTC) → Save succeeds |
| `node .claude/scripts/check-done.mjs W-75.2` | gates green |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A preview call on every keystroke | medium | 400 ms debounce, cancel the previous request |
| Tenants whose catalogue has neither `SPECIAL` nor `FIXED_ALLOWANCE` | low | the drawer asks the officer to pick the balancing earning; Save stays possible without one (today's behaviour) |
| Officers used to the old form | low | same tab, same permissions; the read view of the profile card stays |

## 10. Rollback

Revert the commit; `SalaryVersionForm.jsx` comes back with it. `W-75.1` is unaffected.
