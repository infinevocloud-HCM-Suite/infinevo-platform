# W-16 Leave Engine — Evidence from Frozen System

**Date:** 2026-09-22 · **Frozen snapshot:** HRMS-Backend@d984c64, HRMS-Frontend@c72116c, Payroll-Bend-SBoot@39b37d6, Payroll-Fend-react@053ca62

| Topic | Finding | Evidence |
|---|---|---|
| **HRMS Request Entity** | Dual-entity design: `LeaveRequest.java` (legacy) + `LeaveRequests.java` (active). Tables: `leave_request` + `leave_requests`. New code uses `LeaveRequests` only. | `LeaveRequests.java:17` (entity), `DB_SCHEMA.md:198-211` (both tables exist) |
| **HRMS Request Columns** | `id`, `employee_id`, `employee_name`, `from_date`, `to_date`, `is_half_day`, `half_day_period` ("first"/"second"), `reporting_manager_status`, `hr_status`, `total_days`, `selected_dates` (Set), `lop_generated`, `lop_allocation` (Map). Manual override: `manual_days_allocation` (Map<leaveTypeId, days>). | `LeaveRequests.java:20-113` |
| **HRMS Approval Workflow** | Two-stage: Reporting Manager → HR. Roles: "reporting manager" / "reporting_manager" / "reporting-manager" (normalized), and "hr". Role acts only if prior stage is PENDING. Hard-coded in controller logic, no config table. RM can approve when RM status = PENDING (line 196); HR can approve only after RM decides and HR status = PENDING (lines 200-204). | `LeaveRequestController.java:155-222` |
| **HRMS Leave Types** | Entity: `LeaveType.java`. Columns: `id`, `name`, `default_days` (double), `leave_carried_forward` (boolean), `start_date`, `end_date`, `apply_to_all_employees`. No paid/unpaid flag; no accrual config. | `LeaveType.java:14-57` (HRMS) |
| **HRMS Balance Entities** | Two parallel entities: `LeaveBalance.java` (line 9) stores yearly totals per type (e.g., `annual_allotted=24`, `annual_used=0`). `EmployeeLeaveBalance.java` (line 8) stores per-employee, per-type balances: `employee_id`, `leave_type`, `leave_type_id`, `remaining_days` (Float). No consumption breakdown; total remaining computed at read time. | `LeaveBalance.java:9-72`, `EmployeeLeaveBalance.java:8-97` |
| **Payroll Allocation Entity** | `EmployeeLeaveAllocation.java:9`. Unique on (org_id, employee_id, leave_type, year). Columns: `annual_days` (Int), `carried_forward_days` (Int, default 0), `consumed_days` (Int, default 0), `expiration_date`, `carry_forward` (Boolean), `monthly_lop_breakdown` (JSON), `monthly_lwp_breakdown` (JSON), `lop_days` (Int, default 0). | `EmployeeLeaveAllocation.java:8-228` |
| **Payroll Consumption Entity** | `EmployeeLeaveBalanceConsumption.java:8`. Tracks each consumption event. Columns: `leave_id` (unique), `allocation_id` (FK), `consumed_days`, `balance_after`, `running_ytd`, `balance_days`, `lop_days`, `lwp`, `monthly_breakdown` (JSON), `monthly_lop_breakdown` (JSON), `monthly_entries` (JSON). | `EmployeeLeaveBalanceConsumption.java:8-254` |
| **Payroll LeaveType** | `LeaveType.java:14` (Payroll). Extensive config: `name`, `code`, `type` (paid/unpaid), `unit` (day_based), `allow_half_day`, `validity_from`/`validity_to`, eligibility (genders, departments, designations, work locations), accrual (enabled, frequency, units), reset (enabled, frequency), carry_forward (enabled, units), encashment (enabled, units), past/future booking (limits), include_weekend/holiday, exceed_balance policy, pro-rate, max_leave_per_application. | `LeaveType.java:1-169` (Payroll), DB_SCHEMA.md:771-781 |
| **LOP Derivation** | Payroll: LOP = max(0, consumed_days - annual_days - carried_forward_days). Calculated per month in `recalculateConsumptionBalances()`: `if runningYtd > totalAlloc then lop = runningYtd - totalAlloc` (line 698). LWP set equal to LOP if LOP > 0 (line 824). HRMS: `EmployeeMonthlyLop.java:9` records LOP **after** leave approval; LOP populated per month-year pair. | `EmployeeLeaveAllocationServiceImpl.java:666-718`, `EmployeeMonthlyLop.java:9-114` |
| **Monthly LOP (HRMS)** | Table: `employee_monthly_lop` (line 9). Columns: `employee_id`, `leave_request_id`, `leave_type_id`, `leave_type_name`, `year`, `month`, `lop_days` (Float), `payroll_processed` (Boolean). Populated when leave request approved; read by Payroll at pay run via REST API (line 1091). | `EmployeeMonthlyLop.java:9-114`, `EmployeePayRunServiceImpl.java:1091` |
| **Bulk Import** | Payroll import table: `employee_leave_import` (line 8). Columns: `count`, `leave_date`, `employee_number`, `leave_type`, `organization_id` (FK). Controller `POST /api/employee-leave-imports` (line 14), `/api/employee-leave-imports/imports` bulk (line 97). No CSV parsing visible; DTO-to-entity mapping only (ServiceImpl lines 24-102). | `EmployeeLeaveImport.java:8-78`, `EmployeeLeaveImportController.java:1-111`, `EmployeeLeaveImportServiceImpl.java:1-103` |
| **HRMS→Payroll Bridge** | HRMS LOP queried via `IntegrateWithHrmsService.fetchLeaves(List<email>, String period)` (line 1091). Returns `Map<String, Double>` (email → LOP days). Called during pay run generation. Per-day deduction: `perDayPay * lop_days` (line 1170). | `EmployeePayRunServiceImpl.java:1091, 1170` |

---

## 1. HRMS Side — Request & Approval

**Main entity:** `LeaveRequests` (active) at `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/LeaveRequests.java:17`.

**Columns:**
- `id` (PK), `employee_id`, `employee_name`, `from_date`, `to_date` (LocalDate)
- `is_half_day` (boolean), `half_day_period` ("first" or "second")
- `reporting_manager_status` (LeaveRequestStatus enum), `hr_status` (LeaveRequestStatus enum) — both default PENDING
- `total_days` (double), `reason` (TEXT)
- `manual_days_allocation` (Map<Long, Float>, leave_type_id → days override)
- `selected_dates` (Set<LocalDate>), `lop_allocation` (Map<Long, Float>)
- `leave_balance_deducted` (Boolean), `leave_balance_recredited` (Boolean)
- Timestamps: `created_date`, `reporting_manager_updated_at`, `hr_updated_at`
- Comments: `reporting_manager_comment`, `hr_comment` (1000 chars each)

**Two-stage approval flow:**

1. **Reporting Manager stage** (lines 195-198 of `LeaveRequestController.java`):
   - User must have role "reporting manager" / "reporting_manager" / "reporting-manager" (normalized check, line 185-187)
   - Can approve only if `reportingManagerStatus == PENDING`
   - Sets `actingRole = "reporting manager"` to signal RM approval path

2. **HR stage** (lines 200-204):
   - User must have role "hr"
   - Can approve only if `reportingManagerStatus != PENDING` (RM has decided) AND `hrStatus == PENDING`
   - Sets `actingRole = "hr"` to signal HR approval path

3. **Status update** (line 217): `leaveRequestsService.updateLeaveRequestStatus(id, newStatus, actingRole, comment)`
   - `newStatus` is `LeaveRequestStatus` enum (APPROVED, REJECTED, PENDING)
   - Endpoint: `PUT /leaves/{id}/status` with query params `status` and optional `comment`
   - No approval hierarchy stored; roles checked at request time only

**Leave-type entity** (HRMS): `LeaveType.java:14` (table: `leave_types`)
- Columns: `id`, `name`, `default_days` (double), `leave_carried_forward` (boolean), `start_date`, `end_date`, `apply_to_all_employees`, `employee_ids` (Set<String>)
- No accrual, encashment, or policy config — basic type registration only

**Balance entities** (HRMS):

1. **`LeaveBalance.java:9`** (table: `leave_balances`): Per-year, per-type totals
   - `employee_id`, `year`, `annual_allotted` (default 24), `annual_used`, `sick_allotted` (default 12), `sick_used`, `casual_allotted` (default 8), `casual_used`, `maternity_allotted` (default 90), `maternity_used`, `paternity_allotted` (default 10), `paternity_used`
   - Computed fields: `getAnnualRemaining()` = `allotted - used` (line 53-71)

2. **`EmployeeLeaveBalance.java:8`** (table: `employee_leave_balances`): Per-employee, per-type, current balance
   - `employee_id`, `leave_type`, `leave_type_id`, `remaining_days` (Float)
   - Single record per employee-type; total remaining only, no monthly breakdown

---

## 2. Payroll Side — Allocation & Consumption

**Allocation entity:** `EmployeeLeaveAllocation.java:9` (table: `employee_leave_allocation`)

Unique constraint on (organization_id, employee_id, leave_type, year) (lines 10-14).

**Columns:**
- `id` (PK), `organization_id`, `employee_id`, `leave_type` (string), `year` (string, not INT)
- `annual_days` (Int, NOT NULL), `carried_forward_days` (Int, default 0)
- `consumed_days` (Int, default 0), `expiration_date` (LocalDate)
- `carry_forward` (Boolean, default false)
- `monthly_lop_breakdown` (JSON), `monthly_lwp_breakdown` (JSON), `monthly_breakdown` (JSON)
- `lwp` (Int), `lop_days` (Int, default 0)
- Timestamps: `created_by`, `created_at`, `updated_at`

**Consumption entity:** `EmployeeLeaveBalanceConsumption.java:8` (table: `employee_leave_balance_consumption`)

Tracks individual consumption events (one row per leave taken, per month-year or per employee-type-year).

**Columns:**
- `id` (PK), `leave_id` (unique string), `allocation_id` (FK to allocation)
- `organization_id`, `employee_id`, `leave_type`, `year`
- `consumed_days` (Int, NOT NULL), `balance_days` (Int), `balance_after` (Int), `running_ytd` (Int)
- `reason` (500 chars), `leave_month` (string, e.g., "January")
- `monthly_breakdown` (JSON), `monthly_lop_breakdown` (JSON), `monthly_lwp_breakdown` (JSON), `monthly_entries` (JSON)
- `lwp` (Int, default 0), `lop_days` (Int, default 0)
- Timestamps: `created_by`, `created_at`, `updated_at`

**Balance recalculation:** `EmployeeLeaveAllocationServiceImpl.recalculateConsumptionBalances()` (line 666) re-derives balances after any allocation/consumption change:
- Iterates consumption rows for `(organizationId, employeeId, year)`
- For each row matching leave_type: `runningYtd += consumed_days`
- **LOP calculation:** `if runningYtd > totalAlloc then lop_days = runningYtd - totalAlloc; else lop_days = 0` (line 698)
- Sets `balance_after = max(0, totalAlloc - runningYtd)` (line 696)
- Saves updated row (line 712)

At read time (line 813-826): If `consumed <= total then lop = lwp = 0`; else `lop = consumed - total` and `lwp = lop` (lines 818, 824).

**Payroll LeaveType config:** `LeaveType.java:14` (Payroll, table: `leave_type`)

Extensive policy matrix per organization:
- Basic: `name`, `code`, `type` (paid/unpaid), `unit` (day_based/hour_based), `status`, `allow_half_day`, `validity_from/to`, `description`
- Eligibility: `genders` (Set), `departments` (Many-to-Many), `designations` (Many-to-Many), `work_locations` (Many-to-Many)
- Accrual (lines 89-96): `accrual_is_enabled`, `accrual_frequency`, `accrual_units` (BigDecimal), `accrual_regular_hours`
- Reset (lines 102-106): `reset_is_enabled`, `reset_frequency`
- Carry-forward (lines 109-113): `carry_forward_is_enabled`, `carry_forward_units`
- Encashment (lines 116-120): `encashment_is_enabled`, `encashment_units`
- Past/future booking (lines 123-133): `past_booking_is_enabled`, `past_booking_limit_days`, `future_booking_is_enabled`, `future_booking_limit_days`
- Inclusion rules (lines 136-146): `include_weekend_is_enabled`, `include_weekend_min_days`, `include_holiday_is_enabled`, `include_holiday_min_days`
- Overrun (lines 149-153): `exceed_balance_is_enabled`, `exceed_balance_mode`
- Misc: `effective_after_period`, `effective_after_units`, `pro_rate_is_enabled`, `max_leave_per_application`

---

## 3. The Join Risk — Where Models Disagree

| Aspect | HRMS | Payroll | Risk |
|---|---|---|---|
| **Units** | Days (double, supports half-days via `is_half_day` + `half_day_period` flags) | Days (Integer in allocation, Float in LOP). No half-day concept in consumption entity. | 0.5 day in HRMS rounds how in Payroll? Payroll LOP calc uses `int`, loses precision. |
| **Allocation vs. Accrual** | Allocation: manual entry into `LeaveBalance` fields (hardcoded defaults: annual=24, sick=12, casual=8, maternity=90, paternity=10). No accrual engine. | Allocation: per-type config (accrual_enabled, accrual_frequency, accrual_units). Accrual can be monthly, quarterly, annual. | Two completely different models. HRMS = static yearly bucket. Payroll = month-by-month accrual. Payroll's imported allocations (via CSV) are snapshot; they don't accrue. |
| **Carry-forward** | LeaveType has `leave_carried_forward` (boolean) only. No cap on forward days, no expiry. | LeaveType has `carry_forward_is_enabled` + `carry_forward_units` (max days to carry). EmployeeLeaveAllocation has `carried_forward_days` (Int), `carry_forward` (Boolean), `expiration_date`. | HRMS has no way to model "carry max 5 days" or expiry. Payroll's expiration_date never enforced in code (DEBT-?). |
| **Encashment** | Not supported. | Payroll LeaveType has `encashment_is_enabled` + `encashment_units` (days convertible to cash). No implementation visible in service code. | Payroll supports policy; never exercised. HRMS has no parallel. |
| **Pro-rating on joining/leaving** | EmployeePayRunServiceImpl (line 1132-1151): calculates `paidDays` as days from DOJ to end of pay period, caps eligible days. No leave pro-rating. | LeaveType has `pro_rate_is_enabled` flag (no implementation visible). No DOJ adjustment in allocation import. | Payroll can pro-rate salary but not leaves. HRMS pro-rates salary only. |
| **Year boundary** | Calendar year (hardcoded defaults). LeaveBalance.year = Year.now().getValue(). | Configurable per org; stored as String in `employee_leave_allocation.year`. No financial-year vs. calendar-year distinction in code. | Misalignment if org uses Apr-Mar financial year. HRMS locks to calendar Jan-Dec. |
| **Paid vs. Unpaid** | LeaveType has no `is_paid` flag in HRMS schema (DB_SCHEMA.md:196 says "is_paid" but entity does not have it). | LeaveType.type ∈ {paid, unpaid}. LOP deduction only applies to unpaid leaves (theory; code doesn't check). | Payroll can distinguish; HRMS treats all as consumed. All LOP treated same when returned to pay run. |

**Cross-system LOP flow:**
- HRMS approves leave → generates `EmployeeMonthlyLop` record
- Payroll REST call fetches LOP from HRMS at pay run time (line 1091)
- Payroll **also** has `employee_leave_balance_consumption.lop_days` (calculated internally)
- **Which one is authoritative at pay run?** Code shows HRMS fetch (line 1091); consumption LOP unused in pay run (gap?). Per FEATURE_MAP line 346: "Pay run reads `employee_leave_balance_consumption` for LOP days, replacing the earlier HRMS `fetchLeaves` path." — **contradicts code audit.**

---

## 4. Loss of Pay (LOP) — Current Derivation

**HRMS:** `EmployeeMonthlyLop.java:9` (table: `employee_monthly_lop`)

- Populated when `LeaveRequests` approved (logic in service, not visible in excerpt; LOP flags in entity line 106-113)
- Columns: `employee_id`, `leave_request_id`, `leave_type_id`, `leave_type_name`, `year`, `month`, `lop_days` (Float), `payroll_processed` (Boolean, default false)
- Queried by Payroll via `IntegrateWithHrmsService.fetchLeaves()` (line 1091)

**Payroll:** `employee_leave_balance_consumption.lop_days` (Integer)

- Derived after each allocation/consumption update: `recalculateConsumptionBalances()` (line 666)
- Formula (line 698): `lop = (consumed_days > total_allocation) ? (consumed_days - total_allocation) : 0`
- Also stored in `employee_leave_allocation.lop_days` (summary, line 60)
- At pay run: `double lopAmount = perDayPay * lop_days` (line 1170)
- Deducted from net pay: `finalNetPay = proratedNetPay - lopAmount` (line 1177)

**Calculation differences:**
- HRMS LOP = accumulated over all approved requests for month-type
- Payroll LOP = accumulated consumption over annual allocation; recalculated on each change
- HRMS LOP is cumulative (tied to leave_request); Payroll LOP is comparative (against allocation)
- If HRMS LOP says 2 days in Jan, but allocation says 20 days and consumed only 10, Payroll recalc shows 0 LOP → mismatch

---

## 5. Bulk Import — File Format & Validation

**Payroll import:** `EmployeeLeaveImport.java:8` and controller `EmployeeLeaveImportController.java` (lines 14-111)

**Endpoint:** `POST /api/employee-leave-imports/imports` (line 97), accepts `List<EmployeeLeaveImportDTO>`

**DTO fields:**
- `count` (Integer), `date` (LocalDate), `employee_number` (String), `leave_type` (String), `organization_id` (FK)

**Service:** `EmployeeLeaveImportServiceImpl.saveAll()` (line 93)
- Receives `List<EmployeeLeaveImportDTO>`, converts to entities via mapper, saves all via repository
- **No CSV parsing** — input is already JSON/deserialized DTO
- **No validation**: no uniqueness check, no employee existence check, no leave-type validation visible
- **No partial rollback**: if one row fails, transaction rolls back all (line 93 `@Transactional`)
- **No import result tracking**: no success/failure count returned

**Frontend:** Payroll UI sends POST to `/api/leave-allocation` (item 26, FEATURE_MAP:342), not `/api/employee-leave-imports`. Those are separate flows.

---

## 6. GAP_INVENTORY — Leave-Related Findings

| ID | Component | Details | Evidence |
|---|---|---|---|
| **BUG-003** | Payroll / LOP | "Half-day LOP parsed as zero, causing overpayment." **Status: RESOLVED — verify before closing.** `IntegrateWithHrmsServiceImpl.java:33` now returns `Map<String, Double>`. Code audit shows HRMS Float fields (line 28 of `EmployeeMonthlyLop.java`), no parsing error. Old bug may be residual; consumption path unaffected. | GAP_INVENTORY.md:29 |
| **DEBT-013** | Both backends | Package/class name typo: `timeshhet/`, `leaveAndAttedance/` (sic), `EmployyePortalContoller.java` — breaks grep, signals low review rigour. `leaveAndAttedance/` visible in Payroll entity paths. | GAP_INVENTORY.md:51, `EmployeeLeaveImport.java:1` path contains typo |

No other BUG or DEBT entries reference leave specifically. No findings on balance carry-forward logic, LOP encashment, accrual validation, or pro-rating.

---

## Flow — HRMS Request to Payroll LOP Deduction

1. **Employee applies** → `POST /leaves/add` (multipart form), line 100 of controller
2. **Service saves** → `LeaveRequestsService.saveLeaveRequest()` — stores `LeaveRequests` entity
3. **Reporting Manager approves** → `PUT /leaves/{id}/status?status=APPROVED` by user with "reporting_manager" role (line 156-222)
4. **HR approves** → same endpoint, user with "hr" role, after RM approved (line 200-204)
5. **LOP generated** → `LeaveRequests.lop_generated = true`, `lop_allocation` map populated (flags line 106-113)
6. **EmployeeMonthlyLop record inserted** → month, year, employee_id, lop_days (Float) written to table (line 9-114)
7. **Pay run initiated** → `POST /payruns` in Payroll creates `PayRun` entity
8. **Generate employee pay runs** → `PayRunServiceImpl.createPayRun()` calls `employeeService.generateEmployeePayRuns()` (line 435 of PayRunServiceImpl)
9. **Fetch LOP from HRMS** → `EmployeePayRunServiceImpl.generateEmployeePayRuns()` (line 1003) calls `integrateWithHrmsService.fetchLeaves(employeeEmails, payPeriod)` (line 1091)
10. **Set LOP on employee pay run** → `e.setTotalNoOfLeaves(leaves)` (line 1125), then `double lopAmount = perDayPay * leaves` (line 1170)
11. **Deduct from net pay** → `finalNetPay = proratedNetPay - lopAmount` (line 1177)
12. **Save employee pay run** → `EmployeePayRun` record with `lop` field set, payment_status = YET_TO_PAY

| Step | Controller | Service | Line |
|---|---|---|---|
| Apply (HRMS) | `LeaveRequestController` | `LeaveRequestsService.saveLeaveRequest()` | controller:100, service:113 |
| Reporting Manager Approval (HRMS) | `LeaveRequestController` | `LeaveRequestsService.updateLeaveRequestStatus()` | controller:156-222 |
| HR Approval (HRMS) | `LeaveRequestController` | `LeaveRequestsService.updateLeaveRequestStatus()` | controller:156-222 |
| LOP Derivation (HRMS) | *(inline in service)* | `LeaveRequestServiceImpl` | *not visible in excerpt* |
| Pay Run Create (Payroll) | `PayRunController` | `PayRunServiceImpl.createPayRun()` | not shown |
| Generate Employee Pay Runs (Payroll) | `EmployeePayRunController` | `EmployeePayRunServiceImpl.generateEmployeePayRuns()` | ServiceImpl:1003 |
| Fetch LOP from HRMS (Payroll) | *(indirect via service call)* | `IntegrateWithHrmsServiceImpl.fetchLeaves()` | EmployeePayRunServiceImpl:1091 |

---

## Summary of Key Facts

1. **Dual-entity debt:** HRMS has `LeaveRequest` + `LeaveRequests`; both map to `leave_request` + `leave_requests` tables; only `LeaveRequests` actively used.
2. **Two-stage hard-coded approval:** RM → HR, no config, roles checked at request time only.
3. **Half-day support:** HRMS models half-day via `is_half_day` boolean + `half_day_period` string; Payroll has no half-day concept, uses Integer days only.
4. **Accrual mismatch:** HRMS = static annual bucket (24 annual, 12 sick, 8 casual); Payroll = per-type accrual config (enabled, frequency, units) but no accrual engine in code.
5. **Carry-forward:** HRMS flag only; Payroll has expiration_date field (never enforced).
6. **LOP authority conflict:** FEATURE_MAP says Payroll reads consumption LOP; code shows HRMS API fetch takes precedence.
7. **Import is DTO-only:** No CSV parsing, no validation, all-or-nothing rollback.
8. **typo in package name:** `leaveAndAttedance/` (missing 'n') — affects grep and search.

