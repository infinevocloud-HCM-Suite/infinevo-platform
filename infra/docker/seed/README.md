# Seed data

**Two tenants holding different module sets.** Every developer then sees the
difference between a Payroll-only customer and one holding both, every day.

> Seed one tenant with everything and entitlement bugs stay invisible until a real
> customer buys one module — which is the worst possible time to find them.

## Status: mechanism ready, content pending `W-07`

`01-tenants.sql` is empty of inserts, because **the tenant table does not exist yet.**
It is created by `W-07` (tenant model), four tickets away. `W-02` was specified as if
the table already existed; that ordering was wrong and is recorded on issue #3.

Nothing else is blocked by this. The stack, the schemas, the roles and the loader all
work today. When `W-07` lands, the inserts go into `01-tenants.sql` and `./seed.sh`
starts doing something.

## Running it

```bash
infra/docker/seed/seed.sh
```

Idempotent — safe to run repeatedly. It is **local only**: it is not a Flyway
migration and must never be reachable from a deployed environment.

## The two tenants, once `W-07` exists

| Tenant | Modules | Shows you |
|---|---|---|
| `acme-payroll` | **Payroll only** | What a Payroll-only customer sees. No HRMS screens |
| `globex-full` | **HRMS + Payroll** | The combined experience |

Their administrators already exist in Keycloak — `admin.acme` and `admin.globex`, both
with password `local_dev_pw`. See `../keycloak/dev-realm.json`.
