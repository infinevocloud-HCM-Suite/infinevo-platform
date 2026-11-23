# W-114.2 — Claude on Microsoft Foundry: the Azure side of the AI helper

| Field | Value |
|---|---|
| **Work item** | `W-114.2` |
| **Kind** | Infra |
| **Size / skill** | M — INFRA (the brief said S; it touches `main.bicep`, three existing modules and one new one) |
| **Owner** | Claude (`dev-claude-w114`) |
| **Blocked by** | nothing to write and merge. **To deploy:** the founder's Azure steps in §7 |
| **Blocks** | A real model answer in `W-114.4`. Nothing else: `W-114.3` onward build and test against the stub client |
| **Decisions** | `D-18` (India region) — **this ticket is a founder-approved exception to it, see below** |
| **Gaps addressed** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |

## Founder decisions (2026-11-23)

| Question | Answer |
|---|---|
| Model | Claude on Microsoft Foundry |
| Data location | Processing outside India is accepted for the AI helper. PAN, Aadhaar and bank account numbers are masked before sending (`W-114.4`); salary and leave figures are sent |
| Who deploys | The founder. This ticket delivers Bicep that builds and lints |

**This conflicts with `D-18` as written.** `D-18` puts everything in India
(`docs/target-state/07-decisions.md:30`) and `main.bicep` refuses any other region
(`infra/azure/main.bicep:11-19`). The founder's answer above is an exception for one
resource: the AI model. Everything else stays in Central India and the `location` lock is
not touched. The exception is recorded as `D-61` in `docs/target-state/07-decisions.md`.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | `az bicep build` and `lint` pass with the AI resource declared and switched off by default | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

| # | Today | Evidence |
|---|---|---|
| 1 | No AI resource exists in the Bicep. | `grep -rn "CognitiveServices" infra/azure` → 0 matches |
| 2 | Every resource is forced into Central India; Claude cannot be deployed there. | `infra/azure/main.bicep:11-19`; Microsoft lists East US 2 and Sweden Central only (see §3) |
| 3 | The private endpoint module accepts five kinds of target, none of them an AI resource. | `infra/azure/modules/private-endpoint.bicep:13-21` |
| 4 | The private DNS module has zones for Postgres, Redis, Blob, Queue and Key Vault only. | `infra/azure/modules/private-dns.bicep:18,37,60,82,101` |
| 5 | The app container has no AI setting. | `grep -rn "INFINEVO_AI" infra/azure` → 0 matches |

**Baseline**

| Command | Exit | Output |
|---|---|---|
| `az bicep build --file infra/azure/main.bicep` | not run | to be recorded before the change |
| `az bicep lint --file infra/azure/main.bicep` | not run | to be recorded before the change |

## 2. Scope

**In scope**

- New module `infra/azure/modules/ai-foundry.bicep`: one Foundry (AI Services) resource, one Claude deployment, the role assignment for the app's identity, a monthly budget alert.
- A private endpoint and the DNS zones for it.
- Two settings on the app container: `INFINEVO_AI_ENDPOINT`, `INFINEVO_AI_DEPLOYMENT` (Spring reads them as `infinevo.ai.endpoint`, `infinevo.ai.deployment`).
- A switch, `deployAi`, **off by default**, so merging this changes nothing in Azure.

**Out of scope**

- The Java client — `W-114.4`.
- Running the deployment, accepting marketplace terms, quota — the founder (§7).
- The per-customer cap — `W-114.1`. The budget alert here is a second, Azure-side guard.

## 3. What Microsoft's documents say

| Point | Finding | Source |
|---|---|---|
| Regions | Claude is offered for Global Standard deployment in **East US 2** and **Sweden Central** | [Claude models in Foundry](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/claude-models?tabs=pay-go) |
| Deployment type | Every Claude model supports Global Standard; some Azure-hosted ones also offer a US data zone. No India or Asia zone was found | [Hosting comparison](https://learn.microsoft.com/en-my/Azure/foundry/foundry-models/concepts/claude-models-hosting-comparison) |
| Endpoint | `https://<resource>.services.ai.azure.com/anthropic`, Claude's Messages API | [Configure Claude Code for Foundry](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/how-to/configure-claude-code) |
| Sign-in | Microsoft Entra ID works, token scope `https://ai.azure.com/.default`; an API key is the alternative | [Use Claude models in Foundry](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/how-to/use-foundry-models-claude) |
| Bicep | Microsoft publishes a Bicep and Terraform starter kit. Each Claude deployment is `GlobalStandard` and needs a `modelProviderData` block (organisation name, country code, industry in lower case) | [Deploy Claude on Foundry with Bicep](https://learn.microsoft.com/en-us/azure/developer/ai/how-to/deploy-claude-foundry) · [Starter kit](https://learn.microsoft.com/en-us/samples/azure-samples/claude/claude/) |
| Buying | Needs an Azure Marketplace subscription; free trial subscriptions are not supported | [Configure Marketplace](https://learn.microsoft.com/en-my/Azure/foundry/foundry-models/how-to/configure-marketplace) |
| Data processor | Anthropic is the seller and operator and processes prompts and outputs | [Data privacy for Claude models](https://learn.microsoft.com/en-us/azure/foundry/responsible-ai/claude-models/data-privacy) |

### Not confirmed — the implementer checks each before writing the Bicep

| Point | Why it matters | If the answer is no |
|---|---|---|
| The exact Bicep schema of a Claude deployment (`Microsoft.CognitiveServices/accounts/deployments`, model format, `modelProviderData` field names) | The search returned the starter kit's description, not the schema | Copy the starter kit's template verbatim |
| A private endpoint carries Claude traffic end to end | Microsoft documents private endpoints for Foundry resources in general, nothing specific to Claude | Keep `publicNetworkAccess` off only if calls still work; otherwise stop and ask the founder — an open endpoint is their call |
| A private endpoint in Central India can reach a resource in Sweden Central or East US 2 | The network is in Central India and must stay there | Same as above |
| The DNS zones an AI Services resource needs (`privatelink.cognitiveservices.azure.com`, `privatelink.openai.azure.com`, `privatelink.services.ai.azure.com`) | A missing zone makes the app resolve the public address | Add the zone the portal names |
| The role the app identity needs. The starter kit says `Cognitive Services User`; that is sample guidance, not the official role page | Wrong role means `401` | Use the role the official page names |
| Which subscription types may buy Claude. Community answers say Enterprise or MCA-E only | The founder's subscription may not qualify | Founder raises it with Microsoft before step 1 of §7 |
| The newest Sonnet-class model in the catalogue | The default below is a parameter, not a fact | Founder sets `aiModelName` and `aiModelVersion` |
| Whether a budget filtered to this resource sees Marketplace charges for Claude | Otherwise the alert never fires | Scope the budget to the whole resource group instead |

## 4. Changes

| File | Change |
|---|---|
| `infra/azure/modules/ai-foundry.bicep` | **New.** Params: `environment`, `aiLocation`, `aiModelName`, `aiModelVersion`, `aiCapacity`, `appPrincipalId`, `alertEmail`, `aiMonthlyBudget`, `tags`. Resources: the AI Services account `ais-infinevo-${environment}` with a custom subdomain, `publicNetworkAccess: 'Disabled'`, `disableLocalAuth: true` (no keys exist, so none can leak); one deployment, Global Standard; the role assignment for `id-app-${environment}` only — the worker never calls the model; a monthly budget with mail at 80% and 100% to `alertEmail`. Outputs: `accountId`, `endpoint` (`https://<name>.services.ai.azure.com/anthropic`), `deploymentName` |
| `infra/azure/main.bicep` | New params `deployAi bool = false`, `aiLocation` (`@allowed(['swedencentral','eastus2'])`, default `swedencentral`), `aiModelName`, `aiModelVersion`, `aiCapacity`, `aiMonthlyBudget`. Module `aiFoundry` at `envRg`, `if (deployAi)`. A private endpoint module instance on the pattern of `:448-460`, also `if (deployAi)`. Pass `aiEndpoint` and `aiDeployment` into `containerApps` (`:341-384`), empty when `deployAi` is false. **The `location` param and its `@allowed` list (`:11-19`) are not changed** |
| `infra/azure/modules/private-endpoint.bicep` | Add `'account'` to the allowed `groupId` list (`:13-21`) |
| `infra/azure/modules/private-dns.bicep` | The AI zones, each linked to the VNet like the vault zone (`:101`), with outputs beside `:119-123` |
| `infra/azure/modules/containerapps.bicep` | Params `aiEndpoint`, `aiDeployment` (default `''`). Add `INFINEVO_AI_ENDPOINT` and `INFINEVO_AI_DEPLOYMENT` to the **app** container's env only, beside `AZURE_CLIENT_ID` (`:429`), which the managed identity sign-in already needs |
| `infra/azure/parameters/dev.bicepparam`, `uat.bicepparam`, `prod.bicepparam` | `deployAi = false` written out, so switching it on is one visible line |
| `infra/azure/README.md` | A short "AI helper" section: the D-18 exception, the switch, the founder's steps |

**Model default:** the newest Sonnet-class Claude model in the Foundry catalogue when this
is built. It is a parameter. If nothing newer is confirmed at build time, use the Sonnet
model Microsoft's own starter kit deploys.

**Why Sweden Central by default:** it is one of the two regions Microsoft lists and it is
nearer to India than East US 2. The deployment is Global Standard either way, so the
region is where the resource lives, not a promise about where a request is processed.

## 5. What a merge to `main` does

| Workflow | On this merge | Evidence |
|---|---|---|
| `infra.yml` | Builds and lints every Bicep file, then runs `what-if`. **It never deploys** | `.github/workflows/infra.yml:38-58,74` |
| `deploy.yml` | Updates container images only. It runs no Bicep: `grep -c "az deployment" .github/workflows/deploy.yml` → 0 | `.github/workflows/deploy.yml:678-731` |

So this ticket can merge without waiting for the founder. With `deployAi = false` the
`what-if` shows no AI resource. The settings reach the app only when the founder runs the
Bicep, because container settings live in `containerapps.bicep` and the pipeline sets none
(`.github/workflows/deploy.yml:739`).

## 6. Standing rules

- [x] Creates no table, no migration, no money field.
- [x] No secret added: sign-in is by managed identity, keys are switched off on the resource.
- [x] `legacy/` untouched.
- [x] The single environment's `-dev` resources are live: nothing here changes them until `deployAi` is set to `true` and the founder deploys.

## 7. The founder's steps (not part of this ticket's done check)

| # | Step |
|---|---|
| 1 | Confirm the subscription may buy Claude; accept the Claude terms in Azure Marketplace |
| 2 | Set `deployAi = true`, the model name and version, and the budget in `dev.bicepparam` |
| 3 | Run the deployment (`infra/azure/deploy.sh`) and read the `what-if` first |
| 4 | Check from inside the network that the app reaches the endpoint (after `W-114.4`: one chat message from the test company) |

## 8. Verification

```bash
az bicep build --file infra/azure/main.bicep
az bicep lint  --file infra/azure/main.bicep
for f in infra/azure/modules/*.bicep; do az bicep build --file "$f" && az bicep lint --file "$f"; done
```

| Check | Expected | Result |
|---|---|---|
| Build and lint, root and every module | Exit 0, no new warning | not run |
| `grep -n "'centralindia'" infra/azure/main.bicep` | The `location` lock is still the only allowed value | not run |
| `grep -n "deployAi" infra/azure/parameters/*.bicepparam` | `false` in all three | not run |
| `infra.yml` on the pull request, `what-if` step | No AI resource listed while `deployAi = false` | not run |
| Built ARM JSON of `ai-foundry.bicep` | `publicNetworkAccess` is `Disabled`, `disableLocalAuth` is `true`, one role assignment, for the app identity only | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A point in the "Not confirmed" table turns out false at deploy time | Medium | Each row names what to do; the switch stays off until the founder has checked |
| The exception to `D-18` is forgotten and read later as a breach | Medium | Stated at the top here and in `infra/azure/README.md`; needs its own line in `07-decisions.md` |
| Someone widens the `location` list to make this work | Low | The spec forbids it; the verification greps for it |
| Model cost runs away | Low | The per-customer cap (`W-114.1`) and the budget alert here |

## 10. Rollback

Set `deployAi = false` and redeploy: the settings go empty and the backend falls back to
the stub client. Deleting the AI resource is a manual founder step.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `infra/` | Confirm the "Not confirmed" rows against Microsoft's pages and the starter kit; record the answers in the pull request |
| 2 | `infra/` | `ai-foundry.bicep`, the private endpoint and DNS changes |
| 3 | `infra/` | `main.bicep` params and wiring, `containerapps.bicep` settings, the three parameter files, README section |
