# Feature: Mobile Payslips & Signed PDF Download

| Field | Value |
|---|---|
| **Feature ID** | `W-98.4` · `PAYROLL-MOBILE-PAYSLIPS` |
| **Promoted to** | `docs/target-state/features/W-98-4-mobile-payslips-signed-download.md` — **`W-98-4` with hyphens**, never `W-98.4` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend` (`src/mobile/payslips/`, `src/mobile/MobileTabBar.jsx`), `code/backend/payroll` |
| **Related gaps** | founder 2026-11-13 planned product roadmap (`W-98` Mobile app slice 4: Payslips) |
| **Status** | **Built on `feature/krushna-specs` (100% tests/lint green, pending manager review)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | `W-98.1` (mobile foundation & auth container) |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `payroll` (payslip endpoints) | 1 | **Pass (1 module)** |
| Flyway migration | None (creates 0 tables; uses existing `payroll.employee_payrun_line`) | 1 | **Pass (0 migrations)** |
| Externally testable behaviour | Employee opens mobile Payslips tab, views chronological monthly pay summaries, taps to expand salary breakdown, and downloads/shares the official signed PDF payslip to the device | 1 | **Pass (1 behaviour)** |
| Frontend area | `mobile/payslips` (`src/mobile/payslips/`) | 1 | **Pass (1 area)** |

Within size cap across all four axes. No split required.

---

## 1. Problem

Accessing monthly payslips is the single most frequent employee self-service task on mobile devices:
1. **Desktop payslip view is formatted for A4 print**: The existing `PayslipPage.jsx` (`code/frontend/src/shell/portal/PayslipPage.jsx:1-200`) is styled as a fixed-width A4 document with multi-column table layouts that require heavy horizontal zooming on mobile screens.
2. **Missing native file download on mobile webview**: In a hybrid native app, standard browser blob download (`<a download="payslip.pdf">`) fails or is silently blocked by Android and iOS webview sandboxes without native filesystem permissions.
3. **Employees expect 1-tap sharing**: Mobile workers expect to save their payslip PDF to device storage or share it directly via native OS share sheets (WhatsApp, Gmail, AirDrop, Google Drive) for loan, tax, or visa applications.

---

## 2. Scope

### In scope
- **Mobile Payslips Screen (`src/mobile/payslips/MobilePayslipsScreen.jsx`)**:
  - Mounted under the bottom navigation "Payslips" tab (`/me/payslips`).
  - Latest payslip showcase banner: Large card highlighting the most recent paid month (e.g. "October 2026", Net Pay in bold `MoneyText`, Paid On date, "Download PDF" button).
  - Monthly history list: Grouped by Financial Year, listing previous months with Net Pay and Disbursed status.
  - Pull-to-refresh to fetch newly approved pay runs.
- **Mobile Payslip Breakdown Sheet (`src/mobile/payslips/MobilePayslipDetailSheet.jsx`)**:
  - Sliding bottom sheet displaying itemized salary components:
    - **Earnings**: Basic, HRA, Special Allowance, Reimbursements.
    - **Deductions**: Employee PF, ESI, Professional Tax, TDS.
    - **Net Pay Highlight**: Prominent net take-home figure.
    - Attendance summary: Payable days, Loss of pay days.
- **Native PDF Download & Share**:
  - Leverages `@capacitor/filesystem` to stream the official signed PDF payslip and save it directly to the device's public `Documents` / `Downloads` folder.
  - Leverages `@capacitor/share` to open the native OS share sheet (`Share.share({ title: 'Payslip Oct 2026', url: fileUri })`).
  - Uses existing signed download URL (`GET /api/v1/payroll/payslips/{payrunId}/download` or signed link).

### Out of scope
- Off-cycle pay run creation or salary computation (payroll officer desktop workflows).
- Year-end Form 16 Part A/B generation (owned by `W-36.4`/`W-36.5`).
- Modification of employee CTC structure.

---

## 3. Flow

```
[Employee Taps Payslips Tab on Mobile App]
                     │
                     ▼
[GET /api/v1/payroll/payslips/my]
  ├── Returns List of Published Payslips (Latest Month First)
                     │
                     ▼
[Mobile UI Renders Showcase Card (Latest Month) & History List]
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
[Tap "View Details"]    [Tap "Download / Share PDF"]
         │                       │
         ▼                       ▼
[Mobile Detail Sheet]   [Fetch Signed PDF Blob from Server]
  • Earnings List                │
  • Deductions List              ▼
  • Net Pay              [@capacitor/filesystem Writes PDF to Local Storage]
                                 │
                                 ▼
                         [@capacitor/share Opens Native Share Sheet]
                           (Save to Files / Share via WhatsApp / Email)
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/payroll`)

> No new database tables or logic required. Uses existing validated Payroll payslip endpoints:
- `GET /api/v1/payroll/payslips/my` (`PayslipController.java`)
- `GET /api/v1/payroll/payslips/{payrunId}` (`PayslipController.java`)
- `GET /api/v1/payroll/payslips/{payrunId}/download` (`PayslipController.java`)

### 4.2 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| Package | `code/frontend/package.json` | Add `@capacitor/filesystem: ^6.0.0` and `@capacitor/share: ^6.0.0`. |
| Service | `code/frontend/src/mobile/payslips/mobilePayslipService.js` | **New**. Client for fetching published payslips and saving/sharing PDF files using Capacitor plugins. |
| Screen | `code/frontend/src/mobile/payslips/MobilePayslipsScreen.jsx` | **New**. Mobile view displaying latest pay banner, historical months list, and download buttons. |
| Sheet | `code/frontend/src/mobile/payslips/MobilePayslipDetailSheet.jsx` | **New**. Touch-friendly bottom drawer showing earnings, deductions, net salary, and attendance days. |
| Router | `code/frontend/src/mobile/MobileShell.jsx` | Route `/me/payslips` to `MobilePayslipsScreen` on mobile native container. |

---

## 5. Database Changes

> **Creates no database tables and executes 0 Flyway migrations.**

- [x] Standing rule: Reuses existing `payroll.employee_payrun_line` and `payroll.pay_run` tables.
- [x] Standing rule: Multi-tenant safety enforced by tenant context filter.
- [x] Standing rule: Money amounts formatted strictly through `MoneyText` / `BigDecimal`.

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Component | `code/frontend/src/mobile/payslips/MobilePayslipsScreen.test.jsx` | Renders latest payslip card with formatted net pay; renders history rows; verifies empty state when no pay runs published. |
| Component | `code/frontend/src/mobile/payslips/MobilePayslipDetailSheet.test.jsx` | Renders itemized earnings and deductions; verifies totals match net pay; tests download button click. |
| Unit | `code/frontend/src/mobile/payslips/mobilePayslipService.test.js` | Mocks Capacitor Filesystem and Share plugins; verifies file path resolution and share invocation. |

---

## 7. Verification

```bash
# 1. Run payroll backend payslip tests
./mvnw -pl payroll test -Dtest='PayslipControllerTest,PayslipServiceImplTest'

# 2. Run mobile payslips frontend tests
cd code/frontend
npm test -- src/mobile/payslips/

# 3. Frontend lint check
npm run lint
```

| Check | Action | Expected Result |
|---|---|---|
| List Payslips | Open Payslips tab on mobile | Published payslips displayed with currency formatted (e.g. `₹ 65,420.00`). |
| View Details | Tap on a month row | Bottom drawer expands showing Gross Earnings, Deductions, and Net Pay breakdown. |
| Native PDF Share | Tap Share button | Native OS share sheet opens with the saved PDF file attachment. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Storage permission denied on older Android versions | Low (Android 10+ uses scoped storage) | Use Capacitor Filesystem `Directory.Cache` or `Directory.Documents` which requires no legacy external storage permissions. |
| Corrupted PDF download on interrupted network | Low | Verify blob MIME type (`application/pdf`) and byte length before writing to filesystem. |

---

## 9. Rollback

1. Additive mobile screen; desktop portal payslips (`MyPayslips.jsx`, `PayslipPage.jsx`) continue unchanged.

