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

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 2 scripts, one table each — aggregate exception | 1 — exception granted 2026-09-22 |
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
- Tenant creation that takes a module set
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
   --> [core.tenant] + [core.subscription] + [core.subscription_module]

[W-12.2, later] --> [EntitlementService.holds(tenant, module)] --> boolean
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../subscription/SubscriptionController.java` | new |
| Controller | `core/.../tenant/TenantController.java` | new — creation now takes a module set |
| Service | `core/.../subscription/SubscriptionService.java` | new |
| Service | `core/.../subscription/EntitlementReadService.java` | new — the seam `W-12.2` caches and enforces |
| Entity | `core/.../subscription/Subscription.java`, `SubscriptionModule.java` | new, each `@Table(schema="core")` |
| Repository | two | new |
| Enumeration | `core/.../subscription/PlatformModule.java`, `SubscriptionStatus.java` | new |

`PlatformModule` has exactly two values — `HRMS` and `PAYROLL`. `core` is not a module;
every tenant has all twenty-one core capabilities (`01-platform-shape.md:57`).

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/tenants` | name, country, modules | `201` | Bearer, `platform-admin` |
| GET | `/api/v1/tenants/{id}/subscription` | — | status, modules, dates | Bearer, tenant bound |
| PUT | `/api/v1/tenants/{id}/subscription/modules` | modules | `200` | Bearer, `platform-admin` |
| PUT | `/api/v1/tenants/{id}/subscription/status` | status | `200` | Bearer, `platform-admin` |

Adding a module is an upgrade — the switch `CLAUDE.md` promises instead of a re-onboarding.
**Removing** one is not symmetrical and is decision 1.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__subscription.sql` | `core.subscription` | yes | additive |
| `core/V0NN__subscription_module.sql` | `core.subscription_module` | yes | additive |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

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
- [x] Expand / contract — new tables only

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
| Integration | `core/.../subscription/TenantCreationIT.java` | creating a tenant with `[PAYROLL]` yields exactly one module row, and `EntitlementReadService` reports `HRMS` not held |

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

Nothing is deployed. Both scripts are additive and forward-only —
`migration/README.md:135-143`.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables, each in its own script |
| Flyway only, `ddl-auto` nowhere | two scripts; none added |
| `Money`/`BigDecimal` for money | creates no money column, deliberately |
| Index on `tenant_id` plus lookup columns | two indexes, `tenant_id` leading |
| Expand / contract | new tables only |
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
| 3 | Who may create a tenant? | **`platform-admin` only** — settled 2026-09-23. No self-service signup until there is billing behind the seam |
