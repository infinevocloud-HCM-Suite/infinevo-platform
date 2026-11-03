# Feature: W-80 Core Module Screens Professional UI Redesign Specification (10/10 UX)

> Part 1 of 3: Core Module Screens
> Theme Palette: Sky Blue (`#E0F2FE` / `#38BDF8`), Royal Blue (`#1D4ED8` / `#2563EB`), Crisp White (`#FFFFFF`), and Clean Slate (`#F8FAFC`).
> Non-Breaking: Retains 100% of existing functional contracts, routes, and business logic while completely transforming visual design from 5/10 prototype to 10/10 Tier-1 enterprise SaaS.

| Field | Value |
|---|---|
| **Feature ID** | W-80 |
| **Owner** | Frontend Architecture & Product Design |
| **Apps touched** | `infinevo-platform/code/frontend` (`@core`, `@shell`, `@shared`) |
| **Status** | Approved Specification |
| **Visual Target** | 10/10 Enterprise SaaS (Linear / Rippling / Stripe caliber) |
| **Color Scheme** | Sky Blue + Vibrant Blue + Crisp White with Slate neutral hierarchy |

---

## 1. Executive Summary & Design Analysis

### Current State (Before: 5/10)
* **Visual Impression:** Relies heavily on generic Ant Design 5 defaults with monotonous `#1677ff` buttons and high-saturation badge tags.
* **Layout Deficiencies:** Dense tables lack breathing space, inconsistent card paddings, and arbitrary border outlines.
* **Information Architecture:** Complex forms (e.g. Employee onboarding, Org locations) appear as unbroken vertical text-input walls without clear section groupings or contextual helper prompts.
* **Approvals & Requests:** Generic list tables that fail to communicate approval urgency or multi-tier chain states.

### Target State (After: 10/10 Professional Grade)
* **Color System:** Harmonious sky-blue tints (`#F0F7FF`, `#E0F2FE`), vibrant royal blue actions (`#2563EB`), pure white cards (`#FFFFFF`), and refined slate borders (`#E2E8F0`).
* **Visual Polish:** Soft multi-stop shadows (`0 1px 3px 0 rgba(15, 23, 42, 0.05)`), `12px` rounded corners, and elevated micro-interactions.
* **Data Presentation:** Crisp avatar badges, clear key-value definition lists with tabular figures, status pills with soft pastel fills, and contextual step wizards.

---

## 2. Universal Design System Tokens (Core Module)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        COLOR HIERARCHY SYSTEM                         │
├───────────────────┬───────────────────┬────────────────────────────────┤
│ Role              │ Hex Code          │ Application                    │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ Primary Blue      │ #2563EB           │ Primary actions, active tabs   │
│ Primary Hover     │ #1D4ED8           │ Button hover states, focus     │
│ Sky Blue Accent   │ #38BDF8           │ Progress rings, subtle badges  │
│ Sky Blue Tint     │ #F0F7FF           │ Surface highlights, card tints │
│ Sky Blue Border   │ #BAE6FD           │ Active card borders            │
│ Pure White        │ #FFFFFF           │ Elevated card backgrounds      │
│ App Background    │ #F8FAFC           │ Page canvas background         │
│ Hairline Border   │ #E2E8F0           │ Card borders, dividers         │
│ Dark Slate Head   │ #0F172A           │ Page headers, primary numbers  │
│ Body Slate        │ #334155           │ Field labels, standard copy    │
│ Muted Slate       │ #64748B           │ Secondary metadata, hints      │
└───────────────────┴───────────────────┴────────────────────────────────┘
```

---

## 3. Screen-by-Screen Before vs. After Specification

Every screen in the Core module is catalogued below with zero omissions:

### 3.1 People & Employee Management

#### Screen 1: Employee Directory (`/employees`)
* **Route:** `/employees` | **Component:** `EmployeeList.jsx`
* **Before (5/10):** Standard raw Ant Design table with default borders. Status is a plain text tag. Filters are crammed into a simple header row. Actions are small text links ("Edit", "View").
* **After (10/10):**
  * **Header Banner:** Clean title with active headcount badge ("53 Active Employees"), primary "Add Employee" royal blue button (`#2563EB`) with icon, and secondary "Import CSV" button.
  * **Search & Filter Bar:** Full-width rounded input with embedded search icon, alongside dropdown filter pills (Department, Location, Employment Status) with clear counts.
  * **Table UX:** Elevated card table with subtle hover row background (`#F8FAFC`).
    * *Avatar Column:* High-resolution photo avatar or initials with soft sky-blue badge (`#E0F2FE`).
    * *Employee Identity:* Bold name (`#0F172A`), subtitle with Employee ID and official email in muted slate (`#64748B`).
    * *Department & Designation:* Formatted as two distinct lines (Designation bold, Department secondary).
    * *Status Badge:* Soft rounded pill: Active (`#ECFDF5` bg, `#059669` text), On Leave (`#EFF6FF` bg, `#2563EB` text), Terminated (`#FFF1F2` bg, `#E11D48` text).
    * *Actions:* Hover-revealed three-dot menu icon drawer.

#### Screen 2: Add New Employee (`/employees/new`)
* **Route:** `/employees/new` | **Component:** `EmployeeCreate.jsx`
* **Before (5/10):** Long continuous single-column form with basic input fields and generic submit button. Easy to cause cognitive fatigue.
* **After (10/10):**
  * **Progress Stepper:** Clean 4-step horizontal breadcrumb indicator at top: `1. Basic Info ➔ 2. Work & Org ➔ 3. Statutory & Identity ➔ 4. Review & Create`. Completed steps highlighted with Sky Blue checks.
  * **Form Sections:** 2-column card grid with section headers featuring subtle blue left-accent borders (`3px solid #2563EB`).
  * **Input Styling:** Rounded `8px` inputs with floating labels, clear placeholder examples, and real-time validation badges (PAN format verification, IFSC lookup auto-fill).
  * **Action Footer:** Sticky bottom bar with "Save Draft" (secondary white button) and "Continue to Next Step" (royal blue).

#### Screen 3: Bulk Employee Import (`/employees/import`)
* **Route:** `/employees/import` | **Component:** `EmployeeImport.jsx`
* **Before (5/10):** Basic default upload box with little guidance on errors or CSV format mismatch.
* **After (10/10):**
  * **Step 1 Card (Template):** Downloadable sample CSV card with a 1-click button and visual column legend.
  * **Step 2 Card (Drag & Drop):** Large dashed sky-blue dropzone (`border: 2px dashed #BAE6FD`, `background: #F0F7FF`) with upload cloud icon and file size guidance.
  * **Step 3 Card (Pre-validation Grid):** Real-time validation table showing parsed rows: green check for valid records, soft red pill tags highlighting exact erroneous cells with tooltip error messages (e.g., "Invalid PAN format").
  * **Submit Bar:** Shows dynamic counts: "48 Valid Rows Ready", "2 Errors to Fix".

#### Screen 4: Employee 360 Profile (`/employees/:id`)
* **Route:** `/employees/:id` | **Component:** `EmployeePage.jsx`
* **Before (5/10):** Generic Ant tabs with flat card layouts. Content looks squished with minimal visual distinction between sections.
* **After (10/10):**
  * **Hero Header Card:** Pure white elevated card (`#FFFFFF`) with sky-blue banner strip. Features employee large avatar, full name (`fontSize: 24px`, bold), employee code badge, designation, department, work location, and phone/email badges with direct action icons. Quick status toggle on top right.
  * **Tab Navigation:** Segmented tab pills with smooth underline transition in Royal Blue.
  * **Tabs Included:**
    1. *Overview Tab (`OverviewTab.jsx`):* Quick KPI summary cards (Attendance this month, Remaining leaves, Current manager card with direct 1-click profile link).
    2. *Personal & Contact Tab (`SectionTab.jsx`):* Clean 2-column key-value grid with muted labels and bold values; inline edit button triggers an elegant slide-over drawer.
    3. *Work & Organization Tab:* Clear org hierarchy tree diagram, joining date, probation status indicator, and contract type badge.
    4. *Reporting Line Tab (`ReportingLineTab.jsx`):* Visual tree node showing Direct Manager, Skip-level Manager, and Direct Reports with mini avatar cards.
    5. *Statutory & Identity Tab:* Secure masked view of Aadhaar/PAN with copy icon and verify status indicator.
    6. *Bank Details Tab:* Card-style bank passbook layout with verified green seal badge.
    7. *Documents Tab (`DocumentsTab.jsx`):* Grid of document preview cards (Aadhaar, Degree, Offer Letter) with file type icons (PDF/Image), upload date, and download/preview buttons.
    8. *Employee Leave History (`/employees/:id/leave`):* Leave ledger table showing opening quota, consumed days, pending requests, and LOP deductions.
  * **Terminate / Exit Modal (`TerminateModal.jsx`):** High-clarity confirmation dialog with warning alert banner, last working day calendar picker, exit reason dropdown, and dual-confirm button.

---

### 3.2 Organisation Structure

#### Screen 5: Departments (`/org/departments`)
* **Route:** `/org/departments` | **Component:** `Departments.jsx`
* **Before (5/10):** Flat list table showing department name and code.
* **After (10/10):** Card-grid or tree view option. Each department card displays Department Name, Cost Center Code, Head of Department avatar card, active headcount badge, and quick "+ Add Sub-Department" action.

#### Screen 6: Designations (`/org/designations`)
* **Route:** `/org/designations` | **Component:** `Designations.jsx`
* **Before (5/10):** Simple table of designation names.
* **After (10/10):** Hierarchy-ordered list with level indicators (e.g. L1 - Executive, L2 - Lead, L3 - Director), employee count badges, and inline edit modal.

#### Screen 7: Work Locations / Branches (`/org/work-locations`)
* **Route:** `/org/work-locations` | **Component:** `WorkLocations.jsx`
* **Before (5/10):** Basic table with address text.
* **After (10/10):** Visual branch cards with map pin icon, state/PT jurisdiction badge, SEZ indicator pill, and employee headcount tag.

#### Screen 8: Add / Edit Work Location (`/org/work-locations/new` & `/:id/edit`)
* **Route:** `/org/work-locations/new`, `/org/work-locations/:id/edit` | **Component:** `WorkLocationForm.jsx`
* **Before (5/10):** Standard form inputs with standard submit button.
* **After (10/10):** Organized 2-section card layout: Section 1 (Branch Details & Address), Section 2 (Tax & Statutory Details, PT State registration, Establishment codes).

---

### 3.3 Approvals & Workflow Engine

#### Screen 9: Approvals Inbox (`/approvals`)
* **Route:** `/approvals` | **Component:** `Inbox.jsx`
* **Before (5/10):** Standard Ant table with action buttons. Hard to distinguish leave requests from timesheets.
* **After (10/10):**
  * **Top Metrics:** 3 summary cards (Total Pending [Sky Blue], Urgent > 48h [Soft Amber], Processed This Week [Soft Green]).
  * **Filter Segment:** Multi-entity pills: `All`, `Leave Requests`, `WFH`, `Timesheets`, `Reimbursements`.
  * **Request Row Card:** Requester avatar, request category badge, date range, concise reason, and one-click quick `Approve` (green) / `Reject` (red) buttons directly in the row.

#### Screen 10: Approval Instance Detail (`/approvals/:instanceId`)
* **Route:** `/approvals/:instanceId` | **Component:** `InstanceDetail.jsx`
* **Before (5/10):** Simple text layout with approve/reject buttons.
* **After (10/10):**
  * **Visual Audit Stepper:** Vertical graphical timeline showing: `1. Submitted by Employee (Timestamp) ➔ 2. Approved by Manager ➔ 3. Current Step (Pending HR Review) ➔ 4. Final Execution`.
  * **Contextual Data Box:** Sky-blue tint container displaying all request details and attachments.
  * **Decision Bar:** Floating footer with remarks textarea and prominent Approve/Reject actions.

#### Screen 11: Delegations (`/approvals/delegations`)
* **Route:** `/approvals/delegations` | **Component:** `Delegations.jsx`
* **Before (5/10):** Flat table of past delegations.
* **After (10/10):** Active delegation card banner (showing active proxy, date window, and "Revoke" button) + historical delegation ledger.

#### Screen 12: Approval Definitions / Workflows (`/approvals/definitions`)
* **Route:** `/approvals/definitions` | **Component:** `Definitions.jsx`
* **Before (5/10):** Raw rule list with confusing step indicators.
* **After (10/10):** Visual workflow builder diagram where administrators can configure multi-level chains (Level 1: Reporting Manager, Level 2: Department Head, Escalation after 48h).

---

### 3.4 Holiday Calendars

#### Screen 13: Holiday Calendars (`/holidays`, `/holidays/new`, `/:id/edit`)
* **Route:** `/holidays` | **Component:** `Calendars.jsx`
* **Before (5/10):** Basic table of calendar names.
* **After (10/10):** Location-tagged cards (e.g., "Bangalore HQ Calendar", "Mumbai Office") showing total declared holidays, upcoming holiday pill, and edit action.

#### Screen 14: Calendar Holiday List (`/holidays/:id`)
* **Route:** `/holidays/:id` | **Component:** `CalendarHolidays.jsx`
* **Before (5/10):** Simple date and holiday name table.
* **After (10/10):** Interactive dual view (Calendar View + Month-wise Accordion). Holiday pills color-coded by type: Mandatory (Royal Blue), Optional/Restricted (Sky Blue).

#### Screen 15: Holiday Lookup (`/holidays/lookup`)
* **Route:** `/holidays/lookup` | **Component:** `HolidayLookup.jsx`
* **Before (5/10):** Dropdown and result box.
* **After (10/10):** Interactive widget allowing employees and HR to pick any date and instantly see if it is a working day, weekend, or holiday for any branch.

---

### 3.5 Leave Management

#### Screen 16: Leave Types & Policies (`/leave/types`)
* **Route:** `/leave/types` | **Component:** `LeaveTypes.jsx`
* **Before (5/10):** Monolithic table of policy rules.
* **After (10/10):** Elegant policy cards for each leave type (Casual Leave, Sick Leave, Earned Leave, Maternity, Paternity) showing annual quota, accrual frequency (Monthly/Annual), carry-forward cap, and LOP rules.

#### Screen 17: Leave Allocations (`/leave/allocations`)
* **Route:** `/leave/allocations` | **Component:** `Allocations.jsx`
* **Before (5/10):** Dense table of employee balances.
* **After (10/10):** Department-grouped table with search, balance adjustment modal with reason field, and "+ Bulk Credit Annual Leave" button.

#### Screen 18: Leave Requests (Admin View) (`/leave/requests`)
* **Route:** `/leave/requests` | **Component:** `LeaveRequests.jsx`
* **Before (5/10):** Standard table with basic status tags.
* **After (10/10):** Rich data table with employee avatar, leave type badge, date range with total days pill (e.g. `2.5 Days`), reason preview tooltip, and quick approval controls.

#### Screen 19: Record Leave (On Behalf) (`/leave/requests/new`)
* **Route:** `/leave/requests/new` | **Component:** `RecordLeave.jsx`
* **Before (5/10):** Standard form.
* **After (10/10):** Clean modal/drawer featuring searchable employee selector with instant balance counter preview before submitting.

#### Screen 20: Leave Request Detail (`/leave/requests/:id`)
* **Route:** `/leave/requests/:id` | **Component:** `LeaveRequestDetail.jsx`
* **Before (5/10):** Flat key-value text.
* **After (10/10):** Card summary with applicant profile, balance impact simulator (Shows balance before and balance after deduction), attached medical certificate previewer, and approval chain.

#### Screen 21: Leave Data Import (`/leave/import`)
* **Route:** `/leave/import` | **Component:** `LeaveImport.jsx`
* **Before (5/10):** Basic upload button.
* **After (10/10):** 3-stage visual wizard: Template Download ➔ Dropzone Upload ➔ Data Integrity Validation Grid.

---

### 3.6 Work From Home (WFH)

#### Screen 22: WFH Policies (`/wfh/policies`)
* **Route:** `/wfh/policies` | **Component:** `WfhPolicies.jsx`
* **Before (5/10):** Simple policy list.
* **After (10/10):** Modern policy card grid with monthly allowable limits, advance notice requirements, and manager approval toggle.

#### Screen 23: WFH Allocations (`/wfh/allocations`)
* **Route:** `/wfh/allocations` | **Component:** `WfhAllocations.jsx`
* **Before (5/10):** Basic employee balance table.
* **After (10/10):** Clear table with remaining WFH quota bars and quick bulk-adjust controls.

#### Screen 24: WFH Requests (Admin Log) (`/wfh/requests`)
* **Route:** `/wfh/requests` | **Component:** `WfhRequests.jsx`
* **Before (5/10):** Standard list table.
* **After (10/10):** Elevated table with date range chips, reason preview, and quick status filter tabs (`Pending`, `Approved`, `Rejected`).

#### Screen 25: WFH Data Import (`/wfh/import`)
* **Route:** `/wfh/import` | **Component:** `WfhImport.jsx`
* **Before (5/10):** Raw file uploader.
* **After (10/10):** Polished sky-blue dropzone with real-time CSV row validation and error highlighting.

---

### 3.7 Access & System Administration

#### Screen 26: Users & Access (`/users`)
* **Route:** `/users` | **Component:** `UsersScreen.jsx`
* **Before (5/10):** Single flat table combining active accounts and invitations.
* **After (10/10):**
  * **Segmented Navigation:** Tab 1: `Active Users`, Tab 2: `Pending Invitations`, Tab 3: `Deactivated`.
  * **User Row:** Profile avatar, role badge (Admin: Purple, HR: Blue, Employee: Neutral), Keycloak SSO status indicator, and "+ Invite Users" modal.

#### Screen 27: Roles & Permissions (`/roles`)
* **Route:** `/roles` | **Component:** `RolesScreen.jsx`
* **Before (5/10):** Plain list of roles.
* **After (10/10):** Interactive role permission matrix: role selector on left, functional modules and granular permissions checkbox grid on right.

#### Screen 28: System Audit Logs (`/audit`)
* **Route:** `/audit` | **Component:** `AuditLogScreen.jsx`
* **Before (5/10):** Dense table with JSON text dumps.
* **After (10/10):** Clean activity log with actor badge, action verb pill (`Created`, `Updated`, `Deleted`), target entity link, timestamp with relative time, and "View JSON Diff" drawer.

#### Screen 29: Setup Checklist (`/setup`)
* **Route:** `/setup` | **Component:** `SetupChecklist.jsx`
* **Before (5/10):** Unformatted checklist.
* **After (10/10):** Gamified onboarding stepper with circular progress ring (`65% Completed`), completed check items with green badges, and 1-click "Configure Now" deep-links.

#### Screen 30: Company Profile (`/settings/company`)
* **Route:** `/settings/company` | **Component:** `CompanyProfile.jsx`
* **Before (5/10):** Standard form inputs.
* **After (10/10):** Brand identity studio card: logo upload with instant live header preview, legal details card, and official registered address card.

#### Screen 31: Multi-tenant Platform Home (`/admin`)
* **Route:** `/admin` | **Component:** `PlatformHome.jsx`
* **Before (5/10):** Empty placeholder or raw stats.
* **After (10/10):** Master super-admin cockpit: Total Active Tenants card, Total Licensed Users card, Platform API health status, and quick tenant search bar.

#### Screen 32: Tenant Registry & Detail (`/admin/tenants`, `/new`, `/:id`)
* **Route:** `/admin/tenants`, `/admin/tenants/new`, `/admin/tenants/:id` | **Component:** `TenantList.jsx`, `TenantCreate.jsx`, `TenantDetail.jsx`
* **Before (5/10):** Raw database table view.
* **After (10/10):** SaaS tenant card cards with active subscription tier badge, domain URL, employee headcount, module switches (Core, HRMS, Payroll toggles), and support impersonation launcher.

---

### 3.8 Employee Self-Service Core Screens & Public

#### Screen 33: Public Accept Invitation (`/invitations/accept`)
* **Route:** `/invitations/accept` | **Component:** `AcceptInvitation.jsx`
* **Before (5/10):** Blank page with default login inputs.
* **After (10/10):** Premium branded welcome portal: Company logo, friendly welcome headline, password strength meter, and royal blue "Activate Account" button.

#### Screen 34: Self-Service: My Profile (`/me/profile` or `/me`)
* **Route:** `/me` (Profile Panel) | **Component:** `MyProfile.jsx`
* **Before (5/10):** Read-only flat text.
* **After (10/10):** Personal digital ID card with clean personal information tabs, emergency contact cards, and self-service address update request button.

#### Screen 35: Self-Service: My Leave (`/me` - Leave Panel)
* **Route:** `/me` (Leave Panel) | **Component:** `MyLeave.jsx`
* **Before (5/10):** Basic numbers in plain boxes.
* **After (10/10):** 3 visual balance widgets with circular progress meters (e.g. `12 / 18 Days Available`), leave expiry warning banner, and past request history table.

#### Screen 36: Self-Service: Apply Leave (`/me/leave/apply`)
* **Route:** `/me/leave/apply` | **Component:** `ApplyLeave.jsx`
* **Before (5/10):** Standard form inputs.
* **After (10/10):** Interactive application drawer: Leave type selector pills, interactive calendar range picker, half-day toggles, dynamic balance impact calculator ("This will deduct 2 days, leaving 10 available"), and file upload dropzone.

#### Screen 37: Self-Service: My WFH (`/me` - WFH Panel) & Apply (`/me/wfh/apply`)
* **Route:** `/me` (WFH Panel), `/me/wfh/apply` | **Component:** `MyWfh.jsx`, `ApplyWfh.jsx`
* **Before (5/10):** Flat text list.
* **After (10/10):** Monthly remote work balance card with remaining days counter and streamlined single-click date picker application modal.

---

## 4. Verification & UI QA Acceptance Criteria

1. **Brand Identity:** Sky blue (`#E0F2FE`), vibrant blue (`#2563EB`), and pure white surfaces present across all 37 Core screens.
2. **Zero Functional Drift:** All Redux actions, API endpoints (`/api/v1/*`), and permissions checks remain 100% intact.
3. **Typography:** Standardized 14px body text with 600-weight headings in modern slate (`#0F172A`).
4. **Responsive Integrity:** Tables support horizontal scroll without page layout breaks on smaller screens.

