# Feature: TDS Challan Recording & Pay Run TDS Allocation

| Field | Value |
|---|---|
| **Feature ID** | `W-89.1` · `PAYROLL-STATUTORY` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | none in `GAP_INVENTORY.md`; the frozen system had no challan entry or 24Q tracking |
| **Status** | Ready for CA / founder review |
| **Blocked by** | CA confirmation on OLTAS/ITD payment gateway auto-sync vs manual challan entry |

## 0. As built — Claude rebuild, 2026-11-25

Mohit's branch `feature/mohit-branch-2-w89-specs` was reviewed and rebuilt on `dev-claude-w89`. Where this section and the sections below disagree, this section is what the code does.

| Decision | What the code does | Why |
|---|---|---|
| Form 138, not Form 24Q | From tax year 2026-27 the salary TDS return is Form 138 (Income-tax Act 2025). The portal file follows Protean's "Form Number 138-24Q – Q1 to Q3" layout for FVU 1.2: FH 18, BH 72, CD 30, DD 45 fields. Years before 2026-27 get the CSV only | Founder 2026-11-25 ("Form 138 now"); the next real filing is Q3 2026-27, due 31 Jan 2027 |
| Q4 | The Form 138 Q4 layout (the salary detail that replaces Annexure II) is not published yet, so the Q4 portal file and the Annexure II file are refused with that reason. The Annexure II CSV works | Protean: "expected to be released soon" |
| Quarter of a pay run | The quarter of the day it was paid (`paid_on`, else `pay_date`, else the month's last day). A March salary paid on 2 April is reported in Q1 of the next year | Form 138 DD field 19 (payment date) must fall inside the quarter |
| Challan amounts | Whole rupees only. Tax, surcharge and cess are written as one "tax" amount (Form 138 does not split them); fee goes in the fee field, penalty 0 | CD fields 8–12 are whole rupees with `.00` |
| Allocation | One challan's tax + surcharge + cess is split across employees' TDS on **paid** runs of its quarter (TDS is deducted when salary is paid — D-150; an approved run that is not yet paid is not offered). Allocations of one quarter are saved one at a time (all its challans locked in id order), and marking the quarter filed waits for a save in progress. An employee cannot be covered beyond the TDS deducted on that run (all challans together), and a challan cannot be allocated beyond its tax + surcharge + cess. Re-allocating replaces; 0 removes. Interest and fee are never split, so "fully allocated" ignores them | Review F-1, F-2, F-5, F-7 |
| Split allocation | An employee's TDS on one run split across two challans gives two DD rows; the run's gross is split in proportion, so each row's amount paid is above 0 | DD 20 must be > 0; DD 24 = DD 25 |
| Uncovered TDS | Shown as its own row (deposited 0) and blocks the portal file | DD 24 must equal DD 25 |
| No PAN | `PANNOTAVBL`, remark `C`; blocks the file when TDS is under 20% of the amount paid | Remark C rule in the Form 138 spec |
| Section code | `1002` (salary, non-government employer) | Form 138 Annexure 2 |
| Filing | A quarter is marked filed with its 15-digit token number and date; while filed, its challans and allocations are locked; it can be reopened. The latest earlier filed quarter's token goes in BH field 9 and BH 52 = Y | BH 9 and 52; review F-8 |
| Employer block | Name from the company profile; address, state and PIN from the work location marked as the filing address (state mapped to Protean's codes); TAN, PAN, deductor type, email, phone, GSTIN and the responsible person's PAN, email and phone from Payroll › Settings › TDS deductor (new screen; these columns are new on `payroll.tax_deductor`, V227) | BH fields 19–45, 59, 69 |
| Problems list | The statement preview lists every reason the portal file cannot be made yet, in plain words; the Download button stays off until it is empty | So the officer fixes the data, not the file |
| Permissions | `payroll.tax.view` reads, `payroll.tax.manage` writes (V226; finance gets view). Settings-manage no longer opens challans | Review T-5 |
| Annexure II figures | Taken from Form 16: gross = gross salary, income from salary = Form 16's; section 10 = gross − section 16 − income from salary. No fallback figures: an error stops the statement | Review F-3, F-4 |
| Migrations | `payroll/V225__tds_challans.sql` (challans, allocations, `tds_return`), `core/V226__form24q_actions.sql` (actions and grants), `payroll/V227__tax_deductor_return_contacts.sql` (deductor contact columns) | Mohit's V192/V195 collided with main |
| Unconfirmed until the FVU is run | Whether the DD serial restarts per challan (the code restarts it); whether two DD rows for one employee with section 1002 under two challans are accepted; state codes written as `15`, not `015` | The published spec does not settle these |
| Not done | Correction returns; nil statements; book-entry (government) deductors; the Q4 file; checking the file with Protean's FVU (to be done before the first upload) | — |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V192__tds_challans.sql` | 1 |
| Externally testable behaviour | Payroll officer records government tax deposit challans and allocates deposited amounts to pay run TDS deductions | 1 |
| Frontend area | none (UI in `W-89.4`) | 1 |

---

## 2. Problem & Context

Under Section 200(3) of the Income Tax Act, 1961, every employer deducting tax at source (TDS) under Section 192 from salaries must deposit the tax to the Central Government using Challan ITNS 281 by the 7th of the subsequent month (30th April for March). 

Today, while the system computes TDS on each pay run (`payroll.pay_figure.tds_amount` via `W-29.2`, and stores employee records in `payroll.employee_tds_record` via `W-36.1`), **there is no table or API to record tax deposit challans (BSR code, challan serial number, tender date, tax/cess breakdown)**. Without challan records, Form 24Q Annexure I cannot map deductee TDS to government treasury deposits.

---

## 3. Scope

**In scope:**
- PostgreSQL table `payroll.tds_challan` storing ITNS 281 challans with tenant isolation (RLS).
- PostgreSQL table `payroll.tds_challan_allocation` linking challan deposits to specific pay runs and employee TDS deductions.
- REST endpoints for creating, updating, listing, and deleting unfiled challans.
- Allocation endpoint to map challan amounts across employee TDS lines for the quarter.
- Guarded by `@RequiresAction("payroll.tax.manage")`.

**Out of scope:**
- Form 24Q Annexure I generation & export (owned by `W-89.2`).
- Form 24Q Annexure II annual salary compute (owned by `W-89.3`).
- Frontend screen & dialogs (owned by `W-89.4`).
- Direct banking integration / auto-fetching challans from TRACES/OLTAS.

---

## 4. Flow

```
Payroll Officer --> POST /api/v1/payroll/compliance/challans (BSR, challan no, date, tax, cess)
                --> TdsChallanServiceImpl.createChallan --> payroll.tds_challan
Payroll Officer --> POST /api/v1/payroll/compliance/challans/{id}/allocate (payRunId)
                --> TdsChallanServiceImpl.allocateToPayRun --> payroll.tds_challan_allocation
```

---

## 5. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V192__tds_challans.sql` | Create `payroll.tds_challan` and `payroll.tds_challan_allocation` with RLS policies and indexes | yes | yes (drop tables on rollback) |

### Table: `payroll.tds_challan`
```sql
CREATE TABLE payroll.tds_challan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    financial_year VARCHAR(9) NOT NULL CHECK (financial_year ~ '^[0-9]{4}-[0-9]{4}$'),
    quarter VARCHAR(2) NOT NULL CHECK (quarter IN ('Q1', 'Q2', 'Q3', 'Q4')),
    bsr_code VARCHAR(7) NOT NULL CHECK (bsr_code ~ '^[0-9]{7}$'),
    challan_serial_no VARCHAR(5) NOT NULL CHECK (challan_serial_no ~ '^[0-9]{1,5}$'),
    tender_date DATE NOT NULL,
    tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (tax_amount >= 0),
    surcharge_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (surcharge_amount >= 0),
    cess_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (cess_amount >= 0),
    interest_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (interest_amount >= 0),
    fee_penalty_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (fee_penalty_amount >= 0),
    total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount = tax_amount + surcharge_amount + cess_amount + interest_amount + fee_penalty_amount),
    minor_head VARCHAR(3) NOT NULL DEFAULT '200' CHECK (minor_head IN ('200', '400')),
    is_filed BOOLEAN NOT NULL DEFAULT false,
    remarks VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_tds_challan_bsr_serial_date
    ON payroll.tds_challan (tenant_id, bsr_code, challan_serial_no, tender_date);

CREATE INDEX idx_tds_challan_tenant_fy_quarter
    ON payroll.tds_challan (tenant_id, financial_year, quarter);
```

### Table: `payroll.tds_challan_allocation`
```sql
CREATE TABLE payroll.tds_challan_allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    challan_id UUID NOT NULL REFERENCES payroll.tds_challan(id) ON DELETE CASCADE,
    pay_run_id UUID NOT NULL REFERENCES payroll.pay_run(id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    allocated_tax NUMERIC(14,2) NOT NULL CHECK (allocated_tax >= 0),
    allocated_cess NUMERIC(14,2) NOT NULL CHECK (allocated_cess >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_tds_challan_alloc_emp_run
    ON payroll.tds_challan_allocation (tenant_id, challan_id, pay_run_id, employee_id);
```

- [x] `tenant_id` present on both tables with RLS enabled.
- [x] Index on `tenant_id` plus lookup columns.
- [x] Money columns use `NUMERIC(14,2)`.
- [x] Every statement names `payroll.`.

---

## 6. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `payroll/compliance/TdsChallan.java` | JPA entity mapping `payroll.tds_challan` |
| Entity | `payroll/compliance/TdsChallanAllocation.java` | JPA entity mapping `payroll.tds_challan_allocation` |
| Repository | `payroll/compliance/TdsChallanRepository.java` | Spring Data JPA repo with tenant queries |
| Repository | `payroll/compliance/TdsChallanAllocationRepository.java` | Spring Data JPA repo for allocation queries |
| DTO | `payroll/compliance/TdsChallanRequest.java` | Request body record with validation constraints |
| DTO | `payroll/compliance/TdsChallanResponse.java` | Response record exposing challan details + allocation sum |
| DTO | `payroll/compliance/ChallanAllocationRequest.java` | Request body to allocate challan to pay run TDS |
| Service | `payroll/compliance/TdsChallanService.java` | Interface for challan lifecycle and allocation |
| ServiceImpl | `payroll/compliance/TdsChallanServiceImpl.java` | Implements validation, allocation, and total checks |
| Controller | `payroll/compliance/TdsChallanController.java` | REST endpoints guarded by `@RequiresAction("payroll.tax.manage")` |

### API Contract

| Method | Path | Request Body | Response Body | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/payroll/compliance/challans` | `TdsChallanRequest` | `TdsChallanResponse` (`201`) | `payroll.tax.manage` |
| `GET` | `/api/v1/payroll/compliance/challans` | query params `financialYear`, `quarter` | `List<TdsChallanResponse>` | `payroll.tax.manage` |
| `GET` | `/api/v1/payroll/compliance/challans/{id}` | — | `TdsChallanResponse` | `payroll.tax.manage` |
| `PUT` | `/api/v1/payroll/compliance/challans/{id}` | `TdsChallanRequest` | `TdsChallanResponse` | `payroll.tax.manage` |
| `DELETE` | `/api/v1/payroll/compliance/challans/{id}` | — | `204 No Content` | `payroll.tax.manage` |
| `POST` | `/api/v1/payroll/compliance/challans/{id}/allocate` | `ChallanAllocationRequest` | `TdsChallanResponse` | `payroll.tax.manage` |

---

## 7. Open Questions for CA Review

1. **Minor Head 200 vs 400:** Should minor head default to `200` (TDS Payable by Taxpayer) for regular payroll filings, with `400` (Regular Assessment) selectable only as an override?
2. **Allocation Splitting:** When allocating a single monthly ITNS 281 challan across employees in a pay run, should the allocation auto-pro-rate tax and cess according to each employee's `tds_amount` in `payroll.pay_figure`?

---

## 8. Verification

```bash
mvnw clean test -pl migration,payroll -am -Dtest="*TdsChallan*"
```
- Integration test `TdsChallanIT`: tests creating a challan, validating BSR code (7 digits), allocating against pay run figures, preventing edits once marked `is_filed = true`.
- RLS test `TdsChallanRlsIT`: tests tenant isolation when querying challans.
