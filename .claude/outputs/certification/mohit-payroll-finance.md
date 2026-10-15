# Mohit — payroll officer + finance plan (`mbirla@infinevocloud.com`, ITC-0043)

Before you start: P1 is Pass in `TRACKER.md` and the seeded October pay run is deleted (P0). Record every step in `TRACKER.md`.

## Phase P2 — unblocks Sahmad

| Id | Screen | Do | Expect |
|---|---|---|---|
| M-01 | `/payroll/settings/pay-schedule` | Monthly, pay day last working day, first period Oct 2026 | Saved; `/payroll/runs` offers Oct 2026 |
| M-02 | `/payroll/settings/fbp` | Plan with Meal 2,200 per month and Fuel 1,000 per month, declaration open | Saved; Sahmad sees FBP on `/me` |
| M-03 | `/payroll/settings/professional-tax` | Karnataka and Maharashtra slabs | Saved; BLR and PUN employees get PT |
| M-04 | `/payroll/settings/tax-declaration` | Open FY 2026-27 window to 2026-11-30 | Saved; `/payroll/settings/tax-declaration/2026` shows it |
| M-05 | `/payroll/settings/epf`, `/payroll/settings/esi` | Check seeded values: EPF 12/12, ESI 0.75/3.25, deductor TAN BLRI12345A | As seeded |

## Salary

| Id | Screen | Do | Expect |
|---|---|---|---|
| S-01 | `/payroll/components` | List; add component `Shift Allowance`, taxable, fixed | Appears; usable in structures |
| S-02 | `/employees/:id` salary tab, NH-1…NH-5 | Assign CTC per README | Monthly gross shown; Basic, HRA, PF computed |
| S-03 | NH-2 | Confirm ESI eligible at 2,40,000 | ESI lines present |
| S-04 | ITC-0044 Sahmad | Revision effective 2026-10-01, plus 10 percent | Two revisions listed; Oct run uses the new one |
| S-05 | `/payroll/prior-payroll` | Import prior payroll for NH-1, Apr–Sep 2026 | Totals shown; counted in tax |

## Pay inputs and claims, before the run

| Id | Screen | Do | Expect |
|---|---|---|---|
| P-01 | pay inputs | Add Shift Allowance 3,000 for Sahmad, Oct, tagged to the run | Listed, status pending run |
| P-02 | `/payroll/deductions` | Ad-hoc deduction 500 for Sahmad, Oct | Listed |
| P-03 | `/payroll/claims` as finance / tenant-admin | Approve Sahmad's Internet 1,500 claim (after E-09); reject NH-1's test claim with a reason | Approved claim queued to the Oct run; rejected one shows the reason to the employee (routes to tenant-admin per V089/V145 seed) |
| P-04 | `/employees/:id/tax-declaration/2026-27` | View Sahmad's declaration (after E-08a) | Old regime, 80C 1,50,000, HRA, 80D visible; read-only to you |

## Pay run October 2026 — J1, wait for Krushna's handoff

| Id | Screen | Do | Expect |
|---|---|---|---|
| M-10 | `/payroll/runs` | Create Oct 2026 | Draft with all active employees; NH-3 included after reactivation |
| M-11 | `/payroll/runs/:id` | Review LOP and inputs | Sahmad 0 LOP (approved CL); NH-2 LOP days from L-09; Sahmad's 3,000 input, 500 deduction and 1,500 claim present |
| M-12 | same | Compute | Status computed; worker finishes within minutes; no employee errored |
| M-13 | same | Check Sahmad's line | Gross = revised CTC/12 + 3,000; PF 12 percent of basic; PT by location; TDS per old regime; net = gross − PF − PT − TDS − 500 + 1,500 |
| M-13a | same | Check NH-2 | ESI employee 0.75 percent, employer 3.25 percent; LOP reduces earnings pro rata |
| M-14 | same | Lock, then as finance approve and Mark paid | Status paid; Sahmad sees the payslip on `/me` |
| M-15 | `/payroll/runs/new-off-cycle` | Off-cycle for NH-4, bonus 10,000, Oct | Separate run, computed, paid |
| M-16 | `/payroll/runs/:id` | Try to edit a paid run | Refused |

## After the run — J2

| Id | Screen | Do | Expect |
|---|---|---|---|
| R-01 | `/payroll/dashboard` | Compare total gross, net, PF, TDS, headcount with the run | Figures match |
| R-02 | TDS / Form 16 | Generate the Form 16 statement for Sahmad; upload a Part A PDF | Downloadable; Sahmad sees it |
| R-03 | payslip | Open Sahmad's signed payslip link from his `/me` | Opens without a login prompt; expires per spec |
| R-04 | exports | Export the run to XLSX | File opens; totals equal R-01 |

## Negative

| Id | Screen | Do | Expect |
|---|---|---|---|
| N-01 | `/employees/new`, `/leave/types` | Type the URL | Refused |
| N-02 | `/admin` | Type the URL | Refused |

Handoffs: tell Sahmad when M-14 is Pass (payslip) and when R-02 is Pass (Form 16).
