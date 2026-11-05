# Feature: Plan, support plan and employee limit on the subscription

| Field | Value |
|---|---|
| **Feature ID** | `W-84.1` · `PLAT-01` |
| **Owner** | claude (`dev-claude-w84`) |
| **Apps touched** | `code/backend/core` (`subscription`, `tenant`), `code/backend/migration`, `code/frontend` (`src/core/admin`) |
| **Related gaps** | none in `GAP_INVENTORY.md`; the frozen system has no plans or limits |
| **Status** | Ready |
| **Blocked by** | nothing. Founder 2026-11-05: `PAST_DUE` keeps blocking nothing, the limit applies the same in every status; prices stay out (`W-94`) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `V188` | 1 |
| Externally testable behaviour | Platform admin sets a tenant's plan, support plan and employee limit, and sees them on the tenant list | 1 |
| Frontend area | `core/admin` | 1 |

`W-84.2` enforces the limit and shows usage inside the tenant. This spec only stores and sets it.

## Decisions (founder, 2026-11-05)

| Question | Answer |
|---|---|
| Plan names | `STARTER`, `GROWTH`, `ENTERPRISE` |
| Support plan names | `STANDARD`, `PRIORITY` (`W-115` reads this for its response timer) |
| Where the limit comes from | The admin types any whole number. The plan is a label for billing; it fixes nothing |
| Existing tenants | No limit until one is set. `NULL` means unlimited |
| `PAST_DUE` | Unchanged. Not a condition anywhere in `W-84` |

## Problem

`core.subscription` carries status, dates and modules only (`code/backend/migration/.../core/V034__subscription.sql:6-17`). There is nowhere to record what a customer is paying for, what support they get, or how many employees they may have. The tenant page shows Modules and Status cards and nothing else about the deal (`code/frontend/src/core/admin/TenantDetail.jsx:207-234`). The tenant list counts user accounts, not employees (`V159__list_tenants_admin_invitation.sql:55-59`).

## Rule

A subscription has three new optional fields: **plan**, **support plan**, **employee limit**. Only platform staff in the Infinevo tenant can set them, through the same guard as status and modules (`SubscriptionController.java:48-69`: `core.tenant.provision` plus `requirePlatformTenant()`). Changing any of them takes effect at once. Lowering the limit below current headcount is allowed: it removes nobody, it only stops additions (`W-84.2`). A limit of `0` is refused; use `NULL` for unlimited. The platform tenant itself has no limit and the card is hidden for it, as the Status card is (`TenantDetail.jsx:224`).

## Flow

```
Platform admin --> TenantDetail "Plan" card --> PUT /api/v1/tenants/{id}/subscription/plan
               --> SubscriptionServiceImpl.updatePlan --> core.set_subscription_plan() --> core.subscription
Platform admin --> TenantList --> GET /api/v1/tenants --> core.list_tenants() (employee_count, employee_limit)
```

## Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V188__subscription_plan.sql` | `ALTER TABLE core.subscription ADD COLUMN plan varchar(16) NULL, ADD COLUMN support_plan varchar(16) NULL, ADD COLUMN employee_limit integer NULL`; `CHECK (plan IN ('STARTER','GROWTH','ENTERPRISE'))`, `CHECK (support_plan IN ('STANDARD','PRIORITY'))`, `CHECK (employee_limit IS NULL OR employee_limit > 0)`; new `SECURITY DEFINER` function `core.set_subscription_plan(p_tenant_id uuid, p_plan text, p_support_plan text, p_employee_limit integer)` on the pattern of `core.set_subscription_status` (`V034:245-266`), `REVOKE` from `PUBLIC`, `GRANT` to `app_user`; `DROP` and re-`CREATE` `core.list_tenants()` and `core.list_tenant(uuid)` as `V159:16-120` did, adding `employee_count bigint` (rows in `core.employee` with `deleted = false` and `status <> 'TERMINATED'`) and `employee_limit integer` | existing table, RLS already on | expand only; the previous release ignores the new columns |

- [x] No new table, so no new `tenant_id` or policy. States it, as the rule asks
- [x] No new index: the columns are read with the row, never searched
- [x] No money columns
- [x] Expand only. Nothing dropped, nothing renamed
- [x] Every statement names `core.`

`employee_count` uses the same definition `W-84.2` enforces, so the list and the gate never disagree. Terminated and soft-deleted employees do not count; `SUSPENDED` employees do, because they can be reactivated without a check.

## Backend changes

| Layer | File | Change |
|---|---|---|
| Enumeration | `core/subscription/SubscriptionPlan.java`, `SupportPlan.java` | new. `STARTER, GROWTH, ENTERPRISE`; `STANDARD, PRIORITY` |
| Entity | `core/subscription/Subscription.java` | `plan`, `supportPlan` (`@Enumerated(STRING)`, nullable), `employeeLimit` (`Integer`, nullable) after `externalRef` (`:54-55`) |
| DTO | `core/subscription/PlanUpdateRequest.java` | new. `record(SubscriptionPlan plan, SupportPlan supportPlan, Integer employeeLimit)`; every field optional; `employeeLimit` `null` means unlimited |
| DTO | `core/subscription/SubscriptionResponse.java` | adds `plan`, `supportPlan`, `employeeLimit` (`:8-15`) |
| Service | `core/subscription/SubscriptionService.java`, `SubscriptionServiceImpl.java` | `updatePlan(UUID tenantId, PlanUpdateRequest)`: refuses the platform tenant as `updateModules` does (`:150`), runs inside `asTenant` as `updateStatus` does (`:205`), no-op when nothing changes, else calls `core.set_subscription_plan` through `jdbcTemplate` (`:225-231`), then sets the three fields on the loaded `Subscription` entity so the Hibernate audit listener (`code/backend/shared/src/main/java/com/infinevo/shared/audit/AuditEventListener.java`) records old and new values in `core.audit_log` (`V008`) on flush, with the actor. No cache bump: nothing permission-related changes |
| Controller | `core/subscription/SubscriptionController.java` | `PUT /plan`, `@RequiresAction("core.tenant.provision")`, `requirePlatformTenant()` (`:60-69` pattern). `employeeLimit` of `0` or negative answers `400` through the existing `IllegalArgumentException` handler (`:78`) |
| DTO | `core/tenant/TenantOverview.java` | adds `employee_count` (`long`) and `employee_limit` (`Integer`, nullable) (`:14-24`) |
| Repository | `core/tenant/TenantQueryService.java` | `ROW_MAPPER` reads the two new columns (`:56`) |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `PUT` | `/api/v1/tenants/{id}/subscription/plan` | `{ "plan": "GROWTH", "supportPlan": "PRIORITY", "employeeLimit": 50 }` any field may be `null` | `SubscriptionResponse` with the three new fields | `core.tenant.provision`, platform tenant only |
| `GET` | `/api/v1/tenants/{id}/subscription` | — | now carries `plan`, `supportPlan`, `employeeLimit` | unchanged (`:38-46`) |
| `GET` | `/api/v1/tenants`, `/api/v1/tenants/{id}` | — | `TenantOverview` now carries `employee_count`, `employee_limit` | unchanged |

`GET …/subscription` answers only for the bound tenant (`SubscriptionController.java:41-44`), so a tenant admin reads their own limit through it. That is what `W-84.2` uses.

## Frontend changes

| File | Change |
|---|---|
| `src/core/admin/tenantService.js` | `setPlan(id, { plan, supportPlan, employeeLimit })` → `PUT /v1/tenants/{id}/subscription/plan`, `PLATFORM_ONLY` (`:99-106` pattern) |
| `src/core/admin/TenantDetail.jsx` | New "Plan" card between Modules and Status (`:207-234`), hidden for the platform tenant: `Select` plan (`Starter / Growth / Enterprise / —`), `Select` support plan (`Standard / Priority / —`), `InputNumber` employee limit (min 1, empty means unlimited), one Save button, `aria-label`s `Plan`, `Support plan`, `Employee limit`, id `btn-save-plan`. Shows "Employees: N of M" or "N, no limit" from `employee_count` / `employee_limit`. Saving a limit below `employee_count` asks first: "This tenant has N employees. Nobody is removed; adding or reactivating is blocked until the count falls below M." |
| `src/core/admin/TenantList.jsx` | New column "Employees" after Modules (`:86-94`): `N / M`, or `N` when no limit; red when `N > M` |
| `src/core/admin/platform.js` | Label maps for the two enums, the same way statuses are labelled |

No route added.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/subscription/SubscriptionServiceTest.java` | `updatePlan` no-op when unchanged; calls the function when changed; refuses the platform tenant; refuses limit `0` |
| Integration | `core/subscription/SubscriptionPlanIT.java` | new. `PUT /plan` from the platform tenant writes the three columns and an `audit_log` row naming `employee_limit` in `changed_columns`; from a customer tenant answers `403`; `GET /subscription` echoes them; `core.list_tenants()` returns `employee_count` that excludes a terminated and a soft-deleted employee and includes a suspended one |
| Component | `src/core/admin/TenantDetail.test.jsx` | Plan card hidden for the platform tenant; Save calls `setPlan` with the three values; a limit below the count opens the confirm; blank limit sends `null` |
| Component | `src/core/admin/TenantList.test.jsx` | "Employees" column reads `12 / 50`, `12` with no limit, red at `55 / 50` |

## Verification

```bash
cd code/backend && ./mvnw -B -pl migration,core -am verify
cd code/frontend && npx vitest run src/core/admin && npm run lint && npm run build
```

| Check | Expected |
|---|---|
| `V188` applies on a database at `V187` | Flyway `Successfully applied 1 migration`; `\d core.subscription` shows three new nullable columns and three CHECKs |
| `PUT …/subscription/plan` as platform admin with `{ "employeeLimit": 25 }` | `200`, body carries `employeeLimit: 25`, `plan` and `supportPlan` unchanged |
| Same call as a tenant admin | `403` |
| `{ "employeeLimit": 0 }` | `400` |
| Tenant list after the change | Employees column reads `N / 25` |
| Tenant page, Plan card, set limit below headcount | Confirm dialog, then saved; nothing else on the tenant changes |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `list_tenants` with a subquery per tenant on `core.employee` is slow with many tenants | Low; tens of tenants today | `idx_employee_tenant_status` exists (`Employee.java:87`); the subquery hits it |
| Dropping and recreating `list_tenants` while the old `app` release is running | Low | `V159` did the same; the old mapper ignores extra columns |

## Rollback

Expand only. Leave the columns in place; revert the application. The old release never reads them.
