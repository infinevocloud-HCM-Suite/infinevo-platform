# Feature: Company Locale on Tenant Admin Company Profile

| Field | Value |
|---|---|
| **Feature ID** | `W-116` · `CORE-SETTINGS` |
| **Promoted to** | `docs/target-state/features/W-116-company-locale-on-company-profile.md` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/core`, `code/frontend` (`src/core/settings`, `src/core/admin`) |
| **Related gaps** | founder 2026-11-05 during `W-84`; founder 2026-11-13 planned product roadmap (`W-116` Company locale) |
| **Status** | **In Review (pending manager approval)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | nothing |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` | 1 | **Pass (1 module)** |
| Flyway migration | None (creates 0 tables; `core.tenant.country_code`, `timezone`, `leave_year_start_month` already exist in `V033`) | 1 | **Pass (0 migrations)** |
| Externally testable behaviour | Tenant administrator manages Country, Timezone, and Leave Year Start on Company Profile (`/settings/company`); Timezone and Leave Year are editable any time; Country is editable only until the first pay run exists, then locked | 1 | **Pass (1 behaviour)** |
| Frontend area | `core/settings` (`CompanyProfile.jsx`, plus `TenantDetail.jsx`) | 1 | **Pass (1 area)** |

Within size cap across all four axes. No split required.

---

## 1. Problem

Today, country, timezone, and leave year start are treated as immutable platform setup values rather than company settings:
1. **Trapped in platform admin screen**: `country_code`, `timezone`, and `leave_year_start_month` exist in `core.tenant` (added in `V033__tenant_locale_columns.sql`) but are only visible to platform admins on `/admin/tenants/:id` (`TenantDetail.jsx`), where they are read-only after creation.
2. **Company profile is incomplete**: The tenant admin's Company Profile screen (`code/frontend/src/core/settings/CompanyProfile.jsx:34-40`) only allows editing the tagline and logo. A company operating in a different timezone or on a calendar-year leave cycle (Jan–Dec) cannot change their own settings.
3. **Pay run lock requirement**: Statutory setup, tax calculation, and pay run execution in India are strictly keyed to the Indian financial year and Indian statutory bodies (EPF, ESI, PT, TDS). Once a pay run has been processed in a tenant, allowing a tenant admin to change their country would corrupt downstream tax records and statutory ledger history.

This ticket moves Country, Timezone, and Leave Year Start into the tenant admin's Company Profile (`/settings/company`), allows Timezone and Leave Year Start to be updated at any time, and enforces a strict lock on Country once the first pay run exists (leaving platform admin override on `/admin/tenants/:id`).

---

## 2. Scope

### In scope
- **Backend locale update API (`code/backend/core`)**:
  - Update `TenantProfileRequest` and `TenantProfileResponse` to include `countryCode`, `timezone`, `leaveYearStartMonth`, and `countryLocked`.
  - In `TenantProfileServiceImpl`:
    - `timezone`: Validated against Java `ZoneId.getAvailableZoneIds()`. Editable any time by `core.tenant.manage`.
    - `leaveYearStartMonth`: Validated between 1 and 12 (1 = Jan, 4 = Apr). Editable any time by `core.tenant.manage`.
    - `countryCode`: 2-letter ISO code validated against `reference.country`.
    - **Lock rule**: Checked via pay run existence. If a pay run exists in `payroll.pay_run` for the tenant:
      - If requested by regular tenant admin (`!isPlatformAdmin`) with a changed `countryCode`: refused with HTTP 400 Bad Request (`"Country cannot be modified after the first pay run has been processed. Contact platform support."`).
      - Platform admin callers (holding `core.tenant.provision`) can override.
- **Frontend Company Profile (`code/frontend/src/core/settings/CompanyProfile.jsx`)**:
  - Country selector dropdown populated from `reference.country` (India `IN` selected by default).
  - When `countryLocked === true`: Country dropdown is disabled with tooltip: `"Locked after first pay run — contact platform admin to change"`.
  - Timezone selector dropdown: Shows standard timezones for the selected country (e.g. `Asia/Kolkata` for `IN`, US zones for `US`), with a "Show all timezones" toggle below.
  - Leave year start month selector: Select dropdown with 12 months (e.g. "April (Default — Indian Financial Year)", "January (Calendar Year)").
  - Form validation via Yup schema.

### Explicitly out of scope (Non-India Country Deep-Tail)
As noted on the founder roadmap, for a country outside India, the deep adaptations belong to future country engine tickets, none in this ticket:
- EPF, ESI, PT, TDS, and LWF calculation engine switches.
- Multi-country tax year rules outside April–March.
- Non-INR multi-currency presentation.
- Country-specific identity numbers (replacing PAN, Aadhaar, UAN).
- Alternate country setup templates outside India (`W-73.9`).
- Custom payroll pay frequencies (bi-weekly, weekly) and weekend calendars.

---

## 3. Flow

```
[Tenant Administrator Navigates to /settings/company]
                         │
                         ▼
[GET /api/v1/tenants/current/profile]
  ├── Returns { name, tagline, logoUrl, countryCode: "IN", timezone: "Asia/Kolkata", 
  │             leaveYearStartMonth: 4, countryLocked: false }
                         │
                         ▼
[Form Renders Country, Timezone, and Leave Year Selectors]
  ├── Timezone offers zones filtered by Country ("Asia/Kolkata") + "Show all"
  └── Leave Year Start offers 12 months
                         │
                         ▼
[User Edits Timezone & Leave Year ──► Clicks Save]
                         │
                         ▼
[PUT /api/v1/tenants/current/profile]
  ├── Check Pay Run Exists:
  │     ├── Country Unchanged or No Pay Run Exists ──► Update core.tenant ──► 200 OK
  │     └── Country Changed AND Pay Run Exists ──► Rejection 400 Bad Request
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/core`)

| Layer | File | Change |
|---|---|---|
| DTO | `com.infinevo.core.tenant.TenantProfileRequest.java` | Add `countryCode`, `timezone`, `leaveYearStartMonth` (`file:code/backend/core/src/main/java/com/infinevo/core/tenant/TenantProfileRequest.java:1-12`). |
| DTO | `com.infinevo.core.tenant.TenantProfileResponse.java` | Add `countryCode`, `timezone`, `leaveYearStartMonth`, and `boolean countryLocked` (`file:code/backend/core/src/main/java/com/infinevo/core/tenant/TenantProfileResponse.java:15-22`). |
| Service | `com.infinevo.core.tenant.TenantProfileServiceImpl.java` | In `read()`, select `country_code`, `timezone`, `leave_year_start_month`; compute `countryLocked` based on `hasPayRuns(tenantId)`; in `update()`, enforce lock guard and persist fields into `core.tenant` (`file:code/backend/core/src/main/java/com/infinevo/core/tenant/TenantProfileServiceImpl.java:68-128`). |
| Port | `com.infinevo.core.tenant.PayRunExistenceChecker.java` | **New**. Safe check port: queries `payroll.pay_run` existence without compile-time module coupling. |
| Reference | `com.infinevo.core.reference.CountryReferenceService.java` | Provide list of active countries from `reference.country` for dropdown options. |

### 4.2 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| Company profile | `code/frontend/src/core/settings/CompanyProfile.jsx` | Add Country, Timezone, and Leave Year Start form controls; disable Country when `profile.countryLocked` is true; include timezone filter and "Show all timezones" toggle (`file:code/frontend/src/core/settings/CompanyProfile.jsx:144-245`). |
| Service | `code/frontend/src/core/settings/companyProfileService.js` | Hand `countryCode`, `timezone`, `leaveYearStartMonth` in `update()` payload. |
| Timezones | `code/frontend/src/core/settings/timezones.js` | **New**. Helper mapping ISO country codes to primary IANA timezones with complete fallback list. |

---

## 5. Database Changes

> **Creates no database tables and executes 0 Flyway migrations.**

- [x] Standing rule: Columns `country_code CHAR(2)`, `timezone VARCHAR(64)`, and `leave_year_start_month SMALLINT` were created in `V033__tenant_locale_columns.sql`.
- [x] Standing rule: No schema changes; purely API and UI exposure of existing tenant columns.
- [x] Standing rule: No cross-module database foreign keys.

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `com.infinevo.core.tenant.TenantProfileServiceImplTest.java` | Validates updating timezone and leave year start month; validates updating country when no pay run exists; verifies country update rejected with 400 when pay run exists. |
| Unit | `com.infinevo.core.tenant.TimezoneValidatorTest.java` | Verifies invalid IANA zone strings rejected; verifies valid zone accepted. |
| Integration | `com.infinevo.core.tenant.TenantProfileIT.java` | End-to-end `GET /api/v1/tenants/current/profile` and `PUT /api/v1/tenants/current/profile` verifying updated fields persist in `core.tenant`. |
| Component | `code/frontend/src/core/settings/CompanyProfile.test.jsx` | Verifies rendering Country, Timezone, and Leave Year Start; verifies Country disabled when `countryLocked` is true; tests form submission. |

---

## 7. Verification

Verification commands and steps:

```bash
# 1. Run core backend unit and integration tests
./mvnw -pl core test -Dtest='TenantProfileServiceImplTest,TenantProfileIT,TimezoneValidatorTest'

# 2. Run frontend profile tests
cd code/frontend
npm test -- src/core/settings/CompanyProfile.test.jsx

# 3. Frontend lint check
npm run lint
```

### Verification matrix

| Check | Action | Expected Result |
|---|---|---|
| Fetch Profile | `GET /api/v1/tenants/current/profile` | Returns `countryCode: "IN"`, `timezone: "Asia/Kolkata"`, `leaveYearStartMonth: 4`, `countryLocked: false/true`. |
| Update Timezone & Leave Year | `PUT /api/v1/tenants/current/profile` with `timezone: "Asia/Dubai"`, `leaveYearStartMonth: 1` | Successfully saved (200 OK); values updated in `core.tenant`. |
| Country Locked Guard | Tenant has processed pay run; tenant admin submits `countryCode: "US"` | HTTP 400 Bad Request: `"Country cannot be modified after the first pay run has been processed"`. |
| Platform Admin Override | Platform admin updates country on `/admin/tenants/:id` | Allowed with confirmation dialog. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Changing leave year start after leave allocations granted | Medium | Leave allocations already evaluate `leave_year_start_month` dynamically (D-93); UI displays an informational note: "Changing leave year start applies to upcoming allocation periods". |
| Invalid timezone string corrupting employee clock calculations | Low | Backend strictly validates timezone with `ZoneId.of(timezone)` before writing to database. |

---

## 9. Rollback

1. Field values can be reverted to defaults (`country_code = 'IN'`, `timezone = 'Asia/Kolkata'`, `leave_year_start_month = 4`) directly in `core.tenant`.
2. Frontend `CompanyProfile.jsx` changes are backwards compatible with older backend responses.

