# Infra Tracker

> Containers, GitHub Actions, Azure, security and operations — streams G and H.
> **GitHub is authoritative.** Status legend: [README.md](README.md).
> Last refreshed: **2026-09-24**, against `main` — every row checked against a merge commit.

## Summary

| | Tickets | Spec approved | Code on main | Feature done |
|---|---|---|---|---|
| Containers & local stack | 2 | 2 | 2 | 2 |
| GitHub Actions | 4 | 4 | 4 | 2 |
| Azure | 5 | 5 | 5 | **0** |
| Security & operations | 7 | 5 | 5 | 4 |
| Open defects | 1 | — | — | — |

**Nothing Azure has ever been deployed.** Five tickets are merged and zero are proven (#124).

---

## 1. Containers and the local stack

| # | Ticket | What it is | Spec | Code | Feature |
|---|---|---|---|---|---|
| #3 | `W-02` Local development stack | Nine containers from one command, schema bootstrap on start, two seeded tenants with different module sets, mail catcher | approved | on main | **done** — all healthy in 114s |
| #69 | `W-49` Containerisation | Three production images: backend carrying both `app.jar` and `worker.jar` selected by `INFINEVO_ROLE`, unprivileged nginx on 8080, Keycloak with no realm baked in. All non-root, no secrets | approved | on main | **done** — 154 / 24 / 225 MB, built and gated by CI |

Files: `infra/docker/` — `compose.yml`, `backend.Dockerfile`, `frontend.Dockerfile`,
`keycloak.Dockerfile`, `migration-runner.Dockerfile`, two dev Dockerfiles, three
entrypoints, `nginx/`, `postgres/`, `seed/`.

Left behind by `W-49`, both handed to `W-59`: the container scan's "report-only until
`W-49`" condition has expired, and the Keycloak image runs with primary group 0.

---

## 2. GitHub Actions

| # | Ticket | Workflow | What it does | Spec | Code | Feature |
|---|---|---|---|---|---|---|
| #4 | `W-03` Build & test pipeline | `.github/workflows/ci.yml` | Compile, lint, test, build images. Deploys nothing, pushes to no registry | approved | on main | **done** |
| #70 | `W-50` (part of) | `.github/workflows/infra.yml` | Bicep lint and what-if on every change under `infra/azure/` | approved | on main | **code done** — what-if never run against a real subscription |
| #74 | `W-54` Deployment pipeline | `.github/workflows/deploy.yml` | Promote the image CI already built, never rebuild it; run the migration job; switch revision; roll back by shifting traffic. OIDC to Azure | approved | on main | **code done** — merged 2026-09-22, never executed |
| #79 | `W-59` Scanning | `.github/workflows/security.yml` | Trivy dependencies and images, Semgrep SAST, Gitleaks, Dependabot. Carried Spring Boot 3.3.13 → 3.5.16 (`D-60`, 29 CVEs) | approved 2026-09-16 | on main `ebb1d14` | **done** |

`W-59` edited the same `images` job that `W-49` rewrote, and landed as its own workflow.

`.github/workflows/tickets.yml` is harness, not infra — see
[HARNESS-TRACKER.md](HARNESS-TRACKER.md).

---

## 3. Azure

| # | Ticket | What it builds | Spec | Code | Feature |
|---|---|---|---|---|---|
| #70 | `W-50` Azure infrastructure as code | The estate in Bicep — 2 resource groups, container registry, Key Vault, Log Analytics, Container Apps environment and 4 apps, Postgres 16 Flexible, Redis, Storage blob and queue, 4 managed identities, Front Door with WAF | approved 2026-09-18 | on main | **code done** |
| #71 | `W-51` Networking & identity | The perimeter — VNet and subnets, private endpoints and private DNS for Postgres, Redis, Storage and Key Vault, Front Door origin lock by IP, managed-identity RBAC, in-VNet migration job, Central India residency | approved 2026-09-19 rev 3 | on main | **code done** |
| #72 | `W-52` Queue & worker | Background jobs — Storage Queue in Azure and Azurite locally, job dispatch, job status and progress, ShedLock so the two cron jobs stop firing twice on multiple replicas | approved 2026-09-21 | on main `e833196` | **done** — Storage Queue, ShedLock, `JobStatusController` |
| #73 | `W-53` Caching | Cache abstraction, permission cache, master data cache, invalidation. The Redis already exists from `W-50` | approved 2026-09-22 | on main `808d837` | **done** |
| #76 | `W-56` Secrets | No credential anywhere in the repo — Key Vault, managed-identity resolution, dual-role zero-downtime database rotation | approved 2026-09-23 rev 6 | on main `64ec5fd` | **code done** — rotation removed in rev 6; never run in Azure |

### What "code done" is hiding

`W-50`, `W-51`, `W-54`, `W-56`, `W-60`, `W-61` and `W-62` are verified as code and never
as an environment. The Bicep builds and lints clean and the scripts parse, but **no live
check has ever run**: not Front Door routing, not the WAF, not the six private-path probes,
not the 16 role assignments, not the migration job, not a deployment, not a Key Vault
resolution, not an alert rule, and **not a restore**. The shape of the risk is `W-50`'s `AcrPull` role — declared, never
exercised, green through four gate passes. **#124** closes this and is part of the work,
not polish.

### Carried forward

- `W-52` is merged, so `W-20` notifications and `W-29` pay run are unblocked on the queue.
- `W-52` must handle **idempotency in code**: Storage Queue does not guarantee ordering
  (`D-50`).

---

## 4. Security, operations, go-to-market

Observability, alerting, backup and load testing are merged. `W-57`, `W-58` and `W-64` have no
spec yet.

| # | Ticket | What it is | Status |
|---|---|---|---|
| #77 | `W-57` Deny-by-default authentication | Everything closed unless explicitly listed, with a build-time check that fails on a new unlisted public endpoint. Also carries the `X-Azure-FDID` origin check deferred out of `W-51` | `W-10` Identity |
| #78 | `W-58` Tenant isolation tests | Cross-tenant read tests, row-level security verification, wired into the pipeline | ready — `W-08` merged |
| #80 | `W-60` Observability | Structured logging, tracing, metrics, health endpoints, dashboards. One request followable across app, worker and database | **on main `3e1aebc`** · code done |
| #81 | `W-61` Alerting | Alert rules, routing to a person, an on-call process | **on main `10e60bc`** · code done — no rule has ever fired |
| #82 | `W-62` Backup & disaster recovery | Backup configuration, a restore that has actually been performed, a recovery runbook | **on main `1d1123a`** · code done — **no restore has been performed** |
| #83 | `W-63` Load test | Scenarios, baseline, regression run | **on main** · code done |
| #84 | `W-64` Penetration test | External engagement and remediation. Re-tests the perimeter and is where `D-51` Front Door Standard gets revisited | `W-57`, `W-58` |

---

## 5. Open defects and follow-ups

| # | What | Size |
|---|---|---|
| #124 | Prove the nine unverified `W-50` / `W-51` acceptance checks against a real dev environment | M |

Closed with their tickets: #138 (migrate job reported success while applying nothing),
#120 (docs drift from `W-49`).

---

## 6. Decisions that shape this stream

| Decision | Effect |
|---|---|
| `D-10` | Azure Container Apps, not Kubernetes |
| `D-11` | API gateway deferred |
| `D-18` | Central India region |
| `D-19` | 10 tenants × 100 employees — the scale every sizing choice is made against |
| `D-48` | One backend image; `app` and `worker` selected by `INFINEVO_ROLE` |
| `D-49` | Frontend runs non-root nginx on 8080 |
| `D-50` | Azure Storage Queue, not Service Bus — a private endpoint on Service Bus is Premium-tier only, roughly ten times Standard |
| `D-51` | Front Door **Standard**, not Premium — no managed OWASP rule set, no Private Link origins |
| `D-53` | Key Vault is the single documented exception to "only Front Door is public" |
| `D-54` | Container Apps origins are protected by an IP boundary, not authentication, until `W-57` |

---

## Related

- Every ticket and its features: [../target-state/08-work-plan.md](../../../docs/target-state/08-work-plan.md)
- Azure design: [../target-state/05-azure-architecture.md](../../../docs/target-state/05-azure-architecture.md)
- Runtime and containers: [../target-state/04-runtime-containers.md](../../../docs/target-state/04-runtime-containers.md)
- Decision register: [../target-state/07-decisions.md](../../../docs/target-state/07-decisions.md)
- Other trackers: [DEV-TRACKER.md](DEV-TRACKER.md) · [HARNESS-TRACKER.md](HARNESS-TRACKER.md) · [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md)
