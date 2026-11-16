# Feature: Block Core Mutations for Suspended Customers

| Field | Value |
|---|---|
| **Feature ID** | `W-85` · `CORE-SECURITY` |
| **Promoted to** | `docs/target-state/features/W-85-block-core-for-suspended-customers.md` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/shared`, `code/backend/core`, `code/frontend` (`src/shell`) |
| **Related gaps** | founder 2026-11-13 planned product roadmap ("Priority 1 — customer lifecycle: `W-85` Block Core for suspended customers") |
| **Status** | **In Review (pending manager approval)** |
| **Approved by** | — (pending manager review) |
| **Approved on** | — |
| **Blocked by** | nothing (founder decision: viewing and export remain permitted read-only; all mutations blocked) |

---

## Size cap

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `shared` (with `core` subscription provider) | 1 | **Pass (1 module)** |
| Flyway migration | None (creates 0 tables, reuses `core.subscription.status`) | 1 | **Pass (0 migrations)** |
| Externally testable behaviour | State-mutating requests (`POST`, `PUT`, `PATCH`, `DELETE`) to Core endpoints for a `SUSPENDED` or `CANCELLED` tenant are refused with `403 TENANT_SUSPENDED`; read-only (`GET`, `HEAD`, `OPTIONS`) requests and data exports succeed | 1 | **Pass (1 behaviour)** |
| Frontend area | `shell` (suspension banner and disabling mutation actions) | 1 | **Pass (1 area)** |

Within size cap across all four axes. No split required.

---

## 1. Problem & Decision

### 1.1 The problem today
When a customer's subscription is marked `SUSPENDED` or `CANCELLED` (`core.subscription.status`, `code/backend/core/src/main/java/com/infinevo/core/subscription/SubscriptionStatus.java:21-24`):
1. **HRMS and Payroll are protected**: Endpoints annotated with `@RequiresModule(PlatformModule.HRMS)` or `@RequiresModule(PlatformModule.PAYROLL)` run through `RequiresModuleAspect.java:39-48` and `EntitlementService.java:113-116`, which actively inspect the subscription snapshot and throw `ApiError.TENANT_SUSPENDED` (HTTP 403).
2. **Core is completely unguarded**: Core is the foundational platform layer and is never optional, so Core controllers (`EmployeeController`, `OrgController`, `LeaveTypeController`, `DocumentController`, `TenantController`) do not carry `@RequiresModule`.
3. **Unrestricted changes on suspended tenants**: A tenant admin or HR user of a suspended company can currently continue creating employees, modifying bank details, inviting new users, updating departments, and changing leave policies without restriction.

### 1.2 The settled decision: Viewing & Export while Suspended
- **State-mutating requests (`POST`, `PUT`, `PATCH`, `DELETE`)**: Strictly **BLOCKED** with `403 TENANT_SUSPENDED`. No employee creation, profile edits, document uploads, or configuration changes can occur.
- **Safe read-only requests (`GET`, `HEAD`, `OPTIONS`)**: **PERMITTED**. Suspended customers retain access to inspect their records for statutory compliance and labour law audit retention.
- **Data Export (`core.employee.export`, etc.)**: **PERMITTED**. A customer who is suspended or leaving the platform must legally be able to export their own employment, attendance, and leave records.
- **Platform Admin & Impersonation**: **EXEMPT**. Platform administrators (and live support impersonation sessions with `core.tenant.manage`) can execute operations to remediate accounts or process renewals.

---

## 2. Scope

### In scope
- **Central Tenant Suspension Guard (`TenantSuspensionAspect` in `code/backend/shared`)**:
  - Intercepts all controller methods matching `@RequiresAction` or Spring `@RequestMapping` / `@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping`.
  - Ordered ahead of `RequiresActionAspect` (Order: `Ordered.HIGHEST_PRECEDENCE + 95`).
  - Evaluates request HTTP method:
    - If `GET`, `HEAD`, or `OPTIONS`: passes through.
    - If `POST`, `PUT`, `PATCH`, or `DELETE`: checks tenant subscription status.
    - If status is `SUSPENDED` or `CANCELLED`, rejects immediately with `ApiError.TENANT_SUSPENDED` ("This tenant's subscription is suspended", HTTP 403).
  - Skips check for platform admin tenant (`00000000-0000-0000-0000-000000000001`) and active platform impersonation sessions.
- **Subscription Status Provider & Cache Integration**:
  - Leverages `PermissionCache` / `EntitlementSource` to read the cached `EntitlementSnapshot` avoiding additional database queries on every HTTP call.
- **Frontend UI Suspension Banner & Disabling (`code/frontend/src/shell/`)**:
  - Global banner rendered in `AppShell.jsx` when the active tenant subscription status is `SUSPENDED` or `CANCELLED`:
    `"Your subscription is suspended. View-only access and statutory data exports remain available. Contact support@infinevo.com to restore service."`
  - Visual disabling of primary action buttons (e.g. "Add Employee", "Save", "Delete", "Send Invitation") across Core screens.

### Out of scope
- Automatic scheduled job moving overdue tenants to `PAST_DUE` (owned by `W-86`).
- Stripe / Razorpay payment gateway integration (owned by `W-94`).
- Total login lockout blocking all access (rejected by the statutory retention decision).

---

## 3. Flow

```
[Incoming HTTP Request to Core Endpoint]
                  │
                  ▼
[TenantContextFilter: Binds TenantContext & MDC]
                  │
                  ▼
[TenantSuspensionAspect (ORDER = HIGHEST_PRECEDENCE + 95)]
                  │
  ┌───────────────┴────────────────────────┐
  ▼                                        ▼
[Safe Read-Only (GET/HEAD/OPTIONS)]      [Mutating Request (POST/PUT/PATCH/DELETE)]
  │                                        │
  ▼                                        ▼
Proceed to Controller / Export           [Check EntitlementSnapshot.status]
                                           ├── ACTIVE or PAST_DUE ──► Proceed to Controller
                                           └── SUSPENDED / CANCELLED ──► Refuse 403 TENANT_SUSPENDED
```

---

## 4. Architecture & Changes

### 4.1 Backend changes (`code/backend/shared`)

| Layer | File | Change |
|---|---|---|
| Aspect | `com.infinevo.shared.tenant.TenantSuspensionAspect.java` | **New**. Spring AOP aspect intercepting controller methods; checks HTTP method and refuses state mutations when tenant is `SUSPENDED` or `CANCELLED`. |
| Port | `com.infinevo.shared.tenant.TenantSubscriptionStatusSource.java` | **New**. Functional port in `shared` resolved by `core` to provide the current tenant's subscription status. |
| Test | `com.infinevo.shared.tenant.TenantSuspensionAspectTest.java` | **New**. Unit tests verifying GET passes, POST/PUT/DELETE fail with `403 TENANT_SUSPENDED`, and platform admin is exempt. |
| Integration | `com.infinevo.core.tenant.TenantSuspensionIT.java` | **New**. End-to-end integration test validating that `POST /api/v1/employees` returns 403 on a suspended tenant while `GET /api/v1/employees` succeeds. |

### 4.2 Frontend changes (`code/frontend`)

| File | Change |
|---|---|
| Shell header | `code/frontend/src/shell/Header.jsx` | Render warning banner when `tenant.subscriptionStatus === 'SUSPENDED'` (`file:code/frontend/src/shell/Header.jsx:184-232`). |
| App shell | `code/frontend/src/shell/AppShell.jsx` | Expose `isSuspended` flag to context so child screens can disable action buttons (`file:code/frontend/src/shell/AppShell.jsx:105-140`). |
| Core style | `code/frontend/src/core/core-ui.css` | Add styling for read-only suspension alert banner. |

---

## 5. Database Changes

> **Creates no database tables and executes 0 Flyway migrations.**

- [x] Standing rule: Reuses existing `core.subscription.status` (`ACTIVE`, `PAST_DUE`, `SUSPENDED`, `CANCELLED`) created by `V034__subscription.sql`.
- [x] Standing rule: No schema changes; purely behavioral enforcement.
- [x] Standing rule: No cross-module database foreign keys.

---

## 6. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `com.infinevo.shared.tenant.TenantSuspensionAspectTest.java` | Verifies `GET` requests pass through; verifies `POST`, `PUT`, `PATCH`, `DELETE` throw `EntitlementDeniedException(TENANT_SUSPENDED)`; verifies platform admin tenant is exempt. |
| Integration | `com.infinevo.core.tenant.TenantSuspensionIT.java` | Configures tenant with `status = 'SUSPENDED'`; calls `POST /api/v1/employees` $\rightarrow$ expects 403 `TENANT_SUSPENDED`; calls `GET /api/v1/employees` $\rightarrow$ expects 200 OK with employee list; calls `GET /api/v1/employees/export` $\rightarrow$ expects 200 OK CSV. |
| Component | `code/frontend/src/shell/AppShell.test.jsx` | Verifies persistent suspension banner renders when tenant status is `SUSPENDED`. |

---

## 7. Verification

Verification commands and steps:

```bash
# 1. Run backend tests for suspension guard
./mvnw -pl shared,core test -Dtest='TenantSuspensionAspectTest,TenantSuspensionIT'

# 2. Run frontend shell tests
cd code/frontend
npm test -- src/shell/AppShell.test.jsx

# 3. Frontend lint check
npm run lint
```

### Verification matrix

| Check | Request | Expected Status | Response Error Code |
|---|---|:---:|---|
| Suspended Tenant Read | `GET /api/v1/employees` | `200 OK` | — (Returns data) |
| Suspended Tenant Export | `GET /api/v1/employees/export` | `200 OK` | — (Returns CSV) |
| Suspended Tenant Create | `POST /api/v1/employees` | `403 Forbidden` | `TENANT_SUSPENDED` |
| Suspended Tenant Update | `PUT /api/v1/employees/{id}` | `403 Forbidden` | `TENANT_SUSPENDED` |
| Suspended Tenant Delete | `DELETE /api/v1/employees/{id}` | `403 Forbidden` | `TENANT_SUSPENDED` |
| Platform Admin Impersonation | `POST /api/v1/employees` with `X-Impersonation` | `201 Created` | — (Permitted for support staff) |

---

## 8. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Extra latency on every Core request | Negligible | Subscription status is cached in `EntitlementSnapshot` in-memory; check is a sub-millisecond enum comparison. |
| Critical statutory reports blocked during dispute | Nil | The spec explicitly guarantees read-only access and export capabilities remain functional. |
| Impersonation by platform support blocked | Low | Aspect explicitly checks `ActingAs.isSet()` and bypasses the suspension block for platform staff. |

---

## 9. Rollback

1. Behavioral change only: `TenantSuspensionAspect` can be disabled via a Spring configuration property (`infinevo.security.suspension-guard.enabled=false`) without redeploying database scripts.
2. Frontend banner renders conditionally based on API response and does not impact regular tenants.

