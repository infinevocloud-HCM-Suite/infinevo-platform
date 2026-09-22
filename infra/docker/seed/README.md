# Seed data

**Two tenants.** One tenant hides every isolation bug there is: a query that forgot
its tenant filter returns exactly the right rows, and an RLS policy that never matches
looks identical to one that always matches.

## Status

| Part | State |
|---|---|
| Loader | Works — `seed.sh`, idempotent |
| Two tenants | **Seeded.** `core.tenant` exists (`W-07`), so `01-tenants.sql` inserts |
| Different module sets | Pending `W-12` — `core.subscription` does not exist yet |

The module asymmetry is the half still missing. Until `W-12` creates the subscription
table, both tenants are indistinguishable in what they have bought, so an entitlement
check has nothing to fail against. Seed one tenant with everything and entitlement bugs
stay invisible until a real customer buys one module, which is the worst possible time
to find them — that argument still holds, and this is only half-answered.

## Running it

```bash
infra/docker/seed/seed.sh
```

Idempotent — safe to run repeatedly. It is **local only**: it is not a Flyway
migration and must never be reachable from a deployed environment.

## The two tenants

| Tenant | `tenant_id` | Modules, once `W-12` lands |
|---|---|---|
| Acme Manufacturing | `11111111-1111-1111-1111-111111111111` | **Payroll only** — what a Payroll-only customer sees |
| Globex Corporation | `22222222-2222-2222-2222-222222222222` | **HRMS + Payroll** — the combined experience |

The UUIDs are fixed, not generated, so a developer can hardcode one in a request and
still be talking about the same tenant after a restart. They are the same two the
migration module's `TenantIsolationIT` uses.

Their administrators already exist in Keycloak — `admin.acme` and `admin.globex`, both
with password `local_dev_pw`. See `../keycloak/dev-realm.json`.
