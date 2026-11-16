# Feature: Gratuity Eligibility, Calculation and Payout

| Field | Value |
|---|---|
| **Feature ID** | `W-91` · `PAYROLL-STATUTORY` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | none in `GAP_INVENTORY.md`; the frozen system had no gratuity calculation |
| **Status** | Ready for CA / founder review |
| **Blocked by** | CA confirmation on 26-day basis vs calendar day basis for non-covered establishments |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V190__gratuity.sql` | 1 |
| Externally testable behaviour | Calculates gratuity payout, applies the statutory ceiling and Section 10(10) tax exemption, and records gratuity settlements | 1 |
| Frontend area | none (UI in `W-90.4`) | 1 |

---

## 2. Problem & Context

Under the *Payment of Gratuity Act, 1972*, an employee who has rendered continuous service of at least 5 years is entitled to gratuity upon resignation, retirement, or termination (the 5-year condition is waived in the case of death or disablement). 

Currently, Infinevo has no gratuity calculation engine or record store. When an employee exits, HR/Payroll must compute gratuity externally on spreadsheets and manually enter ad-hoc amounts.

---

## 3. Scope

**In scope:**
- PostgreSQL table `payroll.gratuity_settlement` with RLS.
- Gratuity calculation engine based on the statutory formula:
  $$\text{Gratuity} = \frac{15 \times \text{Last Drawn (Basic + DA)} \times \text{Completed Years of Service}}{26}$$
- Service period rounding rule: tenure $> 6$ months is rounded up to the next full year; $\le 6$ months is rounded down.
- Statutory ceiling enforcement: statutory limit of ₹20,00,000 (20 Lakhs).
- Tax exemption calculation under Section 10(10) of the Income Tax Act:
  - Least of: (1) Actual gratuity received, (2) Statutory limit of ₹20,00,000, (3) Eligible statutory formula amount.
  - Excess over exemption is tagged as taxable salary under Section 17(1).
- REST API endpoint for estimating gratuity for any employee prior to exit.
- REST API endpoint for creating a formal gratuity settlement record linked to exit.

**Out of scope:**
- Leave encashment (handled in `W-90.1`).
- F&F full pay run integration (handled in `W-90.3`).
- Gratuity Trust / Group Gratuity Scheme insurance accounting with LIC.

---

## 4. Flow

```
HR / Payroll Officer --> POST /api/v1/payroll/gratuity/estimate?employeeId={id}&exitDate={date}
                     --> GratuityServiceImpl.estimateGratuity
                     --> Loads Employee joining date, salary structure (Basic + DA)
                     --> Applies 5-year rule, 15/26 formula, ₹20L ceiling & Sec 10(10) exemption
                     --> Returns GratuityEstimateResponse
```

---

## 5. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V190__gratuity.sql` | Create `payroll.gratuity_settlement` with RLS policies and indexes | yes | yes |

### Table: `payroll.gratuity_settlement`
```sql
CREATE TABLE payroll.gratuity_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    joining_date DATE NOT NULL,
    exit_date DATE NOT NULL,
    completed_years INTEGER NOT NULL CHECK (completed_years >= 0),
    is_exempt_from_5yr_rule BOOLEAN NOT NULL DEFAULT false, -- death / disablement
    last_drawn_basic NUMERIC(14,2) NOT NULL CHECK (last_drawn_basic >= 0),
    last_drawn_da NUMERIC(14,2) NOT NULL DEFAULT 0.00 CHECK (last_drawn_da >= 0),
    calculated_amount NUMERIC(14,2) NOT NULL CHECK (calculated_amount >= 0),
    statutory_ceiling NUMERIC(14,2) NOT NULL DEFAULT 2000000.00,
    gross_gratuity_payable NUMERIC(14,2) NOT NULL CHECK (gross_gratuity_payable >= 0),
    tax_exempt_amount NUMERIC(14,2) NOT NULL CHECK (tax_exempt_amount >= 0),
    taxable_amount NUMERIC(14,2) NOT NULL CHECK (taxable_amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'PAID', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_gratuity_settlement_employee
    ON payroll.gratuity_settlement (tenant_id, employee_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX idx_gratuity_settlement_tenant_status
    ON payroll.gratuity_settlement (tenant_id, status);
```

---

## 6. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `payroll/gratuity/GratuitySettlement.java` | JPA Entity for `payroll.gratuity_settlement` |
| Repository | `payroll/gratuity/GratuitySettlementRepository.java` | Spring Data JPA repository with tenant isolation |
| DTO | `payroll/gratuity/GratuityEstimateRequest.java` | Record: `employeeId`, `exitDate`, `reason` (RESIGNATION, RETIREMENT, DEATH, DISABLEMENT) |
| DTO | `payroll/gratuity/GratuityEstimateResponse.java` | Record: tenure, eligible flag, calculation breakdown, exempt vs taxable amounts |
| Service | `payroll/gratuity/GratuityService.java` | Interface for estimation and settlement recording |
| ServiceImpl | `payroll/gratuity/GratuityServiceImpl.java` | Implements Payment of Gratuity Act formula, 6-month rounding rule, and Section 10(10) caps |
| Controller | `payroll/gratuity/GratuityController.java` | REST endpoints guarded by `@RequiresAction("payroll.settlement.manage")` |

### API Contract

| Method | Path | Request Body | Response Body | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/payroll/gratuity/estimate` | `GratuityEstimateRequest` | `GratuityEstimateResponse` | `payroll.settlement.manage` |
| `POST` | `/api/v1/payroll/gratuity/settlements` | `GratuitySettlementCreateRequest` | `GratuitySettlementResponse` (`201`) | `payroll.settlement.manage` |
| `GET` | `/api/v1/payroll/gratuity/settlements/{id}` | — | `GratuitySettlementResponse` | `payroll.settlement.view` |

---

## 7. Open Questions for CA Review

1. **Dearness Allowance (DA):** If an employer does not have a separate DA component, does Basic salary alone constitute the wage for gratuity? *(Standard practice: Yes)*.
2. **Rounding of Service:** Does the company round $> 6$ months to 1 year for resignation as well, or strictly for retirement/death? *(Act standard: 6 months or more counts as 1 full year after completing 5 years)*.

---

## 8. Verification

```bash
mvnw clean test -pl migration,payroll -am -Dtest="*Gratuity*"
```
- Unit test `GratuityCalculatorTest`: tests tenure < 5 years returns ₹0 (unless death/disablement), tenure 5y 7m rounds to 6 years, cap at ₹20,00,000, and Section 10(10) split.
- Integration test `GratuityIT`: tests estimation and settlement creation via REST API.
