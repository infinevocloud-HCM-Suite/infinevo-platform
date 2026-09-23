# W-12 — Tenant, subscription & entitlement

| Field | Value |
|---|---|
| **Work item** | `W-12` · issue [#13](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/13) |
| **Kind** | New — Core platform tenant management, subscription payment seam & entitlement enforcement |
| **Stream / track** | Stream C — Core platform · Track P |
| **Wave** | 3 — Identity and tenancy |
| **Size / skill** | L · BE |
| **Owner** | unassigned |
| **Blocked by** | `W-10` #11 (merged 2026-09-22) |
| **Blocks** | `W-24` Setup checklist & invitations · `W-45` Frontend shell · `W-65` Admin console |
| **Capabilities** | `CORE-01` Tenant management · `PLAT-01` Subscription & entitlement |
| **Decisions** | `D-12` Self-serve org creation & subscription payment seam · `D-56` RLS 3-branch CASE |
| **Gaps addressed** | Missing subscription model, lack of module-level entitlement enforcement, and static navigation feed leading to potential security vulnerabilities across HRMS & Payroll |
| **Status** | **Approved for build** |
| **Approved by** | Sanjib (founder) |
| **Approved on** | 2026-09-22 |

---

## 1. Problem

Currently, the platform lacks a unified subscription management engine, module entitlement framework, and dynamic navigation feed:
- **No Self-Serve Organisation Onboarding (`CORE-01`)**: Customers cannot create organizations self-serve and pick their desired modules (`HRMS`, `PAYROLL`, or `BOTH`). Legacy Payroll hardcodes organisation entity mapping in [`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/Organization.java:27`](file:///d:/Workspace/infinevo-platform/legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/Organization.java#L27) and [`OrganizationServiceImpl.java:45`](file:///d:/Workspace/infinevo-platform/legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/service/impl/OrganizationServiceImpl.java#L45).
- **No Payment Seam (`D-12`)**: The platform lacks a standardized `core.subscription` and `core.subscription_module` model carrying a subscription `status` field to act as the seam for future payment gateway integrations (`02-data-model.md:48`).
- **Security Vulnerability — Live Endpoints Gated Only by Hidden Menus**: Without server-side module entitlement checks, a Payroll-only tenant could directly invoke HRMS API endpoints if they guess the URL path. As mandated by `09-build-order.md:185`:
  > *Done when: a Payroll-only tenant gets a 403 on an HRMS endpoint and no HRMS menu. Watch: both halves, always. A hidden menu over a live endpoint is a security bug.*
- **Static Navigation Feed**: The frontend lacks an entitlement-aware navigation feed endpoint (`GET /api/v1/tenant/entitlements`), preventing dynamic UI menu rendering based on active tenant subscriptions (replacing hardcoded organisation checks in [`legacy/Payroll-Fend-react/src/pages/mainPages/leaveManagement/leaveAllocation/leaveAllocation.jsx:234`](file:///d:/Workspace/infinevo-platform/legacy/Payroll-Fend-react/src/pages/mainPages/leaveManagement/leaveAllocation/leaveAllocation.jsx#L234)).

---

## 2. Scope

**In scope**

- **Tenant Management & Self-Serve Organisation Creation Flow (`CORE-01`, `D-12`)**:
  - Expose API endpoints (`POST /api/v1/organisations`) to allow authenticated users to create a tenant organization, specify basic details, and select platform modules (`HRMS`, `PAYROLL`).
  - Automatically map the creating user to the new tenant in `core.user_tenant` (`W-08`).
- **Subscription Engine & Payment Seam (`PLAT-01`, `D-12`)**:
  - Implement `core.subscription` table holding subscription `status` (`ACTIVE`, `PENDING`, `CANCELLED`).
  - Implement `core.subscription_module` table recording individual granted modules per tenant (`module_code` = `HRMS`, `PAYROLL`).
  - Initialize subscriptions to `ACTIVE` status upon module selection (providing a seamless seam for future automated billing transitions).
- **Server-Side API Entitlement Enforcement Filter (`EntitlementEnforcementFilter`)**:
  - Intercept incoming HTTP API requests following `TenantContextFilter` execution (`W-08`).
  - Validate requested endpoint module requirements (e.g. `/api/v1/hrms/**` or `@RequiresModule(ModuleCode.HRMS)`) against active `core.subscription_module` grants for the current `TenantContext`.
  - Reject unauthorized module API accesses with `HTTP 403 Forbidden` (`ApiError.FORBIDDEN`, message: `"Tenant is not entitled to access module HRMS"`).
- **Navigation & Entitlement Feed API (`GET /api/v1/tenant/entitlements`)**:
  - Expose endpoint returning active module subscriptions and dynamic navigation menu structures for the currently bound tenant.
  - Return only entitled navigation items (e.g., a Payroll-only tenant receives only Core & Payroll menu options; HRMS menu items are omitted).
- **Flyway Database Migration (`V004__subscription.sql`)**:
  - Create `core.subscription` and `core.subscription_module` tables with mandatory `tenant_id` foreign keys, secondary indexes (`DEBT-018`), and RLS 3-branch `CASE` policies (`D-56`).

**Out of scope**

- Actual Stripe/Razorpay payment gateway integration (deferred per `D-12`; status seam is provided).
- Setup checklist progress tracking (`W-24`).
- Frontend shell layout components (`W-45`).
- Admin console tenant provision GUI (`W-65`).

---

## 3. Flow

```
[Authenticated User]
      │
      ├─► 1. POST /api/v1/organisations { "name": "Acme Corp", "modules": ["PAYROLL"] }
      │      - Creates core.tenant (tenant_id)
      │      - Inserts core.user_tenant (user_id, tenant_id)
      │      - Inserts core.subscription (status = 'ACTIVE')
      │      - Inserts core.subscription_module (module_code = 'PAYROLL')
      │
[Tenant User Request]
      │
      ├─► 2. HTTP Request to API Endpoint (e.g., GET /api/v1/hrms/employees)
      │      Header: X-Tenant-Id: <tenant_id> (or JWT tenant_id)
      │
      ▼
[TenantContextFilter (W-08)]
      │
      ├─► Binds TenantContext (tenant_id) & DB session app.current_tenant_id
      │
      ▼
[EntitlementEnforcementFilter (W-12)]
      │
      ├─► Resolves target module from URI / Handler (e.g., HRMS)
      ├─► Queries EntitlementService for tenant's active subscription_module grants
      │
      ├───► Module NOT active (e.g. Payroll-only tenant accessing HRMS)
      │        └─► Return HTTP 403 Forbidden (ApiError.FORBIDDEN)
      │
      └───► Module ACTIVE (or Core endpoint)
               └─► Proceed to Controller Handler ──► HTTP 200 OK

[Frontend Shell Request]
      │
      └─► 3. GET /api/v1/tenant/entitlements
             └─► Returns { "modules": ["PAYROLL"], "navigation": [...] } (HRMS menu omitted)
```

---

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Migration | `code/backend/migration/src/main/resources/db/migration/core/V004__subscription.sql` | `[NEW]` Flyway DDL for `core.subscription` and `core.subscription_module` with RLS 3-branch CASE policies |
| Annotation | `code/backend/shared/src/main/java/com/infinevo/shared/entitlement/RequiresModule.java` | `[NEW]` Custom annotation for marking controllers/methods with required platform module (`HRMS`, `PAYROLL`) |
| Enum | `code/backend/shared/src/main/java/com/infinevo/shared/entitlement/ModuleCode.java` | `[NEW]` Enumeration of platform modules (`CORE`, `HRMS`, `PAYROLL`) |
| Filter | `code/backend/shared/src/main/java/com/infinevo/shared/entitlement/EntitlementEnforcementFilter.java` | `[NEW]` Servlet filter verifying tenant module subscriptions against target endpoint requirements |
| Auto-Config | `code/backend/shared/src/main/java/com/infinevo/shared/entitlement/EntitlementAutoConfiguration.java` | `[NEW]` Spring Boot auto-configuration registering entitlement beans |
| Entity | `code/backend/core/src/main/java/com/infinevo/core/tenant/entity/Subscription.java` | `[NEW]` Core JPA Entity for `core.subscription` |
| Entity | `code/backend/core/src/main/java/com/infinevo/core/tenant/entity/SubscriptionModule.java` | `[NEW]` Core JPA Entity for `core.subscription_module` |
| Repository | `code/backend/core/src/main/java/com/infinevo/core/tenant/repository/SubscriptionRepository.java` | `[NEW]` JPA Repository for managing tenant subscriptions |
| Repository | `code/backend/core/src/main/java/com/infinevo/core/tenant/repository/SubscriptionModuleRepository.java` | `[NEW]` JPA Repository for active subscription module lookups |
| Service | `code/backend/core/src/main/java/com/infinevo/core/tenant/service/TenantService.java` | `[NEW]` Interface for organisation creation & subscription management |
| ServiceImpl | `code/backend/core/src/main/java/com/infinevo/core/tenant/service/impl/TenantServiceImpl.java` | `[NEW]` Organisation creation flow, module selection, and user-tenant mapping logic |
| Service | `code/backend/core/src/main/java/com/infinevo/core/tenant/service/EntitlementService.java` | `[NEW]` Interface checking tenant module entitlement & building dynamic navigation feed |
| ServiceImpl | `code/backend/core/src/main/java/com/infinevo/core/tenant/service/impl/EntitlementServiceImpl.java` | `[NEW]` Implementation querying subscription modules and constructing entitlement feeds |
| Controller | `code/backend/core/src/main/java/com/infinevo/core/tenant/controller/OrganisationController.java` | `[NEW]` REST controller for organisation creation (`POST /api/v1/organisations`) |
| Controller | `code/backend/core/src/main/java/com/infinevo/core/tenant/controller/EntitlementController.java` | `[NEW]` REST controller exposing entitlement & navigation feed (`GET /api/v1/tenant/entitlements`) |
| Controller (Test/Sample) | `code/backend/core/src/main/java/com/infinevo/core/tenant/controller/HrmsSampleController.java` | `[NEW]` Sample HRMS endpoint annotated with `@RequiresModule(ModuleCode.HRMS)` for verification |
| Controller (Test/Sample) | `code/backend/core/src/main/java/com/infinevo/core/tenant/controller/PayrollSampleController.java` | `[NEW]` Sample Payroll endpoint annotated with `@RequiresModule(ModuleCode.PAYROLL)` for verification |

**API Contract**

| Method | Path | Request Body | Response | Auth |
|---|---|---|---|---|
| `POST` | `/api/v1/organisations` | `CreateOrganisationRequest` (`name`, `modules`) | `201 Created` (`OrganisationResponse`) | Bearer JWT |
| `GET` | `/api/v1/tenant/entitlements` | None | `200 OK` (`TenantEntitlementResponse`) | Bearer JWT + TenantContext |
| `GET` | `/api/v1/hrms/sample` | None | `200 OK` (HRMS data) or `403 Forbidden` | Bearer JWT + TenantContext |
| `GET` | `/api/v1/payroll/sample` | None | `200 OK` (Payroll data) or `403 Forbidden` | Bearer JWT + TenantContext |

---

## 5. Frontend changes

*(Backend work item — frontend consumption implemented in `W-45` Frontend Shell)*

| File | Change |
|---|---|
| `code/frontend/src/api/entitlements.ts` | `[NEW]` API client method invoking `GET /api/v1/tenant/entitlements` |
| `code/frontend/src/components/Navigation.tsx` | `[NEW]` Renders sidebar navigation items filtered by `TenantEntitlementResponse.modules` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/` | `ShellLayout` | EntitlementGuard (filters routes based on `entitlements.modules`) |

---

## 6. Database changes

> Flyway only. See `CONVENTIONS.md` rule 4 & rule 7.

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V004__subscription.sql` | `core.subscription`, `core.subscription_module` | yes (`tenant_id`) | yes |

**Schema Definition (`core.subscription` & `core.subscription_module`)**

```sql
CREATE TABLE core.subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_subscription_tenant_id ON core.subscription(tenant_id);

ALTER TABLE core.subscription ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.subscription
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );

CREATE TABLE core.subscription_module (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    subscription_id UUID NOT NULL REFERENCES core.subscription(id) ON DELETE CASCADE,
    module_code VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_subscription_module UNIQUE (subscription_id, module_code)
);

CREATE INDEX idx_subscription_module_tenant_id ON core.subscription_module(tenant_id);
CREATE INDEX idx_subscription_module_lookup ON core.subscription_module(tenant_id, module_code, status);

ALTER TABLE core.subscription_module ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.subscription_module
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );
```

- [x] `tenant_id` present on every new table (`CONVENTIONS.md` rule 7)
- [x] Index on `tenant_id` plus lookup columns (`DEBT-018`)
- [x] RLS 3-branch `CASE` isolation policy applied (`D-56`)
- [x] Expand / contract sequencing — no destructive step

---

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `EntitlementServiceImplTest.java` | Tests active module checking and dynamic navigation menu filtering |
| Unit | `EntitlementEnforcementFilterTest.java` | Verifies HTTP 403 rejection when accessing un-subscribed module endpoints |
| Unit | `TenantServiceImplTest.java` | Tests self-serve organization creation, subscription creation, and module selection |
| Integration | `EntitlementEnforcementIT.java` | Spring Boot Integration Test verifying: <br>1. Payroll-only tenant accessing `/api/v1/hrms/sample` receives `403 Forbidden` <br>2. Payroll-only tenant fetching `/api/v1/tenant/entitlements` receives `modules: ["PAYROLL"]` with HRMS menus excluded <br>3. HRMS-only tenant accessing `/api/v1/hrms/sample` receives `200 OK` <br>4. Multi-module tenant accesses both HRMS & Payroll endpoints with `200 OK` |

---

## 8. Verification

How to prove it works:

```bash
# Execute unit and integration tests for Subscription and Entitlement module
./mvnw clean verify -Dtest=EntitlementServiceImplTest,EntitlementEnforcementFilterTest,TenantServiceImplTest,EntitlementEnforcementIT
```

| Check | Expected | Result |
|---|---|---|
| Organisation Creation Flow | `POST /api/v1/organisations` creates tenant, subscription, and user-tenant mapping | Pending |
| API Entitlement Enforcement | Payroll-only tenant receives `HTTP 403` on HRMS endpoint (`/api/v1/hrms/sample`) | Pending |
| Navigation Entitlement Feed | `GET /api/v1/tenant/entitlements` returns active modules and omits un-subscribed menus | Pending |
| Dual Module Access | Tenant with both HRMS & PAYROLL gets `HTTP 200` on both module endpoints | Pending |
| Full Suite & CI Lint Compliance | `./mvnw clean verify` passes and zero `spring.flyway` occurrences outside `migration` | Pending |

---

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Performance bottleneck from DB lookup on every request in `EntitlementEnforcementFilter` | Low | Cache tenant active modules in high-performance request cache or memory store |
| Unannotated endpoint default behavior | Medium | Default `EntitlementEnforcementFilter` to require entitlement check on any `/api/v1/hrms/**` or `/api/v1/payroll/**` path prefix if annotation is omitted |

---

## 10. Rollback

If issues arise during deployment:
1. Revert Flyway migration `V004__subscription.sql` if required.
2. In emergency mode, disable `EntitlementEnforcementFilter` via Spring property `infinevo.security.entitlement.enabled=false`.
