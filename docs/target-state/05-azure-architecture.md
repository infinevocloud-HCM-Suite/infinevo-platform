# 05 — Azure Architecture

> Target state. Where it runs. No Dockerfile detail here — that is `04`.

---

## 1. Hosting choice

**Azure Container Apps, not Kubernetes.** (`D-10`)

| Consideration | Assessment |
|---|---|
| Containers to run | Five |
| Team operating it | One lead plus part-time developers |
| What Container Apps gives | Autoscaling, scale-to-zero, revision-based blue-green, managed ingress and certificates, private networking — with nobody operating a cluster |
| What Kubernetes would add | Fine-grained scheduling, a large ecosystem, portability |
| What Kubernetes would cost | A cluster to patch, upgrade and debug, permanently |

Five containers do not justify a cluster. Kubernetes remains available later if the shape
changes; migrating from Container Apps to it is not a rewrite.

---

## 2. Resource groups

| Group | Contents | Lifetime |
|---|---|---|
| `rg-infinevo-shared` | Container registry, Key Vault, Log Analytics workspace, DNS zone, Front Door | Permanent, one only |
| `rg-infinevo-dev` | Full stack, minimal sizing, scales to zero | Rebuildable at will |
| `rg-infinevo-uat` | Full stack, small. Migration rehearsals and acceptance testing | Stable |
| `rg-infinevo-prod` | Full stack, redundant, private | Protected, change-controlled |

**Every environment has the same shape**, differing only in sizing and redundancy. A
rehearsal that runs in acceptance therefore means something for production.

---

## 3. Resource inventory, per environment

| Azure service | Purpose | Replaces today |
|---|---|---|
| Container Apps Environment | Runs the five containers | Manual deploys on DigitalOcean |
| Database for PostgreSQL, Flexible Server | One server. Two databases: platform (4 schemas) and Keycloak | Two MySQL databases |
| Cache for Redis | Permissions, master data, tenant config, sessions | The in-process cache that breaks with two instances |
| Service Bus | Pay run, report and import job queue | Nothing — everything is synchronous today |
| Blob Storage | Employee documents, payslips, investment proofs | **Cloudinary**, which is outside Azure |
| Key Vault (shared) | Every secret | Passwords in `application.properties`, committed |
| Container Registry (shared) | Built images | Nothing |
| Front Door + WAF (shared) | Entry, TLS, certificates, attack filtering | Nothing |
| Application Insights | Traces, metrics, live dashboards | Nothing |
| Log Analytics (shared) | Central logs across environments | Server-local log files |
| Managed identity, per container | Authenticates to database, vault and storage without passwords | Connection strings in files |
| Private endpoints + VNet | Database, cache and storage unreachable from the internet | A publicly reachable database |

---

## 4. Networking and identity

```
Internet
   │
   ▼
Front Door + WAF ──────► Container Apps Environment
                              │   app · worker · web · keycloak
                              │
                         (private endpoints only)
                              │
              ┌───────────────┼───────────────┬──────────────┐
              ▼               ▼               ▼              ▼
          Postgres         Redis          Service Bus      Blob
```

| Rule |
|---|
| Only Front Door is public. Nothing else has a public endpoint |
| Containers reach data services over private endpoints inside the virtual network |
| No connection string anywhere. Each container has a managed identity with least-privilege access |
| Key Vault holds what cannot be an identity: third-party keys, signing secrets |
| TLS everywhere, terminated at Front Door and re-established inward |

**The managed identity point is the one that matters.** It removes the class of incident
where a leaked configuration file grants database access — which is the current exposure,
since credentials sit in files in the repositories.

---

## 5. Database roles and row-level security

Repeated from `02` §9 because it is an infrastructure control, not only a schema detail.

| Role | Used by | Rights | RLS |
|---|---|---|---|
| `app_user` | `app`, `worker` | Read/write `core`, `hrms`, `payroll`; read `reference` | **Enforced**, tenant set per transaction |
| `migration_user` | Pipeline migration step only | DDL | Bypassed |
| `readonly_user` | Reporting, exports, replica | Read only | Enforced |

**The application never connects as an owner.** Without that, row-level security is advice
rather than a boundary.

| Production database settings |
|---|
| High availability, zone-redundant |
| Automated backups with point-in-time restore |
| A read replica for reporting and exports — **deferred at current scale (`D-19`)**, added when reporting load justifies it |
| Connection pooling in front, so many container instances do not exhaust connections |
| Private access only |

---

## 6. Deployment and rollback

```
merge to main
   │
   ├─► build images (backend, frontend, keycloak)
   ├─► run tests and lint          ── fails here, nothing deploys
   ├─► push to Container Registry with an immutable tag
   │
   ├─► deploy to dev        automatic
   ├─► deploy to uat        on approval
   └─► deploy to prod       on approval, same image, never rebuilt
```

| Step | Detail |
|---|---|
| Migration | Runs as a separate step before the new revision takes traffic, using `migration_user` |
| Release | A new Container Apps revision starts alongside the old one |
| Traffic | Shifted to the new revision once healthy |
| Rollback | Shift traffic back. Seconds, not a redeploy |
| Schema rollback | **Not automatic.** Forward-only migrations mean a bad schema change needs a new script. Plan migrations to be backward-compatible with the previous release |

**The last row is the one that bites people.** Code rolls back in seconds; schema does not.
Every migration should leave the previous version of the application still able to run —
add columns before using them, remove them a release later.

---

## 7. Data residency and compliance

| Item | Position |
|---|---|
| Region | **India (`D-18`).** Indian payroll data under the Digital Personal Data Protection Act. Primary and backups both in-jurisdiction |
| Cloudinary | Employee documents, payslips and investment proofs currently sit with a third party outside Azure. Moving them to Blob Storage is a migration in its own right, not a configuration change |
| Backups | Must be in the same jurisdiction as the primary |
| Audit | `CORE-14` provides the change history that compliance expects and neither product has today |

---

## 7a. Public surface policy (`D-22`)

**Deny by default.** Everything requires a valid token. There is no `permitAll()` on a path
prefix, because a prefix grant silently publishes any controller later mapped beneath it.

The complete list of endpoints reachable without a session:

| Endpoint | How it is protected | Why it must be open |
|---|---|---|
| Signed payslip download | HMAC signature, time-limited, single payslip. **Signature never logged** | Employees open it from an email, often with no session |
| Container health and readiness | Internal to the Container Apps environment. Not routed through Front Door | The platform probes it |

Nothing else. Organisation registration requires an identity-provider account first, so it is
authenticated. The marketing website makes no platform calls at all (`D-13`).

**Enforced, not merely documented:** a build-time check fails if a new endpoint is added
without authentication and without an entry on the exception list above. A rule nobody can
accidentally break is worth more than a rule everyone is asked to remember.

---

## 8. Cost shape

Not a budget. The levers, so sizing decisions are informed.

| Lever | Effect |
|---|---|
| Dev and acceptance scale to zero when idle | Removes most non-production compute cost |
| One Postgres server, two databases | Avoids paying for a second server for Keycloak |
| Worker scales on queue depth | Pay-run capacity exists only during pay runs |
| Read replica | Added when reporting load justifies it, not before |
| High availability | Production only |

**Target scale is 10 tenants, max 100 employees each (`D-19`)** — about 1,000 employees
total. That is a small system. Two instances for availability rather than throughput, the
smallest viable database tier with high availability in production only, and no read
replica until reporting load justifies one. See `07` §4 for what this downgrades.

**Do not size production from today's load either.** Today's system cannot run more than one
instance, so current usage measures its limits, not demand.

---

## Related

- Containers: `04-runtime-containers.md` · Schema: `02-data-model.md`
- Decisions: `07-decisions.md`
