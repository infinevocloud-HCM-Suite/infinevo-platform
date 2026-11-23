# Feature: Leave Encashment on Exit & Tax Exemption

| Field | Value |
|---|---|
| **Feature ID** | `W-90.1` · `PAYROLL-SETTLEMENT` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready for CA / founder review |
| **Blocked by** | CA confirmation on encashment divisor (30 days vs 26 days vs actual month days) |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V193__leave_encashment.sql` (renumbered from V191 as V190-V192 were reserved) | 1 |
| Externally testable behaviour | Calculates accrued privilege/earned leave encashment at exit, applies Section 10(10AA) exemption cap (₹25,00,000), and records settlement | 1 |
| Frontend area | none (UI in `W-90.4`) | 1 |

---

## 2. Problem & Context

When an employee exits, unutilized earned/privilege leave (EL/PL) balances must be encashed. 
Under Section 10(10AA) of the Income Tax Act (amended by Notification No. 31/2023), the maximum lifetime tax exemption for leave encashment on retirement/resignation for non-government employees is **₹25,00,000** (raised from ₹3,00,000).

Currently, leave balances exist in `core.leave_allocation` and `core.leave_balance` (`W-16.2`), but there is no payroll calculation engine to compute encashment value, apply tax exemption under Section 10(10AA), or track encashment transactions.

---

## 3. Scope

**In scope:**
- PostgreSQL table `payroll.leave_encashment_settlement` with RLS.
- Reads active leave balances from `core.leave_balance` for leave types flagged as `is_encashable = true`.
- Encashment formula:
  $$\text{Per Day Rate} = \frac{\text{Last Drawn Basic (+ DA if any)}}{\text{Divisor (Default: 30 days)}}$$
  $$\text{Leave Encashment} = \text{Eligible Leave Days} \times \text{Per Day Rate}$$
- Section 10(10AA) statutory tax exemption calculation:
  - Least of: (1) Actual leave encashment received, (2) Statutory ceiling of ₹25,00,000, (3) 10 months' average salary, (4) Cash equivalent of leave based on maximum 30 days per completed year.
  - Excess is treated as taxable salary.
- REST API for estimating and creating leave encashment settlement rows.

**Out of scope:**
- Notice pay (handled in `W-90.2`).
- Consolidated F&F compute (handled in `W-90.3`).
- Mid-service leave encashment while in service (fully taxable, separate feature).

---

## 4. Flow

```
Payroll Officer --> POST /api/v1/payroll/leave-encashment/estimate?employeeId={id}&exitDate={date}
                --> LeaveEncashmentServiceImpl.estimateEncashment
                --> Queries Core Leave Balance (encashable EL/PL only)
                --> Computes per-day rate based on Basic (+ DA) / 30
                --> Applies Section 10(10AA) ₹25 Lakh ceiling & exemption rules
                --> Returns LeaveEncashmentResponse
```

---

## 5. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V193__leave_encashment.sql` | Create `payroll.leave_encashment_settlement` with RLS and foreign keys | yes | yes |

### Table: `payroll.leave_encashment_settlement`
```sql
CREATE TABLE payroll.leave_encashment_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    leave_type_id UUID NOT NULL,
    encashable_days NUMERIC(6,2) NOT NULL CHECK (encashable_days >= 0),
    per_day_rate NUMERIC(14,2) NOT NULL CHECK (per_day_rate >= 0),
    gross_encashment_amount NUMERIC(14,2) NOT NULL CHECK (gross_encashment_amount >= 0),
    tax_exempt_amount NUMERIC(14,2) NOT NULL CHECK (tax_exempt_amount >= 0),
    taxable_amount NUMERIC(14,2) NOT NULL CHECK (taxable_amount >= 0),
    statutory_ceiling NUMERIC(14,2) NOT NULL DEFAULT 2500000.00,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'PAID', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE UNIQUE INDEX uk_leave_encashment_emp_type
    ON payroll.leave_encashment_settlement (tenant_id, employee_id, leave_type_id)
    WHERE status <> 'CANCELLED';
```

---

## 6. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `payroll/settlement/LeaveEncashmentSettlement.java` | JPA entity mapping `payroll.leave_encashment_settlement` |
| Repository | `payroll/settlement/LeaveEncashmentRepository.java` | Spring Data JPA repo with tenant isolation |
| DTO | `payroll/settlement/LeaveEncashmentEstimateRequest.java` | DTO: `employeeId`, `exitDate`, `customDays` (optional override) |
| DTO | `payroll/settlement/LeaveEncashmentResponse.java` | DTO: days, rate, gross amount, exempt amount, taxable amount |
| Service | `payroll/settlement/LeaveEncashmentService.java` | Interface for estimation, calculation, and recording |
| ServiceImpl | `payroll/settlement/LeaveEncashmentServiceImpl.java` | Implements divisor math, leave type checks, and Section 10(10AA) limit |
| Controller | `payroll/settlement/LeaveEncashmentController.java` | REST endpoints guarded by `@RequiresAction("payroll.salary.manage")` (`/record`) and `@RequiresAction("payroll.salary.read")` (`/estimate`, `/{employeeId}`) matching seeded platform roles |

---

## 7. Open Questions for CA Review

1. **Per-day Divisor:** In Indian industry, the daily rate divisor for leave encashment is commonly either 30 days, 26 working days, or actual month days. Does the CA recommend a tenant-configurable setting (defaulting to 30)?
2. **Components Included:** Confirm that only Basic (+ DA if any) is considered for leave encashment, excluding HRA and allowances as per standard Income Tax definitions.

---

## 8. Verification

```bash
mvnw clean test -pl migration,payroll -am -Dtest="*LeaveEncashment*"
```
- Unit test `LeaveEncashmentTest`: verifies per-day calculation, 10(10AA) ₹25 Lakh cap, and non-encashable leave types (e.g. Sick Leave, Casual Leave) rejection.
