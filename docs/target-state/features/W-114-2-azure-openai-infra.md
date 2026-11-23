# W-114.2 — Azure OpenAI: the Azure side of the AI helper

| Field | Value |
|---|---|
| **Work item** | `W-114.2` |
| **Kind** | Infra |
| **Size / skill** | M — INFRA (it touches `main.bicep`, three existing modules and one new one) |
| **Owner** | Claude (`dev-claude-w114`) |
| **Blocked by** | nothing to write and merge. **To deploy:** the founder's Azure steps in §7 |
| **Blocks** | A real model answer in `W-114.4`. Nothing else: `W-114.3` onward build and test against the stub client |
| **Decisions** | `D-18` (India region) — kept: the resource sits in India. `D-61` — the model resource stays in India where the model is offered there; processing may happen outside India under a Global deployment |
| **Gaps addressed** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |

## Founder decisions (2026-11-23)

| Question | Answer |
|---|---|
| Model | A GPT model on Azure OpenAI. Not Claude (changed the same day) |
| Data location | Keep the resource in India if the model is offered there. Processing outside India is accepted for the AI helper: PAN, Aadhaar and bank account numbers are masked before sending (`W-114.4`); salary and leave figures are sent |
| Who deploys | The founder. This ticket delivers Bicep that builds and lints |

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
| 2 | Every resource is forced into Central India by one `location` param. Azure OpenAI's India models are listed for **South India**, so the AI resource needs its own region param. | `infra/azure/main.bicep:11-19`; §3 |
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

- New module `infra/azure/modules/ai-openai.bicep`: one Azure OpenAI resource, one GPT deployment, the role assignment for the app's identity, a monthly budget alert.
- A private endpoint and the DNS zone for it.
- Two settings on the app container: `INFINEVO_AI_ENDPOINT`, `INFINEVO_AI_DEPLOYMENT` (Spring reads them as `infinevo.ai.endpoint`, `infinevo.ai.deployment`).
- A switch, `deployAi`, **off by default**, so merging this changes nothing in Azure.
- Region, model, version and deployment type are parameters. Moving between the three choices in §3.1 is a parameter change, not a code change.

**Out of scope**

- The Java client — `W-114.4`.
- Running the deployment and checking quota — the founder (§7).
- The per-customer cap — `W-114.1`. The budget alert here is a second, Azure-side guard.

## 3. What Microsoft's documents say

| Point | Finding | Source |
|---|---|---|
| Models in South India, **Global Standard** | `gpt-4.1` (version `2025-04-14`), `gpt-4.1-mini`, `gpt-4.1-nano`, `gpt-5` (version `2025-08-07`), `gpt-5-mini`, `gpt-5-nano` are listed for `southindia` | [Region availability for Foundry Models sold by Azure](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/models-sold-directly-by-azure-region-availability) |
| Models in South India, **Standard (regional)** | Only `gpt-4.1-mini` was found listed for `southindia` | same page |
| Central India | Not found in any table read | same page |
| Where a request is processed | **Standard:** within the Azure geography you chose (it may move between regions inside that geography). **Data Zone Standard:** inside a Microsoft-defined zone. **Global Standard:** in any geography where the model is deployed | [Deployment types](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/deployment-types) |
| Where data rests | For every deployment type, data stored at rest stays in the Azure geography of the resource. That includes the abuse-monitoring store | [Deployment types](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/concepts/deployment-types) · [Data, privacy and security](https://learn.microsoft.com/en-us/azure/foundry/responsible-ai/openai/data-privacy) |
| Sign-in without a key | A managed identity is given the role **`Cognitive Services OpenAI User`** on the resource; allow up to five minutes after assigning it | [Entra ID authentication for Azure OpenAI](https://learn.microsoft.com/en-us/azure/foundry-classic/openai/how-to/managed-identity) |
| Switching keys off | The Bicep property is `disableLocalAuth`; it can take a few minutes to take effect | [Configure keyless authentication](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/how-to/configure-entra-id) |
| Private endpoint | Sub-resource (group id) `account`; DNS zone `privatelink.openai.azure.com` for the `*.openai.azure.com` endpoint | [Private endpoint DNS zone values](https://learn.microsoft.com/en-us/azure/private-link/private-endpoint-dns) · [Azure OpenAI in a virtual network](https://learn.microsoft.com/en-us/azure/ai-foundry/openai/how-to/network?view=foundry-classic) |
| Quota | Set per subscription, region, model and deployment type; more is asked for with a quota request form | [Quotas and limits](https://learn.microsoft.com/en-us/azure/foundry/openai/quotas-limits) |
| API | The `/openai/v1` route needs no `api-version` | [Azure OpenAI v1 API](https://learn.microsoft.com/en-us/azure/foundry/openai/api-version-lifecycle) |

### 3.1 The region rule

| Choice | Resource | Deployment | Request processed | Use when |
|---|---|---|---|---|
| **1 — default** | `southindia` | `Standard`, `gpt-4.1-mini` | Inside India | Always, unless the model proves too weak for the helper |
| 2 | `southindia` | `GlobalStandard`, `gpt-5-mini` or `gpt-4.1` or `gpt-5` | Anywhere the model runs; data at rest stays in India | The founder wants a stronger model. Allowed by `D-61` |
| 3 | outside India (`swedencentral`, `eastus2`) | `GlobalStandard` | Anywhere; data at rest outside India | Only if South India has no quota at all. Needs the founder's word first — it goes beyond `D-61` |

Choices 1 and 2 keep `D-18` whole for data at rest: South India is in the India geography.
Choice 1 also keeps processing in India, so it needs no exception at all.

### Not confirmed — the implementer checks each before writing the Bicep

| Point | Why it matters | If the answer is no |
|---|---|---|
| `gpt-4.1-mini` is still listed for `Standard` in `southindia`, and its version string (assumed `2025-04-14`, the date the page gives for `gpt-4.1`) | Choice 1 depends on it; the search returned a summary of the table, not the table | Use choice 2 |
| A newer generally-available GPT model than `gpt-5` exists for `southindia`. Community posts name `gpt-5.5` and later; no Microsoft table read confirms them for India | The default is a parameter, not a fact | Founder sets `aiModelName` and `aiModelVersion` |
| Tool (function) calling on the chosen model. The model router page lists function tools for Chat Completions; the per-model page was not read | The helper cannot work without it | Pick another model from the same table |
| The founder's subscription has quota for the chosen model, type and region. Community answers say some full-size `gpt-5` models need an access request first; nothing found says `gpt-4.1-mini` does | A deployment with zero quota fails | Founder files the quota request (§7 step 1) |
| A private endpoint in the Central India network can reach a resource in South India | The network is in Central India and stays there | Stop and ask the founder — an open endpoint is their call |
| Whether an India resource is created as kind `OpenAI` (endpoint `*.openai.azure.com`) without trouble, or must be kind `AIServices` | It decides the endpoint host and the DNS zone | Use `AIServices` and add the zones `privatelink.cognitiveservices.azure.com` and `privatelink.services.ai.azure.com` |
| An APAC data zone exists and covers India | It would be a middle option between choices 1 and 2 | Ignore it; the rule above stands |
| The Entra token scope for the `/openai/v1` route. The classic guide uses `https://cognitiveservices.azure.com/.default`; the newer Responses guide uses `https://ai.azure.com/.default` | Wrong scope means `401` | It is a setting in `W-114.4` (`infinevo.ai.token-scope`); change the value, not the code |

## 4. Changes

| File | Change |
|---|---|
| `infra/azure/modules/ai-openai.bicep` | **New.** Params: `environment`, `aiLocation`, `aiModelName`, `aiModelVersion`, `aiDeploymentSku`, `aiCapacity`, `appPrincipalId`, `alertEmail`, `aiMonthlyBudget`, `tags`. Resources: the account `oai-infinevo-${environment}` (`Microsoft.CognitiveServices/accounts`, kind `OpenAI`) with a custom subdomain, `publicNetworkAccess: 'Disabled'`, `disableLocalAuth: true` (no keys exist, so none can leak); one deployment (`accounts/deployments`, model format `OpenAI`) with the sku from `aiDeploymentSku`; the role assignment `Cognitive Services OpenAI User` for `id-app-${environment}` only — the worker never calls the model; a monthly budget filtered to this resource, with mail at 80% and 100% to `alertEmail`. Outputs: `accountId`, `endpoint` (`https://<name>.openai.azure.com`), `deploymentName` |
| `infra/azure/main.bicep` | New params `deployAi bool = false`; `aiLocation` (`@allowed(['southindia','centralindia','swedencentral','eastus2'])`, default `southindia`); `aiModelName` (default `gpt-4.1-mini`); `aiModelVersion`; `aiDeploymentSku` (`@allowed(['Standard','DataZoneStandard','GlobalStandard'])`, default `Standard`); `aiCapacity`; `aiMonthlyBudget`. Module `aiOpenAi` at `envRg`, `if (deployAi)`. A private endpoint module instance on the pattern of `:448-460`, also `if (deployAi)`. Pass `aiEndpoint` and `aiDeployment` into `containerApps` (`:341-384`), empty when `deployAi` is false. **The `location` param and its `@allowed` list (`:11-19`) are not changed** |
| `infra/azure/modules/private-endpoint.bicep` | Add `'account'` to the allowed `groupId` list (`:13-21`) |
| `infra/azure/modules/private-dns.bicep` | The zone `privatelink.openai.azure.com`, linked to the VNet like the vault zone (`:101`), with an output beside `:119-123` |
| `infra/azure/modules/containerapps.bicep` | Params `aiEndpoint`, `aiDeployment` (default `''`). Add `INFINEVO_AI_ENDPOINT` and `INFINEVO_AI_DEPLOYMENT` to the **app** container's env only, beside `AZURE_CLIENT_ID` (`:429`), which the managed identity sign-in already needs |
| `infra/azure/parameters/dev.bicepparam`, `uat.bicepparam`, `prod.bicepparam` | `deployAi = false` written out, so switching it on is one visible line |
| `infra/azure/README.md` | A short "AI helper" section: the region rule in §3.1, `D-61`, the switch, the founder's steps |

**Model default:** `gpt-4.1-mini` on a `Standard` deployment in `southindia` — the one
combination found that keeps both the data and the processing in India. It is checked
again at build time against Microsoft's region table. The newest generally-available GPT
model confirmed for South India is `gpt-5` (`2025-08-07`), on `GlobalStandard` only.

**Deploying to a region the `aiLocation` list allows outside India** is choice 3 and needs
the founder's word. The list holds those values so that move is a parameter, not a code change.

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
| 1 | In the Azure portal, check the subscription has quota for the chosen model, deployment type and region (default: `gpt-4.1-mini`, `Standard`, South India). If it shows zero, file the quota request. No marketplace terms are needed for Azure OpenAI |
| 2 | Set `deployAi = true`, and the model name, version and budget, in `dev.bicepparam` |
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
| `grep -n "'centralindia'" infra/azure/main.bicep` | The `location` lock still allows only `centralindia`; `aiLocation` is a separate param defaulting to `southindia` | not run |
| `grep -n "deployAi" infra/azure/parameters/*.bicepparam` | `false` in all three | not run |
| `infra.yml` on the pull request, `what-if` step | No AI resource listed while `deployAi = false` | not run |
| Built ARM JSON of `ai-openai.bicep` | `kind` is `OpenAI`, `publicNetworkAccess` is `Disabled`, `disableLocalAuth` is `true`, one role assignment (`Cognitive Services OpenAI User`), for the app identity only | not run |
| `grep -rniE "claude|anthropic" infra/azure` | 0 matches | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A point in the "Not confirmed" table turns out false at deploy time | Medium | Each row names what to do; the switch stays off until the founder has checked |
| `gpt-4.1-mini` is too weak for the action parts (`W-114.8`, `W-114.17`–`W-114.20`) | Medium | Move to choice 2 by changing two parameters; `D-61` already allows it |
| Someone moves the resource outside India without asking | Low | The default is `southindia`; choice 3 is marked as the founder's call here and in the README |
| Someone widens the `location` list to make this work | Low | The spec forbids it; the verification greps for it |
| Azure keeps prompts for abuse monitoring | Certain, by design | That store sits in the resource's geography (India); only masked text is ever sent |
| Model cost runs away | Low | The per-customer cap (`W-114.1`) and the budget alert here |

## 10. Rollback

Set `deployAi = false` and redeploy: the settings go empty and the backend falls back to
the stub client. Deleting the AI resource is a manual founder step.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `infra/` | Confirm the "Not confirmed" rows against Microsoft's pages; record the answers in the pull request |
| 2 | `infra/` | `ai-openai.bicep`, the private endpoint and DNS changes |
| 3 | `infra/` | `main.bicep` params and wiring, `containerapps.bicep` settings, the three parameter files, README section |
