# W-10 Identity — Authentication & User Identity Evidence — 2026-09-22

| Component | Implementation | Auth Method | Token Validation | User Storage |
|---|---|---|---|---|
| **HRMS Backend** | Custom JWT (`jjwt` 0.12.5) | Bearer token in `Authorization` header | In-filter validation | `ourusers` table with email, role, actions |
| **HRMS Frontend** | React Context + localStorage | Stores JWT in `localStorage["token"]` | None (frontend-side only) | Context state restored on boot |
| **Payroll Backend** | Keycloak OAuth2 Resource Server | OAuth2 token from Keycloak realm | JwtAuthenticationConverter extracts realm roles | `companyUser`, `organizationUserMapping`, `organizationUserRoleMapping` |
| **Payroll Frontend** | Keycloak SSO (`keycloak-js`) | OAuth2 flow | Keycloak token validation | None (tokens only) |

---

## 1. HRMS Backend Custom JWT

### Token minting
**File:** `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/service/JWTUtils.java`

- **Token creation method:** `JWTUtils:31-41` — `createToken()` builds token with claims map, subject, issuedAt, expiration, signed with HS256
- **Token generation entry points:** 
  - `JWTUtils:43-52` — `generateToken(UserDetails, List<String> actions)` 
  - `JWTUtils:58-63` — `generateIdentityToken(OurUsers, List<String> roles)`
  - `JWTUtils:65-73` — `generateTokenForRole(OurUsers, String activeRole, List<String> actions)`
- **Token claims:**
  - `role` (lowercase first authority) — `JWTUtils:45-48`
  - `actions` (array of action names) — `JWTUtils:50`
  - `userId` (user integer ID) — `JWTUtils:60`
  - `roles` (array of role names) — `JWTUtils:61, 68-69`
  - `activeRole` (currently selected role) — `JWTUtils:70`
  - Standard JWT: `sub` (email/username), `iat` (issued at), `exp` (expiration)
- **Signing secret:** Hardcoded string (93 chars) truncated to 32 bytes for HS256 — `JWTUtils:26`
  ```
  "673567893696976453275974432697R634967R738467R678T3486576834R8763T4783876764538745673865"
  ```
- **Signing algorithm:** HS256 — `JWTUtils:28, 39`
- **Token lifetime:** 24 hours (86400000 ms) — `JWTUtils:23`

### Token validation
**File:** `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/config/JWTAuthFilter.java`

- **Filter class:** `JWTAuthFilter extends OncePerRequestFilter` — `JWTAuthFilter:20`
- **Validation flow:**
  1. Extract `Authorization` header — `JWTAuthFilter:34`
  2. Require `Bearer ` prefix — `JWTAuthFilter:38`
  3. Extract username from token — `JWTAuthFilter:44`
  4. Validate token against user details via `isTokenValid()` — `JWTAuthFilter:49`
  5. Set Spring SecurityContext with authorities — `JWTAuthFilter:50-59`
- **Token parser:** Uses `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` — `JWTUtils:77-81`
- **Claims extraction methods:** `JWTUtils:84-108` — `extractClaims()`, `extractUsername()`, `extractActions()`, `extractRoles()`, `extractActiveRole()`

### Security configuration
**File:** `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/config/SecurityConfig.java`

- **Filter chain:** `SecurityConfig:34-51`
- **Permitted endpoints (no auth required):**
  - `/auth/**` — `SecurityConfig:38`
  - `/public/**` — `SecurityConfig:38`
  - `/register` — `SecurityConfig:38`
- **Role-based path guards:**
  - `/admin/**` → `hasAuthority("admin")` — `SecurityConfig:39`
  - `/user/**` → `hasAuthority("user")` — `SecurityConfig:40`
  - `/adminuser/**` → `hasAnyAuthority("admin", "user")` — `SecurityConfig:41`
  - `/common/**` → `hasAnyAuthority("admin", "hr", "manager", "supervisor", "user")` — `SecurityConfig:42-43`
- **Session policy:** STATELESS — `SecurityConfig:46`
- **Filter position:** Added before `UsernamePasswordAuthenticationFilter` — `SecurityConfig:48`

### User entity
**File:** `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/OurUsers.java`

- **Table name:** `ourusers` — `OurUsers:18`
- **Primary columns:**
  - `id` (BIGINT PK, auto-increment) — `OurUsers:24`
  - `email` (VARCHAR, unique) — `OurUsers:27`
  - `password` (VARCHAR, BCrypt hashed) — `OurUsers:30`
  - `emp_id` (VARCHAR, unique) — `OurUsers:44`
  - `name` (VARCHAR) — `OurUsers:29`
  - `city` (VARCHAR) — `OurUsers:31`
  - `roles` (ManyToMany to `role` table via `user_roles` join) — `OurUsers:34-40`
  - `blacklistedToken` (VARCHAR, for logout tracking) — `OurUsers:46-47`
- **Relationships:**
  - Many-to-many: `OurUsers → Role` via `user_roles` join table — `OurUsers:34-40`
  - One-to-one: `OurUsers → Employee` — `OurUsers:49-52`
  - One-to-many: `OurUsers → UserActionMapping` — `OurUsers:54-55`

---

## 2. Payroll Backend Keycloak OAuth2

### OAuth2 Resource Server configuration
**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/config/SecurityConfig.java`

- **OAuth2 issuer URI:** `https://authentication.infinevocloud.com/realms/HRMS` (via `spring.security.oauth2.resourceserver.jwt.issuer-uri` in properties)
- **Keycloak realm:** `HRMS` — `SecurityConfig:44` (via `JwtGrantedAuthoritiesConverter`)
- **Security filter chain:** `SecurityConfig:20-36`
  - Permits `/api/public/**` and `/auth/**` without auth — `SecurityConfig:26-27`
  - Requires auth for `/api/**` and all other routes — `SecurityConfig:28-29`
  - Session policy: STATELESS — `SecurityConfig:30`
  - OAuth2 resource server configured with JWT converter — `SecurityConfig:31-33`

### JWT authentication converter
**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/config/SecurityConfig.java:38-51`

- **Converter class:** `JwtAuthenticationConverter` with `JwtGrantedAuthoritiesConverter` — `SecurityConfig:39-48`
- **Role extraction:**
  - Claims path: `realm_access.roles` — `SecurityConfig:44`
  - Authority prefix: `ROLE_` — `SecurityConfig:45`
  - Converts Keycloak realm roles to Spring authorities (e.g., `admin` → `ROLE_admin`)

### Keycloak admin client configuration
**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/config/KeycloakAdminConfig.java`

- **Configuration class:** Properties-based bean with prefix `keycloak.admin` — `KeycloakAdminConfig:6-7`
- **Fields:**
  - `serverUrl` — configured as `https://authentication.infinevocloud.com` — line 19-24
  - `realm` — configured as `HRMS` — line 26-31
  - `clientId` — configured as `hrms-payroll-backend` — line 33-38
  - `clientSecret` — hardcoded in `application.properties:19` as `G9ccmvOROhVK3JYNqy5vhMtfwVjCtUo6`
  - `username` — hardcoded as `admin` — `application.properties:20`
  - `password` — hardcoded as `admin@123` — `application.properties:21`

### Keycloak admin client provider
**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/config/KeycloakClientProvider.java`

- **Bean creation:** `getKeycloakInstance()` — `KeycloakClientProvider:16-25`
- **Grant type:** OAuth2Constants.CLIENT_CREDENTIALS — `KeycloakClientProvider:21`
- **Authentication:** Uses `clientId` and `clientSecret` from config — `KeycloakClientProvider:22-23`

### User creation in Keycloak
**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/keycloak/KeycloakUserServiceImpl.java`

- **Method:** `createUserInKeycloak(CompanyUserDTO)` — `KeycloakUserServiceImpl:101-148`
  1. Create `UserRepresentation` with email, first/last name, credentials — lines 107-121
  2. Set password (non-temporary) — lines 116-119
  3. Post to Keycloak `UsersResource.create()` — line 129
  4. Extract userId from response Location header — line 142
  5. Save to local `CompanyUser` table (see CompanyUserServiceImpl below)

- **Temporary user creation:** `createTemporaryUserInKeycloak(UserInvitationDTO)` — `KeycloakUserServiceImpl:292-343`
  - Generates password and creates user with minimal info
  - Handles 409 (conflict) by reusing existing Keycloak userId — line 328-331

- **Password reset in Keycloak:** `updateUserPassword(email, newPassword)` — `KeycloakUserServiceImpl:151-176`
  - Finds user by email, calls `resetPassword()` on Keycloak user resource

- **User lookup:** `getUserIdByEmail(email)` — `KeycloakUserServiceImpl:224-246`

### CompanyUser entity
**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/CompanyUser.java`

- **Table name:** `companyUser` — `CompanyUser:6`
- **Columns:**
  - `id` (BIGINT PK) — `CompanyUser:10-11`
  - `userId` (VARCHAR, unique, Keycloak UUID) — `CompanyUser:14`
  - `userEmail` (VARCHAR, unique) — `CompanyUser:19`
  - `firstName`, `lastName` (VARCHAR) — `CompanyUser:22-25`
  - `companyName`, `phoneNumber`, `country`, `states` (VARCHAR) — `CompanyUser:16, 28, 31, 34`
  - `password` (VARCHAR, BCrypt encoded) — `CompanyUser:37`
  - `toc` (Boolean, terms of conditions) — `CompanyUser:40-41`

### OrganizationUserMapping entity
**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/OrganizationUserMapping.java`

- **Table name:** `organizationUserMapping` — `OrganizationUserMapping:6`
- **Columns:**
  - `id` (BIGINT PK) — `OrganizationUserMapping:10-11`
  - `userId` (VARCHAR, NOT NULL, from Keycloak `sub` claim) — `OrganizationUserMapping:14`
  - `organizationId` (VARCHAR, NOT NULL) — `OrganizationUserMapping:17`

### OrganizationUserRoleMapping entity
**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/OrganizationUserRoleMapping.java`

- **Table name:** `organizationUserRoleMapping` — `OrganizationUserRoleMapping:6`
- **Columns:**
  - `id` (BIGINT PK) — `OrganizationUserRoleMapping:10-11`
  - `userId` (VARCHAR, NOT NULL, Keycloak UUID) — `OrganizationUserRoleMapping:14`
  - `organizationId` (VARCHAR, NOT NULL) — `OrganizationUserRoleMapping:17`
  - `roleId` (VARCHAR, nullable) — `OrganizationUserRoleMapping:20`
  - `roleName` (VARCHAR, nullable) — `OrganizationUserRoleMapping:23`
  - `isEmployeePortalEnable` (Boolean, default false) — `OrganizationUserRoleMapping:26`

---

## 3. Password Reset & User Creation

### HRMS Backend — password reset
**File:** `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/controller/PasswordResetController.java`

- **Forgot password endpoint:** `POST /auth/forgot-password` — `PasswordResetController:18-22`
  - Calls `PasswordResetService.sendResetLink(email)` — line 20
  - Sends email link (implementation in PasswordResetService, not shown)

- **Reset password endpoint:** `POST /auth/reset-password` — `PasswordResetController:24-27`
  - Accepts `ResetPasswordRequest` DTO
  - Calls `PasswordResetService.resetPassword(request)` — line 26

- **User creation:** No admin API for external systems; only local registration in `AuthController.java` and user management in `UsersManagementService.java`

### Payroll Backend — user creation
**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/CompanyUserServiceImpl.java`

- **Registration flow:** `registerUser(CompanyUserDTO)` — `CompanyUserServiceImpl:116-147`
  1. Create user in Keycloak via `KeycloakUserService.createUserInKeycloak()` — line 123
  2. Save local `CompanyUser` record with Keycloak `userId` — lines 127-143
  3. Encode password locally (BCrypt) — line 137

- **Keycloak admin API usage:**
  - Keycloak client initialized in `KeycloakClientProvider.getKeycloakInstance()` — `KeycloakClientProvider:16-25`
  - Grant type: CLIENT_CREDENTIALS with hardcoded client credentials — lines 21-23

---

## 4. Frontends

### HRMS Frontend — JWT handling
**File:** `legacy/HRMS_Frontend/src/components/context/ContextProvider.jsx`

- **Auth context provider:** `ContextProvider` — `ContextProvider:6-86`
- **Token storage:** localStorage key `"token"` — `ContextProvider:17`
- **Auth state structure:** `authState` JSON object with `authenticated`, `roles`, `activeRole`, `actions` — `ContextProvider:18, 51-56`
- **Token restoration on boot:** `useEffect` loads from localStorage — `ContextProvider:14-39`
- **Logout:** Clears all localStorage auth keys — `ContextProvider:59-69`

**File:** `legacy/HRMS_Frontend/src/axiosInterceptor.js`

- **Interceptor configuration:** Axios global request interceptor — `axiosInterceptor:12-30`
- **Unauthenticated endpoints (no token sent):**
  - `/auth/login` — `axiosInterceptor:7`
  - `/auth/forgot-password` — `axiosInterceptor:8`
  - `/auth/reset-password` — `axiosInterceptor:9`
- **Note:** The interceptor shown does NOT attach the JWT token — it only removes it from login endpoints. The actual token attachment is missing or elsewhere.

**File:** `legacy/HRMS_Frontend/src/components/auth/LoginPage.jsx`

- **Token stored on successful login:** `localStorage.setItem("token", response.token)` — `LoginPage:53, 120`
- **Token refreshed from response:** `localStorage.setItem("token", newToken)` — `LoginPage:73`

### Payroll Frontend — Keycloak SSO
**File:** `legacy/Payroll-Fend-react/src/shared/appConfig/keycloak.js`

- **Library:** `import Keycloak from "keycloak-js"` — `keycloak.js:1`
- **Configuration:**
  - Server URL: `http://localhost:8080` (hardcoded, dev-only) — line 3
  - Realm: `payroll-dev` — line 4
  - Client ID: `payroll-fe` — line 5

**File:** `legacy/Payroll-Fend-react/src/index.js`

- **Initialization flow:**
  1. Redux store created — line 6
  2. Axios interceptor imported — line 11
  3. Auth check via `checkAuthToken()` before render — lines 17-18
  4. App wrapped in Redux Provider — line 21

---

## 5. Profile Sync

**Finding:** NO EVIDENCE of profile synchronization between Keycloak and local database.

- Payroll backend creates users **in Keycloak first**, then saves a reference record in `CompanyUser` — `CompanyUserServiceImpl:116-147`
- Only basic metadata copied: email, first/last name, phone (CompanyUserDTO → CompanyUser)
- **No reverse sync:** Changes to Keycloak profiles are not pulled into the database
- **No event-driven sync:** No listeners or scheduled tasks to sync profile updates
- **No API endpoint:** No endpoint like `/sync-profile` or `/pull-keycloak-profile` exists

---

## 6. GAP_INVENTORY — Authentication-related issues

| ID | Component | Finding | Line | Legacy/Target |
|---|---|---|---|---|
| **BUG-001** | Auth / Security | Employees logged into HRMS not recognised by Payroll and vice versa. No SSO; users authenticate twice and tokens incompatible. | 27 | legacy |
| **DEBT-004** | Both backends | Secrets hardcoded in `.properties` — Keycloak secret, Cloudinary keys, Brevo key, `fed.secret`, DB passwords | 42 | legacy |
| **DEBT-033** | HRMS↔Payroll | HRMS↔Payroll integration authenticated by `X-API-KEY: md5("12345AB")`, a shared secret committed in plaintext | 83 | legacy |

---

## Cross-Service Integration (HRMS → Payroll)

**File:** `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/config/HashUtil.java`

- **API key MD5 hashing:** Shared secret `12345AB` is MD5-hashed before being sent in `X-API-KEY` header
- **Endpoint:** `POST /public/get-employee-leaves` (public, no JWT auth required but requires API key)

**File:** `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/config/WebClientConfig.java`

- **Payroll calls HRMS:** WebClient configured to attach MD5-hashed API key on every request

---

## Summary of Key Findings

1. **Dual auth systems:** HRMS uses custom JWT (hardcoded 93-character secret), Payroll uses Keycloak OAuth2 (client credentials flow). No shared identity provider.

2. **Token claims differ:** HRMS includes `role`, `actions`, `activeRole` in JWT. Payroll extracts `realm_access.roles` from Keycloak token.

3. **User storage:** HRMS stores users in `ourusers` table with email/password. Payroll stores Keycloak userId reference in `companyUser`, `organizationUserMapping`, `organizationUserRoleMapping`.

4. **Secrets in code:** JWT signing secret and Keycloak credentials hardcoded in properties files. API key for cross-service auth is plaintext shared secret MD5-hashed on wire.

5. **No profile sync:** Payroll copies basic user data to local database on creation but never syncs back from Keycloak.

6. **Frontend differences:** HRMS Frontend stores JWT in localStorage, Payroll Frontend uses Keycloak SDK (`keycloak-js`) for full OAuth2 flow.

