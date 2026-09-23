# Feature: Identity & single sign-on

| Field | Value |
|---|---|
| **Feature ID** | `W-10` · ticket #11 · `CORE-02` |
| **Promoted to** | `docs/target-state/features/W-10-identity.md` on branch `W-10-identity` |
| **Owner** | unassigned |
| **Apps touched** | new `code/backend/shared`, `code/backend/app`, `code/frontend`, `infra/docker/keycloak` |
| **Related gaps** | BUG-001 (fixed), DEBT-004 (discounted), DEBT-033 (deferred) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `shared` only | 1 |
| Flyway migration | `V009__user_account.sql` | 1 |
| Externally testable behaviour | one Keycloak login reaches a protected endpoint in both modules | 1 |
| Frontend area | `src/shell` auth | 1 |

Within cap.

---

## 1. Problem

Two authentication systems exist and neither talks to the other.

- HRMS mints its own JWT with a hardcoded 93-character HS256 secret — `legacy/HRMS_Backend/.../service/JWTUtils.java:26,31-41`
- Payroll validates Keycloak tokens against realm `HRMS` — `legacy/Payroll-Bend-SBoot/.../config/SecurityConfig.java:38-51`
- An employee logged into HRMS is not recognised by Payroll and vice versa — `legacy/docs/GAP_INVENTORY.md:27` (BUG-001)

The new platform has the dependency but no configuration.

- `spring-boot-starter-oauth2-resource-server` is declared — `code/backend/shared/pom.xml:27`
- No `issuer-uri` is set in any profile — `code/backend/app/src/main/resources/application.yml:44-48`
- No `SecurityFilterChain` bean exists anywhere in `code/backend/`

Three defects in what is already merged block a working login.

- The dev realm puts a tenant **slug** in a user attribute, `"tenant": ["acme-payroll"]` — `infra/docker/keycloak/dev-realm.json:66-70`
- `TenantAuthenticationExtractor` requires a **UUID** in a `tenant_id` or `tid` claim and returns `400 VALIDATION_FAILED` on anything else — `code/backend/shared/.../TenantAuthenticationExtractor.java:76-90`
- The realm declares no protocol mapper, so no tenant claim reaches the token at all — `infra/docker/keycloak/dev-realm.json:28-50`
- Nothing seeds `core.user_tenant`, so every authenticated request falls to `401 TENANT_NOT_BOUND` — `infra/docker/seed/01-tenants.sql:23-26`, `TenantContextFilter.java:88-98`
- `TenantContextFilter` exempts `/api/v1/auth/login`, a local-login path the retirement of HRMS's token system says must never exist — `TenantContextFilter.java:29`

## 2. Scope

**In scope**

- One `SecurityFilterChain` in `shared`, resource-server only, `issuer-uri` from configuration
- Realm export rewritten: fixed user UUIDs, a `tenant_id` protocol mapper emitting the tenant UUID, password policy, token lifetimes
- `core.user_account` — the local profile, one row per (user, tenant)
- First-request profile sync: an authenticated principal with a `user_tenant` row gets its `user_account` row created or refreshed from token claims
- `core.user_tenant` seed rows binding the three dev users to the two seeded tenants
- Frontend: `keycloak-js` in the shell, token attached by the one API client
- Deleting the `/api/v1/auth/login` exemption

**Out of scope**

- Roles, actions and any `hasAuthority` decision — that is `W-11` (#12)
- Provisioning a user who has no `user_tenant` row — that is `W-24` invitations (#28)
- Subscription and entitlement — `W-12` (#13)
- Migrating real `ourusers` / `companyUser` rows — `W-67`
- Password reset screens; Keycloak's own pages serve it (`02-data-model.md:64`)

## 3. Flow

```
[Browser] --> [Keycloak /realms/infinevo] --> [SPA holds token]
   --> [Authorization: Bearer] --> [SecurityFilterChain validates via JWKS]
   --> [TenantContextFilter binds tenant from tenant_id claim + user_tenant]
   --> [UserProfileSyncFilter upserts core.user_account]
   --> [protected endpoint]
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `shared/.../security/ResourceServerConfig.java` | new — `SecurityFilterChain`, JWKS validation, stateless, `/actuator/health` permitted |
| Filter | `shared/.../tenant/TenantContextFilter.java` | change — drop `/api/v1/auth/login` from `EXEMPT_PATH_PATTERNS` (line 29) |
| Filter | `shared/.../identity/UserProfileSyncFilter.java` | new — runs after `TenantContextFilter`, upserts the profile |
| Entity | `shared/.../identity/UserAccount.java` | new |
| Repository | `shared/.../identity/UserAccountRepository.java` | new |
| Service | `shared/.../identity/UserProfileSyncService.java` | new |
| Config | `app/src/main/resources/application.yml` | change — add `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${KEYCLOAK_ISSUER_URI}` |

`KEYCLOAK_ISSUER_URI` is already passed to the container — `infra/docker/compose.yml:160`.

**API contract**

W-10 adds no endpoint. Login is a browser redirect to Keycloak; the backend only validates.

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me` | — | `{userId, email, firstName, lastName, tenantId}` | Bearer, tenant bound |

One endpoint exists so the login can be proved end to end without waiting for `W-13`.

## 5. Frontend changes

| File | Change |
|---|---|
| `src/shell/auth/keycloak.js` | new — adapter, URL and realm from `VITE_KEYCLOAK_URL` |
| `src/main.jsx` | change — `init({onLoad:'login-required'})` before render |
| `src/shared/api/client.js` | change — request interceptor attaching the bearer token, fulfilling the note at line 13 |
| `package.json` | change — add `keycloak-js` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/` | `AppShell` | authenticated; unauthenticated redirects to Keycloak |

The tenant stays off the client, as `client.js:16-18` requires.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V009__user_account.sql` | `core.user_account` | yes | additive only |

- [x] `tenant_id` present — `user_account` carries it, one row per (user, tenant)
- [x] Index on `tenant_id` plus lookup columns — `(tenant_id, keycloak_user_id)` unique, `(tenant_id, email)`
- [x] Money columns — none
- [x] Expand / contract — a new table only, no destructive step

Columns, merging `ourusers` (`OurUsers.java:24-47`) and `companyUser` (`CompanyUser.java:10-41`):

`id uuid` · `tenant_id uuid` · `keycloak_user_id uuid` · `email` · `first_name` · `last_name` · `phone_number` · `status` · `last_synced_at` · the four audit columns already used by `V002__user_tenant.sql:8-11`.

Dropped deliberately: `password` — Keycloak is the authority (`01-platform-shape.md:63`); `blacklistedToken` — stateless tokens do not need a blacklist.

One table, one script, matching `migration/README.md:127-129`. RLS and the `tenant_isolation` policy with the mandatory `CASE` form go in the same script — `migration/README.md:76-123`.

**A second script seeds nothing.** The three `user_tenant` rows go in `infra/docker/seed/02-user-tenants.sql`, local-only, beside `01-tenants.sql`, because seed data is not schema.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `shared/.../security/ResourceServerConfigTest.java` | unauthenticated request → 401; health → 200 |
| Unit | `shared/.../identity/UserProfileSyncServiceTest.java` | insert on first sight, update on changed claim, no write when unchanged |
| Integration | `shared/.../identity/UserAccountRlsIT.java` | tenant A cannot read tenant B's `user_account` row as `app_user` |
| Integration | `shared/.../identity/LoginFlowIT.java` | a token minted for the dev realm reaches `/api/v1/me` with the right tenant bound |

`LoginFlowIT` extends `AbstractIntegrationTest` and adds a Keycloak container; it must carry `@EnabledIfDockerAvailable`, as `code/backend/shared/src/test/java/com/infinevo/shared/test/EnabledIfDockerAvailable.java` requires, or it will skip silently the way #117 did.

## 8. Verification

```bash
# 1. Migration applies and the table is protected
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres \
  psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid = 'core.user_account'::regclass;"

# 2. Full stack, then a real token
docker compose -f infra/docker/compose.yml up -d
bash infra/docker/seed/seed.sh
TOKEN=$(curl -s -d client_id=infinevo-web -d username=admin.globex \
  -d password=local_dev_pw -d grant_type=password \
  http://localhost:8081/realms/infinevo/protocol/openid-connect/token | jq -r .access_token)

# 3. The claim the binding filter needs is actually in the token
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq .tenant_id

# 4. The protected endpoint
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/me

# 5. Tests
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| 1 — RLS on `core.user_account` | `t` |
| 3 — `tenant_id` claim | `22222222-2222-2222-2222-222222222222` |
| 4 — with token | `200`, body naming Gita Globex and that tenant |
| 4 — without token | `401` |
| `/api/v1/auth/login` | `404` — the path is gone, not exempted |
| 5 — suite | green, and `LoginFlowIT` **runs** rather than skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Realm user UUIDs are generated at import, so a seeded `user_tenant` row cannot reference them | **high — certain unless handled** | Pin `"id"` on each user in the realm export; the seed then references fixed UUIDs on both sides |
| `security.jwt.signing-secret` stays in `application.yml:46` and a custom-JWT path grows back | medium | Remove it in this ticket; `W-56` put it there for secret plumbing, and nothing reads it |
| `X-Tenant-Id` header still overrides the token claim (`TenantAuthenticationExtractor.java:28-31`), which reads against `client.js:16-18` | low — membership is still verified | Leave the behaviour, correct the comment; a multi-tenant user needs some way to choose |
| Keycloak container makes `LoginFlowIT` slow or flaky in CI | medium | Reuse one container for the class; assert on a minted token, not on browser redirects |

## 10. Rollback

Nothing is deployed, so rollback is branch-level. `V009` is additive and forward-only — `migration/README.md:135-143`. If login must be disabled after merge, unset `KEYCLOAK_ISSUER_URI`; the app then fails to start rather than serving unauthenticated, which is the safe direction.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | `core.user_account` has both, in `V009` |
| Flyway for every schema change, `ddl-auto` nowhere | one script; no `ddl-auto` added |
| `Money`/`BigDecimal` for money | creates no money column |
| Index on `tenant_id` plus lookup columns | two composite indexes, `tenant_id` leading |
| Expand / contract | new table only |
| No module references another module | code lands in `shared`; `hrms` and `payroll` are untouched |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-001 no SSO (`GAP_INVENTORY.md:27`) | **Fixed.** This is the ticket that closes it |
| DEBT-004 secrets in `.properties` (`:42`) | **Discounted.** `W-56` already moved secrets to Key Vault; this ticket adds no new secret |
| DEBT-033 MD5 `X-API-KEY` between HRMS and Payroll (`:83`) | **Deferred.** One backend removes the call entirely; nothing here re-creates it |

## 13. Decisions — settled at approval, 2026-09-22

| # | Question | Answer |
|---|---|---|
| 1 | One profile row per tenant, or per user? | **Per tenant.** Hard rule 7 admits no exception outside `reference`; a user in two tenants gets two rows and a duplicated name and email |
| 2 | A user with no `user_tenant` row gets `401` | **Accepted.** `W-10` does not provision; `W-24` (#28) does. Nobody self-registers until then |
| 3 | `security.jwt.signing-secret` at `application.yml:46` | **Delete it in this ticket.** Nothing reads it, and leaving it invites a custom-JWT path back |
