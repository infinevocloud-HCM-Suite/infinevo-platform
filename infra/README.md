# infra

Everything about running the platform. No application code here.

| Folder | Holds | Built by |
|---|---|---|
| `azure/` | Bicep definitions - Container Apps, Postgres, Key Vault, networking | `W-50` |
| `docker/` | Dockerfiles and the local compose stack | `W-49`, `W-02` |
| `keycloak/` | Realm export and login theme | `W-10` |

Pipelines live in `.github/workflows/` and cannot move here - GitHub Actions only
reads workflows from that path.

## Why there is no legacy equivalent

The four frozen applications contain one deployment file between them,
`legacy/Payroll-Fend-react/nginx/nginx.conf`. No Dockerfiles, no compose file, no
infrastructure as code, no realm export, no pipeline - the current system is deployed
by hand.

So unlike `docs/` and the code, this pillar has no frozen twin to mirror. Everything
here is new capability rather than a port, which is also why `W-49` and `W-50` have the
longest lead time in the plan.

## What keycloak is

Keycloak is the login system - it holds users, roles and sessions, and issues the tokens
the backend trusts. `keycloak/` holds its **configuration**, not application code: the
realm export (roles, clients, password policy, token lifetimes, login flows) so a new
environment is restored from a file rather than clicked together by hand, and the theme
so the login page looks like the product.
