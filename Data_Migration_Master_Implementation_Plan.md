# Infinevo Cloud HCM — Stream I Data Migration Master Implementation Plan
> **Revision:** 3.0 (Synthesized & Execution-Ready)  
> **Date:** 2026-10-15  
> **Owners:** KarmaveerM (Data/Infra Lead) & Mohit (QA/Migration Lead)  
> **Target Platform:** PostgreSQL 16 (`core`, `hrms`, `payroll`, `reference`) + Keycloak Realm `infinevo` + Azure Blob Storage (`infinevo-documents`) + Azure Front Door Standard (`D-51`)  
> **Source Systems (Frozen 2026-09-13 Snapshot):**  
> 1. Legacy HRMS: MySQL (`hrmstestdb` on `64.227.128.119:3306`) + Cloudinary Bucket `dthv5gvff` + Custom JWT  
> 2. Legacy Payroll: MySQL (`payroll_test_db` on `64.227.128.119:3306`) + Cloudinary Bucket `dbsu0ghks` + Keycloak Realm `HRMS`  
> **Applicable Tickets:** `W-67` (Governance), `W-68.1` (Extract/Staging), `W-68.2` (Transform/Merge), `W-68.3` (Load/Restartability), `W-69` (Documents), `W-70` (Reconciliation), `W-71` (Rehearsals), `W-72` (Cutover)  
> **Status:** Awaiting Founder Approval to Begin Phase 1  

---

## Executive Implementation Matrix

| Phase | Milestone / Deliverable | Key Files Involved | Dependencies | Acceptance Gate & Verification |
|---|---|---|---|---|
| **Phase 1** | **Flyway Schema Alignment (`V177`)** | Renumber `V175` $\rightarrow$ [`V177__migration_ledger.sql`](file:///d:/Infinevo%20Platform/infinevo-platform/code/backend/migration/src/main/resources/db/migration/core/V177__migration_ledger.sql) | `origin/main` at `V176` | `./mvnw test -pl code/backend/migration` passes; `core.migration_ledger` and `core.lop_mismatch_log` created with RLS. |
| **Phase 2** | **CLI Orchestrator (`run_migration.py`)** | New: [`code/migration/run_migration.py`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/run_migration.py) | Phase 1 | Single CLI command executes all steps; updates `core.migration_ledger` per step; supports `--dry-run` and `--step`. |
| **Phase 3** | **Keycloak & Credential Hardening** | [`code/migration/loaders/load_keycloak.py`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/loaders/load_keycloak.py), [`config.example.yaml`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/config/config.example.yaml) | Phase 2 | Zero hardcoded passwords; environment-injected secrets; Cohort 2 linked with `core.employee_invitation` (`W-24.2`). |
| **Phase 4** | **Azure Blob Streaming (W-69)** | [`code/migration/loaders/load_documents.py`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/loaders/load_documents.py), `code/migration/documents/blob_streamer.py` | Phase 2 | Dual-mode: in-memory streaming to Azure Blob with real SHA-256 and byte sizes; local metadata-only fallback. |
| **Phase 5** | **Staging & Git Hygiene** | `.gitignore`, [`code/migration/transformers/merge_employees.py`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/transformers/merge_employees.py) | Phase 2 | Staging JSONs untracked from git; Parquet/DuckDB caching enabled; merge runtime $\le$ 30 seconds. |
| **Phase 6** | **Reconciliation & Rehearsals (W-71)** | [`financial_reconciliation.py`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/reconciliation/financial_reconciliation.py), [`lop_mismatch_generator.py`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/reconciliation/lop_mismatch_generator.py) | Phases 1–5 | **3 consecutive clean rehearsals** (<45 min each); **0.00 INR financial variance**; Finance sign-off hash generated. |
| **Phase 7** | **Cutover Execution (W-72)** | Cutover Runbook & Rollback Script | Phase 6 | 60-minute cutover window executed; Front Door routing flipped; tested sub-20-minute PITR rollback available. |

---

## 1. Flyway Alignment & Database Governance (Phase 1)

### 1.1 Version Renumbering (`V177`)
On `origin/main`, the Flyway migration sequence is already populated through `V176`:
- `payroll/V175__default_reimbursement_components.sql` (merged in `50ea654d`)
- `core/V176__manager_approval_delegate.sql` (merged in `origin/main`)

The migration ledger script from `core-migration-non-prod-data` was drafted as `V175__migration_ledger.sql`. To eliminate collisions and maintain strict monotonic Flyway versioning, it is renamed to:
**`code/backend/migration/src/main/resources/db/migration/core/V177__migration_ledger.sql`**.

### 1.2 Schema Placement (`core` Schema)
The tables reside in the `core` schema, directly utilizing existing multi-tenant connection pooling, role grants (`app_user`, `worker_user`, `migration_user`), and Row Level Security:

```sql
-- V177__migration_ledger.sql
-- Schema: core
-- Purpose: W-67 / W-68 / W-70 Restartability Ledger & Loss-of-Pay Mismatch Audit Log

-- 1. Migration Execution Ledger (W-68.3 restartability & idempotency)
CREATE TABLE core.migration_ledger (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    step_name           VARCHAR(64) NOT NULL,
    records_processed   INT NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL, -- PENDING, IN_PROGRESS, COMPLETED, FAILED
    started_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMPTZ NULL,
    error_message       TEXT NULL
);

CREATE INDEX idx_migration_ledger_tenant_step 
    ON core.migration_ledger (tenant_id, step_name);

ALTER TABLE core.migration_ledger ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.migration_ledger
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

COMMENT ON TABLE core.migration_ledger IS
    'W-68.3: tracks migration step execution state and processed row counts for safe crash recovery';

-- 2. Loss-of-Pay Mismatch Log (W-70 reconciliation & Finance sign-off)
CREATE TABLE core.lop_mismatch_log (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE,
    employee_number             VARCHAR(64) NOT NULL,
    work_email                  VARCHAR(255) NOT NULL,
    pay_period                  VARCHAR(7) NOT NULL CHECK (pay_period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    hrms_unpaid_days            NUMERIC(4,1) NOT NULL,
    payroll_lop_days            NUMERIC(4,1) NOT NULL,
    variance_days               NUMERIC(4,1) NOT NULL,
    payroll_processed_deduction NUMERIC(12,2) NOT NULL,
    status                      VARCHAR(32) NOT NULL DEFAULT 'PENDING_FINANCE_SIGN_OFF',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_lop_mismatch_status CHECK (status IN ('PENDING_FINANCE_SIGN_OFF', 'APPROVED_BY_FINANCE', 'ADJUSTED'))
);

CREATE INDEX idx_lop_mismatch_log_tenant_period 
    ON core.lop_mismatch_log (tenant_id, pay_period);

ALTER TABLE core.lop_mismatch_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.lop_mismatch_log
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

COMMENT ON TABLE core.lop_mismatch_log IS
    'W-70: tracks variances between HRMS unpaid leave days and Payroll historical LOP deductions for Finance sign-off';
```

---

## 2. Master CLI Orchestrator Architecture (`run_migration.py`) (Phase 2)

### 2.1 Orchestrator Design
The migration runner consolidates all individual extraction, merge, loading, and audit tasks into a single executable CLI script at [`code/migration/run_migration.py`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/run_migration.py):

```
                                  run_migration.py
                                         │
 ┌───────────────────────────────────────┴───────────────────────────────────────┐
 │                                                                               │
 ▼                                                                               ▼
[Step 0: Pre-Flight]                                                    [Ledger Recorder]
 - DB Connectivity                                                       - Records step starts
 - Flyway V177 Check                                                     - Tracks row counts
 - Keycloak Reachability                                                 - Logs failures/retries
 - Key Vault / .env config                                               - RLS isolation
 │
 ▼
[Step 1: Extract (W-68.1)] ────────► extract_hrms.py & extract_payroll.py (READ ONLY)
 │
 ▼
[Step 2: Merge & Transform (W-68.2)] ► merge_employees.py (Deterministic UUIDs, 3 Cohorts)
 │
 ▼
[Step 3: Core Database Load (W-68.3)] ► load_postgres.py (Tenants, Employees, Satellites)
 │
 ▼
[Step 4: Leaves & Calendars] ───────► load_leave.py (Holidays, Calendars, Leave Policies)
 │
 ▼
[Step 5: Historical Payruns] ───────► load_payruns.py (Payrun headers & line items)
 │
 ▼
[Step 6: Identity Provisioning] ────► load_keycloak.py (Realm infinevo + W-24.2 Invites)
 │
 ▼
[Step 7: Documents (W-69)] ─────────► load_documents.py (Azure Blob Streamer / Metadata)
 │
 ▼
[Step 8: Financial Audit (W-70)] ───► financial_reconciliation.py (0.00 INR Delta Proof)
 │
 ▼
[Step 9: LOP Variance Log] ─────────► lop_mismatch_generator.py (core.lop_mismatch_log)
```

### 2.2 CLI Capabilities & Interface
```bash
# Full execution end-to-end
python code/migration/run_migration.py --all

# Run specific step with dry-run verification
python code/migration/run_migration.py --step=reconciliation --dry-run

# Resume execution from a failed step
python code/migration/run_migration.py --resume-from=payruns

# Run against single tenant
python code/migration/run_migration.py --tenant=a0000000-0000-0000-0000-000000000001
```

---

## 3. Extraction & Staging Pipeline (W-68.1)

### 3.1 Connection Security & Source Isolation
- Target Host: `64.227.128.119:3306`
- Databases: `hrmstestdb` (HRMS) and `payroll_test_db` (Payroll)
- User: `ro_migration_user`
- Hard Safety Rule: Extractor connects exclusively with `SET SESSION TRANSACTION READ ONLY`. No table locks, no source DDL, zero write queries.

### 3.2 Extracted Dataset Catalog
- **HRMS Entities (22 tables):** `ourusers`, `employee`, `personal`, `contact`, `identification`, `work`, `timesheets`, `timesheet`, `day_entry`, `task_entry`, `projects`, `tasks`, `assignments`, `leave_requests`, `leave_balances`, `leave_types`, `attendance`, `clock_sessions`, `employee_document`, `holidays`, `user_roles`, `user_action_mapping`.
- **Payroll Entities (10 tables):** `organization`, `workLocations`, `department`, `designation`, `employee`, `companyUser`, `payruns`, `employee_payruns`, `employee_leave_balance_consumption`, `employee_poi_document`.

---

## 4. Deterministic 3-Cohort Merge Engine (W-68.2)

### 4.1 Multi-Org to Multi-Tenant Mapping
Uses dynamic configuration in [`code/migration/config/org_mapping.json`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/config/org_mapping.json):
```json
{
  "1001": {
    "tenant_id": "a0000000-0000-0000-0000-000000000001",
    "tenant_name": "InfiNevo India Pvt Ltd"
  },
  "1002": {
    "tenant_id": "a0000000-0000-0000-0000-000000000002",
    "tenant_name": "InfiNevo Cloud Services"
  }
}
```
*Guarantees zero hardcoded single-tenant assumptions.*

### 4.2 Merge Algorithm & Data Sanitization Rules
Incorporates the empirical production fixes from [`merge_employees.py`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/transformers/merge_employees.py):
1. **Deterministic Identity:** Target UUID generated via `uuid5(NAMESPACE, f"{tenant_id}:{email_or_emp_number}")`.
2. **Email Sanitization & Duplicate Quarantine:** 
   - Empty/blank emails quarantined as `EMPTY_EMAIL`.
   - Multiple legacy employee records sharing the exact same email inside the same source organization are quarantined into `stg_quarantined_duplicates.json` with code `DUPLICATE_SOURCE_EMAIL`.
3. **Date Anomaly Sanitization:**
   - Detects and clears future birthdates (year $> 2010$).
   - Detects and clears invalid chronology where $\text{DOB} \ge \text{DOJ}$.
4. **MySQL Type Coercion:** Handles MySQL `BIT` literals (`b'\x01'`, `b'\x00'`) converting cleanly to boolean `True`/`False`.
5. **3-Cohort Entity Rules:**
   - **Cohort 1 ($HRMS \cap Payroll$):** HRMS supplies Demographics (DOB, Gender, DOJ, Personal Contacts); Payroll supplies Statutory (PAN, UAN, PF, ESI), Bank Accounts, CTC Structure, and Payruns.
   - **Cohort 2 ($HRMS \setminus Payroll$):** 100% of HRMS demographics preserved; bank accounts copied if available; flagged for Keycloak invitation email (`W-24.2`).
   - **Cohort 3 ($Payroll \setminus HRMS$):** 100% of Payroll statutory profile, CTC, and payruns preserved; timesheets initialized empty.

---

## 5. Target Loading & Keycloak Security Hardening (W-68.3)

### 5.1 Topological Loading Sequence
To guarantee zero foreign key constraint violations, loading executes strictly in topological order:
1. `core.tenant` (Seeds mapped tenants from `org_mapping.json`)
2. `core.user_account` & `core.user_tenant` (Auth profiles)
3. `core.employee` (Root employee entity with tenant-scoped `employee_number`)
4. Satellites:
   - `core.employee_personal` (`V015`)
   - `core.employee_contact` (`V016`)
   - `core.employee_identification` (`V017`)
   - `core.employee_bank` (`V019`)
   - `payroll.employee_statutory_profile` (`V050`)
5. Calendars & Leaves:
   - `core.holiday_calendar` & `core.holiday`
   - `core.leave_type` & `core.leave_policy`
   - `core.leave_allocation` & `core.leave_request`
6. Payruns:
   - `payroll.payrun`
   - `payroll.employee_payrun`
   - `payroll.employee_payrun_line`
7. Documents:
   - `core.document` (`V037`)

### 5.2 Keycloak Credential & Invitation Hardening
- **Password Security:** Remove hardcoded `"Welcome@Infinevo2026!"` from [`load_keycloak.py:32`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/loaders/load_keycloak.py#L32). In production, generate per-user cryptographic temporary tokens (`secrets.token_urlsafe(24)`).
- **Cohort 2 Invitation Integration:** For HRMS-only users, create the Keycloak account with `UPDATE_PASSWORD` action and populate `core.employee_invitation` (`V118`). On cutover, trigger standard onboarding invitation links.
- **Service Account Access:** Target Keycloak Admin API authenticated via client credentials or environment-injected admin secrets (`KEYCLOAK_ADMIN_PASSWORD`).

---

## 6. Document Migration & Azure Blob Streaming (W-69)

### 6.1 Path Layout & Zero Public SAS Links
- Container: `infinevo-documents` in private Azure Storage Account `stinfinevo{env}`.
- Relative Path: `{tenantId}/{employeeId}/EMPLOYEE_DOCUMENT/{documentId}`.
- Zero public URLs or SAS links stored in PostgreSQL. File downloads are authenticated through the Spring Boot API (`DocumentController`).

### 6.2 Dual-Mode Engine
1. **Live Stream Mode (`--stream-blobs`):**
   - In-memory streaming from Cloudinary URL (`urllib`) to Azure Blob Storage container (`azure-storage-blob`).
   - Computes SHA-256 hash on-the-fly (`hashlib.sha256()`).
   - Records live byte size and true checksum in `core.document`.
2. **Metadata Fallback Mode (`--metadata-only`):**
   - Populates `core.document` with deterministic placeholder checksums `sha256(legacy_id + file_name)` and `size_bytes = 0`.
   - Used for rapid local development, offline rehearsal, and CI test pipelines.

---

## 7. Financial Reconciliation & LOP Variance Governance (W-70)

### 7.1 Mathematical 0.00 INR Delta Proof
Reconciliation in [`financial_reconciliation.py`](file:///d:/Infinevo%20Platform/infinevo-platform/code/migration/reconciliation/financial_reconciliation.py) verifies historical payroll accuracy using Python `Decimal` at scale 4:
- Line-by-line gross earnings, deductions, and net pay comparisons.
- Sums of payrun headers equal sums of child employee payrun lines.
- **Assertion Gate:** If $\text{variance} > 0.00\text{ INR}$, the migration halts immediately with fatal exit status.

### 7.2 LOP Mismatch Log & Pre-Cutover Sign-Off
- Compares HRMS approved unpaid leave days vs Payroll processed Loss-of-Pay deduction days for all historical pay periods.
- Records all differences in `core.lop_mismatch_log`.
- **Finance Sign-Off Rule:** Finance reviews and signs off on the LOP Mismatch Log during the **Rehearsal Phase (W-71)**. The signed SHA-256 hash of this table is verified during cutover night.

---

## 8. Rehearsal Strategy & Cutover Runbook (W-71 / W-72)

### 8.1 "3 Clean Rehearsals" Rule (W-71)
Live cutover will not be authorized until 3 consecutive complete rehearsals succeed against the non-prod staging dataset meeting:
1. Zero unhandled exceptions or script crashes.
2. Exact 0.00 INR financial variance across all historical pay periods.
3. Total execution runtime $< 45$ minutes.
4. Verified database restore drill via `infra/azure/dr/restore-drill.sh` (`W-62`).

### 8.2 Live Cutover Night Runbook (< 60 Minutes) (W-72)

```
 T-120 min   Pre-cutover sync; take manual PITR snapshot of Azure Flexible Server.
     │
 T-000 min ──► [Freeze Window Start]
     │         - Deploy Nginx 503 maintenance page on legacy web tier.
     │         - Legacy databases remain completely untouched.
     │
 T+005 min ──► [Delta Extraction]
     │         - python run_migration.py --step=extract
     │         - Verify source row counts match staging dumps.
     │
 T+015 min ──► [Merge Transformation]
     │         - python run_migration.py --step=merge
     │         - Confirm 0 new quarantine records.
     │
 T+025 min ──► [Blob Streaming]
     │         - python run_migration.py --step=documents --stream-blobs
     │         - Complete asset sync from Cloudinary to Azure Blob Storage.
     │
 T+035 min ──► [Database & Keycloak Load]
     │         - python run_migration.py --step=load
     │         - Load Postgres in topological order; provision Keycloak realm infinevo.
     │
 T+042 min ──► [Automated Financial Reconciliation]
     │         - python run_migration.py --step=reconcile
     │         - Automated assertion: 0.00 INR gross/net delta.
     │         - Verify pre-signed Finance LOP sign-off hash.
     │
 T+048 min ──► [Azure Front Door Origin Switch]
     │         - Update Azure Front Door Standard (D-51) origin routing to new ACA backend.
     │         - Verify SSL certificates and health probes.
     │
 T+055 min ──► [Smoke Tests]
     │         - Verify logins for Cohort 1, 2, and 3 test users.
     │         - Verify payslip viewing, timesheet entries, leave balances.
     │
 T+060 min ──► [CUTOVER COMPLETE]
               - Remove maintenance banner; open platform to production traffic.
```

### 8.3 Disaster Recovery & Rollback Plan (< 20 Minutes)
If any blocking defect or reconciliation discrepancy occurs prior to $T+48$ min:
1. **Database:** Abort execution; trigger Azure PostgreSQL Flexible Server Point-in-Time Restore (PITR) to pre-cutover snapshot (`restore-drill.sh`).
2. **Keycloak:** Execute purge script deleting all users tagged with `migration_batch_id`.
3. **Traffic:** Re-enable legacy web tier Nginx routing; lift 503 banner. Legacy applications resume operation immediately.

---

## 9. Immediate Action Items & Execution Order

Following founder approval:
1. **[Karmaveer]** Renumber `code/backend/migration/src/main/resources/db/migration/core/V175__migration_ledger.sql` to `V177__migration_ledger.sql`. Run Maven compilation and Flyway verification.
2. **[Karmaveer]** Create `code/migration/run_migration.py` wrapping the modular loaders into an integrated CLI runner.
3. **[Karmaveer]** Sanitize `load_keycloak.py` credentials and add Azure Blob streaming module to `load_documents.py`.
4. **[Mohit]** Execute full dry-run of `run_migration.py` against non-prod staging data to complete Rehearsal 1.
5. **[Mohit]** Review `core.lop_mismatch_log` and generate Finance pre-signoff hash.

---
*End of Master Implementation Plan — Awaiting Founder Approval to Begin Phase 1.*
