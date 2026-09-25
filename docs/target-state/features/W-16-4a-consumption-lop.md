# Feature: Leave consumption and loss-of-pay derivation

| Field | Value |
|---|---|
| **Feature ID** | `W-16.4a` · from ticket #20 · `CORE-07` |
| **Promoted to** | `docs/target-state/features/W-16-4a-consumption-lop.md` on branch `W-16-4a-consumption-lop` — **`W-16-4a` with hyphens**, never `W-16.4a`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | BUG-003 (fixed), DEBT-018 (honoured) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-16.3` (an approved request), `W-16.2` (a balance), `W-19` (the ledger LOP is written to) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 2 scripts, one table each — aggregate exception | 1 — exception granted 2026-09-22 |
| Externally testable behaviour | an approved request consumes balance, and any excess becomes loss-of-pay days on the ledger | 1 |
| Frontend area | none | 1 |

Within cap.

---

## 1. Problem

Two products derive loss of pay by different arithmetic from different data, and a third
place decides which answer the payslip uses.

- Payroll derives it comparatively: `lop = consumed_days > total_allocation ? consumed_days − total_allocation : 0`, recomputed for every row on every change — `EmployeeLeaveAllocationServiceImpl.java:666-718`, the test at `:698`
- HRMS derives it cumulatively: a row per employee, year and month written when a request is approved — `EmployeeMonthlyLop.java:9-114`
- The two disagree by construction. HRMS may record two loss-of-pay days in January while Payroll, seeing 10 days consumed against a 20-day allocation, computes zero
- The pay run uses **neither** consistently. It calls HRMS over HTTP — `EmployeePayRunServiceImpl.java:1091` — multiplies by a per-day rate at `:1170` and subtracts at `:1177`, while Payroll's own consumption figure goes unused

The recalculation is also destructive. `recalculateConsumptionBalances()` walks every
consumption row for an employee and year and overwrites `balance_after`, `running_ytd` and
`lop_days` in place, so the figure behind a payslip issued last month can silently change
this month.

And precision is lost on the way: Payroll's `consumed_days` and `lop_days` are `Integer` —
`EmployeeLeaveBalanceConsumption.java:92,95` — while HRMS records `lop_days` as a `Float`.
That is BUG-003's real shape, and `GAP_INVENTORY.md:29` marks it resolved on the HRMS side
only.

## 2. Scope

**In scope**

- `core.leave_consumption` — one immutable row per consumption event
- `core.leave_monthly_lop` — the per-employee, per-month loss-of-pay figure
- Derivation: consumption beyond available balance becomes loss of pay
- Writing that figure to `core.pay_input` as the `LOP_DAYS` kind, so the pay run never asks a module for it
- Re-crediting on cancellation, as a compensating row

**Out of scope**

- Applying loss of pay to money — `W-18.2` stamps the policy and `W-29` computes the payslip
- The working-day policy that decides what a day is worth — `W-18.1`
- Bulk import — `W-16.4b`
- Encashment

## 3. Flow

```
[W-16.3 request APPROVED]
   --> [LeaveConsumptionService] --> [core.leave_consumption, append only]
   --> balance from W-16.2 --> excess? --> [core.leave_monthly_lop]
   --> [PayInputService.record(LOP_DAYS, period) (W-19)]
```

The pay run reads `core.pay_input` and nothing else. That is what removes the cross-service
HTTP call at `EmployeePayRunServiceImpl.java:1091`.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Service | `core/.../leave/LeaveConsumptionService.java` | new |
| Service | `core/.../leave/LopDerivationService.java` | new |
| Entity | `core/.../leave/LeaveConsumption.java`, `LeaveMonthlyLop.java` | new, each `@Table(schema="core")` |
| Repository | `core/.../leave/LeaveConsumptionRepository.java`, `LeaveMonthlyLopRepository.java` | new |
| Controller | `core/.../leave/LeaveConsumptionController.java` | new — read only |
| DTO | `core/.../leave/LeaveConsumptionResponse.java`, `LopResponse.java` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/employees/{id}/leave-consumption` | `?year=` | the rows, in order | Bearer, tenant bound |
| GET | `/api/v1/employees/{id}/lop` | `?period=` | days, with the working shown | Bearer, tenant bound |

No write endpoint. Consumption is a consequence of an approval, never something a client
posts.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V0NN__leave_consumption.sql` | `core.leave_consumption` | yes | additive |
| `core/V0NN__leave_monthly_lop.sql` | `core.leave_monthly_lop` | yes | additive |

Version numbers assigned when the branch is cut — `migration/README.md:17-31`.

`leave_consumption`: `id uuid` · `tenant_id uuid NOT NULL` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` ·
`allocation_id uuid NOT NULL REFERENCES core.leave_allocation(id)` ·
`leave_request_id uuid NULL REFERENCES core.leave_request(id)` ·
`consumed_days numeric(5,2) NOT NULL` · `consumed_on date NOT NULL` ·
`period char(7) NOT NULL` · `reverses_id uuid NULL REFERENCES core.leave_consumption(id)` ·
`reason varchar(500)` · four audit columns.

`leave_monthly_lop`: `id uuid` · `tenant_id uuid NOT NULL` ·
`employee_id uuid NOT NULL REFERENCES core.employee(id)` · `period char(7) NOT NULL` ·
`leave_type_id uuid NULL REFERENCES core.leave_type(id)` ·
`lop_days numeric(5,2) NOT NULL` · `pay_input_id uuid NULL REFERENCES core.pay_input(id)` ·
four audit columns.

- [x] `tenant_id` on both, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `(tenant_id, employee_id, period)` on both, `(tenant_id, allocation_id)` on consumption
- [x] **Money columns — none.** Day counts are `numeric(5,2)`; the rupee value is `W-18.2`'s
- [x] Expand / contract — new tables only

**Append-only, and no `balance_after` column.** The frozen consumption row stores
`balance_after`, `running_ytd` and `balance_days` and rewrites them on every recalculation —
`EmployeeLeaveBalanceConsumption.java:92`. A derived figure stored on an immutable row is a
figure that will be wrong. The balance comes from `W-16.2`, summed at read time.

**A cancellation writes a negative row**, linked by `reverses_id`. Nothing is edited and
nothing is deleted, so a payslip's basis can always be reconstructed.

`app_user` is granted `SELECT, INSERT` and not `UPDATE` or `DELETE`, the same posture as
`core.audit_log` and `core.pay_input`.

RLS and the `tenant_isolation` policy in the exact `CASE` form in each script —
`migration/README.md:76-123`.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../leave/LopDerivationServiceTest.java` | consumption within balance yields zero; excess yields the difference; half-days survive; a reversal reduces loss of pay |
| Unit | `core/.../leave/LeaveConsumptionServiceTest.java` | one approval writes exactly one row; a repeat of the same approval writes none |
| Integration | `core/.../leave/LopToPayInputIT.java` | a derived figure appears in `core.pay_input` with kind `LOP_DAYS` and the right period |
| Integration | `core/.../leave/LeaveConsumptionImmutabilityIT.java` | `app_user` is refused `UPDATE` and `DELETE` |
| Integration | `core/.../leave/LeaveConsumptionRlsIT.java` | tenant A cannot read tenant B's consumption or loss-of-pay rows |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

`LopToPayInputIT` is the one that proves the cross-service call is gone: the figure reaches
payroll through a table, not an HTTP request.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
for t in leave_consumption leave_monthly_lop; do
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT '$t', relrowsecurity FROM pg_class WHERE oid='core.$t'::regclass;"
  docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
    "SELECT privilege_type FROM information_schema.table_privileges
      WHERE grantee='app_user' AND table_name='$t' ORDER BY 1;"
done
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name FROM information_schema.columns
    WHERE table_schema='core' AND table_name='leave_consumption'
      AND column_name IN ('balance_after','running_ytd','balance_days');"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on both | `t` twice |
| Grants | `INSERT` and `SELECT` only, both tables |
| Derived columns | **no rows** — none of the three exists |
| `lop_days` type | `numeric`, scale 2 |
| Suite | green, no skips |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A recalculation routine is written because the legacy one is familiar | **high — it is the frozen design** | Append-only, no derived columns, and verification asserts they are absent |
| Loss of pay is derived twice — here and again in the pay run | medium, expensive | `core.pay_input` is the single channel; `W-29` reads the ledger and derives nothing |
| Integer truncation returns through a DTO | medium | Every field is `BigDecimal`; half-day cases asserted in two tests |
| A duplicate approval event double-consumes | medium | `leave_request_id` is unique among non-reversing rows; the service test covers the repeat |
| Loss of pay written for a period already locked in the ledger | medium | `W-19`'s lock refuses the insert at the database; this service surfaces the refusal rather than swallowing it |

## 10. Rollback

Nothing is deployed. Both scripts are additive and forward-only —
`migration/README.md:135-143`. Consumption is append-only, so a mistake is corrected by a
reversing row, never by an edit.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables, each in its own script |
| Flyway only, `ddl-auto` nowhere | two scripts; none added |
| `Money`/`BigDecimal` for money | no money column; day counts are `numeric(5,2)` |
| Index on `tenant_id` plus lookup columns | `tenant_id` leads every index |
| Expand / contract | new tables only |
| No module references another module | `core` only; payroll reads `core.pay_input`, never this |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-003 half-day loss of pay (`GAP_INVENTORY.md:29`) | **Fixed.** `numeric(5,2)` end to end; no integer path remains |
| Destructive recalculation (`EmployeeLeaveAllocationServiceImpl.java:666-718`) | **Fixed by replacement.** Append-only rows, balance summed at read time |
| Two competing loss-of-pay figures | **Fixed.** One derivation, written once to the ledger |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Does loss of pay derive per leave type or per employee per month?** The frozen systems do both — HRMS records a type on the row, Payroll compares against a type's allocation. **Recommend** deriving per type and summing per month onto one ledger entry, since a payslip needs one number but a dispute needs the breakdown.
2. **What happens when a cancellation arrives after the period is locked?** **Recommend** the reversing row is written to the next open period, not the locked one, and the response says so — the alternative is either rewriting a closed payslip or losing the credit.
