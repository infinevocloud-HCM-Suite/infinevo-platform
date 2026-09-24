# Infra Tracker

> Containers, GitHub Actions, Azure, security and operations — streams G and H.
> **GitHub is authoritative. Done means on `origin/main`, nothing else.** Legend: [README.md](README.md).
> Last refreshed: **2026-09-24**, against `origin/main` at `1d1123a`.

## Summary

| | Rows | Done | Of which proven live | In flight | Ready | Blocked |
|---|---|---|---|---|---|---|
| Containers & local stack | 2 | 2 | 2 | 0 | 0 | 0 |
| GitHub Actions | 3 | 3 | 2 | 0 | 0 | 0 |
| Azure | 8 | 7 | **0** | 1 | 0 | 0 |
| Security & operations | 5 | 1 | 0 | 0 | 3 | 1 |
| **Total** | **18** | **13** | **4** | **1** | **3** | **1** |

`W-55` Index & query standard is done and tracked in [DEV-TRACKER.md](DEV-TRACKER.md) §7.

**Azure dev is deployed, but nothing in it starts.** The estate exists in
`rg-infinevo-dev` and `rg-infinevo-shared`, deployed by hand (the `Deploy` workflow has never
succeeded). All four apps run image `git-26c6078` and every revision since 2026-09-19 fails
activation. Branch `W-51-azure-dev-live` has sat unmerged since 2026-09-19.

---

## 0. Blocking right now

| What | Since | Effect |
|---|---|---|
| **GitHub Actions billing** — jobs are refused with "recent account payments have failed or your spending limit needs to be increased" | 2026-09-24 07:29 UTC | CI, `Tickets`, `Deploy` and `Security Rescan` fail without starting a runner. `/merge` gate 10 needs a green CI run, so **no ticket can merge** |
| **No container starts in Azure dev** | 2026-09-19 | Keycloak: `PSQLException: SCRAM-based authentication, but no password was provided` — its database secret is empty. `app`, `worker`: Spring fails on `OAuth2ResourceServerJwtConfiguration…jwtDecoderByJwkKeySetUri` — the JWT key-set setting from `W-10` is not set in the Bicep. `web`: startup probe fails. `worker` also has no scale trigger |
| **Azure deploy identity not configured** | since `W-54` merged | `Deploy` needs `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` as repository variables, and a federated credential for `refs/heads/main`. Neither exists |

---

## 1. Containers and the local stack

| # | Ticket | Spec | Status | Owner | Built by |
|---|---|---|---|---|---|
| #3 | `W-02` Local development stack — nine containers from one command | approved | **Done** — proven, all healthy in 114s | — | InvoiceLLM |
| #69 | `W-49` Containerisation — three production images, non-root, no secrets | approved | **Done** — proven, built and gated by CI | KarmaveerM | KarmaveerM |

---

## 2. GitHub Actions

| # | Ticket | Workflow | Spec | Status | Owner | Built by |
|---|---|---|---|---|---|---|
| #4 | `W-03` Build & test pipeline | `ci.yml` | approved | **Done** — proven; **not running today** (billing) | SayInfi | InvoiceLLM |
| #74 | `W-54` Deployment pipeline | `deploy.yml` | approved | **Done — not live.** Never succeeded | KarmaveerM | sanjib |
| #79 | `W-59` Scanning — dependency, code, container, secret scans, Dependabot | job in `ci.yml` | approved | **Done** — proven in CI | BirenGit | BirenGit |

`.github/workflows/tickets.yml` is harness — see [HARNESS-TRACKER.md](HARNESS-TRACKER.md).

---

## 3. Azure

| # | Ticket | Spec | Status | Owner | Built by |
|---|---|---|---|---|---|
| #70 | `W-50` Azure infrastructure as code — the estate in Bicep | approved | **Done — not live** | KarmaveerM | InvoiceLLM |
| #71 | `W-51` Networking & identity — VNet, private endpoints, RBAC | approved | **Done — not live** | — | InvoiceLLM |
| #72 | `W-52` Queue & worker — Storage Queue, job status, ShedLock | approved | **Done — not live** (tested on Azurite) | — | BirenGit |
| #73 | `W-53` Caching — Redis, permission and master-data cache | approved | **Done — not live** | — | KarmaveerM |
| #76 | `W-56` Secrets — Key Vault, no default values, worker role | approved | **Done — not live** (#129) | BirenGit | sanjib |
| #80 | `W-60` Observability — logging, tracing, metrics, dashboards | approved | **Done — not live** | — | BirenGit |
| #81 | `W-61` Alerting — alert rules, on-call routing | approved | **Done — not live** | KarmaveerM | KarmaveerM |
| #124 | Prove the nine unverified `W-50` / `W-51` checks in a real dev environment | — | **In flight** — `W-51-azure-dev-live` (KarmaveerM, 2026-09-19, not merged) | — | — |

### What "not live" is hiding

The Bicep builds and lints, the scripts parse, but **no live check has ever run**: not
Front Door routing, not the WAF, not the private-path probes, not the role assignments,
not the migration job, not a container resolving a Key Vault secret. #124 closes this and
is part of the work, not polish.

---

## 4. Security, operations, go-to-market

| # | Ticket | Spec | Status | Owner |
|---|---|---|---|---|
| #77 | `W-57` Deny-by-default authentication | — | Ready — `W-10` done | — |
| #78 | `W-58` Tenant isolation tests | — | Ready | — |
| #82 | `W-62` Backup & disaster recovery — a restore actually performed | written | **Done — not live** (`1d1123a`) | — |
| #83 | `W-63` Load test | — | Ready — needs a live environment | — |
| #84 | `W-64` Penetration test | — | Blocked — `W-57`, `W-58` | — |

`W-59`, `W-60` and `W-61` are counted in §2 and §3. `W-65` admin console and `W-66`
website are in [DEV-TRACKER.md](DEV-TRACKER.md) §7.

---

## 5. Decisions that shape this stream

| Decision | Effect |
|---|---|
| `D-10` | Azure Container Apps, not Kubernetes |
| `D-18` | Central India region |
| `D-19` | 10 tenants × 100 employees — the scale every sizing choice is made against |
| `D-48` | One backend image; `app` and `worker` selected by `INFINEVO_ROLE` |
| `D-49` | Frontend runs non-root nginx on 8080 |
| `D-50` | Azure Storage Queue, not Service Bus |
| `D-51` | Front Door **Standard**, not Premium |
| `D-53` | Key Vault is the single exception to "only Front Door is public" |
| `D-54` | Container Apps origins protected by an IP boundary until `W-57` |
| `D-60` | Spring Boot 3.5.x, clearing 29 CVEs |

---

## Related

- Every ticket and its features: [../target-state/08-work-plan.md](../target-state/08-work-plan.md)
- Azure design: [../target-state/05-azure-architecture.md](../target-state/05-azure-architecture.md)
- Runtime and containers: [../target-state/04-runtime-containers.md](../target-state/04-runtime-containers.md)
- Decision register: [../target-state/07-decisions.md](../target-state/07-decisions.md)
- Other trackers: [DEV-TRACKER.md](DEV-TRACKER.md) · [HARNESS-TRACKER.md](HARNESS-TRACKER.md) · [MIGRATION-TRACKER.md](MIGRATION-TRACKER.md)
