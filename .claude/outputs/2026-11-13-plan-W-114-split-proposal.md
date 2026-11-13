# `W-114` Infinevo HCM AI Helper — split proposal, no spec written

| Field | Value |
|---|---|
| **Ticket** | `W-114` Infinevo HCM AI helper, size **L**, kind **feature** |
| **Owner** | karma (`dev-karma`) |
| **Verdict** | **Cannot be one spec.** Breaks all four size-cap axes (`plan-feature` rules) |
| **Asked of founder** | Approve the split shape, LLM infrastructure choice, and confirmation security model before drafting `W-114.1` |

The `plan-feature` skill stops here by design: *"If the proposed feature crosses more than one on any axis, propose a split into sub-tickets (e.g., W-nn.1, W-nn.2) and stop before drafting."*

---

## 1. Why it breaks the size cap

| Axis | Limit | `W-114` as ticketed | Status |
|---|---|---|---|
| **Backend module** | 1 | Touches `core` (engine, sessions, SPI), `hrms` (leave/attendance tools), `payroll` (payslip/tax tools), and `shared` (security/contracts) | **Breaks cap (3+ modules)** |
| **Flyway migration** | 1 | Chat sessions, messages, action intents, confirmation state | **1 migration needed (`core`)** |
| **Externally testable behaviour** | 1 | (1) Conversational Q&A retrieval, (2) Action proposal & human-in-the-loop confirmation execution, (3) Tool registry dispatch | **Breaks cap (at least 3 behaviours)** |
| **Frontend area** | 1 | Floating launcher, slide-over drawer, message stream, interactive action confirmation cards | **Breaks cap (Backend + Frontend)** |

---

## 2. Hard Architectural Invariants

Every sub-ticket must maintain the platform's non-negotiable rules:
1. **Tenant Isolation via RLS**: Every session, message, and action intent table must carry `tenant_id` and PostgreSQL Row-Level Security (`D-09`).
2. **Caller Identity & RBAC**: The AI helper must act strictly on behalf of the authenticated user (`core.user_account` / `core.employee`). It cannot bypass `@RequiresAction` or inspect records outside the caller's authorized scope.
3. **Module Boundaries (`D-01`)**: `hrms` and `payroll` must never see each other. The AI orchestrator in `core` cannot directly import `hrms` or `payroll` services. Tool registration must follow the SPI pattern established by the Approval Engine (`W-15.1`–`W-15.3`).
4. **Mandatory Human Confirmation**: The assistant must **never** mutate state autonomously. It generates a structured `AiActionProposal` (e.g., apply leave, submit regularisation); execution occurs **only** when the user explicitly clicks Confirm, sending a single-use intent confirmation request.
5. **Full Audit Logging (`W-22.1`)**: Any execution originating from the AI helper must record in `core.audit_log` with `channel = 'AI_ASSISTANT'`, the user ID, intent ID, and input parameters.

---

## 3. Recommended Split — 5 Sub-Tickets

| Sub-ticket | Name | Module | Migration | Testable Behaviour | Size |
|---|---|---|---|---|---|
| **`W-114.1`** | **AI Helper Core Engine, Session Schema & Tool SPI** | `core` | `V189` (`core`) | Chat session lifecycle (`POST /api/v1/ai/sessions`, `GET`, `DELETE`), message persistence under RLS, and `AiToolProvider` SPI registry | M |
| **`W-114.2`** | **LLM Gateway & Core Read-Only Q&A** | `core` | None | Secure invocation of LLM (via Azure OpenAI / configured provider), tenant prompt grounding, tool-calling loop, Core tool provider (holiday calendar, company policies, org lookup) | M |
| **`W-114.3`** | **HRMS & Payroll Read-Only Tool Providers** | `hrms`, `payroll` | None | Domain tool providers: `hrms` (leave balances, attendance records, WFH status) and `payroll` (payslip summary, tax declaration status); strict caller scope checks | M |
| **`W-114.4`** | **Action Proposal, User Confirmation & Audit Execution** | `core`, `hrms` | None | Two-step action workflow: AI produces pending `AiActionIntent`; user reviews parameters and confirms (`POST /api/v1/ai/actions/{intentId}/confirm`); executes leave application via `LeaveApplicationService`; writes to `core.audit_log` | M |
| **`W-114.5`** | **In-App Assistant Frontend Drawer & Action Cards** | `frontend` (`shell`) | None | Floating trigger button in `AppShell`, slide-over chat drawer, Markdown stream renderer, interactive "Review & Confirm" action cards for pending intents | M |

---

## 4. Execution Order & Dependencies

```
[W-114.1 Engine & Sessions (core)]
         │
         ▼
[W-114.2 LLM Gateway & Core Q&A (core)]
         │
         ├────────────────────────────────────────┐
         ▼                                        ▼
[W-114.3 HRMS & Payroll Tools (hrms, payroll)]   [W-114.4 Action Confirmation & Audit (core, hrms)]
         │                                        │
         └───────────────────┬────────────────────┘
                             ▼
               [W-114.5 Frontend Drawer (shell)]
```

- `W-114.1` unblocks `W-114.2`.
- `W-114.3` and `W-114.4` can proceed once the gateway and SPI exist in `W-114.2`.
- `W-114.5` builds against the completed backend API contracts.

---

## 5. Architectural Decisions for Founder Approval

1. **LLM Provider & Azure Integration**:
   - *Recommendation*: **Azure OpenAI Service** (e.g. `gpt-4o-mini` or `gpt-4o`).
   - *Why*: The platform is hosted on Azure Container Apps (`D-10`) with Key Vault (`W-56`) and Managed Identity (`W-51`). Using Azure OpenAI private endpoints ensures customer HCM data never traverses the public internet or external third-party consumer APIs.
2. **Flyway Migration Sequence**:
   - Highest applied on `main` is `V188__subscription_plan.sql`.
   - `W-114.1` reserves **`V189`** in `core`: `core.ai_session`, `core.ai_message`, and `core.ai_action_intent`.
3. **Intent Confirmation Expiry**:
   - Action intents expire after 15 minutes if not confirmed.
   - An intent can only be confirmed by the exact `user_account_id` that initiated the conversation.

---

## 6. Action Requested of Founder

Approve the proposed 5-ticket split (`W-114.1` through `W-114.5`) and LLM hosting strategy so that `/plan-feature W-114.1` can begin drafting.
