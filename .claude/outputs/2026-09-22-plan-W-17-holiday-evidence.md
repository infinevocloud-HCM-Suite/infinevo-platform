# W-17 Holiday Calendar — Evidence Pass

| Component | HRMS | Payroll |
|---|---|---|
| **Entity class** | `entity/Holiday.java` | `entity/leaveAndAttedance/holiday/Holiday.java` |
| **Table name** | `holidays` | `holidays` |
| **Org-scoped** | ✗ No | ✓ Yes (FK `organizationId`) |
| **Location-scoped** | ✗ No | Partial: `Set<String>` join table, not FK |

---

## 1. Holiday Entities & Tables

### HRMS Holiday
- **Class**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Holiday.java:1-58` (legacy)
- **Table**: `holidays`
- **Columns**: `id` (PK), `name`, `date` (LocalDate), `day` (String — calculated day name)
- **Scoping**: None — no tenant column, no organization FK, no location reference

### Payroll Holiday
- **Class**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leaveAndAttedance/holiday/Holiday.java:1-130` (legacy)
- **Table**: `holidays`
- **Columns**: `id`, `holidayId` (public ID), `name`, `from_date`, `to_date`, `description`, `is_restricted_holiday` (boolean), `organizationId` (FK), `status` (boolean)
- **Locations storage**: `Set<String>` collection via `@ElementCollection` mapped to join table `holiday_locations` with string values, not FK to `WorkLocation` (line 39-45)

---

## 2. Work Location Scoping — Neither Reads per Location

**HRMS Holiday**: Zero location awareness. No location column, no location query.

**Payroll Holiday**: Stores locations as simple strings in a join table, not as foreign keys to `WorkLocation`. Per-**organization** only; locations are a denormalized Set without relational constraint.
- **Proof**: Line 35-37: `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "organizationId", nullable = false)`
- **Proof**: Line 39-45: `@ElementCollection @CollectionTable(name = "holiday_locations", joinColumns = @JoinColumn(name = "holiday_id")) @Column(name = "location") private Set<String> locations`

---

## 3. Payroll Work Location Entity

- **Class**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/WorkLocation.java:1-130` (legacy)
- **Table**: `workLocations`
- **Columns**: `id`, `workLocationId` (unique public ID), `workLocationName`, `streetAddress1`, `streetAddress2`, `city`, `state`, `zipCode`, `country`, `isFilingAddress` (Boolean), `organizationId` (FK to `organization`), `status` (Boolean)
- **Relation**: Each work location belongs to one organization (line 40-42)

---

## 4. Holiday Import Paths

**Found**: `EmployeeLeaveImportController` at `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/leaveAndAttendance/leaveImport/EmployeeLeaveImportController.java:1-112` (legacy)
- Handles bulk leave data import (`/api/employee-leave-imports` endpoints) and `/imports` POST for bulk upload
- **Does NOT import holidays** — imports `EmployeeLeaveImportDTO`, which is leave allocation/consumption data, not holiday definitions

**Not found**: No dedicated holiday bulk-import or CSV-import endpoint in either backend. Holiday creation is manual via CRUD endpoints only.

---

## 5. Who Reads Holidays — Not Found

No evidence that either backend reads holidays during:
- **Leave calculation**: No `HolidayService` / `HolidayRepository` reference in `EmployeeLeaveAllocationServiceImpl`, `EmployeeLeaveConsumptionServiceImpl`, or any leave service
- **Attendance tracking**: No reference in attendance services
- **Payroll working-days calculation**: No reference in `EmployeePayRunServiceImpl` or pay-run logic

Holiday services provide only CRUD operations (`getAllHolidays`, `getHoliday`, `createHoliday`, `updateHoliday`, `deleteHoliday`); they are read by controllers but not consulted by domain logic.

---

## Citations

| Claim | File:Line | Status |
|---|---|---|
| HRMS Holiday entity / class | `HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Holiday.java:1-58` | legacy |
| HRMS Holiday table name | `DB_SCHEMA.md:252-260` | legacy |
| Payroll Holiday entity / class | `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leaveAndAttedance/holiday/Holiday.java:1-130` | legacy |
| Payroll Holiday locations as Set<String> | `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leaveAndAttedance/holiday/Holiday.java:39-45` | legacy |
| Payroll Holiday per organization | `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/leaveAndAttedance/holiday/Holiday.java:35-37` | legacy |
| Payroll WorkLocation entity | `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/WorkLocation.java:1-130` | legacy |
| Payroll WorkLocation FK to org | `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/WorkLocation.java:40-42` | legacy |
| Leave import controller (no holiday import) | `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/leaveAndAttendance/leaveImport/EmployeeLeaveImportController.java:1-112` | legacy |
| Holiday services: CRUD only | `Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/service/leaveAndAttendance/holiday/HolidayService.java:1-32` | legacy |

