# Sahmad — employee plan (`sahmad@infinevocloud.com`, ITC-0044)

Before you start: P2 is Pass in `TRACKER.md`. Your manager is Krushna. Record every step in `TRACKER.md`.

## Portal

| Id | Screen | Do | Expect |
|---|---|---|---|
| E-01 | first sign-in | Welcome page, then `/me` | Name, designation, department, manager as words; no UUID anywhere |
| E-02 | `/me` documents tab | Upload a PDF, open it, delete it | Works; dialog closes |

## Leave

| Id | Screen | Do | Expect |
|---|---|---|---|
| E-03 | `/me/leave/apply` | CL 2026-10-15 to 16, reason | Pending with manager Krushna; balance shows 2 reserved |
| E-03a | `/me/leave/apply` | SL 2026-10-20, reason | Pending (Krushna rejects in L-07); you see the rejection reason |
| E-03b | `/me/leave/apply` | EL 2026-10-27, then cancel it | Cancelled; balance restored |
| E-03c | `/me/leave/apply` | CL for 2026-10-31 (restricted holiday after Y-01) | Refused or warned per policy |
| E-04 | `/me/leave` | After L-06 | CL approved; balance 10 |

## Attendance, overtime, timesheet

| Id | Screen | Do | Expect |
|---|---|---|---|
| E-05 | `/hrms/attendance` | Clock in and out the same day; next day clock in only | Log shows times; missing clock-out flagged |
| E-05a | `/hrms/regularizations` | Regularise the missing clock-out | Pending; approved after T-03 |
| E-06 | `/hrms/overtime-requests` | OT 2026-10-18, 3 hours, remarks | Pending; approved after T-04 |
| E-06a | `/hrms/my-work` | After T-05 | Cert Project and its tasks listed |
| E-07 | `/hrms/timesheets/week/2026-10-12` | 8 h per day Mon–Fri on Cert Project, submit | Submitted; locked for edit; approved after T-06 |

## Benefits and tax

| Id | Screen | Do | Expect |
|---|---|---|---|
| E-08 | `/me` FBP | Declare Meal 2,200, Fuel 1,000 | Saved; totals within plan caps |
| E-08a | `/me` tax declaration FY 2026-27 | Pick new regime, save; switch to old; enter 80C 1,50,000, HRA rent 15,000 per month with landlord PAN, 80D 25,000, other income 0 | Saved; summary shows estimated tax for both regimes |
| E-08b | same | Upload a proof | Known exception D-25; note what happens, do not raise |
| E-09 | `/payroll/claims` | New claim Internet 1,500, October, attach receipt PDF | Submitted; approved after P-03; amount visible |

## After the pay run — J2

| Id | Screen | Do | Expect |
|---|---|---|---|
| E-10 | `/me` payslips | Oct 2026 payslip | Period, paid on, net pay; opens `/me/payslips/:id` with the figures Mohit checked in M-13 |
| E-10a | same | Signed link | Opens without login |
| E-11 | `/me` tax | TDS to date; Form 16 after R-02 | Present |

## Negative

| Id | Screen | Do | Expect |
|---|---|---|---|
| N-01 | `/employees`, `/leave/requests`, `/payroll/runs`, `/admin` | Type each URL | Refused; not in menu |
| N-02 | `/employees/<another id>` | Type a colleague's id from the seed | Refused |
| N-03 | `/me/leave/requests/<id>` | Open a leave request that is not yours | Refused |

Handoffs: tell Krushna after E-03, E-05a, E-06, E-07; tell Mohit after E-08a and E-09.
