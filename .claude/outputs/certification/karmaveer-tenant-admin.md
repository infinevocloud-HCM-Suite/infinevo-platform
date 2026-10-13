# Karmaveer — tenant admin plan (`kmohapatra@infinevocloud.com`, ITC-0041)

Before you start: P0 is Pass in `TRACKER.md` (worker fixed, seeded Oct pay run deleted, you can sign in). You go first; Krushna, Mohit and Sahmad wait for your P1 steps. Record every step in `TRACKER.md`.

Infinevo Test Co was seeded by script on 2026-10-09. Section A checks what the seed should have left. Where a check finds nothing, do the "If missing" column before moving on. Either way the step is a test: a seeded screen must open and show its rows as words, not codes.

## A. Company details — verify, create if missing

| Id | Screen | Check | If missing | Expect |
|---|---|---|---|---|
| K-00 | `/settings/company` | Legal name, address, PAN, TAN, financial year start, leave year start | Fill: Infinevo Test Co, 1 Test Park Bengaluru 560001, PAN AAACI1234A, TAN BLRI12345A, FY April, leave year January | Saved; shown on reload |
| K-01 | `/setup` | Checklist shows 8 steps: Work location, Employee, Pay schedule, Prior payroll, Salary components, EPF, ESI, Professional tax, with progress | — | Work location, Employee, Salary components, EPF, ESI already detected done; Pay schedule, Prior payroll, Professional tax to do |
| K-01a | `/setup` | Skip Prior payroll with reason "no prior payroll for test company" | — | Shows skipped with the reason; progress updates |
| K-01b | `/setup` | After Mohit's M-01 and M-03 | — | All steps done or skipped; progress 100 percent; Open on a step goes to its screen |
| K-02 | `/org/departments` | 5 rows: ENG, HR, FIN, SALES, OPS | Create them with those codes and names | Rows listed by name; codes not shown raw |
| K-02a | `/org/designations` | 8 rows: CEO, VP, MGR, SSE, SE, HRBP, ACCT, EXEC | Create them | Listed |
| K-02b | `/org/work-locations` | 3 rows: Bengaluru HQ (filing address), Pune Office, Hyderabad Office | Create via `/org/work-locations/new` with state codes KA, MH, TG | Listed; exactly one filing address |
| K-02c | `/org/departments` | Add `Quality`; then add `Quality` again | — | First saved; second refused as duplicate |
| K-02d | `/org/work-locations/:id/edit` | Edit Pune postcode to 411002, save | — | Saved |
| K-03 | `/holidays` | A default calendar for 2026 | Create `India 2026`, mark default, add Republic Day 26 Jan, Independence Day 15 Aug, Gandhi Jayanti 2 Oct, Diwali 8 Nov, Christmas 25 Dec | Default badge on one calendar; a second calendar cannot take default |
| K-04 | `/approvals/definitions` | Rules exist | Create: Leave = manager then HR; Reimbursement claim = finance; Timesheet = manager; Overtime = manager; Regularisation = manager | Each rule listed with its steps |
| K-05x | `/leave/types` | CL 12, EL 18, SL 10 with policies | Create them with those annual days, effective 2026-01-01 | Listed; policy opens |
| K-06x | LOP policy (under Leave or Payroll settings) | A policy exists | Create: exceed-balance = LOP, LOP deducted in the same month | Saved |
| K-07x | `/employees` | 53 employees ITC-0001 to ITC-0053 | Tell the founder to run the seed again. Do not create by hand | List shows 53 with names, departments, designations as words |

P1 is Pass when K-00 to K-07x are Pass. Tell Krushna and Mohit.

## B. Users, roles and access

| Id | Screen | Do | Expect |
|---|---|---|---|
| K-08 | `/users` Users tab | Open; find Krushna, Mohit, Sahmad | Roles exactly as README: hr+manager+employee; payroll-officer+finance+employee; employee |
| K-08a | `/users` Invitations tab | Any PENDING invitation for the three | Resend; email arrives; Accept sets a password and signs in |
| K-09 | `/users` Invite user | Invite `qa-extra@infinevocloud.com` with role finance | Row PENDING; Revoke it; row gone |
| K-10 | `/roles` | Create custom role `Auditor` with read-only employee and audit permissions; assign to NH-5 on `/users` (after K-05) | NH-5 sees Employees and Audit, nothing else |
| K-10a | `/users` | Change your own roles to drop tenant-admin | Refused as the last tenant admin |
| K-10b | `/users` | Disable NH-5's account, then re-enable | Cannot sign in while disabled |

## C. Platform level, as `staff.infinevo` (founder or you if you hold it)

| Id | Screen | Do | Expect |
|---|---|---|---|
| K-20 | `/admin` | Open | Tiles: total, active, suspended, waiting for admin; Infinevo Test Co listed |
| K-21 | `/admin/tenants/new` | Create `Isolation Co`, country IN, April leave year, both modules, admin email a second address you control | Created; invitation sent; India template applied (holidays, leave types present) |
| K-22 | Isolation Co as its admin | Add one department and one employee | Visible only in Isolation Co |
| K-23 | Infinevo Test Co, `/employees`, `/org/departments` | Search for the Isolation Co employee and department | Not found. Tenant isolation holds |
| K-24 | `/admin/tenants/:id` of Isolation Co | Act as, with a reason; open `/employees`; stop acting | Banner shown while acting; `/audit` of Isolation Co records the impersonation with the reason |
| K-25 | `/admin/tenants/:id` of Isolation Co | Revoke HRMS module | Isolation Co admin loses the HRMS menu; HRMS data read-only |
| K-26 | same | Suspend Isolation Co | Its admin cannot use any module screen |

## D. After the pay run — J2

| Id | Screen | Do | Expect |
|---|---|---|---|
| K-11 | `/audit` | Filter by Krushna's hire of NH-1, L-06 approval, M-14 lock | One entry each with actor, time, before and after |
| K-12 | `/audit` | Filter by Sahmad as actor | Only his own actions |
| K-13 | notifications | Check that the invitation, leave approval and payslip emails were received by the people above | Received; Brevo sender `application@infinevocloud.com` |
| K-14 | exports or scheduled reports | Schedule a weekly headcount report to yourself | Appears in the scheduled list; first run delivered |

## Negative

| Id | Screen | Do | Expect |
|---|---|---|---|
| N-01 | `/admin` as yourself, not staff | Type the URL | Refused |
| N-02 | `/payroll/runs/:id` as yourself | Try Compute | Refused, no payroll role |

Handoffs: tell Krushna and Mohit when P1 is Pass. Tell the founder at once if K-07x finds fewer than 53 employees.
