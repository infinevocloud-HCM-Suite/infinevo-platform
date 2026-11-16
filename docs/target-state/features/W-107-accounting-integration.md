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
| Flyway migration | `V196__accounting_integration.sql` | 1 |
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

## 5. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V196__accounting_integration.sql` | Create `payroll.accounting_ledger_mapping` with RLS | yes | yes |

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
