# Feature: Labour Welfare Fund (LWF)

| Field | Value |
|---|---|
| **Feature ID** | `W-92` · `PAY-STATUTORY-LWF` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/payroll`, `code/frontend` (`src/payroll/settings`, `src/payroll/payrun`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-92`; legacy application only held the boolean flag (`legacy/Payroll-Bend-SBoot/.../BasicDetailsServiceImpl.java:234`) without calculation; `.claude/work/active-work.md` noted *"Left: LWF not calculated"* |
| **Status** | Ready |
| **Blocked by** | nothing (state statutory rules compiled from state Labour Welfare Fund Acts) |

---

## 1. What and why

The **Labour Welfare Fund (LWF)** is a state-level statutory contribution enacted under state-specific Labour Welfare Fund Acts to provide social security and improve working conditions for organized-sector employees.

Like the Employees' State Insurance (ESI) and Provident Fund (EPF), LWF consists of two statutory portions:
1. **Employee Contribution (`LWF_EMPLOYEE`):** Deducted from the employee's net pay (`LineKind.DEDUCTION`).
2. **Employer Contribution (`LWF_EMPLOYER`):** Contributed by the organization as a company benefit (`LineKind.BENEFIT`).

Unlike EPF and ESI, LWF rules are **not uniform nationally**:
- **State jurisdiction:** Contribution applicability and amounts are dictated by the employee's **work location state** (`core.org.work_location.state`).
- **Deduction schedules:** Some states mandate deductions **monthly** (e.g., Haryana, Chandigarh), some **half-yearly in June and December** (e.g., Maharashtra, Gujarat, Delhi, Goa, West Bengal), and some **annually in December** (e.g., Karnataka, Tamil Nadu, Andhra Pradesh, Telangana). In non-deduction months, no contribution is levied.
- **States without LWF:** Several states/UTs (e.g., Uttar Pradesh, Rajasthan, Bihar, Himachal Pradesh) do not have active LWF enactments.

### Current State in Infinevo Platform
1. **Database:** The employee-level flag `is_eligible_for_lwf` already exists in `payroll.employee_statutory_profile` ([`V050__employee_statutory_profile.sql:10`](file:///d:/Workspace/infinevo-platform/code/backend/migration/src/main/resources/db/migration/payroll/V050__employee_statutory_profile.sql#L10), [`EmployeeStatutoryProfile.java:73-77`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/salary/EmployeeStatutoryProfile.java#L73-L77)).
2. **Frontend UI:** The "Set up salary" and "Revise salary" drawers already have the LWF checkbox (`StatutoryProfileCard.jsx`).
3. **Missing Engine:** [`StatutoryLineContributor.java`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java) contains calculation logic for EPF, EPS, EDLI, ESI, and Professional Tax, but has **zero** logic for LWF. As a result, no LWF deductions or benefits are ever calculated in pay runs.
4. **Missing Settings:** There is no configuration page under `/payroll/settings` to view state LWF rules, register establishment IDs, or enable/disable LWF per state.

This ticket delivers **complete Labour Welfare Fund calculation, tenant configuration, and reporting**.

---

## 2. Size Cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` (reuses `core.org` work locations and `payroll.payrun`) | 1 |
| Flyway migration | `payroll/V192__org_lwf_setting.sql` (tenant state configuration table) | 1 |
| Externally testable behaviour | When an employee is eligible for LWF, their pay run automatically includes employee deduction (`LWF_EMPLOYEE`) and employer contribution (`LWF_EMPLOYER`) during applicable deduction months based on their work location state | 1 |
| Frontend area | `code/frontend/src/payroll/settings` (`LwfSetting.jsx`, route `/payroll/settings/lwf`) and `src/payroll/payrun` (statutory schedule export) | 1 |

---

## 3. Scope

### In Scope
1. **Statutory Reference Rules:** Standard national statutory rules for states levying LWF (contribution amounts, deduction frequencies, and qualifying months).
2. **Tenant State Configuration (`payroll.org_lwf_setting`):**
   - Ability for tenant admin / payroll officer to view state rules for active work locations.
   - Enter state establishment/registration numbers.
   - Toggle LWF on/off per state.
   - Override default contribution rates if a special establishment classification applies.
3. **Pay Run Contributor (`StatutoryLineContributor.java`):**
   - Resolves employee work location state.
   - Evaluates employee eligibility (`is_eligible_for_lwf == true`).
   - Checks whether the pay period end date matches the state's deduction cycle.
   - Generates:
     - `LWF_EMPLOYEE` line (`LineKind.DEDUCTION`)
     - `LWF_EMPLOYER` line (`LineKind.BENEFIT`)
4. **Frontend UI:**
   - Dedicated settings screen at `/payroll/settings/lwf` registered in `SettingsLayout.jsx` and `payroll/index.js`.
   - Displays state cards/tables with deduction cycle, employee/employer share, and registration number.
5. **Periodic Return Export:**
   - Adds "LWF Return" statement download (`.xlsx` / `.csv`) under Statutory Returns menu on `RunPage.jsx`.

### Out of Scope
1. **Direct State Portal API Filing:** State labour welfare boards do not offer public return filing APIs; return statements are generated for portal upload/physical challan submission.
2. **Designation-based Labour Exemptions:** Some state acts exempt managerial personnel earning above specific thresholds; in Infinevo, managerial exemption is cleanly governed by unchecking `is_eligible_for_lwf` on the employee's statutory profile.

---

## 4. State-wise Statutory Rules & Deduction Cycles

The platform maintains built-in statutory defaults matching state Labour Welfare Fund regulations:

| State Code | State Name | Deduction Cycle | Applicable Months | Employee Share (₹) | Employer Share (₹) | Notes / Wage Tier |
|:---:|---|:---:|:---:|:---:|:---:|---|
| **MH** | Maharashtra | Half-Yearly | June (6), December (12) | ₹12.00 / ₹25.00 | ₹36.00 / ₹75.00 | Standard ₹12 / ₹36 (Gross > ₹3,000); ₹25 / ₹75 under 2024 amendment |
| **KA** | Karnataka | Annual | December (12) | ₹20.00 / ₹50.00 | ₹40.00 / ₹100.00 | 1:2 ratio; ₹20 / ₹40 standard (₹50 / ₹100 under 2024 amendment) |
| **GJ** | Gujarat | Half-Yearly | June (6), December (12) | ₹6.00 | ₹12.00 | 1:2 ratio |
| **HR** | Haryana | Monthly | All (1 to 12) | 0.2% of Gross (Cap ₹25) | 2x Employee Share (Cap ₹50) | Capped at ₹25 (EE) and ₹50 (ER) |
| **TN** | Tamil Nadu | Annual | December (12) | ₹20.00 / ₹50.00 | ₹40.00 / ₹100.00 | December deduction |
| **DL** | Delhi | Half-Yearly | June (6), December (12) | ₹0.75 | ₹2.25 | 1:3 ratio |
| **AP** | Andhra Pradesh | Annual | December (12) | ₹20.00 | ₹50.00 | December deduction |
| **TS** | Telangana | Annual | December (12) | ₹20.00 | ₹50.00 | December deduction |
| **KL** | Kerala | Half-Yearly | June (6), December (12) | ₹20.00 | ₹20.00 | 1:1 ratio |
| **WB** | West Bengal | Half-Yearly | June (6), December (12) | ₹3.00 | ₹15.00 | 1:5 ratio |
| **MP** | Madhya Pradesh | Half-Yearly | June (6), December (12) | ₹10.00 | ₹30.00 | 1:3 ratio |
| **CH** | Chandigarh | Monthly | All (1 to 12) | ₹5.00 | ₹20.00 | Fixed monthly |
| **PB** | Punjab | Monthly | All (1 to 12) | ₹5.00 | ₹20.00 | Fixed monthly |
| **GA** | Goa | Half-Yearly | June (6), December (12) | ₹60.00 | ₹180.00 | June & Dec |

*For all other states (e.g. UP, RJ, BR, UK, OR): No LWF is levied; contributions evaluate to ₹0.*

---

## 5. Backend Architecture

### Package: `com.infinevo.payroll.statutory.lwf`

| Layer | File / Class | Responsibility |
|---|---|---|
| Entity | `OrgLwfSetting.java` | JPA entity mapped to `payroll.org_lwf_setting` (tenant isolated) |
| Repository | `OrgLwfSettingRepository.java` | Lookups by `(tenantId, stateCode)` and `findAllByTenantId(tenantId)` |
| Service | `LwfService.java` | Interface defining state rule resolutions, settings management, and calculation |
| ServiceImpl | `LwfServiceImpl.java` | Resolves tenant settings falling back to built-in state statutory defaults |
| Controller | `LwfController.java` | REST endpoints under `/api/v1/payroll/settings/lwf` |
| DTO | `LwfStateSettingResponse.java` | State rule metadata, active work locations count, current rates, and registration number |
| DTO | `LwfSettingUpdateRequest.java` | Request body to update registration number, custom rates, or enable/disable flag |
| Pay Run | `StatutoryLineContributor.java` | Calls `LwfService.calculate()` and appends `LWF_EMPLOYEE` and `LWF_EMPLOYER` lines |

---

### API Contract

#### 1. List LWF State Settings for Tenant
```http
GET /api/v1/payroll/settings/lwf
```
* **Authorization:** `@RequiresAction("payroll.statutory_settings.read")`
* **Response `200 OK`:**
```json
{
  "status": 200,
  "message": "LWF settings retrieved",
  "data": [
    {
      "stateCode": "MH",
      "stateName": "Maharashtra",
      "hasLwf": true,
      "isEnabled": true,
      "deductionCycle": "HALF_YEARLY_JUNE_DECEMBER",
      "employeeContribution": 12.00,
      "employerContribution": 36.00,
      "registrationNumber": "MH-LWF-123456",
      "activeLocationsCount": 2,
      "isOverridden": false
    },
    {
      "stateCode": "KA",
      "stateName": "Karnataka",
      "hasLwf": true,
      "isEnabled": true,
      "deductionCycle": "ANNUAL_DECEMBER",
      "employeeContribution": 20.00,
      "employerContribution": 40.00,
      "registrationNumber": null,
      "activeLocationsCount": 1,
      "isOverridden": false
    }
  ]
}
```

#### 2. Update LWF State Setting
```http
PUT /api/v1/payroll/settings/lwf/{stateCode}
```
* **Authorization:** `@RequiresAction("payroll.statutory_settings.update")`
* **Request Body:**
```json
{
  "isEnabled": true,
  "registrationNumber": "MH-LWF-987654",
  "employeeContribution": 25.00,
  "employerContribution": 75.00
}
```
* **Response `200 OK`:** Returns updated `LwfStateSettingResponse`.

#### 3. Export LWF Return Statement for Pay Run
```http
GET /api/v1/payroll/runs/{id}/statutory/lwf/export?format=XLSX|CSV
```
* **Authorization:** `@RequiresAction("payroll.statutory_report.export")`
* **Response:** File attachment containing employee code, name, state, gross earnings, employee LWF, employer LWF, and total remittance.

---

## 6. Pay Run Calculation Logic

In [`StatutoryLineContributor.java`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java):

```java
// 1. Check employee statutory profile flag
if (profile != null && profile.isEligibleForLwf()) {
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

### Qualifying Month Check
- `MONTHLY`: Every month (months 1 through 12).
- `HALF_YEARLY_JUNE_DECEMBER`: `month == 6 || month == 12`.
- `ANNUAL_DECEMBER`: `month == 12`.
- If `month` is not a deduction month, `hasContribution() == false`.

---

## 7. Frontend Architecture

### File: `code/frontend/src/payroll/settings/LwfSetting.jsx`
- Sits in `/payroll/settings/lwf`.
- Shows a list/table of states where the organization has active work locations.
- Each state displays:
  - State name & code.
  - Statutory deduction frequency badge (e.g. "Half-yearly: Jun & Dec", "Annual: Dec", "Monthly").
  - Employee & employer standard shares.
  - Registration/Establishment code input field.
  - Enable / Disable switch.
- Drawer or inline modal to edit custom rates if applicable.

### Navigation & Routing
- Added to `SETTINGS_MENU_ITEMS` in [`SettingsLayout.jsx`](file:///d:/Workspace/infinevo-platform/code/frontend/src/payroll/settings/SettingsLayout.jsx):
  ```jsx
  {
    key: '/payroll/settings/lwf',
    icon: <TeamOutlined />,
    label: 'Labour Welfare Fund',
  }
  ```
- Registered route in [`code/frontend/src/payroll/index.js`](file:///d:/Workspace/infinevo-platform/code/frontend/src/payroll/index.js):
  ```jsx
  {
    path: '/payroll/settings/lwf',
    element: <LwfSetting />,
  }
  ```

---

## 8. Database Changes

### Migration: `payroll/V192__org_lwf_setting.sql`

```sql
-- Migration: V192__org_lwf_setting.sql
-- Description: W-92 payroll.org_lwf_setting — tenant state Labour Welfare Fund configuration and registration

CREATE TABLE payroll.org_lwf_setting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    state_code VARCHAR(10) NOT NULL REFERENCES reference.state(code),
    is_enabled BOOLEAN NOT NULL DEFAULT true,
    deduction_cycle VARCHAR(32) NOT NULL DEFAULT 'HALF_YEARLY_JUNE_DECEMBER',
    employee_contribution NUMERIC(19,4) NOT NULL DEFAULT 12.0000,
    employer_contribution NUMERIC(19,4) NOT NULL DEFAULT 36.0000,
    registration_number VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_org_lwf_setting_tenant_state UNIQUE (tenant_id, state_code)
);

ALTER TABLE payroll.org_lwf_setting ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON payroll.org_lwf_setting
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

CREATE INDEX idx_org_lwf_setting_tenant_state ON payroll.org_lwf_setting(tenant_id, state_code);
```

---

## 9. Impact on Standing Rules

- **`tenant_id` & RLS:** `payroll.org_lwf_setting` contains `tenant_id` and strict PostgreSQL Row Level Security (RLS) policy using `app.current_tenant_id`.
- **Flyway Migrations:** Exactly 1 migration (`V192__org_lwf_setting.sql`). Schema names are explicitly qualified (`payroll.`).
- **Precision:** All monetary rates use `NUMERIC(19,4)` in database and `BigDecimal` / `Money` with explicit `RoundingMode.HALF_UP` in Java. Floating point types are prohibited.
- **Indexes:** Indexed on `(tenant_id, state_code)`.
- **Expand / Contract:** Creates new table with safe defaults; fully non-destructive.
- **Module Boundaries:** `payroll` reads `core.org.work_location` strictly through existing service contracts (`WorkLocationService.get()`).

---

## 10. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `LwfServiceTest.java` (Unit) | Backend | Proves state rule resolution: verifies MH half-yearly in Jun/Dec, KA annual in Dec, HR monthly, and zero deduction in non-deduction months. |
| `LwfControllerTest.java` (Unit) | Backend | Verifies `GET /settings/lwf` returns active state configurations; verifies `PUT /settings/lwf/{state}` updates rates and registration number. |
| `StatutoryLineContributorTest.java` | Backend | Verifies `LWF_EMPLOYEE` and `LWF_EMPLOYER` lines are added during pay run computation when eligible and in qualifying month. |
| `LwfSetting.test.jsx` (Component) | Frontend | Verifies state cards render correctly, switch toggles state enablement, and update form saves successfully. |
| `RunPage.test.jsx` | Frontend | Verifies LWF return download option exists in statutory returns dropdown. |

---

## 11. Verification Commands

```bash
# 1. Backend format check and unit tests
cd code/backend
mvn spotless:check -pl payroll
mvn test -pl payroll "-Dtest=LwfServiceTest,LwfControllerTest,StatutoryLineContributorTest"

# 2. Frontend lint and tests
cd ../frontend
npm run lint
npm test -- src/payroll/settings/LwfSetting.test.jsx
```

