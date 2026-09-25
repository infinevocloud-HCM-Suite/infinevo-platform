# Spec review — W-50 — Azure Infrastructure as Code — 2026-09-17 (first pass)

Draft: `docs/target-state/features/W-50-azure-iac.md`
Prior: none (first review)
`main` at `57b6ee7`. Ticket #70 is `ready` (unblocked by W-49 #69).

## Verdict
**APPROVE WITH CONDITIONS** — No High findings. Three Low findings / conditions, all minor metadata or operational details.

The spec adheres strictly to `TEMPLATE-INFRA.md`, `05-azure-architecture.md`, `07-decisions.md`, and the handoff requirements established in `W-05` and `W-49`.

## Checks

| # | Check | Result | Notes |
|---|---|---|---|
| 1 | Citations resolve | **All (7 of 7)** | Every citation verified against the active tree: `infra/README.md:16-20`, `W-01-repository-skeleton.md:90`, `W-49-containerisation.md:73`, `W-05-postgres-schemas.md:102`, `09-build-order.md:265`, `05-azure-architecture.md:28`, and `02-data-model.md` §5 (`D-45`). |
| 2 | Template complete | **All sections (9 of 9)** | `TEMPLATE-INFRA.md` structure complete. Baseline measured, scope bounded, SKU matrix defined, deliberate breaks listed, §5 verification table properly blank for verifier, gap disposition complete. |
| 3 | Gaps and standing rules | **Pass** | `DEBT-004` (secrets in configuration) fully resolved forward via Key Vault; `DEBT-020` and `DEBT-021` cleanly deferred to `W-53` and `W-52`. Standing rules respected: zero committed secrets, no `ddl-auto` (`D-46`), non-root container assumptions maintained. |
| 4 | Work is buildable | **Pass** | Modular Bicep architecture with root orchestrator (`main.bicep`), decoupled modules (`registry`, `keyvault`, `postgres`, `redis`, `servicebus`, `storage`, `containerapps`), environment parameters (`dev`, `uat`, `prod`), and idempotent orchestration scripts (`deploy.sh`, `teardown.sh`, `post-deploy-db.sh`). |

## Stale-Text & Truthfulness Sweep
- No placeholder "TODO" or fabricated outputs.
- Baseline is accurately measured: `infra/azure/` currently contains only `.gitkeep`, no Azure resource groups or ACRs currently exist in subscription query.
- Decided architecture parameters honored:
  - Region: `centralindia` (`D-18`).
  - Scale: 10 tenants × 100 employees (`D-19`) reflected in minimal burstable SKUs (`B1ms`, Basic C0) with scale-to-zero in non-production.
  - Dual-database single-instance topology: `infinevo` and `keycloak` on one Flexible Server (`05` §3).
  - Dedicated `migration` schema created by `02-schemas.sql` (`D-45`).

## Findings

| ID | Severity | Finding | Where | Status |
|---|---|---|---|---|
| C-1 | Condition | **Founder / Manager confirmation needed on decisions**: Confirm decisions 1–5 in §10 before starting build: (1) Bicep over Terraform, (2) `Standard_B1ms` for dev Postgres, (3) `teardown.sh` strictly refusing `prod`, (4) ACR Basic for dev / Standard for prod, (5) Shared Key Vault in `rg-infinevo-shared`. | §10 "Decisions confirmed" | OPEN — founder |
| F-1 | Low | **Owner field is blank in the spec header**: Set `Owner` to `KarmaveerM` (the developer driving Stream G / Wave 2). | Header line 10 | OPEN |
| F-2 | Low | **Transient IP firewall rule cleanup in dev**: Risk 4 notes that pre-`W-51` (before private endpoints are active), the deployment script will add a temporary IP firewall rule on Flexible Server to execute `post-deploy-db.sh`. Ensure `post-deploy-db.sh` guarantees rule cleanup even if `provision.sh` fails (e.g. via `trap` in bash). | §7 Risk 4; scripts | OPEN |

## What is Good

- **The "Rebuild-Twice" test is front and center**: Directly enshrines the core principle from `09-build-order.md:265` (*"An environment you cannot recreate is not infrastructure as code"*) as deliberate break #1 and verification check #6.
- **Explicit container definitions**: Unlike early drafts that stopped at the Container Apps Environment, the spec includes `modules/containerapps.bicep`, ensuring that `app`, `worker`, `web`, and `keycloak` are instantiated as real Container Apps resources with correct ports and probes.
- **Non-destructive teardown**: `teardown.sh` explicitly scopes deletions to `rg-infinevo-{env}`, leaving `rg-infinevo-shared` (ACR, Key Vault, Log Analytics) intact, and actively refuses to destroy `prod`.
- **Clean handoff from W-05**: Does not reinvent role/schema provisioning; reuses `infra/postgres/provision.sh` directly against the Flexible Server endpoint.

## Route

1. Set `Owner: KarmaveerM` in the header of `docs/target-state/features/W-50-azure-iac.md`.
2. Present the spec to the founder/manager for confirmation of the 5 decisions and formal approval.
3. Upon approval, proceed to **Step 3 (Build)** on branch `W-50-azure-iac`.
