# Certification tracker — Infinevo Test Co

Status values: `Blocked` `Ready` `Pass` `Fail` `Exception`. Update your own rows only. One line per step. A Fail also gets one row in Defects at the bottom.

## Gates

| Gate | Owner | Needs | Status | Date |
|---|---|---|---|---|
| P0 Environment: worker fixed, seeded Oct pay run deleted, four logins work | Founder | – | Ready | |
| P1 Setup: K-00…K-07x | Karmaveer | P0 | Pass | 2026-10-13 |
| P2 People and payroll config: K-05, K-06, M-01…M-05 | Krushna, Mohit | P1 | Ready | |
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
| K-20 | Blocked | | staff.infinevo |
| K-21 | Blocked | | staff.infinevo |
| K-22 | Blocked | | |
| K-23 | Blocked | | |
| K-24 | Blocked | | staff.infinevo |
| K-25 | Blocked | | staff.infinevo |
| K-26 | Blocked | | staff.infinevo |
| K-11 | Blocked | | J2 |
| K-12 | Blocked | | J2 |
| K-13 | Blocked | | J2 |
| K-14 | Blocked | | |
| N-01 | Ready | | |
| N-02 | Blocked | | |

## Krushna — HR + manager (`ksonalkarinfinevocloud@ooynib.com`, ITC-0045)

| Id | Status | Date | Note |
|---|---|---|---|
| K-05 | Pass | 2026-10-14 | |
| K-05a | Pass | 2026-10-14 | |
| K-05b | Pass | 2026-10-14 | |
| K-06 | Pass | 2026-10-14 | Fixed: core.role.assign granted to hr; role selection modal added to EmployeePage |
| K-07 | Pass | 2026-10-14 | |
| H-01 | Pass | 2026-10-14 | |
| H-02 | Pass | 2026-10-14 | Fixed: core.document.delete granted to hr; Delete button visible in DocumentsTab |
| H-03 | Pass | 2026-10-14 | |
| H-04 | Pass | 2026-10-14 | |
| H-05 | Pass | 2026-10-14 | |
| H-06 | Pass | 2026-10-14 | |
| L-01 | Pass | 2026-10-14 | Fixed: pastBookingLimitDays field added to PolicyForm |
| L-02 | Pass | 2026-10-14 | Saved with manual date 2026-01-01 (known exception D-55) |
| L-03 | Pass | 2026-10-14 | |
| L-04 | Pass | 2026-10-14 | |
| L-05 | Pass | 2026-10-14 | Approved at Level 1 (Manager) |
| L-06 | Pass | 2026-10-14 | Fully approved at Level 2 (HR) |
| L-07 | Blocked | 2026-10-14 | Defer: request not visible in inbox (needs E-03a) |
| L-08 | Pass | 2026-10-14 | Fixed: core.leave.manage granted to hr; Record Leave button enabled |
| L-09 | Ready | 2026-10-14 | Unblocked by L-08 fix |
| L-10 | Pass | 2026-10-14 | Fixed: core.approval.delegate granted to manager; Delegations menu and route enabled |
| L-11 | Pass | 2026-10-14 | Known exception D-43 behavior noted and documented; record system outcome. |
| Y-01 | Pass | 2026-10-14 | Saved; default toggle switch is disabled when creating or editing a secondary calendar. |
| Y-02 | Pass | 2026-10-14 | Lists all October 2026 holidays including the newly added restricted holiday on 31 Oct. |
| T-01 | Pass | 2026-10-14 | Fixed: grace_period_minutes added to schema, DTOs, service, and AttendanceSettings form |
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
| M-01 | Blocked | | |
| M-02 | Blocked | | |
| M-03 | Blocked | | |
| M-04 | Blocked | | |
| M-05 | Blocked | | |
| S-01 | Blocked | | |
| S-02 | Blocked | | needs K-05 |
| S-03 | Blocked | | |
| S-04 | Blocked | | |
| S-05 | Blocked | | |
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
| E-01 | Blocked | | |
| E-02 | Blocked | | |
| E-03 | Blocked | | |
| E-03a | Blocked | | |
| E-03b | Blocked | | |
| E-03c | Blocked | | needs Y-01 |
| E-04 | Blocked | | needs L-06 |
| E-05 | Blocked | | |
| E-05a | Blocked | | |
| E-06 | Blocked | | |
| E-06a | Blocked | | needs T-05 |
| E-07 | Blocked | | |
| E-08 | Blocked | | needs M-02 |
| E-08a | Blocked | | needs M-04 |
| E-08b | Exception | | D-25 |
| E-09 | Blocked | | |
| E-10 | Blocked | | J2 |
| E-10a | Blocked | | |
| E-11 | Blocked | | needs R-02 |
| N-01 | Blocked | | |
| N-02 | Blocked | | |
| N-03 | Blocked | | |

## Defects

| # | Date | Tester | Step | Role | URL | What happened | Expected | Founder no. |
|---|---|---|---|---|---|---|---|---|
| 1 | 2026-10-14 | Krushna | K-06 | HR | /employees/:id | The HR role has no option to assign roles to employees | HR should be able to assign the Manager role to NH-1 | |
| 2 | 2026-10-14 | Krushna | H-02 | HR | /employees/:id | If an incorrect document is uploaded by mistake, there is no Delete or Remove option in the actions menu to remove it | Uploaded document should have a Delete / Remove option in actions column | |
| 3 | 2026-10-14 | Krushna | L-01 | HR | /leave/types | In Policy Settings drawer, the input field for "Past-booking limit" (pastBookingLimitDays) is missing from the form | Policy form should have an input field for past-booking limit days | |
| 4 | 2026-10-14 | Krushna | L-08 | HR | /leave/requests/new | On /leave/requests, the "Record Leave" button is missing, and accessing /leave/requests/new directly displays "Not Entitled". The HR role is missing the core.leave.manage permission required to record leave on behalf of employees. | HR role should have 'core.leave.manage' permission so the "Record Leave" button is visible and HR can record leave on behalf of employees. | |
| 5 | 2026-10-14 | Krushna | L-10 | Manager | /approvals/delegations | Manager role lacks core.approval.delegate permission. "Delegations" is missing under Approvals in the sidebar, and navigating directly to /approvals/delegations shows "Module Not Subscribed" (Not Entitled). | Manager role should hold core.approval.delegate to access /approvals/delegations and delegate approval authority during absence. | |
| 6 | 2026-10-14 | Krushna | T-01 | HR | /hrms/attendance-settings | In Attendance Settings (/hrms/attendance-settings), the input field for "Grace period duration" (e.g. grace 10 min) is missing from the form. Only hours calculation, minimum hours, and regularization limits are available. | Attendance Settings form should have an input field to configure grace period duration (e.g. 10 min). | |
