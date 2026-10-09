# Architectural Analysis: W-67 Migration Rules & Governance

> **Analysis Date:** 2026-10-09  
> **Target Ticket:** `W-67` Migration Rules (Issue #87, Stream I)  
> **Status:** Read-only architectural investigation and evidence synthesis  
> **Harness Skill:** `/analyze`

---

## 1. Summary Evidence Table

| Topic / Claim | Source System | Evidence (`file:line`) | Analysis & Hard Findings |
|---|---|---|---|
| **Timesheets Duplicate Entity** | Legacy HRMS / UAT Dump | [hrmsNPDB_timesheet.sql:60-61](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheet.sql#L60-L61)<br>[hrmsNPDB_timesheets.sql:47](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheets.sql#L47) | `timesheets` (hierarchical) has active rows (`TS-101` to `TS-108`). `timesheet` (flat, with `thursady_hours` typo) has **0 rows** in the dump and is completely retired. |
| **Leave Request Duplicate Entity** | Legacy HRMS / UAT Dump | [hrmsNPDB_leave_request.sql:45-46](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_request.sql#L45-L46)<br>[hrmsNPDB_leave_requests.sql:56](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_requests.sql#L56) | `leave_requests` has live records with manager comments and approval status. `leave_request` is empty (**0 rows**) and retired. |
| **Leave Authority & LOP Discrepancy** | Legacy Payroll & HRMS | [payroll_test_db_employee_leave_balance_consumption.sql:62](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_leave_balance_consumption.sql#L62)<br>[hrmsNPDB_leave_balances.sql:49](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_balances.sql#L49) | Payroll pay runs read LOP and consumption directly from `employee_leave_balance_consumption` (`payrun_status = 'PROCESSED'`). HRMS tracks balances in `leave_balances`. Both are authoritative for their respective historical domains. |
| **Employee Master Merge & Identity** | Target Core Platform | [Employee.java:80-93](file:///d:/Infinevo%20Platform/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/employee/Employee.java#L80-L93)<br>[Employee.java:149-150](file:///d:/Infinevo%20Platform/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/employee/Employee.java#L149-L150) | Target `core.employee` requires mandatory `tenant_id`, unique `(tenant_id, employee_number)`, unique `(tenant_id, work_email)`, and links to Keycloak via `user_account_id`. |
| **Dual Auth Disparity** | Legacy HRMS & Payroll | [hrmsNPDB_ourusers.sql:25-40](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_ourusers.sql#L25-L40)<br>[payroll_test_db_companyUser.sql:25-39](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_companyUser.sql#L25-L39) | HRMS authenticates via local bcrypt passwords on `ourusers` (`BUG-001`). Payroll authenticates via Keycloak OAuth2 UUIDs on `companyUser(userId)`. Unified platform mandates Keycloak Realm `HRMS`. |
| **Dual Cloudinary Document Buckets** | Legacy HRMS & Payroll | [hrmsNPDB_employee_document.sql:46](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_employee_document.sql#L46)<br>[payroll_test_db_employee_poi_document.sql:44](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_poi_document.sql#L44) | Two separate Cloudinary accounts exist: HRMS bucket `dthv5gvff` (employee docs) and Payroll bucket `dbsu0ghks` (POI investment proofs). Both must be extracted to Azure Blob Storage private containers. |
| **Free-Text Org Masters** | Legacy HRMS vs Target Core | [hrmsNPDB_work.sql:26-37](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_work.sql#L26-L37)<br>[Employee.java:164-165](file:///d:/Infinevo%20Platform/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/employee/Employee.java#L164-L165) | HRMS stores `department`, `job_title`, and `work_location` as unvalidated strings. Target `core.employee` requires foreign keys to `core.department`, `core.designation`, and `core.work_location`. |

---

## 2. Definitive Answers to the Five W-67 Governance Questions

### Question 1: Which of the two live timesheet systems holds data worth keeping?
* **Settled Finding:** **`timesheets` (hierarchical) is the sole authoritative system.**
* **Evidence:** In the latest UAT database dump ([hrmsNPDB_timesheets.sql:47](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheets.sql#L47)), `timesheets` contains all active submission records (`TS-101` through `TS-108`) with workflow statuses (`APPROVED`, `SUBMITTED`). In contrast, the legacy flat `timesheet` table ([hrmsNPDB_timesheet.sql:60](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheet.sql#L60)) contains **0 rows**.
* **Migration Rule:** Only rows from `timesheets` and its child tables (`day_entry`, `task_entry`, `project_entry`) will be migrated into `hrms.timesheet` (`W-42`). The flat `timesheet` table is dropped from migration extraction.

### Question 2: Which duplicate leave and balance entities hold live rows?
* **Settled Finding:** **`leave_requests` is the authoritative request table; `leave_balances` is the baseline balance table.**
* **Evidence:** The singular table `leave_request` ([hrmsNPDB_leave_request.sql:45](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_request.sql#L45)) has **0 rows** in the dump. The plural table `leave_requests` ([hrmsNPDB_leave_requests.sql:56](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_requests.sql#L56)) holds all active requests with approval timestamps and LOP flags.
* **Migration Rule:** Migrate `leave_requests` into `core.leave_request`. Ignore `leave_request`. For balances, migrate `leave_balances` into `core.leave_allocation` (`W-16.2`); `employee_leave_balances` contains negative values and unverified comp-off totals and will be used only for secondary audit reference.

### Question 3: Which side seeds Core's leave tables?
* **Settled Finding:** **HRMS seeds active policy & balances; Payroll seeds historical financial consumption.**
* **Evidence:** The pay run in Payroll reads directly from `employee_leave_balance_consumption` ([payroll_test_db_employee_leave_balance_consumption.sql:48-49](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_leave_balance_consumption.sql#L48-L49)) where each row is tagged with a `payrun_id` and status `PROCESSED`. HRMS maintains `leave_balances` for current-year employee request validation.
* **Migration Rule:** 
  1. Current allocations and policy balances are seeded from HRMS `leave_types` and `leave_balances` into `core.leave_allocation` and `core.leave_policy`.
  2. Past pay run line calculations are preserved as-is from Payroll `employee_leave_balance_consumption` to guarantee payslip reproducibility.

### Question 4: Whether both products genuinely hold live employee data (The 3-Cohort Rule)?
* **Settled Finding:** **Yes, both products hold live employee records, but their user bases do not fully overlap.**
* **User Decision Formulated:** **Strict 3-Cohort Email Rule (Cohort 4 excluded).** No fuzzy matching, heuristic PAN/Aadhaar fallbacks, or phone number joins will be attempted.
* **The Three Concrete Cohorts:**
  1. **Cohort 1 (Unified Match):** Exact match on `LOWER(TRIM(HRMS.work_email)) = LOWER(TRIM(Payroll.work_mail))`. Merges into 1 `core.employee` record. Demographics (personal, contact, identification) come from HRMS; bank details, statutory profiles (EPF/ESI/PT), CTC structures, and past pay runs come from Payroll. Creates 1 Keycloak user in `core.user_account`.
  2. **Cohort 2 (HRMS-Only):** Email exists in HRMS but not Payroll (e.g., interns, independent contractors, vendor staff). Creates 1 `core.employee` record with HRMS demographics and 1 Keycloak login. CTC and statutory profiles remain unallocated.
  3. **Cohort 3 (Payroll-Only):** Email exists in Payroll but not HRMS (e.g., board directors, former employees, off-payroll beneficiaries). Creates 1 `core.employee` record with Payroll data and 1 Keycloak login. Timesheet and attendance modules remain unassigned.

### Question 5: How are the two existing Loss-of-Pay (LOP) calculations reconciled?
* **Settled Finding:** **Historical pay runs stay frozen; discrepancies are logged for Finance sign-off.**
* **Evidence:** HRMS computes LOP from leave requests with `lop_generated = true` ([hrmsNPDB_leave_requests.sql:45](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_requests.sql#L45)); Payroll computes LOP using `lop_days` in `employee_leave_balance_consumption` ([payroll_test_db_employee_leave_balance_consumption.sql:35](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_leave_balance_consumption.sql#L35)).
* **Migration Rule:**
  1. Past payroll calculations are never retroactively recalculated.
  2. The `W-70` reconciliation script generates an automated **Loss-of-Pay Mismatch Log** comparing HRMS unpaid days against Payroll LOP deductions for all overlapping employees.
  3. The cutover cannot proceed until Finance signs off on the mismatch log.

---

## 3. Supporting Migration Seams Identified

### Seam A: Dual-Bucket Cloudinary Migration (`W-69`)
* **HRMS Bucket:** `res.cloudinary.com/dthv5gvff/...` holds employee identity documents (Aadhaar, PAN, certificates, photos) in `employee_document`.
* **Payroll Bucket:** `res.cloudinary.com/dbsu0ghks/...` holds proof-of-investment receipts (80C, 80D, rent receipts, form 12BB) in `employee_poi_document`.
* **Target Landing:** Both streams must download binaries, upload to Azure Blob Storage private containers (`documents`, `payslips`, `proofs`), and generate records in `core.document` with secure UUIDs and SAS signed links ([W-21-document-store.md:34](file:///d:/Infinevo%20Platform/infinevo-platform/docs/target-state/features/W-21-document-store.md#L34)).

### Seam B: Authentication & Keycloak User Provisioning (`W-68.3`)
* **Disparity:** HRMS `ourusers` holds local bcrypt passwords ([hrmsNPDB_ourusers.sql:49](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_ourusers.sql#L49)); Payroll `companyUser` holds Keycloak subject UUIDs ([payroll_test_db_companyUser.sql:48](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_companyUser.sql#L48)).
* **Target Rule:** The migration engine must call the Keycloak Admin REST API to provision/ensure Keycloak accounts in the single `HRMS` realm for all unique emails across Cohorts 1, 2, and 3, set temporary passwords / invitation triggers (`W-24.2`), create `core.user_account`, and stamp `core.employee.user_account_id`.

### Seam C: Org Master Normalization
* **Disparity:** HRMS `work` stores `department` and `job_title` as free text ([hrmsNPDB_work.sql:27-29](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_work.sql#L27-L29)).
* **Target Rule:** 
  1. Extraction creates distinct lookup sets from Payroll's structured org tables (`department`, `designation`, `workLocations`).
  2. Free-text strings from HRMS are normalized and matched against the master IDs. If an HRMS title has no match, a new tenant-scoped master entry is generated and linked via foreign key.

---

## 4. Verification with the Test Dataset

The analysis has been verified against the newly generated test dataset in [`Non-prod_hrms_payroll/test-dataset/`](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/test-dataset):
* **26 Total Users** structured explicitly into Cohort 1 (12 users), Cohort 2 (7 users), and Cohort 3 (7 users).
* **Zero `@infinevocloud.com` emails.**
* Covers every functional module: Attendance, Clock Sessions, Overtime, Leaves, Timesheets, CTC, Statutory PF/ESI/PT, Tax Declarations, POI Cloudinary proofs, and historical pay runs.

---

## 5. Next Recommended Action

Run `/plan-feature W-67` to draft the formal, founder-reviewable specification file:  
`docs/target-state/features/W-67-migration-rules.md`
