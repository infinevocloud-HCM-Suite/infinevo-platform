# Feature: Full and Final (F&F) Settlement Computation & Lifecycle

| Field | Value |
|---|---|
| **Feature ID** | `W-90.3` · `PAYROLL-SETTLEMENT` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready for CA / founder review |
| **Blocked by** | `W-90.1`, `W-90.2`, `W-91` |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V195__fnf_settlement.sql` (renumbered from V193 to sequence after V193 Leave Encashment and V194 Notice Period) | 1 |
| Externally testable behaviour | Aggregates final salary, leave encashment, gratuity, notice pay, deductions, and executes final exit settlement | 1 |
| Frontend area | none (UI in `W-90.4`) | 1 |

---

## 2. Problem & Context

When an employee exits, HR and Payroll must compile a master Full and Final (F&F) settlement statement consolidating:
- Pro-rated salary for days worked in exit month.
- Leave encashment (`W-90.1`).
- Gratuity (`W-91`).
- Notice pay addition or deduction (`W-90.2`).
- Reimbursements, pending loan balances, and recoveries.
- Statutory deductions (EPF, ESI, PT, and Final TDS recalculation).

Currently, there is no consolidated settlement entity. Payroll officers have to run off-cycle runs manually with ad-hoc adjustments, creating compliance and audit risk.

---

## 3. Scope

**In scope:**
- PostgreSQL table `payroll.fnf_settlement` and `payroll.fnf_settlement_item` with RLS.
- Settlement computation service:
  $$\text{Net F\&F Payable} = \text{Total Settlement Earnings} - \text{Total Settlement Deductions}$$
- Integration with sub-settlement modules:
  - Automatically loads and links `LeaveEncashmentSettlement` (`W-90.1`).
  - Automatically loads and links `GratuitySettlement` (`W-91`).
  - Automatically loads and links `NoticePeriodSettlement` (`W-90.2`).
- Final statutory deductions:
  - EPF & ESI computed on pro-rated exit salary.
  - Professional tax applied for the exit month.
  - Final TDS computed considering annual income, exemptions (10(10), 10(10AA)), and tax deducted to date.
- Employee status transition upon settlement completion (`PAID`):
  - Sets `core.employee.status = 'TERMINATED'` and records exit date.
- REST API for F&F lifecycle: compute draft, adjust line items, approve, lock, and pay.

**Out of scope:**
- F&F frontend screens (handled in `W-90.4`).
- Physical asset collection workflow (handled in `W-103`).

---

## 4. Flow

```
Payroll Officer --> POST /api/v1/payroll/fnf/initiate?employeeId={id}&exitDate={date}
                --> FnfSettlementServiceImpl.initiateSettlement
                --> Combines Pro-rated Pay + Leave Encashment + Gratuity + Notice Pay + Deductions
                --> Creates Draft F&F Settlement Statement
Payroll Officer --> POST /api/v1/payroll/fnf/{id}/approve
Payroll Officer --> POST /api/v1/payroll/fnf/{id}/pay
                --> Updates employee status to TERMINATED in core.employee
```

---

## 5. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V195__fnf_settlement.sql` | Create `payroll.fnf_settlement` with RLS | yes | yes |

### Table: `payroll.fnf_settlement`
```sql
CREATE TABLE payroll.fnf_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    resignation_date DATE NOT NULL,
    exit_date DATE NOT NULL,
    exit_reason VARCHAR(50) NOT NULL, -- RESIGNATION, RETIREMENT, TERMINATION, ABSCONDING
    working_days_in_exit_month INTEGER NOT NULL CHECK (working_days_in_exit_month >= 0),
    exit_month_salary NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    leave_encashment_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    gratuity_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    notice_pay_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    other_earnings NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    gross_earnings NUMERIC(14,2) NOT NULL,
    notice_recovery_amount NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    epf_deduction NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    esi_deduction NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    pt_deduction NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    tds_deduction NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    other_recoveries NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    gross_deductions NUMERIC(14,2) NOT NULL,
    net_payable NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'UNDER_REVIEW', 'APPROVED', 'PAID', 'CANCELLED')),
    approved_by VARCHAR(100) NULL,
    approved_at TIMESTAMPTZ NULL,
    paid_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_fnf_settlement_employee
    ON payroll.fnf_settlement (tenant_id, employee_id)
    WHERE status <> 'CANCELLED';
```

---

## 6. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `payroll/settlement/FnfSettlement.java` | JPA entity mapping `payroll.fnf_settlement` |
| Repository | `payroll/settlement/FnfSettlementRepository.java` | Spring Data JPA repo with tenant queries |
| DTO | `payroll/settlement/FnfInitiateRequest.java` | DTO: `employeeId`, `exitDate`, `exitReason` |
| DTO | `payroll/settlement/FnfSettlementResponse.java` | Full DTO with all earnings, deductions, and net figures |
| Service | `payroll/settlement/FnfSettlementService.java` | Orchestrates salary pro-rating, leave, gratuity, notice, and tax |
| ServiceImpl | `payroll/settlement/FnfSettlementServiceImpl.java` | Implements aggregation, final TDS compute, and employee status update |
| Controller | `payroll/settlement/FnfSettlementController.java` | REST endpoints guarded by `@RequiresAction("payroll.salary.manage")` (initiate, approve, pay) and `@RequiresAction("payroll.salary.read")` (get/list) matching seeded platform roles |

### API Contract

| Method | Path | Request Body | Response Body | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/payroll/fnf/initiate` | `FnfInitiateRequest` | `FnfSettlementResponse` (`201`) | `payroll.salary.manage` |
| `GET` | `/api/v1/payroll/fnf/{id}` | — | `FnfSettlementResponse` | `payroll.salary.read` |
| `POST` | `/api/v1/payroll/fnf/{id}/approve` | — | `FnfSettlementResponse` | `payroll.salary.manage` |
| `POST` | `/api/v1/payroll/fnf/{id}/pay` | — | `FnfSettlementResponse` | `payroll.salary.manage` |

---

## 7. Open Questions for CA Review

1. **Negative Settlement Balance:** If recoveries and notice pay shortfall exceed gross earnings (creating a negative net balance where employee owes company), should the settlement record as ₹0 payable with an outstanding recovery notice, or block payment?
2. **Timing of Employee Termination:** Should `core.employee.status` transition to `TERMINATED` immediately upon settlement approval, or upon recording the final payment? *(Standard: upon settlement payment)*.

---

## 8. Verification

```bash
mvnw clean test -pl migration,payroll -am -Dtest="*FnfSettlement*"
```
- Integration test `FnfSettlementLifecycleIT`: tests initiate -> approve -> pay lifecycle, validating total net math and verifying employee status changes to `TERMINATED`.
