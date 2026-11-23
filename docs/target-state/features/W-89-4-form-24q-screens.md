# Feature: Form 24Q Quarterly Return & Challan Management Screens

| Field | Value |
|---|---|
| **Feature ID** | `W-89.4` · `FE-PAYROLL` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/frontend` (`src/payroll/form24q`, routes) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready for CA / founder review |
| **Blocked by** | `W-89.1`, `W-89.2`, `W-89.3` |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | `core/V195__form24q_actions.sql` | 1 |
| Externally testable behaviour | Payroll officer manages TDS challans, views quarterly reconciliation status, and downloads Annexure I & II exports | 1 |
| Frontend area | `payroll/form24q` | 1 |

---

## 2. Problem & Context

Today, the payroll officer can view TDS deductions inside individual pay runs and employee tax declarations (`W-47.3`), but has no central place to:
1. Enter and manage income tax deposit challans (ITNS 281).
2. Track whether monthly TDS deductions match deposited challans for a given quarter.
3. Validate statutory filing readiness (missing PANs, incorrect section codes).
4. Download Form 24Q Annexure I (all quarters) and Annexure II (Quarter 4).

---

## 3. Scope

**In scope:**
- New route and page: `/payroll/form24q` (`Form24QPage.jsx`).
- Header controls:
  - Financial Year dropdown (defaulting to current FY).
  - Quarter selection tabs (`Q1: Apr–Jun`, `Q2: Jul–Sep`, `Q3: Oct–Dec`, `Q4: Jan–Mar`).
- Section 1: **Challans Card (`ChallanList.jsx`)**
  - Table of recorded challans for the selected quarter: BSR code, Challan No, Tender Date, Tax, Cess, Total Deposited.
  - "Record Challan" button opening `ChallanModal.jsx` (validated inputs: 7-digit BSR, up to 5-digit Challan No, tender date, breakdown).
  - Allocation status pill: `Fully Allocated` (green), `Unallocated ₹X` (orange), `Overallocated` (red).
- Section 2: **Reconciliation & Pre-flight Summary Card**
  - Total Deductees count, Total Gross Salary Paid, Total TDS Deducted, Total TDS Deposited, Net Shortfall/Balance.
  - Warning banner when any employee is missing a PAN or unallocated TDS exists.
- Section 3: **Export Actions Card**
  - "Download Annexure I (CSV)" and "Download Annexure I (e-TDS TXT)".
  - "Download Annexure II (CSV)" and "Download Annexure II (e-TDS TXT)" — enabled only when Quarter 4 is selected; displays a tooltip *"Annexure II is filed with Q4 return only"* for Q1–Q3.
- Guarded by `@RequiresAction("payroll.tax.view")` for reading and `payroll.tax.manage` for recording challans.

**Out of scope:**
- Backend implementation (handled in `W-89.1`, `W-89.2`, `W-89.3`).
- Direct submission to TRACES portal without downloading file.

---

## 4. Flow

```
Payroll Officer --> Clicks "Form 24Q (TDS)" in Payroll navigation
                --> Route /payroll/form24q
                --> Dispatches fetchChallans(fy, quarter) & fetchAnnexure1Preview(fy, quarter)
                --> Views Challan table & reconciliation metrics
                --> Clicks "Record Challan" --> Submits ChallanModal --> Refreshes list
                --> Clicks "Download Annexure I" --> Calls /export endpoint --> Browser receives file
```

---

## 5. Frontend changes

| File | Change |
|---|---|
| `src/payroll/form24q/form24QService.js` (new) | Service functions wrapping `/api/v1/payroll/compliance/challans` and `/api/v1/payroll/compliance/24q/*` |
| `src/payroll/form24q/Form24QPage.jsx` (new) | Main statutory page containing FY selector, quarter tabs, reconciliation metrics, and export buttons |
| `src/payroll/form24q/ChallanList.jsx` (new) | Component rendering the list of recorded challans with allocation status |
| `src/payroll/form24q/ChallanModal.jsx` (new) | Ant Design Modal for entering or editing ITNS 281 challan details with form validation |
| `src/payroll/routes.jsx` | Adds route `/payroll/form24q` mapping to `Form24QPage`, guarded by `payroll.tax.view` |
| `src/payroll/navigation.js` | Adds navigation menu item "Form 24Q" under Payroll › Statutory Reports |

### Routes Added

| Path | Component | Guard / Layout |
|---|---|---|
| `/payroll/form24q` | `Form24QPage` | `AppLayout`, permission `payroll.tax.view` |

---

## 5a. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V195__form24q_actions.sql` | Add `payroll.tax.view` and `payroll.tax.manage` to action catalogue and grant to `platform-admin`, `tenant-admin`, and `payroll-officer` | yes | yes |

---

## 6. Open Questions for CA Review

1. **Menu Placement:** Should Form 24Q sit under **Payroll › Statutory** alongside PF/ESI, or under **Payroll › Tax** alongside Tax Declarations and Form 16?
2. **Reconciliation Lock:** Should the UI forbid downloading the official e-TDS text file if there is an unresolved shortfall between TDS deducted and challan deposits, or only display a prominent warning banner?

---

## 7. Verification

```bash
cd code/frontend && npx vitest run src/payroll/form24q && npm run lint
```
- Unit test `Form24QPage.test.jsx`: tests rendering FY/Quarter selectors, fetching summary data, disabling Annexure II download on Q1–Q3, and enabling on Q4.
- Unit test `ChallanModal.test.jsx`: tests validation rules (BSR code 7 digits, positive amounts, total calculation).
- Unit test `form24QService.test.js`: tests calling the respective backend APIs with correct parameters and handling blob downloads.
