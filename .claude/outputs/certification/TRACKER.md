# Certification tracker — Infinevo Test Co

Status values: `Blocked` `Ready` `Pass` `Fail` `Closed`. `Closed` means the step was wrong or already covered and there is nothing left to run. Update your own rows only. One line per step. A Fail also gets one row in Defects at the bottom.

## Gates

| Gate | Owner | Needs | Status | Date |
|---|---|---|---|---|
| P0 Environment: worker fixed, seeded Oct pay run deleted, four logins work | Founder | – | Ready | 2026-10-14 |
| P1 Setup: K-00…K-07x | Karmaveer | P0 | Pass | 2026-10-13 |
| P2 People and payroll config: K-05, K-06, M-01…M-05 | Krushna, Mohit | P1 | Pass | 2026-10-13 |
| J1 October pay run may start: L-06, L-09, T-03, T-04, T-06, E-07, E-08a, E-09, S-02, P-01…P-03 | Mohit | P2 | Blocked | |
| J2 Reconciliation: M-14 paid | all | J1 | Blocked | |

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
| K-15 | Ready | | Moved from H-02: delete the document Krushna uploaded |
| K-16 | Ready | | Moved from L-08: apply on behalf of NH-2, lands Approved |
| K-20 | Pass | 2026-10-14 | Platform overview shows 4 tiles (Total, Active, Suspended, Waiting for admin); Infinevo Test Co listed |
| K-21 | Pass | 2026-10-14 | Second tenant Isolation Co provisioned: IN, April leave year, HRMS and PAYROLL, India template |
| K-22 | Pass | 2026-10-14 | LEGAL department and Jane Doe employee added inside Isolation Co |
| K-23 | Pass | 2026-10-14 | Neither LEGAL nor Jane Doe visible in Infinevo Test Co |
| K-24 | Pass | 2026-10-14 | Act as Isolation Co with reason; banner shown, employees checked, stopped, audit trail verified |
| K-25 | Pass | 2026-10-14 | HRMS module revoked from Isolation Co; HRMS menu removed |
| K-26 | Pass | 2026-10-14 | Isolation Co suspended; locked out of module screens |
| K-11 | Pass | 2026-10-14 | Actor column displays user email ID; audit entries verified with actor, time, and diffs |
| K-12 | Pass | 2026-10-14 | Actor filter by email verified; displays filtered user actions |
| K-13 | Ready | | J2 |
| K-14 | Ready | | |
| N-01 | Pass | 2026-10-14 | /admin as tenant admin refused (403 / Not Entitled) |
| N-02 | Pass | 2026-10-14 | Pay run compute refused without payroll-officer role |

## Krushna — HR + manager (`ksonalkarinfinevocloud@ooynib.com`, ITC-0045)

| Id | Status | Date | Note |
|---|---|---|---|
| K-05 | Pass | 2026-10-14 | |
| K-05a | Pass | 2026-10-14 | |
| K-05b | Pass | 2026-10-14 | |
| K-06 | Closed | 2026-10-14 | Invitations sent. Role assignment is admin-only and passed under K-08 on 2026-10-13; step rewritten |
| K-07 | Pass | 2026-10-14 | |
| H-01 | Pass | 2026-10-14 | |
| H-02 | Closed | 2026-10-14 | Edit and upload passed. Delete is admin-only (V037 decision 2) and moved to K-15 |
| H-03 | Pass | 2026-10-14 | |
| H-04 | Pass | 2026-10-14 | |
| H-05 | Pass | 2026-10-14 | |
| H-06 | Pass | 2026-10-14 | |
| L-01 | Fail | 2026-10-14 | In the Policy Settings drawer, the input field for "Past-booking limit" (pastBookingLimitDays) is missing from the form. |
| L-02 | Pass | 2026-10-14 | Saved with manual date 2026-01-01 (known exception D-55) |
| L-03 | Pass | 2026-10-14 | |
| L-04 | Pass | 2026-10-14 | |
| L-05 | Pass | 2026-10-14 | Approved at Level 1 (Manager) |
| L-06 | Pass | 2026-10-14 | Fully approved at Level 2 (HR) |
| L-07 | Blocked | 2026-10-14 | Defer: request not visible in inbox (needs E-03a) |
| L-08 | Ready | 2026-10-14 | Rewritten: NH-2 applies on /me/leave/apply. Admin on-behalf moved to K-16 |
| L-09 | Ready | 2026-10-14 | Rewritten: approve NH-2's request, then NH-2 applies beyond balance |
| L-10 | Fail | 2026-10-14 | Manager role lacks core.approval.delegate permission. "Delegations" is missing under Approvals in the sidebar, and navigating directly to /approvals/delegations shows "Module Not Subscribed" (Not Entitled). |
| L-11 | Pass | 2026-10-14 | Known exception D-43 behavior noted and documented; record system outcome. |
| Y-01 | Pass | 2026-10-14 | Saved; default toggle switch is disabled when creating or editing a secondary calendar. |
| Y-02 | Pass | 2026-10-14 | Lists all October 2026 holidays including the newly added restricted holiday on 31 Oct. |
| T-01 | Closed | 2026-10-14 | Removed: attendance grace period is not a feature of the platform |
| T-02 | Pass | 2026-10-14 | |
| T-03 | Pass | 2026-10-14 | Regularisation approved; attendance log times corrected automatically. |
| T-04 | Pass | 2026-10-14 | Approved; approved overtime is listed in HR overtime summary ready for payroll sync. |
| T-05 | Pass | 2026-10-14 | Project saved; tasks visible on Sahmad and NH-1's /hrms/my-work dashboard. |
| T-06 | Pass | 2026-10-14 | Timesheet approved; cumulative logged hours total 40 hours. |
| T-07 | Pass | 2026-10-14 | Status transitions to REJECTED; NH-1 can view reason and reopen timesheet for editing. |
| T-08 | Pass | 2026-10-14 | Dashboard metric counts match executed certification activities. |
| N-01 | Pass | 2026-10-14 | Route not present in menu; application denies access with 403 Forbidden screen. |
| N-02 | Pass | 2026-10-14 | Access refused with 403 Forbidden screen. |

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
| S-05 | Fail | 2026-10-13 | Upload fails: requires action core.document.upload |
| P-01 | Blocked | | |
| P-02 | Blocked | | |
| P-03 | Blocked | | needs E-09 |
| P-04 | Blocked | | needs E-08a |
| M-10 | Blocked | | J1 |
| M-11 | Blocked | | |
| M-12 | Blocked | | |
| M-13 | Blocked | | |
| M-13a | Blocked | | |
| M-14 | Blocked | | |
| M-15 | Blocked | | |
| M-16 | Blocked | | |
| R-01 | Blocked | | J2 |
| R-02 | Blocked | | |
| R-03 | Blocked | | |
| R-04 | Blocked | | |
| N-01 | Blocked | | |
| N-02 | Blocked | | |

## Sahmad — employee

| Id | Status | Date | Note |
|---|---|---|---|
| E-01 | Pass | 2026-10-14 | Name, designation, department, and reporting manager displayed as readable words on Welcome page and /me profile |
| E-02 | Ready | | Delete removed from the step; upload and open only |
| E-03 | Pass | 2026-10-14 | CL 15-16 Oct applied; pending with Krushna, 2 days reserved |
| E-03a | Fail | 2026-10-14 | Validation error: Leave policy requires supporting documents to be attached even after attaching document |
| E-03b | Pass | 2026-10-14 | EL 27 Oct applied and cancelled; balance restored |
| E-03c | Blocked | | needs Y-01 |
| E-04 | Pass | 2026-10-14 | CL approved by manager; balance updated and verified on /me/leave |
| E-05 | Pass | 2026-10-14 | Clock-in/out logged; missing clock-out flagged |
| E-05a | Pass | 2026-10-14 | Regularization saved and pending; view requires date range filter (known D-47) |
| E-06 | Pass | 2026-10-14 | Overtime 18 Oct 3h saved and pending; view requires date range filter (known D-47) |
| E-06a | Pass | 2026-10-14 | Cert Project and assigned tasks listed on /hrms/my-work |
| E-07 | Pass | 2026-10-14 | Week of 2026-10-12 timesheet submitted with 40h on Cert Project; locked for edit |
| E-08 | Fail | 2026-10-14 | No FBP (Flexible Benefit Plan) tab or panel exists on /me portal (BUG-D5-025) |
| E-08a | Pass | 2026-10-14 | HRA ₹1.8L and VI-A ₹1.75L declared; submitted successfully; status changed to Submitted |
| E-08b | Closed | | Removed: proof-of-investment screens are known exception D-25 |
| E-09 | Fail | 2026-10-14 | Component dropdown in New Claim drawer has no data to select, blocking claim submission |
| E-10 | Blocked | | J2 |
| E-10a | Blocked | | |
| E-11 | Blocked | | needs R-02 |
| N-01 | Pass | 2026-10-14 | All admin/staff URLs (/employees, /leave/requests, /payroll/runs, /admin) refused with 404 (routes unmounted) |
| N-02 | Pass | 2026-10-14 | Direct colleague profile URL (/employees/<id>) refused with 404 |
| N-03 | Pass | 2026-10-14 | Access to non-owned leave request refused with 404 |

## Defects

| # | Date | Tester | Step | Role | URL | What happened | Expected | Founder no. |
|---|---|---|---|---|---|---|---|---|
| 3 | 2026-10-14 | Krushna | L-01 | HR | /leave/types | In Policy Settings drawer, the input field for "Past-booking limit" (pastBookingLimitDays) is missing from the form | Policy form should have an input field for past-booking limit days | |
| 5 | 2026-10-14 | Krushna | L-10 | Manager | /approvals/delegations | Manager role lacks core.approval.delegate permission. "Delegations" is missing under Approvals in the sidebar, and navigating directly to /approvals/delegations shows "Module Not Subscribed" (Not Entitled). | Manager role should hold core.approval.delegate to access /approvals/delegations and delegate approval authority during absence. | |
| 1 | 2026-10-13 | Mohit | S-05 | payroll-officer | /payroll/prior-payroll | Upload prior payroll CSV fails with "Not permitted: requires action 'core.document.upload'" | Role payroll-officer should hold core.document.upload or prior payroll import should allow payroll-officer | |
| 6 | 2026-10-14 | Karmaveer | K-11, K-12 | Tenant Admin | /audit | Actor column displays Keycloak user ID (UUID) instead of user email ID | Actor column displays user email ID | Fixed (`ed707988`) |
| | 2026-10-14 | Sahmad | E-01 | employee | /welcome, /me | Reporting manager missing on Welcome page and profile | Reporting manager name displayed beside department and designation | BUG-D5-021 |
| | 2026-10-14 | Sahmad | E-03a | employee | /me/leave/apply | Attaching document to SL application throws: "Leave policy requires supporting documents to be attached" (Trace ID: 9dc26ed2-63de-4777-a198-bef4f71a4a2b) | Attached documentId is sent in payload and leave application succeeds | BUG-D5-023 |
| | 2026-10-14 | Sahmad | E-09 | employee | /me (claims) | In New Claim drawer, component dropdown is empty / has no data, blocking submission of reimbursement claim | Reimbursement components (e.g. Internet) selectable in dropdown | BUG-D5-024 |
| | 2026-10-14 | Sahmad | E-08 | employee | /me | FBP tab is missing from employee portal; no panel exists for employee to declare flexible benefits | FBP tab/panel available on /me allowing Meal and Fuel declarations | BUG-D5-025 |
