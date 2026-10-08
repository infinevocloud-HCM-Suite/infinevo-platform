# Feature: Country templates — a new tenant starts with its country's defaults

| Field | Value |
|---|---|
| **Feature ID** | `W-73.9` · from `W-73` |
| **Promoted to** | `docs/target-state/features/W-73-9-country-templates.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core` (tenant provisioning, holiday, leave), `code/backend/payroll` (statutory settings, components), `code/backend/migration` (`reference`) |
| **Related gaps** | `D-42` (admin email on Create Tenant — same screen) |
| **Status** | Draft |
| **Written by** | claude for the founder, 2026-10-07 |
| **Blocked by** | `D-42` on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` owns the template and the seam; `payroll` fills its part through a `TenantTemplateContributor` bean, as navigation does with `NavigationContributor` — no module calls another | 1 (+ the contributor seam) |
| Flyway migration | `V162` — `reference.country_template` rows for India | 1 |
| Externally testable behaviour | a tenant created with country `IN` has EPF, ESI, PT, a national holiday calendar, standard leave types and the common salary components before its admin signs in | 1 |
| Frontend area | `core/admin` (one select, one preview) | 1 |

## 1. Problem

| Fact | Evidence |
|---|---|
| Create Tenant takes a country code and does nothing with it beyond storing it | `TenantCreate.jsx:77-78`, `TenantRequest.java:9` |
| A new tenant's setup checklist has eight empty steps the admin fills by hand: work location, pay schedule, components, EPF, ESI, PT … | `SetupStepCatalogue` |
| Statutory rates for India already live in `reference` migrations | `reference/V106`, `V147` (tax rules) |
| Legacy carried 32 named earning types every Indian tenant needs | `addNewCustomEarning.js:74-87` · `D-37` |

## 2. Scope

**In scope**

- `reference.country_template(country_code, section, payload jsonb, version)` — sections: `holidays` (national list by year), `leave_types`, `salary_components` (the catalogue entries: earnings with named types, deductions), `statutory` (EPF, ESI, PT defaults with "enabled" false where the tenant must opt in), `pay_schedule` (monthly, last working day)
- India (`IN`) seeded in `V162`; other countries later, one migration each
- On provision (`TenantController.java:40`), after the tenant and its roles exist: `TenantTemplateService.apply(tenantId, countryCode)` runs every `TenantTemplateContributor` (core: holidays, leave types; payroll: components, statutory, pay schedule). Each writes rows only where the tenant has none — idempotent, safe to re-run
- Setup checklist steps whose data the template filled are marked **done by template** (a new step state, shown as "Pre-filled — review")
- Create Tenant: the Country select shows "Starts with: holidays, leave types, salary components, EPF/ESI/PT" when a template exists, "No template — the admin sets everything up" when not
- `POST /api/v1/tenants/{id}/apply-template` for a tenant created before this (platform staff)

**Out of scope**

- Tax slabs and rules — already `reference`, applied per financial year by `W-33`
- State-level holidays; the admin adds them on `/holidays`
- Editing templates from a screen; they are migrations

## 3. Flow

```
platform-admin --> Create tenant {country IN, adminEmail} --> POST /api/v1/tenants
  --> TenantService.create --> roles seeded --> TenantTemplateService.apply(IN)
        core:    HolidayTemplateContributor   -> core.holiday_calendar + holidays (current and next year)
                 LeaveTypeTemplateContributor -> core.leave_type (Earned, Casual, Sick, Maternity, Paternity, LOP)
        payroll: ComponentTemplateContributor -> payroll.earning/deduction catalogue (Basic, HRA, DA, Conveyance, Special, …; PF, ESI, PT, TDS)
                 StatutoryTemplateContributor -> epf_setting, esi_setting, pt_setting (enabled=false, rates set)
                 PayScheduleTemplateContributor -> monthly, last working day
  --> setup steps: PAY_SCHEDULE, SALARY_COMPONENTS, EPF, ESI, PROFESSIONAL_TAX -> PRE_FILLED
  --> admin invitation (D-42)
admin --> /setup --> five steps "Pre-filled — review", three to do
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Entity | `core/.../template/CountryTemplate` (`reference` schema, read-only) | `countryCode, section, payload, version` |
| Interface | `core/.../template/TenantTemplateContributor` | `section()`, `apply(tenantId, payload)` — modules implement it |
| Service | `core/.../template/TenantTemplateService` | loads the country's sections, calls contributors in order, records `core.tenant_template_applied(tenant_id, section, version, applied_at)` |
| Contributors | `core`: holidays, leave types · `payroll`: components, statutory, pay schedule | each idempotent ("only where none") |
| Setup | `core/.../setup/SetupChecklistService` | step state `PRE_FILLED` when the step's checker finds template-written rows and no edit since |
| Controller | `TenantController.java` | `POST /{id}/apply-template` (`core.tenant.provision`); `GET /api/v1/reference/country-templates` (which countries have one, their section names) |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/reference/country-templates` | — | `[{countryCode, sections[], version}]` | `core.tenant.provision` |
| POST | `/api/v1/tenants` | `+ nothing` — `country_code` already there | unchanged | unchanged |
| POST | `/api/v1/tenants/{id}/apply-template` | — | `{applied: [section…], skipped: [section…]}` | `core.tenant.provision` |

## 5. Frontend changes

| File | Change |
|---|---|
| `core/admin/TenantCreate.jsx:77-85` | country select backed by the reference list; the "Starts with" line |
| `core/admin/TenantPage.jsx` | "Apply country template" button with the result |
| `core/setup/SetupChecklist.jsx` | `PRE_FILLED` rendered as "Pre-filled — review" with the link |

**Routes added** — none.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V162__country_template_in.sql` | `reference.country_template` (no `tenant_id` — `reference` is exempt by the rule) + India rows; `core.tenant_template_applied(tenant_id, section, version, applied_at)` + RLS | reference: no · applied: yes | yes |

- [x] `tenant_id` and RLS on the `core` table
- [x] no money in the template except component defaults as `numeric` text in JSON, parsed to `BigDecimal`

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Integration | `TenantTemplateApplyIT` | new `IN` tenant has 6 leave types, ≥ 12 holidays for this year, EPF/ESI/PT rows disabled with rates, ≥ 20 components; second apply changes nothing |
| Integration | `TenantTemplateIsolationIT` | rows carry the new tenant's id only |
| Unit | `SetupChecklistPrefilledTest` | step shows `PRE_FILLED` until edited |
| Unit | `TenantCreate.test.jsx` | the "Starts with" line per country |

## 8. Verification

| Check | Expected |
|---|---|
| Create tenant `IN` on Azure dev, sign in as its admin | `/setup` shows 5 pre-filled, 3 open; `/payroll/settings/epf` shows rates, disabled |
| Create tenant `AE` | "No template"; checklist all open |
| Apply template to an older tenant with components already | Components skipped, the rest applied |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Template rates go stale | certain, yearly | `version` on the row; a new migration bumps it; applied table records which version a tenant got |
| A tenant does not want a default (e.g. ESI) | medium | everything statutory is seeded **disabled**; components can be deleted while unused |

## 10. Rollback

`core.tenant_template_applied` says what was written; a tenant's template rows can be deleted while unused. The reference table stays.
