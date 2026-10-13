# Keycloak — local development realm

`dev-realm.json` is a **minimal development realm** (`W-02`, spec Q3). It exists so the
frontend can be exercised, nothing more. `W-10` replaces it wholesale with the real
export — roles, groups, password policy, token lifetimes, login flows and the theme.

**Every credential in it is a local placeholder and must never appear in a deployed
environment.**

## The file carries no comments, deliberately

Keycloak's realm importer rejects unknown properties outright:

```
ERROR: Unrecognized field "_comment" (class RealmRepresentation), not marked as ignorable
```

JSON has no comment syntax, so explanation lives here instead.

## What is in it

| Realm role | For |
|---|---|
| `platform-admin` | Internal administration — admin console, support impersonation (`PLAT-02`) |
| `tenant-admin` | Administers one tenant: employees, setup, approvals |
| `employee` | Self-service, limited to whichever modules the tenant holds |
| `hr` | HR for one tenant: employees, leave, attendance, timesheets |
| `manager` | Approves the team's requests through the reporting line |
| `payroll-officer` | Runs payroll for one tenant |
| `finance` | Payroll review and reports for one tenant |

The last four mirror the system roles `V022__role_action.sql` seeds into `core.role`. The
backend reads roles from `core.user_role`, not from the token, so the realm role on a user
is documentation and a Keycloak import requirement — a user may only reference a realm
role the realm defines.

| Client | |
|---|---|
| `infinevo-web` | Public client for the React frontend, redirecting to `localhost:5173` |

| User | Password | Tenant | Role |
|---|---|---|---|
| `admin.acme` | `Local_dev_pw1!` | `acme-payroll` — **Payroll only** | `tenant-admin` |
| `admin.globex` | `Local_dev_pw1!` | `globex-full` — **HRMS + Payroll** | `tenant-admin` |
| `employee.acme` | `Local_dev_pw1!` | `acme-payroll` | `employee` |
| `employee.globex` | `Local_dev_pw1!` | `globex-full` | `employee` |
| `hr.globex` | `Local_dev_pw1!` | `globex-full` | `hr` |
| `manager.globex` | `Local_dev_pw1!` | `globex-full` | `manager` |
| `payroll.globex` | `Local_dev_pw1!` | `globex-full` | `payroll-officer` |
| `finance.globex` | `Local_dev_pw1!` | `globex-full` | `finance` |
| `staff.infinevo` | `Local_dev_pw1!` | platform tenant `00000000-…-0001` | `platform-admin` |

Two administrators, because the two seeded tenants hold **different module sets**:
`admin.acme` sees no HRMS screens and `admin.globex` sees everything. Globex also carries
one login per system role so an end-to-end browser suite can sign in as each; the seed
(`../seed/README.md`) gives every one of them a `core.user_role` grant and an employee
record, and makes `manager.globex` the manager of `employee.globex`.

Every user's `id` is pinned (`a…` Acme, `b…` Globex, `c…` platform) and repeated in the
seed SQL — `02-user-tenants.sql`, `03-user-roles.sql`, `06-employees.sql`. A new user
needs all four files edited together or its first login ends in `401 TENANT_NOT_BOUND`.

The `tenant_id` attribute on each user is how the binding filter (`W-08`) will resolve the
tenant from the authenticated principal — never from a header or a query parameter the
client controls.

## Re-importing after a change

The realm imports only on first start. To pick up an edit:

```bash
docker compose -f infra/docker/compose.yml down -v
docker compose -f infra/docker/compose.yml up -d
```
