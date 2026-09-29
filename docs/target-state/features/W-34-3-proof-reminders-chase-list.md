# Feature: Proof of investment — reminders and chase list

| Field | Value |
|---|---|
| **Feature ID** | `W-34.3` · from ticket #46 (`W-34`) · `PAY-11` part 3 of 3 |
| **Promoted to** | `docs/target-state/features/W-34-3-proof-reminders-chase-list.md` on the developer's `dev-<name>` branch — **`W-34-3` with hyphens**, never `W-34.3`; `guard-edit` blocks the dotted form |
| **Owner** | mohit |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | DEBT-018 (honoured), DEBT-021 (fixed — no own scheduler), DEBT-022 (fixed) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-29 |
| **Blocked by** | `W-34.1` — the proof table and the due date. `W-20.2` (`b6e6012`) is on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an officer sees who has not submitted proof, and those employees get a reminder before the due date | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

| Today (frozen) | Evidence |
|---|---|
| Own daily cron, 09:00 UTC, on every replica | `scheduler/POIReminderScheduler.java:17`; DEBT-021 |
| Loads every proof in the org, filters `DRAFT` in memory | `service/claimsanddeclarations/POIReminderService.java:123-130` |
| An employee who never opened a proof has no row, so is never reminded | same, `:123` |
| Dashboard: filter by status, regime, search, page | `controller/employeeitdeclaration/AdminProofOfInvestmentController.java:36-60` |

Paths above are under `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/`.

**The machinery exists.** `W-20.2` ships the rule table, the claim-first sweep in the
worker, the `POI_REMINDER` event with a seeded template and the `POI_DUE_DATE` anchor. What
is missing is something to supply that anchor and its audience. Today a `POI_DUE_DATE` rule is
refused at creation with "Nothing in this runtime supplies the POI_DUE_DATE date yet"
(`code/backend/core/src/main/java/com/infinevo/core/notification/ReminderRuleServiceImpl.java:170-175`).

| Piece | Where |
|---|---|
| `POI_REMINDER(employee_name, financial_year, due_date)` | `core/.../notification/NotificationEvent.java` |
| Seeded in-app and email templates | `migration/.../core/V038__notification_template.sql:74-75` |
| `Anchor.POI_DUE_DATE` | `core/.../notification/Anchor.java` |
| Sweep fills `employee_name`, `due_date`, `financial_year` | `worker/.../notification/ReminderEvaluator.java:255-273` |
| Resolver seams | `ReminderAnchorResolver`, `ReminderAudienceResolver` in `core/.../notification/` |

## 2. Scope

**In scope**

- `ProofDueDateAnchorResolver implements ReminderAnchorResolver` — `POI_DUE_DATE`
- `ProofPendingAudienceResolver implements ReminderAudienceResolver` — audience `POI_PENDING`
- Chase list: one row per employee with a submitted declaration for the year, including those with no proof yet
- Status counts for the year

**Out of scope**

- A default rule per tenant — the officer creates one through `POST /api/v1/reminder-rules` (`W-20.2`); legacy reminders were off until configured
- Reminding reviewers of pending steps — the engine's escalation (`W-15.3`)
- Screens — a later `W-47` part

## 3. Flow

```
[officer] POST /api/v1/reminder-rules  { event: POI_REMINDER, audience: POI_PENDING, anchor: POI_DUE_DATE,
                                         offset_days: 7, send_at_local_time: "10:00", repeat_every_days: 2 }
          --> accepted now that both resolvers exist

[worker sweep, W-20.2]
   --> ProofDueDateAnchorResolver.resolveAnchorDate(rule, tenant)
         fy = FinancialYear.of(today in tenant zone)
         window(fy).poi_due_date, empty when poi_locked or unset
   --> ProofPendingAudienceResolver.resolve(rule, tenant)       own read-only transaction
         active employees with a SUBMITTED declaration for fy
         AND (no proof OR proof status IN (DRAFT, REJECTED))
   --> compose(POI_REMINDER, each)                             claim-first, once per run

[officer] GET /payroll/proof-of-investment?fy=&status=&search=&page=&size=     payroll.proof.read
[officer] GET /payroll/proof-of-investment/summary?fy=                          payroll.proof.read
```

`NOT_STARTED` is a reported status only, meaning the declaration is submitted and no proof row
exists. It is never stored. Both resolvers read through the same `ProofPendingQuery`, so the
list and the reminder never disagree.

## 4. Backend changes

Under `code/backend/payroll/src/main/java/com/infinevo/payroll/proof/`.

| Layer | File | Change |
|---|---|---|
| Resolver | `ProofDueDateAnchorResolver` | new `@Component`; `anchor()` = `POI_DUE_DATE` |
| Resolver | `ProofPendingAudienceResolver` | new `@Component`; `audience()` = `"POI_PENDING"`; `@Transactional(readOnly = true)`, as `SubjectAudienceResolver` |
| Query | `ProofPendingQuery` | new — one JPQL over declaration ⟕ proof, tenant-scoped, ids only for the resolver, paged rows for the list |
| Service / Impl | `ProofChaseService`, `…Impl` | new — `list(fy, status, search, pageable)`, `summary(fy)` |
| Controller | `ProofChaseController` | new |
| DTO | `ProofChaseRow`, `ProofChaseSummary` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/payroll/proof-of-investment` | `fy` (required), `status` (`NOT_STARTED`/`DRAFT`/`SUBMITTED`/`APPROVED`/`REJECTED`, optional), `search` (name or number prefix), `page`, `size` (25, max 100) | page of `employee_id`, number, name, `tax_regime`, `proof_status`, `proof_id?`, `submitted_at?`, `claimed_total`, `approved_total?` | `payroll.proof.read` |
| GET | `/api/v1/payroll/proof-of-investment/summary` | `fy` | count per status, `due_date`, `proof_open` | `payroll.proof.read` |

Paging and search follow `GET /api/v1/employees` (`W-13.3`): 25 by default, clamped at 100,
prefix search. Totals are `BigDecimal`, summed in SQL.

## 5. Frontend changes

None.

## 6. Database changes

None. The indexes `W-34.1` creates carry the list:
`idx_employee_proof_of_investment_tenant_fy_status`, and `W-32.1`'s
`idx_employee_investment_declaration_tenant_fy_status`.

- [x] `tenant_id` + RLS — no new table
- [x] Index on `tenant_id` plus lookup columns — existing, listed above
- [x] Money — no column; totals as `BigDecimal`
- [x] Expand / contract — nothing to expand

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../proof/ProofDueDateAnchorResolverTest.java` | due date returned; empty when locked, when unset, when no window for the year; year taken on 31 March vs 1 April |
| Integration | `payroll/.../proof/ProofPendingAudienceIT.java` | of five employees (no declaration; declaration `DRAFT`; submitted, no proof; proof `DRAFT`; proof `APPROVED`), and one inactive with a submitted declaration: exactly two ids returned; tenant B's rows never appear |
| Integration | `payroll/.../proof/ProofReminderRuleIT.java` | creating a `POI_DUE_DATE` / `POI_PENDING` rule is `201` (was `400`); `ReminderEvaluator` run inside the window composes one `POI_REMINDER` per pending employee with `due_date` set; a second run the same day composes none |
| Integration | `payroll/.../proof/ProofChaseListIT.java` | **acceptance**: the list shows `NOT_STARTED`, `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED` rows with correct totals; the status filter and search narrow it; summary counts match; `size=500` is clamped to 100; an employee without `payroll.proof.read` gets `403` |

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
grep -rln "implements ReminderAnchorResolver\|implements ReminderAudienceResolver" code/backend/payroll/src/main/java/
grep -rn "@Scheduled" code/backend/payroll/src/main/java/com/infinevo/payroll/proof/ || echo "no scheduler"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Resolvers | two files under `payroll/proof/` |
| Scheduler | `no scheduler` |
| Suite | green, no skips; `ProofChaseListIT` and `ProofReminderRuleIT` present and passing |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The worker cannot see payroll's resolvers | low | `worker/pom.xml:19` already depends on `payroll`; `ProofReminderRuleIT` runs the evaluator |
| The sweep's financial year is calendar-derived and differs from the tenant's | low | the anchor resolver and `ReminderEvaluator.java:267-271` both use April–March; resolver uses `FinancialYear.of` |
| The list and the reminder disagree on "pending" | medium if written twice | one `ProofPendingQuery` for both |
| A large tenant's audience loads entities | low | ids only, one query, as `SubjectAudienceResolver` |

## 10. Rollback

Nothing is deployed and no schema changes. Removing the two resolver beans makes existing
`POI_DUE_DATE` rules resolve nothing.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | creates no table |
| Flyway only | no schema change |
| `Money`/`BigDecimal` | totals as `BigDecimal` |
| Index on `tenant_id` plus lookup columns | reuses `W-32.1` and `W-34.1` indexes |
| Expand / contract | nothing changed |
| No module references another module | `payroll` → `core` (`ReminderAnchorResolver`, `ReminderAudienceResolver`) |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-018 no indexes | **Honoured** — existing tenant-led indexes |
| DEBT-021 scheduler on every replica | **Fixed** — no own scheduler; `W-20.2`'s claim-first sweep |
| DEBT-022 unscoped finders | **Fixed** — legacy loaded the whole org (`POIReminderService.java:123`); every query here is tenant-bound |

## 13. Decisions — settled 2026-09-29

| # | Question | Answer |
|---|---|---|
| 1 | Who is reminded? | **Submitted declaration, and no proof, or proof `DRAFT` or `REJECTED`.** Legacy missed everyone with no proof row |
| 2 | Seed a default rule? | **No.** Legacy's reminders were opt-in per org; the officer adds a rule |
| 3 | Store `NOT_STARTED`? | **No.** It is the absence of a row, reported by the query |

## 14. Open for the founder

None.
