# Feature: AI helper switch and monthly cap per customer

| Field | Value |
|---|---|
| **Feature ID** | `W-114.1` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core` (`subscription`, `tenant`, new `ai`), `code/backend/migration`, `code/frontend` (`src/core/admin`) |
| **Related gaps** | none in `GAP_INVENTORY.md`; the frozen system has no AI helper |
| **Status** | Ready |
| **Blocked by** | nothing |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 1 — next free version at build time | 1 |
| Externally testable behaviour | The platform admin switches the AI helper on for a customer with a monthly cap, and the customer's status call reports it | 1 |
| Frontend area | `core/admin` | 1 |

## Where this sits

`W-114` (Infinevo HCM AI helper) is split into ten parts. This is the first. It stores the
switch and the cap and counts usage. It calls no AI model and shows no chat.

| Part | What | Area |
|---|---|---|
| **`W-114.1`** | **Switch, monthly cap and usage count — this spec** | `core` |
| `W-114.2` | Claude on Azure (Microsoft Foundry), private endpoint, Key Vault secret | `infra` |
| `W-114.3` | Chat session tables and the tool plug-in point | `core` |
| `W-114.4` | Model gateway with masking of PAN, Aadhaar and bank numbers; Core questions | `core` |
| `W-114.5` | Plain chat drawer | frontend `shell` |
| `W-114.6` | HRMS read-only tools | `hrms` |
| `W-114.7` | Payroll read-only tools | `payroll` |
| `W-114.8` | Apply leave: propose, confirm, re-check permission, audit | `core` |
| `W-114.9` | Confirm and Cancel cards | frontend `shell` |
| `W-114.10` | Nightly deletion of chats older than 30 days | `worker` |
| `W-114.11` | Help library: how-to articles we write and ship; the helper answers "how do I…" only from them, with a link to the screen | `core` |
| `W-114.12` | Role guide: what each role can access and is supposed to do, from the user's real permissions | `core` |
| `W-114.13` | Screen awareness: knows the screen the user is on; "take me there" links to screens the user may open | frontend `shell` |
| `W-114.14` | Explain my payslip and TDS | `payroll` |
| `W-114.15` | Explain my leave balance | `core` |
| `W-114.16` | Setup guide for a new tenant admin, from the setup checklist | `core` |
| `W-114.17` | Employee self-service: apply WFH, withdraw or cancel own leave, with Confirm | `core` |
| `W-114.18` | Manager approvals: list what is waiting, approve or reject with Confirm | `core` |
| `W-114.19` | HR team answers: who is on leave, who joined, within what HR may see | `core` |
| `W-114.20` | Create employee from chat, with Confirm | `core` |
| `W-114.21` | Pay run summary and step guide. Read-only: the helper never starts, approves or pays a run | `payroll` |
| `W-114.22` | Starter questions and tasks per role, shown when the chat opens. A starter shows only when the user has the permission and the part behind it is on `main` | `core` (the list), frontend `shell` (the chips) |

Order: `.1` → `.2` → `.3` → `.4` → `.5` → `.6` and `.7` → `.8` → `.9` → `.10`, then
understand (`.11` to `.16`, then `.22`), then do (`.17` to `.21`).

## Decisions (founder)

| Date | Question | Answer |
|---|---|---|
| 2026-11-23 | The split | Ten parts, then twelve more (`.11` to `.22`) so the helper explains the system and does tasks |
| 2026-11-23 | Where help answers come from | Articles we write and ship with the product, plus the user's real permissions. The helper does not describe a screen or step that is not in them |
| 2026-11-23 | Pay run | Summary and step guidance only. The officer presses every button on the pay run screen |
| 2026-11-23 | Order of the new parts | Understand first, then do |
| 2026-11-23 | Migration numbers | No number is fixed in a spec; the developer takes the next free version when building |
| 2026-11-23 | AI model | Claude on Microsoft Foundry, not `gpt-4o`. Processing outside India is accepted: PAN, Aadhaar and bank account numbers are masked before sending; salary and leave figures are sent. Region and price are confirmed in `W-114.2` |
| 2026-11-23 | Chat deletion is the last part | The order stays. **The helper is not switched on for any real customer until `W-114.10` is on `main`.** Only the test company uses it before then |
| 2026-11-16 | Default | Off for every customer. Only the platform admin switches it on and sets the cap |
| 2026-11-16 | Off or over the cap | Off answers `403`; over the cap answers `429` |

Decided in this spec, for the founder to overrule:

| Point | Choice | Why |
|---|---|---|
| Where the switch and cap live | `core.subscription`, beside `employee_limit` | They are part of the deal with the customer, set by the same person on the same page (`V188__subscription_plan.sql:15-18`). The split proposal said `core.tenant` |
| Where usage is counted | A new table `core.ai_usage`, one row per customer and month | The customer's own request must add to the count. `core.subscription` is written only by platform staff through `SECURITY DEFINER` functions; a counter there would need a new write path for every user |
| Cap when switched on | Required, a whole number above 0 | An AI model costs money per request; "on with no cap" should not be possible by accident |
| What one request is | One chat message sent by a user. Tool calls inside it are not counted. A message that fails at the model is still counted | Simple to explain on an invoice |
| Month | Calendar month in UTC. A new month starts a new row; nothing resets | No job needed |

## Problem

| # | Today | Evidence |
|---|---|---|
| 1 | Nothing records whether a customer may use the AI helper, or how much. | `core.subscription` holds status, modules, plan, support plan and employee limit only: `code/backend/migration/src/main/resources/db/migration/core/V188__subscription_plan.sql:15-23` |
| 2 | The tenant page has Modules, Plan and Status cards and nothing about AI. | `code/frontend/src/core/admin/TenantDetail.jsx:257,275,328` |
| 3 | The later parts need one place to ask "may this customer make an AI request now". | no `ai` package under `code/backend/core/src/main/java/com/infinevo/core/` |

## Scope

**In scope**

- Two columns on `core.subscription`: the switch and the cap.
- `core.ai_usage` and an atomic "count one request" step that cannot pass the cap.
- `AiAccessGate`, the one check the later parts call.
- `PUT …/subscription/ai` for the platform admin; `GET /api/v1/ai/status` for the signed-in customer.
- An "AI helper" card on the tenant page.

**Out of scope**

- Any call to an AI model, any chat endpoint, any chat screen — `W-114.2` to `W-114.5`.
- A customer-side screen for the switch. The tenant admin cannot turn it on.
- Billing for usage — `W-94`.
- Blocking a suspended customer. Chat is a write, so the `W-85.1` guard covers it once that is on `main`.

## Flow

```
Platform admin --> TenantDetail "AI helper" card --> PUT /api/v1/tenants/{id}/subscription/ai
               --> SubscriptionServiceImpl.updateAi --> core.set_subscription_ai() --> core.subscription

Signed-in user --> GET /api/v1/ai/status --> AiAccessGate.status() --> core.subscription + core.ai_usage

W-114.4 (later) --> AiAccessGate.consume() --> off: 403 AI_DISABLED
                                           --> at the cap: 429 AI_CAP_REACHED
                                           --> else: count + 1, go on
```

## Database changes

| Migration | Change | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V…__ai_switch_and_usage.sql` — next free version at build time | See the steps below | yes | yes — expand only |

| # | Step |
|---|---|
| 1 | `ALTER TABLE core.subscription ADD COLUMN ai_enabled BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN ai_monthly_request_cap INTEGER NULL` |
| 2 | `CHECK (ai_monthly_request_cap IS NULL OR ai_monthly_request_cap > 0)` and `CHECK (NOT ai_enabled OR ai_monthly_request_cap IS NOT NULL)` |
| 3 | `core.set_subscription_ai(p_tenant_id uuid, p_enabled boolean, p_cap integer)`, `SECURITY DEFINER`, `SET search_path = pg_catalog, pg_temp`, raises when no row is found; `REVOKE EXECUTE … FROM PUBLIC`, `GRANT EXECUTE … TO app_user`. Copy the shape of `core.set_subscription_plan` (`V188__subscription_plan.sql:27-53`) |
| 4 | `CREATE TABLE core.ai_usage` — `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id) ON DELETE CASCADE`, `usage_month DATE NOT NULL` (first day of the month), `request_count INTEGER NOT NULL DEFAULT 0 CHECK (request_count >= 0)`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` |
| 5 | `CREATE UNIQUE INDEX uk_ai_usage_tenant_month ON core.ai_usage (tenant_id, usage_month)` |
| 6 | `ALTER TABLE core.ai_usage ENABLE ROW LEVEL SECURITY` and the standard `tenant_isolation` policy, exactly as `code/backend/migration/README.md:89-134` gives it |
| 7 | `GRANT SELECT, INSERT, UPDATE ON core.ai_usage TO app_user` |
| 8 | `core.list_tenants()` and `core.get_tenant_overview(uuid)` gain `ai_enabled`, `ai_monthly_request_cap`, `ai_usage_this_month`. The OUT columns change, so drop and recreate both, as `V188__subscription_plan.sql:56-60` did |

- [x] `tenant_id` and an RLS policy on the new table
- [x] Unique index leads with `tenant_id`; no other lookup exists
- [x] No money column
- [x] Expand only — nothing dropped or renamed; the old release ignores the extra columns
- [x] Every statement names `core.`

## Backend changes

All paths are under `code/backend/core/src/main/java/com/infinevo/core/`.

| Layer | File | Change |
|---|---|---|
| Entity | `subscription/Subscription.java` | `aiEnabled` (`boolean`), `aiMonthlyRequestCap` (`Integer`, nullable) |
| DTO | `subscription/AiSettingsUpdateRequest.java` | **New.** `record(boolean enabled, Integer monthlyRequestCap)` |
| DTO | `subscription/SubscriptionResponse.java` | Adds `aiEnabled`, `aiMonthlyRequestCap` after `supportPlan` (`:20-21`) |
| Service | `subscription/SubscriptionService.java`, `SubscriptionServiceImpl.java` | `updateAi(UUID tenantId, AiSettingsUpdateRequest)`, built like `updatePlan` (`:251-260`): refuses the platform tenant, runs inside `asTenant` (`:319`), calls `core.set_subscription_ai` the way `:284` calls the plan function. `enabled = true` with no cap, or a cap below 1, throws `IllegalArgumentException` |
| Controller | `subscription/SubscriptionController.java` | `PUT /ai`, `@RequiresAction("core.tenant.provision")`, `requirePlatformTenant()` — the `PUT /plan` pattern (`:71-76`). The existing handler turns `IllegalArgumentException` into `400` (`:89`) |
| DTO, query | `tenant/TenantOverview.java`, `tenant/TenantQueryService.java` | The three new columns from step 8 |
| Entity | `ai/AiUsage.java` | **New.** Maps `core.ai_usage` |
| Repository | `ai/AiUsageRepository.java` | **New.** `findByTenantIdAndUsageMonth`; and `countOne(tenantId, month, cap)` — one native statement: `INSERT … VALUES (…, 1) ON CONFLICT (tenant_id, usage_month) DO UPDATE SET request_count = core.ai_usage.request_count + 1, updated_at = now() WHERE core.ai_usage.request_count < :cap RETURNING request_count`. No row returned means the cap is reached |
| Service | `ai/AiAccessGate.java`, `AiAccessGateImpl.java` | **New.** `status()` returns `AiStatus(enabled, monthlyRequestCap, usedThisMonth, month)` for the bound tenant; a tenant with no subscription row is `enabled = false`. `consume()` throws `AiDisabledException` when off, else runs `countOne` and throws `AiCapReachedException` when it returns no row |
| Exceptions | `ai/AiDisabledException.java`, `ai/AiCapReachedException.java` | **New.** |
| Controller | `ai/AiStatusController.java` | **New.** `GET /api/v1/ai/status`, any signed-in user. Its own handlers: `AiDisabledException` → `403 AI_DISABLED`, `AiCapReachedException` → `429 AI_CAP_REACHED`. `ApiError` is an enum in `shared` (`code/backend/shared/src/main/java/com/infinevo/shared/error/ApiError.java`) and has neither code; to stay inside `core`, the handlers build `ApiErrorResponse` through its record constructor with the code as text, from one helper `ai/AiErrorResponses.java`. Handlers in this codebase are per controller, so `W-114.4`'s controller declares the same two handlers and calls the same helper |

`countOne` is the whole cap check. It is one statement, so two requests at the same moment
cannot both take the last place.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| `PUT` | `/api/v1/tenants/{id}/subscription/ai` | `{ "enabled": true, "monthlyRequestCap": 500 }` | `SubscriptionResponse` with the two new fields | `core.tenant.provision`, platform tenant only |
| `GET` | `/api/v1/ai/status` | — | `{ "enabled": true, "monthlyRequestCap": 500, "usedThisMonth": 12, "month": "2026-11" }` | signed in |
| `GET` | `/api/v1/tenants`, `/api/v1/tenants/{id}` | — | `TenantOverview` now carries `ai_enabled`, `ai_monthly_request_cap`, `ai_usage_this_month` | unchanged |

Switching off keeps the cap and the usage rows. Lowering the cap below this month's count is
allowed; the next request answers `429`.

## Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/core/admin/tenantService.js` | `setAi(id, { enabled, monthlyRequestCap })` → `PUT /v1/tenants/{id}/subscription/ai`, on the `setPlan` pattern (`:112`) |
| `code/frontend/src/core/admin/TenantDetail.jsx` | New "AI helper" card after Plan (`:275-327`), hidden for the platform tenant: a `Switch` (`aria-label` `AI helper`), an `InputNumber` monthly cap (min 1, required when the switch is on, `aria-label` `Monthly request cap`), the line "Used this month: N of M", one Save button `btn-save-ai`. Switching on asks: "The AI helper sends this customer's questions to the AI model. Switch on?" |

**Routes added:** none.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/src/test/.../subscription/SubscriptionServiceTest.java` (extend) | `updateAi` refuses the platform tenant; refuses on with no cap; refuses cap `0`; calls the function when valid |
| Unit | `core/src/test/.../ai/AiAccessGateImplTest.java` | Off throws `AiDisabledException` and counts nothing; no subscription row is off; at the cap throws `AiCapReachedException`; below the cap counts one |
| Integration | `core/src/test/.../ai/AiUsageIT.java` | `countOne` with cap 3 succeeds three times and returns nothing the fourth; twenty parallel calls with cap 10 leave `request_count = 10`; a new month starts at 1; tenant B cannot read tenant A's row as `app_user` |
| Integration | `core/src/test/.../subscription/SubscriptionAiIT.java` | `PUT /ai` from the platform tenant writes both columns; from a customer tenant answers `403`; `GET /api/v1/ai/status` echoes them; `core.list_tenants()` returns the three new columns |
| Component | `code/frontend/src/core/admin/TenantDetail.test.jsx` (extend) | Card hidden for the platform tenant; Save disabled when on with no cap; Save calls `setAi`; usage line reads `12 of 500` |

## Verification

```bash
cd code/backend && ./mvnw -B -pl migration,core -am verify
cd code/frontend && npx vitest run src/core/admin && npm run lint && npm run build
```

| Check | Expected | Result |
|---|---|---|
| The migration applies on the current `main` schema | `Successfully applied 1 migration`; `\d core.subscription` shows two new columns; `\d core.ai_usage` shows RLS enabled | not run |
| Every existing customer after the migration | `ai_enabled = false`, cap `NULL` | not run |
| `PUT …/subscription/ai` as platform admin, `{ "enabled": true, "monthlyRequestCap": 500 }` | `200`, body carries both | not run |
| Same call as a tenant admin | `403` | not run |
| `{ "enabled": true }` with no cap; `{ "enabled": true, "monthlyRequestCap": 0 }` | `400` both | not run |
| `GET /api/v1/ai/status` as an employee of that customer | `enabled: true`, `monthlyRequestCap: 500`, `usedThisMonth: 0` | not run |
| `GET /api/v1/ai/status` as an employee of another customer | `enabled: false` | not run |
| `AiUsageIT` parallel case | `request_count = 10`, ten refusals | not run |
| Tenant page, AI helper card | Switch, cap and "Used this month: 0 of 500" show; Save stores them | not run |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-86` and `W-116` also recreate `core.list_tenants()` or add subscription functions | Medium | Whichever merges second rebuilds its function from the one then on `main`; the migration has no fixed number |
| The helper is switched on for a real customer before chats are deleted (`W-114.10`) | Low | Founder rule above; the switch is off by default and only platform staff can change it |
| A failed model call still uses one request | Certain | Stated in the decisions; the cap is a cost guard, not a bill |
| Twelve rows a year per customer in `core.ai_usage` are never deleted | Certain, harmless | They are the usage history `W-94` may bill from |

## Rollback

Expand only. Revert the application; the columns and the table stay and nothing reads them.
Every customer is `ai_enabled = false` unless a platform admin changed it.

## Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `migration` | The one script, steps 1-8 |
| 2 | `core` | Subscription fields, `updateAi`, `PUT /ai`, tenant overview columns |
| 3 | `core` | `ai` package: usage entity and repository, gate, status endpoint, tests |
| 4 | frontend `core/admin` | `setAi` and the AI helper card |
