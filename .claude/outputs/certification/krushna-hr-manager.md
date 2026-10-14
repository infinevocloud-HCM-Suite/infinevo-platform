# Krushna — HR + manager plan (`ksonalkarinfinevocloud@ooynib.com`, ITC-0045)

Before you start: P1 is Pass in `TRACKER.md`. You act as HR on all screens and as manager for anyone reporting to you. Record every step in `TRACKER.md`; a Fail gets one row in its Defects section.

## Phase P2 — unblocks Sahmad and Mohit

| Id | Screen | Do | Expect |
|---|---|---|---|
| K-05 | `/employees/new` | Add the five new hires from README, tab by tab (personal, identification, bank). NH-1 and NH-4 report to you | Each saved with an employee number; list shows department and designation as words |
| K-05a | `/employees/new` | Try NH-1 again with the same work email, then the same PAN | 400, error beside the field, nothing created |
| K-05b | `/employees/new` | Enter the name in lower case `ritu anand` | Saved as `Ritu Anand` |
| K-06 | each new hire page | Send invitation (NH-1 also gets the manager role) | Row PENDING on `/users?tab=invitations`; email arrives |
| K-07 | `/employees/:id` of ITC-0044 | Confirm Sahmad's manager is you; if not, set the reporting line to yourself | Reporting line shows Krushna Sonalkar |

## Employees

| Id | Screen | Do | Expect |
|---|---|---|---|
| H-01 | `/employees` | Search by name, by number; filter by department | Correct rows; dates as dd-mmm-yyyy, no codes |
| H-02 | `/employees/:id` | Edit personal, identification, bank; upload one document; delete it | Saved; document gone; dialog closes |
| H-03 | `/employees/:id` | Change NH-5's manager to ITC-0005 | Reporting line updated; ITC-0005 sees NH-5 as a direct report |
| H-04 | `/employees/:id` | Terminate NH-3 with reason and remarks | Status terminated; reason stored and shown |
| H-05 | same | Reactivate NH-3 | Active again; history keeps the exit |
| H-06 | `/employees/import` | Download the template, import 2 rows | 2 created; errors reported per row if any |

## Leave setup and requests

| Id | Screen | Do | Expect |
|---|---|---|---|
| L-01 | `/leave/types` | Open CL policy, change past-booking limit to 15 days, save | Saved; effective-from kept |
| L-02 | `/leave/types` | New type `Comp Off`, 5 days, policy saved without an effective date | Starts 1 Jan 2026 automatically |
| L-03 | `/leave/allocations` | Allocate Comp Off 5 to Sahmad | Balance 5 on `/leave/employees/:id` |
| L-04 | `/leave/employees/:id` | Open Sahmad | CL 12, EL 18, SL 10, Comp Off 5, minus any seeded use |
| L-05 | `/approvals` as manager | Approve Sahmad's CL 15–16 Oct (after E-03) | Moves to the HR step |
| L-06 | `/leave/requests` as HR | Approve the same request | Approved; Sahmad's CL balance down by 2 |
| L-07 | `/approvals` | Reject Sahmad's SL 20 Oct with a comment | Rejected; balance unchanged; Sahmad sees the reason |
| L-08 | `/leave/requests/new` | Apply on behalf of NH-2: EL 2026-10-22, 3 days | Pending at manager; visible to NH-2 |
| L-09 | `/leave/requests` | Approve both steps; apply on behalf again for NH-2 beyond balance | Second one marked LOP per policy |
| L-10 | `/approvals/delegations` | Delegate your approvals to ITC-0002 for one day; Sahmad applies again | ITC-0002 sees it |
| L-11 | `/leave/import` | Import one balance adjustment | Known exception D-43; note behaviour only |

## Holidays

| Id | Screen | Do | Expect |
|---|---|---|---|
| Y-01 | `/holidays` | Open the default calendar, add a restricted holiday 2026-10-31 | Saved; the default switch is disabled for a second calendar |
| Y-02 | `/holidays/lookup` | Look up Oct 2026 | Lists it |

## Attendance, overtime, projects, timesheets

| Id | Screen | Do | Expect |
|---|---|---|---|
| T-01 | `/hrms/attendance-settings` | Set grace 10 min, save | Saved |
| T-02 | `/hrms/attendance-log` | Open Oct for Sahmad (after E-05) | His clock rows present |
| T-03 | `/hrms/regularizations/all` | Approve Sahmad's regularisation | Approved; log corrected |
| T-04 | `/hrms/overtime-requests/all` | Approve Sahmad's OT 18 Oct, 3 h | Approved; appears in the HR overtime list |
| T-05 | `/hrms/projects` | Create project `Cert Project`, add Sahmad and NH-1, two tasks | Visible on their `/hrms/my-work` |
| T-06 | `/hrms/timesheet-review` as manager | Approve Sahmad's week of 12 Oct per project | Approved; hours total 40 |
| T-07 | `/hrms/timesheet-review/:id` | Reject NH-1's week with a comment | Status rejected; NH-1 can edit |
| T-08 | `/hrms/dashboard` | Compare headcount, on leave today, pending approvals with what you did | Figures match |

## Negative

| Id | Screen | Do | Expect |
|---|---|---|---|
| N-01 | `/payroll/runs`, `/payroll/settings` | Type the URL | Not in menu; screen refuses |
| N-02 | `/admin` | Type the URL | Refused |

Handoffs: tell Mohit when L-06, L-09, T-03, T-04 and T-06 are Pass. He cannot run October payroll before that.
