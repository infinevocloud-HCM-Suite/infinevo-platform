# Feature: Labour Welfare Fund (LWF)

| Field | Value |
|---|---|
| **Feature ID** | `W-92` · `PAY-STATUTORY-LWF` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration`, `code/frontend` (`src/payroll/settings`, `src/payroll/payrun`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-92`; legacy application only held the boolean flag without calculation |
| **Status** | Draft - In Review |
| **Blocked by** | Waits on CA review |

---

## 1. What and why

The **Labour Welfare Fund (LWF)** is a state-level statutory contribution enacted under state-specific Labour Welfare Fund Acts to provide social security and improve working conditions for organized-sector employees.

Like the Employees' State Insurance (ESI) and Provident Fund (EPF), LWF consists of two statutory portions:
1. **Employee Contribution (`LWF_EMPLOYEE`):** Deducted from the employee's net pay (`LineKind.DEDUCTION`).
2. **Employer Contribution (`LWF_EMPLOYER`):** Contributed by the organization as a company benefit (`LineKind.BENEFIT`).

Unlike EPF and ESI, LWF rules are **not uniform nationally**:
- **State jurisdiction:** Contribution applicability and amounts are dictated by the employee's **work location state** (`core.work_location.state_code`).
- **Deduction schedules:** Some states mandate deductions **monthly** (e.g., Haryana, Punjab, Chandigarh), some **half-yearly in June and December** (e.g., Maharashtra, Gujarat, Delhi, Goa, West Bengal), and some **annually in December** (e.g., Karnataka, Tamil Nadu, Andhra Pradesh, Telangana). In non-deduction months, no contribution is levied.
- **Variable calculation rules:** While many states charge flat rupee amounts, states like **Haryana** charge a percentage of gross wages (0.2% employee capped at ₹25, 2x employer capped at ₹50), and states like **Maharashtra** have wage-tiered rules (under ₹3,000 gross exempt/reduced vs. standard).
- **States without LWF:** Several states/UTs (e.g., Uttar Pradesh, Rajasthan, Bihar, Himachal Pradesh) do not have active LWF enactments.

### Current State in Infinevo Platform
1. **Database:** The employee-level flag `is_eligible_for_lwf` already exists in `payroll.employee_statutory_profile` (`code/backend/migration/src/main/resources/db/migration/payroll/V050__employee_statutory_profile.sql`).
2. **Frontend UI:** The "Set up salary" and "Revise salary" drawers already have the LWF checkbox (`StatutoryProfileCard.jsx`).
3. **Missing Engine:** `StatutoryLineContributor.java` contains calculation logic for EPF, EPS, EDLI, ESI, and Professional Tax, but has **zero** logic for LWF. As a result, no LWF deductions or benefits are ever calculated in pay runs.
4. **Missing Settings & Architectural Model:**
   - **Founder Decision (2026-11-24):** A single flat amount and cycle column on `org_lwf_setting` cannot support Haryana's percentage-with-cap rule, wage tiers, or dated government rate revisions.
   - Therefore, LWF strictly follows the **Professional Tax (PT) architecture pattern**:
     - Standard statutory rules, deduction cycles, wage tiers, and percentage caps live in a **dated national reference table maintained by Infinevo** (`reference.lwf_rule`).
     - Tenant configuration in `payroll.org_lwf_setting` stores **only** the tenant's state registration/establishment code and active/inactive toggle (`is_enabled`).
     - **No tenant override of statutory contribution amounts or cycles.** Standard statutory state rules apply automatically, and future government rate updates reach tenants without schema migrations or tenant re-configuration.

This ticket delivers **complete Labour Welfare Fund calculation, tenant configuration, and reporting**.

---

## 2. Size Cap & Database Changes

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` (reuses `core.work_location` and `payroll.payrun`) | 1 |
| Flyway migrations | 2 migrations (next free versions at build time):<br>1. `payroll/V<N>__lwf_schema.sql`<br>2. `core/V<M>__lwf_permissions.sql` | 2 |
| Externally testable behaviour | When an employee is eligible for LWF, their pay run automatically includes employee deduction (`LWF_EMPLOYEE`) and employer contribution (`LWF_EMPLOYER`) during applicable deduction months based on their work location state | 1 |
| Frontend area | `code/frontend/src/payroll/settings` (`LwfSetting.jsx`, route `/payroll/settings/lwf`) and `src/payroll/payrun` (statutory schedule export) | 1 |

### Database Schema Updates

#### Migration 1: `payroll/V<N>__lwf_schema.sql` (Next free version at build time)
Creates reference tables and tenant setting table:
```sql
-- 1. National reference table of states and LWF levy status
CREATE TABLE IF NOT EXISTS reference.lwf_state (
    state_code VARCHAR(2) PRIMARY KEY,
    state_name VARCHAR(64) NOT NULL,
    levies_lwf BOOLEAN NOT NULL DEFAULT true
);

-- 2. National dated reference table for LWF statutory calculation rules
CREATE TABLE IF NOT EXISTS reference.lwf_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_code VARCHAR(2) NOT NULL REFERENCES reference.lwf_state(state_code),
    effective_from DATE NOT NULL,
    effective_to DATE DEFAULT NULL,
    deduction_months INTEGER[] NOT NULL,
    calc_mode VARCHAR(32) NOT NULL, -- 'FLAT' or 'PERCENTAGE_WITH_CAP'
    min_gross NUMERIC(14, 4) NOT NULL DEFAULT 0.0000,
    max_gross NUMERIC(14, 4) DEFAULT NULL,
    employee_amount NUMERIC(14, 4) DEFAULT NULL,
    employer_amount NUMERIC(14, 4) DEFAULT NULL,
    employee_rate NUMERIC(6, 4) DEFAULT NULL,
    employer_rate NUMERIC(6, 4) DEFAULT NULL,
    employee_cap NUMERIC(14, 4) DEFAULT NULL,
    employer_cap NUMERIC(14, 4) DEFAULT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lwf_rule_lookup
    ON reference.lwf_rule (state_code, effective_from);

-- 3. Tenant LWF registration and toggle (no amount or cycle columns)
CREATE TABLE IF NOT EXISTS payroll.org_lwf_setting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    state_code VARCHAR(2) NOT NULL REFERENCES reference.lwf_state(state_code),
    registration_number VARCHAR(32) DEFAULT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(64) NOT NULL,
    CONSTRAINT uq_org_lwf_setting UNIQUE (tenant_id, state_code)
);

CREATE INDEX IF NOT EXISTS idx_org_lwf_setting_tenant
    ON payroll.org_lwf_setting (tenant_id);
```

#### Migration 2: `core/V<M>__lwf_permissions.sql` (Next free version at build time)
Follows standard role grant pattern established in `code/backend/migration/src/main/resources/db/migration/core/V184__wfh_seed_roles.sql`:
1. Seeds `reference.action`:
   - `payroll.lwf.read` — "View tenant LWF settings and return statements"
   - `payroll.lwf.manage` — "Configure tenant LWF registrations and state enablement"
2. Creates grant function `core.grant_lwf_actions(p_tenant_id UUID)` granting actions to `platform-admin`, `tenant-admin`, `payroll-officer`, and `finance`.
3. Creates trigger `tenant_seed_system_roles_lwf` on `core.tenant`.
4. Idempotent backfill across all existing tenants.

---

## 3. Scope

### In Scope
1. **Statutory Reference Rules (`reference.lwf_state`, `reference.lwf_rule`):**
   - National statutory rules for all states levying LWF.
   - Supports flat rupee amounts (e.g. MH, KA, GJ, TN, DL) and percentage-with-cap models (e.g. Haryana 0.2% / 0.4% with ₹25/₹50 caps).
   - Dated versioning (`effective_from`, `effective_to`) ensuring past pay runs calculate against historical rates and future rate updates apply automatically.
2. **Tenant State Configuration (`payroll.org_lwf_setting`):**
   - Lists operating states detected from active work locations (`core.work_location`).
   - Enter state establishment/registration numbers.
   - Toggle LWF on/off per state.
3. **Pay Run Contributor (`StatutoryLineContributor.java`):**
   - Resolves employee work location state.
   - Evaluates employee eligibility (`is_eligible_for_lwf == true`).
   - Checks whether the pay period end month matches the state's `deduction_months`.
   - Generates:
     - `LWF_EMPLOYEE` line (`LineKind.DEDUCTION`)
     - `LWF_EMPLOYER` line (`LineKind.BENEFIT`)
4. **Off-Cycle Pay Run Guard:**
   - LWF deductions apply exclusively to regular monthly pay runs; off-cycle runs bypass LWF calculation.
5. **Frontend UI:**
   - Dedicated settings screen at `/payroll/settings/lwf` registered in `SettingsLayout.jsx`.
   - Displays state cards with statutory deduction cycles, statutory rates, and registration input.
6. **Periodic Return Export:**
   - Adds "LWF Return" statement download (`.xlsx` / `.csv`) under Statutory Returns menu on `RunPage.jsx`.

### Out of Scope
1. **Direct State Portal API Filing:** State labour welfare boards do not offer public return filing APIs; return statements are generated for portal upload/physical challan submission.
2. **Designation-based Labour Exemptions:** Some state acts exempt managerial personnel earning above specific thresholds; in Infinevo, managerial exemption is cleanly governed by unchecking `is_eligible_for_lwf` on the employee's statutory profile.

---

## 4. State-wise Statutory Rules & Deduction Cycles

The platform maintains built-in statutory reference rules matching state Labour Welfare Fund regulations:

| State Code | State Name | Deduction Cycle | Applicable Months | Employee Share (₹) | Employer Share (₹) | Mode / Cap |
|:---:|---|:---:|:---:|:---:|:---:|---|
| **MH** | Maharashtra | Half-Yearly | June (6), December (12) | ₹12.00 / ₹25.00 | ₹36.00 / ₹75.00 | FLAT (Standard ₹12/₹36; ₹25/₹75 under 2024 amendment) |
| **KA** | Karnataka | Annual | December (12) | ₹20.00 / ₹50.00 | ₹40.00 / ₹100.00 | FLAT (1:2 ratio; ₹20/₹40 standard, ₹50/₹100 under 2024 amendment) |
| **GJ** | Gujarat | Half-Yearly | June (6), December (12) | ₹6.00 | ₹12.00 | FLAT (1:2 ratio) |
| **HR** | Haryana | Monthly | All (1 to 12) | 0.2% of Gross | 2x Employee Share | PERCENTAGE_WITH_CAP (EE Cap ₹25, ER Cap ₹50) |
| **TN** | Tamil Nadu | Annual | December (12) | ₹20.00 / ₹50.00 | ₹40.00 / ₹100.00 | FLAT |
| **DL** | Delhi | Half-Yearly | June (6), December (12) | ₹0.75 | ₹2.25 | FLAT (1:3 ratio) |
| **AP** | Andhra Pradesh | Annual | December (12) | ₹20.00 | ₹50.00 | FLAT |
| **TS** | Telangana | Annual | December (12) | ₹20.00 | ₹50.00 | FLAT |
| **KL** | Kerala | Half-Yearly | June (6), December (12) | ₹20.00 | ₹20.00 | FLAT (1:1 ratio) |
| **WB** | West Bengal | Half-Yearly | June (6), December (12) | ₹3.00 | ₹15.00 | FLAT (1:5 ratio) |
| **MP** | Madhya Pradesh | Half-Yearly | June (6), December (12) | ₹10.00 | ₹30.00 | FLAT (1:3 ratio) |
| **CH** | Chandigarh | Monthly | All (1 to 12) | ₹5.00 | ₹20.00 | FLAT |
| **PB** | Punjab | Monthly | All (1 to 12) | ₹5.00 | ₹20.00 | FLAT |
| **GA** | Goa | Half-Yearly | June (6), December (12) | ₹60.00 | ₹180.00 | FLAT |

*For all other states (e.g. UP, RJ, BR, UK, OR): `levies_lwf = false`; contributions evaluate to ₹0.*

---

## 5. Backend Architecture

### Package: `com.infinevo.payroll.statutory.lwf`

| Layer | File / Class | Responsibility |
|---|---|---|
| Entity | `OrgLwfSetting.java` | JPA entity mapped to `payroll.org_lwf_setting` (tenant isolated: registration number and enabled toggle) |
| Entity | `LwfRule.java`, `LwfState.java` | JPA entities mapped to `reference.lwf_rule` and `reference.lwf_state` |
| Repository | `OrgLwfSettingRepository.java` | Lookups by `(tenantId, stateCode)` and `findAllByTenantId(tenantId)` |
| Repository | `LwfRuleRepository.java` | Finds in-force rule by state code and date (`findByStateCodeAndPeriodEnd`) |
| Service | `LwfService.java` | Interface defining state rule resolutions, settings management, and calculation |
| ServiceImpl | `LwfServiceImpl.java` | Matches active work location states, loads in-force reference rules, and computes contributions |
| Controller | `LwfController.java` | REST endpoints under `/api/v1/payroll/settings/lwf` |
| DTO | `LwfStateSettingResponse.java` | State rule metadata, deduction cycle, active work locations count, current rates, and registration number |
| DTO | `LwfSettingUpdateRequest.java` | Request body to update registration number and enable/disable flag |
| Pay Run | `StatutoryLineContributor.java` | Calls `LwfService.calculate()` and appends `LWF_EMPLOYEE` and `LWF_EMPLOYER` lines |

---

### API Contracts

#### 1. List LWF State Settings for Tenant
```http
GET /api/v1/payroll/settings/lwf
```
* **Authorization:** `@RequiresAction("payroll.lwf.read")`
* **Response `200 OK`:**
```json
{
  "status": 200,
  "message": "LWF settings retrieved",
  "data": [
    {
      "stateCode": "MH",
      "stateName": "Maharashtra",
      "leviesLwf": true,
      "isEnabled": true,
      "deductionCycle": "HALF_YEARLY_JUNE_DECEMBER",
      "deductionMonths": [6, 12],
      "employeeContribution": 25.00,
      "employerContribution": 75.00,
      "registrationNumber": "MH-LWF-123456",
      "activeLocationsCount": 2
    },
    {
      "stateCode": "HR",
      "stateName": "Haryana",
      "leviesLwf": true,
      "isEnabled": true,
      "deductionCycle": "MONTHLY",
      "deductionMonths": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      "calcMode": "PERCENTAGE_WITH_CAP",
      "employeeRate": 0.002,
      "employeeCap": 25.00,
      "employerCap": 50.00,
      "registrationNumber": "HR-LWF-789",
      "activeLocationsCount": 1
    }
  ]
}
```

#### 2. Update LWF State Setting
```http
PUT /api/v1/payroll/settings/lwf/{stateCode}
```
* **Authorization:** `@RequiresAction("payroll.lwf.manage")`
* **Request Body:**
```json
{
  "isEnabled": true,
  "registrationNumber": "MH-LWF-987654"
}
```
* **Response `200 OK`:** Returns updated `LwfStateSettingResponse`.

#### 3. Export LWF Return Statement for Pay Run
```http
GET /api/v1/payroll/payruns/{id}/statutory/lwf/export?format=XLSX|CSV
```
* **Authorization:** `@RequiresAction("payroll.lwf.read")`
* **Response:** File attachment containing employee code, name, state, gross earnings, employee LWF, employer LWF, and total remittance.

---

## 6. Pay Run Calculation Logic

In `code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java`:

```java
// 1. LWF applies only to regular pay runs
if (ctx.isRegular() && profile != null && profile.isEligibleForLwf()) {
    String stateCode = resolveEmployeeWorkLocationState(ctx);
    LocalDate periodEnd = ctx.periodEnd();
    int month = periodEnd.getMonthValue();

    LwfCalculationResult lwf = lwfService.calculate(
        tenantId,
        stateCode,
        grossEarnings,
        month,
        periodEnd
    );

    if (lwf.hasContribution()) {
        lines.add(new PayLine(
            LineKind.DEDUCTION,
            LineSource.STATUTORY,
            null,
            "LWF_EMPLOYEE",
            "Labour welfare fund - employee",
            lwf.employeeAmount(),
            false
        ));
        lines.add(new PayLine(
            LineKind.BENEFIT,
            LineSource.STATUTORY,
            null,
            "LWF_EMPLOYER",
            "Labour welfare fund - employer",
            lwf.employerAmount(),
            false
        ));
    }
}
```

---

## 7. Frontend Architecture

### File: `code/frontend/src/payroll/settings/LwfSetting.jsx`
- Route: `/payroll/settings/lwf`.
- Shows a list/table of states where the organization has active work locations.
- Each state displays:
  - State name & code.
  - Statutory deduction frequency badge (e.g. "Half-yearly: Jun & Dec", "Annual: Dec", "Monthly").
  - Employee & employer standard shares / rate rule description.
  - Registration/Establishment code input field.
  - Enable / Disable switch.

### Navigation & Routing
- Added to `SETTINGS_MENU_ITEMS` in `code/frontend/src/payroll/settings/SettingsLayout.jsx`:
  ```jsx
  {
    key: '/payroll/settings/lwf',
    icon: <TeamOutlined />,
    label: 'Labour Welfare Fund',
  }
  ```
- Registered route in `code/frontend/src/payroll/index.js`.

---

## 8. Acceptance Criteria

| # | Given | When | Then |
|---|---|---|---|
| **AC-1** | An active employee in Haryana with gross ₹20,000 and `is_eligible_for_lwf = true` | Pay run computed for any month (e.g. May) | Calculates `LWF_EMPLOYEE` = ₹25.00 (0.2% of 20,000 = 40, capped at 25) and `LWF_EMPLOYER` = ₹50.00 (capped at 50) |
| **AC-2** | An active employee in Maharashtra with `is_eligible_for_lwf = true` | Pay run computed for June (month 6) or December (month 12) | Deducts `LWF_EMPLOYEE` and `LWF_EMPLOYER` per Maharashtra statutory rule |
| **AC-3** | An active employee in Maharashtra with `is_eligible_for_lwf = true` | Pay run computed for July (month 7) | No LWF lines generated (`hasContribution = false`) because July is not a deduction month |
| **AC-4** | An active employee in Karnataka with `is_eligible_for_lwf = true` | Pay run computed for December (month 12) | Deducts Karnataka LWF annual amount; all other months evaluate to ₹0 |
| **AC-5** | An employee works in Uttar Pradesh (`UP`) where no LWF is levied | Pay run computed | No LWF lines generated (`levies_lwf = false`) |
| **AC-6** | Tenant admin disables LWF for a state in `/payroll/settings/lwf` | Pay run computed for employees in that state | No LWF lines generated (`isEnabled = false`) |
| **AC-7** | An off-cycle pay run is computed | Pay run worker evaluates statutory lines | LWF calculation is skipped; LWF applies exclusively to regular pay runs |
| **AC-8** | User without `payroll.lwf.manage` attempts to update LWF registration | `PUT /api/v1/payroll/settings/lwf/{stateCode}` called | Server returns `HTTP 403 Forbidden` |

---

## 9. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| **State LWF rates revised mid-year by state government** | High | National reference table `reference.lwf_rule` uses dated validity ranges (`effective_from`, `effective_to`), allowing seamless rate transitions without schema updates. |
| **Complex percentage-with-cap formulas (Haryana) miscalculated** | Medium | `LwfRule` encapsulates `calc_mode` with dedicated calculation logic and unit tests covering below-cap, at-cap, and above-cap gross earnings. |
| **Off-cycle runs trigger duplicate semi-annual deductions** | Medium | `StatutoryLineContributor` enforces `ctx.isRegular()`; off-cycle runs are explicitly bypassed. |
| **Tenant administrator accidentally alters statutory deduction rates** | Low | Elimination of tenant rate overrides ensures tenants can only configure registration numbers and enable/disable toggles; statutory rates are centrally managed. |

---

## 10. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `LwfServiceTest.java` (Unit) | Backend | Verifies state rule matching, monthly vs. half-yearly vs. annual qualifying month checks, Haryana percentage/cap calculation, and tenant disable toggle. |
| `LwfControllerTest.java` (Unit) | Backend | Verifies `GET /settings/lwf` returns active location states and `PUT` updates registration number; verifies 403 authorization. |
| `LwfCalculationIT.java` (Integration) | Backend | End-to-end test with regular pay run execution for MH (June vs July) and HR (all months); verifies pay line persistence. |
| `LwfSetting.test.jsx` (Component) | Frontend | Verifies settings page renders operating states, toggles state enablement, and updates registration number. |

---

## 11. Verification Commands

```bash
# 1. Backend build, spotless and tests
cd code/backend
mvn spotless:check -pl payroll
mvn test -pl payroll "-Dtest=LwfServiceTest,LwfControllerTest,LwfCalculationIT"

# 2. Frontend lint and tests
cd ../frontend
npm run lint
npm test -- src/payroll/settings/LwfSetting.test.jsx
```
