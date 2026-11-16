# Feature: Tenant Options & Selectable Rules Registry

| Field | Value |
|---|---|
| **Feature ID** | `W-104` · `CORE-OPTIONS` |
| **Promoted to** | `docs/target-state/features/W-104-tenant-options.md` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/core`, `code/backend/migration`, `code/frontend` (`src/core/settings`) |
| **Related gaps** | DEBT-014 (`legacy/Payroll-Bend-SBoot/.../MasterConfig.java:14-30`), founder 2026-11-13 planned product roadmap ("Priority 2 — competing with Keka: `W-104` Tenant options") |
| **Status** | **In Review (pending manager approval)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | nothing |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `core` | 1 | **Pass (1 module)** |
| Flyway migration | `V194__tenant_options.sql` (`core`) | 1 | **Pass (1 migration)** |
| Externally testable behaviour | Tenant administrator views, searches, and updates categorized tenant options on `/settings/options`; backend services resolve typed values (`getBoolean`, `getString`, `getInteger`) with defaults and in-memory cache invalidation | 1 | **Pass (1 behaviour)** |
| Frontend area | `core/settings` (screen at `/settings/options`) | 1 | **Pass (1 area)** |

Within size cap across all four axes. No split required.

---

## 1. Problem

As different customers onboard onto the multi-tenant platform, they request variations in business rules and policies (e.g. "Can employees edit their own emergency contact details?", "Should PAN and Aadhaar be masked on payslips?", "Enforce strict IP/geofencing check on mobile attendance punches?", "Allow past-dated leave applications up to 15 days?"):
1. **Risk of hardcoded tenant forks**: Without a common configuration mechanism, developers are tempted to introduce tenant-specific conditions in code (e.g. `if ("acme-corp".equals(tenant.getSlug())) { ... }`), violating multi-tenant cleanliness and maintainability.
2. **Schema bloat from single-flag migrations**: Every minor customer toggle currently requires adding nullable columns across scattered tables (`core.tenant`, `payroll.tds_setting`, `core.lop_policy`).
3. **Legacy debt (DEBT-014)**: The legacy system had an untyped, un-tenanted, and undocumented JSON blob table (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/MasterConfig.java:14-30` and `MasterConfigRepository.java:12`) with unknown blast radius and no tenant isolation.
4. **No unified administration interface**: Tenant administrators have no single console to inspect and toggle customer preferences; settings are scattered across disconnected screens.

This ticket delivers `core.tenant_option`: a typed, validated, cached, and row-level isolated key-value options store in `core`, with a management screen under `/settings/options`.

---

## 2. Scope

### In scope
- **Data model (`core.tenant_option`)**:
  - `tenant_id` (UUID) + `option_key` (VARCHAR) unique composite key.
  - Namespaced option keys (e.g. `core.employee.self_edit_contact`, `hrms.attendance.strict_geofence`, `payroll.payslip.mask_pan`, `core.security.enforce_mfa`).
  - Supported value types: `BOOLEAN`, `STRING`, `INTEGER`, `JSON`.
  - Categories: `GENERAL`, `SECURITY`, `ATTENDANCE`, `LEAVE`, `PAYROLL`, `PORTAL`.
  - Secret flag: `is_secret` (masks value in API responses unless caller holds `core.tenant_option.manage`).
- **Java Service API (`TenantOptionService`)**:
  - `boolean getBoolean(String key, boolean defaultValue)`
  - `String getString(String key, String defaultValue)`
  - `int getInteger(String key, int defaultValue)`
  - `<T> T getJson(String key, Class<T> targetClass, T defaultValue)`
  - `void setOption(String key, String rawValue)`
  - High-performance in-memory cache (per-tenant `ConcurrentHashMap` invalidating on `setOption` or external update).
- **Default Seed Registry**:
  - System-defined metadata catalogue with fallback defaults so an unset option cleanly resolves to platform default without database writes.
- **Tenant Admin Console (`/settings/options`)**:
  - Tabbed interface by category (`General`, `Security`, `Attendance`, `Leave`, `Payroll`).
  - Search filter by option name / description.
  - Form controls matching value types (Ant Design `Switch` for boolean, `InputNumber` for integer, `Input` for string).
  - "Reset to Default" button per option.
- **Authorization**:
  - `core.tenant_option.read` granted to `tenant-admin`, `hr`, `payroll-officer`.
  - `core.tenant_option.manage` restricted to `tenant-admin`.

### Out of scope
- Platform-global server environment variables (managed via Azure Key Vault / Spring properties).
- Dynamic runtime Flyway database migrations triggered from option values.
- Tenant billing tier overrides (governed by `W-84.1` subscription plans).

---

## 3. Flow

```
[Backend Business Logic Request (e.g. Clock-In)]
                     │
                     ▼
[TenantOptionService.getBoolean("hrms.attendance.strict_geofence", false)]
  ├─── Cache Hit (Tenant Context Map) ──► Returns Boolean Instantly (< 1ms)
  └─── Cache Miss ──► Query core.tenant_option
                         ├── Found ──► Populate Cache & Return Value
                         └── Not Found ──► Return Registry Default (false)

[Tenant Administrator Updates Setting]
                     │
                     ▼
[PUT /api/v1/tenant-options/hrms.attendance.strict_geofence { "value": "true" }]
                     │
                     ▼
[Validate Option Type & Bounds]
                     │
                     ▼
[Save to core.tenant_option] ──► [Invalidate Tenant Cache] ──► [Log to core.audit_log]
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/core`)

| Layer | File | Change |
|---|---|---|
| Entity | `com.infinevo.core.option.TenantOption.java` | **New**. Entity mapped to `core.tenant_option`. |
| Enum | `com.infinevo.core.option.OptionValueType.java` | **New**. `BOOLEAN`, `STRING`, `INTEGER`, `JSON`. |
| Enum | `com.infinevo.core.option.OptionCategory.java` | **New**. `GENERAL`, `SECURITY`, `ATTENDANCE`, `LEAVE`, `PAYROLL`, `PORTAL`. |
| Definition | `com.infinevo.core.option.OptionDefinition.java` | **New**. Metadata descriptor with default values, allowed regex, and descriptions. |
| Registry | `com.infinevo.core.option.TenantOptionRegistry.java` | **New**. Pre-configured registry of known platform option keys and defaults. |
| Repository | `com.infinevo.core.option.TenantOptionRepository.java` | **New**. Query options by tenant and key. |
| Service | `com.infinevo.core.option.TenantOptionService.java` | **New**. Service interface exposing typed getters (`getBoolean`, etc.) and setters. |
| ServiceImpl | `com.infinevo.core.option.TenantOptionServiceImpl.java` | **New**. Thread-safe cached implementation with validation and audit integration. |
| Controller | `com.infinevo.core.option.TenantOptionController.java` | **New**. REST endpoints for querying and updating tenant options (`/api/v1/tenant-options`). |

### 4.2 API contract

| Method | Path | Request Body | Response Body | Auth / Permission |
|---|---|---|---|---|
| `GET` | `/api/v1/tenant-options` | — (optional `?category=...`) | `List<TenantOptionResponse>` | `core.tenant_option.read` |
| `GET` | `/api/v1/tenant-options/{key}` | — | `TenantOptionResponse` | `core.tenant_option.read` |
| `PUT` | `/api/v1/tenant-options/{key}` | `UpdateOptionRequest` (`value`) | `TenantOptionResponse` | `core.tenant_option.manage` |
| `POST` | `/api/v1/tenant-options/{key}/reset` | — | `TenantOptionResponse` (reverts to default) | `core.tenant_option.manage` |

### 4.3 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| `src/core/settings/optionService.js` | **New**. API client for listing, updating, and resetting tenant options. |
| `src/core/settings/TenantOptionsScreen.jsx` | **New**. Screen mounted at `/settings/options`: Ant Design `Tabs` by category, search bar, switch / number inputs, and Reset buttons. |
| `src/shell/AppShell.jsx` | Expose "Tenant Options" under Settings menu when user holds `core.tenant_option.read` (`file:code/frontend/src/shell/AppShell.jsx:105-120`). |

---

## 5. Database Changes

> **Flyway script:** `code/backend/migration/src/main/resources/db/migration/core/V194__tenant_options.sql`

```sql
-- Schema: core
-- Version: V194
-- Feature: W-104 Tenant options and selectable rules registry

CREATE TABLE core.tenant_option (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES core.tenant(tenant_id),
    option_key      VARCHAR(100) NOT NULL,
    option_value    TEXT NOT NULL,
    value_type      VARCHAR(20) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    description     TEXT,
    is_secret       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_tenant_option_type CHECK (value_type IN ('BOOLEAN', 'STRING', 'INTEGER', 'JSON')),
    CONSTRAINT chk_tenant_option_cat  CHECK (category IN ('GENERAL', 'SECURITY', 'ATTENDANCE', 'LEAVE', 'PAYROLL', 'PORTAL'))
);

CREATE UNIQUE INDEX uk_tenant_option_tenant_key 
    ON core.tenant_option (tenant_id, option_key);

CREATE INDEX idx_tenant_option_tenant_category 
    ON core.tenant_option (tenant_id, category);

ALTER TABLE core.tenant_option ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.tenant_option
    USING (
      tenant_id = CASE
        WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
        WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
        ELSE current_setting('app.current_tenant_id', true)::uuid
      END
    );

-- Seed role grants
CREATE OR REPLACE FUNCTION core.grant_tenant_option_actions(p_tenant_id UUID)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN RETURN; END IF;
    INSERT INTO core.role_action (tenant_id, role_id, action_code)
    SELECT p_tenant_id, r.id, grants.action_code
    FROM (VALUES
        ('tenant-admin', 'core.tenant_option.read'),
        ('tenant-admin', 'core.tenant_option.manage'),
        ('hr', 'core.tenant_option.read'),
        ('payroll-officer', 'core.tenant_option.read')
    ) AS grants (role_code, action_code)
    JOIN core.role r ON r.tenant_id = p_tenant_id AND r.code = grants.role_code AND r.is_system
    ON CONFLICT (tenant_id, role_id, action_code) DO NOTHING;
END;
$$;

SELECT core.grant_tenant_option_actions(t.tenant_id) FROM core.tenant t;
```

- [x] Standing rule: Schema named explicitly on every statement (`core.tenant_option`).
- [x] Standing rule: `tenant_id` is leading column in unique index `uk_tenant_option_tenant_key` and secondary index.
- [x] Standing rule: `ENABLE ROW LEVEL SECURITY;` + `CREATE POLICY tenant_isolation` using fail-safe `CASE` format.
- [x] Standing rule: Multi-tenant safety — no hardcoded customer IDs.

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `com.infinevo.core.option.TenantOptionServiceImplTest.java` | Typed retrieval (`getBoolean`, `getInteger`, `getString`), registry default fallbacks when unset, cache hit vs database query, cache invalidation upon `setOption`. |
| Unit | `com.infinevo.core.option.TenantOptionGuardTest.java` | Verifies `core.tenant_option.manage` is required for updates; verifies non-admin cannot update options; verifies secret options are masked. |
| Integration | `com.infinevo.core.option.TenantOptionRlsIT.java` | Confirms tenant isolation under `app_user` (one tenant cannot read or modify another tenant's options). |
| Component | `src/core/settings/TenantOptionsScreen.test.jsx` | Tests rendering category tabs, toggling boolean switches, typing integer limits, and resetting options. |

---

## 7. Verification

Verification commands and steps:

```bash
# 1. Run core backend unit and integration tests
./mvnw -pl core test -Dtest='TenantOptionServiceImplTest,TenantOptionGuardTest,TenantOptionRlsIT'

# 2. Run frontend options screen tests
cd code/frontend
npm test -- src/core/settings/TenantOptionsScreen.test.jsx

# 3. Frontend lint check
npm run lint
```

### Verification matrix

| Check | Action | Expected Result |
|---|---|---|
| Default Fallback | Query unset key `TenantOptionService.getBoolean("hrms.attendance.strict_geofence", false)` | Returns `false` without database query. |
| Update & Cache Invalidation | `PUT /api/v1/tenant-options/hrms.attendance.strict_geofence` with `"true"` | Returns 200; subsequent service call returns `true` immediately from memory cache. |
| Type Validation | `PUT /api/v1/tenant-options/...` with non-integer string on an `INTEGER` option | Rejects with HTTP 400 Bad Request. |
| Secret Masking | Call `GET /api/v1/tenant-options/{key}` on a secret option as viewer | Value returns masked `********`. |
| RLS Isolation | Query `core.tenant_option` from Tenant B connection | Returns 0 rows from Tenant A's configured options. |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Stale cache across clustered app instances | Low (today single/double container) | Cache invalidation event published or short TTL (e.g. 5 minutes) ensures cluster convergence. |
| Malformed JSON in JSON-type options | Low | Validate JSON syntax via `Jackson ObjectMapper` before writing to database. |
| Option deletion breaking downstream code | Low | Options are never deleted; resetting an option simply removes the tenant override, gracefully falling back to registry default. |

---

## 9. Rollback

1. Flyway script is forward-only; if uninstalled, the service layer defaults gracefully to pre-seeded Java constants.
2. Frontend `/settings/options` route can be disabled immediately by revoking `core.tenant_option.read` permission.

