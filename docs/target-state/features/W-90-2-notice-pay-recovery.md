# Feature: Notice Period Pay & Shortfall Recovery Engine

| Field            | Value                                                                                          |
| ---------------- | ---------------------------------------------------------------------------------------------- |
| **Feature ID**   | `W-90.2` · `PAYROLL-SETTLEMENT`                                                                |
| **Owner**        | Mohit (`dev-mohit`)                                                                            |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration`                                               |
| **Related gaps** | none in `GAP_INVENTORY.md`                                                                     |
| **Status**       | Ready for CA / founder review                                                                  |
| **Blocked by**   | CA confirmation on whether notice recovery reduces taxable gross or acts as post-tax deduction |

## 1. Size cap

| Axis                          | This spec                                                                                                        | Limit |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------- | ----- |
| Backend module                | `payroll`                                                                                                        | 1     |
| Flyway migration              | `V194__notice_period_settlement.sql` (renumbered from V192 to sequence after TDS V192 and Leave Encashment V193) | 1     |
| Externally testable behaviour | Calculates notice pay payout (buyout by employer) or notice shortfall recovery (deduction from exiting employee) | 1     |
| Frontend area                 | none (UI in `W-90.4`)                                                                                            | 1     |

---

## 2. Problem & Context

Employment contracts stipulate a notice period (e.g., 30, 60, or 90 days). Upon exit:

- If an employee is relieved early before serving their full notice period, the unserved shortfall days are recovered from their final settlement (**Notice Pay Recovery**).
- If the employer waives notice or terminates immediately, the employer pays salary in lieu of notice (**Notice Pay in Lieu**).

Currently, there is no automated notice period calculation or recovery tracking in the payroll engine.

---

## 3. Scope

**In scope:**

- PostgreSQL table `payroll.notice_period_settlement` with RLS.
- Notice period calculation engine:
  - Required Notice Days (from employee policy or employment contract).
  - Served Notice Days (from resignation date to actual last working day).
  - Shortfall / Excess Days.
  - Calculation basis: Daily rate calculated from Gross Salary or Basic Salary as configured.
- Direction of transaction:
  - `RECOVERY`: Employee owes company (deducted from F&F).
  - `PAYOUT`: Company owes employee (added to F&F earnings).
  - `WAIVED`: Employer formally waives shortfall (0 amount, recorded for audit).
- REST API to compute, adjust, or waive notice pay settlement.

**Out of scope:**

- F&F full aggregation (handled in `W-90.3`).
- HR resignation approval workflow (handled in `W-103`).

---

## 4. Flow

```
HR / Payroll Officer --> POST /api/v1/payroll/notice-period/calculate
                     --> NoticePeriodServiceImpl.calculate
                     --> Evaluates Contract Notice Days vs Actual Served Days
                     --> Determines Recovery vs Payout vs Waiver
                     --> Returns NoticePeriodSettlementResponse
```

---

## 5. Database changes

| Migration                                    | Change                                             | Tenant-aware? | Reversible? |
| -------------------------------------------- | -------------------------------------------------- | ------------- | ----------- |
| `payroll/V194__notice_period_settlement.sql` | Create `payroll.notice_period_settlement` with RLS | yes           | yes         |

### Table: `payroll.notice_period_settlement`

```sql
CREATE TABLE payroll.notice_period_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    resignation_date DATE NOT NULL,
    expected_exit_date DATE NOT NULL,
    actual_exit_date DATE NOT NULL,
    required_notice_days INTEGER NOT NULL CHECK (required_notice_days >= 0),
    served_notice_days INTEGER NOT NULL CHECK (served_notice_days >= 0),
    shortfall_days INTEGER NOT NULL DEFAULT 0,
    daily_rate NUMERIC(14,2) NOT NULL CHECK (daily_rate >= 0),
    rate_basis VARCHAR(20) NOT NULL DEFAULT 'GROSS' CHECK (rate_basis IN ('GROSS', 'BASIC')),
    settlement_type VARCHAR(20) NOT NULL CHECK (settlement_type IN ('RECOVERY', 'PAYOUT', 'WAIVED')),
    settlement_amount NUMERIC(14,2) NOT NULL CHECK (settlement_amount >= 0),
    waiver_reason VARCHAR(255) NULL,
    approved_by VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'SETTLED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_notice_settlement_employee
    ON payroll.notice_period_settlement (tenant_id, employee_id)
    WHERE status <> 'CANCELLED';
```

---

## 6. Backend changes

| Layer       | File                                                   | Change                                                                                                                                                                                      |
| ----------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Entity      | `payroll/settlement/NoticePeriodSettlement.java`       | JPA entity mapping `payroll.notice_period_settlement`                                                                                                                                       |
| Repository  | `payroll/settlement/NoticePeriodRepository.java`       | Spring Data JPA repo with tenant isolation                                                                                                                                                  |
| DTO         | `payroll/settlement/NoticePeriodCalculateRequest.java` | DTO: resignation date, actual exit date, required days, waiver flag                                                                                                                         |
| DTO         | `payroll/settlement/NoticePeriodResponse.java`         | DTO: shortfall days, rate, settlement type, total amount                                                                                                                                    |
| Service     | `payroll/settlement/NoticePeriodService.java`          | Interface for notice shortfall math and settlement                                                                                                                                          |
| ServiceImpl | `payroll/settlement/NoticePeriodServiceImpl.java`      | Implements calendar day delta, daily rate basis, and waiver handling                                                                                                                        |
| Controller  | `payroll/settlement/NoticePeriodController.java`       | REST endpoints guarded by `@RequiresAction("payroll.salary.manage")` (`/calculate`, `/waive`) and `@RequiresAction("payroll.salary.read")` (`/{employeeId}`) matching seeded platform roles |

---

## 7. Open Questions for CA Review

1. **Taxability of Notice Pay Recovery:** In Indian payroll taxation, judicial rulings (e.g., Gujarat High Court in _Nandkishore Deshpande_) debate whether notice pay recovery reduces taxable gross salary or is treated as a post-tax recovery. What is Infinevo's standard tax treatment policy?
2. **Rate Basis:** Should the daily rate be calculated on Gross monthly salary or Basic monthly salary? _(Recommendation: Configurable per tenant policy)_.

---

## 8. Verification

```bash
mvnw clean test -pl migration,payroll -am -Dtest="*NoticePeriod*"
```

- Unit test `NoticePeriodTest`: tests shortfall calculation, waiver action setting amount to ₹0, and gross vs basic rate determination.
