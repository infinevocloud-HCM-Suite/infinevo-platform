# Feature: Company locale on the tenant admin's Company profile

| Field | Value |
|---|---|
| **Feature ID** | `W-116` · `CORE-SETTINGS` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/core`, `code/backend/payroll` (one class), `code/backend/migration`, `code/frontend/src/core/settings`, `code/frontend/src/core/admin` |
| **Related gaps** | founder 2026-11-05 during `W-84`; founder 2026-11-13 roadmap |
| **Status** | Ready — approved 2026-11-19. Not built. |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | nothing |

## The rules

| Field | Who edits it | When it locks |
|---|---|---|
| Timezone | Tenant admin, on Company profile | Never |
| Leave year start month | Tenant admin, on Company profile | **Once the company has any leave allocation or leave request** (founder, 2026-11-19). Then it is read-only: "Contact support to change the leave year." |
| Country | Tenant admin, on Company profile | Once the company has a pay run that is not cancelled. After that only the platform admin can change it, on the tenant page. |

Why the leave year locks: allocations carry fixed year dates, and resets and year labels are
worked out from the start month (`LeaveResetServiceImpl.java:152-170`,
`LeaveDateUtils.java:68-80`). Changing the month under existing rows could overlap years or
repeat a reset. Locking needs no data change.

## Size

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` (plus one port implementation in `payroll`) | 1 | Pass |
| Flyway migration | **1** (was wrongly "0") — one function for the platform admin's country change | 1 | Pass |
| Testable behaviour | a tenant admin edits the three fields within the lock rules | 1 | Pass |
| Frontend area | `core/settings` and one card in `core/admin` | 1 | **Over by one** |

Size **M**. The admin-side change is one small form on `TenantDetail.jsx`; splitting it off
would leave a locked country with no way to change it.

---

## 1. Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Country, timezone and leave year start are set once at tenant creation and cannot be changed by anyone. | `code/backend/core/src/main/java/com/infinevo/core/tenant/TenantRequest.java:16-18`; `TenantDetail.jsx:241-242` only displays them |
| 2 | The tenant admin's Company profile edits only tagline and logo. | `code/backend/core/src/main/java/com/infinevo/core/tenant/TenantProfileRequest.java`; `TenantProfileServiceImpl.java:86-92` |
| 3 | The profile response already returns `leaveYearStartMonth` (D-93) but not country or timezone. | `TenantProfileResponse.java:15-16` |
| 4 | No endpoint lists countries. Only `/api/v1/reference/country-templates` exists. | `code/backend/core/src/main/java/com/infinevo/core/template/CountryTemplateController.java:16` |

## 2. Scope

**In scope**

- Country, timezone and leave year start on the Company profile, with the lock rules above.
- `countryLocked` and `leaveYearLocked` in the profile response.
- A countries read endpoint from `reference.country`.
- A pay-run check that `core` defines and `payroll` implements.
- Platform admin country change on the tenant page.

**Out of scope**

- Anything that makes a non-India country *work*: statutory engines, tax year, currency, identity numbers, country templates (`W-73.9`), pay frequencies.
- Changing the leave year start once leave exists. That is a support task, not a screen.
- Platform admin change of timezone or leave year. The tenant admin has those.

## 3. Flow

```
Tenant admin --> /settings/company --> GET /api/v1/tenants/current/profile
             --> edits --> PUT /api/v1/tenants/current/profile --> TenantProfileServiceImpl.update
                 timezone ............ valid IANA zone? ......................... save
                 leaveYearStartMonth . changed and leave exists? --> 409 ....... else save
                 countryCode ......... changed and pay run exists? --> 409 ..... else save

Platform admin --> /admin/tenants/:id --> PUT /api/v1/tenants/{id}/country --> core.set_tenant_country(…)
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| DTO | `core/.../tenant/TenantProfileRequest.java` | Add `String countryCode`, `String timezone`, `Short leaveYearStartMonth`. |
| DTO | `core/.../tenant/TenantProfileResponse.java`, `TenantBranding.java` | Add `countryCode`, `timezone`, `boolean countryLocked`, `boolean leaveYearLocked`. `leaveYearStartMonth` is already there. Keep the older constructors. |
| Service | `core/.../tenant/TenantProfileServiceImpl.java` | `read` (line 112) also selects `country_code`, `timezone`. `update` (line 77) applies the rules in §4.1. |
| Port | `core/.../tenant/PayRunExistenceChecker.java` | **New** interface: `boolean hasProcessedPayRun(UUID tenantId)`. `core` only declares it. Injected as `ObjectProvider`; with no implementation present the country is not locked. |
| Port impl | `payroll/.../payrun/PayrollPayRunExistenceChecker.java` | **New.** Queries `payroll.payrun` for a row with `status <> 'CANCELLED'`. `core` never reads the `payroll` schema. Same pattern as `SetupStepChecker` and `TenantTemplateContributor`, which `payroll` already implements. |
| Repository | `core/.../leave/LeaveAllocationRepository.java`, `LeaveRequestRepository.java` | Add `boolean existsByTenantId(UUID tenantId)` to each. |
| Controller | `core/.../reference/CountryController.java` | **New.** `GET /api/v1/reference/countries` → `[{ code, name }]` from `reference.country`, ordered by name. Signed-in users; no action needed (reference data). |
| Controller | `core/.../tenant/TenantController.java` | **New** `PUT /api/v1/tenants/{id}/country`. |
| Service | `core/.../tenant/TenantServiceImpl.java` | **New** `changeCountry(UUID tenantId, String countryCode)`: `platformTenant.requirePlatformTenant()`, refuse the platform tenant itself, validate the code, call `core.set_tenant_country(?, ?)`. |

"Processed pay run" means any row in `payroll.payrun` whose status is not `CANCELLED` —
`DRAFT`, `LOCKED`, `COMPUTING`, `COMPUTED`, `FAILED`, `APPROVED` or `PAID`
(`code/backend/migration/src/main/resources/db/migration/payroll/V055__payrun.sql:16-17`).
A draft counts: statutory setup is already in use by then.

### 4.1 Rules in `update`

| Field | In the request | Result |
|---|---|---|
| `countryCode`, `timezone`, `leaveYearStartMonth` | missing or null | **Unchanged.** An older client that sends only tagline and logo keeps working. |
| `tagline`, `logoDocumentId` | missing or null | Cleared — today's behaviour, not changed here. |
| `timezone` | not a zone in `ZoneId.getAvailableZoneIds()` | `400 VALIDATION_FAILED` |
| `leaveYearStartMonth` | not 1–12 | `400 VALIDATION_FAILED` |
| `leaveYearStartMonth` | differs from the stored value, and a leave allocation or leave request exists | `409 CONFLICT`: "The leave year cannot be changed once leave has been allocated or requested. Contact support." |
| `countryCode` | not in `reference.country` | `400 VALIDATION_FAILED` |
| `countryCode` | differs from the stored value, and `hasProcessedPayRun` is true | `409 CONFLICT`: "The country cannot be changed after the first pay run. Contact support." |
| any locked field | same as the stored value | Accepted. The screen sends the whole form. |

All checks run before any write; one failure saves nothing.

### 4.2 API contract

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/tenants/current/profile` | — | `{ name, tagline, logoDocumentId, logoUrl, leaveYearStartMonth, countryCode, timezone, countryLocked, leaveYearLocked }` | `core.tenant.read` |
| `PUT` | `/api/v1/tenants/current/profile` | `{ tagline, logoDocumentId, countryCode?, timezone?, leaveYearStartMonth? }` | same as GET | `core.tenant.manage` |
| `GET` | `/api/v1/reference/countries` | — | `[{ code, name }]` | signed in |
| `PUT` | `/api/v1/tenants/{id}/country` | `{ countryCode }` | `TenantOverview` | `core.tenant.provision`, platform tenant only |

The platform admin's change ignores the pay-run lock. That is its purpose. It is audited.

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/core/settings/CompanyProfile.jsx` | Add Country, Timezone and Leave year start to the Formik form and the Yup schema. Country is disabled with "Locked after the first pay run — contact support" when `countryLocked`. Leave year start is disabled with "Locked once leave has been allocated or requested — contact support" when `leaveYearLocked`. Timezone lists the zones for the chosen country first, with "Show all timezones". A 409 from the server is shown next to its field. |
| `code/frontend/src/core/settings/companyProfileService.js` | Send the three fields; add `countries()`. |
| `code/frontend/src/core/settings/timezones.js` | **New.** Country code → usual IANA zones; the full list comes from `Intl.supportedValuesOf('timeZone')`. |
| `code/frontend/src/core/admin/TenantDetail.jsx` | Next to "Country" (line 241) add "Change", shown with `core.tenant.provision`: a country select and a confirm dialog — "Changing the country does not change statutory setup, pay runs or templates already in place." |
| `code/frontend/src/core/admin/tenantService.js` | Add `setCountry(id, countryCode)`. |

**Routes added:** none. `/settings/company` and `/admin/tenants/:id` exist.

## 6. Database changes

| Migration | Contents | Tenant-aware? | Reversible? |
|---|---|---|---|
| next free Flyway version at build time, `core/V…__set_tenant_country.sql` | `core.set_tenant_country(p_tenant_id uuid, p_country_code char(2))` — updates `core.tenant.country_code`, `updated_at`; raises if the tenant is not found. `SECURITY DEFINER`, `SET search_path = pg_catalog, pg_temp`, `REVOKE EXECUTE … FROM PUBLIC`, `GRANT EXECUTE … TO app_user`. | n/a — no table | yes — drop the function |

Why a function: the platform admin is bound to the platform tenant, and row-level security
hides other tenants' rows from a plain `UPDATE`. This is the pattern of
`core.set_subscription_plan` (`core/V188__subscription_plan.sql:27-53`).

The three columns already exist (`core/V033__tenant_locale_columns.sql:5-8`). The tenant
admin's own update is a plain `UPDATE core.tenant` under its own tenant binding, as the
tagline update is today.

- [x] No new table
- [x] Expand only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../tenant/TenantProfileServiceTest.java` (extend) | Every row of §4.1, including "missing means unchanged" and "same value on a locked field is accepted" |
| Integration | `core/src/test/.../tenant/TenantProfileIT.java` (extend) | GET returns the new fields; PUT saves them; leave year 409 after an allocation is inserted, and after a request is inserted |
| Integration | `app/src/test/.../CountryLockIT.java` | With the real `payroll` implementation: `DRAFT` run locks; a tenant with only a `CANCELLED` run is not locked |
| Integration | `core/src/test/.../tenant/TenantCountryChangeIT.java` | Platform admin changes a locked tenant's country; a tenant admin calling the endpoint gets 403; the platform tenant's own country is refused |
| Unit | `core/src/test/.../reference/CountryControllerTest.java` | Returns codes and names, ordered |
| Component | `code/frontend/src/core/settings/CompanyProfile.test.jsx` (extend) | The three controls; each lock disables its control and shows its note; a 409 shows by the field |
| Component | `code/frontend/src/core/admin/TenantDetail.test.jsx` (extend) | "Change" needs `core.tenant.provision`; confirm sends the new code |

## 8. Verification

```bash
cd code/backend && ./mvnw -pl core,payroll,migration,app verify
cd code/frontend && npm test -- src/core/settings src/core/admin && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| `GET /api/v1/tenants/current/profile` on a new tenant | `countryCode: "IN"`, `timezone: "Asia/Kolkata"`, `leaveYearStartMonth: 4`, both locks `false` | not run |
| PUT `timezone: "Asia/Dubai"` | 200, saved | not run |
| PUT `timezone: "Mars/Olympus"` | 400 | not run |
| PUT `leaveYearStartMonth: 1`, no leave data | 200, saved | not run |
| Allocate leave to one employee, then PUT `leaveYearStartMonth: 4` | 409; value unchanged; GET shows `leaveYearLocked: true` | not run |
| PUT only `{ tagline, logoDocumentId }` | 200; country, timezone and leave year unchanged | not run |
| Create a draft pay run, then PUT `countryCode: "AE"` as tenant admin | 409; GET shows `countryLocked: true` | not run |
| Same tenant, platform admin `PUT /api/v1/tenants/{id}/country` `{ "countryCode": "AE" }` | 200; audit row written | not run |
| Tenant admin calls `PUT /api/v1/tenants/{id}/country` | 403 | not run |
| `GET /api/v1/reference/countries` | list containing `{ "code": "IN", "name": "India" }` | not run |
| Company profile screen with both locks on | Country and Leave year start disabled with their notes; Timezone editable | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A company sets the wrong leave year, allocates leave, then cannot change it | Medium | The note under the field before it locks: "This cannot be changed once leave is allocated or requested." Support can correct it by hand |
| Changing the timezone moves the "today" used for attendance and leave (`TenantClock`) | Medium | Not locked by founder decision; the screen warns "Attendance and leave dates use this timezone from now on." Past records keep their stored dates |
| A changed country leaves India statutory setup in place | Certain | Out of scope and said so on the confirm dialog; the lock stops it once payroll is in use |
| A runtime without the `payroll` implementation never locks the country | Low | The single app always carries `payroll`; `CountryLockIT` runs against the real bean |

## 10. Rollback

1. Revert the code. The Company profile goes back to tagline and logo; saved values stay and keep working, as they did when set at creation.
2. A wrong value is corrected by the platform admin (country) or by support in `core.tenant` (timezone, leave year start).
3. The function can stay in place; nothing else calls it.
