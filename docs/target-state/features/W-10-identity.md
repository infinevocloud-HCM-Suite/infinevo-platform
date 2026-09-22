# W-10 — Identity

| Field | Value |
|---|---|
| **Work item** | `W-10` · issue [#11](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/11) |
| **Kind** | Port — Centralize Identity & Single Sign-On via Keycloak (retire legacy HRMS auth) |
| **Stream / track** | Stream C — Core platform · Track P |
| **Wave** | 3 — Identity and tenancy |
| **Size / skill** | M · BE |
| **Owner** | unassigned |
| **Blocked by** | `W-08` #9 (merged 2026-09-20) |
| **Blocks** | `W-11` Authorization & roles · `W-12` Tenant, subscription & entitlement |
| **Capabilities** | `CORE-02` Identity & single sign-on |
| **Decisions** | `D-01` Keycloak central IdP · `D-02` OIDC token standard · `D-03` HRMS custom auth retired · `D-04` Core user profile sync · `D-21` Keycloak realm `HRMS` · `D-22` Deny-by-default auth · `D-56` RLS 3-branch CASE |
| **Gaps addressed** | Fragmented authentication paths and duplicate user credentials across HRMS (`ourusers`) and Payroll (`companyUser`) |
| **Status** | **Approved for build** |
| **Approved by** | Sanjib (founder) |
| **Approved on** | 2026-09-22 |

---

## 1. Problem

Currently, the platform has two separate, fragmented authentication paths across the legacy applications in [`legacy/`](file:///d:/Workspace/infinevo-platform/legacy):
- **HRMS** (`legacy/HRMS_Backend`): Uses a custom, standalone JWT token generation filter (`JWTAuthFilter.java`), local password hashing (BCrypt), local `ourusers` table, and `password_reset_token` table.
- **Payroll** (`legacy/Payroll-Bend-SBoot`): Uses Keycloak OAuth2 / OpenID Connect (OIDC) authentication (`KeycloakUserServiceImpl.java`, `HRMS` realm).

Having two separate auth systems creates security vulnerabilities, duplicate user credentials, fragmented user sessions, and maintenance overhead. As mandated by `D-03`, `09-build-order.md:181`, and issue `#11`:
> *HRMS's token system is deleted, not adapted. Two auth paths is the problem being solved.*

Without centralizing identity in Keycloak:
- A user logged into Payroll cannot seamlessly access HRMS capabilities without re-authenticating.
- Local password storage in HRMS presents security risks and prevents unified password reset enforcement.
- User identity references between HRMS (`ourusers`) and Payroll (`companyUser`) are incompatible, preventing unified tenant employee mapping (`W-13`).

---

## 2. Scope

**In scope**

- **Keycloak Realm & Client Configuration (`infra/keycloak/`)**:
  - Configure unified `HRMS` platform realm (`D-21`) with standard OIDC public client (`react-app`) and bearer-only backend API resource clients.
  - Standardize OIDC JWT token structure to contain claims: `sub` (Keycloak User UUID), `preferred_username`, `email`, `given_name`, `family_name`, and custom claim `tenant_id`.
- **Backend Resource Server JWT Authentication (`code/backend/shared`)**:
  - Configure Spring Security Resource Server (`oauth2ResourceServer().jwt()`) in `shared` module for all platform backend microservices.
  - Enforce `D-22` Deny-by-default security policy (`.anyRequest().authenticated()`). No blanket `permitAll()` prefix grants exist.
  - Implement `KeycloakJwtAuthenticationConverter` to convert standard OIDC JWT claims into Spring `Authentication` principal (`JwtAuthenticationToken`).
  - Enforce token signature verification against Keycloak OIDC JWKS (JSON Web Key Set) endpoint.
- **Integration with `W-08` Tenant Context Filter**:
  - Ensure Spring Security Resource Server filter executes prior to `TenantContextFilter` (`W-08`).
  - `TenantContextFilter` extracts `sub` and `tenant_id` from the authenticated `JwtAuthenticationToken` principal, verifies tenant membership against `core.user_tenant`, and binds `TenantContext` & DB session `app.current_tenant_id` (`D-56`, `D-57`).
- **Core User Profile Synchronization (`UserProfileSyncService`)**:
  - Automatically synchronize Keycloak user identity (`sub`, `email`, `first_name`, `last_name`) into `core.user_account` (`02-data-model.md:54`) upon valid authentication.
  - Link user accounts to core employees (`CORE-04`) via `user_account_id`.
- **Password Reset & Credential Delegation**:
  - Delegate all password creation, password resets, multi-factor authentication (MFA), and credential management completely to Keycloak OIDC workflows.
  - Retire and delete HRMS `password_reset_token` table (`02-data-model.md:63`) and endpoints.
- **Deletion of Legacy HRMS Auth Code (`legacy/HRMS_Backend`)**:
  - Completely delete legacy HRMS custom security filters (`JWTAuthFilter`, `SecurityConfig`, `AuthEntryPointJwt`, `JwtUtils`, `OurUserDetailsService`), local password encoders, and custom login controllers.

**Out of scope**

- Redis-backed authorization and role-to-action checking (`W-11` Authorization & roles).
- Tenant module entitlement enforcement (`W-12`).
- Employee Master profile data beyond authentication account identity (`W-13`).

---

## 3. Flow

```
[Client / Single Page App]
      │
      ├─► 1. Authenticate with Keycloak (OIDC Authorization Code Flow + PKCE)
      │      Keycloak Issues OIDC Bearer JWT Token
      │
      │ 2. HTTP Request with Bearer JWT Token (Header: Authorization: Bearer <token>)
      ▼
[Spring Security Resource Server (shared/SecurityConfig)]
      │
      ├─► Validate JWT Signature & Issuer against Keycloak JWKS endpoint
      │
      ├─► KeycloakJwtAuthenticationConverter:
      │      Extracts sub (Keycloak UUID), email, name, tenant_id claim
      │      Sets SecurityContextHolder.getContext().setAuthentication(jwtAuthToken)
      │
      ▼
[TenantContextFilter (W-08)]
      │
      ├─► Reads sub & target tenant_id from SecurityContext JWT
      ├─► Verifies core.user_tenant membership (using 3-branch CASE D-56)
      └─► Binds TenantContext & app.current_tenant_id for PostgreSQL RLS
            │
            ▼
[UserProfileSyncService (Core)]
      │
      ├─► Query core.user_account WHERE keycloak_sub = jwt.sub
      │      ├─► Exists ──► Update email, first_name, last_name, last_login_at if changed
      │      └─► New    ──► INSERT into core.user_account (tenant_id, keycloak_sub, email, status)
      │
      ▼
[Business Controller / REST API]
      │
      └─► Return HTTP 200 OK Response
```

---

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Security Config | `code/backend/shared/src/main/java/com/infinevo/shared/security/SecurityConfig.java` | `[NEW]` Centralized Spring Security Resource Server config validating Keycloak JWT tokens with `D-22` deny-by-default rules |
| Token Converter | `code/backend/shared/src/main/java/com/infinevo/shared/security/KeycloakJwtAuthenticationConverter.java` | `[NEW]` Converts Keycloak OIDC JWT token claims into Spring `JwtAuthenticationToken` principal |
| Entity | `code/backend/core/src/main/java/com/infinevo/core/identity/entity/UserAccount.java` | `[NEW]` Core entity mapping `core.user_account` database table (`keycloak_sub`, `email`, `status`, `tenant_id`) |
| Repository | `code/backend/core/src/main/java/com/infinevo/core/identity/repository/UserAccountRepository.java` | `[NEW]` Spring Data JPA repository for querying `core.user_account` by `keycloakSub` and `email` |
| Service | `code/backend/core/src/main/java/com/infinevo/core/identity/service/UserProfileSyncService.java` | `[NEW]` Service auto-synchronizing Keycloak JWT user claims to `core.user_account` |
| Controller | `code/backend/core/src/main/java/com/infinevo/core/identity/controller/UserController.java` | `[NEW]` Exposes `GET /api/v1/users/me` returning current authenticated user profile details |
| Legacy HRMS Security | `code/backend/hrms/.../security/*` | `[DELETE]` Delete legacy `JWTAuthFilter`, `JwtUtils`, `AuthEntryPointJwt`, `OurUserDetailsService`, and custom login endpoints |

**API Contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/users/me` | None | `200 OK` (`UserAccountResponse` DTO) | Bearer JWT |
| `POST` | `/realms/{realm}/protocol/openid-connect/token` | Grant Type: `password` or `authorization_code` | `200 OK` (`access_token`, `refresh_token`, `id_token`) | Keycloak Native |
| `POST` | `/realms/{realm}/protocol/openid-connect/logout` | Refresh token | `204 No Content` | Keycloak Native |

*Note: All custom endpoints enforce `D-22` Deny-by-default authentication. No public unauthenticated endpoint prefixes exist.*

---

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/auth/keycloak.ts` | `[NEW]` Keycloak OIDC JavaScript client configuration (`keycloak-js`) |
| `code/frontend/src/auth/AuthProvider.tsx` | `[NEW]` React Context Provider managing Keycloak authentication state and token refresh |
| `code/frontend/src/auth/ProtectedRoute.tsx` | `[NEW]` Route guard redirecting unauthenticated users to Keycloak SSO login page |
| Legacy Auth Screens | `[DELETE]` Remove legacy local login form (`HRMS_Frontend`) and custom password reset components |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/login` | `KeycloakRedirect` | Redirects to Keycloak SSO Login URL |
| `/auth/callback` | `AuthCallback` | Handles OIDC authorization code exchange |

---

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V003__user_account.sql` | `core.user_account` | yes (`tenant_id`) | yes |

**Schema Definition (`core.user_account`)**

```sql
CREATE TABLE core.user_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    keycloak_sub VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_account_sub UNIQUE (keycloak_sub)
);

CREATE INDEX idx_user_account_tenant_email ON core.user_account(tenant_id, email);

-- Enable RLS with 3-branch CASE policy (D-56)
ALTER TABLE core.user_account ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.user_account
    USING (
        CASE 
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );
```

- [x] `tenant_id` present on `core.user_account` (`CONVENTIONS.md` rule 7)
- [x] Unique index on `keycloak_sub` and index on `(tenant_id, email)` (`DEBT-018`)
- [x] RLS 3-branch `CASE` isolation policy applied (`D-56`)
- [x] Flyway script created in `core` schema migrations (`CONVENTIONS.md` rule 4)

---

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `KeycloakJwtAuthenticationConverterTest.java` | Validates JWT claim extraction (`sub`, `email`, `tenant_id`, roles) |
| Unit | `UserProfileSyncServiceTest.java` | Verifies `core.user_account` creation and update synchronization logic |
| Integration | `KeycloakAuthIT.java` | Spring Boot Testcontainers test with Mock JWT Issuer verifying: <br>1. Valid Keycloak JWT -> HTTP 200 & user auto-synced to `core.user_account` <br>2. Missing/Expired JWT -> HTTP 401 Unauthorized <br>3. Legacy auth endpoints return HTTP 404/Retired |

---

## 8. Verification

How to prove it works:

```bash
# Execute unit and integration tests for Identity module
./mvnw clean test -Dtest=KeycloakJwtAuthenticationConverterTest,UserProfileSyncServiceTest,KeycloakAuthIT
```

| Check | Expected | Result |
|---|---|---|
| Keycloak JWT Token Validation | Valid OIDC JWT passes Spring Resource Server filter | Pending |
| User Profile Sync | Keycloak user `sub` is persisted in `core.user_account` | Pending |
| Single Sign-On | Single token provides access across both HRMS and Payroll APIs | Pending |
| Legacy Auth Deletion | Legacy HRMS custom JWT endpoints return HTTP 404 | Pending |
| Deny-By-Default Security | Unauthenticated API requests are rejected with HTTP 401 | Pending |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Keycloak server downtime prevents user logins | Low | Configure Keycloak high-availability cluster / container replica setup |
| JWT claim mismatch during profile sync | Low | Enforce strict OIDC claim mapping unit tests in `KeycloakJwtAuthenticationConverterTest` |
| Remnants of legacy HRMS auth bypass Keycloak | Medium | Complete deletion of legacy `security` package in HRMS + CI build security filter inspection |

---

## 10. Rollback

If issues arise during deployment:
1. Revert Flyway migration `V003__user_account.sql` if required.
2. Re-route HTTP traffic to existing auth endpoints via Azure API Management / NGINX gateway.
3. Keycloak server configuration can be restored using version-controlled export json (`infra/keycloak/realm-export.json`).
