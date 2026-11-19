# Feature: Tenant options — one typed place for per-customer switches

| Field | Value |
|---|---|
| **Feature ID** | `W-104` · `CORE-OPTIONS` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/core`, `code/backend/hrms` (one option, one check), `code/backend/migration`, `code/frontend/src/core/settings`, `code/frontend/src/shell/portal`, `code/frontend/src/shell/navigation` (one label), `code/frontend/src/hrms/attendance`, `code/frontend/src/mobile/attendance` |
| **Related gaps** | DEBT-014 (`legacy/docs/GAP_INVENTORY.md:52`); founder 2026-11-13 roadmap, "Priority 2" |
| **Status** | Ready — approved 2026-11-19. Not built. |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | `W-98.2` — the third option needs clock-in location to be stored first |

## Founder decision (2026-11-19)

The ticket ships with three real options, so the screen is not empty.

| # | Option key | Tab | Default | What the customer gets |
|---|---|---|---|---|
| 1 | `core.employee.self_edit` | Portal | on | When off, employees can view their profile but cannot change it. |
| 2 | `core.portal.panel.<code>.visible` — one per employee-portal tab | Portal | on | When off, that tab is hidden for every employee of the company. |
| 3 | `hrms.attendance.clock_location_required` | Attendance | off | When on, a clock-in or clock-out without a location is refused. |

## Size

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` and `hrms` | 1 | **Over by one** — option 3 is read in `hrms` |
| Flyway migration | 1 — next free version at build time | 1 | Pass |
| Testable behaviour | a tenant admin changes an option and the product behaves differently for that company | 1 | Pass |
| Frontend area | `core/settings`, `shell/portal`, `hrms/attendance`, `mobile/attendance` | 1 | **Over** — one small change in each consumer |

Size **L** (was M): the store and screen are M, and the three options each add a small consumer.

---

## 1. Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Each small per-customer switch needs its own table or column and its own migration. | `code/backend/migration/src/main/resources/db/migration/hrms/V121__attendance_preference.sql:4`; `payroll/V190__tds_switches.sql:9` |
| 2 | The frozen system used one untyped, un-tenanted JSON table for this; nobody knows what reads it. | DEBT-014 |
| 3 | There is no shared way for a module to ask "is this switch on for this customer", so the easy path is tenant-specific code. | no `TenantOption` class anywhere under `code/` |

## 2. Scope

**In scope**

- `core.tenant_option`: one row per tenant and key, holding only the customer's override.
- A code registry that defines every key: type, category, default, label, description, allowed range.
- `TenantOptionService` with typed getters, backed by the shared cache.
- Read, update and reset API; a settings screen with tabs by category.
- Two actions and their grants, for existing and future tenants.
- The three first options and the code that obeys them (§4.1).

**Out of scope**

- Any option beyond the three in §4.1.
- Moving existing typed settings into options. `hrms.attendance_preference`, `payroll.tds_setting` and the rest stay as they are.
- Secrets. An option is never a password, key or token; those live in Key Vault. The `is_secret` flag of the first draft is dropped.
- Server-wide settings (Spring properties), plan limits (`W-84.1`).

### When is a setting an option?

| Use a tenant option when all of these are true | Use its own typed table when any of these is true |
|---|---|
| One value per customer | Rows per employee, location, leave type or any other entity |
| On/off, a number or a short text | Money, a rate, a statutory value |
| The value applies from now; no history needed | It needs an effective date or history |
| Changing it needs no other data to change | It has its own screen, approval or lifecycle |

A new key is added in code, in the registry, by the ticket that reads it. That ticket's spec
names the key, type and default.

## 3. Flow

```
module code --> TenantOptionService.getBoolean(key) --> CacheService.getOrCompute --> core.tenant_option --> (no row) registry default

tenant admin --> /settings/options --> PUT /api/v1/tenant-options/{key} --> validate against the registry
             --> upsert core.tenant_option --> CacheService.evict --> audit
```

## 4. Backend changes

All under `code/backend/core/src/main/java/com/infinevo/core/option/`.

| Layer | File | Change |
|---|---|---|
| Entity | `TenantOption.java` | **New.** `id`, `tenantId`, `optionKey`, `optionValue`, audit columns. Same audit listener as other core entities. |
| Enum | `OptionValueType.java` | **New.** `BOOLEAN`, `STRING`, `INTEGER`, `JSON`. |
| Enum | `OptionCategory.java` | **New.** `GENERAL`, `SECURITY`, `ATTENDANCE`, `LEAVE`, `PAYROLL`, `PORTAL`. |
| Definition | `OptionDefinition.java` | **New.** Record: key, type, category, default, label, description, optional min/max or pattern. |
| Registry | `TenantOptionRegistry.java` | **New.** Collects every `OptionDefinition` bean. A module registers a key by declaring a bean — `core` never names another module. Fails startup on a duplicate key or a default that does not match its type. |
| Repository | `TenantOptionRepository.java` | **New.** `findByTenantIdAndOptionKey`, `findByTenantId`. |
| Service | `TenantOptionService.java`, `TenantOptionServiceImpl.java` | **New.** `getBoolean(key)`, `getString(key)`, `getInteger(key)`, `getJson(key, Class)`, `set(key, rawValue)`, `reset(key)`, `list(category)`. A getter for a key not in the registry throws — a typo must fail in a test, not return a silent default. |
| Controller | `TenantOptionController.java` | **New.** The API below, with its own `@ExceptionHandler`s: unknown key → `404 NOT_FOUND`; wrong type, out of range or bad JSON → `400 VALIDATION_FAILED`. (Handlers in this codebase are per controller; without them these return 500.) |
| Navigation | `core/.../navigation/NavigationCatalogue.java` | Add `core.settings.options` under `core.settings`, after `core.settings.company` (line 289-295): path `/settings/options`, feed `/api/v1/tenant-options`, action `core.tenant_option.read`. |

### 4.1 The three first options

| Key | Registered by | Obeyed in | Rule |
|---|---|---|---|
| `core.employee.self_edit` (BOOLEAN, default `true`, `PORTAL`) | `core` | The two update endpoints of `core/.../employee/EmployeeDetailController.java` (lines 100 and 113) | When `false`, a caller who holds only `core.employee.update_own` gets `403 SELF_EDIT_DISABLED`. A caller with `core.employee.update` (HR) is not affected. |
| `core.portal.panel.<code>.visible` (BOOLEAN, default `true`, `PORTAL`) | `core`, one definition per `PortalPanelProvider` bean, built from the provider's code — `core` names no other module | `core/.../portal/PortalPanelService.java`, next to the module and action checks (lines 67-80) | When `false`, the panel is left out of the feed. The `profile` panel gets no key and cannot be hidden. Hiding a tab hides the tab only; it does not block the API behind it. |
| `hrms.attendance.clock_location_required` (BOOLEAN, default `false`, `ATTENDANCE`) | `hrms`, by an `OptionDefinition` bean | `hrms/.../attendance/ClockServiceImpl.java` (`clockIn`, `clockOut`) | When `true`, a request with no latitude and longitude gets `400 LOCATION_REQUIRED` and no session is written. Applies to every client, web and phone. |

The label of each panel option is "Show the <panel title> tab".

### Cache

| Point | Rule |
|---|---|
| Service | The shared `CacheService` (`code/backend/shared/src/main/java/com/infinevo/shared/cache/CacheService.java`), implemented by `RedisCacheService`. No in-process map — it would go stale on the second app instance. |
| Key | `TenantCacheKeyGenerator.tenantKey(tenantId, "tenant-option", optionKey)` |
| Read | `getOrCompute(key, String.class, Duration.ofMinutes(10), loader)`. The loader returns the stored value or the registry default. |
| Write | `set` and `reset` call `evict(key)` after the database write, in the same service method. |
| Redis down | `RedisCacheService` already falls back to the loader, so the option is read from the database. |

### API contract

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/tenant-options` | optional `?category=` | `List<TenantOptionResponse>` — every registry key with `key`, `category`, `valueType`, `label`, `description`, `value`, `defaultValue`, `overridden` | `core.tenant_option.read` |
| `PUT` | `/api/v1/tenant-options/{key}` | `{ "value": "…" }` | `TenantOptionResponse` | `core.tenant_option.manage` |
| `DELETE` | `/api/v1/tenant-options/{key}` | — | `TenantOptionResponse` with the default (the override row is removed) | `core.tenant_option.manage` |

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/core/settings/optionService.js` | **New.** List, update, reset. |
| `code/frontend/src/core/settings/TenantOptionsScreen.jsx` | **New.** Tabs for **all six** categories — General, Security, Attendance, Leave, Payroll, Portal; a tab with no keys is hidden. Search by label. `Switch` for boolean, `InputNumber` for integer, `Input` for string, a text area for JSON. "Reset to default" per row. Empty state: "No options are available yet." Loading and error states; write controls shown only with `useCan('core.tenant_option.manage')`. |
| `code/frontend/src/core/index.js` | Add the lazy import and the route (next to `/settings/company`, line 173-174). |
| `code/frontend/src/shell/navigation/navLabels.js` | Add `'nav.settings.options': 'Options'`. |
| `code/frontend/src/shell/portal/panels/MyProfile.jsx` | On `403 SELF_EDIT_DISABLED`, and when the profile feed says self-edit is off, hide the edit controls and show "Your company has switched off profile changes. Ask HR to update your details." |
| `code/frontend/src/hrms/attendance/` (the web clock control) | When the option is on, ask the browser for location and send it with clock-in and clock-out; on `400 LOCATION_REQUIRED` show "Location is required to clock in." |
| `code/frontend/src/mobile/attendance/MobileAttendanceScreen.jsx` | On `400 LOCATION_REQUIRED` show the same message and offer to open location settings. |

The menu and the route come from the server's navigation feed. `AppShell.jsx` is **not** changed.

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/settings/options` | `TenantOptionsScreen` | mounted when the feed carries `core.settings.options` |

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| next free Flyway version at build time, `core/V…__tenant_option.sql` | `core.tenant_option` | yes | yes — drop the table, trigger, functions and the two action rows |

The migration follows `core/V192__employee_onboarding_tasks.sql` step for step:

| # | Step | V192 lines to copy the shape from |
|---|---|---|
| 1 | `CREATE TABLE core.tenant_option` — `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE`, `option_key VARCHAR(100) NOT NULL`, `option_value TEXT NOT NULL`, `created_at`, `created_by`, `updated_at`, `updated_by` | 4-22 |
| 2 | `CREATE UNIQUE INDEX uk_tenant_option_tenant_key ON core.tenant_option (tenant_id, option_key)` | — |
| 3 | `ENABLE ROW LEVEL SECURITY` and the fail-safe `tenant_isolation` policy | 28-37 |
| 4 | `GRANT SELECT, INSERT, UPDATE, DELETE ON core.tenant_option TO app_user` | 39 |
| 5 | `INSERT INTO reference.action` for `core.tenant_option.read` and `core.tenant_option.manage`, `ON CONFLICT (code) DO NOTHING` — **before** any grant, because `core.role_action.action_code` has a foreign key to it (`core/V022__role_action.sql:9`) | 87-91 |
| 6 | `core.grant_tenant_option_actions(p_tenant_id)`, `SECURITY DEFINER`, skipping the platform tenant; then `REVOKE EXECUTE … FROM PUBLIC` | 94-153 |
| 7 | Trigger function `core.tenant_grant_tenant_option_actions()` with `REVOKE EXECUTE … FROM PUBLIC`, and trigger `tenant_seed_system_roles_tenant_option` `AFTER INSERT ON core.tenant` — so a customer created later gets the grants. The name sorts after `tenant_seed_system_roles`, so the roles exist first. | 156-174 |
| 8 | Backfill: `SELECT core.grant_tenant_option_actions(t.tenant_id) FROM core.tenant t` | 177 |

Type, category and description are **not** columns. They live in the registry, so the table
cannot disagree with the code.

| Role | `core.tenant_option.read` | `core.tenant_option.manage` |
|---|:---:|:---:|
| `tenant-admin` | yes | yes |
| `hr` | yes | — |
| `payroll-officer` | yes | — |

- [x] `tenant_id` on the new table, with RLS
- [x] Unique index leads with `tenant_id`
- [x] No money column
- [x] Expand only

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../option/TenantOptionRegistryTest.java` | Duplicate key and mistyped default fail startup |
| Unit | `core/src/test/.../option/TenantOptionServiceImplTest.java` | Typed getters; default when no row; unknown key throws; `set` validates type and range; `set` and `reset` evict the cache |
| Unit | `core/src/test/.../option/TenantOptionControllerTest.java` | 404 for an unknown key; 400 for a bad value; `manage` needed to write; `read` alone cannot write |
| Integration | `core/src/test/.../option/TenantOptionRlsIT.java` | Tenant B cannot read or write tenant A's rows as `app_user` |
| Integration | `migration/src/test/.../RoleCatalogueIT.java` (extend) | The two actions exist; the three roles hold the grants; a tenant created after the migration holds them too |
| Unit | `core/src/test/.../employee/` (extend the detail controller or service test) | Self-edit off: own update is 403 `SELF_EDIT_DISABLED`; HR update still passes |
| Unit | `core/src/test/.../portal/PortalPanelServiceTest.java` (extend) | A panel whose option is off is left out; `profile` always stays |
| Unit | `hrms/src/test/.../attendance/ClockServiceTest.java` (extend) | Option on: no coordinates is 400 and nothing is written; with coordinates passes. Option off: both pass |
| Component | `code/frontend/src/core/settings/TenantOptionsScreen.test.jsx` | Empty state; tabs incl. Portal; switch, number and reset; write controls hidden without `manage`; load error shown |

Tests register `core.example.sample_switch` (BOOLEAN, default `false`, category `GENERAL`)
through a test-only `OptionDefinition` bean.

## 8. Verification

```bash
cd code/backend && ./mvnw -pl core,migration verify
cd code/frontend && npm test -- src/core/settings/TenantOptionsScreen.test.jsx && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| Migration runs on a database that already has tenants | Table, actions, grants and trigger exist | not run |
| Create a new tenant after the migration | Its `tenant-admin` holds both actions | not run |
| `getBoolean("core.example.sample_switch")` with no row | `false` | not run |
| `PUT …/core.example.sample_switch` `{ "value": "true" }` then the getter | 200, then `true` | not run |
| `PUT` `{ "value": "maybe" }` on a boolean | 400 `VALIDATION_FAILED` | not run |
| `PUT …/no.such.key` | 404 `NOT_FOUND` | not run |
| `DELETE …/core.example.sample_switch` then the getter | 200, then `false`; the row is gone | not run |
| `PUT` as `hr` | 403 | not run |
| Tenant B lists options after tenant A set one | B sees the default | not run |
| Open `/settings/options` as a tenant admin | Menu item "Options" shows; the Portal tab lists self-edit and one switch per portal tab; the Attendance tab lists the location switch | not run |
| Switch self-edit off, then save a phone number as an employee | 403 `SELF_EDIT_DISABLED`; the portal shows the "ask HR" note; HR can still edit that employee | not run |
| Switch the Payslips tab off, then open the portal as an employee | No Payslips tab; other tabs unchanged; tenant B still sees its Payslips tab | not run |
| Switch location required on, then clock in with no coordinates | 400 `LOCATION_REQUIRED`, no session row | not run |
| Same, with coordinates | 201 | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A company hides a tab and believes the data behind it is blocked | Medium | The option's description says "hides the tab only"; access stays governed by roles |
| Location required is switched on and web users with no browser location cannot clock in | Medium | The option's description says so; default is off |
| Options become a dumping ground and typed tables stop being written | Medium | The rule table in §2; each new key is named in the spec of the ticket that reads it |
| A stale value after a write on another instance | Low | Shared Redis cache with evict on write; 10-minute expiry as a backstop |
| A module ships a key and later removes it, leaving orphan rows | Low | Orphan rows are ignored by `list` and never read; a clean-up is a one-line migration when needed |

## 10. Rollback

1. Revert the code. The screen, the API and the menu item go with it.
2. The table and grants can stay; nothing reads them. Removing them is a new forward migration, not a Flyway undo.
3. No other data is touched.
