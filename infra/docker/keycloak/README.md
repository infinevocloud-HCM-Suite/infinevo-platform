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

| Client | |
|---|---|
| `infinevo-web` | Public client for the React frontend, redirecting to `localhost:5173` |

| User | Password | Tenant | Role |
|---|---|---|---|
| `admin.acme` | `local_dev_pw` | `acme-payroll` — **Payroll only** | `tenant-admin` |
| `admin.globex` | `local_dev_pw` | `globex-full` — **HRMS + Payroll** | `tenant-admin` |
| `employee.globex` | `local_dev_pw` | `globex-full` | `employee` |

Two administrators, because the two seeded tenants hold **different module sets**. Once
entitlement lands (`W-11`), `admin.acme` should see no HRMS screens and `admin.globex`
should see everything. That difference is the reason both exist.

The `tenant` attribute on each user is how the binding filter (`W-08`) will resolve the
tenant from the authenticated principal — never from a header or a query parameter the
client controls.

## Re-importing after a change

The realm imports only on first start. To pick up an edit:

```bash
docker compose -f infra/docker/compose.yml down -v
docker compose -f infra/docker/compose.yml up -d
```
