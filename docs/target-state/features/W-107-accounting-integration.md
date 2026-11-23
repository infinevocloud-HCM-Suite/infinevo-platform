# Feature: Accounting Integration — Tally & Zoho Books Journal Export

| Field | Value |
|---|---|
| **Feature ID** | `W-107` · `PAYROLL-ACCOUNTING` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration`, `code/frontend` (`src/payroll/settings/accounting`) |
| **Related gaps** | none in `GAP_INVENTORY.md`; journal vouchers currently created by hand |
| **Status** | Ready for CA / founder review |
| **Blocked by** | CA review of standard chart-of-accounts liability heads |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `payroll/V200__accounting_integration.sql` | 1 |
| Externally testable behaviour | Generates balanced double-entry payroll journal vouchers from completed pay runs and exports them in Tally XML and Zoho Books CSV formats | 1 |
| Frontend area | `payroll/settings/accounting` | 1 |

---

## 2. Problem & Context

At the end of every monthly pay run, finance teams must record a compound journal voucher in their accounting software (primarily TallyPrime or Zoho Books) to reflect:
- **Debit:** Gross Salary Expense, Employer EPF Expense, Employer ESI Expense.
- **Credit:** Net Salaries Payable, TDS Payable, Employee + Employer EPF Payable, ESI Payable, Professional Tax Payable.

Currently, finance officers compute these ledger summaries manually, which is error-prone, time-consuming, and delays monthly book closure.

---

## 3. Scope

**In scope:**
- PostgreSQL table `payroll.accounting_ledger_mapping` storing tenant-specific mapping between Infinevo pay heads and the company's Chart of Accounts ledger names.
- Journal Voucher generation engine for closed pay runs (`APPROVED` or `PAID`):
  - Ensures mathematical balance: $\sum \text{Debits} == \sum \text{Credits}$.
  - Supports standard Indian accounting heads:
    - `Salaries Expense` (Dr)
    - `Employer EPF Contribution` (Dr)
    - `Employer ESI Contribution` (Dr)
    - `Net Salary Payable` (Cr)
    - `TDS on Salary Payable` (Cr)
    - `EPF Payable (A/C 1, 2, 10, 21, 22)` (Cr)
    - `ESI Payable` (Cr)
    - `Professional Tax Payable` (Cr)
- Dual export formats:
  - **Tally XML:** Native TallyPrime / Tally.ERP 9 XML importable directly via *Import Data › Vouchers*.
  - **Zoho Books CSV:** Compatible with Zoho Books *Manual Journals › Import CSV*.
- Frontend screen under Payroll Settings to map ledger names and download monthly journal files.

**Out of scope:**
- Direct REST API sync via Zoho Books OAuth / Tally ODBC server (deferred to a cloud connector follow-up).

---

## 4. Flow

```
Finance Officer --> Maps ledger names in Payroll › Settings › Accounting
                --> Selects closed Pay Run in Payroll › Pay Runs › Journal Voucher
                --> Inspects balanced Debit vs Credit summary table
                --> Clicks "Export Tally XML" or "Export Zoho Books CSV"
```

---

---

## 4a. REST API Contract

All endpoints are scoped to the authenticated caller's tenant (`TenantContext`).

### 1. `GET /api/v1/payroll/accounting/ledger-mappings`
- **Permission**: `payroll.salary.read`
- **Response**: `200 OK`
```json
[
  { "id": "uuid", "headType": "GROSS_SALARY", "ledgerName": "Salaries Expense", "ledgerCode": "EXP-001" },
  { "id": "uuid", "headType": "NET_SALARY", "ledgerName": "Net Salaries Payable", "ledgerCode": "LIA-001" },
  { "id": "uuid", "headType": "EPF_PAYABLE", "ledgerName": "EPF Payable", "ledgerCode": "LIA-002" },
  { "id": "uuid", "headType": "ESI_PAYABLE", "ledgerName": "ESI Payable", "ledgerCode": "LIA-003" },
  { "id": "uuid", "headType": "PT_PAYABLE", "ledgerName": "Professional Tax Payable", "ledgerCode": "LIA-004" },
  { "id": "uuid", "headType": "TDS_PAYABLE", "ledgerName": "TDS Payable", "ledgerCode": "LIA-005" }
]
```

### 2. `PUT /api/v1/payroll/accounting/ledger-mappings`
- **Permission**: `payroll.salary.manage`
- **Request Body**:
```json
{
  "mappings": [
    { "headType": "GROSS_SALARY", "ledgerName": "Salaries Expense", "ledgerCode": "EXP-001" },
    { "headType": "NET_SALARY", "ledgerName": "Net Salaries Payable", "ledgerCode": "LIA-001" }
  ]
}
```
- **Response**: `200 OK`

### 3. `GET /api/v1/payroll/runs/{id}/journal-voucher`
- **Permission**: `payroll.salary.read`
- **Pre-condition**: Pay run status is `APPROVED` or `PAID`.
- **Response**: `200 OK`
```json
{
  "payRunId": "uuid",
  "period": "2026-10",
  "voucherDate": "2026-10-31",
  "totalDebit": "500000.00",
  "totalCredit": "500000.00",
  "isBalanced": true,
  "entries": [
    { "headType": "GROSS_SALARY", "ledgerName": "Salaries Expense", "entryType": "DEBIT", "amount": "500000.00" },
    { "headType": "NET_SALARY", "ledgerName": "Net Salaries Payable", "entryType": "CREDIT", "amount": "420000.00" },
    { "headType": "TDS_PAYABLE", "ledgerName": "TDS on Salary", "entryType": "CREDIT", "amount": "50000.00" },
    { "headType": "EPF_PAYABLE", "ledgerName": "EPF Payable", "entryType": "CREDIT", "amount": "30000.00" }
  ]
}
```

### 4. `GET /api/v1/payroll/runs/{id}/journal-voucher/export?format={tally|zoho}`
- **Permission**: `payroll.salary.read`
- **Query params**: `format` (`tally` or `zoho`)
- **Response**:
  - For `format=tally`: `Content-Type: application/xml`, file download `journal_voucher_2026_10.xml`
  - For `format=zoho`: `Content-Type: text/csv; charset=UTF-8`, file download `journal_voucher_2026_10.csv`

---

## 5. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V200__accounting_integration.sql` | Create `payroll.accounting_ledger_mapping` with RLS | yes | yes |

### Table: `payroll.accounting_ledger_mapping`
```sql
CREATE TABLE payroll.accounting_ledger_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    head_type VARCHAR(50) NOT NULL, -- GROSS_SALARY, EPF_EMPLOYER, ESI_EMPLOYER, NET_SALARY, TDS_PAYABLE, EPF_PAYABLE, ESI_PAYABLE, PT_PAYABLE
    ledger_name VARCHAR(150) NOT NULL,
    ledger_code VARCHAR(50) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_ledger_mapping_head UNIQUE (tenant_id, head_type)
);

ALTER TABLE payroll.accounting_ledger_mapping ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.accounting_ledger_mapping
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );
```

---

## 6. Backend & Frontend changes

| Layer | File | Change |
|---|---|---|
| Service | `payroll/accounting/AccountingJournalService.java` | Builds double-entry voucher from `PayRun` lines |
| Formatter | `payroll/accounting/TallyXmlFormatter.java` | Formats voucher into Tally envelope `<ENVELOPE><BODY><DATA><TALLYMESSAGE>...` |
| Formatter | `payroll/accounting/ZohoBooksCsvFormatter.java` | Formats voucher into Zoho Books manual journal CSV |
| Controller | `payroll/accounting/AccountingController.java` | REST endpoints for ledger mapping CRUD and voucher exports |
| Frontend | `src/payroll/settings/AccountingMappingPage.jsx` | UI to configure ledger names per pay head |
| Frontend | `src/payroll/payrun/JournalVoucherModal.jsx` | Modal in Pay Run page to preview journal and trigger downloads |

---

## 7. Open Questions for CA Review

1. **EPF Breakdown:** Does the CA prefer a single consolidated `EPF Payable` ledger, or individual sub-ledgers for Employee PF (A/C 1), Employer EPS (A/C 10), and PF Admin Charges (A/C 2)? *(Standard default: Consolidated EPF Payable with breakdown in narration)*.
2. **Cost Center Tagging:** Should the journal voucher optionally tag Department / Cost Center per employee line?

---

## 8. Verification

```bash
mvnw clean test -pl migration,payroll -am -Dtest="*Accounting*"
```
- Integration test `AccountingJournalExportIT`: tests generating journal voucher for an approved pay run, verifying sum(debit) == sum(credit), and validating Tally XML and Zoho Books CSV outputs.
