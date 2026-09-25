# W-18 Loss-of-Pay and Working-Day Policy — Evidence

| # | Question | Answer | Evidence |
|---|---|---|---|
| 1 | Per-day pay divisor | Calendar days (not working days, not 30) | legacy/Payroll-Bend-SBoot/serviceimpl/payruns/EmployeePayRunServiceImpl.java:1165 |
| 2 | Working-day basis fields exist? | Yes, but NOT read | AttendancePreference.java:28–34; no code in payrun logic |
| 3 | LOP applied to pay | Deduction formula: `(perDayPay) * (lopDays)` | legacy/Payroll-Bend-SBoot/serviceimpl/payruns/EmployeePayRunServiceImpl.java:1170 |
| 4 | Policy stamped on pay figure | No — no policy or basis recorded | EmployeePayRun.java stores only numeric fields |
| 5 | PaySchedule carries working-day basis | Yes, field exists but unused | legacy/Payroll-Bend-SBoot/entity/PaySchedule.java:40–50 |

---

## 1. Per-Day Pay Divisor: Calendar Days

**Finding**: The daily pay rate is calculated by dividing monthly salary by the total *calendar* days in the period, not working days or a fixed 30.

Calculation at `EmployeePayRunServiceImpl.java:1129–1165`:
- Line 1129–1130: `totalPeriodDays = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1` — this includes all calendar days
- Line 1165: `perDayPay = monthlySalary / totalPeriodDays` — divisor is calendar days only

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/EmployeePayRunServiceImpl.java:1129–1165`

---

## 2. Working-Day-Basis and Attendance-Preference Fields

**Finding**: Two entities carry working-day fields that exist but are ignored by pay run logic.

### AttendancePreference (Payroll)
Table: `attendance_preferences`
Columns:
- `canIncludeWeekendsForPay` (boolean, line 33–34)
- `canIncludeHolidaysForPay` (boolean, line 27–28)
- `canIncludeLeavesForPay` (boolean, line 30–31)

**Status**: Defined in entity but never read during pay calculation. No reference in `EmployeePayRunServiceImpl`.

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leaveAndAttedance/attendance/AttendancePreference.java:27–34`

### PaySchedule (Payroll)
Columns:
- `workingDays` (List<String>, line 36–38) — collection of working days
- `noOfWorkingDays` (Integer, line 40–41) — count of working days
- `workingDaysCalculationType` (String, line 49–50)

**Status**: Fields exist; never read by `EmployeePayRunServiceImpl` or `PayRunServiceImpl`.

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/PaySchedule.java:36–50`

---

## 3. How LOP Is Applied to Pay

**Finding**: LOP is a simple per-day deduction calculated as `(daily_rate) × (lop_days)`, subtracted from the prorated net pay.

Code flow in `EmployeePayRunServiceImpl.java:1154–1177`:
1. Line 1165: `perDayPay = monthlySalary / totalPeriodDays`
2. Line 1170: `lopAmount = perDayPay * leaves` — LOP deduction in salary currency
3. Line 1174–1175: Prorated net pay is computed: `perDayNetPay = originalNetPay / totalPeriodDays; proratedNetPay = perDayNetPay * eligibleDays`
4. Line 1177: `finalNetPay = proratedNetPay - lopAmount` — net pay after LOP

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/EmployeePayRunServiceImpl.java:1154–1177`

---

## 4. Is Policy or Basis Stamped on Pay Figure?

**Finding**: No. The `EmployeePayRun` table carries only numeric results; no metadata about the policy, divisor basis, or working-day preference.

**Columns in EmployeePayRun**:
- `monthlySalary` (DECIMAL)
- `paidDays` (DECIMAL) — number, no basis code
- `lop` (DECIMAL) — amount only
- `netPay` (DECIMAL)
- `paid_days` (DECIMAL) — adjusted for LOP; no context

Nothing in the entity references policy (e.g., no `policy_id`, no `working_day_basis`, no `divisor_type`).

When a disputed payslip is queried later, only the numeric values exist; the derivation basis cannot be explained.

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/payruns/EmployeePayRun.java` — no policy columns

---

## 5. Pay Schedule Working-Day Basis

**Finding**: `PaySchedule.java` carries working-day configuration fields but does NOT influence pay calculation.

**Columns**:
- Line 36–38: `workingDays` — List of working day names
- Line 40–41: `noOfWorkingDays` — explicit count per period
- Line 49–50: `workingDaysCalculationType` — configuration for how to count

**Usage**: These fields are defined but unreferenced in `EmployeePayRunServiceImpl` or `PayRunServiceImpl`. Pay runs ignore them and use calendar day count exclusively.

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/PaySchedule.java:36–50`

---

## Summary

- **Divisor**: Calendar days, hardcoded in calculation logic.
- **Policy fields exist**: Yes (AttendancePreference, PaySchedule), but never read.
- **LOP formula**: Straightforward per-day multiplier.
- **Stamp**: No policy or context is persisted on the pay figure.
- **Implication**: W-18 must implement per-tenant policy selection, apply it at the moment of pay calculation, and record the chosen basis on every pay figure.
