# Feature: W-81 HRMS Module Screens Professional UI Redesign Specification (10/10 UX)

> Part 2 of 3: HRMS Module Screens (Attendance, Timesheets, Projects, Overtime & Dashboard)
> Theme Palette: Sky Blue (`#E0F2FE` / `#BAE6FD`), Royal Blue (`#2563EB` / `#1D4ED8`), Crisp White (`#FFFFFF`), and Soft Slate (`#F8FAFC`).
> Reference Assets: `d:\HCM Project\ui_preview\1_dashboard.jpg` and `2_timesheet.jpg`.
> Non-Breaking: Retains 100% of existing functional endpoints, week grid calculation engines, Redux state, and routes.

| Field | Value |
|---|---|
| **Feature ID** | W-81 |
| **Owner** | Frontend Architecture & Product Design |
| **Apps touched** | `infinevo-platform/code/frontend` (`@hrms`, `@shell`, `@shared`) |
| **Status** | Approved Specification |
| **Visual Target** | 10/10 Enterprise SaaS (Linear / Toggl / Harvest / Rippling caliber) |
| **Color Scheme** | Sky Blue + Vibrant Blue + Crisp White with Slate neutral hierarchy |

---

## 1. Executive Summary & Design Analysis

### Current State (Before: 5/10)
* **Weekly Timesheet (`2_timesheet.jpg`):**
  * Weekend days (Saturday & Sunday) are displayed with aggressive, solid emergency red headers (`#EF4444`), making standard non-working days look like critical system failures.
  * Inputs are nested inside bulky double-bordered card boxes with individual description textareas per day, forcing awkward scrolling and creating visual clutter.
  * Bottom totals lack clear visual grounding and separation.
* **HRMS Dashboard (`1_dashboard.jpg`):**
  * Visual layout is promising, but exhibits prototype artifacts: typo in calendar tab ("Calnmary"), duplicate subtitle text ("Active Employees"), and thick baby-blue borders.
  * Quick action buttons lack consistent icon grounding and modern hover depth.
* **Attendance & Regularization Screens:**
  * Raw clock punch lists lack daily progress indicators, time-tracking visual bars, or status color-coding.

### Target State (After: 10/10 Professional Grade)
* **Timesheet Transformation:** A sleek, compact Monday-Sunday grid. Weekend days use refined muted slate headers (`#F1F5F9`) with subtle dashed borders. Inputs are clean, single-click active cells with popover or inline drawer for descriptions.
* **Dashboard Elevation:** Direct match to `1_dashboard.jpg` elevated to 10/10 standards: polished metric cards with SVG sparklines, interactive attendance calendar with leave indicator dots, and quick action cards.
* **Attendance Clocking:** Premium ClockCard with dynamic live elapsed timers, status rings (Clocked In: Emerald, On Break: Amber, Clocked Out: Neutral), and geolocation/IP verification badges.

---

## 2. Universal Design System Tokens (HRMS Module)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        COLOR HIERARCHY SYSTEM                         │
├───────────────────┬───────────────────┬────────────────────────────────┤
│ Role              │ Hex Code          │ Application                    │
├───────────────────┼───────────────────┼────────────────────────────────┤
│ Primary Blue      │ #2563EB           │ Primary actions, clock-in CTA  │
│ Primary Dark      │ #1D4ED8           │ Active state, button hover     │
│ Sky Blue Accent   │ #38BDF8           │ Chart highlights, radial rings │
│ Sky Blue Surface  │ #F0F9FF           │ Weekday column headers         │
│ Sky Blue Border   │ #BAE6FD           │ Active grid cell focus border  │
│ Weekend Neutral   │ #F1F5F9           │ Weekend headers (Sat & Sun)    │
│ Weekend Text      │ #64748B           │ Muted weekend text label       │
│ Pure White        │ #FFFFFF           │ Main card and table background │
│ Background Grey   │ #F8FAFC           │ Page canvas background         │
│ Hairline Border   │ #E2E8F0           │ Card borders, grid dividers    │
│ Dark Slate Text   │ #0F172A           │ Headings, total hour numbers   │
│ Success Emerald   │ #059669           │ Clocked in, approved hours     │
│ Warning Amber     │ #D97706           │ Missing punch, regularizations │
└───────────────────┴───────────────────┴────────────────────────────────┘
```

---

## 3. Screen-by-Screen Before vs. After Specification

Every screen in the HRMS module is catalogued below with zero omissions:

### 3.1 HRMS Executive & Operational Dashboard

#### Screen 1: HRMS Dashboard (`/hrms/dashboard`)
* **Route:** `/hrms/dashboard` | **Component:** `HrmsDashboardPage.jsx`
* **Reference Mockup:** `d:\HCM Project\ui_preview\1_dashboard.jpg`
* **Before (5/10):** Functional prototype with thick blue borders, label redundancies ("Active Employees" appearing twice), and minor prototype text defects.
* **After (10/10):**
  * **Top Metrics Row (4 Cards):**
    * *Total Payroll Disbursed Card:* Prominent headline figure (`₹40,80,440`, bold 24px) paired with an embedded sky-blue SVG mini-bar chart (`#38BDF8`).
    * *Active Employees Card:* Count `53` with upward trend badge pill (`+4% this month`) and soft blue circular icon badge.
    * *Attendance Today Card:* `96.8%` with high-resolution circular progress gauge (Emerald `#10B981` ring with sky-blue fill).
    * *Pending Approvals Card:* `6 Action Items` with soft coral icon badge and urgent status pill.
  * **Attendance & Leave Overview Card (Middle Left):**
    * *Interactive Monthly Calendar:* Sleek day matrix with clean typography. Dates with employee leaves display subtle indicator dots underneath (Blue for Planned Leave, Purple for Holiday, Amber for Pending).
    * *Header Controls:* Month dropdown, Year selector, and primary royal blue "New Request" button.
    * *Upcoming Holidays & Requests Summary:* Clean vertical breakdown with colored bullet indicators and "Apply Now" button at the bottom.
  * **Recent Activity Feed (Middle Right):**
    * Real-time employee activity cards with clean circular avatars, employee name, event description, and soft rounded status pills (`Clocked In` in green, `Leave Approved` in emerald, `Profile Updated` in blue).
    * "View All" link in the top right.
  * **Quick Actions Panel (Bottom Right):**
    * 3 clean square action cards with sky-blue rounded icon containers:
      1. *Apply Leave* (Calendar icon).
      2. *Clock In/Out* (Clock icon).
      3. *Log Timesheet* (Timesheet grid icon).

---

### 3.2 Time & Attendance

#### Screen 2: My Attendance / Web Clock-in (`/hrms/attendance`)
* **Route:** `/hrms/attendance` | **Component:** `MyAttendance.jsx`, `ClockCard.jsx`
* **Before (5/10):** Simple clock button and unstyled punch table.
* **After (10/10):**
  * **Hero ClockCard:** Centered card with large digital time display (Live ticking seconds, e.g. `09:42:15 AM`), current date banner, and dynamic work timer (`04h 32m Elapsed Today`).
  * **Clocking Actions:** Large primary button with state transitions:
    * *Not Started:* Royal Blue "Clock In" button (`#2563EB`).
    * *Active:* Soft red "Clock Out" with secondary "Take a Break" amber button.
  * **Daily Punch Timeline:** Graphical timeline showing First In, Break In, Break Out, and Last Out with exact timestamps and IP address badges.

#### Screen 3: Company Attendance Log (`/hrms/attendance-log`)
* **Route:** `/hrms/attendance-log` | **Component:** `AttendanceLog.jsx`
* **Before (5/10):** Plain data table with raw database timestamps.
* **After (10/10):**
  * **Filter Bar:** Date picker (Single day / range), Department selector, and status toggle pills (`All`, `Present`, `Late`, `Absent`, `On Leave`).
  * **Log Table:** Employee avatar, ID, First In timestamp, Last Out timestamp, Total Productive Hours, and Late In pill tag (Amber `#FFFBEB` bg with `#B45309` text). Quick "Regularize" action for managers.

#### Screen 4: Attendance Settings (`/hrms/attendance-settings`)
* **Route:** `/hrms/attendance-settings` | **Component:** `AttendanceSettings.jsx`
* **Before (5/10):** Cluttered settings form.
* **After (10/10):** Grouped card sections:
  * *Section 1: General Shifts & Working Hours* (Standard shift start/end, minimum hours for full day and half day).
  * *Section 2: Grace Period & Penalties* (Allowable late minutes, max late marks per month before LOP cut).
  * *Section 3: Geofencing & IP Restrictions* (Toggle office WiFi/IP restriction with whitelist tags).

---

### 3.3 Timesheets

#### Screen 5: My Timesheets List (`/hrms/timesheets`)
* **Route:** `/hrms/timesheets` | **Component:** `TimesheetList.jsx`
* **Before (5/10):** Basic list table showing week dates and generic status.
* **After (10/10):**
  * **Header:** Summary stats showing Total Hours Logged this month, Billable Ratio (`88% Billable`), and "+ Log This Week" CTA button.
  * **Timesheet Archive Cards:** Week range header (e.g. `Oct 26 - Nov 01, 2026`), total hours logged badge (`40.0 hrs`), billable hours breakdown bar, and status pills: `Draft` (Slate), `Submitted` (Sky Blue), `Approved` (Emerald), `Rejected` (Rose).

#### Screen 6: Weekly Timesheet Entry Grid (`/hrms/timesheets/week/:weekStart`)
* **Route:** `/hrms/timesheets/week/:weekStart` | **Component:** `TimesheetWeek.jsx`
* **Reference Mockup:** `d:\HCM Project\ui_preview\2_timesheet.jpg`
* **Before (5/10):** Aggressive solid red weekend pill headers (`Sat`, `Sun`), clunky double-nested inputs taking huge vertical space, plain bottom summary bar.
* **After (10/10):**
  * **Header Controls:** Week navigation stepper (`< Oct 26 - Nov 01, 2026 >`), Employee ID badge pill (`[Employee ID: P074]`), and quick "Copy Previous Week" action.
  * **Project & Task Selection Bar:** Clean dropdown selectors for Project Name and Assigned Task with billable badge indicator.
  * **7-Day Grid Headers:**
    * *Weekdays (Mon - Fri):* Soft sky-blue pill containers (`background: #F0F9FF`, `border: 1px solid #BAE6FD`, text: `#0369A1` bold).
    * *Weekends (Sat & Sun):* Soft muted slate pill containers (`background: #F1F5F9`, `border: 1px solid #E2E8F0`, text: `#64748B` bold) — **eliminates the jarring emergency red**.
  * **Grid Input Cells:**
    * Compact numeric input for hours with clean `0.0` stepper.
    * Inline task description field with subtle placeholder that expands on focus without pushing adjacent columns out of alignment.
  * **Bottom Summary Bar:**
    * Daily column sum badges with visual indicators (e.g. `8.0 hrs` in green if target met, amber if under 8h).
    * Total Weekly Hours Card: Prominent badge (`Total: 40.0 hrs`) in royal blue highlight.
  * **Footer Actions:** Secondary white "Cancel" button + primary royal blue "Submit Timesheet" button.

#### Screen 7: Self-Service Portal Timesheet Widget (`/me` - Timesheet Panel)
* **Route:** `/me` (Timesheet Panel) | **Component:** `MyTimesheetPanel.jsx`
* **Before (5/10):** Minimalistic text box.
* **After (10/10):** Quick daily logging card inside user portal showing today's assigned tasks, hours stepper, and one-click "Save Day" button.

---

### 3.4 Timesheet Review & Approvals

#### Screen 8: Timesheet Review Dashboard (`/hrms/timesheet-review`)
* **Route:** `/hrms/timesheet-review` | **Component:** `ReviewPage.jsx`
* **Before (5/10):** Generic table listing submissions.
* **After (10/10):**
  * **Overview KPI Cards:** `Pending Submissions` (Count), `Total Hours to Approve`, `Rejected Weeks`.
  * **Team Table:** Employee name with avatar, project names, week range, total hours (billable vs non-billable bar), and dual actions: inline "Approve" (green) and "Inspect" (blue).

#### Screen 9: Timesheet Review Week View (`/hrms/timesheet-review/:id`)
* **Route:** `/hrms/timesheet-review/:id` | **Component:** `WeekView.jsx`
* **Before (5/10):** Read-only version of the old timesheet.
* **After (10/10):** Manager review cockpit: Project-by-project breakdown of employee hours, daily total bar chart, manager remark note area, and prominent "Approve Week" / "Request Changes" actions.

#### Screen 10: Timesheet Entry View (`/hrms/timesheet-review/entries/:entryId`)
* **Route:** `/hrms/timesheet-review/entries/:entryId` | **Component:** `EntryView.jsx`
* **Before (5/10):** Raw JSON or basic table row.
* **After (10/10):** Detailed task time inspection card showing exact clock times, task deliverables, and change history audit.

---

### 3.5 Projects & Work Management

#### Screen 11: Projects Directory (`/hrms/projects`)
* **Route:** `/hrms/projects` | **Component:** `ProjectList.jsx`
* **Before (5/10):** Basic table with project code and name.
* **After (10/10):**
  * **Filter Header:** Search project, Client filter, Status filter (`Active`, `On Hold`, `Completed`), and "+ Create Project" royal blue button.
  * **Project Card Grid:** Project Name, Client Badge, Project Manager Avatar, Team Count, Total Logged Hours vs Budget Gauge bar (`320h / 500h`), and health indicator.

#### Screen 12: Project Workspace (`/hrms/projects/:id`)
* **Route:** `/hrms/projects/:id` | **Component:** `ProjectPage.jsx`
* **Before (5/10):** Plain tabs with unstyled content.
* **After (10/10):**
  * **Project Hero:** Title, Client Name, Date range, Budget Burn gauge, and status pill.
  * **Tab 1: Tasks (`TasksTab.jsx`):** List of project deliverables with assigned employee avatars, estimated hours, logged hours, and task status pills (`To Do`, `In Progress`, `Done`).
  * **Tab 2: Team Allocation (`TeamTab.jsx`):** Team members list with hourly billing rates, allocation percentage (e.g. `100% Allocation`), and remove/edit actions.
  * **Project Form Modal (`ProjectForm.jsx`):** Clean 2-column modal to create/edit projects with client picker and budget inputs.

#### Screen 13: My Work / Assigned Tasks (`/hrms/my-work`)
* **Route:** `/hrms/my-work` | **Component:** `MyWork.jsx`
* **Before (5/10):** Flat task list.
* **After (10/10):** Kanban or Card-List view of personal tasks across all assigned projects with direct "Log Time" shortcut buttons.

---

### 3.6 Attendance Regularization & Overtime Requests

#### Screen 14: My Regularizations (`/hrms/regularizations`)
* **Route:** `/hrms/regularizations` | **Component:** `MyRegularizations.jsx`
* **Before (5/10):** Basic list table.
* **After (10/10):** Status-segmented list (`Pending`, `Approved`, `Rejected`), card layout highlighting date, original punch vs requested punch, reason badge, and "+ New Regularization" drawer button.

#### Screen 15: Attendance Regularization Log (All / HR View) (`/hrms/regularizations/all`)
* **Route:** `/hrms/regularizations/all` | **Component:** `RegularizationLog.jsx`
* **Before (5/10):** Generic admin table.
* **After (10/10):** Company-wide regularization audit table with multi-select batch approval capabilities and filter by department.

#### Screen 16: Regularization Detail (`/hrms/regularizations/:id`)
* **Route:** `/hrms/regularizations/:id` | **Component:** `RegularizationDetail.jsx`
* **Before (5/10):** Plain form.
* **After (10/10):** Comparative audit card: Left column shows original biometric/clock punch; Right column shows employee requested punch with reason. Approval decision buttons with remarks field.

#### Screen 17: My Overtime Requests (`/hrms/overtime-requests`)
* **Route:** `/hrms/overtime-requests` | **Component:** `MyOvertime.jsx`
* **Before (5/10):** Flat list.
* **After (10/10):** Monthly overtime hours counter widget (`14.5 Overtime Hours Logged`) with detailed request status history.

#### Screen 18: Overtime Requests Log (All / HR View) (`/hrms/overtime-requests/all`)
* **Route:** `/hrms/overtime-requests/all` | **Component:** `OvertimeLog.jsx`
* **Before (5/10):** Unstyled list.
* **After (10/10):** Payroll-ready overtime hours ledger with total hours summation bar, overtime pay rate multiplier tag (`1.5x / 2.0x`), and approve for payroll button.

#### Screen 19: Overtime Detail (`/hrms/overtime-requests/:id`)
* **Route:** `/hrms/overtime-requests/:id` | **Component:** `OvertimeDetail.jsx`
* **Before (5/10):** Basic text box.
* **After (10/10):** Detailed overtime inspector: Project justification, manager endorsement note, exact start/end hours, and payout eligibility confirmation.

---

## 4. Verification & Visual QA Acceptance Criteria

1. **Weekend Styling Fix:** No solid red headers on weekends in Timesheets; must use refined neutral slate (`#F1F5F9`).
2. **Dashboard Visual Match:** Must match `1_dashboard.jpg` layout with zero typos, clean card borders, and responsive grid.
3. **Timesheet Functional Integrity:** Week navigation, day calculation totals, and submission endpoints must remain 100% intact.
4. **Interactive States:** All cards, table rows, and action buttons must include subtle hover transitions (`all 0.2s ease-in-out`).

