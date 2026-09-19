# W-08 — Tenant binding filter

| Field | Value |
|---|---|
| **Work item** | `W-08` · issue [#9](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/9) |
| **Kind** | BE — Tenant binding filter & database session binding mechanism |
| **Stream / track** | Stream B — Data foundation · Track P |
| **Wave** | 2 — Data platform |
| **Size / skill** | M · BE |
| **Owner** | unassigned |
| **Blocked by** | `W-07` #8 (merged 2026-09-19) |
| **Blocks** | `W-10` Identity · `W-13` Employee master · `W-58` Tenant isolation tests |
| **Capabilities** | `CORE-01` Tenant registry & request isolation context |
| **Decisions** | `D-08` reference schema · `D-09` Postgres with Flyway · `D-56` RLS 3-branch CASE · `D-57` transaction-local setForConnection |
| **Gaps addressed** | `BUG-002` — cross-tenant exposure prevention |
| **Status** | **Draft (Awaiting Founder Approval)** |
| **Approved by** | Pending |
| **Approved on** | Pending |

---

## 1. Problem

`W-07` delivered the `core.tenant` table and row-level security (RLS) policies (`ENABLE ROW LEVEL SECURITY`, `CREATE POLICY tenant_isolation`) based on the PostgreSQL session variable `app.current_tenant_id`. It also provided `TenantContext.java` in `code/backend/shared` with `set(UUID)`, `require()`, and `setForConnection(Connection)`.

However, the application runtime lacks the boundary filter, membership table, and database transaction hooks to:
1. Store and query user-tenant membership relationships in PostgreSQL.
2. Extract the active tenant identity from incoming HTTP authentication tokens or request headers.
3. Verify that the authenticated principal is a permitted member of that tenant against the database without triggering RLS evaluation deadlocks.
4. Bind `TenantContext` to the executing HTTP thread for the duration of the request.
5. Bind `app.current_tenant_id` to open PostgreSQL database connections when a transaction opens.
6. Reliably clear `TenantContext` in a `finally` block at request completion to prevent `ThreadLocal` leaks.

Without this filter, database membership table, and database session binding:
- Queries executing against PostgreSQL run with no tenant bound. Under RLS (`D-56`), such queries evaluate to `NULL` / false and return zero rows.
- Developers risk falling back to legacy patterns of threading `organizationId` / `tenantId` manually as a method parameter across 270+ controller methods.
- Requests with invalid, missing, or unauthorized tenant claims are not intercepted uniformly at the edge, risking unauthenticated or cross-tenant access attempts.

---

## 2. Scope

**In scope**

- **User-Tenant Membership Table (`V002__user_tenant.sql`)**:
  - Bring `core.user_tenant` table creation into `W-08` (`02-data-model.md:55`) via Flyway migration script `V002__user_tenant.sql`.
  - Includes `tenant_id` column, foreign key constraint to `core.tenant`, unique index on `(user_id, tenant_id)`, and RLS isolation policy `CREATE POLICY tenant_isolation` using the 3-branch `CASE` (`D-56`).
- **Token & Header Tenant Claim Extraction (`TenantAuthenticationExtractor`)**:
  - Extract user identity (`user_id` / subject `sub`) from Spring Security JWT.
  - Extract active tenant UUID from JWT claim (`tenant_id`) or `X-Tenant-Id` HTTP request header.
- **Database-Backed Membership Verification & RLS Bootstrap Handling (`core.user_tenant`)**:
  - Tenant membership is verified against `core.user_tenant` (`02-data-model.md:55`) via `TenantMembershipService`.
  - **Resolving the RLS Bootstrap Paradox**: Because `core.user_tenant` has RLS enabled (`WHERE tenant_id = app.current_tenant_id`), querying it with `app.current_tenant_id` unset would return 0 rows. To check membership, `TenantMembershipService` temporarily sets `app.current_tenant_id = target_tenant_id` on the connection for the single check query `SELECT 1 FROM core.user_tenant WHERE user_id = ? AND tenant_id = ?`.
  - If `core.user_tenant` returns `1` row $\rightarrow$ Membership verified! Keep `TenantContext.set(target_tenant_id)` bound for the rest of the HTTP request.
  - If `core.user_tenant` returns `0` rows $\rightarrow$ Membership check fails! Clear `TenantContext.clear()`, reset the connection variable, and reject the request with `403 FORBIDDEN` (`ApiError.FORBIDDEN`).
- **Missing Tenant Handling (F-2)**:
  - If request path is an exempt public path, pass through without binding tenant.
  - If request path is protected and NO tenant is provided (missing from JWT claim and missing from `X-Tenant-Id` header):
    - Query user's tenant memberships in `core.user_tenant` using PostgreSQL function `core.get_user_tenants(user_id)` marked `SECURITY DEFINER` (or system connection).
    - If user belongs to **exactly 1 tenant**, auto-bind that single tenant.
    - If user belongs to **0 tenants** or **multiple (>1) tenants** without specifying a tenant, reject with `401 Unauthorized` (`ApiError.TENANT_NOT_BOUND`).
- **HTTP Request Binding Filter (`TenantContextFilter`)**:
  - Servlet filter registered in `code/backend/shared`.
  - Binds `TenantContext.set(tenantId)` prior to request execution.
  - Clears `TenantContext.clear()` in a `finally` block on every request completion.
  - Formats error responses as standard `ApiErrorResponse` (`TENANT_NOT_BOUND`, `UNAUTHENTICATED`, `FORBIDDEN`, `VALIDATION_FAILED`).
  - Exempts public and unauthenticated endpoints using a structured path matcher.
- **Database Session Binding Mechanism (`TenantDatabaseInterceptor` / `TenantAwareDataSourceProxy`)**:
  - Integrates with Spring JDBC lifecycle via a `DelegatingDataSource` proxy / `TransactionSynchronizationManager` listener that intercepts physical JDBC `Connection` checkout (`getConnection()`) when `setAutoCommit(false)` is invoked.
  - Handles Hibernate's `DELAYED_ACQUISITION_AND_HOLD` connection strategy by intercepting physical connection acquisition when a transaction starts.
  - Calls `TenantContext.setForConnection(conn)` executing `SELECT set_config('app.current_tenant_id', ?, true)` (`D-57`).
- **Spring Auto-Configuration (`TenantBindingAutoConfiguration`)**:
  - Auto-configures the filter and DB session binding interceptor/proxy in the `shared` module for all backend services.
- **Real HTTP Integration & Security Tests (`TenantBindingIT`) (F-3)**:
  - `TenantContextFilterTest`: Unit tests for claim parsing, header extraction, UUID validation, membership verification, and `finally` cleanup.
  - `TenantDatabaseInterceptorTest`: Unit tests for session variable setting on transactional connections and refusal of auto-commit connections.
  - `TenantBindingIT`: Spring Boot Testcontainers integration test executing **real HTTP requests via `MockMvc` / Spring HTTP client** through the full Spring Security filter chain:
    1. Valid HTTP request with JWT + `X-Tenant-Id` matching `core.user_tenant` $\rightarrow$ `HTTP 200 OK`, `app.current_tenant_id` bound, query returns target tenant rows.
    2. Header spoofing HTTP request with JWT + unpermitted `X-Tenant-Id` $\rightarrow$ `HTTP 403 Forbidden` (`ApiError.FORBIDDEN`).
    3. Missing tenant HTTP request for multi-tenant user $\rightarrow$ `HTTP 401 Unauthorized` (`ApiError.TENANT_NOT_BOUND`).
    4. Direct DB query without tenant bound $\rightarrow$ Returns 0 rows under PostgreSQL RLS (`D-56`).

**Out of scope**

- Keycloak server realm setup & user management (`W-10` Identity).
- Domain entity migrations beyond `core.tenant` and `core.user_tenant` (`W-13` onwards).
- Frontend tenant switcher component (`W-11`).

---

## 3. Flow

```
[Client / API Request]
      │
      │ HTTP Request (Bearer JWT / X-Tenant-Id header)
      ▼
[TenantContextFilter]
      │
      ├─► Is public exempt path? ──► Yes ──► Pass through filter chain
      │                                       │
      └─► No                                  ▼
            │                         [Business Controller]
            ├─► Extract user_id (sub) & target tenant_id
            ├─► Tenant provided?
            │      ├─► No ──► Query core.get_user_tenants(user_id):
            │      │           ├─► Exactly 1 tenant ──► Auto-bind single tenant
            │      │           └─► 0 or >1 tenants ──► Return ApiErrorResponse (401 TENANT_NOT_BOUND)
            │      │
            │      └─► Yes ──► Set temp app.current_tenant_id = target_tenant_id
            │                   Query core.user_tenant WHERE user_id = ? AND tenant_id = ?:
            │                   ├─► 0 rows ──► Clear context & return ApiErrorResponse (403 FORBIDDEN)
            │                   └─► 1 row  ──► TenantContext.set(target_tenant_id)
            │                                     │
            │                                     ▼
            │                            [Service Method @Transactional]
            │                                     │
            │                                     ▼ (Hibernate lazy connection acquisition)
            │                            [TenantAwareDataSourceProxy]
            │                                     │
            │                                     ├─► Intercepts physical Connection checkout
            │                                     ├─► Check conn.getAutoCommit() == false
            │                                     └─► TenantContext.setForConnection(conn)
            │                                            └─► SELECT set_config('app.current_tenant_id', ?, true)
            │                                                   │
            │                                                   ▼
            │                                           [Spring Data JPA / PostgreSQL Query with RLS]
            │                                                   │ (WHERE tenant_id = app.current_tenant_id)
            │                                                   ▼
            │                                           [Returns Target Tenant Rows]
            │
            └─► [finally block] ──► TenantContext.clear()
```

---

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Filter | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantContextFilter.java` | `[NEW]` Servlet filter extracting tenant claim/header, verifying `core.user_tenant` membership via target session binding, auto-binding single-tenant users, setting `TenantContext`, and ensuring cleanup in `finally` |
| Security | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantAuthenticationExtractor.java` | `[NEW]` Helper interface & default implementation for extracting user ID (`sub`) and target tenant UUID from Spring `Authentication` / `Jwt` or request header |
| Membership | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantMembershipService.java` | `[NEW]` Service checking user tenant membership against `core.user_tenant` table by binding `target_tenant_id` temporarily or calling `core.get_user_tenants` helper function |
| Database Binding Proxy | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantDatabaseInterceptor.java` | `[NEW]` DataSource proxy / TransactionSynchronization listener calling `TenantContext.setForConnection(conn)` on transactional JDBC connection checkout |
| Auto-Configuration | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantBindingAutoConfiguration.java` | `[NEW]` Spring Boot AutoConfiguration for registering tenant filter, membership service, and database DataSource proxy |
| Auto-Config Metadata | `code/backend/shared/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | `[NEW]` Registers `TenantBindingAutoConfiguration` |

**Exempt Public Endpoint Paths**

| Path Pattern | HTTP Method | Purpose | Auth Required |
|---|---|---|---|
| `/actuator/health` | `GET` | Container health probe | No |
| `/v3/api-docs/**` | `GET` | OpenAPI specification docs | No |
| `/swagger-ui/**` | `GET` | Swagger UI interface | No |
| `/api/v1/auth/login` | `POST` | User authentication endpoint | No |

**API Error Contract Usage**

When tenant extraction or verification fails, `TenantContextFilter` returns standard `ApiErrorResponse`:
- **Missing tenant for multi-tenant user**: `401 Unauthorized` with `ApiError.TENANT_NOT_BOUND` / `UNAUTHENTICATED`
- **User not a member in `core.user_tenant` / header spoofing**: `403 Forbidden` with `ApiError.FORBIDDEN`
- **Malformed tenant UUID**: `400 Bad Request` with `ApiError.VALIDATION_FAILED`

---

## 5. Frontend changes

None (Backend core filter and session binding foundation).

---

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V002__user_tenant.sql` | `core.user_tenant` | yes | yes |

- [x] `tenant_id` present on every new table (`CONVENTIONS.md` rule 7)
- [x] Index on `tenant_id` plus lookup columns (`idx_user_tenant_tenant_id`, `idx_user_tenant_user_id`, `idx_user_tenant_unique` - `DEBT-018`)
- [x] RLS enabled with `CREATE POLICY tenant_isolation` using 3-branch `CASE` (`D-56`)

Flyway migration file to be created under `code/backend/migration/src/main/resources/db/migration/core/V002__user_tenant.sql`:

```sql
CREATE TABLE core.user_tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_user_tenant_tenant_id ON core.user_tenant(tenant_id);
CREATE INDEX idx_user_tenant_user_id ON core.user_tenant(user_id);
CREATE UNIQUE INDEX idx_user_tenant_unique ON core.user_tenant(user_id, tenant_id);

ALTER TABLE core.user_tenant ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.user_tenant
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );

-- Helper function to retrieve user tenant memberships across RLS for single-tenant auto-binding
CREATE OR REPLACE FUNCTION core.get_user_tenants(p_user_id UUID)
RETURNS TABLE (tenant_id UUID)
LANGUAGE sql
SECURITY DEFINER
AS $$
    SELECT tenant_id FROM core.user_tenant WHERE user_id = p_user_id;
$$;
```

---

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `TenantContextFilterTest.java` | `[NEW]` Token claim parsing, `core.user_tenant` membership validation via target session binding, single-tenant auto-binding, multi-tenant missing claim rejection (401), invalid UUID format, unauthenticated request rejection, forbidden membership rejection (403), `finally` cleanup |
| Unit | `TenantDatabaseInterceptorTest.java` | `[NEW]` Calling `setForConnection` on transactional connections, throwing `IllegalStateException` on auto-commit connections |
| Integration | `TenantBindingIT.java` | `[NEW]` Full Spring Boot + Testcontainers integration test executing `V002__user_tenant.sql` migration, inserting test membership rows, executing **real HTTP requests via `MockMvc`** through Spring Security filter chain: verifying valid HTTP request binding, header spoofing 403 rejection, missing tenant 401 rejection, Spring Data JPA `@Transactional` connection binding, and `unboundQuery_returnsZeroRows_underRLS`. |

---

## 8. Verification

Execute the full build and verification suite:

```bash
# 1. Run full verification suite (unit + integration tests)
(cd code/backend && ./mvnw clean verify)

# 2. Run explicit unit test suite
(cd code/backend && ./mvnw test -Dtest=Tenant*Test)

# 3. Run explicit integration test suite with Testcontainers (real MockMvc HTTP requests)
(cd code/backend && ./mvnw verify -Dtest=TenantBindingIT)

# 4. Controller Parameter Audit (Must return 0 matches / exit code 1)
git grep -nE '@RequestParam.*(tenantId|organizationId)|@PathVariable.*(tenantId|organizationId)|@RequestBody.*(tenantId|organizationId)' -- code/backend/
```

| Check | Command / Target | Expected Output | Result |
|---|---|---|---|
| Unit Tests | `(cd code/backend && ./mvnw test -Dtest=Tenant*Test)` | `BUILD SUCCESS` (Passes all claim parsing, header security, and cleanup unit tests) | Pending |
| Integration Tests (Real HTTP Requests) | `(cd code/backend && ./mvnw verify -Dtest=TenantBindingIT)` | `BUILD SUCCESS` (Runs V002__user_tenant.sql migration, passes MockMvc HTTP requests testing 200 OK, 401 TENANT_NOT_BOUND, 403 FORBIDDEN, and JPA transactional RLS isolation) | Pending |
| Unbound Query Behavior | `TenantBindingIT#unboundQuery_returnsZeroRows_underRLS` | Test passes: Unbound query returns zero rows under PostgreSQL RLS | Pending |
| Controller Parameter Audit | `git grep -nE '@RequestParam.*(tenantId\|organizationId)...'` | Exit code 1 / 0 lines returned (Zero endpoints take explicit tenant parameters) | Pending |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `ThreadLocal` leak across pooled web container threads | Low | Enforced `finally TenantContext.clear()` in `TenantContextFilter`. |
| Session variable persistence across pooled database connections | Low | `TenantContext.setForConnection` uses `is_local = true` (`SELECT set_config(..., true)`), which automatically resets the variable at transaction completion. |
| Auto-commit connection silent empty query results | Low | `TenantContext.setForConnection` checks `conn.getAutoCommit() == false` and throws `IllegalStateException` if auto-commit mode is detected (`D-57`). |
| RLS Evaluation Deadlock on Membership Query | Low | `TenantMembershipService` temporarily sets `app.current_tenant_id = target_tenant_id` on the connection during the membership check (or executes `core.get_user_tenants SECURITY DEFINER` function) before final context binding. |

---

## 10. Rollback

Revert the commits adding `TenantContextFilter`, `TenantAuthenticationExtractor`, `TenantMembershipService`, `TenantDatabaseInterceptor`, `TenantBindingAutoConfiguration`, and `V002__user_tenant.sql`.
