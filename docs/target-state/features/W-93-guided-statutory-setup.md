# Feature: Guided Statutory Setup

| Field | Value |
|---|---|
| **Feature ID** | `W-93` · `PAY-STATUTORY-GUIDED-SETUP` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/payroll`, `code/frontend` (`src/payroll/settings`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-93`; legacy lacked setup assistance (`legacy/Payroll-Bend-SBoot/.../OrgStatutoryController.java:23-35` returned raw config DTO without guidance) |
| **Status** | Implemented - In Review |
| **Blocked by** | nothing |

---

## 1. What and why

Today, establishing statutory compliance in `infinevo-platform` requires a payroll administrator to manually configure three disparate settings screens:
1. **EPF Settings** ([`StatutorySettingsController.java:38-51`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/settings/StatutorySettingsController.java#L38-L51), `/payroll/settings/epf`): Contains 18+ technical fields including contribution splits (12% employee, 3.67% PF, 8.33% EPS, 0.50% EDLI, 0.50% admin charges), wage ceiling restriction flags, and CTC inclusion toggles ([`EpfSettingRequest.java:10-32`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/settings/EpfSettingRequest.java#L10-L32)).
2. **ESI Settings** ([`StatutorySettingsController.java:53-66`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/settings/StatutorySettingsController.java#L53-L66), `/payroll/settings/esi`): Requires setting deduction percentages (0.75% employee, 3.25% employer), the statutory wage ceiling (₹21,000), and structure inclusions ([`EsiSettingRequest.java:10-19`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/settings/EsiSettingRequest.java#L10-L19)).
3. **Professional Tax (PT) Settings** ([`ProfessionalTaxController.java:38-58`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/pt/ProfessionalTaxController.java#L38-L58), `/payroll/settings/professional-tax`): Requires navigating each operating state individually to input registration certificates and configure slabs ([`ProfessionalTaxServiceImpl.java:65-75`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/pt/ProfessionalTaxServiceImpl.java#L65-L75)).

The tenant setup checklist ([`SetupStepCatalogue.java:37-39`](file:///d:/Workspace/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/setup/SetupStepCatalogue.java#L37-L39)) tracks these as three separate steps: `EPF` (display order 7), `ESI` (order 8), and `PROFESSIONAL_TAX` (order 9). For a new business owner or HR administrator without deep payroll compliance expertise, navigating three disparate screens with 30+ legal dials creates significant onboarding friction and risk of compliance misconfiguration.

This feature delivers **Guided Statutory Setup**: a conversational, question-driven wizard that asks straightforward operational questions in plain English, automatically derives and populates statutory contribution rules and legal wage caps, links state PT registration numbers to the tenant's operating work locations, and atomically saves EPF, ESI, and PT compliance in a single transaction.

Completing the guided wizard automatically marks all three statutory steps (`EPF`, `ESI`, `PROFESSIONAL_TAX`) completed in the setup checklist ([`PayrollSetupStepConfiguration.java:119-177`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/setup/PayrollSetupStepConfiguration.java#L119-L177)).

---

## 2. Size Cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | **none** (reuses existing `payroll.epf_setting`, `payroll.esi_setting`, `payroll.org_pt_override`, `payroll.org_pt_override_slab`, `reference.pt_state`, and `reference.pt_slab`) | 1 |
| Externally testable behaviour | Tenant administrator completes the guided statutory setup questionnaire, which atomically configures and enables/disables EPF, ESI, and PT compliance with legal defaults, and returns unified statutory status | 1 |
| Frontend area | `code/frontend/src/payroll/settings` (`GuidedStatutorySetup.jsx`, `guidedStatutoryService.js`) | 1 |

---

## 3. Scope

### In Scope
1. **Read API (`GET /api/v1/payroll/settings/guided-setup`):** Retrieves the tenant's statutory readiness, auto-detecting operating states from `core.work_location`, reporting whether each state levies PT, returning current EPF/ESI configurations, and providing legal statutory defaults.
2. **Apply API (`POST /api/v1/payroll/settings/guided-setup`):** Validates answers to the statutory questionnaire and atomically saves EPF, ESI, and PT configurations in a single `@Transactional` service call.
3. **Statutory Rule Auto-Derivation:**
   - **EPF:** Standard statutory rates (12.0000% EE, 12.0000% ER split into 3.6700% PF + 8.3300% EPS, 0.5000% EDLI, 0.5000% Admin), ₹15,000 statutory ceiling, age 58 senior threshold.
   - **ESI:** Standard statutory rates (0.7500% EE, 3.2500% ER), ₹21,000 statutory wage ceiling.
   - **PT:** Auto-detects operating states from active work locations, maps PT levy status from `reference.pt_state`, links PTRC numbers to `payroll.org_pt_override`, and seeds standard slabs from `reference.pt_slab`.
4. **Setup Checklist Resolution:** Saving statutory settings via the wizard marks `EPF`, `ESI`, and `PROFESSIONAL_TAX` as completed in `SetupChecklist` by clearing `updated_by` template markers and writing persistence rows.
5. **Interactive Frontend Wizard:** 5-step wizard component in `src/payroll/settings/GuidedStatutorySetup.jsx` at `/payroll/settings/guided-setup`, with discovery banners in `SettingsLayout.jsx` and direct links from `SetupChecklist.jsx`.

### Out of Scope
1. **New Database Tables or Flyway Migrations:** Zero schema modifications. All questionnaire outputs map directly onto existing tables.
2. **Direct Government Portal Integration:** No direct automated API sync with EPFO Unified Portal or ESIC Portal (this configures calculation rules within the tenant's payroll engine).
3. **Custom PT Slab Authoring in Wizard:** The wizard applies standard government reference slabs for states levying PT. Custom slab overrides remain available on `/payroll/settings/professional-tax` for advanced use cases.
4. **Income Tax Deductor Setup:** Tax deductor configuration (TAN, Deductor Type, AO Code) belongs to `W-36.3` (`payroll.tax_deductor`).

---

## 4. Questionnaire Logic & Rule Derivation

The guided wizard guides the administrator through 4 structured questionnaire sections followed by a final review:

```
[Overview & Operating Locations] ──▶ [1. EPF Setup] ──▶ [2. ESI Setup] ──▶ [3. Professional Tax] ──▶ [4. Review & Confirm]
```

### Section 1: Employees' Provident Fund (EPF)

Under the *Employees' Provident Funds and Miscellaneous Provisions Act, 1952*:

1. **Question:** *"Is your organization registered with the EPFO?"*
   - Options:
     - `Yes, we are registered` (Mandatory if 20+ employees, or voluntary under Section 1(4)).
     - `No, not registered` (Exempt / fewer than 20 employees and not voluntarily registered).
2. **If "Yes" is selected:**
   - **Establishment Code / Registration Number** (*Required*): String, $\le 32$ characters (e.g., `DLCPM1234567000` or regional equivalent).
   - **Registration Date** (*Optional*): `LocalDate`.
   - **Question:** *"Restrict EPF contribution to the statutory wage ceiling of ₹15,000/month?"*
     - `Yes (Recommended)` $\rightarrow$ Caps EPF calculation base to ₹15,000 even if Basic pay is higher (`restrictEmployeeToCeiling = true`, `restrictEmployerToCeiling = true`, `prorateRestrictedWage = true`).
     - `No` $\rightarrow$ Calculates 12% on actual Basic wages without ceiling capping (`restrictEmployeeToCeiling = false`, `restrictEmployerToCeiling = false`, `prorateRestrictedWage = false`).
   - **Question:** *"Include Employer PF contribution (12%) in employee CTC?"*
     - Toggle (`true` / `false`): Sets `includeEmployerInCtc` and `includeEmployerInStructure`.
   - **Auto-Derived Statutory Constants:**
     - `isEnabled = true`
     - `deductionCycle = MONTHLY`
     - `employeeRate = 12.0000`
     - `employerRate = 12.0000`
     - `epsRate = 8.3300` (8.33% diverted to Pension Scheme)
     - `edliRate = 0.5000` (0.50% Insurance)
     - `adminChargeRate = 0.5000` (0.50% EPFO Admin charges)
     - `wageCeiling = 15000.0000`
     - `considerEarnedWage = true`
     - `epsSeniorAge = 58`
3. **If "No" is selected:**
   - `isEnabled = false`
   - Default rates maintained, registration number set to `null`.

---

### Section 2: Employee State Insurance (ESI)

Under the *Employees' State Insurance Act, 1948*:

1. **Question:** *"Is your organization covered under the ESI Act?"*
   - Options:
     - `Yes, we are registered` (Mandatory for non-seasonal factories/establishments with 10+ employees in notified areas earning gross wages $\le$ ₹21,000/month).
     - `No, not covered / exempt`
2. **If "Yes" is selected:**
   - **ESI Employer Registration Code** (*Required*): 17-digit numeric establishment code, string $\le 32$ characters (e.g., `31000123450000101`).
   - **Registration Date** (*Optional*): `LocalDate`.
   - **Question:** *"Include Employer ESI contribution (3.25%) in employee CTC?"*
     - Toggle (`true` / `false`): Sets `includeEmployerInCtc` and `includeInStructure`.
   - **Auto-Derived Statutory Constants:**
     - `isEnabled = true`
     - `deductionCycle = MONTHLY`
     - `employeeRate = 0.7500` (0.75% deduction from gross)
     - `employerRate = 3.2500` (3.25% employer contribution)
     - `wageCeiling = 21000.0000` (Statutory gross wage ceiling)
3. **If "No" is selected:**
   - `isEnabled = false`
   - Default rates maintained, registration number set to `null`.

---

### Section 3: Professional Tax (PT)

Governed under state statutory enactments (Article 276 of the Constitution of India):

1. **Operating State Detection:**
   - The backend queries active work locations via `WorkLocationService.list(true)` ([`ProfessionalTaxServiceImpl.java:268-279`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/pt/ProfessionalTaxServiceImpl.java#L268-L279)).
   - Unique state codes are matched against national reference table `reference.pt_state` (`levies_pt` column, [`PtState.java:23-24`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/pt/PtState.java#L23-L24)).
2. **State Categorization in UI:**
   - **States levying PT** (e.g. Maharashtra `MH`, Karnataka `KA`, Tamil Nadu `TN`, Telangana `TG`, West Bengal `WB`, Gujarat `GJ`, Andhra Pradesh `AP`):
     - Displays state name with badge: `PT Applicable`.
     - Prompts for **PTRC Number (Professional Tax Registration Certificate)**: string $\le 32$ characters (e.g., `27123456789P` for MH, `29123456789` for KA).
     - Notifies user: *"Standard statutory state slabs will apply automatically from national tax rules. You can override individual slabs later in Professional Tax settings if needed."*
   - **States not levying PT** (e.g. Delhi `DL`, Haryana `HR`, Rajasthan `RJ`, Uttar Pradesh `UP`):
     - Displays state name with badge: `Exempt / No PT Levied`.
     - Informs user: *"This state does not levy Professional Tax. No registration certificate or salary deduction is required."*
3. **PT Persistence Rule:**
   - For states levying PT where a registration number is provided:
     - An `OrgPtOverride` record is created or updated in `payroll.org_pt_override` ([`OrgPtOverride.java:18-38`](file:///d:/Workspace/infinevo-platform/code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/pt/OrgPtOverride.java#L18-L38)).
     - If the override does not yet have slabs, standard slabs from `reference.pt_slab.inForce(stateCode, effectiveFrom)` are copied into `payroll.org_pt_override_slab`, ensuring the state has an active configuration with its PTRC number.

---

### Section 4: Review & Atomic Confirmation

The wizard presents a comprehensive visual summary of all statutory decisions:
- **EPF Card:** Enabled status, Registration Number, Capped at ₹15,000 or Actual, CTC inclusion status.
- **ESI Card:** Enabled status, 17-digit Registration Number, ₹21,000 threshold, CTC inclusion status.
- **PT Card:** Operating states table listing state name, PT levy status, and PTRC registration number.

Clicking **"Complete Statutory Setup"** issues a single `POST` request to `/api/v1/payroll/settings/guided-setup` executed inside an atomic transaction.

---

## 5. Backend Architecture

### Package: `com.infinevo.payroll.statutory.guided`

| Layer | Class / Record | Responsibility |
|---|---|---|
| Controller | `GuidedStatutorySetupController.java` | REST endpoints for statutory readiness and questionnaire submission |
| Service | `GuidedStatutorySetupService.java` | Interface defining `getReadiness(tenantId)` and `applySetup(request)` |
| ServiceImpl | `GuidedStatutorySetupServiceImpl.java` | Coordinates `EpfSettingRepository`, `EsiSettingRepository`, `WorkLocationService`, and `OrgPtOverrideRepository` in an atomic `@Transactional` boundary |
| Request DTO | `GuidedStatutorySetupRequest.java` | Combined payload containing EPF, ESI, and PT answers |
| Sub-DTO | `EpfSetupRequest.java` | EPF questions: registration status, code, ceiling preference, CTC inclusion |
| Sub-DTO | `EsiSetupRequest.java` | ESI questions: registration status, 17-digit code, CTC inclusion |
| Sub-DTO | `PtStateSetupRequest.java` | State code, PTRC registration number, effective date |
| Response DTO | `GuidedStatutoryReadinessResponse.java` | Operating states, PT levy flags, existing configurations, legal default rates |
| Sub-DTO | `PtStateReadiness.java` | Details for one operating state (stateCode, stateName, leviesPt, existing PTRC) |
| Sub-DTO | `StatutoryDefaults.java` | Legal standard percentages and ceilings returned to frontend |
| Response DTO | `GuidedStatutorySetupResponse.java` | Unified confirmation returning saved EPF, ESI, and PT state results |

---

### API Contract

#### 1. Get Statutory Readiness
```http
GET /api/v1/payroll/settings/guided-setup
```
* **Authorization:** `@RequiresAction("payroll.salary.read")`
* **Module Guard:** `@RequiresModule(PlatformModule.PAYROLL)`
* **Response `200 OK`:**
```json
{
  "code": 0,
  "message": "Statutory readiness retrieved successfully",
  "data": {
    "epfConfigured": false,
    "epf": {
      "isEnabled": false,
      "registrationNumber": null,
      "restrictEmployeeToCeiling": false,
      "includeEmployerInCtc": false
    },
    "esiConfigured": false,
    "esi": {
      "isEnabled": false,
      "registrationNumber": null,
      "includeEmployerInCtc": false
    },
    "operatingStates": [
      {
        "stateCode": "MH",
        "stateName": "Maharashtra",
        "leviesPt": true,
        "registrationNumber": null,
        "configured": false
      },
      {
        "stateCode": "DL",
        "stateName": "Delhi",
        "leviesPt": false,
        "registrationNumber": null,
        "configured": true
      }
    ],
    "defaults": {
      "epfEmployeeRate": 12.0000,
      "epfEmployerRate": 12.0000,
      "epfEpsRate": 8.3300,
      "epfEdliRate": 0.5000,
      "epfAdminRate": 0.5000,
      "epfWageCeiling": 15000.0000,
      "esiEmployeeRate": 0.7500,
      "esiEmployerRate": 3.2500,
      "esiWageCeiling": 21000.0000
    }
  }
}
```

#### 2. Apply Guided Setup
```http
POST /api/v1/payroll/settings/guided-setup
```
* **Authorization:** `@RequiresAction("payroll.settings.manage")`
* **Module Guard:** `@RequiresModule(PlatformModule.PAYROLL)`
* **Request Body:**
```json
{
  "epf": {
    "registered": true,
    "registrationNumber": "DLCPM1234567000",
    "registrationDate": "2026-04-01",
    "restrictToWageCeiling": true,
    "includeEmployerInCtc": true,
    "includeEdliInCtc": false,
    "includeAdminInCtc": false
  },
  "esi": {
    "registered": true,
    "registrationNumber": "31000123450000101",
    "registrationDate": "2026-04-01",
    "includeEmployerInCtc": true
  },
  "ptStates": [
    {
      "stateCode": "MH",
      "registrationNumber": "27123456789P",
      "effectiveFrom": "2026-04-01"
    }
  ]
}
```
* **Response `200 OK`:**
```json
{
  "code": 0,
  "message": "Guided statutory setup applied successfully",
  "data": {
    "epf": {
      "isEnabled": true,
      "registrationNumber": "DLCPM1234567000",
      "employeeRate": 12.0000,
      "employerRate": 12.0000,
      "wageCeiling": 15000.0000,
      "source": "PERSISTED"
    },
    "esi": {
      "isEnabled": true,
      "registrationNumber": "31000123450000101",
      "employeeRate": 0.7500,
      "employerRate": 3.2500,
      "wageCeiling": 21000.0000,
      "source": "PERSISTED"
    },
    "ptStates": [
      {
        "stateCode": "MH",
        "stateName": "Maharashtra",
        "registrationNumber": "27123456789P",
        "source": "OVERRIDE"
      }
    ],
    "allStatutoryConfigured": true
  }
}
```

---

## 6. Frontend Architecture

### File Structure: `code/frontend/src/payroll/settings/`

| File | Purpose |
|---|---|
| `GuidedStatutorySetup.jsx` (new) | 5-step wizard component using Ant Design `Steps`, `Card`, `Form`, `Radio`, and `Input` |
| `guidedStatutoryService.js` (new) | HTTP client for `GET/POST /v1/payroll/settings/guided-setup` |
| `SettingsLayout.jsx` | Adds contextual banner atop `/payroll/settings/*`: *"Setting up statutory compliance for the first time? [Launch Guided Setup Wizard]"* |
| `code/frontend/src/payroll/index.js` | Mounts route `/payroll/settings/guided-setup` wrapped in `SettingsLayout` |

### Wizard Step Breakdown

1. **Step 0: Overview & Work Locations:**
   - Informs admin of detected operating states.
   - If no work locations are configured in Core, displays a guidance alert with link to `/org/work-locations`.
2. **Step 1: EPF Questions:**
   - Radio group: Registered vs Not Registered.
   - Inputs for Registration Code, Registration Date.
   - Toggle: Wage ceiling capping (₹15,000) vs Actual Basic.
   - Toggle: Include Employer PF in CTC.
3. **Step 2: ESI Questions:**
   - Radio group: Covered vs Not Covered.
   - Input for 17-digit Employer Code.
   - Toggle: Include Employer ESI in CTC.
4. **Step 3: Professional Tax Details:**
   - Interactive table or card list of all operating states.
   - States with `leviesPt == true` offer an input for the state's PTRC Number and effective date.
   - States with `leviesPt == false` render a grey tag "Exempt".
5. **Step 4: Review & Confirm:**
   - Comprehensive card summary of EPF, ESI, and PT configurations.
   - "Apply Statutory Setup" primary button with spinner during submission.
   - Success state with direct buttons: *"Go to Setup Checklist"*, *"Configure Salary Components"*, or *"View Individual Settings"*.

---

## 7. Security and Entitlements

- **Tenant Isolation:** All operations enforce `UUID tenantId = TenantContext.require()`. Repositories query strictly by `tenantId`.
- **Permissions:**
  - `GET /api/v1/payroll/settings/guided-setup` requires seeded permission `payroll.salary.read` (already granted to `payroll-officer` and `tenant-admin`).
  - `POST /api/v1/payroll/settings/guided-setup` requires seeded permission `payroll.settings.manage` (already granted to `payroll-officer` and `tenant-admin`).
- **Module Guard:** Guarded by `@RequiresModule(PlatformModule.PAYROLL)`. If tenant lacks `PAYROLL`, returns `403 Forbidden`.
- **Audit Logging:** Reuses `@Audited` on `EpfSetting`, `EsiSetting`, and `OrgPtOverride`. Updates record the authenticated user's ID/email as `updated_by`.

---

## 8. Setup Checklist Integration

In `SetupStepCatalogue.java:37-39`, the setup checklist defines:
- `EPF` (display order 7)
- `ESI` (display order 8)
- `PROFESSIONAL_TAX` (display order 9)

In `PayrollSetupStepConfiguration.java:119-177`:
- `epfSetupStepChecker`: checks `epfSettingRepository.findByTenantId(tenantId).isPresent()` and that `updated_by` is not the template contributor.
- `esiSetupStepChecker`: checks `esiSettingRepository.findByTenantId(tenantId).isPresent()` and that `updated_by` is not the template contributor.
- `professionalTaxSetupStepChecker`: checks `orgPtOverrideRepository.existsByTenantId(tenantId)` or that all active work locations have valid state codes.

Because `GuidedStatutorySetupServiceImpl.applySetup` persists actual `EpfSetting`, `EsiSetting`, and `OrgPtOverride` entities stamped with the user's actor identity, **completing this wizard automatically advances all three checklist items to 100% completed**, accelerating tenant onboarding from three fragmented tasks into one cohesive experience.

---

## 9. Risks and Rollback

### Risks
1. **Tenant has no work locations created yet:**
   - *Risk:* If a tenant admin runs guided statutory setup before configuring work locations in Core, the operating state list is empty.
   - *Mitigation:* The readiness endpoint returns an empty `operatingStates` list without error; the wizard displays a helpful advisory alert informing the admin they can configure locations now or add state PTRC numbers later under `/payroll/settings/professional-tax`.
2. **Accidentally overwriting existing customized PT slabs:**
   - *Risk:* If a tenant with custom state PT slabs runs guided setup to update their PTRC number, custom slabs could be overwritten.
   - *Mitigation:* The service checks if an `OrgPtOverride` already exists for the state; if slabs already exist, it preserves the custom slabs and only updates `registration_number` and `effective_from`.
3. **Invalid EPF / ESI registration numbers:**
   - *Risk:* Admin enters invalid characters or excessively long strings.
   - *Mitigation:* Request validation validates registration numbers ($\le 32$ characters, non-blank when registered = true) and returns `400 BAD_REQUEST` with clear field errors matching `StatutorySettingsValidationException`.

### Rollback
- Zero database migrations were added. Rollback involves reverting code commits. No schema migrations, table drops, or database repairs are required.

---

## 10. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `GuidedStatutorySetupServiceTest.java` (Unit) | Backend | Verifies `getReadiness` collects active work location states, detects PT levy status, and formats defaults. Verifies `applySetup` derives 12% EPF rates, ₹15k ceiling, 0.75%/3.25% ESI rates, ₹21k ceiling, updates `OrgPtOverride` with PTRC number, and preserves existing slabs. |
| `GuidedStatutorySetupIT.java` (Integration) | Backend | Verifies `GET /api/v1/payroll/settings/guided-setup` returns `200 OK` with defaults; `POST` persists records to `payroll.epf_setting`, `payroll.esi_setting`, and `payroll.org_pt_override`; verifies checklist steps `EPF`, `ESI`, and `PROFESSIONAL_TAX` transition to completed. Enforces `payroll.settings.manage` authorization. |
| `GuidedStatutorySetup.test.jsx` (Component) | Frontend | Verifies wizard renders all 5 steps; step navigation validates required registration numbers; toggling "Not Registered" disables inputs; submittal calls `guidedStatutoryService.save` and displays success screen. |

---

## 11. Verification Commands

```bash
# 1. Backend build, spotless and tests
cd code/backend
mvn spotless:check -pl payroll
mvn test -pl payroll -Dtest=GuidedStatutorySetupServiceTest,GuidedStatutorySetupIT

# 2. Frontend lint and component tests
cd ../frontend
npm run lint
npm test -- GuidedStatutorySetup.test.jsx
```

