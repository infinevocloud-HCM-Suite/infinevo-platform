# Feature: Off-cycle pay run — a bonus reaches a payslip outside the cycle

| Field | Value |
|---|---|
| **Feature ID** | `W-30.2` · from ticket #37 · `PAY-06`/`PAY-07` part 2 of 2 |
| **Promoted to** | `docs/target-state/features/W-30-2-off-cycle-pay-run.md` on the developer's `dev-<name>` branch — **`W-30-2` with hyphens**, never `W-30.2`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | DEBT-007, DEBT-008, DEBT-022 (fixed by the `W-29.1` shape), DEBT-018 (honoured), BUG-010 (kept fixed), DEBT-035, DEBT-036, DEBT-037 (proposed in `.claude/outputs/2026-09-25-analyze-w30-off-cycle.md`, fixed by replacement) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-30.1` — `forRun`, `lockRun`, the tag · `W-29.3` — the contributor loop with `PAY_INPUT` in it · `W-29.4` is **not** a blocker: `compute` goes through whatever `PayRunService.compute` does on the day |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | `V061` — a second `run_type` value, one `notes` column, the unique index narrowed to regular runs. Expand only | 1 |
| Externally testable behaviour | an officer creates an off-cycle run for named employees, adds a bonus to it, locks and computes it, and the bonus is a line on that run and on no other | 1 |
| Frontend area | none — `W-47` builds the pay run screens | 1 |

Within cap. **Zero new tables** — founder decision 2026-09-25, recorded in
`.claude/outputs/2026-09-25-analyze-w30-off-cycle.md` § Founder decisions. `02-data-model.md:344-345`
still lists five tables for `PAY-06`/`PAY-07`; it is superseded by this spec and `/sync-docs`
aligns it after merge.

---

## 1. Problem

Legacy has an off-cycle run, and it is half built. `09-build-order.md:221` says what it is for:
*"this is a Payroll-only customer's substitute for overtime"* — the way a company with no HRMS
pays something extra between two month-ends.

- **Nothing computes.** Seven endpoints — draft, update, get, list, delete, import, import
  release-withheld-salary
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/payRun/offCyclePayrun/OffCyclePayRunController.java:27-89`)
  — and none of them produces net pay, a payslip, a tax figure or a statutory figure. Earnings
  and deductions are stored as typed (`.../serviceimpl/payRun/offCyclePayrun/OffCyclePayRunServiceImpl.java:290-313`)
- **Status is a free string.** `"draft"` by default (`.../entity/payRun/offCyclePayrun/OffCyclePayRun.java:26`),
  copied from the request on update (`OffCyclePayRunServiceImpl.java:147-148`)
- **Import only works for three fixed names.** The tenant must own an earning called exactly
  `Bonus`, one called `Commission` and a deduction called `Income Tax`, or the import throws
  (`OffCyclePayRunServiceImpl.java:263-268`)
- **Days become money at ÷30**, whatever the pay basis (`OffCyclePayRunServiceImpl.java:97`);
  `W-18.1` makes the basis configurable and `W-28` makes the regular run read it
- **Tax override is a JSON string** on the employee row (`OffCyclePayRunServiceImpl.java:317-325`)
- **A separate engine for the same thing.** Four tables of its own
  (`legacy/docs/DB_SCHEMA.md:1479-1530`), its own status words, its own money columns, so an
  off-cycle bonus and an in-cycle bonus are two kinds of record that never meet in a report

The regular run (`W-29.1`–`W-29.4`) already has everything an off-cycle run needs: a row per
employee, a status machine, a lock, a contributor loop that turns ledger inputs into lines, and
a worker. An off-cycle run is that run with three differences: it is created for named
employees rather than everyone, it pays only inputs tagged with its id (`12-core-contracts.md:168`),
and it has no structure and no loss of pay.

## 2. Scope

**In scope**

- `run_type = 'OFF_CYCLE'` on `payroll.payrun`; `notes`
- Create: named employees, a pay date, an optional note. Inclusion: bank required, salary not
- `POST /payruns/{id}/inputs` — the officer adds a payout to the run; the run validates and
  writes the ledger row **tagged with the run id** through `W-30.1`'s seam
- Lock: `PayInputService.lockRun(id, period)` instead of `lock(period)`
- Compute: the `W-29.2` loop unchanged; `STRUCTURE` and `LOP` contribute nothing on an
  off-cycle run; `PAY_INPUT` reads `forRun(id)` instead of the period slice
- Many off-cycle runs per period; the one-per-period index applies to regular runs only

**Out of scope**

- The one-time payout as a thing of its own — it is a `ONE_TIME_PAYOUT` ledger row (`W-19`);
  untagged, the regular run pays it (`W-29.3`); tagged here, this run does
- Bonus computation — `W-29.2` §3 (the periodic rule) and `W-29.3` §3 (paid, not pro-rated)
- Tax on an off-cycle payment — `W-36` adds its contributor to the same loop and decides
  cumulative versus flat
- Statutory on an off-cycle payment — `W-31`, same loop
- Release of withheld salary (`OffCyclePayRunController.java:89`) — **dropped**, founder
  decision 2026-09-25. Legacy stores the import and nothing reads it
- Arrears from a back-dated revision (`W-29.2` §2) and recovery of a negative net from the
  next period (`W-29.3` §2) — each its own ticket; both are natural uses of an off-cycle run
  and neither is in it
- Approval (`W-15.2`), payment and payslips (`W-36`), screens (`W-47`)

## 3. Flow

```
[payroll officer] --> POST /payruns/off-cycle {payDate, employeeIds[], notes} --> payroll.run.execute
   --> period = YearMonth.from(payDate); PayPeriodService.periodFor(period)   (W-28; 409 when no schedule)
   --> per named employee: EmployeeService.get (core) -- must be considered per W-29.1 §3, else 400 naming the employee
                           EmployeeBankService.get                        (core) -- missing => SKIPPED NO_BANK_DETAILS
                           EmployeeSalaryService.versionInForce           (W-26.2) -- recorded if present, never a skip
   --> [payroll.payrun run_type OFF_CYCLE, DRAFT] + [payroll.employee_payrun INCLUDED | SKIPPED]
   --> no uniqueness: a second off-cycle run for the month is fine

[payroll officer] --> POST /payruns/{id}/inputs [{employeeId, kind, amount, sourceRef}] --> payroll.run.execute
   --> run is OFF_CYCLE and DRAFT, else 409
   --> each employeeId is an INCLUDED row of this run, else 400
   --> kind in (ONE_TIME_PAYOUT, OVERTIME, REIMBURSEMENT, AD_HOC_DEDUCTION), else 400   -- no LOP_DAYS
   --> PayInputService.record(cmd: period, kind, amount, sourceModule = payroll,
                              sourceRef = "payrun:" + id + ":" + sourceRef, runRef = id)     (W-19 + W-30.1)
   --> 201, the ledger rows as written

[payroll officer] --> POST /payruns/{id}/lock    --> DRAFT only; PayInputService.lockRun(id, period); LOCKED
[payroll officer] --> POST /payruns/{id}/compute --> exactly W-29.2 / W-29.4:
        for each INCLUDED row, for each PayLineContributor in @Order:
            STRUCTURE : ctx.run().runType() == OFF_CYCLE ? no lines : as W-29.2
            LOP       : OFF_CYCLE ? no lines, lop_days = unpaid_days = 0, paid_days = null : as W-29.3
            PAY_INPUT : inputs = OFF_CYCLE ? the employee's slice of forRun(id) : of forPeriod(period)   (read once per run)
        then W-29.2 sums; COMPUTED
[payroll officer] --> POST /payruns/{id}/cancel  --> as W-29.1; the run lock stays (W-19 §6)
```

**What "considered" means here.** The same test as `W-29.1` §3 row 1, at the run's period:
not deleted, joined on or before `period_end`, `ACTIVE` or terminated on or after
`period_start`. An off-cycle run is the usual way to pay a leaver's final dues, so a terminated
employee inside the window is in. Someone outside it is a `400` naming them, not a silent skip:
the officer named this person and should hear why.

**Why no salary check.** A regular run skips `NO_SALARY` because it has nothing to compute
without a structure (`W-29.1` §3). An off-cycle run computes from inputs only; a new joiner
whose CTC is not yet entered can still be paid a joining bonus. `salary_version_id` is recorded
when a version is in force, so `W-31` and `W-36` can read the statutory profile when they need it.

**`sourceRef`.** Idempotency is `(tenant, source_module, source_ref)` (`W-19` §6). The run
prefixes the officer's reference with its own id so the same reference on two runs is two
rows, and a retried `POST` for one run is one row — the second is `409` from the index, reported
per row in the response.

## 4. Backend changes

All under `code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/`, every file
`W-29.x`'s unless marked new.

| Layer | File | Change |
|---|---|---|
| Enumeration (change) | `PayRunType` | `OFF_CYCLE` added to `REGULAR` (`W-29.1` §4) |
| Entity (change) | `PayRun.java` | `runType` already there; `notes` |
| Service (change) | `PayRunService`, `PayRunServiceImpl` | `createOffCycle(LocalDate payDate, List<UUID> employeeIds, String notes)`; `addInputs(id, List<PayRunInputRequest>)`; `lock` branches on `runType`; `create(YearMonth)` unchanged |
| Service (change) | `PayRunInclusionServiceImpl` | one more entry point: `forNamed(period, employeeIds)` — the §3 rule, bank only, `400` for anyone not considered |
| Contributor (change) | `StructureLineContributor` (`W-29.2`), `LopLineContributor` (`W-29.3`) | first line: `if (ctx.run().runType() == OFF_CYCLE) return List.of();` — and for `LOP`, the zero day figures |
| Contributor (change) | `PayInputLineContributor` (`W-29.3`) | reads `ctx.payInputs()` as today; the **service** decides what goes on the context |
| Service (change) | `PayRunComputationServiceImpl` (`W-29.2`/`W-29.3`) | before the loop: `inputs = OFF_CYCLE ? payInputService.forRun(run.id) : payInputService.forPeriod(period)`. One line. Nothing else in the loop knows the run type |
| Exception | `EmployeeNotInRunException` (`400`), `NotAnOffCycleRunException` (`409`) | new |
| Controller (change) | `PayRunController.java` | two endpoints |
| DTO | `CreateOffCyclePayRunRequest`, `PayRunInputRequest`, `PayRunInputResponse` | new; `PayRunResponse` gains `runType`, `notes` |
| Repository (change) | `PayRunRepository` | `list` filter gains `runType` |

**No new `core` code.** `EmployeeService.get`, `EmployeeBankService.get`,
`EmployeeSalaryService.versionInForce` and the four `PayInputService` methods all exist by the
time this builds.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/payruns/off-cycle` | `{payDate, employeeIds[], notes?}` | `201` the run, `runType: OFF_CYCLE`, counts; `400` listing every employee not considered; `409` no schedule; `400` empty list or a pay date before `first_period_start` | `payroll.run.execute` |
| POST | `/api/v1/payroll/payruns/{id}/inputs` | `[{employeeId, kind, amount, sourceRef}]` | `201` one result per item: the ledger row id, or `DUPLICATE`; `409` unless `OFF_CYCLE` and `DRAFT`; `400` employee not `INCLUDED`, kind `LOP_DAYS`, amount ≤ 0 | `payroll.run.execute` **and** `core.pay_input.write` |
| GET | `/api/v1/payroll/payruns` | `?runType=REGULAR\|OFF_CYCLE` added to `W-29.1`'s filters | as `W-29.1` | `payroll.run.read` |
| GET, POST | every other `W-29.x` path | unchanged | `runType` in every `PayRunResponse` | as `W-29.x` |

No new permission code: `payroll.run.execute` and `payroll.run.read` exist
(`code/backend/migration/src/main/resources/db/migration/core/V025__catalogue_correction.sql:198-199`);
`core.pay_input.write` is `W-19`'s, and `lock` already needs `core.pay_input.lock` as `W-29.1` §4.
The `payroll-officer` role holds all of them after `W-19`.

Not ported: `PUT /{payrollRunId}` (`OffCyclePayRunController.java:37`, free update including
status), `DELETE` (`:67` — cancel), `/import` (`:79` — `POST /inputs` with the tenant's own
component vocabulary), `/import/release-withheld-salary` (`:89` — dropped).

## 5. Frontend changes

None. `W-47` owns the pay run screens; the legacy pages it replaces are
`legacy/Payroll-Fend-react/src/pages/mainPages/payRuns/addOffCycleDetails.js` and
`addOneTimePayoutDetails.js`.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V061__payrun_off_cycle.sql` | `payroll.payrun` | yes | additive — one column, a widened `CHECK`, a narrowed partial index |

`V061` extends the payroll lane's block by one (`DEV-TRACKER.md` § lanes). Use the lane's
number at branch time if it has moved.

```
ALTER TABLE payroll.payrun ADD COLUMN notes varchar(500) NULL;
ALTER TABLE payroll.payrun DROP CONSTRAINT <W-29.1's run_type check>;
ALTER TABLE payroll.payrun ADD  CONSTRAINT ck_payrun_run_type CHECK (run_type IN ('REGULAR','OFF_CYCLE'));
DROP INDEX payroll.uk_payrun_tenant_period;
CREATE UNIQUE INDEX uk_payrun_tenant_period ON payroll.payrun (tenant_id, period)
  WHERE status <> 'CANCELLED' AND run_type = 'REGULAR';
CREATE INDEX idx_payrun_tenant_type_period ON payroll.payrun (tenant_id, run_type, period);
```

| Legacy field (`OffCyclePayRun.java:17-45`, `OffCyclePayrunEmployee.java:17-53`) | Here |
|---|---|
| `payrollRunId` random UUID string (`:20`, `OffCyclePayRunServiceImpl.java:66`) | `payroll.payrun.id` |
| `payDate` (`:23`) | `pay_date`, the one the officer gives; `period` derived from it |
| `status` free string (`:26`), `statusFormatted` (`:28`) | `W-29.1`'s eight values and `CHECK` |
| `type = "off_cycle"` (`:31`) | `run_type = 'OFF_CYCLE'` |
| `notes` (`:33`) | `notes varchar(500)` |
| `employees` list, own table (`:42`) | `payroll.employee_payrun` rows |
| `earnings`, `deductions` per employee, own tables (`OffCyclePayrunEmployee.java:36-40`) | `core.pay_input` rows tagged with the run, then `payroll.employee_payrun_line` |
| `lopAdjustmentDetails` JSON (`:43-44`) | **dropped** — no loss of pay on an off-cycle run |
| `taxes` JSON with an override reason (`:46-47`, `OffCyclePayRunServiceImpl.java:317-325`) | **dropped** — `W-36` decides how tax is overridden, as a column, for both run types |
| `days` on an earning, priced at ÷30 (`OffCyclePayrunEmployeeEarning.java:29`, `OffCyclePayRunServiceImpl.java:97`) | **dropped** — an off-cycle input is an amount. A days-based figure is the regular run's job with `W-18.1`'s basis |

- [x] `tenant_id` already on `payroll.payrun`; both indexes lead with it
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `uk_payrun_tenant_period` kept for regular runs, the BUG-010 fix untouched in effect; `idx_payrun_tenant_type_period` for the list filter
- [x] No money column created; the `W-29.2` columns are `numeric(19,4)` already
- [x] Expand / contract — a nullable column, a constraint and an index replaced in one transaction, no row touched, no destructive step

RLS: the table already carries `tenant_isolation`; no policy change.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../payrun/OffCycleInclusionRulesTest.java` | a named leaver inside the window is `INCLUDED`; no bank is `SKIPPED NO_BANK_DETAILS`; no salary is **`INCLUDED`** with `salary_version_id` null; someone terminated before the period is a `400` naming them; an empty list is `400` |
| Unit | `payroll/.../payrun/OffCycleContributorsTest.java` | on an `OFF_CYCLE` context `STRUCTURE` and `LOP` return no lines and the day figures are zero; on a `REGULAR` context they behave as their own tests say |
| Integration | `payroll/.../payrun/OffCyclePayRunCreateIT.java` | create returns `201` with `period` = the pay date's month and the four dates from `periodFor`; a second off-cycle run for the same month is `201`; a regular run for that month still cannot be duplicated — the narrowed index, with two concurrent regular creates |
| Integration | `payroll/.../payrun/OffCyclePayRunInputsIT.java` | `POST /inputs` writes `core.pay_input` rows with `run_ref` = the run id and `source_ref` prefixed; a second identical `POST` reports `DUPLICATE` and writes nothing; an input for an employee not in the run is `400`; `LOP_DAYS` is `400`; after lock it is `409` **and** the ledger trigger refuses a direct insert with that `run_ref` |
| Integration | `payroll/.../payrun/OffCyclePayRunComputeIT.java` | **the acceptance test.** Two employees, a `ONE_TIME_PAYOUT` of 10,000 for one and an `AD_HOC_DEDUCTION` of 500 for the other; lock, compute: the first row has one `EARNING` line of 10,000 and `net_pay` 10,000, the second one `DEDUCTION` line and `net_pay` −500 (`W-29.3` keeps negative net), no `STRUCTURE` or `LOP` line anywhere, run totals match. **Then** the regular run for the same month, lock, compute: neither input appears on it |
| Integration | `payroll/.../payrun/OffCyclePayRunLockIT.java` | lock writes a `core.pay_input_period_lock` row with `run_ref` set and **no** period lock; the regular run's lock afterwards succeeds; cancel after lock leaves the run lock |
| Integration | `payroll/.../payrun/OffCyclePayRunRlsIT.java` | as `app_user`, tenant A cannot read, add inputs to, lock or compute tenant B's off-cycle run |
| Integration | `payroll/.../payrun/OffCyclePayRunGuardIT.java` | `payroll.run.read` alone is `403` on create and inputs; `payroll.run.execute` without `core.pay_input.write` is `403` on inputs |
| Integration (change) | `W-29.3`'s `PayRunInputsQueryCountIT.java` | one case added: an off-cycle run with 20 employees calls `forRun` once and `forPeriod` never |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. Every `W-29.x` test must
stay green untouched — a regular run never sees a tagged row.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='payroll.payrun'::regclass AND conname='ck_payrun_run_type';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexdef FROM pg_indexes WHERE schemaname='payroll' AND indexname='uk_payrun_tenant_period';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='payroll' AND table_name LIKE 'off_cycle%' OR table_name='one_time_payout';"
cd code/backend && mvn -q verify
git diff --stat main -- code/backend/core
grep -rn "OFF_CYCLE" code/backend/payroll/src/main | grep -v "PayRunType\|StructureLineContributor\|LopLineContributor\|PayRunComputationServiceImpl\|PayRunServiceImpl\|PayRunInclusionServiceImpl\|PayRunController\|Request\|Response"
```

| Check | Expected |
|---|---|
| `CHECK` | `CHECK ((run_type)::text = ANY (ARRAY['REGULAR', 'OFF_CYCLE']))` (Postgres' own spelling) |
| Index | `UNIQUE ... (tenant_id, period) WHERE ((status <> 'CANCELLED') AND (run_type = 'REGULAR'))` |
| Legacy tables | `0` — none of the five exist |
| Suite | green, no skips; `OffCyclePayRunComputeIT` passes, including the regular-run half |
| `core` diff | empty |
| `grep` | empty — the run type is known in exactly those places and nowhere else |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The off-cycle bonus is also paid by the regular run | **high — the expensive one** | `W-30.1` makes `forPeriod` untagged-only; `OffCyclePayRunComputeIT` computes both runs and asserts |
| The narrowed unique index lets two **regular** runs through | medium, expensive | `OffCyclePayRunCreateIT` re-runs `W-29.1`'s concurrent-create case after the index swap |
| `run_type` branches spread through the contributors and `W-31`/`W-36` copy the pattern | medium | Two contributors carry one guard line each; the `grep` in §8 is the fence. `W-31` and `W-36` decide their own off-cycle rule in their own specs |
| The port reintroduces legacy's own earning and deduction tables "for the import screen" | medium | §6 says zero tables and §8 counts them. The import is `POST /inputs` with the tenant's own component codes |
| A days-based payout is asked for and someone adds ÷30 | medium | `PayRunInputRequest` has `amount` and no `days`; `W-18.1`'s basis is for the regular run |
| An officer tags inputs through `core`'s endpoint to a run in the wrong state | low | `POST /inputs` is the supported path and validates; a stray tag is orphaned (`W-30.1` §13 point 4) |
| Lock of an off-cycle run locks the whole month | medium | `lock` branches on `runType`; `OffCyclePayRunLockIT` asserts no period lock row |

## 10. Rollback

Nothing is deployed. The migration is additive; an off-cycle run that was never created leaves
`payroll.payrun` exactly as `W-29.4` left it.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table |
| Flyway only, `ddl-auto` nowhere | one script, `V061` |
| `Money`/`BigDecimal` for money | inputs arrive as `Money`; lines and totals are `W-29.2`'s `numeric(19,4)`; no float anywhere |
| Index on `tenant_id` plus lookup columns | one index replaced, one added, `tenant_id` leading |
| Expand / contract | nullable column, constraint and index swapped in one transaction, no destructive step |
| No module references another module | `payroll` talks to `core` through `PayInputService`, `EmployeeService`, `EmployeeBankService`; never to `hrms` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-035 import needs components named `Bonus`, `Commission`, `Income Tax` (proposed) | **Fixed by replacement** — inputs name a `PayInputKind`; the component vocabulary is the tenant's (`W-26.1`) |
| DEBT-036 fixed ÷30 (proposed) | **Fixed by removal** — an off-cycle input is an amount |
| DEBT-037 one-time payouts never paid (proposed) | **Fixed** — `OffCyclePayRunComputeIT` is the proof: a payout reaches a line |
| BUG-010 two runs for one period (proposed, `W-29` analysis) | **Kept fixed** for regular runs by the narrowed index; off-cycle runs are many per month by design |
| DEBT-007, DEBT-008 no `/api/v1`, hand-built envelopes | **Fixed** by the `W-29.1` shape |
| DEBT-022 finders not org-scoped (`OffCyclePayrunEmployeeRepository.java:15`, `findByPayrollRun` without organisation) | **Fixed** — every finder takes `tenantId`, and RLS |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-25

1. **New tables or a run type?** **A run type, zero tables.** Founder, 2026-09-25. Same engine,
   same statuses, same lock, same worker, same RLS. `02-data-model.md:344-345` to be aligned by
   `/sync-docs`.
2. **Release of withheld salary?** **Dropped.** Founder, 2026-09-25. Legacy stores it and
   nothing reads it.
3. **Which inputs does an off-cycle run collect?** Only rows tagged with its id
   (`12-core-contracts.md:168`). Untagged inputs in the same month stay with the regular run.
4. **Does an off-cycle run need a salary version?** No — bank only. A joining bonus before the
   CTC is entered is a real case. `W-31` and `W-36` read the profile when it is there.
5. **Days-based payouts?** Not here. Legacy's ÷30 is dropped, not ported; a day-priced figure
   belongs to the regular run and `W-18.1`'s basis.
6. **Who writes the tagged input?** The run's own `POST /inputs`, which validates the run and
   the employee before calling the ledger. The `core` endpoint accepts a `runRef` too, but
   nothing checks it there and nothing needs to.
