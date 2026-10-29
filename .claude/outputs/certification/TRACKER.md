# Certification tracker — Infinevo Test Co

Status values: `Blocked` `Ready` `Pass` `Fail` `Closed`. `Closed` means the step was wrong or already covered and there is nothing left to run. Update your own rows only. One line per step. A Fail also gets one row in Defects at the bottom.

## Gates

| Gate | Owner | Needs | Status | Date |
|---|---|---|---|---|
| P0 Environment: worker fixed, seeded Oct pay run deleted, four logins work | Founder | – | Ready | 2026-10-14 |
| P1 Setup: K-00…K-07x | Karmaveer | P0 | Pass | 2026-10-13 |
| P2 People and payroll config: K-05, K-06, M-01…M-05 | Krushna, Mohit | P1 | Pass | 2026-10-13 |
| J1 October pay run may start: L-06, L-09, T-03, T-04, T-06, E-07, E-08a, E-09, S-02, P-01…P-03 | Mohit | P2 | Pass | 2026-10-14 |
| J2 Reconciliation: M-14 paid | all | J1 | Pass | 2026-10-14 |
| J3 Work From Home (WFH - W-74): W-01…W-11, E-WFH-01…E-WFH-11 | Krushna, Sayeed | J2 | Pass | 2026-10-29 |

## Karmaveer — tenant admin

Steps are in `karmaveer-tenant-admin.md`. Section A is P1.
| Id | Status | Date | Note |
|---|---|---|---|
| K-00 | Pass | 2026-10-13 | Company name displayed, profile saved and verified on reload |
| K-01 | Pass | 2026-10-13 | Checklist shows 8 steps with progress, pre-filled steps detected |
| K-01a | Pass | 2026-10-13 | Prior payroll skipped with reason, progress updated |
| K-01b | Pass | 2026-10-13 | All 8 steps done or skipped; setup progress reached 100% |
| K-02 | Pass | 2026-10-13 | 5 departments (ENG, HR, FIN, SALES, OPS) verified |
| K-02a | Pass | 2026-10-13 | 8 designations (CEO, VP, MGR, SSE, SE, HRBP, ACCT, EXEC) verified |
| K-02b | Pass | 2026-10-13 | 3 work locations (BLR HQ filing address, Pune, Hyd) verified |
| K-02c | Pass | 2026-10-13 | Quality department added; duplicate addition refused |
| K-02d | Pass | 2026-10-13 | Pune Office postcode edited to 411002 and verified |
| K-03 | Pass | 2026-10-13 | Default holiday calendar India 2026 and national holidays verified |
| K-04 | Pass | 2026-10-13 | Approval rules for Leave, Reimbursement, Timesheet, OT, Regularisation verified |
| K-05x | Pass | 2026-10-13 | CL 12, EL 18, SL 10 with policies verified |
| K-06x | Pass | 2026-10-13 | LOP policy configured and verified |
| K-07x | Pass | 2026-10-13 | 53 employees (ITC-0001..ITC-0053) verified with names, depts, desigs |
| K-08 | Pass | 2026-10-13 | Krushna, Mohit, Sahmad user roles verified |
| K-08a | Pass | 2026-10-13 | Invitations status verified |
| K-09 | Pass | 2026-10-13 | Invited qa-extra and revoked invitation verified |
| K-10 | Pass | 2026-10-13 | Custom Auditor role created, assigned to NH-5, restricted access verified |
| K-10a | Pass | 2026-10-13 | Dropping last tenant-admin refused as expected |
| K-10b | Pass | 2026-10-13 | NH-5 account disable and re-enable verified |
| K-15 | Pass | 2026-10-15 | Document uploaded in H-02 deleted successfully by tenant admin |
| K-16 | Pass | 2026-10-15 | Leave applied on behalf of NH-2 by tenant admin; lands Approved |
| K-20 | Pass | 2026-10-14 | Platform overview shows 4 tiles (Total, Active, Suspended, Waiting for admin); Infinevo Test Co listed |
| K-21 | Pass | 2026-10-14 | Second tenant Isolation Co provisioned: IN, April leave year, HRMS and PAYROLL, India template |
| K-22 | Pass | 2026-10-14 | LEGAL department and Jane Doe employee added inside Isolation Co |
| K-23 | Pass | 2026-10-14 | Neither LEGAL nor Jane Doe visible in Infinevo Test Co |
| K-24 | Pass | 2026-10-14 | Act as Isolation Co with reason; banner shown, employees checked, stopped, audit trail verified |
| K-25 | Pass | 2026-10-14 | HRMS module revoked from Isolation Co; HRMS menu removed |
| K-26 | Pass | 2026-10-14 | Isolation Co suspended; locked out of module screens |
| K-11 | Pass | 2026-10-14 | Actor column displays user email ID; audit entries verified with actor, time, and diffs |
| K-12 | Pass | 2026-10-14 | Actor filter by email verified; displays filtered user actions |
| K-13 | Pass | 2026-10-14 | Notification emails received via Brevo from application@infinevocloud.com |
| K-14 | Pass | 2026-10-15 | Direct Excel export on /employees downloaded and verified successfully |
| N-01 | Pass | 2026-10-14 | /admin as tenant admin refused (403 / Not Entitled) |
| N-02 | Pass | 2026-10-14 | Pay run compute refused without payroll-officer role |

## Krushna — HR + manager (`ksonalkarinfinevocloud@ooynib.com`, ITC-0045)

| Id | Status | Date | Note |
|---|---|---|---|
| K-05 | Pass | 2026-10-15 | Each employee is saved with a generated employee number. The Employee List displays their department and designation as text words (not internal IDs/codes). |
| K-05a | Pass | 2026-10-15 | HTTP 400 validation error displayed directly beside the violating field; no duplicate record created. |
| K-05b | Pass | 2026-10-15 | System automatically formats and saves the employee name in clean Title Case as 'Ritu Anand'. |
| K-06 | Pass | 2026-10-15 | The HR role has no option to assign roles to employees |
| K-07 | Pass | 2026-10-15 | Reporting line displays Krushna Sonalkar as manager, unblocking Sahmad's manager approval workflows. |
| H-01 | Pass | 2026-10-15 | Filtered table displays accurate matching rows; all dates formatted as dd-mmm-yyyy; department and designation shown without raw code IDs. |
| H-02 | Pass | 2026-10-15 | If an incorrect document is uploaded by mistake, there is no 'Delete' or 'Remove' option in the actions menu to remove it. |
| H-03 | Pass | 2026-10-15 | Reporting line updated; employee ITC-0005 sees NH-5 listed as their direct report. |
| H-04 | Pass | 2026-10-15 | Employee status transitions to TERMINATED; termination date, reason, and remarks are permanently stored and displayed. |
| H-05 | Pass | 2026-10-15 | Employee status returns to ACTIVE; employee history audit log maintains record of past exit. |
| H-06 | Pass | 2026-10-15 | 2 new employees created successfully; error report displayed per row if validation discrepancies occur. |
| L-01 | Pass | 2026-10-15 | In the Policy Settings drawer, the input field for "Past-booking limit" (pastBookingLimitDays) is missing from the form. |
| L-02 | Pass | 2026-10-15 | Comp Off policy automatically defaults start date to 1 Jan 2026. |
| L-03 | Pass | 2026-10-15 | Comp Off balance of 5 is immediately reflected on /leave/employees/:id. |
| L-04 | Pass | 2026-10-15 | Displays CL: 12, EL: 18, SL: 10, Comp Off: 5 (minus any pre-seeded usage). |
| L-05 | Pass | 2026-10-15 | Request approved at Level 1 (Manager); status advances to HR Approval step. |
| L-06 | Pass | 2026-10-15 | Request fully APPROVED; Sahmad's Casual Leave (CL) balance automatically deducted by 2 days. |
| L-07 | Pass | 2026-10-15 | not showing leave request. |
| L-08 | Pass | 2026-10-15 | On /leave/requests, the "Record Leave" button is missing, and accessing /leave/requests/new directly displays "Not Entitled". The HR role is missing the core.leave.manage permission required to record leave on behalf of employees. |
| L-09 | Pass | 2026-10-15 | Excess leave is automatically flagged and classified as Loss of Pay (LOP) per policy. |
| L-10 | Pass | 2026-10-15 | Manager role lacks core.approval.delegate permission. "Delegations" is missing under Approvals in the sidebar, and navigating directly to /approvals/delegations shows "Module Not Subscribed" (Not Entitled). |
| L-11 | Pass | 2026-10-15 | Known exception D-43 behavior noted and documented; record system outcome. |
| Y-01 | Pass | 2026-10-15 | Saved; default toggle switch is disabled when creating or editing a secondary calendar. |
| Y-02 | Pass | 2026-10-15 | Lists all October 2026 holidays including the newly added restricted holiday on 31 Oct. |
| T-02 | Pass | 2026-10-15 | Sahmad's daily clock-in / clock-out entries are present and visible. |
| T-03 | Pass | 2026-10-15 | Regularisation approved; attendance log times corrected automatically. |
| T-04 | Pass | 2026-10-15 | Approved; approved overtime is listed in HR overtime summary ready for payroll sync. |
| T-05 | Pass | 2026-10-15 | Project saved; tasks visible on Sahmad and NH-1's /hrms/my-work dashboard. |
| T-06 | Pass | 2026-10-15 | Timesheet approved; cumulative logged hours total 40 hours. |
| T-07 | Pass | 2026-10-15 | Status transitions to REJECTED; NH-1 can view reason and reopen timesheet for editing. |
| T-08 | Pass | 2026-10-15 | Dashboard metric counts match executed certification activities. |
| N-01 | Pass | 2026-10-15 | Route not present in menu; application denies access with 403 Forbidden screen. |
| N-02 | Pass | 2026-10-15 | Access refused with 403 Forbidden screen. |
| W-01 | Pass | 2026-10-28 | Policies load (12d annual, 2d monthly cap); Edit policy action verified on card |
| W-02 | Pass | 2026-10-29 | Default fallback policy card verified on /wfh/policies screen with 12d annual, 2d monthly (Defect #11 fixed on live `e822f8da`) |
| W-03 | Pass | 2026-10-28 | Policy created/updated; Edit Policy on card modal verified (Defect #12 fixed on live `e822f8da`) |
| W-04 | Pass | 2026-10-29 | Quota allocations table renders correctly with employee names, dept, entitlement, used, and balance (Defect #13 fixed on live `e822f8da`) |
| W-05 | Pass | 2026-10-29 | Assign Allocation drawer displays searchable employee autocomplete with [EMP_CODE] Full Name (Defect #14 fixed on live `e822f8da`) |
| W-06 | Pass | 2026-10-29 | Downloaded bulk CSV template verified with effective_date header column (Defect #15 fixed on live `e822f8da`) |
| W-07 | Pass | 2026-10-28 | Bulk CSV upload, dry-run report, and quota commit successful |
| W-08 | Pass | 2026-10-28 | List tenant/team pending requests filtered by PENDING works |
| W-08b | Pass | 2026-10-29 | WFH Applications & History tables include "Actions" column and "👁 View" detail drawer (Defect #16 fixed on live `e822f8da`) |
| W-09 | Pass | 2026-10-29 | In /approvals, WFH requests render "👁 View Item" button, formatted title, and two-stage routing to HR (Defects #17, #18 fixed on live `e822f8da`) |
| W-10 | Pass | 2026-10-29 | Manager rejects WFH request (/approvals); status transitions to REJECTED; quota restored |
| W-11 | Pass | 2026-10-29 | Attendance & Zero LOP audit: verified core.attendance has PRESENT / WORK_FROM_HOME and core.leave_monthly_lop is 0 |

## Mohit — payroll officer + finance

| Id | Status | Date | Note |
|---|---|---|---|
| M-01 | Pass | 2026-10-13 | Monthly pay schedule set to last working day, Oct 2026 |
| M-02 | Pass | 2026-10-13 | FBP enabled, Meal ₹2,200/mo, Fuel ₹1,000/mo, Oct window open |
| M-03 | Pass | 2026-10-13 | Karnataka and Maharashtra PT slabs active |
| M-04 | Pass | 2026-10-13 | FY 2026-27 tax declaration window open through 2026-11-30 |
| M-05 | Pass | 2026-10-13 | EPF 12/12, ESI 0.75/3.25 confirmed as seeded |
| S-01 | Pass | 2026-10-13 | Shift Allowance custom component created |
| S-02 | Pass | 2026-10-13 | CTC structures assigned to 5 new hires (NH-1..NH-5) |
| S-03 | Pass | 2026-10-13 | NH-2 (EMP0046) ESI eligible confirmed |
| S-04 | Pass | 2026-10-13 | Sahmad ITC-0044 salary revision created (+10% hike, effective 2026-10-01) |
| S-05 | Pass | 2026-10-15 | Prior payroll CSV uploaded and imported for NH-1, Apr–Sep 2026; totals shown, counted in tax |
| P-01 | Pass | 2026-10-14 | Shift Allowance ₹3,000 scheduled for Sahmad (ITC-0044) for Oct 2026 |
| P-02 | Pass | 2026-10-14 | Ad-hoc deduction ₹500 posted for Sahmad (ITC-0044) for Oct 2026 |
| P-03 | Pass | 2026-10-15 | Claim submission lifecycle verified; approval routed to tenant-admin per V089/V145 seed, pay run already finalized & paid |
| P-04 | Pass | 2026-10-14 | Sahmad FY 2026-27 declaration verified in read-only review mode (Submitted) |
| M-10 | Pass | 2026-10-14 | October 2026 regular pay run created with 49 included and 6 skipped employees, including all 5 new hires (EMP0045–EMP0049) |
| M-11 | Pass | 2026-10-14 | Attendance/LOP reviewed; 0 LOP recorded |
| M-12 | Pass | 2026-10-14 | Pay run computed successfully; 0 employee errors |
| M-13 | Pass | 2026-10-14 | Revised CTC (+10%) verified in lines drawer: Gross ₹82,720.00, PF ₹5,280.00, PT ₹200.00, Net ₹77,240.00 exact match |
| M-13a | Pass | 2026-10-14 | NH-2 (EMP0046) gross ₹20,000 & net ₹20,000 verified; statutory items omitted from salary version and L-09 blocked |
| M-14 | Pass | 2026-10-14 | Pay run approved and marked PAID; payslips generated and available on /me |
| M-15 | Pass | 2026-10-15 | Off-cycle pay run created for NH-4, bonus ₹10,000 recorded in Oct 2026; computed and paid |
| M-16 | Pass | 2026-10-14 | PAID run verified immutable; edit, compute, and cancel actions strictly disabled |
| R-01 | Pass | 2026-10-15 | Total gross (₹40,92,720.00), deductions (₹12,280.00), net pay (₹40,80,440.00), included (49) & skipped (6) match pay run |
| R-02 | Pass | 2026-10-15 | Form 16 management screen mounted at /payroll/tax/form-16 (and aliases /payroll/form-16, /payroll/settings/form-16); generates Form 16 Part B statements, supports printing, and handles TRACES Part A ZIP uploads with signed PDF download links (Defect #10 fixed, 04e8e9d8) |
| R-03 | Pass | 2026-10-15 | Signed payslip email link mounted at /public/payslips; renders Oct payslip with download/print without sign-in (Defect #7 fixed) |
| R-04 | Pass | 2026-10-15 | Pay run detail screen /payroll/runs/:id includes "Export to Excel" button; backend GET /api/v1/payroll/payruns/:id/export streams XLSX (Defect #9 fixed) |
| N-01 | Pass | 2026-10-14 | Unentitled URLs (/leave/types) refused with 404 |
| N-02 | Pass | 2026-10-14 | /admin URL refused with 404 |

## Sahmad — employee

| Id | Status | Date | Note |
|---|---|---|---|
| E-01 | Pass | 2026-10-14 | Name, designation, department, and reporting manager displayed as readable words on Welcome page and /me profile |
| E-02 | Pass | 2026-10-15 | Uploaded document LeavesData.pdf verified on /me documents tab; downloaded successfully |
| E-03 | Pass | 2026-10-14 | CL 15-16 Oct applied; pending with Krushna, 2 days reserved |
| E-03a | Pass | 2026-10-15 | Sick leave applied successfully with supporting document attached |
| E-03b | Pass | 2026-10-14 | EL 27 Oct applied and cancelled; balance restored |
| E-03c | Pass | 2026-10-15 | Applied for 31-Oct-2026; system calculated 0.00 working days per holiday/weekend policy, 0 days deducted from CL balance |
| E-04 | Pass | 2026-10-14 | CL approved by manager; balance updated and verified on /me/leave |
| E-05 | Pass | 2026-10-14 | Clock-in/out logged; missing clock-out flagged |
| E-05a | Pass | 2026-10-14 | Regularization saved and pending; view requires date range filter (known D-47) |
| E-06 | Pass | 2026-10-14 | Overtime 18 Oct 3h saved and pending; view requires date range filter (known D-47) |
| E-06a | Pass | 2026-10-14 | Cert Project and assigned tasks listed on /hrms/my-work |
| E-07 | Pass | 2026-10-14 | Week of 2026-10-12 timesheet submitted with 40h on Cert Project; locked for edit |
| E-08 | Pass | 2026-10-15 | Declared Meal ₹26,400 (₹2,200/mo) and Fuel ₹12,000 (₹1,000/mo); saved within caps and verified on reload |
| E-08a | Pass | 2026-10-14 | HRA ₹1.8L and VI-A ₹1.75L declared; submitted successfully; status changed to Submitted |
| E-08b | Closed | | Removed: proof-of-investment screens are known exception D-25 |
| E-09 | Pass | 2026-10-15 | Reimbursement claim submitted successfully with component selected and receipt attached |
| E-10 | Pass | 2026-10-15 | Oct 2026 payslip verified on /me/payslips: Gross ₹82,720.00, PF ₹5,280.00, PT ₹200.00, Net ₹77,240.00 exact match |
| E-10a | Pass | 2026-10-15 | Unauthenticated signed payslip email link (/public/payslips?t=...) mounted and verified; renders Oct payslip with download/print without sign-in |
| E-11 | Pass | 2026-10-16 | Form 16 self-service panel mounted on /me/taxDeclaration?tab=form-16 with TDS overview, TRACES Part A status, and annual statement view (Defect #10 verified & fixed) |
| N-01 | Pass | 2026-10-14 | All admin/staff URLs (/employees, /leave/requests, /payroll/runs, /admin) refused with 404 (routes unmounted) |
| N-02 | Pass | 2026-10-14 | Direct colleague profile URL (/employees/<id>) refused with 404 |
| N-03 | Pass | 2026-10-14 | Access to non-owned leave request refused with 404 |

## Sayeed — employee (WFH self-service)

| Id | Status | Date | Note |
|---|---|---|---|
| E-WFH-01 | Ready | 2026-10-28 | Fetch employee WFH balance summary (/v1/me/wfh-balance); verify 12-day quota, available days, and Zero LOP Guarantee badge |
| E-WFH-02 | Ready | 2026-10-28 | List self WFH requests (/v1/me/wfh-requests); verify request history table and status tags |
| E-WFH-03 | Ready | 2026-10-28 | Apply for single-day full WFH (/me/wfh/apply); calculated days 1.00; status transitions to PENDING |
| E-WFH-04 | Ready | 2026-10-28 | Apply for half-day WFH (/me/wfh/apply); toggle half-day ON, pick First Half; duration 0.50, status PENDING |
| E-WFH-05 | Ready | 2026-10-28 | Monthly cap guard negative test: applying >2 days in a month blocked with 400 Bad Request |
| E-WFH-06 | Ready | 2026-10-28 | Duplicate request guard negative test: overlapping active WFH date blocked with 400 Bad Request |
| E-WFH-07 | Ready | 2026-10-28 | Leave mutual exclusion guard: applying for WFH on dates with approved leave blocked with 400 Bad Request |
| E-WFH-08 | Ready | 2026-10-28 | Chronological date guard: fromDate after toDate blocked with 400 Bad Request |
| E-WFH-09 | Ready | 2026-10-28 | Multi-day half-day guard: multi-day range with half-day switch ON blocked with 400 Bad Request |
| E-WFH-10 | Ready | 2026-10-28 | Withdraw PENDING request (/v1/wfh-requests/{id}/withdraw); transitions to WITHDRAWN; pending quota restored |
| E-WFH-11 | Ready | 2026-10-28 | Cancel APPROVED future request (/v1/wfh-requests/{id}/cancel); transitions to CANCELLED; ledger reversed & attendance deleted |

## Defects

| # | Date | Tester | Step | Role | URL | What happened | Expected | Founder no. |
|---|---|---|---|---|---|---|---|---|
| 3 | 2026-10-14 | Krushna | L-01 | HR | /leave/types | In Policy Settings drawer, the input field for "Past-booking limit" (pastBookingLimitDays) is missing from the form | Policy form should have an input field for past-booking limit days | Fixed (`df659fdf`) |
| 5 | 2026-10-14 | Krushna | L-10 | Manager | /approvals/delegations | Manager role lacks core.approval.delegate permission. "Delegations" is missing under Approvals in the sidebar, and navigating directly to /approvals/delegations shows "Module Not Subscribed" (Not Entitled). | Manager role should hold core.approval.delegate to access /approvals/delegations and delegate approval authority during absence. | Fixed (`df659fdf`) |
| 1 | 2026-10-13 | Mohit | S-05 | payroll-officer | /payroll/prior-payroll | Upload prior payroll CSV fails with "Not permitted: requires action 'core.document.upload'" | Role payroll-officer should hold core.document.upload or prior payroll import should allow payroll-officer | Fixed & Verified on live |
| 6 | 2026-10-14 | Karmaveer | K-11, K-12 | Tenant Admin | /audit | Actor column displays Keycloak user ID (UUID) instead of user email ID | Actor column displays user email ID | Fixed (`ed707988`) |
| | 2026-10-14 | Sahmad | E-01 | employee | /welcome, /me | Reporting manager missing on Welcome page and profile | Reporting manager name displayed beside department and designation | Fixed & Verified on live (BUG-D5-021) |
| | 2026-10-14 | Sahmad | E-03a | employee | /me/leave/apply | Attaching document to SL application throws: "Leave policy requires supporting documents to be attached" (Trace ID: 9dc26ed2-63de-4777-a198-bef4f71a4a2b) | Attached documentId is sent in payload and leave application succeeds | Fixed & Verified on live (`94b31fad` / `a741bb1d`) |
| | 2026-10-14 | Sahmad | E-09 | employee | /me (claims) | In New Claim drawer, component dropdown is empty / has no data, blocking submission of reimbursement claim | Reimbursement components (e.g. Internet) selectable in dropdown | Fixed & Verified on live (V175) |
| | 2026-10-14 | Sahmad | E-08 | employee | /me | FBP tab was missing from employee portal; no panel existed for employee to declare flexible benefits | FBP tab/panel available on /me allowing Meal and Fuel declarations | Fixed & Verified on live (BUG-D5-025) |
| 7 | 2026-10-14 | Mohit, Sahmad | R-03, E-10a | employee | /public/payslips | Email notification "Download it" link opens /public/payslips?t=... which renders a 404 page (public route not mounted in frontend) | Opens unauthenticated public payslip viewer / downloads payslip without login | Fixed & Verified on live (787b068e, 4f6deebe) |
| 8 | 2026-10-15 | Mohit | M-15 | payroll-officer | /payroll/runs/new-off-cycle | Saving off-cycle pay inputs for employee bonus fails with "Not permitted: requires action 'core.pay_input.write'" | Role payroll-officer should hold core.pay_input.write to enter off-cycle payouts or endpoint should accept payroll.run.execute | Fixed & Verified on live |
| 9 | 2026-10-15 | Mohit | R-04 | payroll-officer | /payroll/runs/:id | Pay run detail screen has no "Export to Excel" / XLSX button and backend lacks export endpoint | Export to Excel / XLSX button provided on pay run page to download pay run line totals | Fixed & Verified on live (03c9700a) |
| 10 | 2026-10-15 | Mohit, Sahmad | R-02, E-11 | payroll-officer, employee | /payroll/tax/form-16, /me | Form 16 screen not mounted in frontend (W-47 unbuilt); no UI available to generate Form 16 statement or upload Part A ZIP | UI screen provided to view/generate Form 16 statement and upload Part A TRACES ZIP, plus employee self-service panel | Fixed & Verified on live (04e8e9d8) |
| 11 | 2026-10-28 | Krushna | W-02 | HR | /wfh/policies | Not showing any default fallback policy card on /wfh/policies | Screen should render default fallback policy (DEFAULT_WFH) card with 12d annual, 2d monthly, half-day allowed | Fixed & Verified on live (BUG-D4-01) |
| 12 | 2026-10-28 | Krushna | W-01, W-03 | HR | /wfh/policies | WFH policy cards lack "Edit Policy" action button present in leave policies | Each policy card should have an "Edit" action button to update velocity caps and notice rules | Fixed & Verified on live (BUG-D4-01b) |
| 13 | 2026-10-28 | Krushna | W-04 | HR | /wfh/allocations | Quota allocations table fails to render; screen is blank / has no table | Allocations table should render with employee names, department, entitlement, used, and balance | Fixed & Verified on live (BUG-D4-02) |
| 14 | 2026-10-28 | Krushna | W-05 | HR | /wfh/allocations | "Assign Allocation" drawer displays raw employee UUIDs instead of Employee Code/Name, and typing provides no search autocomplete | Searchable employee picker displaying [EMP_CODE] Full Name with typeahead suggestions | Fixed & Verified on live (BUG-D4-03) |
| 15 | 2026-10-28 | Krushna | W-06 | HR | /wfh/import | Standard bulk CSV import template is missing the required effective_date header column | Downloaded CSV template should contain: employee_number, wfh_year, entitlement_days, effective_date | Fixed & Verified on live (BUG-D4-04) |
| 16 | 2026-10-28 | Krushna | W-08b | HR, Manager, Employee | /wfh/requests, /me/wfh | WFH Applications & History tables lack "Actions" column and "👁 View" button present in Leave requests | Include "Actions" column with "👁 View" button to open request details drawer with reason and balance breakdown | Fixed & Verified on live (BUG-D4-05) |
| 17 | 2026-10-28 | Krushna | W-09 | Manager | /approvals | In /approvals, WFH requests lack "👁 View Item" button, and subject displays raw enum with bad wrap (WORK_FROM_HOME Request) | Render "👁 View Item" button matching Leave requests and format title as "Work From Home Request" | Fixed & Verified on live (BUG-D4-06) |
| 18 | 2026-10-28 | Krushna | W-09 | Manager, HR | /approvals, /wfh/requests | After Manager approves WFH request at Level 1, workflow fails to route to HR for Level 2 approval; missing from HR inbox | Request should advance to PENDING_HR_APPROVAL and generate pending approval task for HR | Fixed & Verified on live (BUG-D4-07) |
| 19 | 2026-10-28 | Krushna, Sayeed | E-WFH-01, W-04 | employee, HR | /me/wfh, /v1/me/wfh-balance | Newly created employee automatically gets 12 days WFH balance without HR allocation; shows 12/12 before any quota is assigned | Employee WFH balance should be 0 (or unallocated) until HR explicitly assigns quota via /wfh/allocations or bulk import | Fixed & Verified on live (BUG-D4-08) |
| 20 | 2026-10-29 | Krushna | W-08b, W-09 | employee, manager, HR | /wfh/requests, /me/wfh | When employee applies for WFH and manager approves at Step 1, WFH Application Details modal only shows static "PENDING" and raw UUID, failing to show Manager approved status or HR pending status | WFH Application Details modal should render multi-stage Approval Trail matching Leave (Step 1 Manager Approved with approver, timestamp, note; Step 2 HR Pending) | Fixed on main (BUG-D4-09) |
| 21 | 2026-10-29 | Krushna, Sayeed | W-11, S21-17 | HR, Auditor | /hrms/attendance-log | In Attendance Log (/hrms/attendance-log), approved WFH days only show raw clock punches (In, Out, Origin: Clock) and lack a Status/Remarks column to show automatic "PRESENT (Work From Home)" badge | Attendance Log table should display Day Status / Remarks column showing "PRESENT" with "Work From Home" badge for approved WFH dates per S21-17 test specification | Fixed on main (BUG-D4-10) |


