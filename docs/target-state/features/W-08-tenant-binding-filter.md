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

However, the application runtime lacks the boundary filter and database transaction hooks to:
1. Extract the active tenant identity from incoming HTTP authentication tokens or request headers.
2. Verify that the authenticated principal is a permitted member of that tenant.
3. Bind `TenantContext` to the executing HTTP thread for the duration of the request.
4. Bind `app.current_tenant_id` to open PostgreSQL database connections when a transaction opens.
5. Reliably clear `TenantContext` in a `finally` block at request completion to prevent `ThreadLocal` leaks.

Without this filter and database session binding:
- Queries executing against PostgreSQL run with no tenant bound. Under RLS (`D-56`), such queries evaluate to `NULL` / false and return zero rows.
- Developers risk falling back to legacy patterns of threading `organizationId` / `tenantId` manually as a method parameter across 270+ controller methods.
- Requests with invalid, missing, or unauthorized tenant claims are not intercepted uniformly at the edge, risking unauthenticated or cross-tenant access attempts.

---

## 2. Scope

**In scope**

- **Token Claim Extraction & Header Security Rule (`TenantAuthenticationExtractor`)**:
  - Extract active tenant UUID (`tenant_id` / `tid` claim from Spring Security JWT).
  - `X-Tenant-Id` header fallback is strictly restricted to `dev`/`test` profiles. In production, if `X-Tenant-Id` is present, it MUST match a UUID in the authenticated user's JWT `tenants` membership claim array; otherwise, the request is rejected with `403 FORBIDDEN` to prevent header spoofing.
- **Membership Verification**:
  - Verify that the authenticated user principal holds membership in the target `tenant_id` (from JWT `tenants` array claim or principal attributes).
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
- **Unit & Integration Tests**:
  - `TenantContextFilterTest`: Tests token claim parsing, header fallback security rules, missing claim handling, membership failure, and `finally` cleanup.
  - `TenantDatabaseInterceptorTest`: Tests session variable setting on transactional connections and refusal of auto-commit connections.
  - `TenantBindingIT`: Spring Boot Testcontainers integration test running actual Spring Data JPA repository queries within `@Transactional` blocks against PostgreSQL with RLS enabled, verifying data separation, unbound query zero-row behavior, and failure handling.

**Out of scope**

- Keycloak server realm setup & user management (`W-10` Identity).
- Domain entity migrations beyond `core.tenant` (`W-13` onwards).
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
            ├─► Extract tenant_id claim / header
            ├─► Validate UUID format & user membership
            │      │
            │      ├─► Invalid / Unauthorized? ──► Return ApiErrorResponse (401/403/400)
            │      │
            │      └─► Valid ──► TenantContext.set(tenantId)
            │                         │
            │                         ▼
            │                [Service Method @Transactional]
            │                         │
            │                         ▼ (Hibernate lazy connection acquisition)
            │                [TenantAwareDataSourceProxy]
            │                         │
            │                         ├─► Intercepts physical Connection checkout
            │                         ├─► Check conn.getAutoCommit() == false
            │                         └─► TenantContext.setForConnection(conn)
            │                                └─► SELECT set_config('app.current_tenant_id', ?, true)
            │                                       │
            │                                       ▼
            │                               [Spring Data JPA / PostgreSQL Query with RLS]
            │                                       │ (WHERE tenant_id = app.current_tenant_id)
            │                                       ▼
            │                               [Returns Target Tenant Rows]
            │
            └─► [finally block] ──► TenantContext.clear()
```

---

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Filter | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantContextFilter.java` | `[NEW]` Servlet filter extracting tenant claim, verifying membership, setting `TenantContext`, and ensuring cleanup in `finally` |
| Security | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantAuthenticationExtractor.java` | `[NEW]` Helper interface & default implementation for extracting tenant ID and membership claims from Spring `Authentication` / `Jwt` |
| Database Binding Proxy | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantDatabaseInterceptor.java` | `[NEW]` DataSource proxy / TransactionSynchronization listener calling `TenantContext.setForConnection(conn)` on transactional JDBC connection checkout |
| Auto-Configuration | `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantBindingAutoConfiguration.java` | `[NEW]` Spring Boot AutoConfiguration for registering tenant filter and database DataSource proxy |
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
- **Missing tenant header / claim**: `401 Unauthorized` with `ApiError.UNAUTHENTICATED` / `TENANT_NOT_BOUND`
- **User not a member of target tenant / spoofed header**: `403 Forbidden` with `ApiError.FORBIDDEN`
- **Malformed tenant UUID**: `400 Bad Request` with `ApiError.VALIDATION_FAILED`

---

## 5. Frontend changes

None (Backend core filter and session binding foundation).

---

## 6. Database changes

> Uses existing Flyway migration `V001__tenant.sql` from `W-07` and session variable `app.current_tenant_id`. No new database migrations required.

---

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `TenantContextFilterTest.java` | `[NEW]` Valid JWT claim binding, header fallback restriction, invalid UUID format, unauthenticated request rejection, forbidden tenant membership rejection, `finally` cleanup |
| Unit | `TenantDatabaseInterceptorTest.java` | `[NEW]` Calling `setForConnection` on transactional connections, throwing `IllegalStateException` on auto-commit connections |
| Integration | `TenantBindingIT.java` | `[NEW]` Full Spring Boot + Testcontainers integration test executing Spring Data JPA queries within `@Transactional` methods, verifying session variable binding, `unboundQuery_returnsZeroRows_underRLS`, and PostgreSQL RLS row isolation |

---

## 8. Verification

Execute the full build and verification suite:

```bash
# 1. Run full verification suite (unit + integration tests)
(cd code/backend && ./mvnw clean verify)

# 2. Run explicit unit test suite
(cd code/backend && ./mvnw test -Dtest=Tenant*Test)

# 3. Run explicit integration test suite with Testcontainers
(cd code/backend && ./mvnw verify -Dtest=TenantBindingIT)

# 4. Controller Parameter Audit (Must return 0 matches / exit code 1)
git grep -nE '@RequestParam.*(tenantId|organizationId)|@PathVariable.*(tenantId|organizationId)|@RequestBody.*(tenantId|organizationId)' -- code/backend/
```

| Check | Command / Target | Expected Output | Result |
|---|---|---|---|
| Unit Tests | `(cd code/backend && ./mvnw test -Dtest=Tenant*Test)` | `BUILD SUCCESS` (Passes all claim parsing, header security, and cleanup unit tests) | Pending |
| Integration Tests | `(cd code/backend && ./mvnw verify -Dtest=TenantBindingIT)` | `BUILD SUCCESS` (Passes all Spring Data JPA transactional RLS isolation tests) | Pending |
| Unbound Query Behavior | `TenantBindingIT#unboundQuery_returnsZeroRows_underRLS` | Test passes: Unbound query returns zero rows under PostgreSQL RLS | Pending |
| Controller Parameter Audit | `git grep -nE '@RequestParam.*(tenantId\|organizationId)...'` | Exit code 1 / 0 lines returned (Zero endpoints take explicit tenant parameters) | Pending |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `ThreadLocal` leak across pooled web container threads | Low | Enforced `finally TenantContext.clear()` in `TenantContextFilter`. |
| Session variable persistence across pooled database connections | Low | `TenantContext.setForConnection` uses `is_local = true` (`SELECT set_config(..., true)`), which automatically resets the variable at transaction completion. |
| Auto-commit connection silent empty query results | Low | `TenantContext.setForConnection` checks `conn.getAutoCommit() == false` and throws `IllegalStateException` if auto-commit mode is detected (`D-57`). |
| Header spoofing via `X-Tenant-Id` | Low | Restricted to `dev`/`test` profiles; in production, validated against token `tenants` membership claim list. |

---

## 10. Rollback

Revert the commits adding `TenantContextFilter`, `TenantAuthenticationExtractor`, `TenantDatabaseInterceptor`, and `TenantBindingAutoConfiguration`.
