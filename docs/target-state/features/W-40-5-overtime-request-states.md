# Feature: Overtime request states

| Field | Value |
|---|---|
| **Feature ID** | `W-40.5` · from ticket `W-40` (#51–52) · extends `CORE-21` for `HRMS-12` |
| **Promoted to** | `docs/target-state/features/W-40-5-overtime-request-states.md` on branch `dev-sayeed` — **`W-40-5` with hyphens** |
| **Owner** | sayeed, branch `dev-sayeed` |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | none new — builds on `W-39.2` |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-30 |
| **Blocked by** | nothing in code — `W-39.2` and `W-19` are on `main` (`b6e6012`). The tracker's wait on `W-16` is kept until the founder lifts it |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 1 script, no table — widens one `CHECK` on `core.overtime_request` | 1 |
| Externally testable behaviour | a waiting overtime request puts nothing in the pay input ledger; approving it puts exactly one row there | 1 |
| Frontend area | none | 1 |

Within cap. This part exists because `hrms` may only reach overtime through `OvertimeService`
(`W-39-2-overtime-capture.md:201`), and that service can only record overtime that is already approved.

---

## 1. Problem

`core.overtime_request` cannot hold a request that is waiting for a decision.

- **Two states only.** The `CHECK` allows `APPROVED` and `CANCELLED` —
  `M/core/V041__overtime_request.sql:28`; the enum matches
  (`code/backend/core/src/main/java/com/infinevo/core/overtime/OvertimeStatus.java:10-11`).
  `W-39.2` left `PENDING` and `REJECTED` to `W-40` (`W-39-2-overtime-capture.md:100,244`).
- **Every row is born approved and posted.** The constructor sets `APPROVED`
  (`.../overtime/OvertimeRequest.java:116`) and `record` posts to the ledger in the same
  transaction, stamped `ADMIN` (`.../overtime/OvertimeServiceImpl.java:66-87`).
- **Cancel assumes a ledger row.** It reverses `getPayInputId()` without checking it is set
  (`OvertimeServiceImpl.java:104`). A waiting request has none.

`REQUEST` is already allowed as a source (`V041:29`, `OvertimeSource.java:10`).

## 2. Scope

**In scope**

- `PENDING` and `REJECTED` in the `CHECK` and the enum
- `OvertimeService.submit` — a `PENDING` row, `source = REQUEST`, nothing posted
- `OvertimeService.approve` — posts the ledger row, `APPROVED`
- `OvertimeService.reject` — `REJECTED`, nothing posted
- `cancel` made safe for a row that never posted

**Out of scope**

- Any new endpoint, and the approval flow itself — `W-40.6`, in `hrms`
- The administrator's three endpoints — unchanged (`W-39-2-overtime-capture.md:107-109`).
  `GET /api/v1/overtime` now also returns waiting and rejected rows; `status` is already in the
  response (`.../overtime/OvertimeResponse.java:17`)
- Pricing. A request carries hours only; what an hours-only row is worth is `W-29`'s
  (`W-39-2-overtime-capture.md:243`)

## 3. Flow

```
W-40.6 submit   --> OvertimeService.submit(entry) --> core.overtime_request (PENDING, REQUEST)      ledger: nothing
W-40.6 approved --> OvertimeService.approve(id)   --> PayInputService.record(OVERTIME)              ledger: one row
                                                  --> status APPROVED, pay_input_id, posted_period
W-40.6 rejected --> OvertimeService.reject(id)    --> status REJECTED                               ledger: nothing
Administrator   --> DELETE /api/v1/overtime/{id}  --> cancel: reverse only if a ledger row exists
```

## 4. Backend changes

Package `com.infinevo.core.overtime`.

| Layer | File | Change |
|---|---|---|
| Enumeration | `OvertimeStatus.java` | Add `PENDING`, `REJECTED` |
| Entity | `OvertimeRequest.java` | A second constructor or factory for a `PENDING` row; `approve(actor)` and `reject(actor)` transitions. `markPosted` and `cancel` stay (`:136-147`) |
| Service | `OvertimeService.java`, `OvertimeServiceImpl.java` | Add `submit`, `approve`, `reject`; guard `cancel` |
| Repository | `OvertimeRequestRepository.java` | No change expected |

**The three new methods**

| Method | Does | Refuses |
|---|---|---|
| `OvertimeResponse submit(OvertimeEntry entry)` | Validates as `record` does (`OvertimeServiceImpl.java:133-164`), saves `PENDING` with `source = REQUEST`. **Calls no ledger method** | `amount` present — `ValidationException`; a request is hours only. `remarks` over 255 characters — `ValidationException` (the column is `VARCHAR(255)`, `V041:16`, and `record` does not check it). Every refusal `record` already makes |
| `OvertimeResponse approve(UUID id)` | `PENDING` → posts one `PayInputCommand` built exactly as `record` builds it (`:76-83`, `sourceRef = overtime_request:<id>`), then `markPosted`, status `APPROVED`. One transaction. **`APPROVED` already → returns the row, posts nothing** | `REJECTED` or `CANCELLED` → `IllegalStateException`; not found → `NotFoundException` |
| `OvertimeResponse reject(UUID id)` | `PENDING` → `REJECTED`. **`REJECTED` already → returns the row** | `APPROVED` or `CANCELLED` → `IllegalStateException`; not found → `NotFoundException` |

`approve` must be safe to call twice because the approval engine retries a handler that failed
(`core/.../approval/OutcomeDispatcher.java:86-93`). The `sourceRef` is the second guard: the
ledger's idempotency key (`W-39-2-overtime-capture.md:124`).

**`cancel`, corrected**

| Row's status | `cancel` does |
|---|---|
| `APPROVED` | As today: reverse the ledger row, `CANCELLED` (`OvertimeServiceImpl.java:104-106`) |
| `PENDING` | `CANCELLED`, **no ledger call** |
| `REJECTED` | `409`, a new `NotCancellableException` mapped beside `AlreadyCancelledException` |
| `CANCELLED` | `409 AlreadyCancelledException`, as today (`:100-102`) |

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V120__overtime_request_states.sql` — reserved for `W-40.5`, 2026-09-30 | alters `core.overtime_request`; creates nothing | already is | forward-only; widens a constraint |

```sql
ALTER TABLE core.overtime_request DROP CONSTRAINT overtime_request_status_check;
ALTER TABLE core.overtime_request ADD CONSTRAINT overtime_request_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));
```

The column default stays `'APPROVED'` (`V041:14`), so the release before this one runs
unchanged against the new schema: it only ever writes the two old values, and both still pass.

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | creates no table; `core.overtime_request` already has both (`V041:9,38-47`) |
| Flyway only, `ddl-auto` nowhere | one script; both statements name `core.` |
| Money as `Money` / `BigDecimal` | no new column. `approve` passes `hours` as `BigDecimal` and no amount |
| Index on `tenant_id` plus lookup columns | no new lookup; the three indexes at `V041:34-36` serve |
| Expand / contract | expand only — the constraint is dropped and re-added wider in one script, never narrower |
| No module references another | `core` only. `hrms` calls the three methods; `core` imports nothing from `hrms` |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../overtime/OvertimeRequestStatesTest.java` | every cell of the two tables in §4; `submit` with an amount is refused; `submit` never touches `PayInputService` (a mock with zero interactions) |
| Integration | `core/.../overtime/OvertimeRequestLedgerIT.java` | after `submit`, zero `core.pay_input` rows for the employee; after `approve`, exactly one, kind `OVERTIME`, `quantity = hours`; a second `approve` leaves one; `reject` leaves zero; `cancel` of a `PENDING` row leaves zero and no reversal row |
| Integration | `core/.../overtime/OvertimeLedgerIT.java` (existing) | unchanged and still green — the administrator path posts as before |
| Integration | `migration` module | a row with `status = 'PENDING'` inserts; `status = 'WAITING'` is refused by the `CHECK` |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'overtime_request_status_check';"
cd code/backend && mvn -q verify
node .claude/hooks/check-done.mjs W-40.5
```

| Check | Expected |
|---|---|
| Constraint | one row naming `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| Suite | green, no skips; `OvertimeLedgerIT` still passes |
| `check-done.mjs` | 5/5 |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A reader of `core.overtime_request` assumes every row is approved and paid | medium — until today that was true | Any new reader must filter on `status = 'APPROVED'`. Today the only reader is `list`, which returns `status`; pay reads the ledger (`core.pay_input`), never this table (`02-data-model.md:111`) |
| `approve` posts twice on a retry | medium | Status check first, ledger idempotency key second; `OvertimeRequestLedgerIT` calls it twice |
| A retried administrator `POST` still posts twice | known, accepted at the `W-39.2` merge (`active-work.md` 2026-09-29 devashis entry) | Not widened here; `approve` reuses the row's own id, which exists before the ledger call |
| The period is locked when the manager approves late | medium | The ledger posts to the next open period and reports it (`W-39-2-overtime-capture.md:126-127`); `approve` stores `posted_period` as `record` does |

## 10. Rollback

Revert the application commit. The wider constraint stays and is harmless. Rows already
`PENDING` or `REJECTED` stay; the older code never reads them as approved because it never
reads `status` to decide pay.

## 11. Gap inventory

| ID | Decision |
|---|---|
| Legacy two status columns, manager and HR (`legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/OvertimeRequest.java:43-49`) | **Not ported** — one status here; who approved is in the approval engine's steps |
| Legacy comp-off on approval (`.../service/OvertimeRequestService.java:83-91`) | **Dropped by decision** — `D-28`, already recorded at `W-39-2-overtime-capture.md:260` |

## 12. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `code/backend/migration` | `core/V120__overtime_request_states.sql` and its migration test |
| 2 | `code/backend/core` | enum, entity transitions, three service methods, the `cancel` guard, tests |

## 13. Decisions — settled 2026-09-30

| # | Question | Answer |
|---|---|---|
| 1 | Where does a waiting request live | **In `core.overtime_request`**, as `W-39.2` reserved. No second table in `hrms` |
| 2 | Does a request carry an amount | **No — hours only.** The legacy form has no amount either (`W-39-2-overtime-capture.md:45-46`) |
| 3 | When is the ledger written | **Only at `approve`.** Nothing waiting or rejected ever reaches pay |
