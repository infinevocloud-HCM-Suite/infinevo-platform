# Feature: Customer REST API & API Key Management

| Field | Value |
|---|---|
| **Feature ID** | `W-108.1` · `PLAT-API` |
| **Owner** | Mohit (`dev-mohit`) |
| **Apps touched** | `code/backend/core`, `code/backend/migration`, `code/frontend` (`src/core/developer`) |
| **Related gaps** | none in `GAP_INVENTORY.md`; all current APIs require Keycloak JWT user sessions |
| **Status** | Ready for founder review |
| **Blocked by** | none |

## 1. Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `V197__customer_api_keys.sql` | 1 |
| Externally testable behaviour | Customer admin generates scoped API keys, authenticates server-to-server requests via `X-API-Key`, and reads/syncs employee and attendance data | 1 |
| Frontend area | `core/developer` | 1 |

---

## 2. Problem & Context

Currently, all Infinevo endpoints require an interactive Keycloak OAuth2 JWT bearer token issued to a browser user session. Customers wanting to integrate external ERP, CRM, Active Directory, or attendance devices have no programmatic server-to-server API authentication mechanism.

---

## 3. Scope

**In scope:**
- PostgreSQL table `core.api_key` with RLS:
  - Scoped permissions (`employees.read`, `employees.write`, `attendance.write`, `payroll.read`).
  - Hashed secret storage using SHA-256 (`api_key_hash`), displaying secret only once on generation.
  - Expiry date, last used timestamp, IP whitelist restriction.
- Spring Security `ApiKeyAuthenticationFilter`:
  - Intercepts requests bearing header `X-API-Key: inf_live_...`.
  - Sets tenant context via `app.current_tenant_id` and binds granted scopes.
  - Token bucket rate limiting (100 requests / minute / tenant).
- Core programmatic REST endpoints under `/api/v1/public/*`:
  - `GET /api/v1/public/employees`: List active employees with pagination.
  - `POST /api/v1/public/attendance/punches`: Bulk ingest attendance biometric clock-ins.
- Frontend screen: `/settings/developer/api-keys` for tenant admins to generate, view usage, and revoke keys.

**Out of scope:**
- Outbound Webhooks (handled in `W-108.2`).

---

## 4. Flow

```
External System --> Request with Header "X-API-Key: inf_live_..."
                --> ApiKeyAuthenticationFilter
                --> Validates SHA-256 hash in core.api_key
                --> Binds Tenant RLS & Scopes
                --> Invokes PublicApiController
```

---

## 5. Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V197__customer_api_keys.sql` | Create `core.api_key` with RLS and index on key hash | yes | yes |

### Table: `core.api_key`
```sql
CREATE TABLE core.api_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(12) NOT NULL, -- e.g. "inf_live_ab12"
    key_hash VARCHAR(64) NOT NULL,   -- SHA-256 of full secret
    scopes TEXT[] NOT NULL DEFAULT '{}',
    ip_whitelist TEXT[] NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    expires_at TIMESTAMPTZ NULL,
    last_used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    revoked_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX uk_api_key_hash ON core.api_key (key_hash);
CREATE INDEX idx_api_key_tenant ON core.api_key (tenant_id, is_active);
```

---

## 6. Verification

```bash
mvnw clean test -pl migration,core -am -Dtest="*ApiKey*"
```
- Integration test `ApiKeyAuthIT`: verifies successful authentication with valid `X-API-Key`, scope enforcement (e.g. `attendance.write` forbidden on employee endpoint), rate limit 429 response, and rejection of revoked keys.
