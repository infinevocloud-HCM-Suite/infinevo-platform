# Feature: Entitlement enforcement

| Field | Value |
|---|---|
| **Feature ID** | `W-12.2` · from ticket #13 · `PLAT-01` |
| **Promoted to** | `docs/target-state/features/W-12-2-entitlement-enforcement.md` on branch `W-12-2-entitlement-enforcement` — **`W-12-2` with hyphens**, never `W-12.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/shared` |
| **Related gaps** | none directly — the capability does not exist to be defective |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-12.1` (a module set), `W-11.2` (the cache this reuses) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `shared` | 1 |
| Flyway migration | **none** | 1 |
| Externally testable behaviour | a tenant without a module gets `403` on that module's endpoints | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

There is nothing to fix, and that is the problem: no endpoint in either product asks what the
customer bought, because no product ever had more than one module.

`09-build-order.md:185` sets the bar and names the trap in the same breath:

> *"Done when: a Payroll-only tenant gets a 403 on an HRMS endpoint **and** no HRMS menu.
> Watch: both halves, always. A hidden menu over a live endpoint is a security bug."*

This ticket is the first half. `W-12.3` is the second, and building only one of them is the
failure the build order is warning about.

The distinction against `W-11.2` matters and is easy to blur. Two different refusals:

| Question | Answered by | Refusal |
|---|---|---|
| Did the customer buy this module? | `W-12.2`, this ticket | `403`, module not held |
| Is this user allowed to do this? | `W-11.2` | `403`, action not held |

A tenant administrator holding every action still cannot reach an HRMS endpoint if the
tenant bought only Payroll.

## 2. Scope

**In scope**

- `@RequiresModule(HRMS)` at class or method level on module controllers, with a **read-only mode** for a revoked module — settled by `W-12.1` decision 1 on 2026-09-22: a tenant that drops a module keeps read access to its historical data for the retention window and may create nothing new
- An entitlement check reading `W-12.1`'s module set for the bound tenant
- Caching it beside the permission set, in the same Redis, invalidated the same way
- A distinct, machine-readable error so the frontend can tell the two refusals apart
- Applying it to every existing `hrms` and `payroll` controller

**Out of scope**

- The subscription tables — `W-12.1`
- Navigation — `W-12.3`
- Per-action permissions — `W-11.2`
- Suspending a tenant for non-payment. The status exists; acting on it is decision 2

## 3. Flow

```
[request] --> TenantContextFilter (W-08)
   --> [@RequiresModule aspect] --> [EntitlementService]
        --> Redis hit? --> Set<module>
        --> miss? --> W-12.1 EntitlementReadService --> Redis
   --> module held? proceed : 403 MODULE_NOT_ENTITLED
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Annotation | `shared/.../entitlement/RequiresModule.java` | new |
| Interceptor | `shared/.../entitlement/RequiresModuleAspect.java` | new |
| Service | `shared/.../entitlement/EntitlementService.java` | new |
| Cache | reuses `shared/.../authz/PermissionCache.java` | change — a second key namespace, same version scheme |
| Error | `shared/.../error/ApiError.java` | change — add `MODULE_NOT_ENTITLED` |

The error enumeration already exists and is already used by `TenantContextFilter` —
`code/backend/shared/src/main/java/com/infinevo/shared/error/ApiError.java`, referenced at
`TenantContextFilter.java:59,81,95`. Adding a value is additive.

**The annotation goes on the module controllers, not on `core`.** Every tenant holds all
core capabilities, so annotating `core` would be wrong and would break a Payroll-only tenant's
leave screens.

**API contract**

No new endpoint. Existing and future `hrms` and `payroll` endpoints gain a refusal.

## 5. Frontend changes

None here. The frontend's reaction to `MODULE_NOT_ENTITLED` belongs to `W-12.3`.

## 6. Database changes

**None. This ticket creates no table and ships no migration.**

- [x] `tenant_id` on every new table — creates none
- [x] Index on `tenant_id` plus lookup columns — adds none
- [x] Money columns — none
- [x] Expand / contract — no schema change

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `shared/.../entitlement/EntitlementServiceTest.java` | held module passes; unheld returns `MODULE_NOT_ENTITLED`; an unbound tenant fails closed |
| Unit | `shared/.../entitlement/RefusalDistinctionTest.java` | a module refusal and a permission refusal carry different codes and are not interchangeable |
| Integration | `shared/.../entitlement/EntitlementIT.java` | Acme (Payroll only) gets `403` on an `hrms` endpoint; Globex gets `200` on the same one |
| Integration | `shared/.../entitlement/EntitlementCoverageIT.java` | **every** `@RestController` in `hrms` and `payroll` carries `@RequiresModule` |

`EntitlementCoverageIT` is the test that keeps this true as the platform grows. A module
endpoint added in six months without the annotation is exactly the live endpoint behind a
hidden menu that the build order calls a security bug — so the test fails the build rather
than waiting for someone to notice.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d
bash infra/docker/seed/seed.sh

for u in admin.acme admin.globex; do
  TOKEN=$(curl -s -d client_id=infinevo-web -d username=$u -d password=local_dev_pw \
    -d grant_type=password \
    http://localhost:8081/realms/infinevo/protocol/openid-connect/token | jq -r .access_token)
  echo -n "$u on an hrms endpoint: "
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/hrms/ping \
    -o /tmp/body -w '%{http_code} '; jq -r .code /tmp/body
done

cd code/backend && mvn -q -pl shared -Dit.test=EntitlementCoverageIT verify
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| `admin.acme` on an `hrms` endpoint | `403` with code `MODULE_NOT_ENTITLED` |
| `admin.globex` on the same endpoint | `200` |
| Coverage test | green — no unannotated module controller |
| Suite | green, no skips |

The two users differ only in what their tenant bought, which is what `W-12.1`'s seed change
made possible.

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Only the menu is hidden and the endpoint stays live | **high — the named security bug** | `EntitlementIT` asserts the endpoint; `W-12.3` is a separate ticket so neither half can be mistaken for both |
| A new module controller ships without the annotation | **high over time** | `EntitlementCoverageIT` fails the build |
| Module and permission refusals are conflated, so the UI says the wrong thing | medium | Distinct error codes, asserted by `RefusalDistinctionTest` |
| Entitlement fails open when Redis is down | medium, severe | Fails closed, like `W-11.2` |
| `core` endpoints get annotated and a Payroll-only tenant loses leave | medium | The annotation is absent from `core` by design; the coverage test checks `hrms` and `payroll` only |

## 10. Rollback

Nothing is deployed and no schema changes. Removing the annotations withdraws enforcement.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | **creates no table** |
| Flyway only, `ddl-auto` nowhere | **ships no migration**; none added |
| `Money`/`BigDecimal` for money | holds no money |
| Index on `tenant_id` plus lookup columns | adds none |
| Expand / contract | no schema change |
| No module references another module | the aspect lives in `shared`; it reads `core` data and annotates module controllers without either module referencing the other |

## 12. Gap inventory

| ID | Decision |
|---|---|
| No entitlement concept in either product | **Fixed.** This ticket is the enforcement half |
| Hard-coded Payroll navigation (`sidebar.js:25-69`) | **Deferred to `W-12.3`**, which owns the menu |

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | What does a suspended subscription do? | **`suspended` blocks every module endpoint, leaving login and billing screens reachable; `past_due` blocks nothing.** Settled 2026-09-23. Locking a customer out over an invoice stays a deliberate act, not something a gate does |
| 2 | Read-only after downgrade? | **Yes** — settled by `W-12.1` decision 1 on 2026-09-22. `@RequiresModule` carries a read-only mode: reads pass, writes return `MODULE_NOT_ENTITLED` |

**Consequence of decision 2:** the annotation takes a mode, the aspect distinguishes safe from
unsafe HTTP methods, and `EntitlementIT` gains a case — a revoked module returning `200` on a
`GET` and `403` on a `POST`.
