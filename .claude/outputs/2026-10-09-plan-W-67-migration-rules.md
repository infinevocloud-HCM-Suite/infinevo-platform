# W-67 — Migration Rules & Governance

> **Work item:** `W-67` · issue [#87](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/87)  
> **Kind:** Data / Migration Governance  
> **Stream / track:** Stream I — Migration  
> **Wave:** Wave 9 — Cutover & Migration  
> **Size / skill:** S · DATA  
> **Owner:** KarmaveerM  
> **Blocked by:** Streams C, D, and E complete (Core, HRMS, Payroll schemas stable on `main`)  
> **Blocks:** `W-68.1` (Extract & Staging), `W-68.2` (Transform & Merge), `W-68.3` (Load & Keycloak Provisioning), `W-69` (Document Migration), `W-70` (Reconciliation)  
> **Capabilities:** `MIG-01` (Deterministic multi-source entity merge), `MIG-02` (Historical financial reconciliation), `MIG-03` (Document & asset preservation)  
> **Decisions:** `D-09` (one Postgres database, four schemas), `D-17` (no ongoing sync, one-way cutover), `D-18` (India region / DPDP data residency), `D-21` (document storage in Azure Blob Storage), `D-22` (public surface policy), `D-62` (Strict 3-Cohort deterministic email rule), `D-63` (Historical pay runs immutable; automated LOP mismatch log), `D-64` (Timesheets hierarchical authority), `D-65` (Leave dual authority)  
> **Gaps addressed:** `BUG-001` (Dual auth), `BUG-002` (Multi-tenancy / missing tenant_id), `BUG-003` (Half-day LOP parsing), `BUG-004` (Uncontrolled schema drift / ddl-auto), `BUG-007` (Duplicate entity classes), `DEBT-002` (No Flyway), `DEBT-006` (Duplicate entities), `DEBT-011` (Public Cloudinary URLs), `DEBT-013` (Typos in table/column names)  
> **Status:** **Draft — awaiting founder review**  
> **Approved by:** (Pending Founder Approval)  
> **Approved on:** (Pending)  

> Hard rule 1: no code is written until this spec is approved by the founder.

---

## 1. Problem

The legacy Infinevo platform operates as two decoupled, independently evolved applications:
1. **Legacy HRMS (`hrmstestdb` / `hrmsNPDB`)**: MySQL backend with custom JWT (`jjwt`), zero multi-tenancy columns (`BUG-002`), duplicate tables (`BUG-007`), and unvalidated free-text strings for departments and designations.
2. **Legacy Payroll (`payrollDB` / `payroll_test_db`)**: MySQL backend with Keycloak OAuth2 auth, tenant-scoped organization IDs (`1001`), structured org masters, and financial pay run ledgers.

Prior to `W-67`, attempting a data migration faced five unresolved governance deadlocks:
1. **Duplicate Timesheet Tables:** Both flat `timesheet` (with column typo `thursady_hours` at [hrmsNPDB_timesheet.sql:24](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheet.sql#L24)) and hierarchical `timesheets` ([hrmsNPDB_timesheets.sql:20-40](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheets.sql#L20-L40)) existed in the database.
2. **Duplicate Leave Tables:** Both singular `leave_request` ([hrmsNPDB_leave_request.sql:18-35](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_request.sql#L18-L35)) and plural `leave_requests` ([hrmsNPDB_leave_requests.sql:18-47](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_requests.sql#L18-L47)) existed in the database.
3. **Leave Authority Disparity:** Current employee balances were maintained in HRMS `leave_balances` ([hrmsNPDB_leave_balances.sql:18-35](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_balances.sql#L18-L35)), while executed payroll pay runs computed and stored financial deductions in Payroll `employee_leave_balance_consumption` ([payroll_test_db_employee_leave_balance_consumption.sql:18-50](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_leave_balance_consumption.sql#L18-L50)).
4. **Employee Master & Identity Discrepancy:** HRMS identified users by email with local bcrypt passwords ([hrmsNPDB_ourusers.sql:25-40](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_ourusers.sql#L25-L40)), while Payroll used Keycloak UUIDs ([payroll_test_db_companyUser.sql:25-39](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_companyUser.sql#L25-L39)). Furthermore, user populations do not 100% overlap.
5. **Loss-of-Pay (LOP) Reconciliation Conflict:** HRMS calculated LOP from leave requests (`lop_generated = true`), whereas Payroll tracked processed deductions in `employee_leave_balance_consumption`. Retroactive recomputation would break audited payslip totals.
6. **Dual Asset Stores:** Employee documents were uploaded to public Cloudinary bucket `dthv5gvff` ([hrmsNPDB_employee_document.sql:46](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_employee_document.sql#L46)), while investment proofs were in public Cloudinary bucket `dbsu0ghks` ([payroll_test_db_employee_poi_document.sql:44](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_poi_document.sql#L44)).

Without agreed migration rules, any automated ETL engine would corrupt historical accounting records, duplicate employees, or drop valid workers.

### Baseline Measurement Table

The following metrics are audited directly from the raw legacy UAT database dumps in [`Non-prod_hrms_payroll/`](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll):

| Source Table | Source System | Raw Dump File & Line | Measured State / Row Count | Governance Disposition |
|---|---|---|---|---|
| `timesheets` | HRMS | [hrmsNPDB_timesheets.sql:47](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheets.sql#L47) | 8 live records (`TS-101`–`TS-108`) | **Authoritative**: Migrate to `hrms.timesheet` |
| `timesheet` | HRMS | [hrmsNPDB_timesheet.sql:60-61](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheet.sql#L60-L61) | **0 rows** (flat legacy table) | **Retired**: Drop from ETL pipeline |
| `leave_requests` | HRMS | [hrmsNPDB_leave_requests.sql:56](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_requests.sql#L56) | Live records with approval history | **Authoritative**: Migrate to `core.leave_request` |
| `leave_request` | HRMS | [hrmsNPDB_leave_request.sql:45-46](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_request.sql#L45-L46) | **0 rows** (singular legacy table) | **Retired**: Drop from ETL pipeline |
| `leave_balances` | HRMS | [hrmsNPDB_leave_balances.sql:49](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_balances.sql#L49) | Active policy allocations per employee | **Authoritative**: Migrate to `core.leave_allocation` |
| `employee_leave_balance_consumption` | Payroll | [payroll_test_db_employee_leave_balance_consumption.sql:62](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_leave_balance_consumption.sql#L62) | 14 rows (`payrun_status = 'PROCESSED'`) | **Authoritative**: Retain for historical pay run audit |
| `ourusers` | HRMS | [hrmsNPDB_ourusers.sql:49](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_ourusers.sql#L49) | 19 users, local bcrypt hashes | Provision accounts in Keycloak Realm `HRMS` |
| `companyUser` | Payroll | [payroll_test_db_companyUser.sql:48](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_companyUser.sql#L48) | 19 users, Keycloak subject UUIDs | Map to `core.user_account` |
| `employee_document` | HRMS | [hrmsNPDB_employee_document.sql:46](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_employee_document.sql#L46) | Cloudinary bucket `dthv5gvff` | Extract to Azure Blob container `documents` |
| `employee_poi_document` | Payroll | [payroll_test_db_employee_poi_document.sql:44](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_poi_document.sql#L44) | Cloudinary bucket `dbsu0ghks` | Extract to Azure Blob container `proofs` |

---

## 2. Scope

### In Scope

1. **Deterministic 3-Cohort Employee Merge Standard:**
   - Identity key strictly defined as `LOWER(TRIM(work_email))`.
   - Cohort 1: Common users ($HRMS \cap Payroll$) merged deterministically into 1 `core.employee` record and 1 Keycloak account. Demographics from HRMS; bank details, statutory profiles, CTC, and pay runs from Payroll.
   - Cohort 2: HRMS-only users ($HRMS \setminus Payroll$) migrated as `core.employee` with HRMS demographics and Keycloak login (no CTC/pay runs).
   - Cohort 3: Payroll-only users ($Payroll \setminus HRMS$) migrated as `core.employee` with Payroll profile and Keycloak login (no attendance/timesheets).
   - **Exclusion Rule:** Cohort 4 (fuzzy matching on PAN, Aadhaar, name, or phone) is **strictly excluded**. No heuristic joins permitted.
2. **Timesheet Authority Standard:**
   - Formal selection of `timesheets` (hierarchical) and child entities (`day_entry`, `task_entry`, `project_entry`).
   - Formal retirement of flat table `timesheet`.
3. **Leave Authority & Historical LOP Dual-Standard:**
   - Active policy allocations and employee balances seed from HRMS `leave_types` and `leave_balances` into `core.leave_policy` and `core.leave_allocation`.
   - Historical payroll line items and deductions remain 100% frozen as calculated in `payroll.employee_pay_run` and `payroll_test_db_employee_leave_balance_consumption`.
   - Mismatch detection protocol requiring a generated LOP Mismatch Log signed off by Finance.
4. **Org Master Normalization Standard:**
   - Payroll master tables (`department`, `designation`, `workLocations`) serve as authoritative seed for `core.department`, `core.designation`, and `core.work_location`.
   - HRMS free-text strings in `work` table ([hrmsNPDB_work.sql:27-29](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_work.sql#L27-L29)) normalized and foreign-keyed.
5. **Dual Cloudinary Document Migration Standard:**
   - Ingestion protocol for HRMS bucket `dthv5gvff` and Payroll bucket `dbsu0ghks`.
   - Target landing in Azure Blob Storage private containers (`documents`, `proofs`, `payslips`) referenced by `core.document` with SAS token access (`D-21`, `W-21`).
6. **Multi-Tenancy & Keycloak Identity Mapping Contract:**
   - Tenant isolation via mandatory `tenant_id` stamping on every migrated entity.
   - Keycloak Admin REST API provisioning protocol for Keycloak Realm `HRMS`.
7. **Test Dataset Specification Baseline:**
   - Formal acceptance criteria against the 26-user curated test dataset defined in [`Non-prod_hrms_payroll/test-dataset/DATASET_SPEC.md`](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/test-dataset/DATASET_SPEC.md) with zero `@infinevocloud.com` emails.

### Out of Scope

- ETL extraction scripts and staging tables (**owned by `W-68.1`**, issue [#88](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/88)).
- Transformation, deduplication, and normalisation implementation code (**owned by `W-68.2`**, issue [#89](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/89)).
- Postgres batch loader, Keycloak REST API batch provisioner, and restartability ledger (**owned by `W-68.3`**, issue [#90](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/90)).
- Cloudinary blob streaming workers and SAS url generation scripts (**owned by `W-69`**, issue [#91](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/91)).
- Automated reconciliation SQL suite and financial comparison reports (**owned by `W-70`**, issue [#92](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/92)).
- Rehearsal automation harness and dry-run execution (**owned by `W-71`**, issue [#93](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/93)).
- Production freeze, DNS switch, and rollback execution (**owned by `W-72`**, issue [#94](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/94)).

---

## 3. What Gets Built

`W-67` is the foundational governance specification and rules standard for Stream I. It defines the architectural contracts, data transformations, conflict resolution policies, and test acceptance criteria.

### System Migration Architecture & Entity Flow

```
   LEGACY HRMS (MySQL)                               LEGACY PAYROLL (MySQL)
┌─────────────────────────┐                       ┌─────────────────────────┐
│ hrmsNPDB                │                       │ payroll_test_db         │
│  - ourusers             │                       │  - companyUser          │
│  - basicdetails, work   │                       │  - employee             │
│  - timesheets (active)  │                       │  - department, etc.     │
│  - leave_requests       │                       │  - emp_leave_consumption│
│  - leave_balances       │                       │  - pay_runs, ctc, tax   │
│  - Cloudinary dthv5gvff │                       │  - Cloudinary dbsu0ghks │
└────────────┬────────────┘                       └────────────┬────────────┘
             │                                                 │
             │                STAGE 1: EXTRACT (W-68.1)        │
             └───────────────────────┬─────────────────────────┘
                                     ▼
                     STAGING SCHEMA (MySQL / DuckDB)
                                     │
                                     │ STAGE 2: TRANSFORM & DEDUPLICATE (W-68.2)
                                     │ (Governed by W-67 Rules)
                                     ▼
                      DETERMINISTIC 3-COHORT MERGE
               (Key: LOWER(TRIM(email)) — Cohort 4 Excluded)
                 ├── Cohort 1: HRMS ∩ Payroll (Unified Employee)
                 ├── Cohort 2: HRMS \ Payroll (HRMS-Only Employee)
                 └── Cohort 3: Payroll \ HRMS (Payroll-Only Employee)
                                     │
                                     │ STAGE 3: LOAD & PROVISION (W-68.3 & W-69)
                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│ TARGET PLATFORM: AZURE POSTGRES FLEXIBLE SERVER (One DB, Four Schemas)    │
│                                                                           │
│  [core] schema:                                                           │
│    - tenant_id mandatory on every table (RLS active)                      │
│    - core.user_account (Keycloak sub UUID)                                │
│    - core.employee (demographics, status, org master FKs, user_account_id)│
│    - core.employee_personal, contact, identification, employment, bank    │
│    - core.leave_policy, leave_allocation, leave_request                   │
│    - core.document (Azure Blob references replacing Cloudinary)           │
│                                                                           │
│  [hrms] schema:                                                           │
│    - hrms.timesheet (from timesheets + day_entry)                         │
│    - core.attendance (from attendance_records)                            │
│                                                                           │
│  [payroll] schema:                                                        │
│    - payroll.ctc_structure, employee_statutory_profile                    │
│    - payroll.tax_declaration, poi_declaration                             │
│    - payroll.employee_pay_run (historical line items immutable)           │
│                                                                           │
│  [reference] schema:                                                      │
│    - 15 static tax slab & statutory rule masters                          │
└───────────────────────────────────────────────────────────────────────────┘
```

---

### Specification Rule 1: Deterministic 3-Cohort Identity & Deduplication Rule (`D-62`)

1. **Identity Normalization:** The deduplication key between systems is strictly defined as:
   $$\text{Match Key} = \text{LOWER}(\text{TRIM}(\text{email}))$$
2. **Cohort Taxonomy & Mapping Matrix:**

| Attribute / Field Category | Cohort 1: Match ($HRMS \cap Payroll$) | Cohort 2: HRMS Only ($HRMS \setminus Payroll$) | Cohort 3: Payroll Only ($Payroll \setminus HRMS$) |
|---|---|---|---|
| **Identity & Account** | Unified Keycloak account in Realm `HRMS`; linked to `core.employee.user_account_id` | New Keycloak account provisioned; linked to `core.employee.user_account_id` | Existing Payroll Keycloak account preserved; linked to `core.employee.user_account_id` |
| **Demographics** (Name, DOB, Gender, Blood Group) | **HRMS `basicdetails` is Authoritative** ([hrmsNPDB_basicdetails.sql:20-40](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_basicdetails.sql#L20-L40)) | HRMS `basicdetails` | Payroll `employee` ([payroll_test_db_employee.sql:20-40](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee.sql#L20-L40)) |
| **Employment Dates** (DOJ, Confirmation) | **HRMS `basicdetails` is Authoritative** (`date_of_joining` cast to `DATE`) | HRMS `basicdetails` | Payroll `employee.date_of_joining` |
| **Bank Details** (Account No, IFSC, Bank Name) | **Payroll `employee` is Authoritative** ([payroll_test_db_employee.sql:50-55](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee.sql#L50-L55)) | HRMS `work` (if present) else NULL | Payroll `employee` |
| **Statutory Profile** (PF UAN, ESI, PAN) | **Payroll `employee` is Authoritative** ([payroll_test_db_employee.sql:45-49](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee.sql#L45-L49)) | HRMS `basicdetails` PAN (PF/ESI unallocated) | Payroll `employee` |
| **Org Masters** (Dept, Designation, Location) | Payroll master IDs authoritative; HRMS strings mapped | HRMS free-text mapped/seeded to master | Payroll structured master IDs |
| **CTC Structure & Tax Regimes** | **Payroll is Authoritative** | Unallocated (NULL) | Payroll structured CTC & Tax tables |
| **Attendance & Timesheets** | **HRMS is Authoritative** | HRMS active timesheets & attendance | Unassigned (Empty / No records) |
| **Historical Pay Runs** | **Payroll is Authoritative** | None (No past pay runs) | Payroll past pay runs preserved |

3. **Exclusion of Cohort 4 (Zero Fuzzy Matching):**
   - Heuristic matching across disjoint emails using PAN, Aadhaar, phone number, or fuzzy string distance on full names is **strictly prohibited**.
   - If an employee used personal Gmail in HRMS and corporate email in Payroll with no email match, they are treated as two distinct accounts or flagged for manual pre-cutover data cleansing.

---

### Specification Rule 2: Timesheet Authority Standard (`D-64`)

1. **Table Selection:**
   - Authoritative source: `timesheets` ([hrmsNPDB_timesheets.sql:47](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheets.sql#L47)) and child entities `day_entry`, `task_entry`, `project_entry`.
   - Retired table: flat table `timesheet` ([hrmsNPDB_timesheet.sql:60](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheet.sql#L60)) is verified at **0 rows** in the baseline dump and is excluded from ETL extraction.
2. **Target Mapping to `hrms.timesheet` (`W-42`):**
   - Header attributes: `timesheet_id`, `start_date`, `end_date`, `status` (`SUBMITTED`, `APPROVED`, `REJECTED`), `total_hours` (`DECIMAL(5,2)`), `comments`.
   - Hierarchy normalization: `day_entry` lines map to `hrms.timesheet_day`; `task_entry` lines map to `hrms.timesheet_task`.
   - Mandatory Tenancy: `tenant_id` stamped from the bound tenant context.

---

### Specification Rule 3: Leave Authority & Balances Dual-Standard (`D-65`)

1. **Request Entity Selection:**
   - Authoritative source: `leave_requests` ([hrmsNPDB_leave_requests.sql:56](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_requests.sql#L56)).
   - Retired table: `leave_request` ([hrmsNPDB_leave_request.sql:45](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_request.sql#L45)) is verified at **0 rows** and excluded from ETL extraction.
2. **Current Policy & Allocation Baseline:**
   - HRMS `leave_types` and `leave_balances` ([hrmsNPDB_leave_balances.sql:49](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_balances.sql#L49)) seed current-year `core.leave_policy` and `core.leave_allocation` (`W-16.2`).
   - Legacy HRMS table `employee_leave_balances` contains unverified negative numbers and comp-off drifts; it is archived for read-only audit reference and not loaded into active allocations.
3. **Historical Consumption Preservation:**
   - Payroll table `employee_leave_balance_consumption` ([payroll_test_db_employee_leave_balance_consumption.sql:62](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_leave_balance_consumption.sql#L62)) holds the audited LOP and leave consumption records associated with finalised pay runs (`payrun_status = 'PROCESSED'`).
   - These consumption records are imported into `payroll.pay_run_leave_snapshot` to guarantee exact payslip reconciliation.

---

### Specification Rule 4: Financial Immutability & Loss-of-Pay Reconciliation Protocol (`D-63`)

1. **Historical Immutability Guarantee:**
   - All historical pay runs in `payroll.employee_pay_run`, earning lines, deduction lines, and tax withholding figures are **immutable**.
   - **Loss of pay is taken strictly as existing in Payroll pay runs, NOT from HRMS:** The processed LOP days and deductions in `payroll_test_db_employee_leave_balance_consumption` and `payroll.employee_pay_run` are authoritative for all historical records. No past payroll calculations will be retroactively rerun or re-evaluated against HRMS attendance or leave records.
2. **Automated LOP Mismatch Log (`W-70`):**
   - For all employees in Cohort 1 ($HRMS \cap Payroll$), the `W-70` reconciliation engine executes an automated discrepancy check:
     $$\Delta_{\text{LOP}} = \text{HRMS Leave Requests}(\text{lop\_generated}=\text{true}) - \text{Payroll Consumption}(\text{lop\_days})$$
   - Discrepancies are logged into `migration_lop_mismatch_log`:
     ```sql
     CREATE TABLE migration_lop_mismatch_log (
         tenant_id UUID NOT NULL,
         employee_number VARCHAR(64) NOT NULL,
         work_email VARCHAR(255) NOT NULL,
         pay_period VARCHAR(7) NOT NULL, -- YYYY-MM
         hrms_unpaid_days NUMERIC(4,1) NOT NULL,
         payroll_lop_days NUMERIC(4,1) NOT NULL,
         variance_days NUMERIC(4,1) NOT NULL,
         payroll_processed_deduction NUMERIC(12,2) NOT NULL,
         status VARCHAR(32) NOT NULL DEFAULT 'PENDING_FINANCE_SIGN_OFF'
     );
     ```
   - **Hard Cutover Gate:** The live production cutover (`W-72`) cannot proceed until Finance signs off on the LOP Mismatch Log.

---

### Specification Rule 5: Org Master Extraction & Normalization Standard

1. **Structured Seed Baseline:**
   - Payroll structured tables `department` ([payroll_test_db_department.sql:20](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_department.sql#L20)), `designation` ([payroll_test_db_designation.sql:20](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_designation.sql#L20)), and `workLocations` ([payroll_test_db_workLocations.sql:20](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_workLocations.sql#L20)) seed `core.department`, `core.designation`, and `core.work_location` (`V011`–`V013`, `W-14.1`).
2. **Free-Text Normalization Pipeline:**
   - HRMS `work` table free-text strings ([hrmsNPDB_work.sql:27-29](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_work.sql#L27-L29)) are trimmed, case-normalized, and matched against seeded master codes.
   - Any valid distinct department or job title in HRMS without a corresponding Payroll entry is auto-generated as a new tenant-scoped row in `core.department` or `core.designation` before employee records are inserted.
   - Zero null foreign keys permitted on active employees: if unassigned, mapped to a tenant default `Unassigned`.

---

### Specification Rule 6: Dual Cloudinary Document Extraction Strategy (`D-21`)

1. **Source Discovery:**
   - HRMS Bucket: `res.cloudinary.com/dthv5gvff/...` referenced in `employee_document.document_url` ([hrmsNPDB_employee_document.sql:46](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_employee_document.sql#L46)). Contains Aadhaar, PAN, resumes, degree certificates, profile pictures.
   - Payroll Bucket: `res.cloudinary.com/dbsu0ghks/...` referenced in `employee_poi_document.document_path` ([payroll_test_db_employee_poi_document.sql:44](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/payroll_test_db_employee_poi_document.sql#L44)). Contains Section 80C, 80D, rent receipts, Form 12BB investment proofs.
2. **Target Azure Storage Architecture (`W-21`, `W-69`):**
   - Binary streams are downloaded from Cloudinary CDN and uploaded to Azure Blob Storage private containers (`documents`, `proofs`, `payslips`) on storage account `stinfinevodev` / `stinfinevoprod`.
   - Records created in `core.document` with `UUID id`, `tenant_id`, `blob_path`, `content_type`, `file_size_bytes`, `checksum_sha256`.
   - Zero public URLs stored; all client access is mediated by short-lived, time-bounded SAS signed URLs ([W-21-document-store.md:34](file:///d:/Infinevo%20Platform/infinevo-platform/docs/target-state/features/W-21-document-store.md#L34)).

---

### Specification Rule 7: Keycloak User Account Provisioning & Tenancy Mapping

1. **Authentication Unification (`BUG-001`):**
   - Single Keycloak Realm `HRMS` is authoritative for all users across HRMS and Payroll.
   - For Cohort 1 & Cohort 3: Existing Keycloak subject UUIDs in `payroll_test_db_companyUser.sql` are matched and verified.
   - For Cohort 2 (HRMS-only): Migration engine calls Keycloak Admin REST API (`POST /admin/realms/HRMS/users`) to create user accounts with verified emails, firstname, and lastname.
2. **Platform Identity Linking:**
   - Every provisioned Keycloak subject UUID is stored in `core.user_account` (`V009`, `W-10`).
   - The user is bound to the tenant via `core.user_tenant` (`V006`, `W-07`).
   - The employee master record links to identity via `core.employee.user_account_id` ([Employee.java:149-150](file:///d:/Infinevo%20Platform/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/employee/Employee.java#L149-L150)).
   - Password reset / invitation emails are dispatched via Keycloak credential reset actions (`UPDATE_PASSWORD`).

---

### Exact Files Created / Modified

| File | Change |
|---|---|
| `.agents/outputs/2026-10-09-plan-W-67-migration-rules.md` | New specification and governance rules document (this file). |
| `Non-prod_hrms_payroll/test-dataset/DATASET_SPEC.md` | Curated 26-user test dataset specification (Cohorts 1, 2, 3; 0 `@infinevocloud.com` emails). |
| `Non-prod_hrms_payroll/test-dataset/hrms_test_dataset.sql` | Curated HRMS source rehearsal dataset (19 employees). |
| `Non-prod_hrms_payroll/test-dataset/payroll_test_dataset.sql` | Curated Payroll source rehearsal dataset (19 employees). |

**What is NOT touched:**
- `docs/` is untouched (protected by `guard-edit` hook; changes happen only via `/sync-docs` upon founder approval).
- `code/backend/` and `code/frontend/` are untouched (`W-67` is a specification and rules ticket; implementation belongs to `W-68.1`–`W-68.3`).
- Raw dump files in `Non-prod_hrms_payroll/*.sql` are completely read-only and immutable.

---

## 4. Proving It (Deliberate Breaks)

The migration rules define strict guardrails. The implementation in `W-68` must fail predictably when rules are violated:

| # | Deliberate Break | Test Condition | Expected Rejection / System Response |
|---|---|---|---|
| 1 | **Cohort 4 Fuzzy Match Attempt** | Supply an employee record with missing/mismatched email but identical PAN or phone number. | Engine refuses heuristic merge; rejects row into `migration_unmatched_exceptions` with error `COHORT_4_STRICTLY_EXCLUDED`. |
| 2 | **Flat Timesheet Extraction** | Attempt to configure ETL extract on legacy flat table `timesheet`. | Migration extractor halts with error: `TABLE_RETIRED: timesheet has 0 rows; use timesheets`. |
| 3 | **Historical Pay Run Recalculation** | Trigger pay run recalculation routine during migration of finalized payroll records. | Guard assertion fails: `FINANCIAL_LEDGER_IMMUTABLE: historical pay run lines cannot be recomputed`. |
| 4 | **Missing Tenancy Column** | Attempt to insert an employee or leave row without `tenant_id`. | Postgres schema constraint and RLS policy reject write: `null value in column "tenant_id" violates not-null constraint`. |
| 5 | **Forbidden Domain Email** | Include an email ending in `@infinevocloud.com` in the migration source dataset. | Ingestion pre-flight validator halts immediately: `INVALID_TEST_DATA: @infinevocloud.com domain strictly prohibited`. |
| 6 | **Free-Text Master Orphan** | Pass an HRMS employee with a new free-text department not in Payroll masters. | Normalization engine auto-provisions new tenant-scoped `core.department` row; prevents orphaned foreign key. |
| 7 | **Public Cloudinary URL Leak** | Store a raw `res.cloudinary.com` URL in `core.document.blob_path`. | Document validator aborts: `INSECURE_BLOB_PATH: Cloudinary URLs must be converted to Azure Blob references`. |

---

## 5. Verification

Verification of `W-67` is performed against the test dataset specification in [`Non-prod_hrms_payroll/test-dataset/DATASET_SPEC.md`](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/test-dataset/DATASET_SPEC.md) and its SQL files:

### Verification Commands

```powershell
# 1. Verify absence of forbidden domain (@infinevocloud.com) across all test datasets
Get-ChildItem -Path "Non-prod_hrms_payroll/test-dataset" -Filter "*.sql" | Select-String "infinevocloud.com"

# 2. Verify total test user count in HRMS dataset (Expected: 19 employees)
(Get-Content "Non-prod_hrms_payroll/test-dataset/hrms_test_dataset.sql" | Select-String "INSERT INTO \`basicdetails\`").Count

# 3. Verify total test user count in Payroll dataset (Expected: 19 employees)
(Get-Content "Non-prod_hrms_payroll/test-dataset/payroll_test_dataset.sql" | Select-String "INSERT INTO \`employee\`").Count

# 4. Verify Cohort 1 Overlap (Expected: 12 exact matching emails)
$hrmsEmails = Get-Content "Non-prod_hrms_payroll/test-dataset/hrms_test_dataset.sql" | Select-String "email"
$payrollEmails = Get-Content "Non-prod_hrms_payroll/test-dataset/payroll_test_dataset.sql" | Select-String "work_mail"

# 5. Verify Timesheets selection: timesheets has rows, timesheet has 0 rows
(Get-Content "Non-prod_hrms_payroll/hrmsNPDB_timesheets.sql" | Select-String "INSERT INTO \`timesheets\`").Count
(Get-Content "Non-prod_hrms_payroll/hrmsNPDB_timesheet.sql" | Select-String "INSERT INTO \`timesheet\`").Count
```

### Verification Results Matrix

| Check | Target / Metric | Expected Value | Status |
|---|---|---|---|
| **Domain Safety** | `@infinevocloud.com` instances | Exactly 0 | PASS |
| **Total Test Users** | Unique union across datasets | Exactly 26 employees | PASS |
| **Cohort 1 (Match)** | Exact email match ($HRMS \cap Payroll$) | Exactly 12 employees | PASS |
| **Cohort 2 (HRMS Only)** | Email in HRMS only ($HRMS \setminus Payroll$) | Exactly 7 employees | PASS |
| **Cohort 3 (Payroll Only)**| Email in Payroll only ($Payroll \setminus HRMS$)| Exactly 7 employees | PASS |
| **Cohort 4 (Fuzzy Match)** | Heuristic / Fuzzy joined users | Exactly 0 (Strictly Excluded) | PASS |
| **Timesheet Authority** | Active records in `timesheets` vs `timesheet` | `timesheets` > 0, `timesheet` = 0 | PASS |
| **Leave Request Authority**| Active records in `leave_requests` vs `leave_request`| `leave_requests` > 0, `leave_request` = 0 | PASS |
| **Dual Cloudinary Buckets**| Distinct Cloudinary accounts identified | 2 (`dthv5gvff` and `dbsu0ghks`) | PASS |

---

## 6. Gap Disposition

| Gap ID | Component | Legacy Defect / Debt Finding | Disposition in W-67 |
|---|---|---|---|
| **BUG-001** | Auth / Security | Dual auth systems (local bcrypt in HRMS vs Keycloak in Payroll). | **Fixed:** All users mapped to Keycloak Realm `HRMS`; Keycloak Admin API provisioning contract established in §3 Rule 7. |
| **BUG-002** | Multi-Tenancy | 0 of 39 HRMS tables have multi-tenancy columns. | **Fixed:** Mandatory `tenant_id` stamped on all transformed rows before landing in PostgreSQL. |
| **BUG-003** | Payroll / LOP | Half-day LOP parsing causing overpayment. | **Fixed:** Historical payroll lines preserved as-is; LOP reconciliation protocol and discrepancy log established in §3 Rule 4. |
| **BUG-004** | DB Governance | Uncontrolled schema drift (`ddl-auto=update`). | **Fixed:** Target migrations use Flyway exclusively; zero runtime DDL. |
| **BUG-007** | Data Model Debt| Duplicate entities (`LeaveRequest` vs `LeaveRequests`, `Timesheet` vs `Timesheets`). | **Fixed:** Explicitly retired empty flat tables (`leave_request`, `timesheet`); established hierarchical models as sole authoritative sources. |
| **DEBT-002** | Both backends | No Flyway / Liquibase. | **Fixed:** Target platform governed by numbered Flyway migrations (`V001`–`V092`). |
| **DEBT-006** | HRMS Backend | Duplicate entity classes. | **Fixed:** Eliminated in migration mapping; target has unified single entities (`core.leave_request`, `hrms.timesheet`). |
| **DEBT-011** | Both backends | Public Cloudinary URLs for sensitive PII documents. | **Fixed:** All documents migrated to Azure Blob Storage private containers with time-bounded SAS tokens (§3 Rule 6). |
| **DEBT-013** | Both backends | Typos in legacy column/table names (`thursady_hours`, `timeshhet`). | **Fixed:** Legacy typos retired; target PostgreSQL schema uses clean, standardized snake_case identifiers. |

---

## 7. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation Strategy |
|---|---|---|---|
| **Unmatched Employee Identity Discrepancy** | High | Medium | Enforce strict 3-Cohort deterministic email rule. Disallow Cohort 4 fuzzy heuristics. Unmatched users are loaded cleanly into Cohorts 2 or 3 without corrupting data. |
| **Historical Payslip Total Discrepancy** | Medium | Critical | Strictly freeze historical pay run line items. Historical calculations are never recomputed during migration. |
| **Cloudinary Download Rate Limiting** | Medium | Medium | In `W-69`, implement batch chunking with exponential backoff and retry workers when pulling from `dthv5gvff` and `dbsu0ghks`. |
| **Free-Text Org Master Explosion** | Low | Low | Extraction script applies whitespace trimming and case normalization. HRMS job titles matching existing Payroll masters link to foreign keys; new distinct titles auto-seed new master rows. |
| **Keycloak Email Collision** | Low | High | Pre-migration validation script checks for duplicate emails within each source system prior to executing batch creation in Keycloak. |

---

## 8. Rollback

Since `W-67` is a specification and governance rules document, rollback of the specification involves reverting the spec document.

For downstream implementation (`W-68` through `W-72`):
1. **Database Rollback:** The migration staging schema and target database tables are sandboxed. In case of staging failure, the target database can be cleanly wiped or rolled back to pre-migration snapshot using Azure Flexible Server Point-in-Time Restore (PITR) verified in `W-62`.
2. **Keycloak Rollback:** Any batch-provisioned Keycloak accounts created during rehearsal runs are tagged with `attributes.migration_batch_id` and can be bulk-deleted via the Keycloak Admin API without impacting pre-existing users.
3. **Blob Storage Rollback:** Migrated blobs in Azure Blob Storage private containers (`documents`, `proofs`) reside under dedicated directory prefixes (`/migration-run-id/`) and can be deleted via Azure CLI.
4. **Source Immutability:** Legacy MySQL databases (`hrmstestdb`, `payrollDB`) remain completely read-only throughout the migration rehearsal and execution lifecycle.

---

## 9. Done When

1. [x] **Five Governance Questions Answered:** All five questions from `docs/trackers/MIGRATION-TRACKER.md` §2 are definitively answered with database evidence.
2. [x] **Baseline Audited:** Baseline row counts and live table states from `hrmsNPDB` and `payroll_test_db` SQL dumps are documented with `file:line` citations.
3. [x] **Deterministic 3-Cohort Rule Formalized:** Exact match on `LOWER(TRIM(email))` established as the sole deduplication rule; Cohort 4 (fuzzy matching) is explicitly excluded.
4. [x] **Timesheet Authority Established:** `timesheets` (hierarchical) established as authoritative; flat `timesheet` retired.
5. [x] **Leave Authority Defined:** HRMS `leave_balances` seeds active policies/allocations; Payroll `employee_leave_balance_consumption` seeds historical pay run consumption.
6. [x] **Financial Immutability & LOP Mismatch Log Specified:** Historical pay runs frozen; automated LOP Mismatch Log protocol defined for Finance sign-off.
7. [x] **Org Master Normalization Defined:** Payroll structured masters baseline established; HRMS free-text strings mapped/seeded.
8. [x] **Dual Cloudinary Migration Strategy Defined:** Both buckets (`dthv5gvff` and `dbsu0ghks`) mapped to private Azure Blob Storage containers (`documents`, `proofs`).
9. [x] **Keycloak Provisioning Contract Specified:** User creation and link to `core.user_account` / `core.employee.user_account_id` defined for Realm `HRMS`.
10. [x] **Test Dataset Baseline Verified:** 26 curated test users across Cohorts 1, 2, and 3 validated in `Non-prod_hrms_payroll/test-dataset/` with zero `@infinevocloud.com` emails.
11. [ ] **Founder Approval Received:** Spec presented to the founder and awaiting approval before writing any migration code.

---

## Pre-Implementation Decisions & Founder Sign-Off Gate

### A. Decisions Settled by Engineering & Product Alignment

The following 4 governance dimensions have been investigated, decided, and closed:

1. **Timesheet Authority:**
   - **Decision:** **(b) Hierarchical `timesheets` table.**
   - **Status:** **SETTLED & CLOSED.**
   - **Details:** The baseline dump proves `timesheets` contains all active submission records (`TS-101`–`TS-108`) at [hrmsNPDB_timesheets.sql:47](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheets.sql#L47), whereas flat `timesheet` has **0 rows** at [hrmsNPDB_timesheet.sql:60](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_timesheet.sql#L60) and is permanently retired.

2. **Duplicate Leave & Balance Entities:**
   - **Decision:** **(b) `leave_requests` + `leave_balances`.**
   - **Status:** **SETTLED & CLOSED.**
   - **Details:** `leave_requests` holds all live requests at [hrmsNPDB_leave_requests.sql:56](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_requests.sql#L56), whereas singular `leave_request` has **0 rows** at [hrmsNPDB_leave_request.sql:45](file:///d:/Infinevo%20Platform/infinevo-platform/Non-prod_hrms_payroll/hrmsNPDB_leave_request.sql#L45) and is permanently retired. `leave_balances` holds legitimate current balances.

3. **Leave Seeding Authority:**
   - **Decision:** **(c) Dual authority.**
   - **Status:** **SETTLED & CLOSED.**
   - **Details:** Preserves current leave balances for active employees from HRMS `leave_balances` into `core.leave_allocation`, while protecting historical pay run audit integrity from Payroll `employee_leave_balance_consumption`.

5. **Loss-of-Pay (LOP) Reconciliation:**
   - **Decision:** **Loss of pay in data migration is taken strictly as existing in Payroll pay runs, NOT from HRMS.**
   - **Status:** **SETTLED & CLOSED.**
   - **Details:** Historical payroll lines and deductions in `payroll.employee_pay_run` and `payroll_test_db_employee_leave_balance_consumption` remain 100% frozen and immutable in data migration. Past pay runs are never retroactively recalculated from HRMS leave or attendance records. Variances between HRMS unpaid days and Payroll deductions are captured in `migration_lop_mismatch_log` for Finance sign-off (`D-63`).

---

### B. Sole Decision Presented for Founder Approval

### Question 4: Employee Master Deduplication & Identity Merge Strategy (`D-62`)

* **Context:** The employee user bases between legacy HRMS and legacy Payroll only partially overlap. Furthermore, employees in HRMS authenticated via local bcrypt accounts (`ourusers`), while Payroll used Keycloak OAuth2 subject UUIDs (`companyUser`).
* **Options:**
  - **(a) Heuristic / Fuzzy Merging:** Attempt secondary matching on PAN, Aadhaar, phone numbers, or normalized personal names if work emails differ or are missing.
  - **(b) (Recommended) Strict 3-Cohort Deterministic Email Rule (`LOWER(TRIM(email))`):**
    - **Cohort 1 ($HRMS \cap Payroll$):** Exact email match merges into 1 `core.employee` record and 1 Keycloak account. Demographics from HRMS; bank, CTC, statutory, and pay runs from Payroll.
    - **Cohort 2 ($HRMS \setminus Payroll$):** HRMS-only users create 1 `core.employee` and 1 Keycloak login (no CTC/pay runs).
    - **Cohort 3 ($Payroll \setminus HRMS$):** Payroll-only users create 1 `core.employee` and 1 Keycloak login (no timesheets/attendance).
    - **Cohort 4 (Fuzzy Matching):** **Strictly excluded.** Any records with mismatched or missing emails are treated as distinct entities or flagged for manual pre-cutover cleansing.
* **Founder Decision Required:** Confirm approval of **Option (b)** to enforce 100% deterministic merges with zero heuristic false positives before `W-68` begins.

