# Feature: W-82 Payroll Module Screens Professional UI Redesign Specification (10/10 UX)

> Part 3 of 3: Payroll Module Screens (Pay Runs, Salary Structure, Statutory, Tax Declarations, Claims, Deductions & Payslips)
> Theme Palette: Sky Blue (`#E0F2FE` / `#BAE6FD`), Royal Blue (`#2563EB` / `#1D4ED8`), Crisp White (`#FFFFFF`), and Soft Slate (`#F8FAFC`).
> Reference Asset: `d:\HCM Project\ui_preview\3_payroll_payrun.jpg`.
> Non-Breaking: Retains 100% of existing financial calculation rules, LOP formulas, statutory engine (EPF, ESI, PT, TDS), and REST API endpoints.

| Field | Value |
|---|---|
| **Feature ID** | W-82 |
| **Owner** | Frontend Architecture & Product Design |
| **Apps touched** | `infinevo-platform/code/frontend` (`@payroll`, `@shell`, `@shared`) |
| **Status** | Approved Specification |
| **Visual Target** | 10/10 Enterprise SaaS (Rippling / Gusto / Stripe / Deel caliber) |
| **Color Scheme** | Sky Blue + Vibrant Blue + Crisp White with Slate neutral hierarchy |

---

## 1. Executive Summary & Design Analysis

### Current State (Before: 5/10)
* **Financial Table Data Misalignment (`3_payroll_payrun.jpg`):**
  * In the main Pay Run calculation table, numerical columns (`Gross Pay`, `PF Deduction`, `PT`, `Net Salary`) are left-aligned or centered instead of **strictly right-aligned**.
  * Currency values use variable-width fonts, causing visual jitter when scanning down columns.
* **Pay Run Stepper & Cockpit:**
  * The step indicator (`Draft ➔ Computed ➔ Approved ➔ Paid`) is functional, but lacks clear step state indications, failure alerts, and detailed execution progress meters.
* **Tax Declarations & Statutory Setup:**
  * Complex IT proof verification screens (80C, 80D, HRA) appear as flat forms without embedded receipt viewers or inline tax impact simulators.
* **Component Configuration:**
  * Earnings and deductions masters lack clean formula builders and taxability badges.

### Target State (After: 10/10 Professional Grade)
* **Financial Table Perfection:**
  * Strict right-alignment for all financial amounts (`₹`) with fixed-width tabular numbers (`font-variant-numeric: tabular-nums`).
  * Zebra or subtle divider striping (`#F8FAFC`) with instant column sorting and search.
* **Pay Run Cockpit Elevation:**
  * Direct match to `3_payroll_payrun.jpg` elevated to 10/10: crisp executive metric cards with sky-blue highlights, modern step timeline, and one-click drill-down line drawer.
* **Officer Verification Workbench:**
  * Dual-pane audit workspace: employee declared proof document (PDF/Image) on the left, line-by-line verification and approval controls on the right.
* **Interactive Digital Payslip:**
  * Premium, bank-grade payslip layout with clear earnings vs deductions breakdown, YTD tax summaries, and PDF download with company branding.

---

## 2. Universal Design System Tokens (Payroll Module)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        COLOR HIERARCHY SYSTEM                         │
├───────────────────┬───────────────────┬────────────────────────────────┤
│ Role              │ Hex Code          │ Application                    │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ Primary Blue      │ #2563EB           │ Primary actions, release CTA   │
│ Primary Dark      │ #1D4ED8           │ Active state, button hover     │
│ Sky Blue Accent   │ #38BDF8           │ Stepper connectors, highlights │
│ Sky Blue Surface  │ #F0F9FF           │ Metric card backgrounds        │
│ Sky Blue Border   │ #BAE6FD           │ Metric card hairline borders   │
│ Pure White        │ #FFFFFF           │ Main card & table background   │
│ Background Grey   │ #F8FAFC           │ Page canvas background         │
│ Hairline Border   │ #E2E8F0           │ Card borders, table dividers   │
│ Dark Slate Head   │ #0F172A           │ Headings, primary currency sum │
│ Body Slate        │ #334155           │ Table labels, regular text     │
│ Success Emerald   │ #059669           │ Calculated, approved, paid     │
│ Warning Amber     │ #D97706           │ Unverified proofs, warnings    │
│ Danger Rose       │ #E11D48           │ High TDS, rejected claims      │
└───────────────────┴───────────────────┴────────────────────────────────┘
```

---

## 3. Screen-by-Screen Before vs. After Specification

Every screen in the Payroll module is catalogued below with zero omissions:

### 3.1 Payroll Dashboard & Pay Run Execution

#### Screen 1: Payroll Executive Dashboard (`/payroll/dashboard`)
* **Route:** `/payroll/dashboard` | **Component:** `DashboardPage.jsx`
* **Before (5/10):** Standard list with raw numbers.
* **After (10/10):**
  * **Payroll Countdown Banner:** Sky-blue gradient card announcing: `October 2026 Pay Run - Due in 4 Days (Cutoff: Oct 25)`. Includes "Start Pay Run" royal blue button.
  * **Executive KPI Trio:**
    1. *Total Disbursed (Last Run):* `₹40,80,440` with month-over-month variance pill (`+2.1%`).
    2. *Total Statutory Liabilities:* `₹4,82,100` (EPF + ESIC + PT + TDS breakdown).
    3. *Total Employees Paid:* `53 Active` with bank transfer status.
  * **Payroll Calendar & Deadlines Card:** Timeline of monthly statutory deadlines (PF E-Sewa due 15th, TDS payment due 7th).

#### Screen 2: Pay Runs History (`/payroll/runs`)
* **Route:** `/payroll/runs` | **Component:** `RunList.jsx`
* **Before (5/10):** Basic table of past runs with plain status tags.
* **After (10/10):**
  * **Header Controls:** Filter by Financial Year, Run Type (`All`, `Regular`, `Off-Cycle`), and primary "+ Create Off-Cycle Run" button.
  * **Run Cards / Table:** Pay period name (e.g. `October 2026 Regular`), Total Gross, Total Deductions, Net Payout, Headcount (`49/53`), Status Pill (`Paid` in Emerald, `Draft` in Sky Blue, `Locked` in Slate), and "View Summary" link.

#### Screen 3: Create Off-Cycle Pay Run (`/payroll/runs/new-off-cycle`)
* **Route:** `/payroll/runs/new-off-cycle` | **Component:** `OffCycleCreate.jsx`
* **Before (5/10):** Cluttered modal with basic inputs.
* **After (10/10):** Clean 3-step wizard:
  * *Step 1: Run Purpose & Dates* (Bonus, Severance, Incentive payout, custom pay period).
  * *Step 2: Employee Selection* (Select all, filter by department, or manual search picker).
  * *Step 3: Component Customization & Gross Overrides*.

#### Screen 4: Pay Run Execution Cockpit (`/payroll/runs/:id`)
* **Route:** `/payroll/runs/:id` | **Component:** `RunPage.jsx`
* **Reference Mockup:** `d:\HCM Project\ui_preview\3_payroll_payrun.jpg`
* **Before (5/10):** Basic table with misaligned numbers, raw status text, and standard buttons.
* **After (10/10):**
  * **Top Header & Actions:**
    * Title: `October 2026 Regular Pay Run` (`fontSize: 24px`, bold `#0F172A`).
    * Dual Action Buttons: Outlined white "Export to Excel" with icon + Solid royal blue "Approve & Release Pay Run" button.
  * **Horizontal Progress Stepper:**
    * Clean interconnected step bar: `1. Draft (Check) ➔ 2. Computed (Check) ➔ 3. Approved (Active Pill) ➔ 4. Paid (Muted)`.
  * **Executive Metric Cards (4 Cards with Sky-Blue Highlights):**
    * *Total Gross Pay:* `₹40,92,720` (Bold 24px).
    * *Total Deductions:* `₹12,280` (Muted red subtitle).
    * *Net Disbursement:* `₹40,80,440` (Bold 24px, Royal Blue accent).
    * *Employees Included:* `49/53` (Showing eligible count).
  * **Employee Pay Run Data Table:**
    * *Headers:* `Employee ID`, `Name`, `Department`, `Gross Pay`, `PF Deduction`, `PT`, `Net Salary`, `Status`.
    * *Financial Formatting:* **All currency amounts strictly right-aligned** with `tabular-nums` formatting.
    * *Status Badge:* Soft rounded pill: `CALCULATED` (`#ECFDF5` bg with `#047857` text).
    * *Interactive Row Click:* Clicking an employee row opens the detailed calculation drawer (`LinesDrawer.jsx`).

#### Screen 5: Employee Payslip Lines Drawer (`LinesDrawer.jsx` inside Run)
* **Route:** Slide-over drawer in `/payroll/runs/:id` | **Component:** `LinesDrawer.jsx`
* **Before (5/10):** Raw JSON-like list of line items.
* **After (10/10):** High-fidelity slide-over drawer: Employee header with LOP days count, Side-by-side Earnings column vs Deductions column, calculation breakdown note (e.g. `Basic Pay = CTC * 50% / 30 * 28 days`), and manual adjustment override box.

---

### 3.2 Salary Components & Employee Compensation Setup

#### Screen 6: Salary Components Master (`/payroll/components`)
* **Route:** `/payroll/components` | **Component:** `ComponentsScreen.jsx`
* **Before (5/10):** Monolithic list of earning/deduction rows.
* **After (10/10):**
  * **Tabbed Categorization:** Tab 1: `Earnings`, Tab 2: `Deductions`, Tab 3: `Reimbursements`, Tab 4: `Statutory Wage Rules`.
  * **Component Cards / Table:** Component Name (e.g. `House Rent Allowance`), Type (`Fixed / Formula-based`), Taxable status badge (`Tax Exempt under Sec 10(13A)`), PF wage eligibility indicator, and status toggle.

#### Screen 7: Employee Salary Setup (`SalaryTab.jsx` in `/employees/:id`)
* **Route:** `/employees/:id` (Salary Tab) | **Component:** `SalaryTab.jsx`
* **Before (5/10):** Basic form with confusing component balancing.
* **After (10/10):**
  * **CTC Overview Card:** Annual CTC input with instant monthly gross auto-calculator.
  * **Component Breakdown Grid:** Dynamic two-column breakdown showing Basic, HRA, Special Allowance, PF Employer share, and Net CTC balancing row.
  * **Revision History Table:** Timeline of past salary revisions with effective dates, percentage hike pill (`+15%`), and revision reason.

---

### 3.3 Reimbursement Claims & Ad-Hoc Deductions

#### Screen 8: Reimbursement Claims Admin List (`/payroll/claims`)
* **Route:** `/payroll/claims` | **Component:** `ClaimList.jsx`
* **Before (5/10):** Plain table with status tags.
* **After (10/10):**
  * **Summary KPI Cards:** `Pending Verification (₹45,200)`, `Approved for Next Run (₹1,20,000)`.
  * **Claim Ledger:** Employee name with avatar, Expense category pill (`Travel`, `Client Meal`, `Internet Allowance`), Amount (Right-aligned bold), attached receipt counter badge (`📎 2 Files`), and quick "Verify" action.

#### Screen 9: Reimbursement Claim Detail & Audit (`/payroll/claims/:id`)
* **Route:** `/payroll/claims/:id` | **Component:** `ClaimDetail.jsx`
* **Before (5/10):** Basic text with raw attachment link.
* **After (10/10):** Dual-column verification modal:
  * *Left Pane:* Interactive document viewer (zoomable receipt image / PDF).
  * *Right Pane:* Claimed amount vs Approved amount input, tax-free exemption flag, payroll cycle assignment, and Approve / Reject buttons.

#### Screen 10: Ad-Hoc & Variable Deductions (`/payroll/deductions`)
* **Route:** `/payroll/deductions` | **Component:** `DeductionList.jsx`, `DeductionGrid.jsx`
* **Before (5/10):** Simple list table.
* **After (10/10):**
  * **Deduction Master Ledger:** Filter by deduction type (`Salary Advance`, `Asset Damage`, `Loan EMI`, `TDS Adjustment`).
  * **Batch Deduction Grid (`DeductionGrid.jsx`):** Rapid spreadsheet-style entry grid for payroll officers to add variable deductions across multiple employees in one click.

#### Screen 11: Self-Service: My Claims (`/me` - Claims Panel)
* **Route:** `/me` (Claims Panel) | **Component:** `MyClaimsPanel.jsx`, `ClaimForm.jsx`
* **Before (5/10):** Basic upload form.
* **After (10/10):** Employee expense reimbursement tracker: Monthly claims counter, "+ Submit New Expense" drawer with camera/file drag-and-drop receipt uploader, auto-extracted date/amount, and real-time approval status timeline.

---

### 3.4 Income Tax Declarations & Form 16

#### Screen 12: Tax Declaration Window Settings (`/payroll/settings/tax-declaration`)
* **Route:** `/payroll/settings/tax-declaration`, `.../:year` | **Component:** `TaxWindowScreen.jsx`
* **Before (5/10):** Unclear date inputs.
* **After (10/10):** Financial year selector (e.g. `FY 2026-27`), window status indicator card (`Open for Provisional Declaration` / `Open for Proof Submission` / `Locked`), submission deadline picker, and "Send Email Reminder" button.

#### Screen 13: Self-Service: Employee Tax Declaration (`/me` - Tax Declaration)
* **Route:** `/me` (taxDeclaration Panel) | **Component:** `DeclarationPage.jsx`
* **Before (5/10):** Giant form with dozens of unorganized input boxes.
* **After (10/10):**
  * **Regime Comparison Widget:** Side-by-side tax liability estimator comparing Old Tax Regime vs New Tax Regime with recommendation badge.
  * **Accordion Sections:**
    * *Section 80C:* PPF, ELSS, Life Insurance, Tuition Fees with dynamic ₹1,50,000 cap meter.
    * *Section 80D:* Health insurance for self & parents with ₹25,000 / ₹50,000 limit bars.
    * *HRA Exemption:* Landlord PAN, address, and monthly rent receipt uploader.
    * *Other Income / Home Loan Interest (Sec 24b)*.

#### Screen 14: Officer Tax Declaration Audit View (`/employees/:employeeId/tax-declaration/:fy`)
* **Route:** `/employees/:employeeId/tax-declaration/:fy` | **Component:** `OfficerDeclarationView.jsx`
* **Before (5/10):** Long text list with download links.
* **After (10/10):** Payroll Officer verification workbench:
  * Table of employee declared items: Declared Amount, Verified Amount, Proof Status (`Pending Verification`, `Approved`, `Rejected`), and embedded instant preview drawer for uploaded bills.
  * "Freeze & Accept for Payroll" royal blue button.

#### Screen 15: Form 16 Management Portal (`/payroll/tax/form-16`, `/payroll/form-16`)
* **Route:** `/payroll/tax/form-16` | **Component:** `Form16Page.jsx`
* **Before (5/10):** File list with upload button.
* **After (10/10):** Form 16 generator cockpit:
  * Assessment Year selector (e.g. `AY 2027-28`).
  * Bulk Part A & Part B merger tool, Digital Signature (DSC) upload status, and 1-click "Publish to Employee Portals" batch distributor.

---

### 3.5 Statutory & Payroll Settings Layout

#### Screen 16: Payroll Settings Layout Frame (`/payroll/settings`)
* **Route:** `/payroll/settings` | **Component:** `SettingsLayout.jsx`
* **Before (5/10):** Basic tab navigation.
* **After (10/10):** Modern left-rail settings navigation with clean icon tabs: `Pay Schedule`, `EPF`, `ESI`, `Professional Tax`, `FBP Plan`, `Tax Windows`.

#### Screen 17: Pay Schedule Settings (`/payroll/settings/pay-schedule`)
* **Route:** `/payroll/settings/pay-schedule` | **Component:** `PayScheduleScreen.jsx`
* **Before (5/10):** Plain input boxes.
* **After (10/10):** Visual pay calendar simulator showing selected pay cycle days (1st to 30th/31st), payroll processing cutoff day, and salary payment date with annual holiday calendar sync toggle.

#### Screen 18: EPF Settings (`/payroll/settings/epf`)
* **Route:** `/payroll/settings/epf` | **Component:** `EpfScreen.jsx`
* **Before (5/10):** Unstyled checkboxes.
* **After (10/10):** Statutory rule card: 12% contribution split rule, ₹15,000 wage ceiling toggle, EDLI admin charge switch, and EPS eligibility rules.

#### Screen 19: ESI Settings (`/payroll/settings/esi`)
* **Route:** `/payroll/settings/esi` | **Component:** `EsiScreen.jsx`
* **Before (5/10):** Basic percentage inputs.
* **After (10/10):** Statutory ESIC rule card: 0.75% employee & 3.25% employer rate toggles, ₹21,000 wage threshold alert, and branch exemption mapping.

#### Screen 20: Professional Tax (PT) Settings (`/payroll/settings/professional-tax`)
* **Route:** `/payroll/settings/professional-tax` | **Component:** `ProfessionalTaxScreen.jsx`
* **Before (5/10):** Flat state list.
* **After (10/10):** State-by-state PT card selector (Karnataka, Maharashtra, Tamil Nadu, etc.) with salary slab schedule tables and February surcharge rules.

#### Screen 21: Flexible Benefit Plan (FBP) Configuration (`/payroll/settings/fbp`)
* **Route:** `/payroll/settings/fbp` | **Component:** `FbpPlanScreen.jsx`
* **Before (5/10):** Simple text fields.
* **After (10/10):** FBP allowance builder: define eligible components (Meal card, Fuel, LTA, Books) with monthly caps and tax exemption conditions.

#### Screen 22: Employee FBP Selection Tab (`FbpDeclarationTab.jsx`)
* **Route:** Sub-tab in employee profile & `/me` portal | **Component:** `FbpDeclarationTab.jsx`
* **Before (5/10):** Basic form.
* **After (10/10):** Visual allowance basket allocation slider where employees distribute their flexible allowance pool with instant take-home pay preview.

---

### 3.6 Prior Payroll, Payslips & Public Verification

#### Screen 23: Prior Payroll Import (`/payroll/prior-payroll`)
* **Route:** `/payroll/prior-payroll` | **Component:** `PriorPayrollPage.jsx`
* **Before (5/10):** Raw file uploader.
* **After (10/10):** Mid-year migration wizard: Download Excel template, upload prior YTD earnings/deductions/TDS deducted by previous employer or legacy system, with column validation grid.

#### Screen 24: Self-Service: My Payslips Archive (`/me` - Payslips Panel)
* **Route:** `/me` (Payslips Panel) | **Component:** `MyPayslips.jsx`
* **Before (5/10):** Plain list of links.
* **After (10/10):** Modern digital payslip gallery: Card for each month displaying Net Pay in large bold font (`₹1,24,500`), Net Paydate, interactive "View Slip" button, and 1-click "Download PDF" button.

#### Screen 25: Interactive Payslip View (`/me/payslips/:payrunId`)
* **Route:** `/me/payslips/:payrunId` | **Component:** `PayslipPage.jsx`
* **Before (5/10):** Basic web page representation.
* **After (10/10):** Executive bank-grade digital payslip:
  * Company logo, address, and CIN at top.
  * Employee identity badge (Name, ID, Department, Bank Account masked, UAN, PAN).
  * 2-Column Ledger: Earnings on left, Deductions on right.
  * Net Payable highlighted in royal blue callout box with amount in words.
  * YTD summary table and digital verification QR code.

#### Screen 26: Public Secure Payslip Verification (`/public/payslips`)
* **Route:** `/public/payslips` | **Component:** `PublicPayslip.jsx`
* **Before (5/10):** Plain document viewer.
* **After (10/10):** Secure, authenticated public verification page with official company watermark, document validity green seal, and read-only verification view for background checkers and financial lenders.

---

## 4. Verification & Visual QA Acceptance Criteria

1. **Financial Precision:** All currency amounts right-aligned with fixed tabular typography (`tabular-nums`).
2. **Pay Run Alignment:** Pay run cockpit matches `3_payroll_payrun.jpg` with updated metric cards, progress stepper, and line item drawer.
3. **Calculation Integrity:** Payslip earnings, deductions, PF, PT, and Net Pay match backend figures without mathematical drift.
4. **Theme Cohesion:** Sky blue surfaces (`#F0F7FF`) and royal blue actions (`#2563EB`) implemented uniformly.

