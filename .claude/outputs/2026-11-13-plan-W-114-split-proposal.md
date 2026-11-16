# `W-114` Infinevo HCM AI Helper — Revised Split Proposal (10 Sub-Tickets)

| Field | Value |
|---|---|
| **Ticket** | `W-114` Infinevo HCM AI helper, size **L**, kind **feature** |
| **Owner** | karma (`dev-karma`) |
| **Status** | Revised split proposal incorporating founder feedback & decisions (2026-11-13 / 2026-11-16) |
| **Asked of founder** | Final approval of the revised 10-ticket split shape, migration reservations, and order |

---

## 1. Context & Feedback Incorporated

Following founder review, the split has been refined with the following adjustments:
1. **Module Decoupling**: Previous `.3` split into dedicated tickets for `hrms` (`W-114.6`) and `payroll` (`W-114.7`).
2. **Leave Service Location**: Previous `.4` corrected to `core` only; leave requests use `core/leave/LeaveRequestService` (not `hrms`).
3. **Migration Numbering**: `main` is at `V191` (`core/V191__pay_input_note.sql`). Migrations reserved are **`V192`** (`W-114.1`) and **`V193`** (`W-114.3`).
4. **Audit Traceability without Schema Mutation**: `core.audit_log` has no `channel` column. Instead of adding an unneeded migration to `audit_log`, `core.ai_action_intent` records the full intent lifecycle, and execution passes MDC `trace_id = 'ai-intent:' || intent_id` so `AuditWriter` binds the entity change in `core.audit_log.trace_id`.
5. **Azure Infrastructure**: Added dedicated infra ticket `W-114.2` before model integration (Bicep, Azure OpenAI private endpoint, Key Vault secret).
6. **Early Q&A Testing**: A plain chat drawer (`W-114.5`) lands immediately after the Q&A engine (`W-114.4`), allowing the browser UI to be tested early. Interactive action confirmation cards land later in `W-114.9`.
7. **No Streaming (SSE)**: Full synchronous REST responses only (`POST /api/v1/ai/chat`). Streaming via SSE is dropped to maintain posture on CVE-2026-47890.
8. **Live Permission Re-Check**: The confirmation step in `W-114.8` re-verifies caller permissions at the exact moment of confirmation, not just when the action proposal was generated.
9. **Tenant AI Switch & Monthly Cap (Decisions D & E)**: Built into `W-114.1` with `V192`. AI is disabled by default for every tenant; platform admin controls switch and monthly cap; service refuses requests when off (`403`) or over cap (`429`).
10. **Retention & Masking Ownership**:
    - **PII Masking (Decision B)**: Resides in the **LLM Gateway (`W-114.4`)** so all tool outputs are scrubbed before reaching the model (PAN, Aadhaar, bank accounts masked; salary/leave/attendance figures permitted).
    - **30-Day Cleanup (Decision C)**: Resides in the **Worker (`W-114.10`)** via a scheduled `AiChatRetentionJob` under `retention_user`.

---

## 2. Revised Sub-Ticket Architecture (10 Sub-Tickets)

Every sub-ticket strictly obeys the size cap (max 1 module/frontend area, max 1 migration, max 1 testable behaviour):

| Sub-Ticket | Name | Module | Migration | Externally Testable Behaviour | Size |
|---|---|---|---|---|---|
| **`W-114.1`** | **Tenant AI Switch & Monthly Usage Cap** | `core` | `V192` (`core`) | Tenant columns on `core.tenant` (`ai_enabled`, `ai_monthly_request_cap`, `ai_usage_current_month`, `ai_usage_month`); platform admin controls on tenant page; request gate rejecting when disabled (`403`) or exceeded (`429`) | M |
| **`W-114.2`** | **Azure OpenAI Infrastructure & Secrets** | `infra` | None | Azure OpenAI resource provisioned via Bicep; flagship model deployment (`gpt-4o`); private endpoint in platform VNet; Key Vault secret reference (`W-56`) | S |
| **`W-114.3`** | **AI Helper Core Engine, Session Schema & Tool SPI** | `core` | `V193` (`core`) | `core.ai_session`, `core.ai_message`, and `core.ai_action_intent` tables with PostgreSQL RLS; session CRUD (`POST /api/v1/ai/sessions`, `GET`, `DELETE`); `AiToolProvider` SPI interface definition | M |
| **`W-114.4`** | **LLM Gateway with PII Masking & Core Q&A** | `core` | None | Azure OpenAI client invocation; PII Masking Filter (Decision B: masks PAN, Aadhaar, bank details, credentials); tool dispatch loop; Core tool provider (holidays, leave policies, org master lookup); synchronous answer response | M |
| **`W-114.5`** | **Frontend Plain Chat Drawer (Early Q&A Trial)** | `frontend` (`shell`) | None | Floating trigger button in `AppShell`; slide-over plain chat drawer; session reset/selection; full-answer text rendering; error displays for disabled/cap-exceeded tenants | M |
| **`W-114.6`** | **HRMS Read-Only Tool Provider** | `hrms` | None | Implements `AiToolProvider` in `hrms` for employee attendance history, timesheet status, and WFH allocation/request status; strict caller identity and tenant scoping | S |
| **`W-114.7`** | **Payroll Read-Only Tool Provider** | `payroll` | None | Implements `AiToolProvider` in `payroll` for payslip summaries, salary structure components, and tax declaration status; strict caller employee and tenant scoping | S |
| **`W-114.8`** | **Leave Action Proposal, Permission Re-Check & Audit Execution** | `core` | None | Two-step action workflow for leave: assistant proposes `AiActionIntent`; user submits `POST /api/v1/ai/actions/{intentId}/confirm`; service re-checks permissions at execution time; invokes `LeaveRequestService`; sets MDC `trace_id = 'ai-intent:' || id` for `AuditWriter` | M |
| **`W-114.9`** | **Frontend Action Confirmation Cards** | `frontend` (`shell`) | None | Interactive "Review & Confirm" cards in chat drawer; displays proposed leave parameters; Confirm / Cancel actions; 15-minute intent expiry handling | S |
| **`W-114.10`** | **30-Day Chat Retention Sweep Worker** | `worker` | None | Scheduled `AiChatRetentionJob` in `worker` with `@SchedulerLock`; deletes `core.ai_message`, `core.ai_action_intent`, and `core.ai_session` older than 30 days under `retention_user` | S |

---

## 3. Execution Order & Dependency Graph

```
[W-114.1: Tenant AI Switch & Cap (core, V192)]
         │
         ▼
[W-114.2: Azure OpenAI Infra (infra)]
         │
         ▼
[W-114.3: Session Schema & Tool SPI (core, V193)]
         │
         ▼
[W-114.4: LLM Gateway + Masking + Core Q&A (core)]
         │
         ▼
[W-114.5: Frontend Plain Chat Drawer (shell)]   <-- Q&A can be tried live here
         │
         ├────────────────────────────────────────┐
         ▼                                        ▼
[W-114.6: HRMS Tools (hrms)]             [W-114.7: Payroll Tools (payroll)]
         │                                        │
         └───────────────────┬────────────────────┘
                             ▼
[W-114.8: Leave Action Proposal & Audit (core)]
                             │
                             ▼
[W-114.9: Frontend Action Confirmation Cards (shell)]
                             │
                             ▼
[W-114.10: 30-Day Chat Retention Sweep (worker)]
```

---

## 4. Key Architectural Invariants & Decisions Matrix

| Topic | Decision | Implementation Details |
|---|---|---|
| **Model Provider** | Azure OpenAI (`gpt-4o`) | Provisioned via Bicep in `W-114.2`; accessed via private endpoint; credentials in Key Vault; no public API egress |
| **Tenant Isolation** | RLS + Tenant Gate | `core.tenant.ai_enabled = FALSE` by default; checked before any LLM call; all session and message tables enforce PostgreSQL RLS (`D-09`) |
| **Usage Cap** | Monthly Tenant Cap | Configured on Platform Admin Tenant page; tracked in `core.tenant.ai_usage_current_month`; resets monthly; returns clean refusal when reached |
| **Personal Data Masking** | Gateway Scrubber | Implemented in `W-114.4`: PAN, Aadhaar, Bank Account Numbers, and tokens are masked (`***`); salary, leave balances, and attendance dates permitted |
| **Response Transport** | Synchronous REST | `POST /api/v1/ai/chat` returns full answer object; zero SSE/streaming to protect against CVE-2026-47890 |
| **Action Safety** | 2-Step Confirmation Loop | Propose intent (15-min TTL) -> user explicitly confirms; permissions re-evaluated at confirmation time (`W-114.8`) |
| **Audit Attribution** | Linked via `trace_id` | `core.ai_action_intent` records proposal metadata; execution sets MDC `trace_id = 'ai-intent:' || id`; `AuditWriter` writes to `core.audit_log.trace_id` |
| **Retention Policy** | 30-day purge | Swept nightly by `AiChatRetentionJob` in `worker` (`W-114.10`) under `retention_user` |

---

## 5. Next Steps

1. Founder reviews and confirms approval of this revised 10-ticket split.
2. Upon approval, run `/plan-feature W-114.1` on `dev-karma` to draft the specification for the first sub-ticket (**`W-114.1` Tenant AI Switch & Monthly Usage Cap**).
