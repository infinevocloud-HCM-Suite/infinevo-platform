# Feature: Subscription and module selection

| Field | Value |
|---|---|
| **Feature ID** | `W-12.1` · from ticket #13 · `PLAT-01` |
| **Promoted to** | `docs/target-state/features/W-12-1-subscription.md` on branch `W-12-1-subscription` — **`W-12-1` with hyphens**, never `W-12.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-10` |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 11, §6 decisions 4 and 7 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 3 scripts — `subscription`, `subscription_module`, and an expand-only `ALTER TABLE core.tenant` (§6 decision 7) — aggregate exception | 1 — exception granted 2026-09-22, widened 2026-09-25 to the tenant ALTER |
| Externally testable behaviour | a tenant is created holding a named set of modules, and that set is readable | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

**Nothing in either product knows what a customer bought.**

- Payroll's `Organization` carries `isOrgActive`, `isDeleted` and `hasRunPayroll` — `legacy/Payroll-Bend-SBoot/.../entity/organization/Organization.java:59,81-85`
- There is **no plan, no subscription, no licence and no module field anywhere**. The evidence pass looked and found none
- Every tenant is implicitly full-feature, because there was only ever one product per installation

That is the whole reason `subscription` and `subscription_module` are marked **New** in
`02-data-model.md:122`. This is not a port. There is no source.

It also explains the note in `infra/docker/seed/01-tenants.sql:16-21`: the two seeded tenants
were meant to differ — Acme holding Payroll alone, Globex holding both — and they do not,
because the table that would record the difference does not exist. Until it does, *"both
tenants are indistinguishable in what they have bought, and an entitlement check has nothing
to fail against."*

`D-12` makes the subscription status the payment seam, so this table is where billing will
eventually attach without the rest of the platform knowing.

## 2. Scope

**In scope**

- `core.subscription` — one per tenant, with the status that is the payment seam
- `core.subscription_module` — one row per granted module
- Tenant creation that takes a module set **and** the tenant's `country_code`, `timezone` and `leave_year_start_month` — three new nullable-with-default columns on `core.tenant`, which today holds `name` only (`code/backend/migration/src/main/resources/db/migration/core/V001__tenant.sql:5-14`)
- Cross-tenant writes by platform staff through `SECURITY DEFINER` functions, never an RLS bypass for `app_user`
- A read API returning the tenant's modules, which `W-12.2` enforces and `W-12.3` renders
- Making the two seeded dev tenants actually differ

**Out of scope**

- **Enforcement** — `W-12.2`. This ticket records what was bought and refuses nothing
- **Navigation** — `W-12.3`
- Billing, invoicing, payment providers. The status column is a seam, not an integration
- **Trials entirely** — decision 2 settled that there is no trial concept. Also discounts and pricing
- The setup checklist — `W-24.1`, which reads the module set from here

## 3. Flow

```
[platform admin] --> [TenantController] --> [TenantService]
   --> core.provision_tenant(...)  SECURITY DEFINER, owner migration_user
   --> [core.tenant] + [core.subscription] + [core.subscription_module]

[platform admin] --> [SubscriptionController] --> [SubscriptionService]
   --> core.set_subscription_modules(...) / core.set_subscription_status(...)
   --> PermissionCache.bumpVersion(tenantId)

[W-12.2, later] --> [EntitlementSource.modulesOf(tenantId)] --> Set<PlatformModule>
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../subscription/SubscriptionController.java` | new |
| Controller | `core/.../tenant/TenantController.java` | new — creation now takes a module set |
| Service | `core/.../subscription/SubscriptionService.java` | new — after any module or status change calls `PermissionCache.bumpVersion(tenantId)` (`code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionCache.java:132-143`), so every replica's cached module set and action set is invalidated together |
| Service | `core/.../subscription/EntitlementReadService.java` | new — implements `shared`'s `EntitlementSource` port (`W-12.2`); `Set<PlatformModule> modulesOf(UUID tenantId)` returns the non-revoked modules of an `active` or `past_due` subscription |
| Entity | `core/.../subscription/Subscription.java`, `SubscriptionModule.java` | new, each `@Table(schema="core")` |
| Repository | two | new |
| Enumeration | `core/.../subscription/SubscriptionStatus.java` | new. `PlatformModule` is **not** here — it lives in `shared` (`W-12.2`), because the aspect that reads it may not depend on `core` |

`PlatformModule` has exactly two values — `HRMS` and `PAYROLL`. `core` is not a module;
every tenant has all twenty-one core capabilities (`01-platform-shape.md:57`).

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/tenants` | name, country_code, timezone, leave_year_start_month, modules | `201` | `@RequiresAction("core.tenant.provision")` |
| GET | `/api/v1/tenants/{id}/subscription` | — | status, modules, dates | `@RequiresAction("core.tenant.read")`, tenant bound |
| PUT | `/api/v1/tenants/{id}/subscription/modules` | modules | `200` | `@RequiresAction("core.tenant.provision")` |
| PUT | `/api/v1/tenants/{id}/subscription/status` | status | `200` | `@RequiresAction("core.tenant.provision")` |

Request fields on `POST`: `name` (required) · `country_code` ISO 3166-1 alpha-2, `CHAR(2)` ·
`timezone` an IANA zone name, `varchar(64)`, rejected unless `ZoneId.of()` accepts it ·
`leave_year_start_month` `smallint` 1–12. The three optional fields fall back to the column
defaults in §6.

**The gate is the action, not the realm role.** `core.tenant.provision` is catalogued as
"platform staff only; never granted to a customer role" (`code/backend/migration/src/main/resources/db/migration/reference/V020__action.sql:43-44`)
and the seeded `tenant-admin` role excludes it (`code/backend/migration/src/main/resources/db/migration/core/V022__role_action.sql:47,91`). The
Keycloak realm role gates nothing (`W-11-1-role-catalogue.md:61,229`); a `hasRole("platform-admin")`
check here would be a second, unchecked permission system.

**Cross-tenant writes go through `SECURITY DEFINER` functions (§6 decision 4).** Platform
staff act on a tenant that is not the bound one, and `app_user` is under RLS on every table.
The migration therefore adds three functions owned by `migration_user`, in the same form as
`core.seed_system_roles` (`code/backend/migration/src/main/resources/db/migration/core/V022__role_action.sql:65-70`) and `core.get_user_tenants`
(`code/backend/migration/src/main/resources/db/migration/core/V002__user_tenant.sql:30-40`):

| Function | Writes | Grant |
|---|---|---|
| `core.provision_tenant(p_name, p_country_code, p_timezone, p_leave_year_start_month, p_modules text[])` → `tenant_id` | `core.tenant`, `core.subscription`, `core.subscription_module` | `REVOKE EXECUTE FROM PUBLIC; GRANT EXECUTE TO app_user` |
| `core.set_subscription_modules(p_tenant_id, p_modules text[])` | `core.subscription_module` — grants new, sets `revoked_on` on dropped, re-grants a revoked one in place | same |
| `core.set_subscription_status(p_tenant_id, p_status)` | `core.subscription.status` | same |

Each is `LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp` with every
name schema-qualified. `app_user` keeps no direct `INSERT`/`UPDATE` on rows of another tenant;
the only door is the function, and the Java service calls it only after `@RequiresAction`
has passed. RLS stays absolute for the application connection.

Adding a module is an upgrade — the switch `CLAUDE.md` promises instead of a re-onboarding.
**Removing** one is not symmetrical and is decision 1.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__subscription.sql` | `core.subscription` | yes | additive |
| `core/V0NN__subscription_module.sql` | `core.subscription_module` | yes | additive |
| `core/V0NN__tenant_locale_columns.sql` | `core.tenant` — `ALTER TABLE ADD COLUMN` ×3, plus the three `SECURITY DEFINER` functions above | yes (existing RLS, `V001:24-33`) | expand only |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

`core.tenant` gains (`§6 decision 7`; `core.tenant` is built, so this is the expand half only,
`migration/README.md:149`): `country_code char(2) NULL DEFAULT 'IN'` ·
`timezone varchar(64) NULL DEFAULT 'Asia/Kolkata'` ·
`leave_year_start_month smallint NULL DEFAULT 1 CHECK (leave_year_start_month BETWEEN 1 AND 12)`.
Nullable with a default so existing rows and the dev seed need no backfill; a `NOT NULL` is a
later contract step. The `leave_year_start_month` default is provisional — §7 question 1 of
`12-core-contracts.md` is open; the column, not the default, is what this ticket commits to.

`subscription`: `id uuid` · `tenant_id uuid NOT NULL` · `status varchar(16) NOT NULL` —
**active, past due, suspended, cancelled** · `started_on date NOT NULL` ·
`current_period_end date NULL` · `external_ref varchar(128) NULL` — the billing seam, unused
for now · four audit columns.

`subscription_module`: `id uuid` · `tenant_id uuid NOT NULL` ·
`subscription_id uuid NOT NULL REFERENCES core.subscription(id)` ·
`module varchar(16) NOT NULL` · `granted_on date NOT NULL` · `revoked_on date NULL` ·
four audit columns.

- [x] `tenant_id` on both, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id)` unique on subscription, `(tenant_id, module)` unique where `revoked_on IS NULL`
- [x] **Money columns — none.** No price, no amount. `D-12` puts billing behind the seam, not in this table
- [x] Expand / contract — new tables, plus nullable-with-default columns on `core.tenant`; nothing dropped or renamed

**`revoked_on` rather than deletion.** A module a tenant used to hold explains data that still
exists, and a payslip from a month the tenant had Payroll must stay explainable after they
downgrade.

RLS and the `tenant_isolation` policy in the exact `CASE` form in each script —
`migration/README.md:76-123`.

**The seed changes too.** `infra/docker/seed/01-tenants.sql` gets a companion granting Acme
`PAYROLL` only and Globex both, which is what its own comment at `:16-21` says is missing.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../subscription/SubscriptionServiceTest.java` | a tenant cannot hold the same module twice; granting a revoked module re-grants rather than duplicating |
| Integration | `core/.../subscription/SubscriptionRlsIT.java` | tenant A cannot read tenant B's subscription as `app_user` |
| Integration | `core/.../subscription/TenantCreationIT.java` | creating a tenant with `[PAYROLL]` yields exactly one module row, and `EntitlementReadService.modulesOf` omits `HRMS`; the created row carries the given `country_code`, `timezone` and `leave_year_start_month`, and the defaults when omitted; `timezone=Mars/Olympus` and `leave_year_start_month=13` are `400 VALIDATION_FAILED` |
| Unit | `core/.../subscription/SubscriptionServiceTest.java` | every module or status change calls `PermissionCache.bumpVersion` with the target tenant's id exactly once; a no-op change (same set) does not |
| Integration | `core/.../subscription/TenantProvisionGuardIT.java` | a user without `core.tenant.provision` gets `403 FORBIDDEN` on `POST /tenants` and both `PUT`s, whatever realm role the token carries; `core.tenant.read` alone reaches only the `GET` |
| Integration | `core/.../subscription/CrossTenantWriteIT.java` | as `app_user` bound to tenant A, a direct `INSERT` into tenant B's `core.subscription_module` is refused by RLS, and `core.set_subscription_modules(B, ...)` succeeds — the function is the only door |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
bash infra/docker/seed/seed.sh
for t in subscription subscription_module; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT t.name, m.module FROM core.tenant t
     JOIN core.subscription s ON s.tenant_id = t.tenant_id
     JOIN core.subscription_module m ON m.subscription_id = s.id
    ORDER BY 1,2;"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on both | `t` twice |
| Seed | Acme → `PAYROLL` only; Globex → `HRMS` and `PAYROLL` |
| Suite | green, no skips |

The seed check is the one that closes the gap `01-tenants.sql:16-21` names.

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Enforcement is added here because the data is here | medium | Named in **Out of scope**; `W-12.2` owns refusal, and a half-enforced entitlement is worse than none |
| Price or plan fields creep in | medium | No money column; `external_ref` is the seam, per `D-12` |
| A module is revoked and dependent data becomes unreadable | medium | `revoked_on`, never deletion; the data stays, the access stops |
| `core` is modelled as a module | low | The enumeration has two values, asserted by the unit test |
| Both dev tenants stay identical because the seed is forgotten | medium | Verification asserts the asymmetry, not just the tables |

## 10. Rollback

Nothing is deployed. All three scripts are additive and forward-only —
`migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables, each in its own script |
| Flyway only, `ddl-auto` nowhere | three scripts; none added |
| `Money`/`BigDecimal` for money | creates no money column, deliberately |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new tables; `core.tenant` gains nullable-with-default columns only |
| Cross-tenant writes (`12-core-contracts.md` §6 decision 4) | `SECURITY DEFINER` functions owned by `migration_user`; no bypass role for `app_user` |
| No module references another module | `core` only |

## 12. Gap inventory

| ID | Decision |
|---|---|
| No subscription concept exists (`Organization.java:59,81-85`) | **Fixed.** This is the ticket that creates it |
| Two dev tenants indistinguishable (`01-tenants.sql:16-21`) | **Fixed.** The seed makes them differ |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | What happens when a module is revoked? | **Read-only for the statutory retention window, no new records** — settled 2026-09-22. `revoked_on` is what marks it, and `W-12.2` must implement a read-only mode rather than a plain refusal |
| 2 | Trials | **No trial concept at all** — settled 2026-09-22, against my recommendation of keeping the status value. Every tenant is active or it is not; adding trials later is a migration |
| 3 | Who may create a tenant? | **`platform-admin` only** — settled 2026-09-23. No self-service signup until there is billing behind the seam. *Corrected 2026-09-25:* enforced as the action `core.tenant.provision`, which only the seeded `platform-admin` role holds (`V022__role_action.sql:86-91`), not as a realm-role check |
