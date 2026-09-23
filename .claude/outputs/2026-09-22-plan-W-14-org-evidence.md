# W-14 Org Structure & Hierarchy — Evidence

## Summary

| Topic | Finding | Where |
|---|---|---|
| Payroll org entities (status) | Department, Designation, WorkLocation fully structured with IDs and org FK | `Payroll-Bend-SBoot/.../organization/*.java` |
| HRMS org (status) | Department stored as free text String in Work entity; no entity for it | `HRMS_Backend/.../entity/Work.java:18` |
| HRMS hierarchy entity | Report entity with reportingManagerId (String) + 3 approval levels | `HRMS_Backend/.../entity/Report.java:1-105` |
| Manager validation | No validation found: reportingManagerId accepted as-is in DTO without employee lookup | `HRMS_Backend/.../controller/EmployeeController.java:403-419` |
| Hierarchy traversal code | Exists: `getEmployeeIdsByReportingManager()` queries by manager ID via employee Report table | `HRMS_Backend/.../serviceimpl/leaverequest/LeaveRequestServiceImpl.java:807` |

---

## Part 1: Payroll Master Entities (Being Adopted)

**Department** — `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/Department.java`

- `id` (Long, PK) — line 11
- `departmentId` (String, unique, not null) — line 13–14
- `name` (String, not null) — line 16–17
- `description` (String) — line 19–20
- `departmentCode` (String) — line 22–23
- `organizationId` (FK to Organization, not null, LAZY) — line 25–27
- `status` (Boolean, default TRUE) — line 30–31

**Designation** — `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/Designation.java`

- `id` (Long, PK) — line 11
- `designationId` (String, unique, not null) — line 13–14
- `name` (String, not null) — line 16–17
- `organizationId` (FK to Organization, not null, LAZY) — line 19–21
- `status` (Boolean, default TRUE) — line 25–26

**WorkLocation** — `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/WorkLocation.java`

- `id` (Long, PK) — line 10–11
- `workLocationId` (String, unique, not null) — line 13–14
- `workLocationName` (String, not null) — line 16–17
- `streetAddress1`, `streetAddress2` (String) — line 19–23
- `city`, `state`, `zipCode`, `country` (String) — line 25–35
- `isFilingAddress` (Boolean) — line 37–38
- `organizationId` (FK to Organization, not null, LAZY) — line 40–42
- `status` (Boolean, default TRUE) — line 45–46

---

## Part 2: HRMS Department & Designation as Free Text

HRMS stores department and designation **as free text strings** in the `Work` entity, not as references to structured master data.

**Work Entity** — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Work.java`

- `id` (Long, PK) — line 14–15
- `department` (String, not blank) — line 17–18. **Type: String. No enum, no FK. Validated as not-blank only.**
- `jobTitle` (String, not blank) — line 20–21. **Acts as designation; also free text String.**
- `payGrade` (String, nullable) — line 23
- `doj` (Date, not null) — line 25–26
- `terminationDate` (Date, nullable) — line 28
- `workstationId` (String, nullable) — line 30
- `timeZone` (String, not blank) — line 32–33
- `shiftStartTime`, `shiftEndTime` (String) — line 35–36
- `employee` (OneToOne back-ref) — line 38–40

**No seed data, enum, or dropdown constraints found in code for department or designation. Conversion challenge:** Free-text values in existing HRMS employees (potentially hundreds of distinct strings) must be mapped to Payroll master IDs during migration. No clean inverse mapping rule exists in the codebase.

---

## Part 3: HRMS Report Entity (Hierarchy Structure)

The `Report` entity holds the manager chain and approval levels per employee.

**Report Entity** — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Report.java`

- `id` (Long, PK) — line 12–13
- `reportingManagerId` (String) — line 15–16. **Not validated against Employee table.**
- `reportingManagerName` (String) — line 18–19
- `indirectManager` (String) — line 21
- `firstLevelApprover` (String) — line 22
- `secondLevelApprover` (String) — line 23
- `thirdLevelApprover` (String) — line 24
- `note` (String, max 2000) — line 26–27
- `employee` (OneToOne back-ref, maps `report` column in Employee table) — line 29–31

**All ID/name fields are String type. None are constrained to valid employees by FK or validation.**

---

## Part 4: Hierarchy Traversal & Manager Lookup

Hierarchy traversal code **exists** and is used by the Leave approval workflow.

**Method:** `LeaveRequestServiceImpl.getLeaveRequestsByReportingManager()` — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/serviceimpl/leaverequest/LeaveRequestServiceImpl.java:803–844`

- Line 807: Calls `employeeService.getEmployeeIdsByReportingManager(reportingManagerEmployeeId)`
- Line 803–805: Logs "Fetching leave requests for manager ID"
- Line 806: Step comment "Get employees reporting to this manager"
- Line 811–813: Returns empty list if no subordinates found

**Method:** `EmployeeService.getEmployeeIdsByReportingManager()` — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/service/EmployeeService.java:202–204`

- Queries `employeeRepository.findByReport_ReportingManagerId(reportingManagerId)`
- **One level only.** Does not recursively traverse a manager-of-manager chain; only direct reports.

**Cycle Detection:** No cycle-detection code found in the frozen system.

---

## Part 5: Validation that Reporting Manager is a Valid Employee

**No validation found.**

Evidence from Employee update path:

**EmployeeController.updateEmployee()** — `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/EmployeeController.java:403–421`

- Lines 407–418: Directly assigns DTO fields to Report entity without lookup or validation:
  - `existingEmployee.getReport().setReportingManagerId(employeeDetails.getReport().getReportingManagerId())`
  - `setReportingManagerName(...)`, `setIndirectManager(...)`, `setFirstLevelApprover(...)`, etc.
- No call to `EmployeeRepository.findByEmpId()` or any employee existence check.
- The reportingManagerId field is accepted as-is from the client DTO.

**Consequence:** A reporting manager ID can be any arbitrary string; it need not correspond to an actual employee record. Leave approval still queries by this ID, and if no employee exists with that Report.reportingManagerId, the query returns an empty list (LeaveRequestServiceImpl line 811–813).
