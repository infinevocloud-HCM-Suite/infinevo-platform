# Feature: Suspended customers can read everything and change nothing — backend

| Field | Value |
|---|---|
| **Feature ID** | `W-85.1` · `CORE-SECURITY` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/backend/shared`, `code/backend/core`, one annotation each in `hrms` and `payroll` where §4.3 finds a read mapped as a write |
| **Related gaps** | founder 2026-11-13 roadmap, "Priority 1 — customer lifecycle" |
| **Status** | Ready — approved 2026-11-19. Not built. |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | nothing |

## Founder decision (2026-11-19)

| Customer status | Read and export | Change |
|---|---|---|
| `ACTIVE`, `PAST_DUE` | Yes | Yes |
| `SUSPENDED`, `CANCELLED` | **Yes — in Core, HRMS and Payroll** | **No — anywhere** |

A suspended or cancelled customer keeps its records for statutory retention: employees,
attendance, leave, payslips. It can export them. It cannot change anything.

## Split (founder, 2026-11-19)

| Ticket | Holds | Blocked by |
|---|---|---|
| `W-85.1` | Backend: the write guard, cancelled treated as suspended, reads let through, status in the navigation feed | nothing |
| `W-85.2` | Frontend: the banner and view-only mode in place of the full-screen Suspended page | `W-85.1` |

## Size

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | `shared` and `core` | 1 | **Over by one** — the check lives in `shared`, the status comes from `core` |
| Flyway migration | none | 1 | Pass |
| Testable behaviour | a suspended tenant reads everywhere and writes nowhere | 1 | Pass |
| Frontend area | none | 1 | Pass |

Size **M**.

**Until `W-85.2` lands:** the web app still shows the old full-screen Suspended page after the
first refused save. Reads work again after a reload. That is no worse than today and ends
with `W-85.2`.

---

## 1. Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Core has no suspension check, so a suspended customer can still create employees, invite users and edit settings. | No Core controller carries `@RequiresModule`; the only check is `EntitlementService.require` |
| 2 | HRMS and Payroll refuse **every** request when suspended, reads included, so the customer cannot see attendance or payslips. | `code/backend/shared/src/main/java/com/infinevo/shared/entitlement/EntitlementService.java:113-116` |
| 3 | A `CANCELLED` customer is not treated as suspended. It gets an empty snapshot with `suspended = false`, so HRMS and Payroll answer `MODULE_NOT_ENTITLED` and Core is open. | `code/backend/core/src/main/java/com/infinevo/core/subscription/EntitlementReadService.java:49-56` |
| 4 | When suspended, the snapshot drops the module list, so nothing can tell which modules the customer may still read. | `EntitlementReadService.java:50-52` |
| 6 | No API tells the frontend the subscription status. | `code/backend/core/src/main/java/com/infinevo/core/navigation/NavigationResponse.java:20-27` |

## 2. Scope

**In scope**

- One central write guard in `shared` that refuses every changing request from a suspended or cancelled tenant.
- The snapshot treats `CANCELLED` like `SUSPENDED` and keeps the module list in both cases.
- The HRMS and Payroll check lets reads through when suspended.
- An explicit, annotated list of writes that stay open (§4.2).
- The navigation feed returns the subscription status.

**Out of scope**

- The banner and view-only mode in the web app — `W-85.2`.
- Moving overdue customers to `PAST_DUE` — `W-86`. `PAST_DUE` blocks nothing.
- Payment gateway — `W-94`.

## 3. Flow

```
request --> TenantContextFilter --> TenantSuspensionAspect --> RequiresModuleAspect --> RequiresActionAspect --> controller
                                          |
              GET / HEAD / OPTIONS -------+--> pass
              no tenant bound ------------+--> pass
              platform tenant or ActingAs +--> pass
              @AllowedWhenSuspended ------+--> pass
              snapshot.suspended() -------+--> 403 TENANT_SUSPENDED
              otherwise ------------------+--> pass
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Aspect | `shared/.../tenant/TenantSuspensionAspect.java` | **New.** Runs on every handler with `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` or a `@RequestMapping` whose method is not safe. Ordered before `RequiresModuleAspect`. Applies the rules in §3. Switch: `infinevo.security.suspension-guard.enabled`, default `true`. |
| Annotation | `shared/.../tenant/AllowedWhenSuspended.java` | **New.** Marks a write that stays open. Method level only. |
| Snapshot | `shared/.../entitlement/EntitlementSnapshot.java` | No new field. `suspended` now means "suspended or cancelled", and the module sets are filled in that case too. Update the Javadoc. |
| Check | `shared/.../entitlement/EntitlementService.java` (`require`, line 113) | When `snapshot.suspended()` and the request is read-only, fall through to the normal module check instead of refusing. A write keeps `403 TENANT_SUSPENDED`. |
| Source | `core/.../subscription/EntitlementReadService.java` (`snapshotOf`, lines 49-56) | `suspended = status is SUSPENDED or CANCELLED`. Load the modules before returning, in every status. |
| Feed | `core/.../navigation/NavigationResponse.java`, `NavigationService.java` | Add `subscriptionStatus` (`ACTIVE`, `PAST_DUE`, `SUSPENDED`, `CANCELLED`; null when the tenant has no subscription row). Keep the older constructors. |

The aspect reads the snapshot through `EntitlementService`, which already caches it. No new
port is needed. If the snapshot cannot be loaded, a write is refused (fail closed), the same
as the module check does today.

### 4.1 API contract

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/navigation` | — | existing body plus `subscriptionStatus` | signed in |
| any write | any | — | `403` `{ "code": "TENANT_SUSPENDED" }` when the bound tenant is suspended or cancelled | — |

### 4.2 Writes that stay open

| Endpoint | Why | How |
|---|---|---|
| `POST /api/v1/exports` | Export is the retention right itself. | `@AllowedWhenSuspended` on `ExportController.export` (`core/.../report/ExportController.java:94`) |
| `POST /api/v1/notifications/{id}/read` | Reading a notification must not raise an error. | `@AllowedWhenSuspended` on `NotificationController` line 46 |
| `POST /api/v1/invitations/accept`, `/decline` | Public, token in the body, no tenant bound when the aspect runs. | Pass by the "no tenant bound" rule; annotate as well so the rule is visible |
| Everything under `/api/v1/tenants/**`, including `/{id}/subscription/status`, `/plan`, `/modules` | Platform staff must be able to lift the suspension. | Pass by the "platform tenant" rule (`PlatformTenant.DEFAULT_PLATFORM_TENANT_ID`) |
| Any request inside an impersonation session | Support staff fix the account. | Pass when `ActingAs.isActing()` |
| Login, logout, token refresh | Keycloak, not this API. | Never reach the aspect |

Nothing else is exempt. `PUT /api/v1/tenants/current/profile` is blocked for a suspended
tenant admin.

### 4.3 Reads mapped as writes

Some read endpoints use `POST` (a search or a report run with a body). The implementer runs

```bash
grep -rn "@PostMapping" code/backend/{core,hrms,payroll}/src/main/java
```

and puts `@AllowedWhenSuspended` on each one that changes no data. The list goes in the
pull request description. This is the only reason `hrms` and `payroll` are touched.

## 5. Frontend changes

None. **Routes added:** none.

## 6. Database changes

None. `core.subscription.status` already exists (`V034__subscription.sql`).

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `shared/.../tenant/TenantSuspensionAspectTest.java` | GET passes; POST, PUT, PATCH, DELETE refused when suspended; no tenant bound passes; platform tenant passes; `ActingAs` passes; `@AllowedWhenSuspended` passes; snapshot load failure refuses |
| Unit | `shared/.../entitlement/EntitlementServiceTest.java` (extend) | Suspended + read + module held passes; suspended + read + module not held is `MODULE_NOT_ENTITLED`; suspended + write is `TENANT_SUSPENDED` |
| Unit | `core/.../subscription/EntitlementReadServiceTest.java` (extend) | `CANCELLED` gives `suspended = true`; modules are loaded when suspended |
| Integration | `app/src/test/.../TenantSuspensionIT.java` | The verification table below, against a real suspended and a real cancelled tenant |

## 8. Verification

```bash
cd code/backend && ./mvnw -pl shared,core,app verify
```

| Check (tenant `SUSPENDED` unless said) | Expected | Result |
|---|---|---|
| `GET /api/v1/employees` | 200 | not run |
| `POST /api/v1/employees` | 403 `TENANT_SUSPENDED` | not run |
| `PUT /api/v1/tenants/current/profile` | 403 `TENANT_SUSPENDED` | not run |
| `POST /api/v1/exports` | 201 with a download link | not run |
| `POST /api/v1/notifications/{id}/read` | 2xx | not run |
| `POST /api/v1/invitations/accept` with a valid token | not `TENANT_SUSPENDED` | not run |
| A HRMS read (`GET /api/v1/hrms/attendance/today`) | 200 | not run |
| A HRMS write (clock in) | 403 `TENANT_SUSPENDED` | not run |
| A Payroll read (payslip list) | 200 | not run |
| Same reads and writes on a `CANCELLED` tenant | same results | not run |
| Platform admin `PUT /api/v1/tenants/{id}/subscription/status` to `ACTIVE` | 200, and the tenant can write again | not run |
| Same write inside an impersonation session | passes | not run |
| `PAST_DUE` tenant `POST /api/v1/employees` | 201 | not run |
| `GET /api/v1/navigation` | body carries `subscriptionStatus: "SUSPENDED"` | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A read mapped as `POST` is missed and breaks a screen for suspended customers | Medium | The grep in §4.3; the customer still has export; adding the annotation is a one-line fix |
| A write endpoint with no mapping annotation the aspect knows | Low | The aspect also matches `@RequestMapping` with an unsafe method; the unit test covers each mapping kind |
| The status is cached, so a newly suspended tenant can write for a short time | Low | Same cache and eviction as the module check today (`SubscriptionServiceImpl` evicts on a status change) — the implementer confirms the eviction covers this path |

## 10. Rollback

1. Set `infinevo.security.suspension-guard.enabled=false` — Core writes are open again for suspended tenants, as today.
2. The read-through in `EntitlementService` has no switch. To restore the old full block, revert the commit.
3. No data is changed, so nothing needs restoring.
