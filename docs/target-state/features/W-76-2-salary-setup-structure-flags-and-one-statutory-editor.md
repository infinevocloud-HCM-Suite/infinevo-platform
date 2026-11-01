# Feature: Salary setup follows the structure flags, one place to edit statutory details

| Field | Value |
|---|---|
| **Feature ID** | `W-76.2` · `PAY-SALARY-SETUP` |
| **Owner** | claude |
| **Apps touched** | `code/frontend` (`src/payroll/salary`) |
| **Related gaps** | follows `W-75.2`; `W-75.2` §5 meant the bottom statutory card as a read view (`W-75-2-salary-setup-drawer.md:71`) but it stayed editable — founder 2026-11-01 |
| **Status** | **Done** — `aaf53c21` |
| **Blocked by** | nothing. Takes over the structure-switch rule from `W-76.1` (dropped 2026-11-01: the pay run pays the employer lines on the saved salary, so the rule is display only) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The salary screens follow the company's "in salary structure" settings (earnings pre-filled, employer statutory lines shown) and statutory details are edited in one place | 1 |
| Frontend area | `payroll/salary` | 1 |

---

## 1. Problem

| Today | Evidence |
|---|---|
| Statutory details can be edited in two places: the drawer and the card at the bottom of the Salary tab | `code/frontend/src/payroll/salary/SalarySetupDrawer.jsx:826-880`; `StatutoryProfileCard.jsx` (form with Save); `SalaryTab.jsx:383` |
| The drawer pre-fills an earning in CTC even when it is not marked "Include in Salary Structure" | `salaryDefaults.js:29-37` reads `includedInCtc` only; the flag is on `EarningResponse.java:25` |
| EDLI, PF admin and employer PF / ESI are listed even when the company keeps them out of the structure; the switches are never read | `StatutoryLineDeriver.java:111-137`; `EpfSetting.java:97-103`, `EsiSetting.java:67` |
| ₹0 statutory lines (EPS when not ticked) are listed | `StatutoryLineDeriver.java:76-86`; `StatutoryContributionsTable.jsx` lists every line |
| LWF and Director can be changed only on the bottom card; the drawer keeps them as loaded | `SalarySetupDrawer.jsx:110-120` |

## 2. Scope

**In scope**

- `StatutoryProfileCard` becomes a read view: ticks shown as Yes / No, numbers as text, no Save; a note "Edit in Set up salary or Revise" for an officer
- LWF and Director ticks move into the drawer's statutory section, so nothing is lost
- Pre-fill and the automatic balancing pick need `includedInSalaryStructure`
- `StatutoryContributionsTable` hides lines whose monthly amount is zero
- `StatutoryContributionsTable` hides an employer line outside CTC when its company switch is off. **Rule:** employee lines always shown; `EPF_EMPLOYER` and `EPS_EMPLOYER` follow `includeEmployerInStructure`, `ESI_EMPLOYER` follows ESI `includeInStructure`, and these show when counted in CTC; `EDLI` and `EPF_ADMIN` show only when their structure switch is on (they are then also paid, `W-76.3`); settings not loaded means every line is shown. The Salary tab and the drawer load the settings (`GET /v1/payroll/settings/epf|esi`, `payroll.salary.read`)

**Out of scope**

- Any server change. What is saved and paid is `W-76.3`
- The pay run's EPS split for an employee not in EPS (`W-76.3`)
- PT on the saved salary view — the saved version carries no PT; the pay run works it out from the work location

## 3. Backend changes

None.

## 4. Frontend changes

| File | Change |
|---|---|
| `payroll/salary/StatutoryProfileCard.jsx` | read-only `Descriptions`: EPF, EPS, EPS on higher wages, PT, LWF, ESI, Director, PF account number, UAN, ESI number; "Not set" when empty; officer note "Edit in Set up salary or Revise" |
| `payroll/salary/SalarySetupDrawer.jsx` | LWF and Director checkboxes in the statutory section, sent in the profile save |
| `payroll/salary/salaryDefaults.js` | `isDefaultEarning` and `pickBalancingEarning` require `includedInSalaryStructure`; `canBalance` (what the server accepts when the officer picks) unchanged |
| `payroll/salary/StatutoryContributionsTable.jsx` | skip zero lines; `shownInStructure(line, { epf, esi })` and `epfSetting` / `esiSetting` props |
| `payroll/salary/SalaryTab.jsx` | load the EPF and ESI settings; pass them to the table |

## 5. Database changes

None. Creates no table and no Flyway script; money stays a server string, no floating point.

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Component | `StatutoryProfileCard.test.jsx` | shows the loaded values; no Save button and no inputs; note shown to an officer only |
| Component | `SalarySetupDrawer.test.jsx` | LWF and Director ticks load and are sent on save |
| Unit | `salaryDefaults.test.js` | in CTC but not in structure → not pre-filled; balancing not picked when not in structure |
| Component | `StatutoryContributionsTable.test.jsx` | a ₹0 line is not shown; EDLI and admin outside CTC hidden when their switch is off; `shownInStructure` per switch; EDLI / admin by their switch only; employer PF counted in CTC always shown; settings missing shows all |
| E2E | `e2e/payroll-officer.spec.js` | unchanged walk passes |

## 7. Verification

```bash
cd code/frontend
npx vitest run src/payroll/salary
npm run lint && npm run build
```

| Check | Expected |
|---|---|
| Vitest | all `src/payroll/salary` tests pass |
| Lint | `0 problems` |
| CI `e2e` job | green |
| `node .claude/scripts/check-done.mjs W-76.2` | gates green |

## 8. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| An officer looks for the old Save on the card | low | the note points to the drawer |
| A company whose earnings were never marked "in structure" gets no pre-filled rows | low | rows can still be added by hand; the migrated company's earnings are all marked (checked 2026-11-01) |

## 9. Rollback

Revert the commit. `W-76.3` is unaffected.
