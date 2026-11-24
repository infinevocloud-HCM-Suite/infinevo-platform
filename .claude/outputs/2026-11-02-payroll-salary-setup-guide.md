# Payroll Salary Setup — Team Guide and Test Cases

**Date:** 2026-11-02 · **Code on main:** `aaf53c21` (W-75, W-76)

## 1. Summary

| Point | In short |
|---|---|
| Company settings | Payroll → Settings sets up pay items, PF, ESI, PT and the pay schedule once for the whole company |
| Employee salary | On the Salary tab, **Set up salary** asks only for the CTC; everything else fills in by itself |
| Balancing | Special Allowance = CTC minus everything else, so totals always match |
| Statutory | PF, EPS, ESI, PT, LWF and Director ticks are set in the same drawer as the salary |
| Golden rule | An item is paid only when the **company switch is on** and the **employee is ticked**; the pay run checks this every month |
| Display-only | Employer PF and ESI "in structure / in CTC" switches only change what is shown; the employer share is always paid when PF or ESI applies |

## 2. Company settings menus (Payroll → Settings)

| Menu | What it does |
|---|---|
| Salary Components | Pay items in 4 tabs: Earnings, Deductions, Benefits, Reimbursements. Each has a default (for example Basic = 50% of CTC, HRA = 50% of Basic) |
| Pay Schedule | Pay day, how working days are counted (calendar, company days or a fixed 30), LOP rounding |
| EPF | PF on or off, rates, wage limit, EPS age limit, EDLI and admin charges |
| ESI | ESI on or off, rates, gross ceiling |
| Professional Tax | PT slabs for each state and the months when PT is deducted |
| Flexible Benefit Plan | Tax-saving benefit items employees can choose from |
| Tax declaration / Form 16 | The employee's tax proof window and the Form 16 settings |

## 3. Switches and what they do

### 3.1 Salary Components — Earning item

| Switch | What it does |
|---|---|
| Include in Salary Structure | Shows the item when you create an employee's salary and fills in its default amount |
| Include in CTC | Counts the item inside CTC; when off, it is paid on top of CTC |
| Taxable Component | Counts the item for income tax (TDS) |
| Calculate Pro-Rata on Attendance/LOP | Cuts the amount for unpaid leave (LOP) days |
| FBP Component | Lets employees use the item for flexible benefit choices |
| Include in ESI | Adds the item to the gross used for ESI |
| EPF inclusion | Sets whether the item counts in the PF wage: always, never, or only when below the limit |
| Variable / One-time / Scheduled | Pay that is not fixed every month, paid once, or planned for a future month |

### 3.2 EPF

| Switch | What it does |
|---|---|
| Enable EPF | Main switch; when off, no PF is paid for anyone, even employees who are ticked |
| Restrict Employee / Employer Share to Wage Ceiling | Works out PF on at most the wage limit (₹15,000) instead of the full Basic |
| Prorate Restricted Wage on LOP | Cuts that limited wage for LOP days |
| Consider Earned Salary | Works out PF on salary actually earned that month, not the full salary |
| Employer Share in Salary Structure / in CTC | **Display only**: employer PF is always paid when PF applies |
| EDLI in salary structure | On: EDLI is shown in the salary and paid. Off: neither |
| EDLI in CTC | Counts EDLI inside CTC; works only when "EDLI in salary structure" is on, and is greyed out otherwise |
| Admin charges in salary structure / in CTC | Same rule as EDLI |

### 3.3 ESI

| Switch | What it does |
|---|---|
| Enable ESI | Main switch; when off, no ESI is paid for anyone |
| Employer Share in Salary Structure / in CTC | **Display only**: employer ESI is always paid when ESI applies |

### 3.4 Pay Schedule

| Switch | What it does |
|---|---|
| Weekends Payable / Holidays Payable | Whether weekends and holidays count as paid days when LOP is worked out |

### 3.5 Professional Tax

| Switch | What it does |
|---|---|
| Female exempt (per slab) | Women in that pay band pay no PT |
| Applicable Months | The months in which PT is deducted |

## 4. Employee salary (Employee → Salary tab)

| Step | What happens |
|---|---|
| 1. Set up salary | A drawer opens; type the CTC and every earning fills in from the company defaults |
| 2. Balancing item | Special Allowance is worked out by itself, so the totals always match CTC |
| 3. Statutory ticks | PF, EPS, ESI, PT, LWF and Director ticks, UAN (12 digits), PF number, ESI number (10 digits); a warning shows if company PF or ESI is off |
| 4. Live summary | Monthly and yearly amounts, employer share, gross, PF/ESI/PT deductions and estimated take-home, before anything is saved |
| 5. Save / Revise | Save creates a salary version; Revise opens the same drawer from the current version |
| 6. Statutory box | The box at the bottom of the tab is view only; edit through Set up salary or Revise |

## 5. Pay run rule (checked every month)

| Item | Paid only when |
|---|---|
| PF (employee + employer) | Company EPF on **and** employee PF ticked |
| EPS | PF applies **and** EPS ticked **and** employee is under the EPS age limit |
| EDLI | PF applies **and** "EDLI in salary structure" on |
| PF admin charges | PF applies **and** "Admin in salary structure" on |
| ESI (employee + employer) | Company ESI on **and** employee ESI ticked **and** gross at or under the ceiling |
| PT | The employee's state slab and the applicable months |

## 6. Test cases

| # | Steps | Expected |
|---|---|---|
| 1 | Set up salary, type CTC 6,00,000 | Basic is 50% of CTC, HRA is 50% of Basic, Special Allowance balances the rest, and the totals match CTC |
| 2 | Company PF on, employee PF ticked, run payroll | Employee and employer PF are both deducted |
| 3 | Untick the employee's PF, run the next month | No PF that month |
| 4 | Turn company PF off, keep the employee ticked | Warning shown in the drawer, and no PF in the pay run |
| 5 | Employee is PF ticked but EPS unticked | No EPS row and no EPS in the pay run |
| 6 | Employee is over the EPS age limit | No EPS; the employer share goes fully to PF |
| 7 | Turn "EDLI in salary structure" off | EDLI is not shown in the salary and not paid |
| 8 | Turn "EDLI in salary structure" on and "EDLI in CTC" on | EDLI is shown and is counted inside CTC |
| 9 | Turn "EDLI in salary structure" off | The "EDLI in CTC" switch is greyed out |
| 10 | ESI on, employee ticked, gross under the ceiling | ESI is deducted |
| 11 | Raise gross above the ESI ceiling | No ESI |
| 12 | Turn the employer PF "in CTC" switch off | Display changes only; employer PF is still paid |
| 13 | Untick "Include in Salary Structure" on an earning | That earning no longer fills in on a new salary |
| 14 | Untick "Include in CTC" on an earning | The earning is paid on top of CTC, and Special Allowance goes up |
| 15 | Tick "Pro-Rata on LOP", give the employee 2 LOP days | That earning is cut for 2 days |
| 16 | Enter an 11-digit UAN, or an ESI number that is not 10 digits | Error, and the salary is not saved |
| 17 | Revise a salary | The drawer opens with current values, and saving creates a new version |
| 18 | Look at the statutory box at the bottom of the tab | View only, with the hint "Edit in Set up salary or Revise" |
| 19 | Log in as Finance | Can see the salary but cannot edit it |
| 20 | PT slab marked female exempt, female employee in that band | No PT deducted |
