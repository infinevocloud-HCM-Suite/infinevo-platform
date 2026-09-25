# Spec review — W-50 — Azure Infrastructure as Code — 2026-09-18 (Revision 2)

Draft: `docs/target-state/features/W-50-azure-iac.md` on branch `W-50-azure-iac`
Prior review: Verdict NOT READY (5 High blockers F-1..F-5, 7 Mediums).
`main` at `365a319` (W-49 merged). Ticket #70 is `ready`.

## Verdict
**APPROVE** — All 5 High blockers (F-1 through F-5) and all 7 Medium findings are fully resolved in this revision.

---

## Resolution of Previous Review Findings

| ID | Severity | Finding | Resolution in Revision 2 |
|---|---|---|---|
| **F-1** | **High** | Resource check used `grep -E "a\|b\|c"` without checking `provisioningState` | **Fixed**: Loop queries each of the 5 core resource types individually using `az resource show --query "properties.provisioningState" -o tsv` and asserts `== Succeeded`. |
| **F-2** | **High** | Schemas check used `grep` and `\dn` without verifying owner | **Fixed**: Step 6 executes an exact SQL count query joining `pg_namespace` and `pg_roles`, asserting that exactly `5` schemas (`core`, `hrms`, `payroll`, `reference`, `migration`) exist and are owned by `migration_user`. |
| **F-3** | **High** | `az bicep build --lint` failed on unrecognized `--lint` argument | **Fixed**: Corrected to standard `az bicep build --file infra/azure/main.bicep` (which executes the native Bicep linter during compilation). |
| **F-4** | **High** | ACR admin disabled and `AcrPull` deferred to W-51; Container Apps could not pull images or reach running state | **Fixed**: (1) `modules/managed-identities.bicep` assigns `AcrPull` role on `crinfinevo` to the UAMIs in `W-50`; (2) `modules/containerapps.bicep` provisions starter images (`mcr.microsoft.com/k8se/quickstart:latest`); (3) Step 5 asserts `properties.runningStatus == 'Running'` across all 4 Container Apps (`app`, `worker`, `web`, `keycloak`). |
| **F-5** | **High** | No subscription, tenant, service principal, or OIDC login documented | **Fixed**: Added Section 1b documenting subscription/tenant variables (`AZURE_SUBSCRIPTION_ID`, `AZURE_TENANT_ID`), CLI login commands, and GitHub Actions OIDC federated login (`azure/login@v2`). |
| **M-1** | **Medium** | Contradicting script paths (`infra/azure/deploy.sh` vs `scripts/deploy.sh`) | **Fixed**: Standardized on flat `infra/azure/deploy.sh`, `teardown.sh`, and `post-deploy-db.sh` across all sections. |
| **M-2** | **Medium** | Missing `Database changes` section per `TEMPLATE-INFRA.md:14-17` | **Fixed**: Added Section 3d detailing the 2 databases, 5 schemas, 4 roles, `provision.sh` handoff, and `ddl-auto` prohibition. |
| **M-3** | **Medium** | Unspecified Postgres admin password generation | **Fixed**: Documented in §2 and §3c that `deploy.sh` generates a cryptographically secure 32-character password via `openssl rand -base64 24` if absent, stores it in Key Vault (`psql-admin-pw`), and passes it as a `@secure()` parameter. |
| **M-4** | **Medium** | `DEBT-011` silent; Blob storage public access not disabled | **Fixed**: Added `DEBT-011` (Cloudinary replacement) to metadata and §6 Gap disposition. In `modules/storage.bicep`, set `allowBlobPublicAccess: false`. |
| **M-5** | **Medium** | Key Vault and managed identities overlap with `W-51` | **Fixed**: Scope and out-of-scope boundaries clearly articulated: `W-50` builds resource shells (Key Vault, UAMIs, `AcrPull`), while `W-51` builds VNet, private endpoints, and data plane RBAC (`Key Vault Secrets User`). |
| **M-6** | **Medium** | "Decisions confirmed" presented proposals as settled | **Fixed**: Retitled to "Decisions requiring founder confirmation" with explicit recommendations. |
| **M-7** | **Medium** | Second spec existed on `W-50-azure-infra` | **Fixed**: Documented that `docs/target-state/features/W-50-azure-iac.md` on branch `W-50-azure-iac` is the sole canonical specification for Ticket #70. |

---

## Checks Summary

| # | Check | Result | Notes |
|---|---|---|---|
| 1 | Citations resolve | **Pass (8/8)** | Every citation points to verified lines in active code (`infra/README.md:16-20`, `W-01:90`, `W-49:73`, `W-05:102`, `09-build-order.md:265,267`, `05-azure-architecture.md:28,46,143`, `02-data-model.md` §5). |
| 2 | Template complete | **Pass (10/10)** | All sections of `TEMPLATE-INFRA.md` present, including Database changes (§3d). |
| 3 | Gaps and standing rules | **Pass** | `DEBT-004` and `DEBT-011` resolved forward. Zero plain secrets, no `ddl-auto` (`D-46`), non-root container assumptions upheld. |
| 4 | Work is buildable | **Pass** | Clean Bicep modular architecture with complete environment parameters and robust automation scripts. |

---

## Route

The specification is now complete and ready for the founder to confirm the 5 decisions and mark the ticket approved on issue #70.
