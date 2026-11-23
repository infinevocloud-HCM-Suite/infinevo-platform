# Feature: AI helper — usage and cost monitoring

| Field | Value |
|---|---|
| **Feature ID** | `W-114.24` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`ai`, `tenant`), `code/frontend/src/core/admin`, one alert rule in `infra/azure/modules/alerting.bicep` |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.4` on `main` (the gateway, the token counts on `AiModelResponse`) |
| **Build order** | Before the first real customer |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | one — token columns on `core.ai_usage` | 1 |
| Externally testable behaviour | The platform admin sees, for one customer, this month's AI requests, tokens and estimated cost | 1 |
| Frontend area | `core/admin` — the "AI helper" card on the tenant page | 1 |

Size **M**. One of each, so it stays one spec. The log line, the metrics and the 80% alert
feed that one behaviour and add no second module, migration or screen. The alert rule is one
resource in an existing Bicep file.

## Decisions

| Date | Who | Decision |
|---|---|---|
| 2026-11-23 | founder | (Research review) Record tokens, rounds, tools, duration, finish reason, filter stops, errors, cap and rate-limit hits per request. Keep monthly token totals per customer. Show requests, tokens and estimated cost to the platform admin. Alert at 80% of a customer's cap. **Never log the prompt, a tool result or the answer** |
| this spec | Claude | The per-request record is **one structured log line**, not a table. A table of every request would grow without limit and need its own deletion rule |
| this spec | Claude | Monthly totals go in three new columns on `core.ai_usage`, beside the request count |
| this spec | Claude | Cost is worked out when read, from the token totals and three price settings. No cost is stored, so a price correction needs no data fix |
| this spec | Claude | The 80% alert is driven by a log line, because logs are what reaches Azure Monitor today — see "Where metrics go" |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | The only usage number kept is a request count per customer and month. | `code/backend/core/src/main/java/com/infinevo/core/ai/AiUsage.java:35-36`; `code/backend/migration/src/main/resources/db/migration/core/V192__ai_switch_and_usage.sql:52-58` |
| 2 | Cost follows tokens, not requests. Nothing records tokens. | same table |
| 3 | The tenant page shows "Used this month: N of M" and nothing about cost. | `code/frontend/src/core/admin/TenantDetail.jsx:402-406` |
| 4 | Nobody is told when a customer nears its cap. The customer finds out at `429`. | `code/backend/core/src/main/java/com/infinevo/core/ai/AiAccessGateImpl.java:70` |
| 5 | `W-114.4` logs the session id, rounds, tool names, token counts and duration, with no fixed shape. | `W-114-4-model-gateway-masking-and-core-answers.md`, "Model access", row "Logged" |

## Where metrics go — checked in the code

| Question | Finding | Evidence |
|---|---|---|
| Does the backend have Micrometer? | Yes, with the Prometheus registry only | `code/backend/shared/pom.xml:72-73` |
| Is it exposed? | Yes, at `/actuator/prometheus` | `code/backend/app/src/main/resources/application.yml:109` |
| Does the app export to Application Insights? | **No.** The Application Insights resource is declared, but no container app is given its connection string and no exporter or agent is on the classpath | `infra/azure/main.bicep:225-229`; no match for `insights` in `infra/azure/modules/containerapps.bicep`; no `applicationinsights`, `azure-monitor` or `opentelemetry` in any `pom.xml` |
| Does anything scrape the Prometheus endpoint? | No match for `prometheus` or `scrape` under `infra/azure/modules` | — |
| Does any code publish its own metric today? | No `MeterRegistry` use under `code/backend` | these would be the first |
| What does reach Azure Monitor? | Container console logs, in Log Analytics. Every existing alert reads them | `infra/azure/modules/alerting.bicep:57,94` |

So: the metrics below are defined now and are readable at the endpoint. **They are on no
dashboard until an exporter exists — a separate infra ticket.** Everything this spec promises
(the admin card, the alert) works from the database and the log line.

## Scope

**In scope**

- One log line per AI request, with a fixed set of fields.
- Counters and a timer in Micrometer.
- Token totals per customer and month.
- Requests, tokens and estimated cost on the tenant page's "AI helper" card.
- A warning log line and an Azure alert when a customer passes 80% of its cap.

**Out of scope**

- Sending metrics to Application Insights or a dashboard — a separate infra ticket.
- Per-user usage, usage by day, a usage screen for the customer.
- Billing a customer for AI use.
- Any log of what was asked or answered.

## Flow

```
POST /api/v1/ai/sessions/{id}/messages   (W-114.4)
  --> every model round returns token counts --> summed for the request
  --> at the end, success or failure:
        AiUsageRepository.addTokens(tenant, month, input, output, cached)
        one log line "ai_request" + metrics
  --> AiAccessGate.consume() returned the count that crosses 80% of the cap
        --> one WARN log line "ai_cap_80" --> Azure alert --> mail

Platform admin --> tenant page --> GET /api/v1/tenants/{id}
  --> core.get_tenant_overview: the count and the three token totals
  --> TenantQueryService works out the estimated cost --> the "AI helper" card
```

## What is recorded for each request

One log line at `INFO`, logger `com.infinevo.core.ai.usage`, message `ai_request`, written
as JSON fields through the encoder the backend already uses (`code/backend/shared/pom.xml:68-69`).

| Field | Value |
|---|---|
| `tenantId`, `sessionId` | ids. No user id, no name |
| `outcome` | `ANSWERED`, `FILTERED`, `ROUND_LIMIT`, `MODEL_ERROR`, `TIMEOUT`, `CAP_REACHED`, `RATE_LIMITED`, `DISABLED` |
| `finishReason` | The model's last `finish_reason`: `stop`, `length`, `content_filter`, `tool_calls` |
| `rounds` | Model calls made, 1 to 5 |
| `tools` | Names of the tools called, in order. Names only — no arguments, no results |
| `inputTokens`, `outputTokens`, `cachedInputTokens` | Summed over the rounds. From the model's `usage`: `prompt_tokens`, `completion_tokens`, `prompt_tokens_details.cached_tokens` (0 when absent) |
| `durationMs` | Whole request |
| `filterStop` | `none`, `question`, `answer` |
| `errorKind` | For a failure: the HTTP status from Azure, or `timeout`. Never the error body |
| `deployment` | The deployment name in use |

**Never in the line, in a metric, or in any other log:** the user's text, the system prompt,
a tool's arguments or result, the answer, an employee's name.

**Metrics** — names fixed here so a later dashboard needs no code change. No tenant tag:
per-customer numbers come from the table.

| Metric | Type | Tags |
|---|---|---|
| `infinevo.ai.requests` | counter | `outcome` |
| `infinevo.ai.tokens` | counter | `kind` = `input`, `output`, `cached_input` |
| `infinevo.ai.tool.calls` | counter | `tool`, `ok` |
| `infinevo.ai.request.duration` | timer | `outcome` |
| `infinevo.ai.rounds` | distribution summary | — |

## Estimated cost

| Setting (`application.yml`) | Meaning | Default |
|---|---|---|
| `infinevo.ai.price.input-per-million` | Price of one million input tokens | `0.44` |
| `infinevo.ai.price.output-per-million` | Price of one million output tokens | `1.76` |
| `infinevo.ai.price.cached-input-per-million` | Price of one million cached input tokens | `0.44` — the input price, until the founder sets the real one |
| `infinevo.ai.price.currency` | Currency code shown beside the estimate | `USD` |

The defaults are the list prices the research review read for the current deployment. They
are settings because the price changes with the model.

`cost = (input − cached) × input price + cached × cached price + output × output price`,
each divided by one million. All `BigDecimal`, rounded half-up to two places at the end.
The card labels it **"Estimated"**. The Azure bill is the truth.

## The 80% warning

| Point | Rule |
|---|---|
| When | `consume()` returns a count equal to the first whole number at or above 80% of the cap. It is an equality, so it fires once a month per customer with no stored flag |
| What | One `WARN` line, logger `com.infinevo.core.ai.usage`, message `ai_cap_80`, fields `tenantId`, `used`, `cap`, `month` |
| Alert | One scheduled query rule on `ContainerAppConsoleLogs_CL` for `ai_cap_80`, on the pattern of the existing rules (`infra/azure/modules/alerting.bicep:40-57`), mailing the existing action group |
| On the card | From 80%: an orange tag "80% of the cap used". At 100%: a red tag "Cap reached" |
| A cap changed mid-month | If the new cap puts the count already past 80%, no line is written; the card's tag still shows. Accepted |

## Backend changes

Paths are under `code/backend/core/src/main/java/com/infinevo/core/`.

| Layer | File | Change |
|---|---|---|
| Entity | `ai/AiUsage.java` | Three read-only fields: `inputTokens`, `outputTokens`, `cachedInputTokens` (`long`) |
| Repository | `ai/AiUsageRepository.java` | **Add** `addTokens(tenantId, month, input, output, cached)`: one `UPDATE … SET input_tokens = input_tokens + :input …` on the month's row. The row exists, because `countOne` ran first (`:32-41`) |
| Service | `ai/AiUsageRecorder.java` | **New.** `record(AiRequestOutcome)`: adds the tokens, writes the log line, updates the metrics. Called once from `AiChatServiceImpl` in a `finally`, so a failed request is recorded with the tokens it used. A failure inside the recorder is logged and never fails the user's request |
| Record | `ai/AiRequestOutcome.java` | **New.** The fields of the table above |
| Service | `ai/AiChatServiceImpl.java` (`W-114.4`) | Sums the token counts of each `AiModelResponse`; calls the recorder. The refusals before the model (`DISABLED`, `CAP_REACHED`, `RATE_LIMITED`) are recorded with zero tokens |
| Service | `ai/AiAccessGateImpl.java` | After `countOne` (`:70`): when the returned count is the 80% mark, write `ai_cap_80` |
| Client | `ai/AzureOpenAiModelClient.java` (`W-114.4`) | Reads `usage.prompt_tokens_details.cached_tokens` into `AiModelResponse` beside the two counts it already holds |
| Config | `ai/AiPriceProperties.java` | **New.** The four settings, prices as `BigDecimal` |
| Query | `tenant/TenantOverview.java`, `tenant/TenantQueryService.java` | `TenantOverview` gains `ai_input_tokens_this_month`, `ai_output_tokens_this_month`, `ai_cached_input_tokens_this_month` (read from the functions, `TenantQueryService.java:112,187`) and `ai_estimated_cost_this_month` (a decimal string) with `ai_cost_currency`, worked out in `TenantQueryService` |
| Settings | `code/backend/app/src/main/resources/application.yml` | The four `infinevo.ai.price.*` lines |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `GET` | `/api/v1/tenants`, `/api/v1/tenants/{id}` | — | `TenantOverview` now also carries the five fields above | unchanged — platform staff |

No new endpoint. The customer's own `GET /api/v1/ai/status` (`W-114.1`) is not changed:
a customer sees requests, not tokens or cost.

## Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/core/admin/TenantDetail.jsx` | In the "AI helper" card (`:372`), under "Used this month" (`:402-406`): "Tokens this month: 1,24,500 in · 18,200 out" and "Estimated cost this month: USD 0.09". The two tags of "The 80% warning". Missing fields read as 0, so the card works against an older backend, as the card already does (`:66-67`) |
| `code/frontend/src/core/admin/TenantDetail.test.jsx` | Extend |

**Feedback line.** When `W-114.25` is on `main`, this card also shows its counts — "Answers
rated this month: 41 up · 6 down" — from `GET /api/v1/tenants/{id}/ai-feedback`. A `404`
hides the line. Whichever of the two parts is built second adds the line.

**Routes added:** none.

## Database changes

> Flyway only. Read `code/backend/migration/README.md` first: every statement names its schema.

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V<next free version at build time>__ai_usage_tokens.sql` (`core`) | `core.ai_usage` gains `input_tokens`, `output_tokens`, `cached_input_tokens`, each `BIGINT NOT NULL DEFAULT 0 CHECK (… >= 0)`. `core.list_tenants()` and `core.get_tenant_overview(uuid)` return the three new columns, added at the end: drop and recreate both, as `V192__ai_switch_and_usage.sql` did for the count | yes — the table has `tenant_id` and its policy already (`V192:54,64-73`) | yes — the columns can be dropped; nothing else reads them |

- [x] No new table. The existing table keeps its `tenant_id`, its policy and its unique index (`V192:61`)
- [x] `app_user` still may not delete from `core.ai_usage` (`V192:80`); `UPDATE` is already granted (`V192:76`)
- [x] No money column: tokens are whole numbers. Cost is worked out in `BigDecimal` and never stored
- [x] No new index: the one lookup is tenant and month
- [x] Expand only: three columns with defaults. The previous release ignores them, and its row mapper ignores extra columns at the end
- [x] Stays inside `core`

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../ai/AiUsageRecorderTest.java` | Tokens of three rounds are summed; a model error still records the tokens used and `outcome = MODEL_ERROR`; a recorder failure does not reach the caller; metrics are counted on a `SimpleMeterRegistry` |
| Unit | `core/src/test/.../ai/AiUsageLogLineTest.java` | With a log capture: the `ai_request` line has every field of the table. **A request whose question, tool result and answer each hold a marker word leaves that word in no log line at any level** |
| Unit | `core/src/test/.../ai/AiAccessGateImplTest.java` (extend) | Cap 500: the 400th request writes `ai_cap_80` once; the 399th and 401st do not. Cap 3: the mark is 3 |
| Unit | `core/src/test/.../tenant/AiCostEstimateTest.java` | The formula on fixed numbers, in `BigDecimal`; cached tokens priced apart; zero tokens give `0.00`; no `double` in the path |
| Unit | `core/src/test/.../ai/AzureOpenAiModelClientTest.java` (extend) | `cached_tokens` is read; a reply without it gives 0 |
| Integration | `core/src/test/.../ai/AiUsageTokensIT.java` | Two requests with scripted token counts add up on the month's row; tenant B's row is untouched; `core.get_tenant_overview` returns the three totals; a customer tenant cannot read another's totals |
| Component | `code/frontend/src/core/admin/TenantDetail.test.jsx` (extend) | The tokens and cost lines; the 80% tag at 400 of 500 and not at 399; "Cap reached" at 500; fields missing → zeros and no tag |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
cd code/frontend && npx vitest run src/core/admin && npm run lint && npm run build
az bicep build --file infra/azure/modules/alerting.bicep && az bicep lint --file infra/azure/modules/alerting.bicep
git grep -nE '^[^#]*ddl-auto[[:space:]]*[:=]' -- code/      # expect nothing
```

| Check | Expected | Result |
|---|---|---|
| The commands | All pass | not run |
| One chat message, stub scripted with 1,200 input and 150 output tokens | The month's row: `request_count` + 1, `input_tokens` + 1200, `output_tokens` + 150 | not run |
| The app log for that message | One `ai_request` line with `rounds`, `tools`, the token counts, `durationMs`, `outcome: ANSWERED` | not run |
| Grep the app log for the question text and the answer text | Not found | not run |
| `GET /actuator/prometheus` | Holds `infinevo_ai_requests_total` and `infinevo_ai_tokens_total` | not run |
| Tenant page as platform admin | "Tokens this month" and "Estimated cost this month: USD …" | not run |
| The same customer's own `GET /api/v1/ai/status` | Unchanged: no token or cost field | not run |
| A customer at 400 of 500 | One `ai_cap_80` line; the card shows the orange tag | not run |
| Stub scripted to fail after one round | Tokens of that round are added; `outcome: MODEL_ERROR` | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A later change logs the prompt or an answer "for debugging" | Medium | `AiUsageLogLineTest` fails on any marker word in any log line |
| The estimate differs from the Azure bill | Certain, slightly | Labelled "Estimated"; prices are settings; the Azure budget alert of `W-114.2` watches the real bill |
| The metrics are defined and nobody sees them | Certain, until an exporter exists | Said plainly above; the card and the alert do not depend on them |
| The token update fails after the answer was produced | Low | The answer is still returned; the failure is logged; the request count was already taken by `consume()` |
| The 80% line is missed because the cap changed mid-month | Low | The card's tag is worked out from the live numbers on every load |
| Tenant ids in logs | Low | An id, not a name; the log workspace is platform staff only |

## Rollback

Revert the commit. The three columns stay, unused and harmless; the card goes back to the
request count. Remove the alert rule with the next Bicep run.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `migration` | The token columns and the two recreated functions |
| 2 | `core` (`ai`) | `AiUsageRecorder`, the log line, the metrics, `addTokens`, cached tokens in the client, the 80% line, tests |
| 3 | `core` (`tenant`) | The overview fields, the price settings, the cost estimate, tests |
| 4 | frontend `core/admin` | The card lines and tags, tests |
| 5 | `infra/` | The `ai_cap_80` alert rule in `alerting.bicep` |
