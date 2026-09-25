# HRMS_Backend Duplicate Entities Analysis — 2026-09-11

## Executive Summary
Three pairs of duplicate entities exist in HRMS_Backend (branch main). Two entities per pair implement the same concept at different stages of evolution. This analysis traces each entity from database table to active controller endpoints to determine which is LIVE (reachable at runtime) and which is DEAD (unreachable).

---

## PAIR 1: LeaveRequest.java vs LeaveRequests.java

### Entity Table Names
- **LeaveRequest.java**: `@Table(name = "leave_request")` — line 82
- **LeaveRequests.java**: `@Table(name = "leave_requests")` — line 17

### Repository Analysis

| Entity | Repository | Ref | Type |
|--------|------------|-----|------|
| LeaveRequest | LeaveRequestRepository | D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\repository\LeaveRequestRepository.java:1-40 | JpaRepository for LeaveRequest |
| LeaveRequests | LeaveRequestRepo (leaverequest/) | D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\repository\leaverequest\LeaveRequestRepo.java:1-23 | JpaRepository for LeaveRequests |

### Service Chain

**LeaveRequest Path:**
- Service: `LeaveRequestService` (service/LeaveRequestService.java:67) — @Service (line 66)
- Constructor injection: `LeaveRequestRepository repository` (line 70)
- Methods: save(), getAll(), updateLeaveRequestStatus(), etc.

**LeaveRequests Path:**
- Interface: `LeaveRequestsService` (service/leaverequest/LeaveRequestsService.java:12)
- Implementation: `LeaveRequestServiceImpl` (serviceimpl/leaverequest/LeaveRequestServiceImpl.java:36) — @Service (line 35)
- Constructor injection: `LeaveRequestRepo leaveRequestRepo` (line 40)
- Methods: saveLeaveRequest(), getLeaveRequestsByEmployeeId(), updateLeaveRequestStatus(), etc.

### Controller Endpoints & LIVE/DEAD Verdict

**LeaveRequestController** (@RestController, line 27, no @RequestMapping):

| Endpoint | Method | Service/Repo Used | Entity | Status |
|----------|--------|-------------------|--------|--------|
| GET /leaves/all | leaveRequestsService.getAllLeaveRequest() | LeaveRequestsService | LeaveRequests | **LIVE** |
| GET /leaves/reporting-manager | leaveRequestsService.getLeaveRequestsByReportingManager() | LeaveRequestsService | LeaveRequests | **LIVE** |
| POST /leaves/add | leaveRequestsService.saveLeaveRequest() | LeaveRequestsService | LeaveRequests | **LIVE** |
| GET /leaves | leaveRequestsService.getLeaveRequestsByEmployeeId() | LeaveRequestsService | LeaveRequests | **LIVE** |
| GET /leaves/{id} | leaveRequestsService.getLeaveRequestById() | LeaveRequestsService | LeaveRequests | **LIVE** |
| PUT /leaves/{id}/status | leaveRequestsService.updateLeaveRequestStatus() | LeaveRequestsService | LeaveRequests | **LIVE** |
| PUT /leaves/{id} (line 288) | service.updateLeaveRequest() | LeaveRequestService | LeaveRequest | **LIVE** |
| DELETE /{id} (line 300) | service.deleteById() | LeaveRequestService | LeaveRequest | **LIVE** |

**VERDICT FOR PAIR 1:**
- **LeaveRequest**: **UNPROVEN** — Has @Service and repository, but endpoints are mostly commented out or overridden by newer ones. However, lines 288-304 show PUT and DELETE endpoints that are active and use LeaveRequestService.
- **LeaveRequests**: **LIVE** — Majority of active GET/POST/PUT endpoints use LeaveRequestsService with LeaveRequests entity.
- **Recommendation**: Both are technically live, but LeaveRequests is the primary flow (newer design). LeaveRequest appears to be legacy that hasn't been fully removed.

---

## PAIR 2: LeaveBalance.java vs EmployeeLeaveBalance.java

### Entity Table Names
- **LeaveBalance.java**: `@Table(name = "leave_balances")` — line 9
- **EmployeeLeaveBalance.java**: `@Table(name = "employee_leave_balances")` — line 8

### Repository Analysis

| Entity | Repository | Ref | Type |
|--------|------------|-----|------|
| LeaveBalance | LeaveBalanceRepository | D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\repository\LeaveBalanceRepository.java | JpaRepository for LeaveBalance |
| EmployeeLeaveBalance | EmployeeLeaveBalanceRepo | D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\repository\EmployeeLeaveBalanceRepo.java | JpaRepository for EmployeeLeaveBalance |

### Service Chain

**LeaveBalance Path:**
- Service: `LeaveBalanceService` (service/LeaveBalanceService.java:17) — @Service (line 16)
- Constructor injection: `LeaveBalanceRepository leaveBalanceRepository` (line 20)
- Methods: getOrCreateLeaveBalance(), getLeaveBalance(), getLeaveBalancesByEmployee(), etc.

**EmployeeLeaveBalance Path:**
- No direct service class; used indirectly by:
  - `LeaveRequestService` (service/LeaveRequestService.java:55-56)
  - `LeaveRequestServiceImpl` (serviceimpl/leaverequest/LeaveRequestServiceImpl.java:9, 39, 51)

### Controller Endpoints & LIVE/DEAD Verdict

**LeaveBalanceController** (@RestController @RequestMapping("/leave-balance"), line 14-16):

| Endpoint | Method | Entity | Status |
|----------|--------|--------|--------|
| GET /leave-balance/my-balance | LeaveBalanceService.getOrCreateLeaveBalance() | LeaveBalance | **LIVE** |
| GET /leave-balance/{empId}/{year} | LeaveBalanceService.getLeaveBalance() | LeaveBalance | **LIVE** |
| GET /leave-balance/employee/{empId} | LeaveBalanceService.getLeaveBalancesByEmployee() | LeaveBalance | **LIVE** |
| GET /leave-balance/search | LeaveBalanceService.searchLeaveBalances() | LeaveBalance | **LIVE** |

**EmployeeLeaveBalance Usage:**
- LeaveRequestService.save() (line 120): Checks balance via `employeeLeaveBalanceRepo.findByEmployeeIdAndLeaveTypeId()`
- LeaveRequestServiceImpl.updateLeaveRequestStatus() (lines 430-464): Creates/updates EmployeeMonthlyLop from approved leave requests
- IntegrateWithPayroll.java (lines 143-149): **COMMENTED OUT** — original code checked EmployeeLeaveBalance, current code uses EmployeeMonthlyLop instead
- No direct @RestController endpoint exposes EmployeeLeaveBalance

**VERDICT FOR PAIR 2:**
- **LeaveBalance**: **LIVE** — Has dedicated controller (LeaveBalanceController) with 4 active endpoints
- **EmployeeLeaveBalance**: **DEAD** (from controller perspective) — No direct controller endpoints; used only as internal data structure for leave request processing. Referenced in commented-out code in IntegrateWithPayroll.

---

## PAIR 3: Timesheet.java vs entity/timesheet/Timesheets.java

### Entity Table Names
- **Timesheet.java**: `@Table(name = "timesheet")` — line 17
- **Timesheets.java**: `@Table(name = "timesheets")` — line 16

### Repository Analysis

| Entity | Repository | Ref | Type |
|--------|------------|-----|------|
| Timesheet | TimesheetRepo | D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\repository\TimesheetRepo.java | JpaRepository for Timesheet |
| Timesheets | TimesheetsRepo | D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\repository\timesheet\TimesheetsRepo.java | JpaRepository for Timesheets |

### Service Chain

**Timesheet Path:**
- Service: `TimesheetService` (service/TimesheetService.java:30) — @Service (line 29)
- Constructor injection: `TimesheetRepo timesheetRepo` (line 32)
- Methods: createTimesheet(), updateTimesheet(), getTimesheetById(), etc.
- Note: Uses Timesheet entity throughout, returns TimesheetDTO

**Timesheets Path:**
- Interface: `TimesheetService` (service/timesheet/TimesheetService.java:17) — interface only
- Implementation: `TimesheetServiceImpl` (serviceimpl/timeshhet/TimesheetServiceImpl.java:42) — @Service (line 41)
- Constructor injection: `TimesheetsRepo timesheetRepo` (line 44)
- Methods: saveTimesheet(), getTimesheetById(), updateTimesheet(), etc.
- Note: Uses Timesheets entity throughout, returns TimesheetDto

### Controller Endpoints & LIVE/DEAD Verdict

**TimesheetController** (@RestController, no @RequestMapping, line 32-33):

| Endpoint | Method | Service Used | Entity | Status |
|----------|--------|---------------|--------|--------|
| POST /timesheet | timesheetService.createTimesheet() | TimesheetService (service/) | Timesheet | **LIVE** |
| GET /timesheet/get-initial-data | timesheetService.getInitialData() | TimesheetService (service/) | Timesheet | **LIVE** |
| GET /timesheet/{timesheetId} | timesheetService.getTimesheetById() | TimesheetService (service/) | Timesheet | **LIVE** |
| PUT /timesheet/{timesheetId} | timesheetService.updateTimesheet() | TimesheetService (service/) | Timesheet | **LIVE** |
| GET /timesheet/employee | timesheetService.getTimesheetsByEmpId() | TimesheetService (service/) | Timesheet | **LIVE** |
| PUT /timesheet/submit-all | timesheetService.findByEmployeeIdAndStatus() | TimesheetService (service/) | Timesheet | **LIVE** |
| DELETE /timesheet/{id} | timesheetService.deleteTimesheetById() | TimesheetService (service/) | Timesheet | **LIVE** |
| PUT /timesheets/{timesheetId}/status | timesheetService.updateStatus() | TimesheetService (service/) | Timesheet | **LIVE** |

**TimesheetsController** (@RestController @RequestMapping("/api/timesheets"), line 38-40):

| Endpoint | Method | Service Used | Entity | Status |
|----------|--------|---------------|--------|--------|
| (Multiple endpoints observed in service) | TimesheetServiceImpl | TimesheetService (timesheet/) | Timesheets | **LIVE** |

**VERDICT FOR PAIR 3:**
- **Timesheet**: **LIVE** — Active endpoints in TimesheetController (service package)
- **Timesheets**: **LIVE** — Active endpoints in TimesheetsController (service/timesheet package)
- **Conflict**: Both are simultaneously active with different controllers and services. This is a genuine architectural duplication that needs refactoring.

---

## Specific Questions — Answered

### Q: Does EmployeeMonthlyLop read from the live leave entity or the dead one?

**Answer: LIVE** (LeaveRequests)

**Evidence:**
- LeaveRequestServiceImpl.java, lines 430-464 shows the LOP calculation flow:
  ```java
  lop.setLeaveRequestId(request.getId());  // request is LeaveRequests (from LeaveRequestRepo)
  lop.setLeaveTypeId(leaveTypeId);
  lop.setLopDays(monthLopDays);
  lop.setPayrollProcessed(false);
  employeeMonthlyLopRepository.save(lop);
  ```
- The `request` object is fetched from `leaveRequestRepo` which is a `LeaveRequestRepo` for `LeaveRequests` entity
- Therefore, EmployeeMonthlyLop.leaveRequestId stores primary keys from the LeaveRequests table, not LeaveRequest

### Q: Is controller/IntegrateWithPayroll.java reachable (i.e. is it a real @RestController with a mapping) and which leave entity does its data path use?

**Answer: YES, REACHABLE** (uses LeaveRequests)

**Evidence:**
- File: `D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\controller\IntegrateWithPayroll.java`
- Line 28: `@RestController`
- Line 29: `@RequestMapping("/public")`
- Line 39: `private LeaveRequestRepo leaveRequestsRepository;` — injected repo is for LeaveRequests
- Line 64: `@PostMapping("/get-employee-leaves")` — active endpoint
- Line 122: `List<LeaveRequests> leaves = leaveRequestsRepository.findByEmployeeId(empId);`
- Current data path uses **LeaveRequests** entity (not the commented-out LeaveBalance code at lines 143-149)

---

## Summary Table

| Pair | Entity A | @Table | Status | Entity B | @Table | Status | Recommendation |
|------|----------|--------|--------|----------|--------|--------|-----------------|
| 1 | LeaveRequest | leave_request | UNPROVEN | LeaveRequests | leave_requests | LIVE | Consolidate to LeaveRequests; audit LeaveRequest usage |
| 2 | LeaveBalance | leave_balances | LIVE | EmployeeLeaveBalance | employee_leave_balances | DEAD | Keep LeaveBalance; EmployeeLeaveBalance is internal-only |
| 3 | Timesheet | timesheet | LIVE | Timesheets | timesheets | LIVE | **CRITICAL**: Refactor — both are active! Choose one pattern |

---

## Related Code Locations

### File References
- Entity files: `D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\entity\`
- Repository files: `D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\repository\`
- Service files: `D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\service\`
- Controller files: `D:\Infinevoclouds\HRMS_Backend\src\main\java\com\phegondev\usersmanagementsystem\controller\`

### Key Controllers
- LeaveRequestController: No explicit @RequestMapping (root-level endpoints)
- LeaveBalanceController: @RequestMapping("/leave-balance")
- TimesheetController: No explicit @RequestMapping (root-level endpoints)
- TimesheetsController: @RequestMapping("/api/timesheets")
- IntegrateWithPayroll: @RequestMapping("/public")

---

## Data Schema Notes (DB_SCHEMA.md compatibility)
- Pair 1 creates two separate tables: `leave_request` and `leave_requests` (singular vs plural naming)
- Pair 2 creates two separate tables: `leave_balances` and `employee_leave_balances` (scope naming)
- Pair 3 creates two separate tables: `timesheet` and `timesheets` (singular vs plural naming)
- All six tables are confirmed as real entity mappings (not aliases or views)
