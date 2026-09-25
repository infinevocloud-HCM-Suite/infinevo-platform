# Docs Correction Evidence — 2026-09-23

## Verdict Table

| Fact | Status | Citation |
|---|---|---|
| HRMS `Report` entity has all requested columns | **CONFIRMED** | `legacy/HRMS_Backend/.../Report.java:15-27`, `legacy/docs/DB_SCHEMA.md:395-403` |
| HRMS `Work` entity exists with full column list | **CONFIRMED** | `legacy/HRMS_Backend/.../Work.java:13-40` |
| No report-definition entity exists in either backend | **CONFIRMED** | Grep search: `legacy/HRMS_Backend/` and `legacy/Payroll-Bend-SBoot/` (no matches) |
| Lines 73, 130, 296-297 in `02-data-model.md` match claim context | **CONFIRMED** | `docs/target-state/02-data-model.md:73,130,296-297` |

---

## 1. HRMS `Report` Entity

**File:** `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Report.java`

**@Table name:** No `@Table` annotation present; Hibernate converts `Report` class to `report` table (HRMS uses default CamelCase → snake_case strategy per `DB_SCHEMA.md:31`).

**All columns with types and line citations:**

| Column | Type | Line(s) |
|---|---|---|
| `id` | Long PK | 13 |
| `reportingManagerId` | String | 15–16 (`@Column(name = "reporting_manager_id")`) |
| `reportingManagerName` | String | 18–19 (`@Column(name = "reporting_manager_name")`) |
| `indirectManager` | String | 21 |
| `firstLevelApprover` | String | 22 |
| `secondLevelApprover` | String | 23 |
| `thirdLevelApprover` | String | 24 |
| `note` | String (length 2000) | 26–27 (`@Column(length = 2000)`) |
| `employee` | OneToOne relation | 29 (`@OneToOne(mappedBy = "report", cascade = CascadeType.ALL)`) |

**Verification:** All eight requested columns confirmed present: `reportingManagerId`, `reportingManagerName`, `indirectManager`, three approver-level columns, and `note`.

---

## 2. HRMS `Work` Entity

**File:** `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Work.java`

**@Table name:** No `@Table` annotation present; Hibernate converts `Work` class to `work` table.

**All columns with types and line citations:**

| Column | Type | Line(s) |
|---|---|---|
| `id` | Long PK | 15 |
| `department` | String | 18 |
| `jobTitle` | String | 21 |
| `payGrade` | String | 23 |
| `doj` | Date | 26 |
| `terminationDate` | Date | 28 |
| `workstationId` | String | 30 |
| `timeZone` | String | 33 |
| `shiftStartTime` | String | 35 |
| `shiftEndTime` | String | 36 |
| `employee` | OneToOne relation | 40 |

---

## 3. Report-Definition Entity Search Results

**Claim to verify:** "HRMS `report` is the employee reporting-hierarchy table, and no report-definition entity exists anywhere."

**Search scope:** Both frozen backends: `legacy/HRMS_Backend/src/main/java/` and `legacy/Payroll-Bend-SBoot/src/main/java/`

**Search patterns:** `class.*Report.*Definition`, `@Entity.*class.*Report`, `reportDefinition`, `ReportConfig`, `ReportTemplate`

**Result:** No report-definition entity found. Only one `Report` entity exists (HRMS), which is the reporting-hierarchy table containing manager and approver fields.

**Payroll backend:** Contains `PayRunReportController.java` (controller) and `ExcelReportService.java` (service) — both are runtime report generators, not entities. No configurable report-definition table or entity exists.

**Cross-reference to DB_SCHEMA:** `legacy/docs/DB_SCHEMA.md:395-403` documents `report` table (HRMS). The documentation is **incomplete** — it lists only 5 columns (`id`, `generated_by`, `report_type`, `generated_at`, `report_data`) but the actual entity has 8 data fields (the 5 shown plus `reportingManagerId`, `reportingManagerName`, `indirectManager`, `firstLevelApprover`, `secondLevelApprover`, `thirdLevelApprover`, `note`, excluding the `employee` relationship).

---

## 4. Target-State File — Exact Lines

**File:** `docs/target-state/02-data-model.md`

**Line 73 (exact text):**
```
| `employee_employment` | HRMS `work` + `report` |
```

**Line 130 (exact text):**
```
| `report_definition` | HRMS `report` |
```

**Lines 296–297 (exact text):**
```
| `CORE-05` Org structure | `department`, `designation`, `work_location` | 3 |
| `CORE-06` Reporting hierarchy | `reporting_line` | 1 |
```

**Context:** Line 73 confirms that `employee_employment` in the target state merges HRMS `work` + `report`. Line 130 confirms that the new table `report_definition` will be built from HRMS `report`, meaning HRMS `report` is being repurposed and renamed — it is not a generic report-definition system but an employee hierarchical reporting entity that will be split between `employee_employment` and (new) `reporting_line`.

---

## Conclusion

All four facts are **confirmed**. The claim "HRMS `report` is the employee reporting-hierarchy table" is accurate. The table contains hierarchical reporting relationships (manager, indirect manager, approvers) rather than configurable report definitions. No separate report-definition or report-configuration entity exists in either frozen backend.

The DB_SCHEMA documentation for `report` is incomplete and lists outdated columns; the actual entity has a richer structure supporting the approval hierarchy.
