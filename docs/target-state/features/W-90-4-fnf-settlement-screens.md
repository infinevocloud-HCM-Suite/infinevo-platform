# Feature: Full and Final (F&F) Settlement Screens & Statement Viewer

| Field | Value |
|---|---|
| **Feature ID** | `W-90.4` · `FE-PAYROLL` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/frontend` (`src/payroll/settlement`, routes) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready for CA / founder review |
| **Blocked by** | `W-90.3` (F&F Compute) |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | HR/Payroll officer initiates, reviews, approves, and prints F&F settlement statements | 1 |
| Frontend area | `payroll/settlement` | 1 |

---

## 2. Problem & Context

Currently, the frontend has pay run screens (`W-47.2`) and tax screens (`W-47.3`), but has no dedicated interface to initiate, inspect, and approve exit settlements. Payroll officers must calculate final dues offline without visibility into automated leave balances, gratuity, or tax caps.

---

## 3. Scope

**In scope:**
- Route `/payroll/settlements` (`FnfListPage.jsx`):
  - Table of exiting employees with status (`DRAFT`, `APPROVED`, `PAID`).
  - Search by employee name, filter by status and date range.
  - "Initiate Settlement" button opening an initiation modal.
- Route `/payroll/settlements/:id` (`FnfStatementPage.jsx`):
  - Employee header: Name, ID, Designation, Joining Date, Exit Date, Total Tenure.
  - Breakdown grid:
    - **Earnings:** Pro-rated Exit Salary, Leave Encashment, Gratuity, Notice Pay Payout.
    - **Deductions:** Notice Recovery, Loan Deductions, Statutory Deductions (EPF, ESI, PT, Final TDS).
    - **Net Summary:** Bold Net Payable amount with currency formatting.
  - Action buttons based on status:
    - `Approve Settlement` (requires `payroll.settlement.approve`).
    - `Mark as Paid` (requires `payroll.settlement.pay`).
    - `Download Settlement Statement (PDF)`.
- Guarded by `@RequiresAction("payroll.settlement.view")`.

**Out of scope:**
- Backend calculation logic (handled in `W-90.1`, `W-90.2`, `W-90.3`, `W-91`).

---

## 4. Flow

```
Payroll Officer --> Route /payroll/settlements
                --> Clicks "Initiate Settlement" --> Selects Employee & Exit Date
                --> Dispatches initiateSettlement --> Navigates to /payroll/settlements/:id
                --> Inspects calculated amounts (Leave, Gratuity, Notice, Tax)
                --> Clicks "Approve" --> Clicks "Mark as Paid"
                --> Clicks "Download Statement (PDF)"
```

---

## 5. Frontend changes

| File | Change |
|---|---|
| `src/payroll/settlement/fnfService.js` (new) | Service API client for `/api/v1/payroll/fnf/*` |
| `src/payroll/settlement/FnfListPage.jsx` (new) | List of settlements with status tags and initiate action |
| `src/payroll/settlement/FnfStatementPage.jsx` (new) | Detailed statement viewer with itemized earnings & deductions |
| `src/payroll/settlement/InitiateFnfModal.jsx` (new) | Modal to select exiting employee and set last working day |
| `src/payroll/routes.jsx` | Adds routes `/payroll/settlements` and `/payroll/settlements/:id` |
| `src/payroll/navigation.js` | Adds "Exit Settlements" menu item under Payroll |

---

## 6. Open Questions for CA Review

1. **Self-Service Access:** Should the exiting employee have read-only access to view and download their F&F settlement slip under `/me/settlement` after final approval, or is it emailed as a PDF?
2. **Settlement Slip Format:** Confirm standard layout headers (Company TAN, PAN, Employee PAN, Bank Account, UAN, Days worked).

---

## 7. Verification

```bash
cd code/frontend && npx vitest run src/payroll/settlement && npm run lint
```
- Unit test `FnfListPage.test.jsx`: tests rendering list, filtering by status, and opening initiate modal.
- Unit test `FnfStatementPage.test.jsx`: tests rendering earnings vs deductions cards and displaying net payable figure.
