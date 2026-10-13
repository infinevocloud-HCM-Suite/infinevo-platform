# Seed data

**Two tenants.** One tenant hides every isolation bug there is: a query that forgot
its tenant filter returns exactly the right rows, and an RLS policy that never matches
looks identical to one that always matches.

## Status

| Part | State |
|---|---|
| Loader | Works — `seed.sh`, idempotent |
| Two tenants | **Seeded.** `core.tenant` exists (`W-07`), so `01-tenants.sql` inserts |
| Dev users' roles | **Seeded.** `03-user-roles.sql`: one grant per login, see the user table below. Without it every local login gets `403` since `W-11.2` |
| Different module sets | **Seeded.** `04-subscriptions.sql`: Acme holds PAYROLL only, Globex holds HRMS and PAYROLL (`W-12.1`) |
| Employee records | **Seeded.** `06-employees.sql`: one `core.employee` row for every non-admin login, linked to it, so the `/me` portal has an employee to show (`W-25` §8) and approval tasks have someone to land on |
| Setup checklist rows | **Seeded.** `05-tenant-setup-steps.sql` mirrors `SetupStepCatalogue` per tenant's modules (`W-24.1` §8); `SeedSetupStepsMatchCatalogueTest` fails the build if they drift |
| Reporting line | **Seeded.** `07-reporting-lines.sql`: `manager.globex` is the PRIMARY manager of `employee.globex`, so the LEAVE flow's `REPORTING_MANAGER` step resolves |

## The users

Every login's password is `Local_dev_pw1!`. Keycloak ids are pinned in
`../keycloak/dev-realm.json` and repeated in `02-user-tenants.sql`, `03-user-roles.sql`
and `06-employees.sql` — change one, change all four.

| Login | Keycloak id | Tenant | Role (`core.role.code`) | Employee |
|---|---|---|---|---|
| `admin.acme` | `a0000000-…-0001` | Acme | `tenant-admin` | none, on purpose |
| `employee.acme` | `a0000000-…-0002` | Acme | `employee` | `ACME-0001` |
| `admin.globex` | `b0000000-…-0001` | Globex | `tenant-admin` | none, on purpose |
| `employee.globex` | `b0000000-…-0002` | Globex | `employee` | `GLBX-0001`, reports to `GLBX-0003` |
| `hr.globex` | `b0000000-…-0003` | Globex | `hr` | `GLBX-0002` |
| `manager.globex` | `b0000000-…-0004` | Globex | `manager` | `GLBX-0003` |
| `payroll.globex` | `b0000000-…-0005` | Globex | `payroll-officer` | `GLBX-0004` |
| `finance.globex` | `b0000000-…-0006` | Globex | `finance` | `GLBX-0005` |
| `staff.infinevo` | `c0000000-…-0001` | Infinevo (platform) | `platform-admin` | none |

Globex carries one login per system role so an end-to-end browser suite can sign in as
each. The two admins have no employee record on purpose: a login without one is the case
the `/me` endpoints must answer cleanly.

A leave request from `employee.globex` therefore has a complete approval chain: step one
goes to `manager.globex` through the reporting line, step two to `hr.globex` through the
`hr` role (`V089__approval_definition.sql`).

**Adding or changing a Keycloak user needs a fresh Keycloak volume.** The realm imports
only on first start, so after editing `dev-realm.json` run
`docker compose -f infra/docker/compose.yml down -v` and bring the stack back up, then
re-run `seed.sh`. Postgres loses its data too; the seed restores it.

## Running it

```bash
infra/docker/seed/seed.sh
```

Idempotent — safe to run repeatedly. It is **local only**: it is not a Flyway
migration and must never be reachable from a deployed environment.

## The two tenants

| Tenant | `tenant_id` | Modules |
|---|---|---|
| Acme Manufacturing | `11111111-1111-1111-1111-111111111111` | **Payroll only** — what a Payroll-only customer sees |
| Globex Corporation | `22222222-2222-2222-2222-222222222222` | **HRMS + Payroll** — the combined experience |

The UUIDs are fixed, not generated, so a developer can hardcode one in a request and
still be talking about the same tenant after a restart. They are the same two the
migration module's `TenantIsolationIT` uses.

Their logins are listed above and live in `../keycloak/dev-realm.json`.
